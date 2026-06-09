package `fun`.nightshift.launcher.client.crypto

import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.DosFileAttributes
import java.util.UUID
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes

/**
 * Default implementation of ProtectedModsPreparer.
 * 
 * Creates a unique temporary directory with UUID-based naming, decrypts premium mods
 * from the encrypted cache, and handles errors gracefully.
 * 
 * Implementation details:
 * - Uses `System.getProperty("java.io.tmpdir")` for temp directory location
 * - Generates UUID v4 using `java.util.UUID.randomUUID()`
 * - Sets `dos:hidden` attribute on Windows (catches exceptions if not supported)
 * - Scans for files ending with `.enc` extension
 * - Uses ModDecryptor for decryption
 * - Logs all operations (DEBUG for each file, ERROR for failures, INFO for directory creation)
 * 
 * Error handling:
 * - Graceful degradation: if one file fails to decrypt, continue with others
 * - Log all errors but don't throw exceptions for individual file failures
 * - If hidden attribute fails, log WARN and continue
 * 
 * @param modDecryptor The decryptor to use for decrypting .enc files
 */
class DefaultProtectedModsPreparer(
    private val modDecryptor: ModDecryptor
) : ProtectedModsPreparer {
    
    private val logger = LoggerFactory.getLogger(DefaultProtectedModsPreparer::class.java)
    
    override fun prepare(encryptedCacheDir: Path, sessionToken: String): Path {
        // Generate random UUID for directory name
        val uuid = UUID.randomUUID()
        val tempBaseDir = Path.of(System.getProperty("java.io.tmpdir"))
        val nsDirName = ".ns-$uuid"
        val nsDir = tempBaseDir.resolve(nsDirName)
        val modsDir = nsDir.resolve("mods")
        
        try {
            // Create directory structure
            Files.createDirectories(modsDir)
            logger.info("Created temporary mods directory: {}", modsDir.toAbsolutePath())
            
            // Set hidden attribute on .ns-{UUID} directory (Windows only)
            setHiddenAttribute(nsDir)
            
            // Scan encrypted cache for .enc files
            if (!encryptedCacheDir.exists()) {
                logger.warn("Encrypted cache directory does not exist: {}", encryptedCacheDir)
                return modsDir
            }
            
            val encryptedFiles = encryptedCacheDir.listDirectoryEntries("*.enc")
            logger.debug("Found {} encrypted files in cache", encryptedFiles.size)
            
            // Decrypt each .enc file
            var successCount = 0
            var failureCount = 0
            
            for (encryptedFile in encryptedFiles) {
                try {
                    decryptFile(encryptedFile, modsDir, sessionToken)
                    successCount++
                } catch (e: Exception) {
                    failureCount++
                    logger.error(
                        "Failed to decrypt file: {} - {}",
                        encryptedFile.name,
                        e.message,
                        e
                    )
                    // Continue with next file (graceful degradation)
                }
            }
            
            logger.info(
                "Decryption complete: {} succeeded, {} failed",
                successCount,
                failureCount
            )
            
            return modsDir
            
        } catch (e: Exception) {
            // Critical failure in directory creation
            logger.error("Failed to prepare protected mods directory", e)
            throw PreparationException("Failed to create temporary mods directory", e)
        }
    }
    
    /**
     * Decrypts a single .enc file and writes it to the temp directory.
     * 
     * @param encryptedFile Path to the .enc file
     * @param targetDir Target directory for decrypted file
     * @param sessionToken Session token for decryption
     * @throws DecryptionException if decryption fails
     */
    private fun decryptFile(encryptedFile: Path, targetDir: Path, sessionToken: String) {
        logger.debug("Decrypting file: {}", encryptedFile.name)
        
        // Read encrypted bytes
        val encryptedBytes = encryptedFile.readBytes()
        
        // Decrypt
        val decryptedBytes = modDecryptor.decrypt(encryptedBytes, sessionToken)
        
        // Remove .enc extension from filename
        val originalFileName = fromEncryptedFileName(encryptedFile.name)
        val targetFile = targetDir.resolve(originalFileName)
        
        // Write decrypted bytes
        targetFile.writeBytes(decryptedBytes)
        
        logger.debug(
            "Successfully decrypted {} to {}",
            encryptedFile.name,
            targetFile.toAbsolutePath()
        )
    }
    
    /**
     * Sets the dos:hidden attribute on a directory (Windows-specific).
     * 
     * If setting the attribute fails (e.g., on non-Windows systems or due to
     * permission issues), logs a warning and continues.
     * 
     * @param directory The directory to set as hidden
     */
    private fun setHiddenAttribute(directory: Path) {
        try {
            Files.setAttribute(directory, "dos:hidden", true)
            logger.debug("Set hidden attribute on directory: {}", directory)
        } catch (e: UnsupportedOperationException) {
            // Not on Windows or dos attributes not supported
            logger.warn(
                "Failed to set hidden attribute on {} - dos:hidden not supported on this platform",
                directory
            )
        } catch (e: Exception) {
            // Other errors (permissions, etc.)
            logger.warn(
                "Failed to set hidden attribute on {} - {}",
                directory,
                e.message
            )
        }
    }
}
