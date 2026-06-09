package `fun`.nightshift.launcher.backend.db

import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import java.time.Instant

/**
 * Persistent log of login attempts. Used by the rate limiter to count failed
 * logins for a given login over a sliding window.
 */
object LoginAttemptRepository {

    fun record(login: String, ipAddress: String, success: Boolean) {
        LoginAttempts.insert {
            it[LoginAttempts.login] = login
            it[LoginAttempts.ipAddress] = ipAddress
            it[LoginAttempts.success] = success
        }
    }

    /**
     * Counts failed login attempts for [login] since [since]. Successful
     * attempts are intentionally ignored: only failures matter for lockouts.
     */
    fun countFailedSince(login: String, since: Instant): Long =
        LoginAttempts.selectAll().where {
            (LoginAttempts.login eq login) and
                    (LoginAttempts.attemptedAt greaterEq since) and
                    (LoginAttempts.success eq false)
        }.count()
}
