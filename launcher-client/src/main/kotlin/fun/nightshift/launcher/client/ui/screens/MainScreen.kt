package `fun`.nightshift.launcher.client.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
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
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.hoverable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import `fun`.nightshift.launcher.client.auth.AuthModule
import `fun`.nightshift.launcher.client.config.LauncherConfig
import `fun`.nightshift.launcher.client.config.LauncherConfigStore
import `fun`.nightshift.launcher.client.i18n.LocalLocalization
import `fun`.nightshift.launcher.client.i18n.LocalizationManager
import `fun`.nightshift.launcher.client.online.OnlineModule
import `fun`.nightshift.launcher.client.paths.LauncherPaths
import `fun`.nightshift.launcher.client.ui.theme.NsColors
import `fun`.nightshift.launcher.shared.dto.AccountInfo
import `fun`.nightshift.launcher.shared.dto.KeyInfo
import `fun`.nightshift.launcher.shared.dto.KeyStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.awt.Desktop

/**
 * Brand-styled launcher home screen.
 *
 * Layout follows the reference mock-up but stays inside our dark-purple
 * palette:
 *  * Top-center: brand title "NightShift Client" + uppercase subtitle
 *               "ИГРОВОЙ КЛИЕНТ".
 *  * Top-right: rounded online counter pill, plus a circular logout chip
 *               followed by minimise / close window controls.
 *  * Body: animated decorative "crystal" blobs as background ambience.
 *  * Bottom-right: round folder icon, "Настройки" pill, large "Играть"
 *                  primary action.
 *
 * Side-effects:
 *  * The [OnlineModule] is started on first composition; the cached count
 *    is refreshed every 30 seconds by the module itself.
 *  * "Папка" reveals %APPDATA%/NightShiftClient/ via [Desktop.getDesktop].
 */
