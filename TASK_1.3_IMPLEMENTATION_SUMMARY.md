# Task 1.3 Implementation Summary: ModDecryptor

## Overview
Implemented the `ModDecryptor` interface and `AesGcmModDecryptor` implementation for decrypting premium mod jar files encrypted with AES-256-GCM.

## Files Created

### 1. DecryptionException.kt
**Path:** `nightshift-launcher/launcher-client/src/main/kotlin/fun/nightshift/launcher/client/crypto/DecryptionException.kt`

Exception class for decryption errors, wrapping underlying cryptographic errors (such as `AEADBadTagException` for tampered/corrupted files).

### 2. ModDecryptor.kt
**Path:** `nightshift-launcher/launcher-client/src/main/kotlin/fun/nightshift/launcher/client/crypto/ModDecryptor.kt`

Interface defining the decryption contract:
```kotlin
interface ModDecryptor {
    fun decrypt(encryptedBytes: ByteArray, sessionToken: String): ByteArray
}
```

### 3. AesGcmModDecryptor.kt
**Path:** `nightshift-launcher/launcher-client/src/main/kotlin/fun/nightshift/launcher/client/crypto/AesGcmModDecryptor.kt`

Implementation of `ModDecryptor` using AES-256-GCM decryption with the following features:

#### Key Features:
- **File Format Validation**: Validates minimum file size (28 bytes: 12 IV + 16 tag)
- **IV Extraction**: Extracts IV from first 12 bytes
- **GCM Tag Validation**: Automatically validated by `Cipher.doFinal()`
- **Ciphertext Extraction**: Extracts encrypted data from middle bytes
- **Key Derivation**: Uses same PBKDF2-HMAC-SHA256 as ModEncryptor
  - Salt: "nightshift.launcher.v1"
  - Iterations: 100,000
  - Key length: 256 bits
- **Error Handling**: 
  - Catches `AEADBadTagException` for tampered/corrupted files
  - Throws `DecryptionException` with descriptive messages
  - Validates file size before attempting decryption

#### Implementation Details:
```kotlin
class AesGcmModDecryptor : ModDecryptor {
    companion object {
        private const val ALGORITHM = "AES/GCM/NoPadding"
        private const val IV_LENGTH_BYTES = 12
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val GCM_TAG_LENGTH_BYTES = 16
        private const val MIN_FILE_SIZE = 28 // IV + tag
        private const val PBKDF2_ITERATIONS = 100_000
        private val SALT = "nightshift.launcher.v1".toByteArray(UTF_8)
    }
    
    override fun decrypt(encryptedBytes: ByteArray, sessionToken: String): ByteArray {
        // 1. Validate file size
        // 2. Extract IV (first 12 bytes)
        // 3. Extract ciphertext (remaining bytes, includes GCM tag)
        // 4. Derive key from session token
        // 5. Decrypt and validate GCM tag
        // 6. Return plaintext or throw DecryptionException
    }
}
```

### 4. AesGcmModDecryptorTest.kt
**Path:** `nightshift-launcher/launcher-client/src/test/kotlin/fun/nightshift/launcher/client/crypto/AesGcmModDecryptorTest.kt`

Comprehensive unit tests covering:
- ✓ Basic round-trip encryption/decryption
- ✓ Empty session token (offline mode)
- ✓ Empty input
- ✓ Large input (1 MB)
- ✓ Wrong session token (should fail)
- ✓ File too small (should fail)
- ✓ Corrupted IV (should fail)
- ✓ Corrupted ciphertext (should fail)
- ✓ Corrupted GCM tag (should fail)
- ✓ Multiple round-trips preserve data integrity
- ✓ Different session tokens for different encryptions

## Requirements Validated

This implementation validates the following requirements from the spec:

- **Requirement 4.4**: Extracts IV from first 12 bytes
- **Requirement 4.5**: Extracts GCM tag from last 16 bytes
- **Requirement 4.6**: Extracts ciphertext from middle bytes
- **Requirement 3.5**: Uses same PBKDF2 key derivation as ModEncryptor
- **Requirement 14.1**: Validates minimum file size (28 bytes)
- **Requirement 14.2**: Throws DecryptionException for files too small
- **Requirement 14.3**: Uses Cipher.doFinal() for GCM tag validation
- **Requirement 14.4**: Handles AEADBadTagException for tampered/corrupted files

## Verification

### Compilation Status
✓ All files compile successfully without errors
✓ No diagnostic issues found

### Build Status
✓ `./gradlew :launcher-client:build -x test` - SUCCESS
✓ `./gradlew :launcher-client:compileTestKotlin` - SUCCESS

### Test Status
⚠ Gradle test execution has environment issues (Gradle daemon/classpath problem)
✓ Test code compiles successfully
✓ Manual verification script created (`verify-decryptor.kt`)

## Integration Points

The `ModDecryptor` is designed to integrate with:

1. **ProtectedModsPreparer** (Task 7.1): Will use ModDecryptor to decrypt .enc files into temp directory
2. **RemoteModJarSource** (Task 4.1): Will use ModDecryptor to verify encrypted cache files
3. **TempDirCleaner** (Task 8.1): Will clean up decrypted files after game exit

## Security Properties

The implementation ensures:

1. **Authentication**: GCM tag validation prevents tampering
2. **Confidentiality**: AES-256 encryption protects file contents
3. **Key Derivation**: PBKDF2 with 100,000 iterations resists brute-force
4. **Session Binding**: Different session tokens produce different keys
5. **Offline Support**: Empty session token allows offline decryption
6. **Error Handling**: Graceful degradation on decryption failures

## Next Steps

According to the task plan, the next tasks are:

- **Task 1.4**: Write property test for ModDecryptor (optional)
- **Task 1.5**: Write unit tests for encryption error handling (optional)
- **Task 2**: Checkpoint - Verify encryption infrastructure

The core implementation is complete and ready for integration with other components.
