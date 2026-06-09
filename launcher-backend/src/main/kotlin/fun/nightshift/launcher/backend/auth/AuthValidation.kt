package `fun`.nightshift.launcher.backend.auth

/**
 * Centralised input validation for the auth endpoints.
 *
 * Each validator returns `null` on success or a short, user-facing error
 * message describing why the input was rejected. The route handlers turn
 * these messages into `400 validation_failed` responses.
 */
object AuthValidation {

    private val LOGIN_REGEX = Regex("^[a-zA-Z0-9_-]{3,20}$")
    private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")
    private val LETTER_REGEX = Regex(".*[A-Za-z].*")
    private val DIGIT_REGEX = Regex(".*\\d.*")

    fun validateLogin(login: String): String? = when {
        login.isBlank() -> "Login is required"
        !LOGIN_REGEX.matches(login) ->
            "Login must be 3-20 characters: letters, digits, underscore or hyphen"
        else -> null
    }

    fun validateEmail(email: String): String? = when {
        email.isBlank() -> "Email is required"
        email.length > 255 -> "Email is too long"
        !EMAIL_REGEX.matches(email) -> "Email format is invalid"
        else -> null
    }

    fun validatePassword(password: String): String? = when {
        password.length < 8 -> "Password must be at least 8 characters"
        !LETTER_REGEX.matches(password) -> "Password must contain at least one letter"
        !DIGIT_REGEX.matches(password) -> "Password must contain at least one digit"
        else -> null
    }

    fun validateHwid(hwid: String): String? = when {
        hwid.isBlank() -> "HWID is required"
        hwid.length > 64 -> "HWID is too long"
        else -> null
    }
}
