package com.tobfd.tsuzuki.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.tobfd.tsuzuki.core.designsystem.R

// Google Sans Flex is bundled unmodified as one variable font; each weight is an instance of its
// wght axis. License: assets/licenses/google_sans_flex_OFL.txt.
private fun googleSansFlex(weight: FontWeight) = Font(
    resId = R.font.google_sans_flex,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight))
)

internal val GoogleSansFlex = FontFamily(
    googleSansFlex(FontWeight.Normal),
    googleSansFlex(FontWeight.Medium),
    googleSansFlex(FontWeight.SemiBold),
    googleSansFlex(FontWeight.Bold)
)

private val baseline = Typography()

private fun TextStyle.withFont() = copy(fontFamily = GoogleSansFlex)

/** A style from design/tokens.json (typography.scale): size sp, line height sp, weight. */
private fun TextStyle.fromToken(size: Int, lineHeight: Int, weight: Int) = copy(
    fontFamily = GoogleSansFlex,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = FontWeight(weight)
)

/**
 * Type scale from design/tokens.json; styles the tokens leave out keep Material defaults. Public for
 * the home-screen widgets, which take sizes and weights from it (Glance can't use app fonts).
 */
val TsuzukiTypography = Typography(
    displayLarge = baseline.displayLarge.withFont(),
    displayMedium = baseline.displayMedium.withFont(),
    displaySmall = baseline.displaySmall.fromToken(36, 44, 600),
    headlineLarge = baseline.headlineLarge.withFont(),
    headlineMedium = baseline.headlineMedium.fromToken(28, 36, 600),
    headlineSmall = baseline.headlineSmall.fromToken(24, 32, 500),
    titleLarge = baseline.titleLarge.fromToken(22, 28, 500),
    titleMedium = baseline.titleMedium.fromToken(16, 24, 500),
    titleSmall = baseline.titleSmall.withFont(),
    bodyLarge = baseline.bodyLarge.fromToken(16, 24, 400),
    bodyMedium = baseline.bodyMedium.fromToken(14, 20, 400),
    bodySmall = baseline.bodySmall.withFont(),
    labelLarge = baseline.labelLarge.fromToken(14, 20, 500),
    labelMedium = baseline.labelMedium.fromToken(12, 16, 500),
    labelSmall = baseline.labelSmall.fromToken(11, 16, 500)
)
