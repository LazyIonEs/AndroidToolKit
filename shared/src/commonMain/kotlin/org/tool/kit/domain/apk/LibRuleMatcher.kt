package org.tool.kit.domain.apk

/**
 * Pure Kotlin matcher implementing LibChecker v5 precedence and portable regex semantics.
 */
class LibRuleMatcher(rules: List<ApkLibRule>) {
    private val exact: Map<ApkLibRuleType, Map<String, ApkLibRule>>
    private val regex: Map<ApkLibRuleType, List<Pair<Regex, ApkLibRule>>>

    init {
        val exactMap = mutableMapOf<ApkLibRuleType, MutableMap<String, ApkLibRule>>()
        val regexCandidates = mutableMapOf<ApkLibRuleType, MutableList<ApkLibRule>>()
        for (rule in rules) {
            require(rule.id > 0) { "Invalid rule id: ${rule.id}" }
            require(rule.priority >= 0) { "Invalid rule priority: ${rule.priority}" }
            val byName = exactMap.getOrPut(rule.type) { mutableMapOf() }
            val previous = byName.put(rule.name, rule)
            require(previous == null) { "Duplicate rule type/name: ${rule.type}/${rule.name}" }
            if (rule.isRegexRule) {
                regexCandidates.getOrPut(rule.type) { mutableListOf() }.add(rule)
            }
        }
        exact = exactMap.mapValues { it.value.toMap() }
        regex = regexCandidates.mapValues { (_, list) ->
            list.sortedWith(compareBy<ApkLibRule>({ it.priority }, { it.id }))
                .map { rule -> Regex(portablePattern(rule.name)) to rule }
        }
    }

    /** Match a single name against exact rules first, then ordered regex rules when enabled. */
    fun match(name: String, type: ApkLibRuleType, useRegex: Boolean = true): ApkLibRuleMatch? {
        exact[type]?.get(name)?.let { return it.toMatch(matchedByRegex = false) }
        if (!useRegex || name.any { it in LINE_SEPARATORS }) return null
        val match = regex[type]?.firstOrNull { (pattern, _) -> pattern.matches(name) }?.second
        return match?.toMatch(matchedByRegex = true)
    }

    /**
     * Match a Java/Kotlin package name against DEX rules by walking ancestor prefixes
     * from longest down to 2 segments, falling back to DEX regex rules.
     */
    fun matchDexPackage(packageName: String): ApkLibRuleMatch? {
        if (packageName.isBlank()) return null
        var candidate = packageName
        while (true) {
            val dotCount = candidate.count { it == '.' }
            if (dotCount >= 1) {
                match(candidate, ApkLibRuleType.Dex, useRegex = false)?.let { return it }
            }
            val lastDot = candidate.lastIndexOf('.')
            if (lastDot <= 0) break
            candidate = candidate.substring(0, lastDot)
        }
        match(packageName, ApkLibRuleType.Dex, useRegex = true)?.let { return it }
        if ('.' !in packageName) {
            match("$packageName.", ApkLibRuleType.Dex, useRegex = true)?.let { return it }
        }
        return null
    }

