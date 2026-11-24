package com.chen.myviewer.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val LightColors = lightColorScheme(
    primary = Color(0xFF2D5A46),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD8E8DD),
    onPrimaryContainer = Color(0xFF143525),
    secondary = Color(0xFF566B5D),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE5ECE6),
    onSecondaryContainer = Color(0xFF1E3126),
    tertiary = Color(0xFF9C6B43),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF3E5D8),
    onTertiaryContainer = Color(0xFF3E2611),
    background = Color(0xFFF6F4EE),
    onBackground = Color(0xFF1C2420),
    surface = Color(0xFFF6F4EE),
    onSurface = Color(0xFF1C2420),
    surfaceVariant = Color(0xFFE9E6DD),
    onSurfaceVariant = Color(0xFF5C6660),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFAF8F3),
    surfaceContainer = Color(0xFFEEEBE3),
    surfaceContainerHigh = Color(0xFFE6E2D8),
    outline = Color(0xFFB8B3A7),
    outlineVariant = Color(0xFFDDD8CC),
)

internal val DarkColors = darkColorScheme(
    primary = Color(0xFF8EC5A5),
    onPrimary = Color(0xFF0D2619),
    primaryContainer = Color(0xFF213C2E),
    onPrimaryContainer = Color(0xFFD3ECDC),
    secondary = Color(0xFFA7BBAE),
    onSecondary = Color(0xFF16281E),
    secondaryContainer = Color(0xFF27362D),
    onSecondaryContainer = Color(0xFFD6E4DA),
    tertiary = Color(0xFFD9A97E),
    onTertiary = Color(0xFF341E0B),
    tertiaryContainer = Color(0xFF3D2E21),
    onTertiaryContainer = Color(0xFFF3E3D3),
    background = Color(0xFF121715),
    onBackground = Color(0xFFE4EBE6),
    surface = Color(0xFF121715),
    onSurface = Color(0xFFE4EBE6),
    surfaceVariant = Color(0xFF222B26),
    onSurfaceVariant = Color(0xFF9AA89F),
    surfaceContainerLowest = Color(0xFF181F1C),
    surfaceContainerLow = Color(0xFF1B231F),
    surfaceContainer = Color(0xFF212B26),
    surfaceContainerHigh = Color(0xFF29352F),
    outline = Color(0xFF4C5B52),
    outlineVariant = Color(0xFF2E3B34),
)

internal val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val DefaultTypography = Typography()

internal val AppTypography = Typography(
    titleLarge = DefaultTypography.titleLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    titleMedium = DefaultTypography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = DefaultTypography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = DefaultTypography.labelLarge.copy(fontWeight = FontWeight.Medium),
    labelMedium = DefaultTypography.labelMedium.copy(fontWeight = FontWeight.Medium),
    bodyMedium = DefaultTypography.bodyMedium.copy(lineHeight = 22.sp),
    bodySmall = DefaultTypography.bodySmall.copy(lineHeight = 18.sp),
)

@Composable
internal fun MyViewerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
