package `fun`.nightshift.launcher.client.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
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
import `fun`.nightshift.launcher.client.ui.ErrorMessages
import `fun`.nightshift.launcher.client.ui.NsLinkButton
import `fun`.nightshift.launcher.client.ui.NsPrimaryButton
import `fun`.nightshift.launcher.client.ui.NsStatusMessage
import `fun`.nightshift.launcher.client.ui.NsTextField
import `fun`.nightshift.launcher.client.ui.NsVerticalSpacer
import kotlinx.coroutines.launch

/**
 * Two-step "forgot password" flow:
 *   1. user submits login+comment → admin gets a request in Admin_Console;
 *   2. user receives an 8-char code out-of-band, enters it + new password.
 *
 * The same screen handles both steps via a [Step] toggle to keep the flow
 * compact and avoid an extra navigation hop.
 */
@Composable
fun PasswordResetScreen(
    auth: AuthModule,
    onBackToLogin: () -> Unit,
) {
    val loc = LocalLocalization.current
    val scope = rememberCoroutineScope()

    var step by remember { mutableStateOf(Step.RequestReset) }
    var loginOrEmail by remember { mutableStateOf("") }
    var comment by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(loc.t("reset.title"), style = MaterialTheme.typography.titleLarge)
        NsVerticalSpacer(24)

        Column(modifier = Modifier.widthIn(max = 360.dp)) {
            when (step) {
                Step.RequestReset -> {
                    NsTextField(loginOrEmail, { loginOrEmail = it; error = null },
                        label = loc.t("reset.field.loginOrEmail"), enabled = !loading)
                    NsVerticalSpacer(12)
                    NsTextField(comment, { comment = it },
                        label = loc.t("reset.field.comment"), enabled = !loading)
                    NsVerticalSpacer(12)
                    NsStatusMessage(error)
                    NsStatusMessage(info, isError = false)
                    NsVerticalSpacer(12)
                    NsPrimaryButton(
                        text = loc.t("reset.button.requestReset"),
                        onClick = {
                            val v = AuthValidation.validateLoginOrEmail(loginOrEmail)
                            if (v != null) {
                                error = ErrorMessages.forValidation(loc, v); return@NsPrimaryButton
                            }
                            loading = true; error = null; info = null
                            scope.launch {
                                val r = auth.requestPasswordReset(loginOrEmail.trim(), comment.trim())
                                loading = false
                                r.fold(
                                    onSuccess = {
                                        info = loc.t("reset.message.requested")
                                        step = Step.ConfirmReset
                                    },
                                    onFailure = { error = ErrorMessages.forBackend(loc, it) }
                                )
                            }
                        },
                        modifier = Modifier.fillMaxSize().widthIn(max = 360.dp),
                        loading = loading,
                    )
                }
                Step.ConfirmReset -> {
                    NsTextField(code, { code = it.uppercase(); error = null },
                        label = loc.t("reset.field.code"), enabled = !loading)
                    NsVerticalSpacer(12)
                    NsTextField(newPassword, { newPassword = it; error = null },
                        label = loc.t("reset.field.newPassword"), isPassword = true, enabled = !loading)
                    NsVerticalSpacer(12)
                    NsStatusMessage(error)
                    NsStatusMessage(info, isError = false)
                    NsVerticalSpacer(12)
                    NsPrimaryButton(
                        text = loc.t("reset.button.confirm"),
                        onClick = {
                            val cErr = AuthValidation.validateResetCode(code)
                            val pErr = AuthValidation.validatePassword(newPassword)
                            if (cErr != null) {
                                error = ErrorMessages.forValidation(loc, cErr); return@NsPrimaryButton
                            }
                            if (pErr != null) {
                                error = ErrorMessages.forValidation(loc, pErr); return@NsPrimaryButton
                            }
                            loading = true; error = null
                            scope.launch {
                                val r = auth.confirmPasswordReset(code.trim(), newPassword)
                                loading = false
                                r.fold(
                                    onSuccess = { onBackToLogin() },
                                    onFailure = { error = ErrorMessages.forBackend(loc, it) }
                                )
                            }
                        },
                        modifier = Modifier.fillMaxSize().widthIn(max = 360.dp),
                        loading = loading,
                    )
                }
            }
            NsVerticalSpacer(8)
            NsLinkButton(text = loc.t("login.title"), onClick = onBackToLogin)
        }
    }
}

private enum class Step { RequestReset, ConfirmReset }