    /**
     * Annotate components and native libraries and aggregate all matched SDKs across
     * native libraries, manifest components, DEX packages, static libraries, and actions.
     */
    fun analyze(
        components: List<ApkComponent>?,
        nativeLibraries: List<ApkNativeLibrary>?,
        dexPackages: Set<String>?,
        manifestExtras: ApkManifestExtras?,
        rulesVersion: Long,
    ): AnalysisOutcome {
        val detected = mutableListOf<ApkDetectedEntry>()

        val enrichedComponents = components?.map { component ->
            val ruleType = when (component.type) {
                ApkComponentType.Activity, ApkComponentType.ActivityAlias -> ApkLibRuleType.Activity
                ApkComponentType.Service -> ApkLibRuleType.Service
                ApkComponentType.Receiver -> ApkLibRuleType.Receiver
                ApkComponentType.Provider -> ApkLibRuleType.Provider
            }
            val source = when (component.type) {
                ApkComponentType.Activity, ApkComponentType.ActivityAlias -> ApkLibrarySource.Activity
                ApkComponentType.Service -> ApkLibrarySource.Service
                ApkComponentType.Receiver -> ApkLibrarySource.Receiver
                ApkComponentType.Provider -> ApkLibrarySource.Provider
            }
            val matched = match(component.name, ruleType, useRegex = true)
                ?: if (component.type == ApkComponentType.ActivityAlias) {
                    component.targetActivity?.let { match(it, ApkLibRuleType.Activity, useRegex = true) }
                } else null
            if (matched != null && component.name.isNotBlank()) {
                detected += ApkDetectedEntry(component.name, source, matched)
            }
            component.copy(rule = matched)
        }

        val enrichedLibraries = nativeLibraries?.map { library ->
            val fileName = library.path.substringAfterLast('/')
            val matched = match(fileName, ApkLibRuleType.Native, useRegex = true)
            if (matched != null) {
                detected += ApkDetectedEntry(library.path, ApkLibrarySource.Native, matched)
            }
            library.copy(rule = matched)
        }

        val dexMatchesByRuleId = linkedMapOf<Int, ApkDetectedEntry>()
        dexPackages.orEmpty().sorted().forEach { pkg ->
            val matched = matchDexPackage(pkg) ?: return@forEach
            val existing = dexMatchesByRuleId[matched.ruleId]
            if (existing == null || pkg.length < existing.name.length) {
                dexMatchesByRuleId[matched.ruleId] = ApkDetectedEntry(pkg, ApkLibrarySource.Dex, matched)
            }
        }
        val dexMatches = dexMatchesByRuleId.values.toList()
        detected += dexMatches

        val staticMatches = manifestExtras?.staticLibraries.orEmpty().mapNotNull { libName ->
            val matched = match(libName, ApkLibRuleType.Static, useRegex = true) ?: return@mapNotNull null
            ApkDetectedEntry(libName, ApkLibrarySource.Static, matched)
        }
        detected += staticMatches

        val actionMatches = manifestExtras?.intentActions.orEmpty().mapNotNull { action ->
            val matched = match(action, ApkLibRuleType.Action, useRegex = true)
                ?: match(action, ApkLibRuleType.Static, useRegex = true)
                ?: return@mapNotNull null
            ApkDetectedEntry(action, ApkLibrarySource.Action, matched)
        }
        detected += actionMatches

        val sdks = detected
            .groupBy { it.rule.uuid }
            .map { (uuid, entries) ->
                val distinctEntries = entries.distinctBy { Triple(it.source, it.name, it.rule.ruleId) }
                val primary = distinctEntries.firstOrNull { it.rule.hasDetail && it.rule.iconId != null }?.rule
                    ?: distinctEntries.firstOrNull { it.rule.hasDetail }?.rule
                    ?: distinctEntries.first().rule
                ApkRecognizedSdk(
                    uuid = uuid,
                    label = primary.label,
                    iconId = distinctEntries.firstNotNullOfOrNull { it.rule.iconId },
                    isSimpleColorIcon = distinctEntries.firstOrNull { it.rule.iconId != null }?.rule?.isSimpleColorIcon
                        ?: primary.isSimpleColorIcon,
                    hasDetail = distinctEntries.any { it.rule.hasDetail },
                    primaryRuleId = primary.ruleId,
                    sources = distinctEntries.mapTo(linkedSetOf()) { it.source },
                    entries = distinctEntries,
                )
            }
            .sortedWith(compareByDescending<ApkRecognizedSdk> { it.entries.size }.thenBy { it.label.lowercase() })

        return AnalysisOutcome(
            components = enrichedComponents,
            nativeLibraries = enrichedLibraries,
            analysis = ApkRuleAnalysis(
                sdks = sdks,
                dexMatches = dexMatches,
                staticMatches = staticMatches,
                actionMatches = actionMatches,
                rulesVersion = rulesVersion,
            ),
        )
    }

    data class AnalysisOutcome(
        val components: List<ApkComponent>?,
        val nativeLibraries: List<ApkNativeLibrary>?,
        val analysis: ApkRuleAnalysis,
    )

    companion object {
        private const val LINE_SEPARATORS = "\n\r\u0085\u2028\u2029"

        /**
         * Normalize `\d` to ASCII digits `[0-9]` (or `0-9` inside `[...]`) so desktop JVM
         * matching behaves identically to Android ICU regexes on official fixtures.
         */
        fun portablePattern(pattern: String): String = buildString {
            var inClass = false
            var index = 0
            while (index < pattern.length) {
                val c = pattern[index++]
                if (c == '\\' && index < pattern.length) {
                    val escaped = pattern[index++]
                    if (escaped == 'd') append(if (inClass) "0-9" else "[0-9]")
                    else {
                        append(c)
                        append(escaped)
                    }
                } else {
                    if (c == '[') inClass = true
                    else if (c == ']') inClass = false
                    append(c)
                }
            }
        }

        private fun ApkLibRule.toMatch(matchedByRegex: Boolean) = ApkLibRuleMatch(
            ruleId = id,
            uuid = uuid,
            label = label,
            type = type,
            iconId = iconId,
            isSimpleColorIcon = isSimpleColorIcon,
            hasDetail = hasDetail,
            matchedByRegex = matchedByRegex,
            ruleName = name,
        )
    }
}
