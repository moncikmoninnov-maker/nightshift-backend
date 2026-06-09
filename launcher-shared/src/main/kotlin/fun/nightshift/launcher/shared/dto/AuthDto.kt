package `fun`.nightshift.launcher.shared.dto

import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(
    val login: String,
    val email: String,
    val password: String,
    val hwid: String
)

@Serializable
data class LoginRequest(
    val login: String,
    val password: String,
    val hwid: String,
    val rememberMe: Boolean = false
)

@Serializable
data class LoginResponse(
    val token: String,
    val expiresAt: Long, // epoch milliseconds
    val account: AccountInfo
)

/**
 * Body for `POST /auth/validate`. The bearer session token is taken from the
 * `Authorization` header, so only the current HWID needs to travel in the body.
 */
@Serializable
data class ValidateRequest(
    val hwid: String
)

@Serializable
data class AccountInfo(
    val id: String,
    val login: String,
    val keyStatus: KeyStatus
)

@Serializable
data class PasswordResetRequest(
    val loginOrEmail: String,
    val comment: String
)

@Serializable
data class PasswordResetConfirm(
    val code: String,
    val newPassword: String
)
