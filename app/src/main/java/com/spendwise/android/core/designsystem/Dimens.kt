package com.spendwise.android.core.designsystem

import androidx.compose.ui.unit.dp

/**
 * Spacing / sizing tokens (AD-018). The a11y floor requires 44–56dp touch
 * targets — `TouchTarget` encodes the minimum.
 */
object SwSpacing {
    val Xs = 4.dp
    val Sm = 8.dp
    val Md = 16.dp
    val Lg = 24.dp
    val Xl = 32.dp

    val TouchTargetMin = 44.dp
}

object SwRadii {
    val Sm = 8.dp
    val Md = 12.dp
    val Lg = 16.dp
    val Pill = 999.dp
}
