package com.athena.reader.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.athena.reader.domain.model.HighlightColor

/**
 * Pigments of a Hellenistic library: papyrus, lamp-black ink, terracotta
 * from Attic pottery, and the oxblood-and-gold of the temple emblem.
 */
internal val Oxblood = Color(0xFF380F0F)
internal val Terracotta = Color(0xFF8F3D24)
internal val GoldLeaf = Color(0xFFC4A35A)
internal val Bronze = Color(0xFF6E5428)
internal val Olive = Color(0xFF5A6340)
internal val Ink = Color(0xFF2C1A0E)
internal val Papyrus = Color(0xFFF6EED4)
internal val Parchment = Color(0xFFFBF6E4)
internal val Fiber = Color(0xFFE8DCB8)
internal val Ivory = Color(0xFFF4EBD4)
internal val NightWood = Color(0xFF140E0A)
internal val Panel = Color(0xFF1E1510)
internal val LampGold = Color(0xFFD7B56A)

val AthenaLightScheme = lightColorScheme(
    primary = Terracotta,
    onPrimary = Parchment,
    primaryContainer = Color(0xFFE8C9A0),
    onPrimaryContainer = Oxblood,
    secondary = Bronze,
    onSecondary = Parchment,
    secondaryContainer = Color(0xFFE6D3A8),
    onSecondaryContainer = Color(0xFF2A1C08),
    tertiary = Olive,
    onTertiary = Parchment,
    tertiaryContainer = Color(0xFFD5DCB8),
    onTertiaryContainer = Color(0xFF1C2410),
    background = Papyrus,
    onBackground = Ink,
    surface = Parchment,
    onSurface = Ink,
    surfaceVariant = Fiber,
    onSurfaceVariant = Color(0xFF5A4630),
    surfaceTint = Bronze,
    surfaceContainerLowest = Color(0xFFFDF8EA),
    surfaceContainerLow = Parchment,
    surfaceContainer = Fiber,
    surfaceContainerHigh = Color(0xFFE4D4A4),
    surfaceContainerHighest = Color(0xFFDCC890),
    outline = Color(0xFF8A7348),
    outlineVariant = Color(0xFFC4B089),
    error = Color(0xFF8B2E2E),
    onError = Parchment,
)

val AthenaDarkScheme = darkColorScheme(
    primary = LampGold,
    onPrimary = Oxblood,
    primaryContainer = Color(0xFF5C3A14),
    onPrimaryContainer = Color(0xFFF0D9A0),
    secondary = Color(0xFFD4C09A),
    onSecondary = Color(0xFF2A1C0C),
    secondaryContainer = Color(0xFF3E2E18),
    onSecondaryContainer = Ivory,
    tertiary = Color(0xFFB7C48A),
    onTertiary = Color(0xFF1A2010),
    tertiaryContainer = Color(0xFF3A4228),
    onTertiaryContainer = Color(0xFFDCE4C0),
    background = NightWood,
    onBackground = Ivory,
    surface = Panel,
    onSurface = Ivory,
    surfaceVariant = Color(0xFF3A2A1C),
    onSurfaceVariant = Color(0xFFD4C4A8),
    surfaceTint = LampGold,
    surfaceContainerLowest = Color(0xFF0E0A08),
    surfaceContainerLow = Color(0xFF1A120E),
    surfaceContainer = Color(0xFF241810),
    surfaceContainerHigh = Color(0xFF2C1E14),
    surfaceContainerHighest = Color(0xFF362418),
    outline = Color(0xFFA09070),
    outlineVariant = Color(0xFF5A4830),
    error = Color(0xFFE8A0A0),
    onError = Oxblood,
)

/**
 * Mineral washes rather than highlighter neon: they sit behind text on
 * papyrus and on lamp-lit wood without turning into candy.
 */
fun HighlightColor.surface(darkTheme: Boolean): Color = when (this) {
    HighlightColor.Yellow -> if (darkTheme) Color(0xFF5C4A18) else Color(0xFFE8C96A)
    HighlightColor.Green -> if (darkTheme) Color(0xFF2A3C20) else Color(0xFFC5D4A3)
    HighlightColor.Blue -> if (darkTheme) Color(0xFF243848) else Color(0xFFC5D4DC)
    HighlightColor.Pink -> if (darkTheme) Color(0xFF5C2830) else Color(0xFFE8C4B8)
    HighlightColor.Purple -> if (darkTheme) Color(0xFF3C2A48) else Color(0xFFD4C8DC)
}
