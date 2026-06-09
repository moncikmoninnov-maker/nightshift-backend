package `fun`.nightshift.launcher.client.crypto

/**
 * Decrypts encrypted premium mod jar files.
 * 
 * The decryption process:
 * 1. Validates minimum file size (28 bytes: 12 IV + 16 tag)
 * 2. Extracts IV from first 12 bytes
 * 3. Extracts GCM tag from last 16 bytes (handled by Cipher.doFinal)
 * 4. Extracts ciphertext from middle bytes
 * 5. Derives the same 256-bit AES key from session token using PBKDF2-HMAC-SHA256
 * 6. Decrypts using AES-256-GCM and validates authentication tag
 */
interface ModDecryptor {
    /**
     * Decrypts an encrypted jar file.
     * 
     * @param encryptedBytes Encrypted bytes in format: [IV(12)][encrypted data][GCM tag(16)]
     * @param sessionToken User's session token for key derivation (use empty string if unavailable)
     * @return Decrypted jar file content
     * @throws DecryptionException if decryption fails, authentication fails, or file format is invalid
     */
    fun decrypt(encryptedBytes: ByteArray, sessionToken: String): ByteArray
}
