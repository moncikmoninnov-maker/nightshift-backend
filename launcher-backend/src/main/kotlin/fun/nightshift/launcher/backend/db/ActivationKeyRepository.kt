package `fun`.nightshift.launcher.backend.db

import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.util.UUID

/**
 * Snapshot of a row in the `activation_keys` table.
 */
data class ActivationKeyRow(
    val id: UUID,
    val keyValue: String,
    val keyType: String,
    val accountId: UUID?,
    val hwid: String?,
    val activatedAt: Instant?,
    val expiresAt: Instant?,
    val status: String,
    val createdAt: Instant,
)

object ActivationKeyRepository {

    fun findByValue(keyValue: String): ActivationKeyRow? =
        ActivationKeys.selectAll().where { ActivationKeys.keyValue eq keyValue }
            .singleOrNull()?.toRow()

    /**
     * Finds the most recent active key for the given account+hwid pair. Returns
     * `null` when no key is bound or all keys have been expired/revoked.
     */
    fun findActiveByAccount(accountId: UUID, hwid: String): ActivationKeyRow? =
        ActivationKeys.selectAll().where {
            (ActivationKeys.accountId eq accountId) and
                    (ActivationKeys.hwid eq hwid) and
                    (ActivationKeys.status eq "active")
        }.singleOrNull()?.toRow()

    /** Inserts a brand-new, unactivated key (used by admin tooling). */
    fun create(keyValue: String, keyType: String): UUID {
        val inserted = ActivationKeys.insert {
            it[ActivationKeys.keyValue] = keyValue
            it[ActivationKeys.keyType] = keyType
            it[status] = "unused"
        }
        return inserted[ActivationKeys.id].value
    }

    /** Binds the key to an account/hwid and flips its status to `active`. */
    fun activate(
        keyId: UUID,
        accountId: UUID,
        hwid: String,
        activatedAt: Instant,
        expiresAt: Instant?,
    ): Int =
        ActivationKeys.update({
            (ActivationKeys.id eq keyId) and (ActivationKeys.status eq "unused")
        }) {
            it[ActivationKeys.accountId] = accountId
            it[ActivationKeys.hwid] = hwid
            it[ActivationKeys.activatedAt] = activatedAt
            it[ActivationKeys.expiresAt] = expiresAt
            it[status] = "active"
        }

    /** Marks the key as expired. Returns the number of affected rows. */
    fun markExpired(keyId: UUID): Int =
        ActivationKeys.update({ ActivationKeys.id eq keyId }) {
            it[status] = "expired"
        }

    private fun ResultRow.toRow(): ActivationKeyRow = ActivationKeyRow(
        id = this[ActivationKeys.id].value,
        keyValue = this[ActivationKeys.keyValue],
        keyType = this[ActivationKeys.keyType],
        accountId = this[ActivationKeys.accountId]?.value,
        hwid = this[ActivationKeys.hwid],
        activatedAt = this[ActivationKeys.activatedAt],
        expiresAt = this[ActivationKeys.expiresAt],
        status = this[ActivationKeys.status],
        createdAt = this[ActivationKeys.createdAt],
    )
}
