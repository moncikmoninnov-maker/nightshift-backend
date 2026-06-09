package `fun`.nightshift.launcher.client.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `fun`.nightshift.launcher.client.auth.AuthModule
import `fun`.nightshift.launcher.client.auth.AuthValidation
import `fun`.nightshift.launcher.client.auth.ValidationError
import `fun`.nightshift.launcher.client.config.LauncherConfig
import `fun`.nightshift.launcher.client.config.LauncherConfigStore
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
import `fun`.nightshift.launcher.client.ui.UserGlyph
import `fun`.nightshift.launcher.client.ui.WindowControlsRow
import `fun`.nightshift.launcher.client.ui.theme.NsColors
import `fun`.nightshift.launcher.shared.dto.AccountInfo
import kotlinx.coroutines.launch

/**
 * Brand-styled login screen.
 *
 * Mirrors the visual language of [MainScreen]:
 *  * Dark-purple gradient backdrop with decorative crystal clusters.
 *  * "NS" crystal mark + brand title at the top.
 *  * Centered card-less form (the dark gradient is the card).
 *  * Branded text fields with leading user/lock icons.
 *  * Big purple gradient pill action button.
 *  * Window minimise / close chips top-right.
 */
@Composable
fun LoginScreen(
    auth: AuthModule,
    config: LauncherConfig,
    configStore: LauncherConfigStore,
    onSuccess: (AccountInfo) -> Unit,
    onRegister: () -> Unit,
    onForgotPassword: () -> Unit,
    onMinimize: () -> Unit = {},
    onClose: () -> Unit = {},
) {
    val loc = LocalLocalization.current
    val scope = rememberCoroutineScope()

    var login by remember { mutableStateOf(config.lastAccountLogin.orEmpty()) }
    var password by remember { mutableStateOf("") }
    var rememberMe by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(false) }
    var loginError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var serverMessage by remember { mutableStateOf<String?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        CrystalAmbience()

        // Top-right window controls
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            WindowControlsRow(onMinimize = onMinimize, onClose = onClose)
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CrystalLogo(modifier = Modifier.size(72.dp))
            Spacer(Modifier.height(8.dp))
            BrandLockup(subtitle = loc.t("login.title"))
            Spacer(Modifier.height(20.dp))

            Column(modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth()) {
                BrandTextField(
                    value = login,
                    onValueChange = { login = it; loginError = null; serverMessage = null },
                    placeholder = loc.t("login.field.login"),
                    enabled = !loading,
                    isError = loginError != null,
                    leadingIcon = { UserGlyph() },
                )
                FieldErrorText(loginError)
                Spacer(Modifier.height(12.dp))

                BrandTextField(
                    value = password,
                    onValueChange = { password = it; passwordError = null; serverMessage = null },
                    placeholder = loc.t("login.field.password"),
                    enabled = !loading,
                    isError = passwordError != null,
                    isPassword = true,
                    leadingIcon = { LockGlyph() },
                )
                FieldErrorText(passwordError)
                Spacer(Modifier.height(14.dp))

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    BrandCheckbox(
                        checked = rememberMe,
                        onCheckedChange = { rememberMe = it },
                        label = loc.t("login.field.rememberMe"),
                    )
                    Spacer(Modifier.weight(1f))
                    BrandGhostButton(
                        text = loc.t("login.link.forgotPassword"),
                        onClick = onForgotPassword,
                    )
                }
                Spacer(Modifier.height(8.dp))

                BrandStatusBanner(serverMessage)
                if (serverMessage != null) Spacer(Modifier.height(12.dp))

                BrandPillButton(
                    text = loc.t("login.button.login"),
                    onClick = {
                        val lErr = AuthValidation.validateLogin(login)
                        val pErr = if (password.isBlank()) ValidationError.Required else null
                        loginError = lErr?.let { ErrorMessages.forValidation(loc, it) }
                        passwordError = pErr?.let { ErrorMessages.forValidation(loc, it) }
                        if (loginError != null || passwordError != null) return@BrandPillButton
                        loading = true
                        serverMessage = null
                        scope.launch {
                            try {
                                val res = auth.login(login.trim(), password, rememberMe)
                                loading = false
                                res.fold(
                                    onSuccess = { acc ->
                                        configStore.save(config.copy(lastAccountLogin = login.trim()))
                                        onSuccess(acc)
                                    },
                                    onFailure = { err ->
                                        serverMessage = ErrorMessages.forBackend(loc, err)
                                        password = "" // Req. 6.6 — clear password on auth failure.
                                    }
                                )
                            } catch (t: Throwable) {
                                loading = false
                                serverMessage = ErrorMessages.forBackend(loc, t)
                                password = ""
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    loading = loading,
                )
                Spacer(Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(loc.t("register.link.haveAccount").replaceFirstChar { it.titlecase() }
                        .let { if (it.endsWith("?")) it else "$it?" }, color = NsColors.TextSecondary)
                    Spacer(Modifier.width(6.dp))
                    BrandGhostButton(text = loc.t("login.link.register"), onClick = onRegister)
                }
            }
        }
    }
}

/**
 * Branded checkbox: a small purple square with a checkmark, plus a label.
 * Avoids Material's default outlined look so it fits the rest of the UI.
 */
@Composable
private fun BrandCheckbox(checked: Boolean, onCheckedChange: (Boolean) -> Unit, label: String) {
    Row(
        modifier = Modifier.clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (checked) NsColors.Accent else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Text("✓", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            } else {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Transparent),
                ) {
                    // Border via inset rectangle
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(NsColors.Outline.copy(alpha = 0.5f)),
                    )
                    Box(
                        modifier = Modifier
                            .padding(1.dp)
                            .size(16.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(NsColors.BackgroundBottom),
                    )
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(label, color = NsColors.TextPrimary, fontSize = 13.sp)
    }
}
