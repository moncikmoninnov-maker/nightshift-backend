#!/usr/bin/env kotlin

@file:DependsOn("org.slf4j:slf4j-simple:2.0.9")

import `fun`.nightshift.launcher.client.crypto.*
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.*

/**
 * Manual verification script for ProtectedModsPreparer.
 * 
 * This script:
 * 1. Creates a test encrypted cache directory
 * 2. Encrypts a test jar file
 * 3. Uses ProtectedModsPreparer to decrypt it to a temp directory
 * 4. Verifies the decrypted file matches the original
 * 5. Cleans up
 */

fun main() {
    println("=== ProtectedModsPreparer Verification ===\n")
    
    val sessionToken = "test-session-token"
    val modEncryptor = AesGcmModEncryptor()
    val modDecryptor = AesGcmModDecryptor()
    val preparer = DefaultProtectedModsPreparer(modDecryptor)
    
    // Create test cache directory
    val testCacheDir = Files.createTempDirectory("test-cache")
    println("Created test cache directory: $testCacheDir")
    
    try {
        // Create test jar content
        val testJarContent = "This is a test jar file content for NightShift Client Beta".toByteArray()
        val testFileName = "NightShift Client Beta-2.3.jar"
        
        println("\n1. Creating test jar file...")
        println("   Filename: $testFileName")
        println("   Content size: ${testJarContent.size} bytes")
        
        // Encrypt and save to cache
        println("\n2. Encrypting jar file...")
        val encryptedBytes = modEncryptor.encrypt(testJarContent, sessionToken)
        val encryptedFileName = toEncryptedFileName(testFileName)
        val encryptedFile = testCacheDir.resolve(encryptedFileName)
        encryptedFile.writeBytes(encryptedBytes)
        println("   Encrypted file: $encryptedFileName")
        println("   Encrypted size: ${encryptedBytes.size} bytes")
        
        // Prepare temp directory
        println("\n3. Preparing temporary mods directory...")
        val modsDir = preparer.prepare(testCacheDir, sessionToken)
        println("   Temp mods directory: $modsDir")
        
        // Verify directory structure
        println("\n4. Verifying directory structure...")
        val nsDir = modsDir.parent
        println("   Parent directory: ${nsDir.name}")
        if (nsDir.name.startsWith(".ns-")) {
            println("   ✓ Directory name matches pattern .ns-{UUID}")
        } else {
            println("   ✗ Directory name does NOT match pattern")
        }
        
        // Verify decrypted file
        println("\n5. Verifying decrypted file...")
        val decryptedFile = modsDir.resolve(testFileName)
        if (decryptedFile.exists()) {
            println("   ✓ Decrypted file exists: $testFileName")
            
            val decryptedContent = decryptedFile.readBytes()
            println("   Decrypted size: ${decryptedContent.size} bytes")
            
            if (decryptedContent.contentEquals(testJarContent)) {
                println("   ✓ Content matches original")
            } else {
                println("   ✗ Content does NOT match original")
            }
        } else {
            println("   ✗ Decrypted file does NOT exist")
        }
        
        // Test with multiple files
        println("\n6. Testing with multiple files...")
        val testFiles = listOf(
            "NightShift Client Beta-2.4.jar" to "Content for version 2.4".toByteArray(),
            "test-mod.jar" to "Public mod content".toByteArray()
        )
        
        for ((fileName, content) in testFiles) {
            val encrypted = modEncryptor.encrypt(content, sessionToken)
            testCacheDir.resolve(toEncryptedFileName(fileName)).writeBytes(encrypted)
            println("   Added: $fileName")
        }
        
        val modsDir2 = preparer.prepare(testCacheDir, sessionToken)
        println("   Prepared new temp directory: $modsDir2")
        
        var allFilesDecrypted = true
        for ((fileName, expectedContent) in testFiles) {
            val file = modsDir2.resolve(fileName)
            if (file.exists() && file.readBytes().contentEquals(expectedContent)) {
                println("   ✓ $fileName decrypted correctly")
            } else {
                println("   ✗ $fileName failed")
                allFilesDecrypted = false
            }
        }
        
        if (allFilesDecrypted) {
            println("\n✓ All tests passed!")
        } else {
            println("\n✗ Some tests failed")
        }
        
        // Cleanup
        println("\n7. Cleaning up...")
        nsDir.toFile().deleteRecursively()
        modsDir2.parent.toFile().deleteRecursively()
        println("   Cleaned up temp directories")
        
    } finally {
        testCacheDir.toFile().deleteRecursively()
        println("   Cleaned up test cache directory")
    }
    
    println("\n=== Verification Complete ===")
}

main()