@Composable
fun MainScreen(
    auth: AuthModule,
    online: OnlineModule,
    paths: LauncherPaths,
    config: LauncherConfig,
    configStore: LauncherConfigStore,
    account: AccountInfo,
    onLogout: () -> Unit,
    onSettings: () -> Unit,
    onPlay: () -> Unit,
    onActivateKey: () -> Unit,
    onMinimize: () -> Unit,
    onClose: () -> Unit,
    launching: Boolean = false,
    launchStatus: String? = null,
) {
    val loc = LocalLocalization.current
    var onlineCount by remember { mutableStateOf<Int?>(online.cachedCount) }

    LaunchedEffect(Unit) {
        online.start { onlineCount = it }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Decorative purple crystal blobs — pure ambience layer.
        CrystalAmbience()

        Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
            HeaderRow(
                onlineCount = onlineCount,
                onLogout = onLogout,
                onMinimize = onMinimize,
                onClose = onClose,
            )

            Spacer(Modifier.height(8.dp))

            BrandLockup()

            Spacer(Modifier.weight(1f))

            BottomRow(
                keyStatus = account.keyStatus,
                onPlay = onPlay,
                onSettings = onSettings,
                onActivateKey = onActivateKey,
                onFolder = { runCatching { Desktop.getDesktop().open(paths.root.toFile()) } },
                launching = launching,
                launchStatus = launchStatus,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Header (logo placeholder column + title + window controls)
// ---------------------------------------------------------------------------

@Composable
private fun HeaderRow(
    onlineCount: Int?,
    onLogout: () -> Unit,
    onMinimize: () -> Unit,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Spacer(Modifier.weight(1f))

        OnlinePill(onlineCount)
        Spacer(Modifier.width(10.dp))
        WindowControls(onLogout = onLogout, onMinimize = onMinimize, onClose = onClose)
    }
}

@Composable
private fun OnlinePill(count: Int?) {
    val loc = LocalLocalization.current
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(NsColors.Surface)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Tiny "users" silhouette icon drawn with two circles + capsules.
        Canvas(modifier = Modifier.size(14.dp)) {
            val c = NsColors.Accent
            // Left head
            drawCircle(c, radius = size.minDimension * 0.18f, center = Offset(size.width * 0.32f, size.height * 0.34f))
            // Right head
            drawCircle(c, radius = size.minDimension * 0.16f, center = Offset(size.width * 0.68f, size.height * 0.36f))
            // Shoulders pill
            val w = size.width * 0.85f
            val h = size.height * 0.42f
            val left = (size.width - w) / 2f
            val top = size.height - h - 1f
            drawRoundRect(
                color = c,
                topLeft = Offset(left, top),
                size = androidx.compose.ui.geometry.Size(w, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2f, h / 2f),
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = count?.toString() ?: "—",
            color = NsColors.TextPrimary,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun WindowControls(onLogout: () -> Unit, onMinimize: () -> Unit, onClose: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // The accented logout chip stands out — primary "exit" action.
        CircleIconButton(
            onClick = onLogout,
            background = NsColors.Accent,
            contentColor = Color.White,
        ) { LogoutGlyph() }
        Spacer(Modifier.width(8.dp))
        CircleIconButton(
            onClick = onMinimize,
            background = NsColors.Surface,
            contentColor = NsColors.TextPrimary,
            size = 28.dp,
        ) {
            // Underscore-like minimise glyph
            Box(
                modifier = Modifier
                    .width(10.dp).height(2.dp)
                    .background(NsColors.TextPrimary, shape = RoundedCornerShape(1.dp))
            )
        }
        Spacer(Modifier.width(8.dp))
        CircleIconButton(
            onClick = onClose,
            background = NsColors.Surface,
            contentColor = NsColors.TextPrimary,
            size = 28.dp,
        ) { CloseGlyph() }
    }
}

@Composable
private fun CircleIconButton(
    onClick: () -> Unit,
    background: Color,
    contentColor: Color,
    size: androidx.compose.ui.unit.Dp = 36.dp,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val scale by animateFloatAsState(if (hovered) 1.08f else 1.0f, tween(durationMillis = 150))
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .hoverable(interaction)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .graphicsLayer { scaleX = scale; scaleY = scale },
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

// ---------------------------------------------------------------------------
// Brand title block
// ---------------------------------------------------------------------------

@Composable
private fun BrandLockup() {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "NightShift Client",
            color = NsColors.Accent,
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "ИГРОВОЙ КЛИЕНТ",
            color = NsColors.TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 4.sp,
        )
    }
}

// ---------------------------------------------------------------------------
// Bottom row: screenshot carousel + action buttons
// ---------------------------------------------------------------------------

@Composable
private fun BottomRow(
    keyStatus: KeyStatus,
    onPlay: () -> Unit,
    onSettings: () -> Unit,
    onActivateKey: () -> Unit,
    onFolder: () -> Unit,
    launching: Boolean = false,
    launchStatus: String? = null,
) {
    val loc = LocalLocalization.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
    ) {
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            FolderRoundButton(onFolder)
            Spacer(Modifier.height(10.dp))
            SettingsPill(onSettings)
            Spacer(Modifier.height(10.dp))
            if (launching) {
                Text(
                    launchStatus ?: loc.t("game.launching"),
                    color = NsColors.TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            PlayButton(
                // Visual "active" tint reserved for ready-to-play state.
                enabled = keyStatus is KeyStatus.Active && !launching,
                // Click is wired whenever it leads SOMEWHERE: either Play
                // (active key) or the activation screen (no key / expired).
                // We only kill the click while a launch is already in flight.
                clickable = !launching,
                onClick = if (keyStatus is KeyStatus.Active) onPlay else onActivateKey,
                label = when {
                    launching -> loc.t("game.launching")
                    keyStatus is KeyStatus.Active -> loc.t("main.button.play")
                    else -> loc.t("key.button.activate")
                },
                loading = launching,
            )
        }
    }
}

@Composable
private fun FolderRoundButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(NsColors.Surface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        FolderGlyph()
    }
}

@Composable
private fun SettingsPill(onClick: () -> Unit) {
    val loc = LocalLocalization.current
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(22.dp))
            .background(NsColors.Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SlidersGlyph()
        Spacer(Modifier.width(8.dp))
        Text(
            loc.t("main.button.settings"),
            color = NsColors.TextPrimary,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun PlayButton(
    enabled: Boolean,
    clickable: Boolean,
    onClick: () -> Unit,
    label: String,
    loading: Boolean = false,
) {
    val bg = if (enabled) NsColors.Accent else NsColors.AccentMuted
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(26.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(bg, bg.copy(alpha = 0.85f).blend(Color.White, 0.10f))
                )
            )
            // `enabled` only controls colour; `clickable` decides whether
            // the touch target is alive. This separation is what unlocks the
            // "Активировать" button when keyStatus = NoKey/Expired.
            .clickable(enabled = clickable, onClick = onClick)
            .padding(horizontal = 26.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            // animated pulsing dot to signal preparation
            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color.White))
        } else {
            TriangleGlyph(color = Color.White)
        }
        Spacer(Modifier.width(10.dp))
        Text(label, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

// ---------------------------------------------------------------------------
// Decorative crystal/blob ambience
// ---------------------------------------------------------------------------

@Composable
private fun CrystalAmbience() {
    val transition = rememberInfiniteTransition()
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
    )

    // Bottom-right faint crystal cluster.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(end = 60.dp, bottom = 20.dp)
            .wrapContentSize(Alignment.BottomEnd),
    ) {
        Canvas(modifier = Modifier.size(280.dp)) {
            val centre = Offset(size.width * 0.55f, size.height * 0.65f)
            val tint = NsColors.Accent.copy(alpha = 0.18f + 0.06f * pulse)
            drawCrystal(centre, scale = 1.0f, tint = tint)
            drawCrystal(centre.copy(x = centre.x - 60f, y = centre.y + 30f), scale = 0.55f, tint = tint.copy(alpha = tint.alpha * 0.7f))
            drawCrystal(centre.copy(x = centre.x + 50f, y = centre.y + 60f), scale = 0.45f, tint = tint.copy(alpha = tint.alpha * 0.6f))
        }
    }

    // Bottom-left small accent crystal under the carousel.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 240.dp, bottom = 30.dp)
            .wrapContentSize(Alignment.BottomStart),
    ) {
        Canvas(modifier = Modifier.size(140.dp)) {
            val tint = NsColors.Accent.copy(alpha = 0.14f + 0.05f * pulse)
            drawCrystal(Offset(size.width / 2f, size.height * 0.6f), scale = 0.6f, tint = tint)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCrystal(
    centre: Offset,
    scale: Float,
    tint: Color,
) {
    val w = 70f * scale
    val h = 130f * scale
    val path = Path().apply {
        moveTo(centre.x, centre.y - h)
        lineTo(centre.x + w, centre.y - h * 0.4f)
        lineTo(centre.x + w * 0.7f, centre.y + h * 0.4f)
        lineTo(centre.x, centre.y + h * 0.7f)
        lineTo(centre.x - w * 0.7f, centre.y + h * 0.4f)
        lineTo(centre.x - w, centre.y - h * 0.4f)
        close()
    }
    drawPath(path, brush = SolidColor(tint))
    // Highlight stroke
    drawPath(path, brush = SolidColor(NsColors.Accent.copy(alpha = 0.35f)), style = Stroke(width = 1.2f))
    // Inner facet line
    val face = Path().apply {
        moveTo(centre.x, centre.y - h)
        lineTo(centre.x, centre.y + h * 0.7f)
    }
    drawPath(face, brush = SolidColor(NsColors.Accent.copy(alpha = 0.25f)), style = Stroke(width = 1f))
}

// ---------------------------------------------------------------------------
// Tiny vector glyphs (no third-party icon dep)
// ---------------------------------------------------------------------------

@Composable
private fun LogoutGlyph() {
    Canvas(modifier = Modifier.size(16.dp)) {
        val c = Color.White
        // Box with door
        drawLine(c, Offset(size.width * 0.15f, size.height * 0.2f), Offset(size.width * 0.15f, size.height * 0.8f), strokeWidth = 1.6f)
        drawLine(c, Offset(size.width * 0.15f, size.height * 0.2f), Offset(size.width * 0.55f, size.height * 0.2f), strokeWidth = 1.6f)
        drawLine(c, Offset(size.width * 0.15f, size.height * 0.8f), Offset(size.width * 0.55f, size.height * 0.8f), strokeWidth = 1.6f)
        // Arrow
        drawLine(c, Offset(size.width * 0.4f, size.height * 0.5f), Offset(size.width * 0.95f, size.height * 0.5f), strokeWidth = 1.8f)
        drawLine(c, Offset(size.width * 0.95f, size.height * 0.5f), Offset(size.width * 0.75f, size.height * 0.32f), strokeWidth = 1.8f)
        drawLine(c, Offset(size.width * 0.95f, size.height * 0.5f), Offset(size.width * 0.75f, size.height * 0.68f), strokeWidth = 1.8f)
    }
}

@Composable
private fun CloseGlyph() {
    Canvas(modifier = Modifier.size(10.dp)) {
        val c = NsColors.TextPrimary
        drawLine(c, Offset(0f, 0f), Offset(size.width, size.height), strokeWidth = 1.6f)
        drawLine(c, Offset(size.width, 0f), Offset(0f, size.height), strokeWidth = 1.6f)
    }
}

@Composable
private fun TriangleGlyph(color: Color) {
    Canvas(modifier = Modifier.size(14.dp)) {
        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(size.width, size.height / 2f)
            lineTo(0f, size.height)
            close()
        }
        drawPath(path, brush = SolidColor(color))
    }
}

@Composable
private fun FolderGlyph() {
    Canvas(modifier = Modifier.size(20.dp)) {
        val c = NsColors.Accent
        val path = Path().apply {
            moveTo(0f, size.height * 0.35f)
            lineTo(size.width * 0.4f, size.height * 0.35f)
            lineTo(size.width * 0.5f, size.height * 0.2f)
            lineTo(size.width, size.height * 0.2f)
            lineTo(size.width, size.height * 0.85f)
            lineTo(0f, size.height * 0.85f)
            close()
        }
        drawPath(path, brush = SolidColor(c.copy(alpha = 0.6f)))
        drawPath(path, brush = SolidColor(c), style = Stroke(width = 1.2f))
    }
}

@Composable
private fun SlidersGlyph() {
    Canvas(modifier = Modifier.size(16.dp)) {
        val c = NsColors.Accent
        // 3 horizontal lines with knobs
        for (i in 0 until 3) {
            val y = size.height * (0.25f + i * 0.25f)
            drawLine(c.copy(alpha = 0.45f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1.4f)
            val knobX = size.width * (0.25f + i * 0.2f)
            drawCircle(c, radius = size.minDimension * 0.07f, center = Offset(knobX, y))
        }
    }
}

// ---------------------------------------------------------------------------
// Small helpers
// ---------------------------------------------------------------------------

private fun Color.blend(other: Color, t: Float): Color = Color(
    red = red * (1 - t) + other.red * t,
    green = green * (1 - t) + other.green * t,
    blue = blue * (1 - t) + other.blue * t,
    alpha = alpha,
)
