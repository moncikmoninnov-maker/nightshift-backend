package `fun`.nightshift.launcher.client.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `fun`.nightshift.launcher.client.ui.theme.NsColors

/**
 * Shared brand-styled visual atoms used by every launcher screen
 * (login, register, key, password reset, main, settings).
 *
 * Placing them in one file keeps every screen visually consistent and
 * removes ~200 lines of duplicated drawing code.
 */

// ---------------------------------------------------------------------------
// Backdrop ambience
// ---------------------------------------------------------------------------

/**
 * Slowly-pulsating decorative crystal cluster anchored to the bottom-right
 * corner. Optional secondary cluster on the bottom-left when [twoSided] is
 * true. Always sized 280dp / 140dp respectively and clipped to the window
 * by the parent layout.
 */
@Composable
fun CrystalAmbience(twoSided: Boolean = true) {
    val transition = rememberInfiniteTransition()
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
    )

    Box(modifier = Modifier.fillMaxSize().padding(end = 60.dp, bottom = 20.dp).wrapContentSize(Alignment.BottomEnd)) {
        Canvas(modifier = Modifier.size(280.dp)) {
            val centre = Offset(size.width * 0.55f, size.height * 0.65f)
            val tint = NsColors.Accent.copy(alpha = 0.18f + 0.06f * pulse)
            drawCrystal(centre, scale = 1.0f, tint = tint)
            drawCrystal(centre.copy(x = centre.x - 60f, y = centre.y + 30f), scale = 0.55f, tint = tint.copy(alpha = tint.alpha * 0.7f))
            drawCrystal(centre.copy(x = centre.x + 50f, y = centre.y + 60f), scale = 0.45f, tint = tint.copy(alpha = tint.alpha * 0.6f))
        }
    }

    if (twoSided) {
        Box(modifier = Modifier.fillMaxSize().padding(start = 40.dp, bottom = 30.dp).wrapContentSize(Alignment.BottomStart)) {
            Canvas(modifier = Modifier.size(160.dp)) {
                val tint = NsColors.Accent.copy(alpha = 0.14f + 0.05f * pulse)
                drawCrystal(Offset(size.width / 2f, size.height * 0.6f), scale = 0.7f, tint = tint)
            }
        }
    }
}

private fun DrawScope.drawCrystal(centre: Offset, scale: Float, tint: Color) {
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
    drawPath(path, brush = SolidColor(NsColors.Accent.copy(alpha = 0.35f)), style = Stroke(width = 1.2f))
    val face = Path().apply {
        moveTo(centre.x, centre.y - h)
        lineTo(centre.x, centre.y + h * 0.7f)
    }
    drawPath(face, brush = SolidColor(NsColors.Accent.copy(alpha = 0.25f)), style = Stroke(width = 1f))
}

// ---------------------------------------------------------------------------
// "NS" crystal logo (used as a header mark on every screen)
// ---------------------------------------------------------------------------

@Composable
fun CrystalLogo(modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        // Soft purple aura — the only background under the monogram so the
        // letter "N" reads as the primary mark.
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Outer halo
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(NsColors.Accent.copy(alpha = 0.45f), Color.Transparent),
                    center = Offset(size.width / 2f, size.height / 2f),
                    radius = size.minDimension * 0.55f,
                ),
                radius = size.minDimension * 0.55f,
                center = Offset(size.width / 2f, size.height / 2f),
            )
            // Tighter inner core for a brighter centre
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(NsColors.Accent.copy(alpha = 0.55f), Color.Transparent),
                    center = Offset(size.width / 2f, size.height / 2f),
                    radius = size.minDimension * 0.30f,
                ),
                radius = size.minDimension * 0.30f,
                center = Offset(size.width / 2f, size.height / 2f),
            )
        }
        // Letter "N" — three thick rounded strokes scaled to the logo size.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val nW = size.width * 0.32f
            val nH = size.height * 0.42f
            val nLeft = cx - nW / 2f
            val nRight = cx + nW / 2f
            val nTop = cy - nH / 2f
            val nBottom = cy + nH / 2f
            val stroke = size.minDimension * 0.085f
            val nColor = Color.White

            // Left vertical
            drawLine(nColor, Offset(nLeft, nBottom), Offset(nLeft, nTop), strokeWidth = stroke,
                cap = androidx.compose.ui.graphics.StrokeCap.Round)
            // Diagonal
            drawLine(nColor, Offset(nLeft, nTop), Offset(nRight, nBottom), strokeWidth = stroke,
                cap = androidx.compose.ui.graphics.StrokeCap.Round)
            // Right vertical
            drawLine(nColor, Offset(nRight, nBottom), Offset(nRight, nTop), strokeWidth = stroke,
                cap = androidx.compose.ui.graphics.StrokeCap.Round)
        }
    }
}

// ---------------------------------------------------------------------------
// Window control row (close + minimise) — used by frameless windows
// ---------------------------------------------------------------------------

