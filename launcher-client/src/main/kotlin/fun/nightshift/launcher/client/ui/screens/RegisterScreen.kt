package `fun`.nightshift.launcher.client.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import `fun`.nightshift.launcher.client.auth.AuthModule
import `fun`.nightshift.launcher.client.auth.AuthValidation
import `fun`.nightshift.launcher.client.i18n.LocalLocalization
import `fun`.nightshift.launcher.client.ui.BrandGhostButton
import `fun`.nightshift.launcher.client.ui.BrandLockup
import `fun`.nightshift.launcher.client.ui.BrandPillButton
import `fun`.nightshift.launcher.client.ui.BrandStatusBanner
import `fun`.nightshift.launcher.client.ui.BrandTextField
import `fun`.nightshift.launcher.client.ui.CrystalAmbience
import `fun`.nightshift.launcher.client.ui.CrystalLogo
import `fun`.nightshift.launcher.client.ui.ErrorMessages
import `fun`.nightshift.launcher.client.ui.FieldErrorText
import `fun`.nightshift.launcher.client.ui.LockGlyph
import `fun`.nightshift.launcher.client.ui.MailGlyph
import `fun`.nightshift.launcher.client.ui.UserGlyph
import `fun`.nightshift.launcher.client.ui.WindowControlsRow
import `fun`.nightshift.launcher.client.ui.theme.NsColors
import `fun`.nightshift.launcher.shared.dto.AccountInfo
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

/**
 * Brand-styled registration screen — visual sibling of [LoginScreen].
 *
 * Validation runs client-side first (mirrors backend rules for snappier UX);
 * the backend remains the source of truth and any error code it returns is
 * surfaced through [ErrorMessages.forBackend].
 *
 * On success the launcher auto-logs the new user in (Req. 3.7) — that flow
 * is owned by [AuthModule.register], which delegates to login internally.
 */
@Composable
fun RegisterScreen(
    auth: AuthModule,
    onSuccess: (AccountInfo) -> Unit,
    onBackToLogin: () -> Unit,
    onMinimize: () -> Unit = {},
    onClose: () -> Unit = {},
) {
    val loc = LocalLocalization.current
    val scope = rememberCoroutineScope()

    var login by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var loginErr by remember { mutableStateOf<String?>(null) }
    var emailErr by remember { mutableStateOf<String?>(null) }
    var passErr by remember { mutableStateOf<String?>(null) }
    var confirmErr by remember { mutableStateOf<String?>(null) }
    var serverMessage by remember { mutableStateOf<String?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        CrystalAmbience()

        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            WindowControlsRow(onMinimize = onMinimize, onClose = onClose)
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CrystalLogo(modifier = Modifier.size(72.dp))
            Spacer(Modifier.height(8.dp))
            BrandLockup(subtitle = loc.t("register.title"))
            Spacer(Modifier.height(16.dp))

            Column(modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth()) {
                BrandTextField(
                    value = login,
                    onValueChange = { login = it; loginErr = null; serverMessage = null },
                    placeholder = loc.t("login.field.login"),
                    enabled = !loading,
                    isError = loginErr != null,
                    leadingIcon = { UserGlyph() },
                )
                FieldErrorText(loginErr)
                Spacer(Modifier.height(8.dp))

                BrandTextField(
                    value = email,
                    onValueChange = { email = it; emailErr = null; serverMessage = null },
                    placeholder = loc.t("register.field.email"),
                    enabled = !loading,
                    isError = emailErr != null,
                    leadingIcon = { MailGlyph() },
                )
                FieldErrorText(emailErr)
                Spacer(Modifier.height(8.dp))

                BrandTextField(
                    value = password,
                    onValueChange = { password = it; passErr = null; serverMessage = null },
                    placeholder = loc.t("login.field.password"),
                    enabled = !loading,
                    isError = passErr != null,
                    isPassword = true,
                    leadingIcon = { LockGlyph() },
                )
                FieldErrorText(passErr)
                Spacer(Modifier.height(8.dp))

                BrandTextField(
                    value = confirm,
                    onValueChange = { confirm = it; confirmErr = null; serverMessage = null },
                    placeholder = loc.t("register.field.passwordConfirm"),
                    enabled = !loading,
                    isError = confirmErr != null,
                    isPassword = true,
                    leadingIcon = { LockGlyph() },
                )
                FieldErrorText(confirmErr)
                Spacer(Modifier.height(10.dp))

                BrandStatusBanner(serverMessage)
                if (serverMessage != null) Spacer(Modifier.height(8.dp))

                BrandPillButton(
                    text = loc.t("register.button.register"),
                    onClick = {
                        val log = LoggerFactory.getLogger("RegisterScreen")
                        log.info("register button clicked, login='{}', email='{}'", login.trim(), email.trim())
                        val lErr = AuthValidation.validateLogin(login)
                        val eErr = AuthValidation.validateEmail(email)
                        val pErr = AuthValidation.validatePassword(password)
                        val cErr = AuthValidation.validatePasswordsMatch(password, confirm)
                        loginErr = lErr?.let { ErrorMessages.forValidation(loc, it) }
                        emailErr = eErr?.let { ErrorMessages.forValidation(loc, it) }
                        passErr = pErr?.let { ErrorMessages.forValidation(loc, it) }
                        confirmErr = cErr?.let { ErrorMessages.forValidation(loc, it) }
                        if (loginErr != null || emailErr != null || passErr != null || confirmErr != null) {
                            log.info("register validation failed: l={} e={} p={} c={}", lErr, eErr, pErr, cErr)
                            return@BrandPillButton
                        }
                        loading = true
                        serverMessage = null
                        val ceh = CoroutineExceptionHandler { _, t ->
                            log.error("Coroutine for register threw uncaught", t)
                            loading = false
                            serverMessage = ErrorMessages.forBackend(loc, t)
                        }
                        scope.launch(ceh) {
                            log.info("calling auth.register…")
                            try {
                                val res = auth.register(login.trim(), email.trim(), password, rememberMe = true)
                                log.info("auth.register returned: success={} err={}", res.isSuccess, res.exceptionOrNull()?.message)
                                loading = false
                                res.fold(
                                    onSuccess = onSuccess,
                                    onFailure = { serverMessage = ErrorMessages.forBackend(loc, it) }
                                )
                            } catch (t: Throwable) {
                                log.error("auth.register threw inside try/catch", t)
                                loading = false
                                serverMessage = ErrorMessages.forBackend(loc, t)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    loading = loading,
                )
                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(loc.t("register.link.haveAccount"), color = NsColors.TextSecondary)
                    Spacer(Modifier.width(6.dp))
                    BrandGhostButton(text = loc.t("login.title"), onClick = onBackToLogin)
                }
            }
        }
    }
}
