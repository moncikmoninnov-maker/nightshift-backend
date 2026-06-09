package `fun`.nightshift.launcher.client.crypto

import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Implementation of ModDecryptor using AES-256-GCM decryption.
 * 
 * Key Derivation:
 * - Algorithm: PBKDF2-HMAC-SHA256
 * - Salt: "nightshift.launcher.v1" (UTF-8 bytes)
 * - Iterations: 100,000
 * - Key length: 256 bits
 * 
 * Decryption:
 * - Algorithm: AES-256-GCM
 * - IV length: 12 bytes (extracted from encrypted file)
 * - GCM tag length: 128 bits (16 bytes)
 * 
 * Input format: [IV(12 bytes)][ciphertext][GCM tag(16 bytes)]
 * 
 * Note: The GCM tag is automatically validated by Cipher.doFinal().
 * If the tag is invalid (file tampered/corrupted), an AEADBadTagException is thrown.
 */
class AesGcmModDecryptor : ModDecryptor {
    
    companion object {
        private const val ALGORITHM = "AES/GCM/NoPadding"
        private const val KEY_ALGORITHM = "AES"
        private const val KEY_DERIVATION_ALGORITHM = "PBKDF2WithHmacSHA256"
        
        private const val IV_LENGTH_BYTES = 12
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val GCM_TAG_LENGTH_BYTES = 16
        private const val KEY_LENGTH_BITS = 256
        private const val PBKDF2_ITERATIONS = 100_000
        
        private const val MIN_FILE_SIZE = IV_LENGTH_BYTES + GCM_TAG_LENGTH_BYTES // 28 bytes
        
        private val SALT = "nightshift.launcher.v1".toByteArray(StandardCharsets.UTF_8)
    }
    
    override fun decrypt(encryptedBytes: ByteArray, sessionToken: String): ByteArray {
        try {
            // Validate minimum file size (12 bytes IV + 16 bytes GCM tag)
            if (encryptedBytes.size < MIN_FILE_SIZE) {
                throw DecryptionException(
                    "Encrypted file too small: ${encryptedBytes.size} bytes (minimum: $MIN_FILE_SIZE bytes)"
                )
            }
            
            // Extract IV from first 12 bytes
            val iv = encryptedBytes.copyOfRange(0, IV_LENGTH_BYTES)
            
            // Extract ciphertext (middle bytes, includes GCM tag at the end)
            // Note: In GCM mode, Cipher.doFinal() expects ciphertext + tag together
            val ciphertext = encryptedBytes.copyOfRange(IV_LENGTH_BYTES, encryptedBytes.size)
            
            // Derive decryption key from session token
            val key = deriveKey(sessionToken)
            
            // Initialize cipher for decryption
            val cipher = Cipher.getInstance(ALGORITHM)
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec)
            
            // Decrypt and validate GCM authentication tag
            // If tag validation fails, AEADBadTagException is thrown
            return cipher.doFinal(ciphertext)
            
        } catch (e: AEADBadTagException) {
            // GCM tag validation failed - file is tampered, corrupted, or wrong session token
            throw DecryptionException(
                "GCM authentication tag validation failed - file may be corrupted, tampered, or encrypted with different session token",
                e
            )
        } catch (e: DecryptionException) {
            // Re-throw our own exceptions
            throw e
        } catch (e: Exception) {
            // Wrap any other exceptions
            throw DecryptionException("Failed to decrypt jar file", e)
        }
    }
    
    /**
     * Derives a 256-bit AES key from the session token using PBKDF2-HMAC-SHA256.
     * 
     * This method uses the same parameters as AesGcmModEncryptor to ensure
     * the same session token produces the same key.
     * 
     * @param sessionToken User's session token (or empty string if unavailable)
     * @return Derived AES-256 secret key
     */
    private fun deriveKey(sessionToken: String): SecretKey {
        val factory = SecretKeyFactory.getInstance(KEY_DERIVATION_ALGORITHM)
        val spec = PBEKeySpec(
            sessionToken.toCharArray(),
            SALT,
            PBKDF2_ITERATIONS,
            KEY_LENGTH_BITS
        )
        val tmp = factory.generateSecret(spec)
        return SecretKeySpec(tmp.encoded, KEY_ALGORITHM)
    }
}
