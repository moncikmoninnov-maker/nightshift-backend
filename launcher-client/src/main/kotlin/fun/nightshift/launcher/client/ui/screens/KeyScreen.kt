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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import `fun`.nightshift.launcher.client.i18n.LocalLocalization
import `fun`.nightshift.launcher.client.keys.KeyModule
import `fun`.nightshift.launcher.client.ui.BrandGhostButton
import `fun`.nightshift.launcher.client.ui.BrandLockup
import `fun`.nightshift.launcher.client.ui.BrandPillButton
import `fun`.nightshift.launcher.client.ui.BrandStatusBanner
import `fun`.nightshift.launcher.client.ui.BrandTextField
import `fun`.nightshift.launcher.client.ui.CrystalAmbience
import `fun`.nightshift.launcher.client.ui.CrystalLogo
import `fun`.nightshift.launcher.client.ui.ErrorMessages
import `fun`.nightshift.launcher.client.ui.KeyGlyph
import `fun`.nightshift.launcher.client.ui.WindowControlsRow
import `fun`.nightshift.launcher.shared.dto.KeyInfo
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

/**
 * Brand-styled activation-key screen — visual sibling of [LoginScreen] /
 * [RegisterScreen].
 *
 * The status banner is hidden until the user actually presses
 * "Активировать" so a fresh KeyScreen never shows a stale "ключ истёк" /
 * "нет ключа" message before the user has done anything.
 */
@Composable
fun KeyScreen(
    keys: KeyModule,
    initialMessage: String? = null, // intentionally unused — kept for API compat
    onActivated: (KeyInfo) -> Unit,
    onLogout: () -> Unit,
    onMinimize: () -> Unit = {},
    onClose: () -> Unit = {},
) {
    val loc = LocalLocalization.current
    val scope = rememberCoroutineScope()

    var keyValue by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    // Banner is empty by default; it only gets a value after the user clicks
    // the activate button and the backend reports an outcome.
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
            modifier = Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CrystalLogo(modifier = Modifier.size(72.dp))
            Spacer(Modifier.height(8.dp))
            BrandLockup(subtitle = loc.t("key.title"))
            Spacer(Modifier.height(20.dp))

            Column(modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth()) {
                BrandTextField(
                    value = keyValue,
                    onValueChange = { keyValue = it; serverMessage = null },
                    placeholder = loc.t("key.field.key"),
                    enabled = !loading,
                    leadingIcon = { KeyGlyph() },
                )
                Spacer(Modifier.height(12.dp))

                BrandStatusBanner(serverMessage)
                if (serverMessage != null) Spacer(Modifier.height(12.dp))

                BrandPillButton(
                    text = loc.t("key.button.activate"),
                    onClick = {
                        val log = LoggerFactory.getLogger("KeyScreen")
                        log.info("activate clicked, key='{}'", keyValue.trim())
                        if (keyValue.isBlank()) {
                            serverMessage = ErrorMessages.forValidation(
                                loc, `fun`.nightshift.launcher.client.auth.ValidationError.Required
                            )
                            return@BrandPillButton
                        }
                        loading = true
                        serverMessage = null
                        val ceh = CoroutineExceptionHandler { _, t ->
                            log.error("activate threw uncaught", t)
                            loading = false
                            serverMessage = ErrorMessages.forBackend(loc, t)
                        }
                        scope.launch(ceh) {
                            try {
                                val res = keys.activate(keyValue.trim())
                                loading = false
                                res.fold(
                                    onSuccess = onActivated,
                                    onFailure = { serverMessage = ErrorMessages.forBackend(loc, it) }
                                )
                            } catch (t: Throwable) {
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
                ) {
                    BrandGhostButton(text = loc.t("main.button.logout"), onClick = onLogout)
                }
            }
        }
    }
}
