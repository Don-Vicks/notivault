package com.notivault.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.notivault.R
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** Inter and JetBrains Mono ship as single variable fonts; each weight is an instance on the wght axis. */
@OptIn(ExperimentalTextApi::class)
private fun variable(res: Int, vararg weights: FontWeight) = FontFamily(
    weights.map { w -> Font(res, w, variationSettings = FontVariation.Settings(FontVariation.weight(w.weight))) },
)

val UiFont = variable(R.font.inter, FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold)
val MonoFont = variable(R.font.jetbrains_mono, FontWeight.Normal, FontWeight.Medium, FontWeight.Bold)

private fun style(size: Int, line: Int, weight: FontWeight, tracking: Double = 0.0) = TextStyle(
    fontFamily = UiFont, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight, letterSpacing = tracking.em,
)

val VaultTypography = Typography(
    displaySmall = style(32, 40, FontWeight.SemiBold, -0.02),
    headlineSmall = style(22, 28, FontWeight.SemiBold, -0.01),
    titleLarge = style(22, 28, FontWeight.SemiBold, -0.01),
    titleMedium = style(18, 24, FontWeight.SemiBold),
    titleSmall = style(15, 20, FontWeight.Medium),
    bodyLarge = style(16, 24, FontWeight.Normal),
    bodyMedium = style(14, 20, FontWeight.Normal),
    bodySmall = style(12, 16, FontWeight.Normal),
    labelLarge = style(14, 20, FontWeight.Medium, 0.01),
    labelMedium = style(13, 16, FontWeight.Medium, 0.02),
    labelSmall = style(12, 16, FontWeight.Medium, 0.04),
)

/** Receipts, timestamps and hashes. */
val MonoStyle = TextStyle(fontFamily = MonoFont, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.em)
