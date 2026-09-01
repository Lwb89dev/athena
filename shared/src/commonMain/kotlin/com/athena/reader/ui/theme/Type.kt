package com.athena.reader.ui.theme

import athena.shared.generated.resources.Res
import athena.shared.generated.resources.cinzel_bold
import athena.shared.generated.resources.cinzel_regular
import athena.shared.generated.resources.eb_garamond_italic
import athena.shared.generated.resources.eb_garamond_regular
import athena.shared.generated.resources.eb_garamond_semibold
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.Font

/** Temple-inscription capitals. Reads as Greek even when the words are not. */
@Composable
fun inscriptionFamily(): FontFamily = FontFamily(
    Font(Res.font.cinzel_regular, weight = FontWeight.Normal),
    Font(Res.font.cinzel_bold, weight = FontWeight.Bold),
)

/** Book hand: a Garamond cut, the closest thing to a printed classical page. */
@Composable
fun manuscriptFamily(): FontFamily = FontFamily(
    Font(Res.font.eb_garamond_regular, weight = FontWeight.Normal),
    Font(Res.font.eb_garamond_italic, weight = FontWeight.Normal, style = FontStyle.Italic),
    Font(Res.font.eb_garamond_semibold, weight = FontWeight.SemiBold),
)

@Composable
fun athenaTypography(
    inscription: FontFamily = inscriptionFamily(),
    manuscript: FontFamily = manuscriptFamily(),
): Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = inscription,
        fontWeight = FontWeight.Bold,
        fontSize = 40.sp,
        lineHeight = 46.sp,
        letterSpacing = 2.sp,
    ),
    displayMedium = TextStyle(
        fontFamily = inscription,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        letterSpacing = 1.8.sp,
    ),
    displaySmall = TextStyle(
        fontFamily = inscription,
        fontWeight = FontWeight.Normal,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = 1.6.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = inscription,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = 1.4.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = inscription,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = 1.2.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = inscription,
        fontWeight = FontWeight.Normal,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = 1.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = inscription,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = 1.2.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = inscription,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 1.4.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = inscription,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 1.1.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = manuscript,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = manuscript,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.1.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = manuscript,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.2.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = inscription,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 1.2.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = inscription,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 1.1.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = inscription,
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 1.sp,
    ),
)
