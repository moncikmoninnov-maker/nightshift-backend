package `fun`.nightshift.launcher.client.crypto

import java.nio.file.Path

/**
 * Prepares temporary directory with decrypted premium mods before game launch.
 * 
 * This component is responsible for:
 * 1. Creating a unique temporary directory with UUID-based naming
 * 2. Setting the directory as hidden (Windows-specific)
 * 3. Scanning the encrypted cache for .enc files
 * 4. Decrypting each .enc file and writing to the temporary directory
 * 5. Handling decryption errors gracefully (graceful degradation)
 * 
 * The temporary directory structure is: `%TEMP%\.ns-{UUID}\mods\`
 * 
 * Error handling philosophy:
 * - If one file fails to decrypt, continue with others (graceful degradation)
 * - If hidden attribute fails to set, log warning and continue
 * - Always return a valid path, even if some/all decryptions fail
 * - Game may still work with public mods if premium mods fail to decrypt
 */
interface ProtectedModsPreparer {
    /**
     * Creates temporary directory and decrypts premium mods into it.
     * 
     * Process:
     * 1. Generate random UUID v4 for directory name
     * 2. Create `%TEMP%\.ns-{UUID}\mods\` directory structure
     * 3. Set `dos:hidden` attribute on `.ns-{UUID}` directory (Windows only)
     * 4. Scan encryptedCacheDir for files ending with `.enc`
     * 5. For each `.enc` file:
     *    - Decrypt using ModDecryptor
     *    - Write decrypted bytes to temp directory
     *    - Remove `.enc` extension from filename
     *    - On error: log ERROR and continue (graceful degradation)
     * 6. Return path to temp mods directory
     * 
     * @param encryptedCacheDir Directory containing .enc files (typically cache/mods/)
     * @param sessionToken User's session token for decryption (use empty string if unavailable)
     * @return Path to temporary mods directory (e.g., `C:\Users\...\Temp\.ns-{UUID}\mods\`)
     * @throws PreparationException if directory creation fails or other critical errors occur
     */
    fun prepare(encryptedCacheDir: Path, sessionToken: String): Path
}
