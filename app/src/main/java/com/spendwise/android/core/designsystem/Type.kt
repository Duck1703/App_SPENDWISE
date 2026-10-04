package com.spendwise.android.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.spendwise.android.R

/**
 * Typography baseline (AD-018) — P3A's Manrope variable font, with the
 * Compose weights we use mapped onto the font's `wght` variation axis.
 *
 * Every Material 3 slot carries the family (EXT-MIN-1): no style falls
 * back to the platform typeface — the unmapped defaults rendered hero
 * amounts and secondary labels in Roboto.
 */
@OptIn(ExperimentalTextApi::class)
private val Manrope = FontFamily(
    Font(
        R.font.manrope,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400)),
    ),
    Font(
        R.font.manrope,
        weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500)),
    ),
    Font(
        R.font.manrope,
        weight = FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(600)),
    ),
    Font(
        R.font.manrope,
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700)),
    ),
)

private fun manrope(
    weight: FontWeight,
    size: Int,
    lineHeight: Int,
): TextStyle = TextStyle(
    fontFamily = Manrope,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
)

val SpendwiseTypography = Typography(
    displayLarge = manrope(FontWeight.SemiBold, 57, 64),
    displayMedium = manrope(FontWeight.SemiBold, 45, 52),
    displaySmall = manrope(FontWeight.SemiBold, 36, 44),
    headlineLarge = manrope(FontWeight.SemiBold, 32, 40),
    headlineMedium = manrope(FontWeight.SemiBold, 24, 32),
    headlineSmall = manrope(FontWeight.SemiBold, 21, 28),
    titleLarge = manrope(FontWeight.SemiBold, 20, 28),
    titleMedium = manrope(FontWeight.Medium, 16, 24),
    titleSmall = manrope(FontWeight.Medium, 14, 20),
    bodyLarge = manrope(FontWeight.Normal, 16, 24),
    bodyMedium = manrope(FontWeight.Normal, 14, 20),
    bodySmall = manrope(FontWeight.Normal, 12, 16),
    labelLarge = manrope(FontWeight.Medium, 14, 20),
    labelMedium = manrope(FontWeight.Medium, 12, 16),
    labelSmall = manrope(FontWeight.Medium, 11, 16),
)
