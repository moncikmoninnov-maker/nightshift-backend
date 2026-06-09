package `fun`.nightshift.launcher.client.crypto

/**
 * Exception thrown when preparation of protected mods temporary directory fails.
 * 
 * This exception indicates a critical failure in the preparation process,
 * such as inability to create the temporary directory or other file system errors.
 * 
 * Note: Individual file decryption failures do NOT throw this exception.
 * The preparer uses graceful degradation for decryption errors.
 */
class PreparationException(message: String, cause: Throwable? = null) : Exception(message, cause)
