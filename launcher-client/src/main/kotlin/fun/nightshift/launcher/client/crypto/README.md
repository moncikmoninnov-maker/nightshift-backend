# Crypto Package - ModEncryptor Implementation

## Overview

This package implements AES-256-GCM encryption for premium mod jar files as part of the Encrypted Premium Jar Protection feature.

## Components

### ModEncryptor (Interface)
- Defines the contract for encrypting jar files
- Method: `encrypt(jarBytes: ByteArray, sessionToken: String): ByteArray`
- Throws: `EncryptionException` on failure

### AesGcmModEncryptor (Implementation)
- Implements ModEncryptor using AES-256-GCM authenticated encryption
- Key features:
  - **Key Derivation**: PBKDF2-HMAC-SHA256 with 100,000 iterations
  - **Salt**: Fixed salt "nightshift.launcher.v1" (UTF-8 bytes)
  - **IV**: Random 12-byte IV generated per encryption using SecureRandom
  - **GCM Tag**: 128-bit (16 bytes) authentication tag
  - **Output Format**: `[IV(12 bytes)][ciphertext][GCM tag(16 bytes)]`

### EncryptionException
- Custom exception for encryption/decryption failures
- Wraps underlying cryptographic errors with context

## Security Properties

1. **Authenticated Encryption**: GCM mode provides both confidentiality and integrity
2. **IV Uniqueness**: Each encryption generates a fresh random IV
3. **Key Derivation**: Session token is stretched using PBKDF2 with 100k iterations
4. **Deterministic Keys**: Same session token always produces same key (required for decryption)
5. **Offline Support**: Empty session token ("") provides basic protection for offline users

## Implementation Details

### Key Derivation Parameters
```kotlin
Algorithm: PBKDF2-HMAC-SHA256
Salt: "nightshift.launcher.v1".toByteArray(UTF_8)
Iterations: 100,000
Key Length: 256 bits
```

### Encryption Process
1. Derive 256-bit AES key from session token using PBKDF2
2. Generate random 12-byte IV using SecureRandom
3. Initialize AES-256-GCM cipher with IV
4. Encrypt jar bytes (GCM automatically appends authentication tag)
5. Return: `IV + ciphertext (includes GCM tag)`

### Output Format
```
+------------------+------------------+------------------+
| IV (12 bytes)    | Ciphertext (N)   | GCM Tag (16)     |
+------------------+------------------+------------------+
```

## Usage Example

```kotlin
val encryptor = AesGcmModEncryptor()
val jarBytes = Files.readAllBytes(Paths.get("mod.jar"))
val sessionToken = "user-session-token-here"

try {
    val encrypted = encryptor.encrypt(jarBytes, sessionToken)
    Files.write(Paths.get("mod.jar.enc"), encrypted)
} catch (e: EncryptionException) {
    logger.error("Encryption failed", e)
}
```

## Requirements Satisfied

This implementation satisfies the following requirements from the spec:

- **Requirement 2.1**: AES-256-GCM encryption after download
- **Requirement 2.2**: Random 12-byte IV generation
- **Requirement 2.3**: PBKDF2-HMAC-SHA256 key derivation with 100k iterations
- **Requirement 2.5**: Correct output format [IV][ciphertext][tag]
- **Requirement 3.1**: Session token used for key derivation
- **Requirement 3.2**: Fixed salt "nightshift.launcher.v1"
- **Requirement 3.3**: 100,000 PBKDF2 iterations
- **Requirement 3.4**: 256-bit key generation

## Testing

Unit tests are provided in `AesGcmModEncryptorTest.kt` covering:
- Basic encryption functionality
- IV uniqueness (different outputs for same input)
- Empty session token handling
- Empty input handling
- Large input handling (1 MB)
- Different session tokens produce different outputs

## Next Steps

The next task (1.3) will implement `ModDecryptor` to reverse this encryption process:
- Extract IV from first 12 bytes
- Extract GCM tag from last 16 bytes
- Derive same key using PBKDF2
- Decrypt and verify GCM authentication tag
