package com.spendwise.android.core.designsystem

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.spendwise.android.R
import com.spendwise.android.core.model.AccountType
import com.spendwise.android.core.model.Money
import com.spendwise.android.core.ui.format.MoneyFormat

/**
 * Deep-teal hero panel (P3A §7/§8): brand gradient + contour motif + scrim.
 * One motif per screen — callers pick `topo` (accounts/budgets) per the
 * handoff's motif map. Content renders inside with white/mint colors.
 */
@Composable
fun SwHeroPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    colorStops = arrayOf(
                        0.0f to Color(0xFF083F3B),
                        0.45f to SwColors.BrandDeep,
                        1.0f to Color(0xFF0C5F54),
                    ),
                ),
            ),
    ) {
        // Radial highlight (85% / 10% anchor, like the prototype hero).
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.radialGradient(
                        colorStops = arrayOf(
                            0.0f to Color(0xFF157E6A).copy(alpha = 0.55f),
                            0.6f to Color(0xFF157E6A).copy(alpha = 0f),
                        ),
                        center = androidx.compose.ui.geometry.Offset(340f, 40f),
                        radius = 620f,
                    ),
                ),
        )
        Image(
            painter = painterResource(R.drawable.topo_lines),
            contentDescription = null,
            modifier = Modifier.matchParentSize(),
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopEnd,
        )
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
            content = content,
        )
    }
}

/**
 * Account type tile (P3A): Cash → mint/emerald gradient + wallet icon;
 * Bank → blue gradient + landmark icon; unknown → muted fallback tile.
 * Decorative — the adjacent type label carries the semantics.
 */
@Composable
fun AccountTile(type: AccountType, modifier: Modifier = Modifier, size: Dp = 46.dp) {
    val (brush, icon) = when (type) {
        AccountType.CASH -> Brush.linearGradient(
            listOf(Color(0xFF1FA684), Color(0xFF0D7A63)),
        ) to R.drawable.ic_lucide_wallet
        AccountType.BANK -> Brush.linearGradient(
            listOf(Color(0xFF3F86D6), Color(0xFF2A62A8)),
        ) to R.drawable.ic_lucide_landmark
        AccountType.UNKNOWN -> Brush.linearGradient(
            listOf(SwColors.SurfaceMuted, SwColors.SurfaceMuted),
        ) to R.drawable.ic_lucide_wallet
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size / 3))
            .background(brush),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = if (type == AccountType.UNKNOWN) SwColors.Muted else Color.White,
            modifier = Modifier.size(size / 2),
        )
    }
}

/**
 * Money display (HANDOFF §8): grouped amount in tabular figures + a muted
 * currency-code suffix sized ~0.62em that never wraps away from the amount.
 */
@Composable
fun MoneyText(
    money: Money,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    currencyColor: Color = SwColors.Muted,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.Bottom) {
        Text(
            text = MoneyFormat.format(money),
            style = style.copy(fontFeatureSettings = "tnum"),
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = money.currency.code,
            style = style.copy(
                fontSize = style.fontSize * 0.62,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            ),
            color = currencyColor,
            maxLines = 1,
        )
    }
}
