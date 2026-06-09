package `fun`.nightshift.launcher.client.crypto

/**
 * Exception thrown when encryption or decryption operations fail.
 * 
 * This exception wraps underlying cryptographic errors and provides
 * context about the failure.
 */
class EncryptionException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
