package org.tool.kit.tests.data

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import java.io.File
import java.util.jar.JarInputStream
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.slf4j.LoggerFactory
import org.tool.kit.data.generator.JunkGenerationPolicy
import org.tool.kit.data.generator.JunkResourceBudget
import org.tool.kit.data.source.JvmJunkCodeDataSource
import org.tool.kit.domain.junk.GenerateJunkCodeRequest
import org.tool.kit.domain.junk.JunkConfiguration
import kotlin.test.*

class JunkGenerationLoggingIntegrationTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun productionDataSourceLogsEveryPublishedArchiveAndCompleteBatchMeasurementsWithoutReportFiles() = runBlocking {
        val work = temporary.newFolder("work")
        val output = temporary.newFolder("output")
        val logger = LoggerFactory.getLogger("JunkGeneration") as Logger
        val previousLevel = logger.level
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        logger.level = Level.INFO
        logger.addAppender(appender)
        try {
            val policy = JunkGenerationPolicy(seed = 44, batchResourceTotal = 5,
                resources = JunkResourceBudget(total = 3, newResourceProbability = 1.0))
            val source = JvmJunkCodeDataSource(work, Dispatchers.IO, policy = policy)
            val result = source.generate(GenerateJunkCodeRequest(output.path,
                JunkConfiguration.Multi("batch", 2, 1, 1, 1, 1)))
            val files = result.archivePaths.map(::File)
            assertEquals(files.map { it.name }.toSet(), File(result.outputPath).list()!!.toSet())
            assertEquals(setOf("batch"), output.list()!!.toSet())
            assertTrue(work.listFiles()!!.isEmpty())

            val messages = appender.list.map { it.formattedMessage }
            val archiveMessages = messages.filter { it.startsWith("Android AAR 生成完成\n") }
            val batchMessages = messages.filter { it.startsWith("Android AAR 批量生成完成\n") }
            assertEquals(2, archiveMessages.size, "Each AAR must log once after batch publication")
            assertEquals(1, batchMessages.size)
            assertEquals(files.map { it.absolutePath }.toSet(), archiveMessages.map {
                it.substringAfter("输出文件：").substringBefore('\n')
            }.toSet())
            assertEquals(File(result.outputPath).canonicalPath,
                batchMessages.single().substringAfter("输出目录：").substringBefore('\n'))

            fun report(message: String): JsonObject {
                assertTrue(message.contains("完整统计 JSON：\n"))
                return Json.parseToJsonElement(message.substringAfter("完整统计 JSON：\n")).jsonObject
            }
            fun JsonObject.number(key: String): Long = getValue(key).jsonPrimitive.long
            val archiveReports = archiveMessages.map(::report)
            val batch = report(batchMessages.single())
            val aggregate = batch.getValue("aggregate").jsonObject
            assertEquals(2L, aggregate.number("measuredAars"))
            assertEquals(0L, aggregate.number("unmeasuredAars"))
            assertEquals(result.totalBytes, batch.number("archiveBytes"))
            val code = aggregate.getValue("code").jsonObject
            for (field in listOf("classes", "methods", "fields", "classBytes")) {
                assertTrue(code.number(field) > 0L)
                assertEquals(archiveReports.sumOf { it.getValue("code").jsonObject.number(field) }, code.number(field))
            }
            var actualClasses = 0L
            var actualLayouts = 0L
            files.forEach { file -> ZipFile(file).use { zip ->
                actualLayouts += zip.entries().asSequence().count { it.name.startsWith("res/layout/") }
                JarInputStream(zip.getInputStream(zip.getEntry("classes.jar"))).use { jar ->
                    while (true) {
                        val entry = jar.nextJarEntry ?: break
                        if (entry.name.endsWith(".class")) actualClasses++
                    }
                }
            } }
            assertEquals(actualClasses, code.number("classes"))
            assertEquals(actualLayouts, aggregate.getValue("layouts").jsonObject.number("count"))
            val generated = aggregate.getValue("resources").jsonObject.number("optionalGeneratedCount")
            assertEquals(archiveReports.sumOf { archive ->
                archive.getValue("resources").jsonObject.getValue("generated").jsonObject.values.sumOf { it.jsonPrimitive.long }
            }, generated)
            assertTrue(generated in 0L..5L)
        } finally {
            logger.detachAppender(appender)
            logger.level = previousLevel
            appender.stop()
        }
    }
}
