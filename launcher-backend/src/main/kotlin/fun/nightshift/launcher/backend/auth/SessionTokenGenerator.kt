package `fun`.nightshift.launcher.backend.auth

import java.security.SecureRandom

/**
 * Cryptographically-strong opaque session token generator.
 *
 * Produces 32 random bytes (256 bits) encoded as a 64-character lowercase
 * hex string, matching the `VARCHAR(64)` column on `sessions.token`.
 */
object SessionTokenGenerator {

    private const val TOKEN_BYTES = 32

    private val random = SecureRandom()

    fun generate(): String {
        val buffer = ByteArray(TOKEN_BYTES)
        random.nextBytes(buffer)
        return buffer.toHex()
    }

    private fun ByteArray.toHex(): String {
        val hex = StringBuilder(size * 2)
        for (b in this) {
            val v = b.toInt() and 0xFF
            hex.append(HEX_CHARS[v ushr 4])
            hex.append(HEX_CHARS[v and 0x0F])
        }
        return hex.toString()
    }

    private val HEX_CHARS = "0123456789abcdef".toCharArray()
}

/** TTL for sessions issued by `/auth/register` and `/auth/login`. */
const val SESSION_TTL_DAYS: Long = 30L
