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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `fun`.nightshift.launcher.client.ui.BrandLockup
import `fun`.nightshift.launcher.client.ui.BrandPillButton
import `fun`.nightshift.launcher.client.ui.BrandStatusBanner
import `fun`.nightshift.launcher.client.ui.CrystalAmbience
import `fun`.nightshift.launcher.client.ui.CrystalLogo
import `fun`.nightshift.launcher.client.ui.WindowControlsRow
import `fun`.nightshift.launcher.client.ui.theme.NsColors

/**
 * Self-update progress screen.
 *
 * Lifecycle:
 *  - [progress01] = 0..1 while the new .exe is being downloaded.
 *  - [errorMessage] is non-null if SHA-256 verification failed or the network
 *    error wasn't caught upstream — the screen then exposes a "Повторить"
 *    button. While [errorMessage] is null and [progress01] < 1f the screen
 *    shows an indeterminate-style progress bar so the user always sees
 *    motion even if Content-Length was missing from the response.
 *  - The screen is intentionally dead-end: no logout, no play. Phase B
 *    requirement 12.2 mandates blocking transition to Login_Screen until
 *    the update completes.
 */
@Composable
fun UpdateScreen(
    targetVersion: String,
    isRollback: Boolean,
    progress01: Float,
    releaseNotes: String,
    errorMessage: String? = null,
    onRetry: () -> Unit = {},
    onMinimize: () -> Unit = {},
    onClose: () -> Unit = {},
) {
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
            BrandLockup(
                subtitle = if (isRollback) "Откат до версии $targetVersion" else "Обновление до версии $targetVersion"
            )
            Spacer(Modifier.height(20.dp))

            Column(modifier = Modifier.widthIn(max = 460.dp).fillMaxWidth()) {
                if (releaseNotes.isNotBlank()) {
                    Text(
                        text = releaseNotes,
                        color = NsColors.TextSecondary,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(20.dp))
                }

                if (errorMessage != null) {
                    BrandStatusBanner(errorMessage)
                    Spacer(Modifier.height(16.dp))
                    BrandPillButton(
                        text = "Повторить",
                        onClick = onRetry,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    val displayProgress = progress01.coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { displayProgress },
                        color = NsColors.Accent,
                        trackColor = NsColors.Surface,
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Загрузка: ${(displayProgress * 100).toInt()}%",
                        color = NsColors.TextSecondary,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}
