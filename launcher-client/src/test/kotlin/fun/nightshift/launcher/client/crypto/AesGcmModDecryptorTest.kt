package `fun`.nightshift.launcher.client.crypto

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

/**
 * Unit tests for AesGcmModDecryptor.
 * 
 * These tests verify:
 * - Basic decryption functionality
 * - Round-trip encryption/decryption
 * - File format validation
 * - Error handling for corrupted/tampered files
 * - Error handling for wrong session tokens
 */
class AesGcmModDecryptorTest : StringSpec({
    
    val encryptor = AesGcmModEncryptor()
    val decryptor = AesGcmModDecryptor()
    
    "decrypt should restore original data after encryption" {
        val original = "Hello, World!".toByteArray()
        val sessionToken = "test-token"
        
        val encrypted = encryptor.encrypt(original, sessionToken)
        val decrypted = decryptor.decrypt(encrypted, sessionToken)
        
        decrypted shouldBe original
    }
    
    "decrypt should work with empty session token" {
        val original = "Test data".toByteArray()
        val sessionToken = ""
        
        val encrypted = encryptor.encrypt(original, sessionToken)
        val decrypted = decryptor.decrypt(encrypted, sessionToken)
        
        decrypted shouldBe original
    }
    
    "decrypt should work with empty input" {
        val original = ByteArray(0)
        val sessionToken = "test-token"
        
        val encrypted = encryptor.encrypt(original, sessionToken)
        val decrypted = decryptor.decrypt(encrypted, sessionToken)
        
        decrypted shouldBe original
    }
    
    "decrypt should work with large input" {
        val original = ByteArray(1024 * 1024) { it.toByte() } // 1 MB
        val sessionToken = "test-token"
        
        val encrypted = encryptor.encrypt(original, sessionToken)
        val decrypted = decryptor.decrypt(encrypted, sessionToken)
        
        decrypted shouldBe original
    }
    
    "decrypt should fail with wrong session token" {
        val original = "Test data".toByteArray()
        val encryptToken = "token-1"
        val decryptToken = "token-2"
        
        val encrypted = encryptor.encrypt(original, encryptToken)
        
        val exception = shouldThrow<DecryptionException> {
            decryptor.decrypt(encrypted, decryptToken)
        }
        
        exception.message shouldContain "GCM authentication tag validation failed"
    }
    
    "decrypt should fail with file too small" {
        val tooSmall = ByteArray(27) // Less than 28 bytes minimum
        val sessionToken = "test-token"
        
        val exception = shouldThrow<DecryptionException> {
            decryptor.decrypt(tooSmall, sessionToken)
        }
        
        exception.message shouldContain "too small"
        exception.message shouldContain "27 bytes"
    }
    
    "decrypt should fail with corrupted IV" {
        val original = "Test data".toByteArray()
        val sessionToken = "test-token"
        
        val encrypted = encryptor.encrypt(original, sessionToken)
        
        // Corrupt the IV (first 12 bytes)
        val corrupted = encrypted.copyOf()
        corrupted[0] = (corrupted[0] + 1).toByte()
        
        val exception = shouldThrow<DecryptionException> {
            decryptor.decrypt(corrupted, sessionToken)
        }
        
        exception.message shouldContain "GCM authentication tag validation failed"
    }
    
    "decrypt should fail with corrupted ciphertext" {
        val original = "Test data".toByteArray()
        val sessionToken = "test-token"
        
        val encrypted = encryptor.encrypt(original, sessionToken)
        
        // Corrupt the ciphertext (middle bytes)
        val corrupted = encrypted.copyOf()
        val middleIndex = encrypted.size / 2
        corrupted[middleIndex] = (corrupted[middleIndex] + 1).toByte()
        
        val exception = shouldThrow<DecryptionException> {
            decryptor.decrypt(corrupted, sessionToken)
        }
        
        exception.message shouldContain "GCM authentication tag validation failed"
    }
    
    "decrypt should fail with corrupted GCM tag" {
        val original = "Test data".toByteArray()
        val sessionToken = "test-token"
        
        val encrypted = encryptor.encrypt(original, sessionToken)
        
        // Corrupt the GCM tag (last 16 bytes)
        val corrupted = encrypted.copyOf()
        corrupted[encrypted.size - 1] = (corrupted[encrypted.size - 1] + 1).toByte()
        
        val exception = shouldThrow<DecryptionException> {
            decryptor.decrypt(corrupted, sessionToken)
        }
        
        exception.message shouldContain "GCM authentication tag validation failed"
    }
    
    "decrypt should preserve data integrity across multiple round-trips" {
        val original = "Test data for multiple round-trips".toByteArray()
        val sessionToken = "test-token"
        
        // Encrypt and decrypt multiple times
        var data = original
        repeat(5) {
            val encrypted = encryptor.encrypt(data, sessionToken)
            data = decryptor.decrypt(encrypted, sessionToken)
        }
        
        data shouldBe original
    }
    
    "decrypt should work with different session tokens for different encryptions" {
        val data1 = "Data 1".toByteArray()
        val data2 = "Data 2".toByteArray()
        val token1 = "token-1"
        val token2 = "token-2"
        
        val encrypted1 = encryptor.encrypt(data1, token1)
        val encrypted2 = encryptor.encrypt(data2, token2)
        
        val decrypted1 = decryptor.decrypt(encrypted1, token1)
        val decrypted2 = decryptor.decrypt(encrypted2, token2)
        
        decrypted1 shouldBe data1
        decrypted2 shouldBe data2
    }
})
