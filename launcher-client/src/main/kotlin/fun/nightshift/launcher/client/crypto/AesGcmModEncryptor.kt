package `fun`.nightshift.launcher.client.crypto

import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Implementation of ModEncryptor using AES-256-GCM encryption.
 * 
 * Key Derivation:
 * - Algorithm: PBKDF2-HMAC-SHA256
 * - Salt: "nightshift.launcher.v1" (UTF-8 bytes)
 * - Iterations: 100,000
 * - Key length: 256 bits
 * 
 * Encryption:
 * - Algorithm: AES-256-GCM
 * - IV length: 12 bytes (randomly generated per encryption)
 * - GCM tag length: 128 bits (16 bytes)
 * 
 * Output format: [IV(12 bytes)][ciphertext][GCM tag(16 bytes)]
 */
class AesGcmModEncryptor : ModEncryptor {
    
    companion object {
        private const val ALGORITHM = "AES/GCM/NoPadding"
        private const val KEY_ALGORITHM = "AES"
        private const val KEY_DERIVATION_ALGORITHM = "PBKDF2WithHmacSHA256"
        
        private const val IV_LENGTH_BYTES = 12
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val KEY_LENGTH_BITS = 256
        private const val PBKDF2_ITERATIONS = 100_000
        
        private val SALT = "nightshift.launcher.v1".toByteArray(StandardCharsets.UTF_8)
    }
    
    private val secureRandom = SecureRandom()
    
    override fun encrypt(jarBytes: ByteArray, sessionToken: String): ByteArray {
        try {
            // Derive encryption key from session token
            val key = deriveKey(sessionToken)
            
            // Generate random IV
            val iv = ByteArray(IV_LENGTH_BYTES)
            secureRandom.nextBytes(iv)
            
            // Initialize cipher for encryption
            val cipher = Cipher.getInstance(ALGORITHM)
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            cipher.init(Cipher.ENCRYPT_MODE, key, gcmSpec)
            
            // Encrypt the jar bytes
            // GCM mode automatically appends the authentication tag to the ciphertext
            val ciphertext = cipher.doFinal(jarBytes)
            
            // Combine IV + ciphertext (which includes GCM tag)
            // Format: [IV(12)][encrypted data + GCM tag(16)]
            return iv + ciphertext
            
        } catch (e: Exception) {
            throw EncryptionException("Failed to encrypt jar file", e)
        }
    }
    
    /**
     * Derives a 256-bit AES key from the session token using PBKDF2-HMAC-SHA256.
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
