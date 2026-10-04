package com.spendwise.android.core.designsystem

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spendwise.android.R

/**
 * Screen header per P3A: optional back chevron, title, optional subtitle.
 */
@Composable
fun SwHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.size(SwSpacing.TouchTargetMin)) {
                Icon(
                    painter = painterResource(R.drawable.ic_lucide_chevron_left),
                    contentDescription = stringResource(R.string.cd_navigate_back),
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() },
        )
        if (subtitle != null) {
            Spacer(Modifier.height(SwSpacing.Xs))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Flush card container (white, 20dp radius, 1dp line) — row lists live inside. */
@Composable
fun SwCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SwColors.Surface),
        border = BorderStroke(1.dp, SwColors.Line),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        content = { content() },
    )
}

enum class SwBannerKind { Info, Error }

/**
 * Inline banner (HANDOFF §8): role=status for info, role=alert for errors —
 * assertive announcements only on real failures.
 */
@Composable
fun SwBanner(
    title: String,
    text: String,
    modifier: Modifier = Modifier,
    kind: SwBannerKind = SwBannerKind.Info,
) {
    val (container, content, icon) = when (kind) {
        SwBannerKind.Info -> Triple(SwColors.InfoContainer, SwColors.Info, R.drawable.ic_lucide_info)
        SwBannerKind.Error -> Triple(SwColors.ErrorContainer, SwColors.Error, R.drawable.ic_lucide_circle_alert)
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(SwRadii.Md))
            .background(container)
            .semantics {
                liveRegion = if (kind == SwBannerKind.Error) LiveRegionMode.Assertive else LiveRegionMode.Polite
            }
            .padding(horizontal = SwSpacing.Md, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, style = MaterialTheme.typography.labelLarge, color = content, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = content)
        }
    }
}

/**
 * Empty/result state (prototype `StateBlock`): optional illustration,
 * centered icon-or-image, title, text, optional action button.
 */
@Composable
fun SwStateBlock(
    title: String,
    text: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    @DrawableRes illustration: Int? = null,
    @DrawableRes icon: Int? = null,
    isError: Boolean = false,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(SwSpacing.Lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(SwSpacing.Xl))
        if (illustration != null) {
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(SwColors.Mint, SwColors.Mint.copy(alpha = 0f)))),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(illustration),
                    contentDescription = null,
                    modifier = Modifier.size(104.dp),
                    contentScale = ContentScale.Fit,
                )
            }
        } else if (icon != null) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(if (isError) SwColors.ErrorContainer else SwColors.Mint),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = if (isError) SwColors.Error else SwColors.Brand,
                    modifier = Modifier.size(30.dp),
                )
            }
        }
        Spacer(Modifier.height(SwSpacing.Md))
        Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(SwSpacing.Xs))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(SwSpacing.Lg))
            Button(onClick = onAction, modifier = Modifier.height(52.dp)) {
                Text(actionLabel)
            }
        }
        Spacer(Modifier.height(SwSpacing.Xl))
    }
}

/** Preset "can't reach the server" state (network failure). */
@Composable
fun SwNetworkError(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    SwStateBlock(
        title = stringResource(R.string.state_network_title),
        text = stringResource(R.string.state_network_body),
        actionLabel = stringResource(R.string.action_try_again),
        onAction = onRetry,
        icon = R.drawable.ic_lucide_circle_alert,
        isError = true,
        modifier = modifier,
    )
}

/** Preset "server blew up" state (5xx). */
@Composable
fun SwServerError(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    SwStateBlock(
        title = stringResource(R.string.state_server_title),
        text = stringResource(R.string.state_server_body),
        actionLabel = stringResource(R.string.action_try_again),
        onAction = onRetry,
        icon = R.drawable.ic_lucide_circle_alert,
        isError = true,
        modifier = modifier,
    )
}

/**
 * Loading placeholder — static rounded blocks (no shimmer: static by
 * default also satisfies the reduced-motion requirement, HANDOFF §10).
 */
@Composable
fun SwSkeletonRows(modifier: Modifier = Modifier, count: Int = 3) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(count) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(76.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(SwColors.SurfaceMuted),
            )
        }
    }
}
