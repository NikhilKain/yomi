package eu.kanade.presentation.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import eu.kanade.tachiyomi.R

// Google Sans Flex, one variable file. Text runs with a little roundness; display sizes go
// fully round and slightly wide, which is where the expressive character comes from.

private fun flex(weight: FontWeight, round: Float, width: Float = 100f) = Font(
    resId = R.font.google_sans_flex,
    weight = weight,
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight.weight),
        FontVariation.width(width),
        FontVariation.Setting("ROND", round),
    ),
)

private val TextFamily = FontFamily(
    flex(FontWeight.Normal, 40f),
    flex(FontWeight.Medium, 40f),
    flex(FontWeight.SemiBold, 50f),
    flex(FontWeight.Bold, 60f),
)

val DisplayFamily = FontFamily(
    flex(FontWeight.Medium, 100f, 108f),
    flex(FontWeight.SemiBold, 100f, 110f),
    flex(FontWeight.Bold, 100f, 112f),
    flex(FontWeight.ExtraBold, 100f, 115f),
)

private fun display(
    size: Int,
    line: Int,
    weight: FontWeight = FontWeight.SemiBold,
    tracking: Double = -0.01,
) = TextStyle(
    fontFamily = DisplayFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.em,
)

private fun text(
    size: Int,
    line: Int,
    weight: FontWeight = FontWeight.Normal,
    tracking: Double = 0.0,
) = TextStyle(
    fontFamily = TextFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.em,
)

val YomiTypography = Typography(
    displayLarge = display(56, 60, FontWeight.Bold, -0.02),
    displayMedium = display(44, 50, FontWeight.Bold, -0.02),
    displaySmall = display(36, 42, FontWeight.SemiBold),
    headlineLarge = display(32, 38, FontWeight.SemiBold),
    headlineMedium = display(28, 34, FontWeight.SemiBold),
    headlineSmall = display(24, 30, FontWeight.SemiBold),
    titleLarge = display(21, 27, FontWeight.Medium, 0.0),
    titleMedium = text(16, 22, FontWeight.SemiBold, 0.005),
    titleSmall = text(14, 20, FontWeight.SemiBold, 0.006),
    bodyLarge = text(16, 24),
    bodyMedium = text(14, 20, tracking = 0.01),
    bodySmall = text(12, 16, tracking = 0.02),
    labelLarge = text(14, 20, FontWeight.SemiBold, 0.005),
    labelMedium = text(12, 16, FontWeight.SemiBold, 0.03),
    labelSmall = text(11, 16, FontWeight.Medium, 0.04),
)

/** Condensed and heavy, for numbers that should read at a glance (chapter counters, stats). */
val NumeralFamily = FontFamily(
    flex(FontWeight.Bold, 100f, 85f),
    flex(FontWeight.Black, 100f, 80f),
)