@Composable
fun WindowControlsRow(
    onMinimize: () -> Unit,
    onClose: () -> Unit,
    accent: () -> Unit = {},
    showAccent: Boolean = false,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (showAccent) {
            CircleIconButton(onClick = accent, background = NsColors.Accent, size = 36.dp) { LogoutGlyph() }
            Spacer(Modifier.width(8.dp))
        }
        CircleIconButton(onClick = onMinimize, background = NsColors.Surface, size = 28.dp) {
            Box(
                modifier = Modifier
                    .width(10.dp).height(2.dp)
                    .background(NsColors.TextPrimary, shape = RoundedCornerShape(1.dp))
            )
        }
        Spacer(Modifier.width(8.dp))
        CircleIconButton(onClick = onClose, background = NsColors.Surface, size = 28.dp) {
            Canvas(modifier = Modifier.size(10.dp)) {
                val c = NsColors.TextPrimary
                drawLine(c, Offset(0f, 0f), Offset(size.width, size.height), strokeWidth = 1.6f)
                drawLine(c, Offset(size.width, 0f), Offset(0f, size.height), strokeWidth = 1.6f)
            }
        }
    }
}

@Composable
fun CircleIconButton(
    onClick: () -> Unit,
    background: Color,
    size: Dp = 36.dp,
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
            modifier = Modifier.size(size).graphicsLayer { scaleX = scale; scaleY = scale },
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

@Composable
private fun LogoutGlyph() {
    Canvas(modifier = Modifier.size(16.dp)) {
        val c = Color.White
        drawLine(c, Offset(size.width * 0.15f, size.height * 0.2f), Offset(size.width * 0.15f, size.height * 0.8f), strokeWidth = 1.6f)
        drawLine(c, Offset(size.width * 0.15f, size.height * 0.2f), Offset(size.width * 0.55f, size.height * 0.2f), strokeWidth = 1.6f)
        drawLine(c, Offset(size.width * 0.15f, size.height * 0.8f), Offset(size.width * 0.55f, size.height * 0.8f), strokeWidth = 1.6f)
        drawLine(c, Offset(size.width * 0.4f, size.height * 0.5f), Offset(size.width * 0.95f, size.height * 0.5f), strokeWidth = 1.8f)
        drawLine(c, Offset(size.width * 0.95f, size.height * 0.5f), Offset(size.width * 0.75f, size.height * 0.32f), strokeWidth = 1.8f)
        drawLine(c, Offset(size.width * 0.95f, size.height * 0.5f), Offset(size.width * 0.75f, size.height * 0.68f), strokeWidth = 1.8f)
    }
}

// ---------------------------------------------------------------------------
// Brand title + subtitle (used by Login / Register / Reset / Key)
// ---------------------------------------------------------------------------

@Composable
fun BrandLockup(subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "NightShift Client",
            color = NsColors.Accent,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = subtitle.uppercase(),
            color = NsColors.TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 4.sp,
        )
    }
}

// ---------------------------------------------------------------------------
// Brand text field (rounded, soft surface, accent focus glow)
// ---------------------------------------------------------------------------

@Composable
fun BrandTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    enabled: Boolean = true,
    isError: Boolean = false,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    val borderColor = when {
        isError -> NsColors.Error
        else -> NsColors.AccentMuted.copy(alpha = 0.6f)
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(NsColors.Surface)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            leadingIcon()
            Spacer(Modifier.width(10.dp))
        }
        Box(modifier = Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    color = NsColors.TextSecondary,
                    fontSize = 14.sp,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                enabled = enabled,
                cursorBrush = SolidColor(NsColors.Accent),
                visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(keyboardType = if (isPassword) KeyboardType.Password else keyboardType),
                textStyle = TextStyle(
                    color = NsColors.TextPrimary,
                    fontSize = 14.sp,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun FieldErrorText(message: String?) {
    if (message.isNullOrBlank()) return
    Text(
        text = message,
        color = NsColors.Error,
        fontSize = 12.sp,
        modifier = Modifier.padding(start = 16.dp, top = 4.dp),
    )
}

// ---------------------------------------------------------------------------
// Brand primary action button (gradient pill)
// ---------------------------------------------------------------------------

@Composable
fun BrandPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingTriangle: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val scale by animateFloatAsState(if (hovered && enabled) 1.02f else 1.0f, tween(150))
    val active = enabled && !loading
    val bgStart = if (active) NsColors.Accent else NsColors.AccentMuted
    val bgEnd = if (active) NsColors.Accent.copy(alpha = 0.85f) else NsColors.AccentMuted.copy(alpha = 0.85f)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(26.dp))
            .background(Brush.horizontalGradient(listOf(bgStart, bgEnd)))
            .hoverable(interaction)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(enabled = active, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (leadingTriangle) {
                Canvas(modifier = Modifier.size(14.dp)) {
                    val path = Path().apply {
                        moveTo(0f, 0f)
                        lineTo(size.width, size.height / 2f)
                        lineTo(0f, size.height)
                        close()
                    }
                    drawPath(path, brush = SolidColor(Color.White))
                }
                Spacer(Modifier.width(10.dp))
            }
            Text(
                text = if (loading) "..." else text,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Brand "ghost" link button (purple text on transparent)
// ---------------------------------------------------------------------------

@Composable
fun BrandGhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(text, color = NsColors.Accent, fontWeight = FontWeight.Medium)
    }
}

// ---------------------------------------------------------------------------
// Inline status banner (success / error)
// ---------------------------------------------------------------------------

@Composable
fun BrandStatusBanner(message: String?, isError: Boolean = true) {
    if (message.isNullOrBlank()) return
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                color = if (isError) NsColors.Error.copy(alpha = 0.15f) else NsColors.Success.copy(alpha = 0.15f)
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(
            message,
            color = if (isError) NsColors.Error else NsColors.Success,
            fontSize = 13.sp,
        )
    }
}

