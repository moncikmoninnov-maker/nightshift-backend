package `fun`.nightshift.launcher.client.crypto

/**
 * Transforms a plaintext jar filename to its encrypted counterpart by appending ".enc" extension.
 *
 * This function is used when saving encrypted premium mod files to the cache directory.
 * The encrypted file format is: `{originalFileName}.enc`
 *
 * Examples:
 * - `toEncryptedFileName("NightShift Client Beta-2.3.jar")` → `"NightShift Client Beta-2.3.jar.enc"`
 * - `toEncryptedFileName("mod.jar")` → `"mod.jar.enc"`
 *
 * Edge case handling:
 * - If the filename already ends with ".enc", it will be appended again (e.g., "file.enc" → "file.enc.enc")
 * - This is intentional to maintain simplicity and avoid ambiguity
 *
 * @param original The original jar filename (e.g., "NightShift Client Beta-2.3.jar")
 * @return The encrypted filename with ".enc" extension appended
 *
 * @see fromEncryptedFileName
 */
fun toEncryptedFileName(original: String): String {
    return "$original.enc"
}

/**
 * Transforms an encrypted filename back to its original plaintext name by removing ".enc" extension.
 *
 * This function is used when decrypting premium mod files from the cache to the temporary directory.
 * The decrypted file should have its original name without the ".enc" suffix.
 *
 * Examples:
 * - `fromEncryptedFileName("NightShift Client Beta-2.3.jar.enc")` → `"NightShift Client Beta-2.3.jar"`
 * - `fromEncryptedFileName("mod.jar.enc")` → `"mod.jar"`
 *
 * Edge case handling:
 * - If the filename does not end with ".enc", it is returned unchanged
 * - This allows graceful handling of malformed filenames without throwing exceptions
 *
 * @param encrypted The encrypted filename with ".enc" extension (e.g., "NightShift Client Beta-2.3.jar.enc")
 * @return The original filename without ".enc" extension
 *
 * @see toEncryptedFileName
 */
fun fromEncryptedFileName(encrypted: String): String {
    return encrypted.removeSuffix(".enc")
}
