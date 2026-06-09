package `fun`.nightshift.launcher.client.ui

import `fun`.nightshift.launcher.client.api.BackendException
import `fun`.nightshift.launcher.client.auth.ValidationError
import `fun`.nightshift.launcher.client.i18n.LocalizationManager

/**
 * Maps backend error codes (and client-side [ValidationError] flags) onto
 * localised messages. All look-ups go through [LocalizationManager] so the
 * UI never has to embed strings directly.
 */
object ErrorMessages {
    fun forBackend(loc: LocalizationManager, t: Throwable): String {
        val be = t as? BackendException
        val key = when (be?.code) {
            "account_exists" -> "error.account_exists"
            "invalid_credentials" -> "error.invalid_credentials"
            "hwid_mismatch" -> "error.hwid_mismatch"
            "rate_limited" -> "error.rate_limited"
            "client_outdated" -> "error.client_outdated"
            "key_not_found" -> "error.key_not_found"
            "key_already_used" -> "error.key_already_used"
            "key_expired" -> "error.key_expired"
            "network_error" -> "error.network"
            else -> null
        }
        return when {
            key != null -> loc.t(key)
            be?.displayMessage?.isNotBlank() == true -> be.displayMessage
            else -> loc.t("error.unknown")
        }
    }

    fun forValidation(loc: LocalizationManager, error: ValidationError): String = loc.t(
        when (error) {
            ValidationError.Required -> "error.required"
            ValidationError.LoginFormat -> "error.login_format"
            ValidationError.EmailFormat -> "error.email_format"
            ValidationError.PasswordTooShort -> "error.password_short"
            ValidationError.PasswordWeak -> "error.password_weak"
            ValidationError.PasswordsMismatch -> "error.passwords_mismatch"
            ValidationError.CodeLength -> "error.code_length"
        }
    )
}
