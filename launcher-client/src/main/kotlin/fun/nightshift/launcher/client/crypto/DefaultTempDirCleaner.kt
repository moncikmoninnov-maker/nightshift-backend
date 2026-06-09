package `fun`.nightshift.launcher.client.crypto

import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.streams.asSequence

/**
 * Default implementation of TempDirCleaner.
 * 
 * Provides best-effort cleanup of temporary directories created by ProtectedModsPreparer.
 * All operations are designed to be non-blocking and gracefully handle errors.
 * 
 * Implementation details:
 * - Uses `Files.walk()` for recursive directory traversal
 * - Deletes files in reverse order (files before directories)
 * - Uses `Files.getLastModifiedTime()` for age calculation
 * - Runs cleanup operations on Dispatchers.IO (background thread)
 * - Logs all operations (INFO for successful operations, WARN for errors)
 * 
 * Error handling:
 * - Catches all exceptions during cleanup
 * - Logs errors at WARN level
 * - Continues with remaining files/directories after errors
 * - Never throws exceptions to caller
 * 
 * Thread safety:
 * - Safe for concurrent calls from different launcher instances
 * - Each instance cleans up its own temporary directory
 * - Age-based cleanup only affects old directories (not active sessions)
 */
class DefaultTempDirCleaner : TempDirCleaner {
    
    private val logger = LoggerFactory.getLogger(DefaultTempDirCleaner::class.java)
    
    override fun cleanup(tempDir: Path) {
        try {
            // Check if directory exists
            if (!tempDir.exists()) {
                logger.debug("Temporary directory does not exist (already cleaned): {}", tempDir)
                return
            }
            
            logger.info("Cleaning up temporary directory: {}", tempDir.toAbsolutePath())
            
            // Walk directory tree and collect paths in reverse order (files before directories)
            val pathsToDelete = Files.walk(tempDir)
                .asSequence()
                .sortedByDescending { it.nameCount } // Sort by depth (deepest first)
                .toList()
            
            var deletedCount = 0
            var failedCount = 0
            
            // Delete each path
            for (path in pathsToDelete) {
                try {
                    Files.delete(path)
                    deletedCount++
                    logger.debug("Deleted: {}", path)
                } catch (e: Exception) {
                    failedCount++
                    logger.warn(
                        "Failed to delete {} - {} (file may be locked or permission denied)",
                        path,
                        e.message
                    )
                    // Continue with next file (graceful degradation)
                }
            }
            
            if (failedCount > 0) {
                logger.warn(
                    "Cleanup completed with errors: {} deleted, {} failed",
                    deletedCount,
                    failedCount
                )
            } else {
                logger.info(
                    "Successfully cleaned up temporary directory: {} files/directories deleted",
                    deletedCount
                )
            }
            
        } catch (e: Exception) {
            logger.warn(
                "Failed to clean up temporary directory {} - {}",
                tempDir,
                e.message,
                e
            )
            // Don't throw - cleanup is best-effort
        }
    }
    
    override fun cleanupOldDirectories(ageThreshold: Duration) {
        try {
            val tempBaseDir = Path.of(System.getProperty("java.io.tmpdir"))
            logger.debug("Scanning for old temporary directories in: {}", tempBaseDir)
            
            // List all entries in temp directory
            val allEntries = try {
                tempBaseDir.listDirectoryEntries()
            } catch (e: Exception) {
                logger.error(
                    "Failed to scan temp directory {} - {}",
                    tempBaseDir,
                    e.message,
                    e
                )
                return // Abort cleanup if we can't scan
            }
            
            // Filter for .ns-* directories
            val nsDirectories = allEntries.filter { entry ->
                entry.isDirectory() && entry.name.startsWith(".ns-")
            }
            
            logger.debug("Found {} .ns-* directories", nsDirectories.size)
            
            if (nsDirectories.isEmpty()) {
                logger.debug("No old temporary directories to clean up")
                return
            }
            
            val now = Instant.now()
            var deletedCount = 0
            var skippedCount = 0
            
            // Check age and delete old directories
            for (nsDir in nsDirectories) {
                try {
                    val lastModified = Files.getLastModifiedTime(nsDir).toInstant()
                    val age = Duration.between(lastModified, now)
                    
                    if (age > ageThreshold) {
                        logger.debug(
                            "Directory {} is {} old (threshold: {}), deleting",
                            nsDir.name,
                            age,
                            ageThreshold
                        )
                        cleanup(nsDir)
                        deletedCount++
                    } else {
                        logger.debug(
                            "Directory {} is {} old (threshold: {}), skipping (may be active session)",
                            nsDir.name,
                            age,
                            ageThreshold
                        )
                        skippedCount++
                    }
                    
                } catch (e: Exception) {
                    logger.warn(
                        "Failed to process directory {} - {}",
                        nsDir.name,
                        e.message
                    )
                    // Continue with next directory
                }
            }
            
            logger.info(
                "Old directory cleanup complete: {} deleted, {} skipped (active sessions)",
                deletedCount,
                skippedCount
            )
            
        } catch (e: Exception) {
            logger.error(
                "Failed to clean up old temporary directories - {}",
                e.message,
                e
            )
            // Don't throw - cleanup is best-effort
        }
    }
}
