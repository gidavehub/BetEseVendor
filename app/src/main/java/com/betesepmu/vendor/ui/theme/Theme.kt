package com.betesepmu.vendor.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = BrandGreen,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = BrandGreenLight,
    onPrimaryContainer = BrandGreenDark,
    secondary = BrandGold,
    onSecondary = BrandBlack,
    secondaryContainer = androidx.compose.ui.graphics.Color(0xFFFBF1C9),
    onSecondaryContainer = BrandBlack,
    tertiary = BrandGreenDark,
    background = LightBackground,
    onBackground = OnLight,
    surface = LightSurface,
    onSurface = OnLight,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = Color4(0xFF425249),
    outline = LightOutline,
)

private val DarkColors = darkColorScheme(
    primary = DarkGreen,
    onPrimary = Color4(0xFF06351A),
    primaryContainer = BrandGreenDark,
    onPrimaryContainer = BrandGreenLight,
    secondary = BrandGold,
    onSecondary = BrandBlack,
    tertiary = DarkGreen,
    background = DarkBackground,
    onBackground = OnDark,
    surface = DarkSurface,
    onSurface = OnDark,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color4(0xFFBFCEC4),
    outline = Color4(0xFF3C4D43),
)

private fun Color4(value: Long) = androidx.compose.ui.graphics.Color(value)

/**
 * BetEse Vendor theme. Dynamic colour is intentionally disabled so the white-and-green
 * brand is consistent across every device.
 */
@Composable
fun BetEseVendorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content,
    )
}
