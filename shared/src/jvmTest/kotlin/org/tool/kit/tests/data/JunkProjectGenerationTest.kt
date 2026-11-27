package org.tool.kit.tests.data

import java.io.File
import java.nio.file.Files
import java.util.jar.JarInputStream
import java.util.zip.ZipFile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes.ASM9
import org.tool.kit.data.generator.JunkGenerationProfile
import org.tool.kit.data.generator.JunkProject
import org.tool.kit.data.generator.JunkProjectGenerator
import org.tool.kit.data.generator.JunkProjectManifest
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JunkProjectGenerationTest {
    @Test fun assignsOneHundredStableProfilesWithAtLeastTwoDifferences() {
        val pinned = project(1).copy(profileId = 41, seed = Long.MIN_VALUE.toString())
        val input = JunkProjectManifest(listOf(pinned) + (2..100).map(::project))
        val assigned = input.resolve()
        assertEquals(assigned, input.copy(projects = input.projects.reversed()).resolve())
        assertEquals(pinned, assigned.projects.first { it.projectId == pinned.projectId })

        val dimensions = assigned.projects.map { project ->
            val profile = JunkGenerationProfile(requireNotNull(project.profileId))
            listOf(profile.roles.ordinal, profile.input.ordinal, profile.calls.ordinal, profile.program.ordinal, profile.state.ordinal)
        }
        assertEquals(100, dimensions.distinct().size)
        assertTrue((0..4).all { column -> dimensions.map { it[column] }.toSet().size == 4 })
        for (left in dimensions.indices) {
            for (right in left + 1 until dimensions.size) {
                assertTrue(dimensions[left].zip(dimensions[right]).count { (a, b) -> a != b } >= 2)
            }
        }

        val expanded = assigned.copy(projects = assigned.projects + project(0)).resolve()
        assertEquals(assigned.projects, expanded.projects.filterNot { it.projectId == project(0).projectId })
    }

    @Test fun rejectsAmbiguousOrUnsupportedAssignments() {
        val pinned = project(1).copy(profileId = 41)
        val inputs = listOf(
            JunkProjectManifest(listOf(pinned, project(2).copy(profileId = 41))),
            JunkProjectManifest(listOf(pinned, pinned.copy(projectId = pinned.projectId.uppercase(), profileId = 42))),
            JunkProjectManifest(listOf(pinned.copy(projectId = "../outside"))),
            JunkProjectManifest(listOf(pinned.copy(profileId = JunkGenerationProfile.CAPACITY))),
            JunkProjectManifest(listOf(pinned.copy(seed = "9223372036854775808"))),
            JunkProjectManifest(listOf(pinned), generatorVersion = JunkGenerationProfile.GENERATOR_VERSION + 1),
        )
        inputs.forEach { input -> assertFailsWith<IllegalArgumentException> { input.resolve() } }
    }

    @Test fun comparesActualArchivesAndRebuildsOneFromTheSavedManifest() {
        val root = Files.createTempDirectory("toolkit-project-aar-test-").toFile()
        try {
            val source = root.resolve("projects.json")
            source.writeText(Json.encodeToString(JunkProjectManifest((1..3).map(::project))))
            val all = JunkProjectGenerator.generate(source, root.resolve("all"), compare = true)
            assertEquals(3, all.archives.size)
            assertTrue(all.archives.all { it.activities == 2 && it.file.isFile })
            val report = Json.parseToJsonElement(requireNotNull(all.comparison).readText()).jsonObject
            assertEquals(3, report.getValue("pairCount").jsonPrimitive.int)
            assertEquals(3, report.getValue("pairs").jsonArray.size)
            assertEquals(setOf("methods", "classes", "calls", "callEdges"), report.getValue("highestPairs").jsonObject.keys)

            val chosen = all.archives[1]
            val rebuilt = JunkProjectGenerator.generate(all.manifest, root.resolve("single"), chosen.project.projectId)
            assertEquals(chosen.project, rebuilt.archives.single().project)
            assertContentEquals(chosen.file.readBytes(), rebuilt.archives.single().file.readBytes())
            ZipFile(chosen.file).use { archive ->
                assertNull(archive.getEntry("proguard.txt"))
                val entry = archive.getEntry("toolkit-generation.json")
                val metadata = Json.parseToJsonElement(archive.getInputStream(entry).bufferedReader().use { it.readText() }).jsonObject
                assertEquals(chosen.project.profileId, metadata.getValue("profile").jsonObject.getValue("id").jsonPrimitive.int)
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun switchingGeneratorVersionReusesTheSavedAssignments() {
        val root = Files.createTempDirectory("toolkit-project-version-test-").toFile()
        try {
            val source = root.resolve("projects.json")
            val latest = JunkGenerationProfile.GENERATOR_VERSION
            source.writeText(Json.encodeToString(JunkProjectManifest((1..2).map(::project), generatorVersion = 2)))
            val original = JunkProjectGenerator.generate(source, root.resolve("original"))
            val optimized = JunkProjectGenerator.generate(original.manifest, root.resolve("optimized"), generatorVersion = latest)
            val originalPlan = Json.decodeFromString<JunkProjectManifest>(original.manifest.readText())
            val optimizedPlan = Json.decodeFromString<JunkProjectManifest>(optimized.manifest.readText())
            assertEquals(originalPlan.copy(generatorVersion = latest), optimizedPlan)
            assertEquals(2, originalPlan.generatorVersion)
            val oldArchive = original.archives.first()
            assertFalse(oldArchive.file.readBytes().contentEquals(optimized.archives.first().file.readBytes()))
            val rebuilt = JunkProjectGenerator.generate(original.manifest, root.resolve("rebuild"), oldArchive.project.projectId)
            assertContentEquals(oldArchive.file.readBytes(), rebuilt.archives.single().file.readBytes())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun manifestControlsOptionalDtoMethodsAndSavesTheChoicesForRebuilds() {
        val root = Files.createTempDirectory("toolkit-optional-dto-adapter-").toFile()
        try {
            val source = root.resolve("projects.json")
            val defaultProject = project(1).copy(profileId = 14, seed = "24", packageCount = 12)
            val enabledProject = project(2).copy(profileId = 62, seed = "24", packageCount = 12,
                includeDtoStringAdapters = true, includeDtoComparison = true)
            source.writeText(Json.encodeToString(JunkProjectManifest(listOf(defaultProject, enabledProject), generatorVersion = 26)))
            val generated = JunkProjectGenerator.generate(source, root.resolve("all"), compare = true)
            val report = Json.parseToJsonElement(requireNotNull(generated.comparison).readText()).jsonObject
            assertEquals(listOf(emptySet(), setOf("()I")), report.edgeDescriptors("getValue"))
            assertEquals(listOf(false, true), generated.archives.map { hasDtoComparison(it.file) })
            val saved = Json.decodeFromString<JunkProjectManifest>(generated.manifest.readText())
            assertEquals(listOf(defaultProject, enabledProject), saved.projects)
            val names = Json.parseToJsonElement(generated.manifest.readText()).jsonObject.getValue("projects").jsonArray
            assertFalse("includeDtoStringAdapters" in names.first().jsonObject)
            assertFalse("includeDtoComparison" in names.first().jsonObject)
            val rebuilt = JunkProjectGenerator.generate(generated.manifest, root.resolve("single"), enabledProject.projectId)
            assertContentEquals(generated.archives.last().file.readBytes(), rebuilt.archives.single().file.readBytes())
        } finally {
            root.deleteRecursively()
        }
    }

    private fun hasDtoComparison(file: File): Boolean = ZipFile(file).use { zip ->
        JarInputStream(zip.getInputStream(zip.getEntry("classes.jar"))).use { jar ->
            generateSequence { jar.nextJarEntry }.filter { entry ->
                entry.name.endsWith(".class") && entry.name.substringAfterLast('/').startsWith("Dto")
            }.any {
                var found = false
                ClassReader(jar.readBytes()).accept(object : ClassVisitor(ASM9) {
                    override fun visitMethod(access: Int, name: String, descriptor: String,
                        signature: String?, exceptions: Array<out String>?): MethodVisitor? {
                        if (name == "compareValue") found = true
                        return null
                    }
                }, ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
                found
            }
        }
    }

    @Test fun sizeBasedProjectsPublishMeasuredSizesAndRebuildFromResolvedCounts() {
        val root = Files.createTempDirectory("toolkit-project-size-test-").toFile()
        try {
            val sized = project(1).copy(packageCount = 0, activitiesPerPackage = 0,
                minimumSizeMb = JsonPrimitive(0.175), maximumSizeMb = JsonPrimitive(0.225))
            val source = root.resolve("projects.json")
            source.writeText(Json.encodeToString(JunkProjectManifest(listOf(sized, project(2)))))
            val all = JunkProjectGenerator.generate(source, root.resolve("all"), compare = true)
            val archive = all.archives.first()
            assertTrue(archive.file.length() in 175_000..225_000)
            val resolved = Json.decodeFromString<JunkProjectManifest>(all.manifest.readText()).projects.first()
            assertEquals(archive.activities, resolved.packageCount)
            assertEquals(1, resolved.activitiesPerPackage)
            assertNull(resolved.sizeRange())
            val rebuilt = JunkProjectGenerator.generate(all.manifest, root.resolve("single"), sized.projectId)
            assertContentEquals(archive.file.readBytes(), rebuilt.archives.single().file.readBytes())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun componentRoutesDistinguishFanOutFromChainWithTheSameSharedSignatures() {
        assertCallOverlapRemoved(
            listOf(
                project(1).copy(packageCount = 8, profileId = 225, seed = "6231970063301349179"),
                project(2).copy(packageCount = 8, profileId = 229, seed = "6782122025431993623"),
            ),
            baselineVersion = 2,
        )
    }

    @Test fun pairedViewsKeepBothStagesWhenEvaluationSharesTheFanOutDescriptor() {
        assertCallOverlapRemoved(
            listOf(
                project(1).copy(packageCount = 8, profileId = 138, seed = "6990203211525664710"),
                project(2).copy(packageCount = 8, profileId = 146, seed = "5523047173474742750"),
            ),
            baselineVersion = 3,
        )
    }

    @Test fun sharedStagesUseThePairedResultsOrTreeSummaryInTheirContracts() {
        val (original, optimized) = compareVersions(
            listOf(
                project(1).copy(packageCount = 8, profileId = 25, seed = "5801043156719015172"),
                project(2).copy(packageCount = 8, profileId = 29, seed = "-5468503734060619576"),
            ),
            baselineVersion = 4,
        )
        val before = original.onlyPair()
        val after = optimized.onlyPair()
        assertEquals(before.getValue("comparedCallEdges"), after.getValue("comparedCallEdges"))
        assertTrue(after.getValue("matchedCallEdges").jsonPrimitive.int < before.getValue("matchedCallEdges").jsonPrimitive.int)

        assertEquals(listOf(setOf("(IIII)I"), setOf("(IIJI)I")), optimized.edgeDescriptors("prepare"))
        assertEquals(listOf(setOf("(IIIII)Z"), setOf("(IIIJI)Z")), optimized.edgeDescriptors("evaluate"))
    }

    @Test fun programResultsAreReturnedFromCalculateAndConsumedByPrepare() {
        val (original, optimized) = compareVersions(
            listOf(
                project(1).copy(packageCount = 8, profileId = 50, seed = "4123219072964910642"),
                project(2).copy(packageCount = 8, profileId = 49, seed = "1740800398287404138"),
            ),
            baselineVersion = 5,
        )
        val before = original.onlyPair()
        val after = optimized.onlyPair()
        assertEquals(before.getValue("comparedCallEdges"), after.getValue("comparedCallEdges"))
        assertTrue(after.getValue("matchedCallEdges").jsonPrimitive.int < before.getValue("matchedCallEdges").jsonPrimitive.int)
        assertEquals(List(2) { setOf("(Ljava/lang/String;)I") }, original.edgeDescriptors("calculate"))
        assertEquals(List(2) { setOf("(Ljava/lang/String;)I") }, original.edgeDescriptors("prepare"))
        assertEquals(listOf(setOf("(Ljava/lang/String;)J"), setOf("(Ljava/lang/String;)Z")), optimized.edgeDescriptors("calculate"))
        assertEquals(listOf(setOf("(Ljava/lang/String;J)I"), setOf("(Ljava/lang/String;Z)I")), optimized.edgeDescriptors("prepare"))
    }

    @Test fun calculateAcceptsRoleContextForMatchingPrimaryInputsAndResults() {
        val (original, optimized) = compareVersions(
            listOf(
                project(1).copy(packageCount = 8, profileId = 177, seed = "-7545287604012869009"),
                project(2).copy(packageCount = 8, profileId = 49, seed = "1740800398287404138"),
            ),
            baselineVersion = 6,
        )
        val before = original.onlyPair()
        val after = optimized.onlyPair()
        assertEquals(before.getValue("comparedCallEdges"), after.getValue("comparedCallEdges"))
        assertTrue(after.getValue("matchedCallEdges").jsonPrimitive.int < before.getValue("matchedCallEdges").jsonPrimitive.int)
        assertEquals(List(2) { setOf("(Ljava/lang/String;)Z") }, original.edgeDescriptors("calculate"))
        assertEquals(listOf(setOf("(Ljava/lang/String;J)Z"), setOf("(Ljava/lang/String;I)Z")), optimized.edgeDescriptors("calculate"))
    }

    @Test fun preparedOperandsSeparatePreparationAndEvaluationContracts() {
        val (original, optimized) = compareVersions(
            listOf(
                project(1).copy(packageCount = 8, profileId = 138, seed = "6990203211525664710"),
                project(2).copy(packageCount = 8, profileId = 168, seed = "3903388214345335848"),
            ),
            baselineVersion = 7,
        )
        val before = original.onlyPair()
        val after = optimized.onlyPair()
        assertEquals(before.getValue("comparedCallEdges"), after.getValue("comparedCallEdges"))
        assertTrue(after.getValue("matchedCallEdges").jsonPrimitive.int < before.getValue("matchedCallEdges").jsonPrimitive.int)
        assertEquals(setOf("(JIII)J"), original.edgeDescriptors("evaluate")[0])
        assertEquals(setOf("(JIII)J"), original.edgeDescriptors("prepare")[1])
        assertEquals(setOf("([JIII)J"), optimized.edgeDescriptors("evaluate")[0])
        assertEquals(setOf("(JIII)[J"), optimized.edgeDescriptors("prepare")[1])
    }

    @Test fun calculateUsesDistinctChainAndTreeOperands() {
        val (original, optimized) = compareVersions(
            listOf(
                project(1).copy(packageCount = 8, profileId = 63, seed = "-8084188783784301055"),
                project(2).copy(packageCount = 8, profileId = 55, seed = "1624448738705680116"),
            ),
            baselineVersion = 8,
        )
        val before = original.onlyPair()
        val after = optimized.onlyPair()
        assertEquals(before.getValue("comparedCallEdges"), after.getValue("comparedCallEdges"))
        assertTrue(after.getValue("matchedCallEdges").jsonPrimitive.int < before.getValue("matchedCallEdges").jsonPrimitive.int)
        assertEquals(List(2) { setOf("(Ljava/lang/String;I)Ljava/lang/String;") }, original.edgeDescriptors("calculate"))
        assertEquals(
            listOf(setOf("(Ljava/lang/String;JII)Ljava/lang/String;"), setOf("(Ljava/lang/String;JI)Ljava/lang/String;")),
            optimized.edgeDescriptors("calculate"),
        )
    }

    @Test fun profiledMethodBodiesReduceOverlapWithoutChangingCallEdges() {
        val (original, optimized) = compareVersions(
            listOf(
                project(1).copy(packageCount = 12, activitiesPerPackage = 4, profileId = 177, seed = "-7545287604012869009"),
                project(2).copy(packageCount = 12, activitiesPerPackage = 4, profileId = 165, seed = "3872409039075771126"),
            ),
            baselineVersion = 9,
        )
        val before = original.onlyPair()
        val after = optimized.onlyPair()
        assertTrue(after.getValue("methodOverlap").jsonPrimitive.double < before.getValue("methodOverlap").jsonPrimitive.double)
        assertEquals(
            original.getValue("archives").jsonArray.map { it.jsonObject.getValue("callEdgeHistogram") },
            optimized.getValue("archives").jsonArray.map { it.jsonObject.getValue("callEdgeHistogram") },
        )
        assertEquals(before.getValue("callEdgeOverlap"), after.getValue("callEdgeOverlap"))
    }

    @Test fun collectionBodiesStayDistinctAcrossTopologiesWithTheSameInputAndProgram() {
        val (original, optimized) = compareVersions(
            listOf(
                project(1).copy(packageCount = 12, activitiesPerPackage = 4, profileId = 129, seed = "1416047901308287016"),
                project(2).copy(packageCount = 12, activitiesPerPackage = 4, profileId = 133, seed = "8335131626015954904"),
            ),
            baselineVersion = 9,
        )
        assertTrue(
            optimized.onlyPair().getValue("methodOverlap").jsonPrimitive.double <
                original.onlyPair().getValue("methodOverlap").jsonPrimitive.double,
        )
    }

    @Test fun roleSpecificMethodsReduceOverlapForBothHighOverlapProfilePairs() {
        val (original, optimized) = compareVersions(
            listOf(
                project(27).copy(packageCount = 48, profileId = 45, seed = "7650977492772279606"),
                project(29).copy(packageCount = 48, profileId = 131, seed = "-5014347732852214037"),
                project(32).copy(packageCount = 48, profileId = 165, seed = "3872409039075771126"),
                project(94).copy(packageCount = 48, profileId = 67, seed = "318142149394398089"),
            ),
            baselineVersion = 10,
        )
        for ((left, right) in listOf("project-29" to "project-94", "project-27" to "project-32")) {
            fun overlap(report: JsonObject) = report.getValue("pairs").jsonArray.map { it.jsonObject }.single {
                it.getValue("left").jsonPrimitive.content == left && it.getValue("right").jsonPrimitive.content == right
            }.getValue("methodOverlap").jsonPrimitive.double
            assertTrue(overlap(optimized) < overlap(original), "$left and $right retain overlapping method bodies")
        }
    }

    @Test fun programSpecificMethodsReduceOverlapBetweenProjectsWithTheSameRole() {
        val (original, optimized) = compareVersions(
            listOf(
                project(51).copy(packageCount = 48, profileId = 16, seed = "1767197725691047424"),
                project(89).copy(packageCount = 48, profileId = 21, seed = "7720312720806296453"),
            ),
            baselineVersion = 11,
        )
        assertTrue(
            optimized.onlyPair().getValue("methodOverlap").jsonPrimitive.double <
                original.onlyPair().getValue("methodOverlap").jsonPrimitive.double,
        )
        assertEquals(original.edgeDescriptors("calculate"), optimized.edgeDescriptors("calculate"))
    }

    @Test fun branchingTextReducesOverlapBetweenProjectsWithTheSameRoleAndProgram() {
        val (original, optimized) = compareVersions(
            listOf(
                project(27).copy(packageCount = 48, profileId = 45, seed = "7650977492772279606"),
                project(89).copy(packageCount = 48, profileId = 21, seed = "7720312720806296453"),
            ),
            baselineVersion = 12,
        )
        assertTrue(
            optimized.onlyPair().getValue("methodOverlap").jsonPrimitive.double <
                original.onlyPair().getValue("methodOverlap").jsonPrimitive.double,
        )
        assertEquals(
            original.getValue("archives").jsonArray.map { it.jsonObject.getValue("callEdgeHistogram") },
            optimized.getValue("archives").jsonArray.map { it.jsonObject.getValue("callEdgeHistogram") },
        )
    }

    @Test fun profiledLongAndPredicateMethodsReduceOverlapAcrossRolesAndTopologies() {
        val (original, optimized) = compareVersions(
            listOf(
                project(27).copy(packageCount = 48, profileId = 45, seed = "7650977492772279606"),
                project(32).copy(packageCount = 48, profileId = 165, seed = "3872409039075771126"),
            ),
            baselineVersion = 13,
        )
        assertTrue(
            optimized.onlyPair().getValue("methodOverlap").jsonPrimitive.double <
                original.onlyPair().getValue("methodOverlap").jsonPrimitive.double,
        )
        assertEquals(
            original.getValue("archives").jsonArray.map { it.jsonObject.getValue("callEdgeHistogram") },
            optimized.getValue("archives").jsonArray.map { it.jsonObject.getValue("callEdgeHistogram") },
        )
    }

    @Test fun longProgramsReduceOverlapForMatchingRolesAndTopologies() {
        val (original, optimized) = compareVersions(
            listOf(
                project(27).copy(packageCount = 48, profileId = 45, seed = "7650977492772279606"),
                project(59).copy(packageCount = 48, profileId = 63, seed = "-8084188783784301055"),
            ),
            baselineVersion = 14,
        )
        assertTrue(
            optimized.onlyPair().getValue("methodOverlap").jsonPrimitive.double <
                original.onlyPair().getValue("methodOverlap").jsonPrimitive.double,
        )
        assertEquals(
            original.getValue("archives").jsonArray.map { it.jsonObject.getValue("callEdgeHistogram") },
            optimized.getValue("archives").jsonArray.map { it.jsonObject.getValue("callEdgeHistogram") },
        )
    }

    @Test fun textTransformsReduceOverlapWhenInputOrTopologyDiffers() {
        val (original, optimized) = compareVersions(
            listOf(
                project(107).copy(packageCount = 48, profileId = 7, seed = "-3338456444447976697"),
                project(110).copy(packageCount = 48, profileId = 19, seed = "2936120429664767355"),
                project(111).copy(packageCount = 48, profileId = 3),
                project(112).copy(packageCount = 48, profileId = 1),
            ),
            baselineVersion = 15,
        )
        val before = original.getValue("pairs").jsonArray
        val after = optimized.getValue("pairs").jsonArray
        assertEquals(before.size, after.size)
        for ((baseline, revised) in before.zip(after)) {
            val left = baseline.jsonObject.getValue("left")
            val right = baseline.jsonObject.getValue("right")
            assertEquals(left, revised.jsonObject.getValue("left"))
            assertEquals(right, revised.jsonObject.getValue("right"))
            val previousOverlap = baseline.jsonObject.getValue("methodOverlap").jsonPrimitive.double
            val revisedOverlap = revised.jsonObject.getValue("methodOverlap").jsonPrimitive.double
            if (right.jsonPrimitive.content == "project-112") {
                assertEquals(previousOverlap, revisedOverlap, "$left and $right share a new program structure")
            } else {
                assertTrue(revisedOverlap < previousOverlap, "$left and $right retain overlapping text transformations")
            }
        }
        assertEquals(
            original.getValue("archives").jsonArray.map { it.jsonObject.getValue("callEdgeHistogram") },
            optimized.getValue("archives").jsonArray.map { it.jsonObject.getValue("callEdgeHistogram") },
        )
    }

    @Test fun longOperandsReduceOverlapWhenInputDiffers() {
        val (original, optimized) = compareVersions(
            listOf(
                project(101).copy(packageCount = 48, profileId = 125, seed = "7818733410281058836"),
                project(105).copy(packageCount = 48, profileId = 93, seed = "114720241181731020"),
            ),
            baselineVersion = 16,
        )
        assertTrue(
            optimized.onlyPair().getValue("methodOverlap").jsonPrimitive.double <
                original.onlyPair().getValue("methodOverlap").jsonPrimitive.double,
        )
        assertEquals(
            original.getValue("archives").jsonArray.map { it.jsonObject.getValue("callEdgeHistogram") },
            optimized.getValue("archives").jsonArray.map { it.jsonObject.getValue("callEdgeHistogram") },
        )
    }

    @Test fun listAndTextFiltersReduceOverlapForSharedRoleGroups() {
        val (original, optimized) = compareVersions(
            listOf(
                project(111).copy(packageCount = 48, profileId = 187, seed = "-1533011082052786308"),
                project(115).copy(packageCount = 48, profileId = 231, seed = "-4669585263890473821"),
                project(117).copy(packageCount = 48, profileId = 14, seed = "-6581364118831192646"),
                project(118).copy(packageCount = 48, profileId = 142, seed = "3013553759431270410"),
                project(119).copy(packageCount = 48, profileId = 228, seed = "8715048380616696794"),
                project(120).copy(packageCount = 48, profileId = 62, seed = "3735753182969360924"),
            ),
            baselineVersion = 17,
        )
        fun overlap(report: JsonObject, left: String, right: String) = report.getValue("pairs").jsonArray.map { it.jsonObject }.single {
            it.getValue("left").jsonPrimitive.content == left && it.getValue("right").jsonPrimitive.content == right
        }.getValue("methodOverlap").jsonPrimitive.double
        for ((left, right) in listOf("project-111" to "project-118", "project-115" to "project-119")) {
            assertTrue(overlap(optimized, left, right) < overlap(original, left, right), "$left and $right retain overlapping filter bodies")
        }
        assertEquals(0.0, overlap(optimized, "project-117", "project-120"))
        assertEquals(
            original.getValue("archives").jsonArray.map { it.jsonObject.getValue("callEdgeHistogram") },
            optimized.getValue("archives").jsonArray.map { it.jsonObject.getValue("callEdgeHistogram") },
        )
    }

    @Test fun singleTextFieldSelectionSeparatesTheIntegerTextAndTextBranchingProfiles() {
        val (original, optimized) = compareVersions(
            listOf(
                project(145).copy(packageName = "com.samples.project145", packageCount = 48, profileId = 143, seed = "572092734568408078"),
                project(148).copy(packageName = "com.samples.project148", packageCount = 48, profileId = 189, seed = "-8982879706596142475"),
            ),
            baselineVersion = 24,
        )
        assertTrue(original.onlyPair().getValue("methodOverlap").jsonPrimitive.double > 0)
        assertEquals(0.0, optimized.onlyPair().getValue("methodOverlap").jsonPrimitive.double)
        assertEquals(
            original.getValue("archives").jsonArray.map { it.jsonObject.getValue("callEdgeHistogram") },
            optimized.getValue("archives").jsonArray.map { it.jsonObject.getValue("callEdgeHistogram") },
        )
    }

    @Test fun optionalDtoComparisonAndProfiledStepsAndCountsReduceOverlap() {
        val projects = listOf(
            Triple(123, 46, "-8654300371224967931"),
            Triple(128, 33, "1093889447413295033"),
            Triple(134, 209, "-33652281574500874"),
            Triple(135, 47, "2970281690415676761"),
            Triple(138, 213, "-5859823114949751828"),
            Triple(140, 35, "-8457872342134678607"),
            Triple(143, 232, "3830388244140617973"),
            Triple(149, 236, "-7894369553757721808"),
        ).map { (id, profile, seed) ->
            project(id).copy(packageName = "com.samples.project$id", packageCount = 48, profileId = profile, seed = seed)
        }
        val (original, optimized) = compareVersions(projects, baselineVersion = 25)
        fun overlaps(report: JsonObject) = report.getValue("pairs").jsonArray.associate { row ->
            val pair = row.jsonObject
            val left = pair.getValue("left").jsonPrimitive.content
            val right = pair.getValue("right").jsonPrimitive.content
            (left to right) to pair.getValue("methodOverlap").jsonPrimitive.double
        }
        val before = overlaps(original)
        val after = overlaps(optimized)
        for ((left, right) in listOf(123 to 128, 134 to 138, 143 to 149, 128 to 140, 123 to 135)) {
            val key = "project-$left" to "project-$right"
            assertTrue(after.getValue(key) < before.getValue(key), "$key retain overlapping method bodies")
        }
        assertEquals(
            original.getValue("archives").jsonArray.map { it.jsonObject.getValue("callEdgeHistogram") },
            optimized.getValue("archives").jsonArray.map { it.jsonObject.getValue("callEdgeHistogram") },
        )
    }

    private fun assertCallOverlapRemoved(projects: List<JunkProject>, baselineVersion: Int) {
        val (original, optimized) = compareVersions(projects, baselineVersion)
        assertTrue(original.onlyPair().getValue("callPatternOverlap").jsonPrimitive.double > 0.0)
        assertEquals(0.0, optimized.onlyPair().getValue("callPatternOverlap").jsonPrimitive.double)
    }

    private fun JsonObject.onlyPair() = getValue("pairs").jsonArray.single().jsonObject

    private fun JsonObject.edgeDescriptors(method: String): List<Set<String>> = getValue("archives").jsonArray.map { archive ->
        archive.jsonObject.getValue("callEdgeHistogram").jsonArray.mapNotNull { edge ->
            val row = edge.jsonObject
            if (row.getValue("calleeNames").jsonArray.any { it.jsonPrimitive.content == method }) {
                row.getValue("descriptor").jsonPrimitive.content
            } else null
        }.toSet()
    }

    private fun compareVersions(projects: List<JunkProject>, baselineVersion: Int): Pair<JsonObject, JsonObject> {
        val root = Files.createTempDirectory("toolkit-project-component-test-").toFile()
        try {
            val source = root.resolve("projects.json")
            source.writeText(Json.encodeToString(JunkProjectManifest(projects, generatorVersion = baselineVersion)))
            val original = JunkProjectGenerator.generate(source, root.resolve("original"), compare = true)
            val optimized = JunkProjectGenerator.generate(original.manifest, root.resolve("optimized"),
                compare = true, generatorVersion = baselineVersion + 1)
            return Json.parseToJsonElement(requireNotNull(original.comparison).readText()).jsonObject to
                Json.parseToJsonElement(requireNotNull(optimized.comparison).readText()).jsonObject
        } finally {
            root.deleteRecursively()
        }
    }

    private fun project(index: Int) = JunkProject("project-$index", "com.fixture.project$index", 2, 1, "fixture_")
}
