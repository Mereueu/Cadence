package com.cadence.player.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object CadenceColors {
    val Background = Color(0xFF000000)
    val Surface = Color(0xFF0F0F0F)
    val SurfaceHigh = Color(0xFF1A1A1A)
    val SurfaceHighest = Color(0xFF262626)
    val Outline = Color(0xFF333333)
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFFB3B3B3)
    val TextTertiary = Color(0xFF808080)
    val BarBackground = Color(0xFF1C1C1C)
}

private val Sans = FontFamily.SansSerif

private val CadenceTypography = Typography(
    displaySmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.8).sp),
    headlineMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.6).sp),
    headlineSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.4).sp),
    titleLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 27.sp, letterSpacing = (-0.3).sp),
    titleMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = (-0.1).sp),
    titleSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.3.sp),
    labelSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 1.2.sp),
)

@Composable
fun CadenceTheme(accent: Color, content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = accent,
        onPrimary = Color.White,
        primaryContainer = lerp(CadenceColors.SurfaceHigh, accent, 0.25f),
        onPrimaryContainer = CadenceColors.TextPrimary,
        secondary = accent,
        onSecondary = Color.White,
        secondaryContainer = lerp(CadenceColors.SurfaceHigh, accent, 0.18f),
        onSecondaryContainer = CadenceColors.TextPrimary,
        tertiary = accent,
        background = CadenceColors.Background,
        onBackground = CadenceColors.TextPrimary,
        surface = CadenceColors.Background,
        onSurface = CadenceColors.TextPrimary,
        surfaceVariant = CadenceColors.SurfaceHigh,
        onSurfaceVariant = CadenceColors.TextSecondary,
        surfaceContainerLowest = CadenceColors.Background,
        surfaceContainerLow = CadenceColors.Surface,
        surfaceContainer = CadenceColors.Surface,
        surfaceContainerHigh = CadenceColors.SurfaceHigh,
        surfaceContainerHighest = CadenceColors.SurfaceHighest,
        outline = CadenceColors.Outline,
        outlineVariant = CadenceColors.Outline,
        inverseSurface = CadenceColors.TextPrimary,
        inverseOnSurface = CadenceColors.Background,
    )
    MaterialTheme(colorScheme = scheme, typography = CadenceTypography) {
        // Text and icons default to light-on-dark everywhere, not only inside Surfaces.
        CompositionLocalProvider(
            LocalContentColor provides CadenceColors.TextPrimary,
            LocalTextStyle provides CadenceTypography.bodyMedium.copy(color = CadenceColors.TextPrimary),
            content = content,
        )
    }
}
