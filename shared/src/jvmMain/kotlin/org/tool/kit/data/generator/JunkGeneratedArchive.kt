package org.tool.kit.data.generator

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Carries statistics to the batch coordinator without publishing an extra report file. */
data class JunkGeneratedArchive(val file: File, val report: JunkGenerationReport? = null)

internal fun publishJunkFiles(files: List<Pair<File, File>>) {
    val staged = mutableListOf<Pair<java.nio.file.Path, java.nio.file.Path>>()
    val backups = mutableListOf<Pair<java.nio.file.Path, java.nio.file.Path>>()
    val installed = mutableListOf<java.nio.file.Path>()
    var committed = false
    try {
        files.forEach { (source, destination) ->
            val temp = Files.createTempFile(destination.parentFile.toPath(), ".${destination.name}", ".part")
            staged += temp to destination.toPath()
            Files.copy(source.toPath(), temp, StandardCopyOption.REPLACE_EXISTING)
        }
        staged.forEach { (_, destination) ->
            if (Files.exists(destination)) {
                require(Files.isRegularFile(destination)) { "Output is not a regular file: $destination" }
                val backup = Files.createTempFile(destination.parent, ".junk-backup-", ".part")
                Files.move(destination, backup, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                backups += backup to destination
            }
        }
        staged.forEach { (source, destination) ->
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            installed.add(destination)
        }
        committed = true
    } catch (error: Throwable) {
        installed.forEach { Files.deleteIfExists(it) }
        backups.asReversed().forEach { (backup, destination) ->
            try { Files.move(backup, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
            catch (restore: Exception) { error.addSuppressed(restore) } // Preserve a backup if restoration itself fails.
        }
        throw error
    } finally {
        staged.forEach { Files.deleteIfExists(it.first) }
        if (committed) backups.forEach { Files.deleteIfExists(it.first) }
    }
}
