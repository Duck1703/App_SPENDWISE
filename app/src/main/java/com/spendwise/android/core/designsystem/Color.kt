package com.spendwise.android.core.designsystem

import androidx.compose.ui.graphics.Color

/**
 * Spendwise color tokens (AD-018), translated from the Human-accepted
 * P3A design system (`P3A-DESIGN-HANDOFF.md` §7). Calm, premium finance:
 * deep-teal brand on light, airy grounds.
 */
object SwColors {
    // Surfaces & ink
    val Ground = Color(0xFFF3F6F4)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceMuted = Color(0xFFEDF2F0)
    val Line = Color(0xFFE3E9E6)
    val Ink = Color(0xFF10282A)
    val Muted = Color(0xFF4A5D5F)

    // Brand
    val BrandDeep = Color(0xFF0B4F4A)
    val Brand = Color(0xFF0B6E5E)
    val BrandMid = Color(0xFF0E7C66)
    val Mint = Color(0xFFDFF3EC)

    // Semantic (never the sole carrier of direction — icons + sign always
    // accompany color per the a11y floor, HANDOFF §8)
    val Income = Color(0xFF0E7C66)
    val IncomeContainer = Color(0xFFDFF3EC)
    val Expense = Color(0xFFC03B3B)
    val ExpenseContainer = Color(0xFFF9E3E1)
    val Warning = Color(0xFFB07A10)
    val WarningContainer = Color(0xFFFCF0D4)
    val Error = Color(0xFFB3261E)
    val ErrorContainer = Color(0xFFF9DEDC)
    val Info = Color(0xFF0F6B7A)
    val InfoContainer = Color(0xFFDCF0F5)

    val OnAccent = Color(0xFFFFFFFF)
}
