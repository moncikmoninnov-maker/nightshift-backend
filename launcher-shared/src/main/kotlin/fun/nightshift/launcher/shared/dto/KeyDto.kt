package `fun`.nightshift.launcher.shared.dto

import kotlinx.serialization.Serializable

/** Type of activation key — defines TTL behavior on the server. */
@Serializable
enum class KeyType {
    DAY,
    WEEK,
    MONTH,
    LIFETIME
}

/**
 * Sealed status hierarchy for an account's key state.
 * Serialized as a polymorphic JSON object via the `type` discriminator.
 */
@Serializable
sealed class KeyStatus {
    @Serializable
    data class Active(val info: KeyInfo) : KeyStatus()

    @Serializable
    data object Expired : KeyStatus()

    @Serializable
    data object NoKey : KeyStatus()
}

/**
 * Activated key metadata returned to the client.
 *
 * - [expiresAt] is null for [KeyType.LIFETIME] keys.
 * - [remainingTimeMs] is the time left until expiry in milliseconds; null for
 *   lifetime keys.
 * - [lifetime] is `true` for keys that never expire — the UI uses this as a
 *   marker to render "Lifetime" instead of a remaining-time counter.
 */
@Serializable
data class KeyInfo(
    val type: KeyType,
    val activatedAt: String,         // ISO-8601
    val expiresAt: String? = null,   // ISO-8601, null for lifetime
    val remainingTimeMs: Long? = null,
    val lifetime: Boolean = false
)

/**
 * Body for `POST /key/activate`. The owning account is resolved from the
 * bearer session token; only the key value and current HWID travel here.
 */
@Serializable
data class KeyActivateRequest(
    val keyValue: String,
    val hwid: String
)

/**
 * Body for `POST /key/validate`. The owning account is resolved from the
 * bearer session token; the HWID is sent so the backend can verify the
 * session/HWID pair before answering.
 */
@Serializable
data class KeyValidateRequest(
    val hwid: String
)
