package `fun`.nightshift.launcher.client.crypto

import java.nio.file.Path
import java.time.Duration

/**
 * Cleans up temporary directories created by ProtectedModsPreparer.
 * 
 * This component is responsible for:
 * 1. Deleting specific temporary directories after game exit
 * 2. Scanning and deleting old temporary directories on launcher startup
 * 3. Handling locked files and permission errors gracefully
 * 
 * The temporary directory pattern is: `%TEMP%\.ns-*`
 * 
 * Error handling philosophy:
 * - Best-effort cleanup: if one file/directory fails to delete, continue with others
 * - Log all errors at WARN level
 * - Never throw exceptions (cleanup failures should not block launcher operation)
 * 
 * Thread safety:
 * - Methods should be called on background threads (Dispatchers.IO)
 * - Safe for concurrent calls from different launcher instances
 */
interface TempDirCleaner {
    /**
     * Deletes a specific temporary directory recursively.
     * 
     * This method is called after game exit to clean up the temporary mods directory
     * created for that specific game session.
     * 
     * Process:
     * 1. Use `Files.walk()` to traverse directory tree in depth-first order
     * 2. Delete files before directories (bottom-up deletion)
     * 3. Handle locked files gracefully (log WARN and continue)
     * 4. Handle permission errors gracefully (log WARN and continue)
     * 
     * Error handling:
     * - If a file is locked (e.g., by antivirus), log WARN and continue
     * - If permission is denied, log WARN and continue
     * - If directory doesn't exist, log DEBUG and return (already cleaned)
     * - Never throw exceptions
     * 
     * @param tempDir Path to temporary directory to delete (e.g., `C:\Users\...\Temp\.ns-{UUID}\`)
     */
    fun cleanup(tempDir: Path)
    
    /**
     * Scans %TEMP% and deletes old .ns-* directories.
     * 
     * This method is called on launcher startup to clean up temporary directories
     * left behind by crashed game sessions or killed launcher instances.
     * 
     * Process:
     * 1. Get temp directory from `System.getProperty("java.io.tmpdir")`
     * 2. List all directories matching `.ns-*` pattern
     * 3. For each directory:
     *    - Get last modified time using `Files.getLastModifiedTime()`
     *    - Calculate age (current time - last modified time)
     *    - If older than ageThreshold: call `cleanup()` on it
     *    - If newer than ageThreshold: skip (may be active game session)
     * 4. Log INFO with count of deleted directories
     * 
     * Age threshold rationale:
     * - Default 1 hour is sufficient for most game sessions
     * - Protects active game sessions from being cleaned up
     * - Handles case where multiple launcher instances are running
     * 
     * Error handling:
     * - If temp directory scan fails, log ERROR and abort cleanup
     * - If individual directory cleanup fails, log WARN and continue with next
     * - Never throw exceptions
     * 
     * @param ageThreshold Directories older than this are deleted (default: 1 hour)
     */
    fun cleanupOldDirectories(ageThreshold: Duration = Duration.ofHours(1))
}
