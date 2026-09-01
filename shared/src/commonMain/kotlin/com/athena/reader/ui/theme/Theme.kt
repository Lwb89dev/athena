package com.athena.reader.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

/** Reader-only typography knobs the settings screen can change at runtime. */
data class ReaderTypography(
    val fontSize: Int = 19,
    val lineHeightMultiplier: Float = 1.65f,
    val fontFamily: FontFamily = FontFamily.Serif,
) {
    val bodyStyle: TextStyle
        get() = TextStyle(
            fontFamily = fontFamily,
            fontSize = fontSize.sp,
            lineHeight = (fontSize * lineHeightMultiplier).sp,
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Proportional,
                trim = LineHeightStyle.Trim.None,
            ),
        )
}

val LocalReaderTypography = staticCompositionLocalOf { ReaderTypography() }

/** True when the app is painting on a dark surface — markers need to know. */
val LocalIsDarkTheme = staticCompositionLocalOf { false }

@Composable
fun AthenaTheme(
    // A Hellenistic library is sunlit olive wood, not the host desktop's dark theme.
    darkTheme: Boolean = false,
    readerTypography: ReaderTypography = ReaderTypography(),
    content: @Composable () -> Unit,
) {
    val manuscript = manuscriptFamily()
    val reading = readerTypography.copy(fontFamily = manuscript)
    CompositionLocalProvider(
        LocalIsDarkTheme provides darkTheme,
        LocalReaderTypography provides reading,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) AthenaDarkScheme else AthenaLightScheme,
            typography = athenaTypography(inscriptionFamily(), manuscript),
            shapes = AthenaShapes,
            content = content,
        )
    }
}
