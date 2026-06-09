package `fun`.nightshift.launcher.client.crypto

/**
 * Encrypts premium mod jar files using AES-256-GCM encryption.
 * 
 * The encryption process:
 * 1. Derives a 256-bit AES key from the session token using PBKDF2-HMAC-SHA256
 * 2. Generates a random 12-byte IV for each encryption operation
 * 3. Encrypts the jar bytes using AES-256-GCM (authenticated encryption)
 * 4. Returns encrypted data in format: [IV(12 bytes)][ciphertext][GCM tag(16 bytes)]
 */
interface ModEncryptor {
    /**
     * Encrypts a jar file and returns the encrypted bytes.
     * 
     * @param jarBytes Original jar file content
     * @param sessionToken User's session token for key derivation (use empty string if unavailable)
     * @return Encrypted bytes in format: [IV(12)][encrypted data][GCM tag(16)]
     * @throws EncryptionException if encryption fails
     */
    fun encrypt(jarBytes: ByteArray, sessionToken: String): ByteArray
}
