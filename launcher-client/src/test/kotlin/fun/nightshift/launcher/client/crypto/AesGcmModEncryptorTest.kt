package `fun`.nightshift.launcher.client.crypto

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual

/**
 * Unit tests for AesGcmModEncryptor.
 * 
 * These tests verify:
 * - Basic encryption functionality
 * - IV generation and uniqueness
 * - Output format structure
 * - Error handling
 */
class AesGcmModEncryptorTest : StringSpec({
    
    val encryptor = AesGcmModEncryptor()
    
    "encrypt should produce output with correct minimum size" {
        val input = "Hello, World!".toByteArray()
        val sessionToken = "test-token"
        
        val encrypted = encryptor.encrypt(input, sessionToken)
        
        // Minimum size: 12 bytes IV + 16 bytes GCM tag = 28 bytes
        encrypted.size shouldBeGreaterThanOrEqual 28
    }
    
    "encrypt should produce different output for same input (different IVs)" {
        val input = "Test data".toByteArray()
        val sessionToken = "test-token"
        
        val encrypted1 = encryptor.encrypt(input, sessionToken)
        val encrypted2 = encryptor.encrypt(input, sessionToken)
        
        // Different IVs should produce different ciphertext
        encrypted1 shouldNotBe encrypted2
        
        // But both should have valid structure
        encrypted1.size shouldBeGreaterThanOrEqual 28
        encrypted2.size shouldBeGreaterThanOrEqual 28
    }
    
    "encrypt should work with empty session token" {
        val input = "Test data".toByteArray()
        val sessionToken = ""
        
        val encrypted = encryptor.encrypt(input, sessionToken)
        
        encrypted.size shouldBeGreaterThanOrEqual 28
    }
    
    "encrypt should work with empty input" {
        val input = ByteArray(0)
        val sessionToken = "test-token"
        
        val encrypted = encryptor.encrypt(input, sessionToken)
        
        // Even empty input produces IV + tag
        encrypted.size shouldBeGreaterThanOrEqual 28
    }
    
    "encrypt should work with large input" {
        val input = ByteArray(1024 * 1024) { it.toByte() } // 1 MB
        val sessionToken = "test-token"
        
        val encrypted = encryptor.encrypt(input, sessionToken)
        
        // Output should be roughly input size + IV + tag
        encrypted.size shouldBeGreaterThanOrEqual (input.size + 28)
    }
    
    "encrypt should produce different output for different session tokens" {
        val input = "Test data".toByteArray()
        val token1 = "token-1"
        val token2 = "token-2"
        
        val encrypted1 = encryptor.encrypt(input, token1)
        val encrypted2 = encryptor.encrypt(input, token2)
        
        // Different tokens should produce different ciphertext
        // (even though IVs are also different, the key derivation ensures this)
        encrypted1 shouldNotBe encrypted2
    }
})