// ---------------------------------------------------------------------------
// User glyph for login field
// ---------------------------------------------------------------------------

@Composable
fun UserGlyph() {
    Canvas(modifier = Modifier.size(18.dp)) {
        val c = NsColors.Accent
        // Head
        drawCircle(c, radius = size.minDimension * 0.18f, center = Offset(size.width / 2f, size.height * 0.32f))
        // Shoulders
        val w = size.width * 0.85f
        val h = size.height * 0.4f
        val left = (size.width - w) / 2f
        val top = size.height - h - 1f
        drawRoundRect(
            color = c,
            topLeft = Offset(left, top),
            size = androidx.compose.ui.geometry.Size(w, h),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2f, h / 2f),
        )
    }
}

@Composable
fun LockGlyph() {
    Canvas(modifier = Modifier.size(18.dp)) {
        val c = NsColors.Accent
        // Shackle (arc approximation via two lines)
        val sx = size.width * 0.3f
        val ex = size.width * 0.7f
        val topY = size.height * 0.22f
        val bottomY = size.height * 0.5f
        drawLine(c, Offset(sx, topY), Offset(sx, bottomY), strokeWidth = 1.6f)
        drawLine(c, Offset(ex, topY), Offset(ex, bottomY), strokeWidth = 1.6f)
        drawLine(c, Offset(sx, topY), Offset(ex, topY), strokeWidth = 1.6f)
        // Body
        drawRoundRect(
            color = c,
            topLeft = Offset(size.width * 0.2f, size.height * 0.5f),
            size = androidx.compose.ui.geometry.Size(size.width * 0.6f, size.height * 0.42f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 3f),
        )
    }
}

@Composable
fun MailGlyph() {
    Canvas(modifier = Modifier.size(18.dp)) {
        val c = NsColors.Accent
        drawRoundRect(
            color = c.copy(alpha = 0.0f), // transparent fill
            topLeft = Offset(size.width * 0.1f, size.height * 0.25f),
            size = androidx.compose.ui.geometry.Size(size.width * 0.8f, size.height * 0.5f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f, 2f),
        )
        // Outline
        drawLine(c, Offset(size.width * 0.1f, size.height * 0.25f), Offset(size.width * 0.9f, size.height * 0.25f), strokeWidth = 1.4f)
        drawLine(c, Offset(size.width * 0.1f, size.height * 0.75f), Offset(size.width * 0.9f, size.height * 0.75f), strokeWidth = 1.4f)
        drawLine(c, Offset(size.width * 0.1f, size.height * 0.25f), Offset(size.width * 0.1f, size.height * 0.75f), strokeWidth = 1.4f)
        drawLine(c, Offset(size.width * 0.9f, size.height * 0.25f), Offset(size.width * 0.9f, size.height * 0.75f), strokeWidth = 1.4f)
        // Envelope flap
        drawLine(c, Offset(size.width * 0.1f, size.height * 0.25f), Offset(size.width * 0.5f, size.height * 0.55f), strokeWidth = 1.4f)
        drawLine(c, Offset(size.width * 0.9f, size.height * 0.25f), Offset(size.width * 0.5f, size.height * 0.55f), strokeWidth = 1.4f)
    }
}

@Composable
fun KeyGlyph() {
    Canvas(modifier = Modifier.size(18.dp)) {
        val c = NsColors.Accent
        // Key bow
        drawCircle(
            color = c,
            radius = size.minDimension * 0.18f,
            center = Offset(size.width * 0.25f, size.height / 2f),
            style = Stroke(width = 1.8f),
        )
        // Shaft
        drawLine(c, Offset(size.width * 0.4f, size.height / 2f), Offset(size.width * 0.95f, size.height / 2f), strokeWidth = 1.8f)
        // Teeth
        drawLine(c, Offset(size.width * 0.7f, size.height / 2f), Offset(size.width * 0.7f, size.height * 0.7f), strokeWidth = 1.6f)
        drawLine(c, Offset(size.width * 0.85f, size.height / 2f), Offset(size.width * 0.85f, size.height * 0.65f), strokeWidth = 1.6f)
    }
}
