package org.tool.kit.tests.data

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.slf4j.LoggerFactory
import org.tool.kit.data.generator.JunkGeneratedArchive
import org.tool.kit.data.generator.JunkGenerationPolicy
import org.tool.kit.data.generator.JunkGenerationReport
import org.tool.kit.data.generator.MultiAarGenerator
import kotlin.test.*

class JunkBatchReportTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun observerFailureWarnsButReturnsTheSuccessfullyReplacedBatch() = runBlocking {
        val work = temporary.newFolder("work")
        val output = temporary.newFolder("output")
        val batch = File(output, "batch").apply { mkdirs() }
        val archive = File(batch, "replacement.aar").apply { writeText("previous complete archive") }
        File(batch, "obsolete.aar").writeText("obsolete archive")
        val notificationFailure = IllegalStateException("observer failure")
        val logger = LoggerFactory.getLogger("JunkGeneration") as Logger
        val previousLevel = logger.level
        val previousAdditive = logger.isAdditive
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        logger.level = Level.WARN
        logger.isAdditive = false
        logger.addAppender(appender)
        try {
            var notifications = 0
            val files = MultiAarGenerator.generate(work.path, output.path, "batch", 1, 1, 1, 1, 1,
                policy = JunkGenerationPolicy(seed = 44), onReport = {
                    notifications++
                    assertEquals("new complete archive", archive.readText())
                    throw notificationFailure
                }) { _, staging ->
                JunkGeneratedArchive(File(staging, archive.name).apply { writeText("new complete archive") })
            }
            assertEquals(1, notifications)
            assertEquals(listOf(archive.canonicalFile), files)
            assertEquals("new complete archive", files.single().readText())
            assertEquals(listOf(archive.name), batch.list()!!.toList())
            assertEquals(listOf(batch.name), output.list()!!.toList(), "Staging and backup directories must be removed")
            assertTrue(work.listFiles()!!.isEmpty())
            val warning = appender.list.single()
            assertEquals(Level.WARN, warning.level)
            assertEquals("JunkGeneration", warning.loggerName)
            assertTrue(warning.formattedMessage.contains("已成功发布，统计通知失败"))
            assertTrue(warning.formattedMessage.contains(batch.canonicalPath))
            assertEquals(notificationFailure.javaClass.name, warning.throwableProxy.className)
            assertEquals(notificationFailure.message, warning.throwableProxy.message)
        } finally {
            logger.detachAppender(appender)
            logger.level = previousLevel
            logger.isAdditive = previousAdditive
            appender.stop()
        }
    }

    @Test fun batchReportSumsInMemoryMeasurementsAndWeightsReuseRatesWhileSkippingMissingReports() = runBlocking {
        val callbacks = AtomicInteger()
        var capturedReport: JunkGenerationReport? = null
        val files = MultiAarGenerator.generate(temporary.root.path, temporary.root.path, "batch", 3, 1, 1, 1, 1,
            policy = JunkGenerationPolicy(seed = 44), onReport = { report ->
                assertNull(capturedReport, "A batch must publish its summary exactly once")
                val published = temporary.root.resolve("batch").listFiles()!!.toList()
                assertEquals(3, published.size)
                assertTrue(published.all { it.isFile && it.extension == "aar" }, "Only completed AARs are published")
                capturedReport = report
            }) { config, output ->
            val archive = File(output, "${config.packageName}.aar").apply { writeText("fixture") }
            val index = callbacks.incrementAndGet()
            val report = if (index <= 2) JunkGenerationReport(mapOf(
                "code" to mapOf("classes" to index, "methods" to index * 4, "fields" to index * 2,
                    "classBytes" to index * 100, "classRoles" to mapOf("helper" to index)),
                "layouts" to mapOf("count" to index, "bytes" to index * 30),
                "resources" to mapOf("generated" to mapOf("drawable" to index),
                    "requests" to mapOf("drawable" to if (index == 1) 5 else 25),
                    "reused" to mapOf("drawable" to if (index == 1) 1 else 8),
                    "optionalFiles" to index, "optionalBytes" to index * 20, "valuesEntries" to 0,
                    "ids" to index, "assets" to 0, "assetBytes" to 0, "metadataResourceFiles" to 0),
                "sizes" to mapOf("aarBytes" to archive.length(), "classesJarBytes" to index * 60,
                    "classBytes" to index * 100, "layoutBytes" to index * 30,
                    "compressedEntryBytes" to mapOf("classesJar" to index * 50)),
            )) else null
            JunkGeneratedArchive(archive, report)
        }
        assertEquals(3, files.size)
        assertEquals(files.map { it.name }.toSet(), temporary.root.resolve("batch").list()!!.toSet())
        val report = Json.parseToJsonElement(assertNotNull(capturedReport).toJson()).jsonObject
        val aggregate = report.getValue("aggregate").jsonObject
        assertEquals(2, aggregate.getValue("measuredAars").jsonPrimitive.int)
        assertEquals(1, aggregate.getValue("unmeasuredAars").jsonPrimitive.int)
        assertEquals(3, aggregate.getValue("code").jsonObject.getValue("classes").jsonPrimitive.int)
        assertEquals(12, aggregate.getValue("code").jsonObject.getValue("methods").jsonPrimitive.int)
        assertEquals(6, aggregate.getValue("code").jsonObject.getValue("fields").jsonPrimitive.int)
        assertEquals(300, aggregate.getValue("code").jsonObject.getValue("classBytes").jsonPrimitive.int)
        assertEquals(3, aggregate.getValue("code").jsonObject.getValue("classRoles").jsonObject.getValue("helper").jsonPrimitive.int)
        assertEquals(90, aggregate.getValue("layouts").jsonObject.getValue("bytes").jsonPrimitive.int)
        val resources = aggregate.getValue("resources").jsonObject
        assertEquals(3, resources.getValue("optionalGeneratedCount").jsonPrimitive.int)
        assertTrue(resources.getValue("optionalGeneratedCount").jsonPrimitive.int <= report.getValue("optionalResourceBudget").jsonPrimitive.int)
        assertEquals(0.3, resources.getValue("requestReuseRate").jsonPrimitive.double, 0.000001)
        assertEquals(0.75, resources.getValue("successfulRequestReuseRate").jsonPrimitive.double, 0.000001)
        assertEquals(0.3, resources.getValue("requestReuseRateByType").jsonObject.getValue("drawable").jsonPrimitive.double, 0.000001)
        val sizes = aggregate.getValue("sizes").jsonObject
        assertEquals(180, sizes.getValue("classesJarBytes").jsonPrimitive.int)
        assertEquals(150, sizes.getValue("resourceBytes").jsonPrimitive.int)
        assertEquals(150, sizes.getValue("compressedEntryBytes").jsonObject.getValue("classesJar").jsonPrimitive.int)
    }
}
