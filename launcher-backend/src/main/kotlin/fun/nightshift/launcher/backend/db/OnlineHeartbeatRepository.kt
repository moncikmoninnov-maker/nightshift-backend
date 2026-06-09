package `fun`.nightshift.launcher.backend.db

import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.util.UUID

/**
 * Heartbeat upserts and online-count queries.
 *
 * The PRIMARY KEY on `session_id` enables true upsert semantics; we model that
 * here as an `update` followed by an `insert` when no row was touched.
 */
object OnlineHeartbeatRepository {

    /** Updates `last_seen` for the session, inserting a row if missing. */
    fun upsert(sessionId: UUID) {
        val updated = OnlineHeartbeats.update({ OnlineHeartbeats.sessionId eq sessionId }) {
            it[lastSeen] = CurrentTimestamp
        }
        if (updated == 0) {
            OnlineHeartbeats.insert {
                it[OnlineHeartbeats.sessionId] = sessionId
            }
        }
    }

    /** Returns the number of heartbeats whose `last_seen` is at or after [since]. */
    fun countSince(since: Instant): Long =
        OnlineHeartbeats.selectAll().where { OnlineHeartbeats.lastSeen greaterEq since }.count()

    /** Removes the heartbeat row for the session (used on logout/exit). */
    fun delete(sessionId: UUID): Int =
        OnlineHeartbeats.deleteWhere { OnlineHeartbeats.sessionId eq sessionId }
}
