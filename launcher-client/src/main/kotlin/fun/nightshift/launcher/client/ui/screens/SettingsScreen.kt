package `fun`.nightshift.launcher.client.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `fun`.nightshift.launcher.client.config.LauncherConfig
import `fun`.nightshift.launcher.client.config.LauncherConfigStore
import `fun`.nightshift.launcher.client.i18n.LocalLocalization
import `fun`.nightshift.launcher.client.i18n.LocalizationManager
import `fun`.nightshift.launcher.client.ui.BrandGhostButton
import `fun`.nightshift.launcher.client.ui.BrandPillButton
import `fun`.nightshift.launcher.client.ui.theme.NsColors

/**
 * Brand-styled settings screen.
 *
 * Layout:
 *  * Header with title on the left and a "Назад" ghost button on the right
 *    (this is the missing back-button users were complaining about).
 *  * Memory slider, language picker (active locale highlighted), branded
 *    on/off toggles for sounds and telemetry, account logout.
 *  * Footer with the primary "Подтвердить" button on the right.
 *
 * The "Подтвердить" button persists the config and returns to the previous
 * screen; the "Назад" button discards unsaved edits and returns immediately.
 */
@Composable
fun SettingsScreen(
    initial: LauncherConfig,
    configStore: LauncherConfigStore,
    localization: LocalizationManager,
    onBack: (LauncherConfig) -> Unit,
    onLogout: () -> Unit,
) {
    val loc = LocalLocalization.current
    var config by remember { mutableStateOf(initial) }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 20.dp),
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                loc.t("settings.title"),
                color = NsColors.TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            BrandGhostButton(text = "← " + loc.t("common.cancel"), onClick = { onBack(initial) })
        }
        Spacer(Modifier.height(20.dp))

        // --- Memory ---
        SectionLabel(loc.t("settings.section.memory"))
        Text("${config.jvmMemoryMb} MB", color = NsColors.Accent, fontWeight = FontWeight.Medium)
        Slider(
            value = config.jvmMemoryMb.toFloat(),
            onValueChange = {
                val snapped = (it.toInt() / LauncherConfig.MEMORY_STEP_MB) * LauncherConfig.MEMORY_STEP_MB
                config = config.copy(jvmMemoryMb = snapped.coerceIn(LauncherConfig.MEMORY_MIN_MB, LauncherConfig.MEMORY_MAX_MB))
            },
            valueRange = LauncherConfig.MEMORY_MIN_MB.toFloat()..LauncherConfig.MEMORY_MAX_MB.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = NsColors.Accent,
                activeTrackColor = NsColors.Accent,
                inactiveTrackColor = NsColors.AccentMuted.copy(alpha = 0.3f),
            ),
        )
        Spacer(Modifier.height(8.dp))

        // --- Language ---
        SectionLabel(loc.t("settings.section.language"))
        Row {
            LanguagePill(label = loc.t("settings.lang.ru"), active = config.language == "ru") {
                config = config.copy(language = "ru")
                localization.setLocale("ru")
            }
            Spacer(Modifier.width(10.dp))
            LanguagePill(label = loc.t("settings.lang.en"), active = config.language == "en") {
                config = config.copy(language = "en")
                localization.setLocale("en")
            }
        }
        Spacer(Modifier.height(16.dp))

        // --- Sound ---
        SectionLabel(loc.t("settings.section.sound"))
        BrandToggleRow(
            label = loc.t("settings.sound.toggle"),
            checked = config.soundEnabled,
            onCheckedChange = { config = config.copy(soundEnabled = it) },
        )
        Spacer(Modifier.height(8.dp))

        // --- Telemetry ---
        BrandToggleRow(
            label = loc.t("settings.telemetry"),
            checked = config.telemetryEnabled == true,
            onCheckedChange = { config = config.copy(telemetryEnabled = it) },
        )
        Spacer(Modifier.height(16.dp))

        // --- Account ---
        SectionLabel(loc.t("settings.section.account"))
        BrandGhostButton(text = loc.t("main.button.logout"), onClick = onLogout)

        Spacer(Modifier.weight(1f))

        // Footer with the primary save button
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            BrandPillButton(
                text = loc.t("common.confirm"),
                onClick = {
                    configStore.save(config)
                    onBack(config)
                },
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        color = NsColors.TextSecondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

/**
 * Branded language pill — fills with accent when active, transparent
 * otherwise. Animated background colour change on tap.
 */
@Composable
private fun LanguagePill(label: String, active: Boolean, onClick: () -> Unit) {
    val bg = if (active) NsColors.Accent else NsColors.Surface
    val textColor = if (active) Color.White else NsColors.TextPrimary
    val scale by animateFloatAsState(if (active) 1.0f else 0.96f, tween(200))
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Text(label, color = textColor, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium)
    }
}

/**
 * Branded animated toggle row. Tappable label switches the value, the
 * thumb slides smoothly between off/on positions and the track tint
 * cross-fades between [NsColors.Surface] and [NsColors.Accent].
 */
@Composable
private fun BrandToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BrandToggle(checked = checked)
        Spacer(Modifier.width(12.dp))
        Text(label, color = NsColors.TextPrimary, fontSize = 14.sp)
    }
}

@Composable
private fun BrandToggle(checked: Boolean) {
    val trackWidth = 48.dp
    val trackHeight = 26.dp
    val thumbSize = 20.dp
    val thumbOffsetTarget = if (checked) (trackWidth - thumbSize - 4.dp) else 4.dp
    val thumbOffset by animateFloatAsState(thumbOffsetTarget.value, tween(220))
    val trackColor = if (checked) NsColors.Accent else NsColors.Surface
    Box(
        modifier = Modifier
            .size(width = trackWidth, height = trackHeight)
            .clip(RoundedCornerShape(50))
            .background(trackColor),
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset.dp, y = ((trackHeight - thumbSize) / 2).value.dp)
                .size(thumbSize)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}
