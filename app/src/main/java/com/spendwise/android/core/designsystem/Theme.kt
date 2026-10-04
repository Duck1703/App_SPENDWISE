package com.spendwise.android.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * `SpendwiseTheme` (AD-018): Material 3 with the Spendwise token scheme.
 * Light only — dark theme is explicitly out of V1 scope (P3A handoff).
 */
private val SpendwiseLightScheme = lightColorScheme(
    primary = SwColors.Brand,
    onPrimary = SwColors.OnAccent,
    primaryContainer = SwColors.Mint,
    onPrimaryContainer = SwColors.BrandDeep,
    secondary = SwColors.BrandMid,
    onSecondary = SwColors.OnAccent,
    secondaryContainer = SwColors.Mint,
    onSecondaryContainer = SwColors.BrandDeep,
    tertiary = SwColors.Info,
    onTertiary = SwColors.OnAccent,
    tertiaryContainer = SwColors.InfoContainer,
    onTertiaryContainer = SwColors.Info,
    background = SwColors.Ground,
    onBackground = SwColors.Ink,
    surface = SwColors.Surface,
    onSurface = SwColors.Ink,
    surfaceVariant = SwColors.SurfaceMuted,
    onSurfaceVariant = SwColors.Muted,
    outline = SwColors.Muted,
    outlineVariant = SwColors.Line,
    error = SwColors.Error,
    onError = SwColors.OnAccent,
    errorContainer = SwColors.ErrorContainer,
    onErrorContainer = SwColors.Error,
)

@Composable
fun SpendwiseTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SpendwiseLightScheme,
        typography = SpendwiseTypography,
        content = content,
    )
}
