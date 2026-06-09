package `fun`.nightshift.launcher.client.crypto

/**
 * Exception thrown when decryption operations fail.
 * 
 * This exception wraps underlying cryptographic errors (such as AEADBadTagException
 * for tampered/corrupted files) and provides context about the failure.
 */
class DecryptionException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
