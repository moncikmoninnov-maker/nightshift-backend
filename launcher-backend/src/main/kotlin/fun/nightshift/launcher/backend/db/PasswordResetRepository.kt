package `fun`.nightshift.launcher.backend.db

import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.util.UUID

data class PasswordResetRow(
    val id: UUID,
    val accountId: UUID,
    val comment: String?,
    val resetCode: String?,
    val codeExpiresAt: Instant?,
    val status: String,
    val createdAt: Instant,
)

object PasswordResetRepository {

    fun createRequest(accountId: UUID, comment: String?): UUID {
        val inserted = PasswordResetRequests.insert {
            it[PasswordResetRequests.accountId] = accountId
            it[PasswordResetRequests.comment] = comment
            it[status] = "pending"
        }
        return inserted[PasswordResetRequests.id].value
    }

    /** Operator-assigned reset code with an expiry — sets status to `approved`. */
    fun assignCode(requestId: UUID, code: String, expiresAt: Instant): Int =
        PasswordResetRequests.update({ PasswordResetRequests.id eq requestId }) {
            it[resetCode] = code
            it[codeExpiresAt] = expiresAt
            it[status] = "approved"
        }

    fun findByCode(code: String): PasswordResetRow? =
        PasswordResetRequests.selectAll().where {
            (PasswordResetRequests.resetCode eq code) and
                    (PasswordResetRequests.status eq "approved")
        }.singleOrNull()?.toRow()

    fun findById(id: UUID): PasswordResetRow? =
        PasswordResetRequests.selectAll().where { PasswordResetRequests.id eq id }
            .singleOrNull()?.toRow()

    fun markUsed(requestId: UUID): Int =
        PasswordResetRequests.update({ PasswordResetRequests.id eq requestId }) {
            it[status] = "used"
        }

    fun markExpired(requestId: UUID): Int =
        PasswordResetRequests.update({ PasswordResetRequests.id eq requestId }) {
            it[status] = "expired"
        }

    private fun ResultRow.toRow(): PasswordResetRow = PasswordResetRow(
        id = this[PasswordResetRequests.id].value,
        accountId = this[PasswordResetRequests.accountId].value,
        comment = this[PasswordResetRequests.comment],
        resetCode = this[PasswordResetRequests.resetCode],
        codeExpiresAt = this[PasswordResetRequests.codeExpiresAt],
        status = this[PasswordResetRequests.status],
        createdAt = this[PasswordResetRequests.createdAt],
    )
}
