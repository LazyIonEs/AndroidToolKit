package org.tool.kit.tests.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Test
import org.tool.kit.domain.apk.ApkAlignment
import org.tool.kit.domain.apk.ApkComponent
import org.tool.kit.domain.apk.ApkComponentType
import org.tool.kit.domain.apk.ApkExportedDeclaration
import org.tool.kit.domain.apk.ApkLibRule
import org.tool.kit.domain.apk.ApkLibRuleType
import org.tool.kit.domain.apk.ApkLibrarySource
import org.tool.kit.domain.apk.ApkManifestExtras
import org.tool.kit.domain.apk.ApkNativeLibrary
import org.tool.kit.domain.apk.LibRuleMatcher
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LibRuleMatcherTest {
    @Serializable
    private data class FixtureFile(val cases: List<FixtureCase>, val schemaVersion: Int)

    @Serializable
    private data class FixtureCase(
        val name: String,
        val input: String,
        val type: Int,
        val useRegex: Boolean = true,
        val expected: Int? = null,
        val rules: List<FixtureRule>,
    )

    @Serializable
    private data class FixtureRule(
        val id: Int,
        val name: String,
        val type: Int,
        val isRegexRule: Boolean,
        val priority: Int,
    )

    @Test
    fun passesAllOfficialMatchingFixtures() {
        val raw = """
            {"cases":[{"expected":2,"input":"foo","name":"exact precedence","rules":[{"id":1,"isRegexRule":true,"name":"foo|bar","priority":2,"type":0},{"id":2,"isRegexRule":false,"name":"foo","priority":20,"type":0},{"id":3,"isRegexRule":true,"name":".*","priority":10,"type":0}],"type":0},{"expected":1,"input":"bar","name":"alternation","rules":[{"id":1,"isRegexRule":true,"name":"foo|bar","priority":2,"type":0},{"id":2,"isRegexRule":false,"name":"foo","priority":20,"type":0},{"id":3,"isRegexRule":true,"name":".*","priority":10,"type":0}],"type":0},{"expected":3,"input":"foobar","name":"anchoring","rules":[{"id":1,"isRegexRule":true,"name":"foo|bar","priority":2,"type":0},{"id":2,"isRegexRule":false,"name":"foo","priority":20,"type":0},{"id":3,"isRegexRule":true,"name":".*","priority":10,"type":0}],"type":0},{"expected":null,"input":"bar\n","name":"final newline","rules":[{"id":1,"isRegexRule":true,"name":"foo|bar","priority":2,"type":0},{"id":2,"isRegexRule":false,"name":"foo","priority":20,"type":0},{"id":3,"isRegexRule":true,"name":".*","priority":10,"type":0}],"type":0},{"expected":1,"input":"foo|bar","name":"literal regex exact","rules":[{"id":1,"isRegexRule":true,"name":"foo|bar","priority":2,"type":0},{"id":2,"isRegexRule":false,"name":"foo","priority":20,"type":0},{"id":3,"isRegexRule":true,"name":".*","priority":10,"type":0}],"type":0},{"expected":1,"input":"libx.so","name":"escaped dot","rules":[{"id":1,"isRegexRule":true,"name":"libx\\.so","priority":1,"type":0}],"type":0},{"expected":null,"input":"libxXso","name":"escaped dot negative","rules":[{"id":1,"isRegexRule":true,"name":"libx\\.so","priority":1,"type":0}],"type":0},{"expected":1,"input":"库12","name":"Unicode literal","rules":[{"id":1,"isRegexRule":true,"name":"库[0-9]+","priority":1,"type":0}],"type":0},{"expected":null,"input":"lib١","name":"ASCII digits","rules":[{"id":1,"isRegexRule":true,"name":"lib\\d+","priority":1,"type":0}],"type":0},{"expected":null,"input":"a\u2028b","name":"line separator","rules":[{"id":1,"isRegexRule":true,"name":".*","priority":1,"type":0}],"type":0},{"expected":1,"input":"😀","name":"non-BMP input","rules":[{"id":1,"isRegexRule":true,"name":".","priority":1,"type":0}],"type":0},{"expected":null,"input":"bar","name":"regex disabled","rules":[{"id":1,"isRegexRule":true,"name":"foo|bar","priority":2,"type":0},{"id":2,"isRegexRule":false,"name":"foo","priority":20,"type":0},{"id":3,"isRegexRule":true,"name":".*","priority":10,"type":0}],"type":0,"useRegex":false},{"expected":null,"input":"foo","name":"wrong type","rules":[{"id":1,"isRegexRule":true,"name":"foo|bar","priority":2,"type":0},{"id":2,"isRegexRule":false,"name":"foo","priority":20,"type":0},{"id":3,"isRegexRule":true,"name":".*","priority":10,"type":0}],"type":1},{"expected":1,"input":"123","name":"digit class ascii","rules":[{"id":1,"isRegexRule":true,"name":"[\\d]+","priority":1,"type":0}],"type":0},{"expected":null,"input":"١","name":"digit class unicode","rules":[{"id":1,"isRegexRule":true,"name":"[\\d]+","priority":1,"type":0}],"type":0},{"expected":1,"input":"\\ddd","name":"literal slash d","rules":[{"id":1,"isRegexRule":true,"name":"\\\\d+","priority":1,"type":0}],"type":0},{"expected":null,"input":"\\١","name":"literal slash unicode","rules":[{"id":1,"isRegexRule":true,"name":"\\\\d+","priority":1,"type":0}],"type":0},{"expected":1,"input":"\\123","name":"slash then digits","rules":[{"id":1,"isRegexRule":true,"name":"\\\\\\d+","priority":1,"type":0}],"type":0},{"expected":null,"input":"\\١","name":"slash then unicode","rules":[{"id":1,"isRegexRule":true,"name":"\\\\\\d+","priority":1,"type":0}],"type":0}],"schemaVersion":5}
        """.trimIndent()
        val parsed = Json.decodeFromString<FixtureFile>(raw)
        assertEquals(19, parsed.cases.size)
        for (case in parsed.cases) {
            val rules = case.rules.map {
                rule(
                    id = it.id,
                    name = it.name,
                    type = ApkLibRuleType.fromCode(it.type)!!,
                    isRegex = it.isRegexRule,
                    priority = it.priority,
                )
            }
            val matcher = LibRuleMatcher(rules)
            val actual = matcher.match(
                name = case.input,
                type = ApkLibRuleType.fromCode(case.type)!!,
                useRegex = case.useRegex,
            )?.ruleId
            assertEquals(case.expected, actual, "Fixture case failed: ${case.name}")
        }
    }

    @Test
    fun matchesDexAncestorPrefixesAndTopLevelRegexPackages() {
        val matcher = LibRuleMatcher(
            listOf(
                rule(1, "com.google.android.material", ApkLibRuleType.Dex, isRegex = false, priority = 1, label = "Material"),
                rule(2, "okhttp3\\.(.*)", ApkLibRuleType.Dex, isRegex = true, priority = 2, label = "OkHttp"),
                rule(3, "kotlin\\.coroutines\\.(.*)", ApkLibRuleType.Dex, isRegex = true, priority = 3, label = "Kotlin Coroutines"),
                rule(4, "kotlin\\.(.*)", ApkLibRuleType.Dex, isRegex = true, priority = 4, label = "Kotlin"),
            )
        )
        assertEquals(1, matcher.matchDexPackage("com.google.android.material.button")?.ruleId)
        assertEquals(1, matcher.matchDexPackage("com.google.android.material")?.ruleId)
        assertEquals(2, matcher.matchDexPackage("okhttp3.internal.http2")?.ruleId)
        assertEquals(2, matcher.matchDexPackage("okhttp3")?.ruleId)
        assertEquals(3, matcher.matchDexPackage("kotlin.coroutines.jvm.internal")?.ruleId)
        assertEquals(4, matcher.matchDexPackage("kotlin.collections")?.ruleId)
        assertNull(matcher.matchDexPackage("com.example.app"))
    }

    @Test
    fun rejectsDuplicateAndInvalidRules() {
        assertFailsWith<IllegalArgumentException> {
            LibRuleMatcher(
                listOf(
                    rule(1, "libfoo.so", ApkLibRuleType.Native),
                    rule(2, "libfoo.so", ApkLibRuleType.Native),
                )
            )
        }
        assertFailsWith<IllegalArgumentException> {
            LibRuleMatcher(listOf(rule(0, "libfoo.so", ApkLibRuleType.Native)))
        }
        assertFailsWith<IllegalArgumentException> {
            LibRuleMatcher(listOf(rule(1, "libfoo.so", ApkLibRuleType.Native, priority = -1)))
        }
    }

    @Test
    fun analyzesAllEightSourcesAndGroupsByUuid() {
        val sharedUuid = "11111111-1111-1111-1111-111111111111"
        val matcher = LibRuleMatcher(
            listOf(
                rule(1, "libshared.so", ApkLibRuleType.Native, uuid = sharedUuid, label = "Shared SDK", iconId = "ic_shared"),
                rule(2, "com.shared.MainActivity", ApkLibRuleType.Activity, uuid = sharedUuid, label = "Shared SDK", iconId = "ic_shared"),
                rule(3, "com.shared.BgService", ApkLibRuleType.Service, uuid = sharedUuid, label = "Shared SDK", iconId = "ic_shared"),
                rule(4, "com.shared.PushReceiver", ApkLibRuleType.Receiver, uuid = sharedUuid, label = "Shared SDK", iconId = "ic_shared"),
                rule(5, "com.shared.InitProvider", ApkLibRuleType.Provider, uuid = sharedUuid, label = "Shared SDK", iconId = "ic_shared"),
                rule(6, "com.shared.sdk", ApkLibRuleType.Dex, uuid = sharedUuid, label = "Shared SDK", iconId = "ic_shared"),
                rule(7, "com.shared.staticlib", ApkLibRuleType.Static, uuid = sharedUuid, label = "Shared SDK", iconId = "ic_shared"),
                rule(8, "com.shared.ACTION_PING", ApkLibRuleType.Action, uuid = sharedUuid, label = "Shared SDK", iconId = "ic_shared"),
            )
        )
        val outcome = matcher.analyze(
            components = listOf(
                ApkComponent("com.shared.MainActivity", ApkComponentType.Activity, ApkExportedDeclaration.Enabled, "p"),
                ApkComponent("com.shared.Alias", ApkComponentType.ActivityAlias, ApkExportedDeclaration.Disabled, "p", "com.shared.MainActivity"),
                ApkComponent("com.shared.BgService", ApkComponentType.Service, ApkExportedDeclaration.Disabled, "p"),
                ApkComponent("com.shared.PushReceiver", ApkComponentType.Receiver, ApkExportedDeclaration.Disabled, "p"),
                ApkComponent("com.shared.InitProvider", ApkComponentType.Provider, ApkExportedDeclaration.Disabled, "p"),
            ),
            nativeLibraries = listOf(
                ApkNativeLibrary("lib/arm64-v8a/libshared.so", "arm64-v8a", 10, 10, false, ApkAlignment.Aligned, ApkAlignment.Aligned),
                ApkNativeLibrary("lib/arm64-v8a/libunknown.so", "arm64-v8a", 10, 10, false, ApkAlignment.Aligned, ApkAlignment.Aligned),
            ),
            dexPackages = setOf("com.shared.sdk.core", "com.shared.sdk.net"),
            manifestExtras = ApkManifestExtras(
                staticLibraries = listOf("com.shared.staticlib"),
                intentActions = listOf("com.shared.ACTION_PING"),
            ),
            rulesVersion = 50L,
        )

        assertEquals(5, outcome.components?.size)
        assertTrue(outcome.components!!.all { it.rule != null })
        assertNotNull(outcome.nativeLibraries!![0].rule)
        assertNull(outcome.nativeLibraries!![1].rule)
        assertEquals(1, outcome.analysis.dexMatches.size)
        assertEquals(1, outcome.analysis.staticMatches.size)
        assertEquals(1, outcome.analysis.actionMatches.size)
        val sdk = outcome.analysis.sdks.single()
        assertEquals(sharedUuid, sdk.uuid)
        assertEquals(ApkLibrarySource.entries.toSet(), sdk.sources)
        assertEquals(50L, outcome.analysis.rulesVersion)
    }

    private fun rule(
        id: Int,
        name: String,
        type: ApkLibRuleType,
        isRegex: Boolean = false,
        priority: Int = id,
        uuid: String = "00000000-0000-0000-0000-${id.toString().padStart(12, '0')}",
        label: String = "Lib$id",
        iconId: String? = null,
    ) = ApkLibRule(
        id = id,
        uuid = uuid,
        name = name,
        label = label,
        type = type,
        iconIndex = if (iconId == null) -1 else id,
        iconId = iconId,
        isSimpleColorIcon = false,
        isRegexRule = isRegex,
        regexName = null,
        priority = priority,
        hasDetail = true,
    )
}
