package com.cadence.player.ui.theme

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The five button/control styles from the design canvas. */
enum class UiStyle(val label: String, val description: String, val defaultAccent: Long) {
    MINIMAL("Minimal", "Mono white, hairline outlines", 0xFFFFFFFF),
    NEON("Neon", "Glowing outlines on black", 0xFF5BE08C),
    SOLID("Solid", "Bold filled buttons, rounded squares", 0xFF5BE08C),
    GLASS("Glass", "Frosted translucent controls on a purple glow", 0xFFFFFFFF),
    HIFI("Hi-Fi", "Hardware keys, amber LEDs and an LCD display", 0xFFFFB020),
}

val LocalUiStyle = staticCompositionLocalOf { UiStyle.MINIMAL }

private val KeyTop = Color(0xFF2E2E2E)
private val KeyBottom = Color(0xFF1D1D1D)
private val KeyShadow = Color(0xFF070707)
private val Amber = Color(0xFFFFB020)

/** Corner radius of album art in the current style. */
@Composable
fun artShape(): Shape = when (LocalUiStyle.current) {
    UiStyle.MINIMAL -> RoundedCornerShape(2.dp)
    UiStyle.NEON -> RoundedCornerShape(14.dp)
    UiStyle.SOLID -> RoundedCornerShape(20.dp)
    UiStyle.GLASS -> RoundedCornerShape(26.dp)
    UiStyle.HIFI -> RoundedCornerShape(6.dp)
}

/** Soft coloured glow drawn behind a circular element. */
fun Modifier.circleGlow(color: Color, spread: Float = 1.45f, alpha: Float = 0.45f): Modifier = drawBehind {
    val r = size.minDimension / 2f * spread
    drawCircle(
        Brush.radialGradient(listOf(color.copy(alpha = alpha), Color.Transparent), center = center, radius = r),
        r,
        center,
    )
}

/** Raised hardware key (Hi-Fi). */
fun Modifier.hifiKey(pressedIn: Boolean = false, radius: Dp = 7.dp): Modifier = drawBehind {
    val cr = androidx.compose.ui.geometry.CornerRadius(radius.toPx())
    val depth = if (pressedIn) 1.dp.toPx() else 4.dp.toPx()
    drawRoundRect(KeyShadow, Offset(0f, depth), size, cr)
    drawRoundRect(
        Brush.verticalGradient(if (pressedIn) listOf(Color(0xFF252525), Color(0xFF171717)) else listOf(KeyTop, KeyBottom)),
        Offset.Zero, size, cr,
    )
    drawRoundRect(Color.White.copy(alpha = 0.08f), Offset.Zero, androidx.compose.ui.geometry.Size(size.width, 1.dp.toPx()), cr)
}

/** The big play / pause button, drawn in the current style. */
@Composable
fun StyledPlayButton(playing: Boolean, buffering: Boolean, size: Dp, onClick: () -> Unit) {
    val style = LocalUiStyle.current
    val accent = MaterialTheme.colorScheme.primary
    val onAccent = MaterialTheme.colorScheme.onPrimary
    val iconSize = size * 0.375f
    when (style) {
        UiStyle.MINIMAL -> Box(
            Modifier.size(size).clip(CircleShape).border(1.5.dp, CadenceColors.TextPrimary, CircleShape).clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            PlayIcon(playing, CadenceColors.TextPrimary, iconSize)
            if (buffering) CircularProgressIndicator(Modifier.size(size), color = CadenceColors.TextPrimary, strokeWidth = 1.dp)
        }
        UiStyle.NEON -> Box(
            Modifier
                .size(size)
                .circleGlow(accent, 1.5f, 0.5f)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(Color.Transparent, accent.copy(alpha = 0.18f))))
                .border(2.dp, accent, CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            PlayIcon(playing, accent, iconSize)
            if (buffering) CircularProgressIndicator(Modifier.size(size), color = accent, strokeWidth = 1.5.dp)
        }
        UiStyle.SOLID -> Box(
            Modifier.size(size).circleGlow(accent, 1.25f, 0.3f).clip(CircleShape).background(accent).clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            PlayIcon(playing, onAccent, iconSize)
            if (buffering) CircularProgressIndicator(Modifier.size(size), color = onAccent, strokeWidth = 2.dp)
        }
        UiStyle.GLASS -> Box(
            Modifier.size(size).circleGlow(Color.Black, 1.3f, 0.35f).clip(CircleShape).background(Color.White.copy(alpha = 0.92f)).clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            PlayIcon(playing, Color(0xFF140A1C), iconSize)
            if (buffering) CircularProgressIndicator(Modifier.size(size), color = Color(0xFF140A1C), strokeWidth = 1.5.dp)
        }
        UiStyle.HIFI -> Column(
            Modifier
                .width(size * 1.12f)
                .height(size * 0.74f)
                .hifiKey(pressedIn = playing)
                .clip(RoundedCornerShape(7.dp))
                .clickable(onClick = onClick),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier.width(22.dp).height(4.dp).circleGlow(Amber, 3f, if (playing) 0.6f else 0f)
                    .clip(RoundedCornerShape(2.dp)).background(if (playing) Amber else Color(0xFF3A3A3A)),
            )
            Spacer(Modifier.height(6.dp))
            PlayIcon(playing, Color(0xFFEDEDED), iconSize)
        }
    }
}

