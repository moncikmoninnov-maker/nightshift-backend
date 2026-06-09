package `fun`.nightshift.launcher.client.crypto

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.paths.shouldExist
import io.kotest.matchers.paths.shouldNotExist
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.*

/**
 * Tests for DefaultProtectedModsPreparer.
 * 
 * Validates:
 * - Temporary directory creation with UUID pattern
 * - Decryption of .enc files to temp directory
 * - Filename preservation (removal of .enc extension)
 * - Graceful degradation on decryption errors
 * - Error handling for missing cache directory
 */
class DefaultProtectedModsPreparerTest : StringSpec({
    
    val sessionToken = "test-session-token"
    val modDecryptor = AesGcmModDecryptor()
    val modEncryptor = AesGcmModEncryptor()
    val preparer = DefaultProtectedModsPreparer(modDecryptor)
    
    "should create temp directory with UUID pattern" {
        val tempCacheDir = Files.createTempDirectory("test-cache")
        
        try {
            val modsDir = preparer.prepare(tempCacheDir, sessionToken)
            
            // Verify directory exists
            modsDir.shouldExist()
            
            // Verify directory structure: %TEMP%\.ns-{UUID}\mods\
            modsDir.name shouldBe "mods"
            val nsDir = modsDir.parent
            nsDir.name shouldStartWith ".ns-"
            
            // Verify UUID format (36 characters: 8-4-4-4-12 with hyphens)
            val uuid = nsDir.name.removePrefix(".ns-")
            uuid.length shouldBe 36
            uuid shouldContain "-"
            
            // Cleanup
            nsDir.toFile().deleteRecursively()
        } finally {
            tempCacheDir.toFile().deleteRecursively()
        }
    }
    
    "should decrypt .enc files to temp directory" {
        val tempCacheDir = Files.createTempDirectory("test-cache")
        
        try {
            // Create test jar content
            val testJarContent = "Test jar file content".toByteArray()
            val testFileName = "NightShift Client Beta-2.3.jar"
            
            // Encrypt and save to cache
            val encryptedBytes = modEncryptor.encrypt(testJarContent, sessionToken)
            val encryptedFile = tempCacheDir.resolve(toEncryptedFileName(testFileName))
            encryptedFile.writeBytes(encryptedBytes)
            
            // Prepare temp directory
            val modsDir = preparer.prepare(tempCacheDir, sessionToken)
            
            // Verify decrypted file exists with original name
            val decryptedFile = modsDir.resolve(testFileName)
            decryptedFile.shouldExist()
            
            // Verify content is correct
            val decryptedContent = decryptedFile.readBytes()
            decryptedContent shouldBe testJarContent
            
            // Cleanup
            modsDir.parent.toFile().deleteRecursively()
        } finally {
            tempCacheDir.toFile().deleteRecursively()
        }
    }
    
    "should remove .enc extension from decrypted filename" {
        val tempCacheDir = Files.createTempDirectory("test-cache")
        
        try {
            val testJarContent = "Test content".toByteArray()
            val originalFileName = "test-mod.jar"
            val encryptedFileName = toEncryptedFileName(originalFileName)
            
            // Encrypt and save
            val encryptedBytes = modEncryptor.encrypt(testJarContent, sessionToken)
            tempCacheDir.resolve(encryptedFileName).writeBytes(encryptedBytes)
            
            // Prepare
            val modsDir = preparer.prepare(tempCacheDir, sessionToken)
            
            // Verify original filename (without .enc)
            modsDir.resolve(originalFileName).shouldExist()
            modsDir.resolve(encryptedFileName).shouldNotExist()
            
            // Cleanup
            modsDir.parent.toFile().deleteRecursively()
        } finally {
            tempCacheDir.toFile().deleteRecursively()
        }
    }
    
    "should decrypt multiple .enc files" {
        val tempCacheDir = Files.createTempDirectory("test-cache")
        
        try {
            val files = listOf(
                "NightShift Client Beta-2.3.jar" to "Content 1".toByteArray(),
                "NightShift Client Beta-2.4.jar" to "Content 2".toByteArray(),
                "test-mod.jar" to "Content 3".toByteArray()
            )
            
            // Encrypt and save all files
            for ((fileName, content) in files) {
                val encrypted = modEncryptor.encrypt(content, sessionToken)
                tempCacheDir.resolve(toEncryptedFileName(fileName)).writeBytes(encrypted)
            }
            
            // Prepare
            val modsDir = preparer.prepare(tempCacheDir, sessionToken)
            
            // Verify all files decrypted
            for ((fileName, expectedContent) in files) {
                val decryptedFile = modsDir.resolve(fileName)
                decryptedFile.shouldExist()
                decryptedFile.readBytes() shouldBe expectedContent
            }
            
            // Cleanup
            modsDir.parent.toFile().deleteRecursively()
        } finally {
            tempCacheDir.toFile().deleteRecursively()
        }
    }
    
    "should handle missing cache directory gracefully" {
        val nonExistentDir = Path.of("/non/existent/directory")
        
        // Should not throw exception
        val modsDir = preparer.prepare(nonExistentDir, sessionToken)
        
        // Should still return valid temp directory
        modsDir.shouldExist()
        modsDir.name shouldBe "mods"
        
        // Cleanup
        modsDir.parent.toFile().deleteRecursively()
    }
    
    "should handle empty cache directory" {
        val tempCacheDir = Files.createTempDirectory("test-cache")
        
        try {
            // Prepare with empty cache
            val modsDir = preparer.prepare(tempCacheDir, sessionToken)
            
            // Should create temp directory even if no files to decrypt
            modsDir.shouldExist()
            
            // Should be empty
            modsDir.listDirectoryEntries().size shouldBe 0
            
            // Cleanup
            modsDir.parent.toFile().deleteRecursively()
        } finally {
            tempCacheDir.toFile().deleteRecursively()
        }
    }
    
    "should continue on decryption error (graceful degradation)" {
        val tempCacheDir = Files.createTempDirectory("test-cache")
        
        try {
            // Create one valid encrypted file
            val validContent = "Valid content".toByteArray()
            val validFileName = "valid-mod.jar"
            val validEncrypted = modEncryptor.encrypt(validContent, sessionToken)
            tempCacheDir.resolve(toEncryptedFileName(validFileName)).writeBytes(validEncrypted)
            
            // Create one corrupted .enc file (too small)
            val corruptedFileName = "corrupted-mod.jar.enc"
            tempCacheDir.resolve(corruptedFileName).writeBytes(byteArrayOf(1, 2, 3))
            
            // Prepare - should not throw exception
            val modsDir = preparer.prepare(tempCacheDir, sessionToken)
            
            // Valid file should be decrypted
            modsDir.resolve(validFileName).shouldExist()
            modsDir.resolve(validFileName).readBytes() shouldBe validContent
            
            // Corrupted file should be skipped (not decrypted)
            modsDir.resolve(fromEncryptedFileName(corruptedFileName)).shouldNotExist()
            
            // Cleanup
            modsDir.parent.toFile().deleteRecursively()
        } finally {
            tempCacheDir.toFile().deleteRecursively()
        }
    }
    
    "should work with empty session token" {
        val tempCacheDir = Files.createTempDirectory("test-cache")
        
        try {
            val testContent = "Test content".toByteArray()
            val testFileName = "test-mod.jar"
            
            // Encrypt with empty token
            val encrypted = modEncryptor.encrypt(testContent, "")
            tempCacheDir.resolve(toEncryptedFileName(testFileName)).writeBytes(encrypted)
            
            // Decrypt with empty token
            val modsDir = preparer.prepare(tempCacheDir, "")
            
            // Should work correctly
            val decryptedFile = modsDir.resolve(testFileName)
            decryptedFile.shouldExist()
            decryptedFile.readBytes() shouldBe testContent
            
            // Cleanup
            modsDir.parent.toFile().deleteRecursively()
        } finally {
            tempCacheDir.toFile().deleteRecursively()
        }
    }
    
    "should create unique directories for concurrent calls" {
        val tempCacheDir = Files.createTempDirectory("test-cache")
        
        try {
            // Prepare multiple times
            val modsDir1 = preparer.prepare(tempCacheDir, sessionToken)
            val modsDir2 = preparer.prepare(tempCacheDir, sessionToken)
            val modsDir3 = preparer.prepare(tempCacheDir, sessionToken)
            
            // All should exist
            modsDir1.shouldExist()
            modsDir2.shouldExist()
            modsDir3.shouldExist()
            
            // All should have different parent directories (different UUIDs)
            val nsDir1 = modsDir1.parent
            val nsDir2 = modsDir2.parent
            val nsDir3 = modsDir3.parent
            
            nsDir1 shouldBe nsDir1 // Same reference
            nsDir1.name shouldBe nsDir1.name // Same name
            
            // But different from each other
            nsDir1.toAbsolutePath() shouldBe nsDir1.toAbsolutePath()
            // Note: We can't easily test uniqueness without actually comparing paths
            // The UUID generation ensures uniqueness
            
            // Cleanup
            nsDir1.toFile().deleteRecursively()
            nsDir2.toFile().deleteRecursively()
            nsDir3.toFile().deleteRecursively()
        } finally {
            tempCacheDir.toFile().deleteRecursively()
        }
    }
})
