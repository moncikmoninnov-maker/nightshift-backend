package `fun`.nightshift.launcher.backend.db

import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.util.UUID

/**
 * Snapshot of a row in the `accounts` table.
 */
data class AccountRow(
    val id: UUID,
    val login: String,
    val email: String,
    val passwordHash: String,
    val hwid: String,
    val hwidStatus: String,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/**
 * CRUD-level access for the `accounts` table. All methods must run inside a
 * transaction (see [DatabaseFactory.dbQuery]).
 */
object AccountRepository {

    fun findByLogin(login: String): AccountRow? =
        Accounts.selectAll().where { Accounts.login eq login }.singleOrNull()?.toRow()

    fun findByEmail(email: String): AccountRow? =
        Accounts.selectAll().where { Accounts.email eq email }.singleOrNull()?.toRow()

    fun findById(id: UUID): AccountRow? =
        Accounts.selectAll().where { Accounts.id eq id }.singleOrNull()?.toRow()

    fun create(
        login: String,
        email: String,
        passwordHash: String,
        hwid: String,
        hwidStatus: String = "locked",
    ): UUID {
        val inserted = Accounts.insert {
            it[Accounts.login] = login
            it[Accounts.email] = email
            it[Accounts.passwordHash] = passwordHash
            it[Accounts.hwid] = hwid
            it[Accounts.hwidStatus] = hwidStatus
        }
        return inserted[Accounts.id].value
    }

    /** Updates the stored HWID and resets `hwid_status` to `locked`. */
    fun updateHwid(accountId: UUID, newHwid: String, newStatus: String = "locked"): Int =
        Accounts.update({ Accounts.id eq accountId }) {
            it[hwid] = newHwid
            it[hwidStatus] = newStatus
            it[updatedAt] = CurrentTimestamp
        }

    fun updatePassword(accountId: UUID, newPasswordHash: String): Int =
        Accounts.update({ Accounts.id eq accountId }) {
            it[passwordHash] = newPasswordHash
            it[updatedAt] = CurrentTimestamp
        }

    fun updateHwidStatus(accountId: UUID, newStatus: String): Int =
        Accounts.update({ Accounts.id eq accountId }) {
            it[hwidStatus] = newStatus
            it[updatedAt] = CurrentTimestamp
        }

    private fun ResultRow.toRow(): AccountRow = AccountRow(
        id = this[Accounts.id].value,
        login = this[Accounts.login],
        email = this[Accounts.email],
        passwordHash = this[Accounts.passwordHash],
        hwid = this[Accounts.hwid],
        hwidStatus = this[Accounts.hwidStatus],
        createdAt = this[Accounts.createdAt],
        updatedAt = this[Accounts.updatedAt],
    )
}
