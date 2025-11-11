package com.thewizrd.simplewear.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.protolayout.types.LayoutColor

internal val wearColorScheme: ColorScheme = ColorScheme(
    primary = Color(0xFFA2C9FD),
    primaryContainer = Color(0xFF1C4975),
    primaryDim = Color(0xFFA2C9FD),
    onPrimary = Color(0xFF00325A),
    onPrimaryContainer = Color(0xFFD2E4FF),

    secondary = Color(0xFF95CDF7),
    secondaryContainer = Color(0xFF004B6F),
    secondaryDim = Color(0xFF95CDF7),
    onSecondary = Color(0xFF00344E),
    onSecondaryContainer = Color(0xFFC9E6FF),

    tertiary = Color(0xFFA9C7FF),
    tertiaryContainer = Color(0xFF264777),
    tertiaryDim = Color(0xFFA9C7FF),
    onTertiary = Color(0xFF07305F),
    onTertiaryContainer = Color(0xFFD6E3FF),

    surfaceContainer = Color(0xFF1D2024),
    surfaceContainerLow = Color(0xFF191C20),
    surfaceContainerHigh = Color(0xFF272A2F),
    onSurface = Color(0xFFE1E2E8),
    onSurfaceVariant = Color(0xFFC3C6CF),

    error = Color(0xFFFFB4AB),
    errorDim = Color(0xFFBA1B1B),
    errorContainer = Color(0xFF93000A),
    onError = Color(0xFF690005),
    onErrorContainer = Color(0xFFFFDAD6),

    //background = Color(0xFF111418),
    //onBackground = Color(0xFFE1E2E8),

    outline = Color(0xFF8D9199),
    outlineVariant = Color(0xFF43474E),
)

val wearTileColorScheme = androidx.wear.protolayout.material3.ColorScheme(
    primary = LayoutColor(wearColorScheme.primary.toArgb()),
    onPrimary = LayoutColor(wearColorScheme.onPrimary.toArgb()),
    primaryContainer = LayoutColor(wearColorScheme.primaryContainer.toArgb()),
    onPrimaryContainer = LayoutColor(wearColorScheme.onPrimaryContainer.toArgb()),
    primaryDim = LayoutColor(wearColorScheme.primaryDim.toArgb()),

    secondary = LayoutColor(wearColorScheme.secondary.toArgb()),
    onSecondary = LayoutColor(wearColorScheme.onSecondary.toArgb()),
    secondaryContainer = LayoutColor(wearColorScheme.secondaryContainer.toArgb()),
    onSecondaryContainer = LayoutColor(wearColorScheme.onSecondaryContainer.toArgb()),
    secondaryDim = LayoutColor(wearColorScheme.secondaryDim.toArgb()),

    tertiary = LayoutColor(wearColorScheme.tertiary.toArgb()),
    onTertiary = LayoutColor(wearColorScheme.onTertiary.toArgb()),
    tertiaryContainer = LayoutColor(wearColorScheme.tertiaryContainer.toArgb()),
    onTertiaryContainer = LayoutColor(wearColorScheme.onTertiaryContainer.toArgb()),
    tertiaryDim = LayoutColor(wearColorScheme.tertiaryDim.toArgb()),

    surfaceContainer = LayoutColor(wearColorScheme.surfaceContainer.toArgb()),
    surfaceContainerHigh = LayoutColor(wearColorScheme.surfaceContainerHigh.toArgb()),
    surfaceContainerLow = LayoutColor(wearColorScheme.surfaceContainerLow.toArgb()),
    onSurface = LayoutColor(wearColorScheme.onSurface.toArgb()),
    onSurfaceVariant = LayoutColor(wearColorScheme.onSurfaceVariant.toArgb()),

    outline = LayoutColor(wearColorScheme.outline.toArgb()),
    outlineVariant = LayoutColor(wearColorScheme.outlineVariant.toArgb()),

    error = LayoutColor(wearColorScheme.error.toArgb()),
    onError = LayoutColor(wearColorScheme.onError.toArgb()),
    errorContainer = LayoutColor(wearColorScheme.errorContainer.toArgb()),
    onErrorContainer = LayoutColor(wearColorScheme.onErrorContainer.toArgb()),

    errorDim = LayoutColor(wearColorScheme.errorDim.toArgb()),
)