@Composable
private fun PlayIcon(playing: Boolean, tint: Color, size: Dp) {
    Crossfade(playing, label = "play") { p ->
        Icon(if (p) LineIcons.Pause else LineIcons.Play, if (p) "Pause" else "Play", tint = tint, modifier = Modifier.size(size))
    }
}

/**
 * Secondary control (prev/next, shuffle, repeat, favourite…) in the current style.
 * [emphasis] marks the bigger buttons next to play.
 */
@Composable
fun StyledIconButton(
    icon: ImageVector,
    description: String,
    active: Boolean,
    size: Dp,
    iconSize: Dp,
    emphasis: Boolean,
    onClick: () -> Unit,
) {
    val style = LocalUiStyle.current
    val accent = MaterialTheme.colorScheme.primary
    val onAccent = MaterialTheme.colorScheme.onPrimary
    when (style) {
        UiStyle.MINIMAL -> {
            val color by animateColorAsState(if (active || emphasis) CadenceColors.TextPrimary else Color(0xFF8C8C8C), label = "min")
            Column(
                Modifier.size(size).clip(CircleShape).clickable(onClick = onClick),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(icon, description, tint = color, modifier = Modifier.size(iconSize))
                if (!emphasis) {
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.width(12.dp).height(1.5.dp).background(if (active) CadenceColors.TextPrimary else Color.Transparent))
                }
            }
        }
        UiStyle.NEON -> {
            val s = if (emphasis) size else size * 0.95f
            Box(
                Modifier
                    .size(s)
                    .then(if (active) Modifier.circleGlow(accent, 1.35f, 0.4f) else Modifier)
                    .clip(CircleShape)
                    .border(1.dp, if (active) accent else Color(0xFF2A2A2A), CircleShape)
                    .clickable(onClick = onClick),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, description, tint = if (active) accent else Color.White, modifier = Modifier.size(iconSize * 0.9f)) }
        }
        UiStyle.SOLID -> {
            val shape = if (emphasis) CircleShape else RoundedCornerShape(12.dp)
            Box(
                Modifier.size(size).clip(shape).background(if (active) accent else Color(0xFF1C1C1C)).clickable(onClick = onClick),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, description, tint = if (active) onAccent else Color.White, modifier = Modifier.size(iconSize * 0.9f)) }
        }
        UiStyle.GLASS -> Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = if (active) 0.24f else 0.10f))
                .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, description, tint = Color.White, modifier = Modifier.size(iconSize * 0.9f)) }
        UiStyle.HIFI -> Column(
            Modifier
                .width(if (emphasis) size * 1.05f else size * 0.95f)
                .height(size * 0.95f)
                .hifiKey(pressedIn = active, radius = 6.dp)
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onClick),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (!emphasis) {
                Box(
                    Modifier.width(12.dp).height(3.dp).circleGlow(Amber, 3f, if (active) 0.6f else 0f)
                        .clip(RoundedCornerShape(2.dp)).background(if (active) Amber else Color(0xFF3A3A3A)),
                )
                Spacer(Modifier.height(4.dp))
            }
            Icon(icon, description, tint = Color(0xFFBDBDBD), modifier = Modifier.size(iconSize * 0.85f))
        }
    }
}

/** Root backdrop for styles that paint more than a flat colour. */
fun styleBackground(style: UiStyle): Brush? = when (style) {
    UiStyle.GLASS -> Brush.radialGradient(
        listOf(Color(0xFF7A3A8F), Color(0xFF3A1B52), Color(0xFF0D0712)),
        center = Offset(300f, 350f),
        radius = 2200f,
    )
    UiStyle.HIFI -> Brush.verticalGradient(listOf(Color(0xFF1B1B1B), Color(0xFF121212)))
    else -> null
}
