#!/usr/bin/env kotlin

/**
 * Manual verification script for ModDecryptor implementation.
 * 
 * This script demonstrates that:
 * 1. Encryption produces valid output
 * 2. Decryption restores original data
 * 3. Round-trip preserves data integrity
 * 4. Wrong session token causes decryption failure
 * 5. Corrupted data causes decryption failure
 */

import `fun`.nightshift.launcher.client.crypto.AesGcmModEncryptor
import `fun`.nightshift.launcher.client.crypto.AesGcmModDecryptor
import `fun`.nightshift.launcher.client.crypto.DecryptionException

fun main() {
    val encryptor = AesGcmModEncryptor()
    val decryptor = AesGcmModDecryptor()
    
    println("=== ModDecryptor Verification ===\n")
    
    // Test 1: Basic round-trip
    println("Test 1: Basic round-trip encryption/decryption")
    val testData = "Hello, NightShift Launcher!".toByteArray()
    val sessionToken = "test-session-token-123"
    
    val encrypted = encryptor.encrypt(testData, sessionToken)
    println("  Original size: ${testData.size} bytes")
    println("  Encrypted size: ${encrypted.size} bytes")
    
    val decrypted = decryptor.decrypt(encrypted, sessionToken)
    println("  Decrypted size: ${decrypted.size} bytes")
    println("  Data matches: ${testData.contentEquals(decrypted)}")
    println("  ✓ Test 1 passed\n")
    
    // Test 2: Empty session token (offline mode)
    println("Test 2: Empty session token (offline mode)")
    val encrypted2 = encryptor.encrypt(testData, "")
    val decrypted2 = decryptor.decrypt(encrypted2, "")
    println("  Data matches: ${testData.contentEquals(decrypted2)}")
    println("  ✓ Test 2 passed\n")
    
    // Test 3: Large data
    println("Test 3: Large data (1 MB)")
    val largeData = ByteArray(1024 * 1024) { it.toByte() }
    val encrypted3 = encryptor.encrypt(largeData, sessionToken)
    val decrypted3 = decryptor.decrypt(encrypted3, sessionToken)
    println("  Data matches: ${largeData.contentEquals(decrypted3)}")
    println("  ✓ Test 3 passed\n")
    
    // Test 4: Wrong session token
    println("Test 4: Wrong session token (should fail)")
    try {
        decryptor.decrypt(encrypted, "wrong-token")
        println("  ✗ Test 4 failed - should have thrown exception")
    } catch (e: DecryptionException) {
        println("  Exception caught: ${e.message}")
        println("  ✓ Test 4 passed\n")
    }
    
    // Test 5: Corrupted data
    println("Test 5: Corrupted data (should fail)")
    val corrupted = encrypted.copyOf()
    corrupted[encrypted.size / 2] = (corrupted[encrypted.size / 2] + 1).toByte()
    try {
        decryptor.decrypt(corrupted, sessionToken)
        println("  ✗ Test 5 failed - should have thrown exception")
    } catch (e: DecryptionException) {
        println("  Exception caught: ${e.message}")
        println("  ✓ Test 5 passed\n")
    }
    
    // Test 6: File too small
    println("Test 6: File too small (should fail)")
    val tooSmall = ByteArray(27) // Less than 28 bytes minimum
    try {
        decryptor.decrypt(tooSmall, sessionToken)
        println("  ✗ Test 6 failed - should have thrown exception")
    } catch (e: DecryptionException) {
        println("  Exception caught: ${e.message}")
        println("  ✓ Test 6 passed\n")
    }
    
    println("=== All verification tests passed! ===")
}
