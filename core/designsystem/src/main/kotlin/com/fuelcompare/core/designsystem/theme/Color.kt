package com.fuelcompare.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val BrandBlue = Color(0xFF1565C0)
val BrandBlueDark = Color(0xFF0D47A1)
val BrandBlueLight = Color(0xFF42A5F5)

internal val LightPrimary = BrandBlue
internal val LightOnPrimary = Color(0xFFFFFFFF)
internal val LightPrimaryContainer = Color(0xFFD8E8FF)
internal val LightOnPrimaryContainer = BrandBlueDark
internal val LightSecondary = BrandBlueDark
internal val LightOnSecondary = Color(0xFFFFFFFF)
internal val LightSecondaryContainer = Color(0xFFDCE6F5)
internal val LightOnSecondaryContainer = Color(0xFF071B33)
internal val LightTertiary = BrandBlueLight
internal val LightOnTertiary = Color(0xFF002F5F)
internal val LightTertiaryContainer = Color(0xFFD0E8FF)
internal val LightOnTertiaryContainer = Color(0xFF001D36)
internal val LightBackground = Color(0xFFF7F9FE)
internal val LightOnBackground = Color(0xFF171C22)
internal val LightSurface = Color(0xFFFFFDFC)
internal val LightOnSurface = Color(0xFF171C22)
internal val LightSurfaceVariant = Color(0xFFE0E7F1)
internal val LightOnSurfaceVariant = Color(0xFF424A55)
internal val LightOutline = Color(0xFF727B87)
internal val LightError = Color(0xFFBA1A1A)
internal val LightOnError = Color(0xFFFFFFFF)
internal val LightErrorContainer = Color(0xFFFFDAD6)
internal val LightOnErrorContainer = Color(0xFF410002)

internal val DarkPrimary = BrandBlueLight
internal val DarkOnPrimary = Color(0xFF002F5F)
internal val DarkPrimaryContainer = BrandBlueDark
internal val DarkOnPrimaryContainer = Color(0xFFD8E8FF)
internal val DarkSecondary = Color(0xFF90CAF9)
internal val DarkOnSecondary = Color(0xFF002F5F)
internal val DarkSecondaryContainer = Color(0xFF173A61)
internal val DarkOnSecondaryContainer = Color(0xFFD8E8FF)
internal val DarkTertiary = Color(0xFF7CC7FF)
internal val DarkOnTertiary = Color(0xFF00344F)
internal val DarkTertiaryContainer = Color(0xFF004B70)
internal val DarkOnTertiaryContainer = Color(0xFFCBE6FF)
internal val DarkBackground = Color(0xFF0B1118)
internal val DarkOnBackground = Color(0xFFE2E8F0)
internal val DarkSurface = Color(0xFF111A24)
internal val DarkOnSurface = Color(0xFFE2E8F0)
internal val DarkSurfaceVariant = Color(0xFF283541)
internal val DarkOnSurfaceVariant = Color(0xFFBEC8D4)
internal val DarkOutline = Color(0xFF88929E)
internal val DarkError = Color(0xFFFFB4AB)
internal val DarkOnError = Color(0xFF690005)
internal val DarkErrorContainer = Color(0xFF93000A)
internal val DarkOnErrorContainer = Color(0xFFFFDAD6)

@Immutable
data class ExtendedColors(
    val cheapest: Color,
    val onCheapest: Color,
    val cheapestContainer: Color,
    val onCheapestContainer: Color,
    val warning: Color,
    val onWarningContainer: Color,
    val warningContainer: Color,
    val infoContainer: Color,
    val onInfoContainer: Color,
)

internal val LightExtendedColors = ExtendedColors(
    cheapest = Color(0xFF1B7A3D),
    onCheapest = Color(0xFFFFFFFF),
    cheapestContainer = Color(0xFFD7F5DF),
    onCheapestContainer = Color(0xFF0B3D1E),
    warning = Color(0xFF8A5300),
    warningContainer = Color(0xFFFFE7C2),
    onWarningContainer = Color(0xFF2B1A00),
    infoContainer = Color(0xFFD8E8FF),
    onInfoContainer = BrandBlueDark,
)

internal val DarkExtendedColors = ExtendedColors(
    cheapest = Color(0xFF7BDFA3),
    onCheapest = Color(0xFF00391A),
    cheapestContainer = Color(0xFF0F4D2A),
    onCheapestContainer = Color(0xFFC4F7D4),
    warning = Color(0xFFFFCF7A),
    warningContainer = Color(0xFF4D3200),
    onWarningContainer = Color(0xFFFFE7C2),
    infoContainer = Color(0xFF143A66),
    onInfoContainer = Color(0xFFD8E8FF),
)

val LocalExtendedColors = staticCompositionLocalOf { LightExtendedColors }
