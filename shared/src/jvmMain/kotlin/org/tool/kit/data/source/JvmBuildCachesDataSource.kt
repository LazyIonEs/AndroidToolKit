package org.tool.kit.data.source

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.tool.kit.domain.cleaner.*
import org.tool.kit.domain.repository.BuildCachesRepository
import java.io.IOException
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import kotlin.coroutines.CoroutineContext

/** NIO traversal never follows links, including while measuring and deleting matched directories. */
class JvmBuildCachesDataSource(private val io: CoroutineDispatcher) : BuildCachesRepository {
    override fun scan(root: String) = scan(CleanerScanRequest.from(root, CleanerRuleConfig()))

    override fun scan(request: CleanerScanRequest, onIssue: (String) -> Unit) = flow {
        val context = currentCoroutineContext()
        val root = Paths.get(request.root).toAbsolutePath().normalize()
        require(!Files.isSymbolicLink(root) && Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS))
        val realRoot = root.toRealPath()
        val snapshot = request.copy(root = realRoot.toString(), rules = request.rules.map { it.copy(conditions = it.conditions.toList()) })
        require(snapshot.maxDepth in 1..50 && snapshot.rules.isNotEmpty() && snapshot.rules.all { validateRule(it).isEmpty() })
        // A suspendable traversal keeps backpressure and cancellation at every directory entry.
        suspend fun visit(directory: Path, depth: Int) {
            context.ensureActive()
            try {
                Files.newDirectoryStream(directory).use { entries ->
                    for (path in entries) {
                        context.ensureActive()
                        try {
                            val attrs = Files.readAttributes(path, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
                            if (attrs.isSymbolicLink || (!attrs.isDirectory && !attrs.isRegularFile)) continue
                            if (!snapshot.includeHidden && Files.isHidden(path)) continue
                            if (!safePath(realRoot, path)) { onIssue(path.toString()); continue }
                            val relative = normalizeRelativePath(realRoot.relativize(path).toString())
                            val target = if (attrs.isDirectory) CleanerTarget.DIRECTORY else CleanerTarget.FILE
                            val matched = matchingCleanerRules(snapshot.rules, target, path.fileName.toString(), relative, if (attrs.isRegularFile) attrs.size() else null)
                            if (matched.isNotEmpty()) {
                                val size = if (attrs.isDirectory) directorySize(path, context, onIssue) else attrs.size()
                                context.ensureActive()
                                emit(BuildDirectory(realRoot.toString(), path.toString(), relative, size,
                                    attrs.lastModifiedTime().toMillis(), attrs.isDirectory, true,
                                    matched.map { it.id }.toSet(), matched.map { it.name }, matched.any { it.defaultSelected }, snapshot,
                                    attrs.fileKey()?.toString()))
                            } else if (attrs.isDirectory && depth < snapshot.maxDepth) visit(path, depth + 1)
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: IOException) { onIssue(path.toString()) }
                        catch (_: SecurityException) { onIssue(path.toString()) }
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: IOException) { if (directory == realRoot) throw failure else onIssue(directory.toString()) }
            catch (failure: DirectoryIteratorException) { if (directory == realRoot) throw failure else onIssue(directory.toString()) }
            catch (failure: SecurityException) { if (directory == realRoot) throw failure else onIssue(directory.toString()) }
        }
        visit(realRoot, 1)
    }.flowOn(io).buffer(0)

    private fun directorySize(path: Path, context: CoroutineContext, onIssue: (String) -> Unit): Long {
        var bytes = 0L
        Files.walkFileTree(path, object : SimpleFileVisitor<Path>() {
            private fun count(attrs: BasicFileAttributes) {
                context.ensureActive()
                bytes = if (Long.MAX_VALUE - bytes < attrs.size()) Long.MAX_VALUE else bytes + attrs.size()
            }
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult { count(attrs); return FileVisitResult.CONTINUE }
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                context.ensureActive(); if (!attrs.isSymbolicLink) count(attrs); return FileVisitResult.CONTINUE
            }
            override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult { context.ensureActive(); onIssue(file.toString()); return FileVisitResult.CONTINUE }
            override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult { context.ensureActive(); if (exc != null) onIssue(dir.toString()); return FileVisitResult.CONTINUE }
        })
        return bytes
    }

    private fun safePath(root: Path, path: Path): Boolean {
        if (path == root || path.parent == null || !path.startsWith(root)) return false
        var current = root
        if (Files.isSymbolicLink(current) || current.toRealPath() != root) return false
        for (part in root.relativize(path)) {
            current = current.resolve(part)
            if (Files.isSymbolicLink(current)) return false
        }
        val realPath = path.toRealPath()
        // Retain containment and also reject aliases such as directory junctions.
        return realPath.startsWith(root) && realPath == path
    }

    override suspend fun delete(directory: BuildDirectory): DeleteBuildCacheResult = withContext(io) {
        val context = currentCoroutineContext()
        context.ensureActive()
        val path = Paths.get(directory.path).toAbsolutePath().normalize()
        var safetyFailure = false
        var revalidated = false
        val deleted = try {
            val request = directory.request
            val root = Paths.get(directory.scanRoot).toAbsolutePath().normalize()
            val attrs = Files.readAttributes(path, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
            val target = if (attrs.isDirectory) CleanerTarget.DIRECTORY else CleanerTarget.FILE
            val valid = request != null && request.root == root.toString() && safePath(root, path) &&
                !attrs.isSymbolicLink && (attrs.isDirectory || attrs.isRegularFile) && attrs.isDirectory == directory.isDirectory &&
                (directory.fileKey == null || directory.fileKey == attrs.fileKey()?.toString()) &&
                matchingCleanerRules(request.rules, target, path.fileName.toString(), root.relativize(path).toString(),
                    if (attrs.isRegularFile) attrs.size() else null).map { it.id }.toSet() == directory.matchedRuleIds && directory.matchedRuleIds.isNotEmpty()
            if (!valid) { safetyFailure = true; false }
            else {
                revalidated = true
                Files.walkFileTree(path, object : SimpleFileVisitor<Path>() {
                    override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                        context.ensureActive()
                        if (!safePath(root, dir)) { safetyFailure = true; throw IOException("Path changed") }
                        return FileVisitResult.CONTINUE
                    }
                    override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                        context.ensureActive()
                        // Links inside a selected directory may be unlinked, but their target is never visited.
                        if (!safePath(root, file.parent) && file.parent != root) { safetyFailure = true; throw IOException("Path changed") }
                        Files.delete(file); return FileVisitResult.CONTINUE
                    }
                    override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                        context.ensureActive(); if (exc != null) throw exc
                        if (!safePath(root, dir)) { safetyFailure = true; throw IOException("Path changed") }
                        Files.delete(dir); return FileVisitResult.CONTINUE
                    }
                })
                true
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: NoSuchFileException) { safetyFailure = true; false }
        catch (_: Exception) { if (!revalidated) safetyFailure = true; false }
        context.ensureActive()
        DeleteBuildCacheResult(directory, deleted, Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS),
            Files.exists(path, LinkOption.NOFOLLOW_LINKS), safetyFailure)
    }
}
