package `fun`.nightshift.launcher.backend.auth

import de.mkammerer.argon2.Argon2Factory

/**
 * Argon2id password hasher, configured per OWASP recommendations.
 *
 * Parameters:
 *  - memory      = 64 MB (65 536 KiB)
 *  - iterations  = 3
 *  - parallelism = 2
 *
 * The plaintext password is wiped from memory immediately after the hash /
 * verify call returns, so callers should pass a fresh `CharArray` they don't
 * need afterwards.
 */
object PasswordHasher {

    private const val ITERATIONS = 3
    private const val MEMORY_KIB = 65_536 // 64 MB
    private const val PARALLELISM = 2

    private val argon2 = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id)

    /**
     * Hashes [password] with Argon2id. The returned string contains the
     * algorithm parameters, the random salt and the digest in the canonical
     * `$argon2id$...` encoding, ready for storage.
     *
     * The supplied [password] array is wiped before this method returns.
     */
    fun hash(password: CharArray): String {
        return try {
            argon2.hash(ITERATIONS, MEMORY_KIB, PARALLELISM, password)
        } finally {
            argon2.wipeArray(password)
        }
    }

    /**
     * Verifies [password] against an encoded Argon2id [hash]. The supplied
     * [password] array is wiped before this method returns.
     */
    fun verify(hash: String, password: CharArray): Boolean {
        return try {
            argon2.verify(hash, password)
        } finally {
            argon2.wipeArray(password)
        }
    }
}
