package `fun`.nightshift.launcher.client.auth

/**
 * Client-side input validators for the Login / Register / Reset screens.
 *
 * Mirrors the rules enforced by the backend (Requirements 3.1–3.3, 6.1) so
 * the UI can show inline errors without a round-trip. The backend remains
 * the source of truth; the client copy is purely for UX.
 */
object AuthValidation {
    /** Login: 3..20 chars, alphanumeric + `_`. */
    private val LOGIN_REGEX = Regex("^[A-Za-z0-9_]{3,20}$")

    /** Loose RFC 5322 — good enough for client-side filtering. */
    private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    fun validateLogin(value: String): ValidationError? = when {
        value.isBlank() -> ValidationError.Required
        !LOGIN_REGEX.matches(value) -> ValidationError.LoginFormat
        else -> null
    }

    fun validateEmail(value: String): ValidationError? = when {
        value.isBlank() -> ValidationError.Required
        !EMAIL_REGEX.matches(value) -> ValidationError.EmailFormat
        else -> null
    }

    fun validatePassword(value: String): ValidationError? = when {
        value.isBlank() -> ValidationError.Required
        value.length < 8 -> ValidationError.PasswordTooShort
        !value.any { it.isLetter() } || !value.any { it.isDigit() } -> ValidationError.PasswordWeak
        else -> null
    }

    fun validatePasswordsMatch(password: String, confirm: String): ValidationError? =
        if (password != confirm) ValidationError.PasswordsMismatch else null

    fun validateLoginOrEmail(value: String): ValidationError? = when {
        value.isBlank() -> ValidationError.Required
        value.contains('@') -> validateEmail(value)
        else -> validateLogin(value)
    }

    fun validateResetCode(value: String): ValidationError? = when {
        value.isBlank() -> ValidationError.Required
        value.length != 8 -> ValidationError.CodeLength
        else -> null
    }
}

/**
 * Localizable validation outcome. The UI looks up the corresponding string
 * via the localization manager — codes here stay locale-agnostic.
 */
enum class ValidationError {
    Required,
    LoginFormat,
    EmailFormat,
    PasswordTooShort,
    PasswordWeak,
    PasswordsMismatch,
    CodeLength,
}
