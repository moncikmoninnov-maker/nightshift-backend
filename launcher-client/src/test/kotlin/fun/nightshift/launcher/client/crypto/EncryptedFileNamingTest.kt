package `fun`.nightshift.launcher.client.crypto

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * Unit tests for encrypted file naming utilities.
 * 
 * These tests verify:
 * - toEncryptedFileName appends ".enc" extension correctly
 * - fromEncryptedFileName removes ".enc" extension correctly
 * - Edge cases (already encrypted names, no extension, special characters)
 * - Round-trip preservation (original → encrypted → original)
 */
class EncryptedFileNamingTest : StringSpec({
    
    "toEncryptedFileName should append .enc to premium mod filename" {
        val original = "NightShift Client Beta-2.3.jar"
        
        val encrypted = toEncryptedFileName(original)
        
        encrypted shouldBe "NightShift Client Beta-2.3.jar.enc"
    }
    
    "toEncryptedFileName should append .enc to public mod filename" {
        val original = "fabric-api-0.119.4-1.21.4.jar"
        
        val encrypted = toEncryptedFileName(original)
        
        encrypted shouldBe "fabric-api-0.119.4-1.21.4.jar.enc"
    }
    
    "toEncryptedFileName should append .enc to filename without extension" {
        val original = "mod-file"
        
        val encrypted = toEncryptedFileName(original)
        
        encrypted shouldBe "mod-file.enc"
    }
    
    "toEncryptedFileName should append .enc even if already has .enc" {
        val original = "file.enc"
        
        val encrypted = toEncryptedFileName(original)
        
        encrypted shouldBe "file.enc.enc"
    }
    
    "toEncryptedFileName should handle empty string" {
        val original = ""
        
        val encrypted = toEncryptedFileName(original)
        
        encrypted shouldBe ".enc"
    }
    
    "toEncryptedFileName should handle filename with spaces" {
        val original = "My Mod File 1.0.jar"
        
        val encrypted = toEncryptedFileName(original)
        
        encrypted shouldBe "My Mod File 1.0.jar.enc"
    }
    
    "toEncryptedFileName should handle filename with special characters" {
        val original = "mod-file_v2.3-beta+build.jar"
        
        val encrypted = toEncryptedFileName(original)
        
        encrypted shouldBe "mod-file_v2.3-beta+build.jar.enc"
    }
    
    "fromEncryptedFileName should remove .enc from encrypted filename" {
        val encrypted = "NightShift Client Beta-2.3.jar.enc"
        
        val original = fromEncryptedFileName(encrypted)
        
        original shouldBe "NightShift Client Beta-2.3.jar"
    }
    
    "fromEncryptedFileName should remove .enc from public mod filename" {
        val encrypted = "fabric-api-0.119.4-1.21.4.jar.enc"
        
        val original = fromEncryptedFileName(encrypted)
        
        original shouldBe "fabric-api-0.119.4-1.21.4.jar"
    }
    
    "fromEncryptedFileName should remove .enc from filename without jar extension" {
        val encrypted = "mod-file.enc"
        
        val original = fromEncryptedFileName(encrypted)
        
        original shouldBe "mod-file"
    }
    
    "fromEncryptedFileName should return unchanged if no .enc extension" {
        val encrypted = "file.jar"
        
        val original = fromEncryptedFileName(encrypted)
        
        original shouldBe "file.jar"
    }
    
    "fromEncryptedFileName should handle empty string" {
        val encrypted = ""
        
        val original = fromEncryptedFileName(encrypted)
        
        original shouldBe ""
    }
    
    "fromEncryptedFileName should handle .enc only" {
        val encrypted = ".enc"
        
        val original = fromEncryptedFileName(encrypted)
        
        original shouldBe ""
    }
    
    "fromEncryptedFileName should remove only last .enc if multiple" {
        val encrypted = "file.enc.enc"
        
        val original = fromEncryptedFileName(encrypted)
        
        original shouldBe "file.enc"
    }
    
    "fromEncryptedFileName should handle filename with spaces" {
        val encrypted = "My Mod File 1.0.jar.enc"
        
        val original = fromEncryptedFileName(encrypted)
        
        original shouldBe "My Mod File 1.0.jar"
    }
    
    "fromEncryptedFileName should handle filename with special characters" {
        val encrypted = "mod-file_v2.3-beta+build.jar.enc"
        
        val original = fromEncryptedFileName(encrypted)
        
        original shouldBe "mod-file_v2.3-beta+build.jar"
    }
    
    "round-trip should preserve original filename (premium mod)" {
        val original = "NightShift Client Beta-2.3.jar"
        
        val roundTrip = fromEncryptedFileName(toEncryptedFileName(original))
        
        roundTrip shouldBe original
    }
    
    "round-trip should preserve original filename (public mod)" {
        val original = "fabric-api-0.119.4-1.21.4.jar"
        
        val roundTrip = fromEncryptedFileName(toEncryptedFileName(original))
        
        roundTrip shouldBe original
    }
    
    "round-trip should preserve original filename (no extension)" {
        val original = "mod-file"
        
        val roundTrip = fromEncryptedFileName(toEncryptedFileName(original))
        
        roundTrip shouldBe original
    }
    
    "round-trip should preserve original filename (with spaces)" {
        val original = "My Mod File 1.0.jar"
        
        val roundTrip = fromEncryptedFileName(toEncryptedFileName(original))
        
        roundTrip shouldBe original
    }
    
    "round-trip should preserve original filename (with special characters)" {
        val original = "mod-file_v2.3-beta+build.jar"
        
        val roundTrip = fromEncryptedFileName(toEncryptedFileName(original))
        
        roundTrip shouldBe original
    }
})
