package com.spendwise.android.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.spendwise.android.R
import com.spendwise.android.core.designsystem.SwRadii
import com.spendwise.android.core.designsystem.SwSpacing

/** Maps a field-level auth error to its localized display text. */
@Composable
fun AuthFieldError.displayText(): String = when (this) {
    AuthFieldError.Required -> stringResource(R.string.auth_error_required)
    is AuthFieldError.TooShort -> stringResource(R.string.auth_error_password_min, minLength)
    is AuthFieldError.Server -> message
}

/** Maps a form-level auth error to its localized display text. */
@Composable
fun AuthFormError.displayText(): String = stringResource(
    when (this) {
        AuthFormError.InvalidCredentials -> R.string.auth_error_invalid_credentials
        AuthFormError.UsernamePossiblyTaken -> R.string.auth_error_username_taken
        AuthFormError.NetworkUnavailable -> R.string.auth_error_network
        AuthFormError.ServerError -> R.string.auth_error_server
        AuthFormError.Generic -> R.string.auth_error_generic
    },
)

/**
 * Shared auth layout: optional back affordance, headline + subtitle,
 * vertically-centered scrollable content with IME padding.
 */
@Composable
fun AuthScaffold(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onNavigateBack: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = SwSpacing.Lg),
        ) {
            Spacer(Modifier.height(SwSpacing.Md))
            if (onNavigateBack != null) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.size(SwSpacing.TouchTargetMin),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.auth_back),
                    )
                }
            } else {
                Spacer(Modifier.height(SwSpacing.TouchTargetMin))
            }
            Spacer(Modifier.height(SwSpacing.Lg))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(SwSpacing.Xs))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(SwSpacing.Lg))
            content()
            Spacer(Modifier.height(SwSpacing.Xl))
        }
    }
}

/** Form-level error banner (errorContainer, icon + message). */
@Composable
fun FormErrorBanner(text: String, modifier: Modifier = Modifier) {
    AuthBanner(
        text = text,
        containerColor = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        icon = Icons.Filled.Warning,
        iconDescription = stringResource(R.string.auth_error_banner_cd),
        modifier = modifier,
    )
}

/** Neutral confirmation banner (e.g. account created → sign in). */
@Composable
fun InfoBanner(text: String, modifier: Modifier = Modifier) {
    AuthBanner(
        text = text,
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        icon = Icons.Filled.Info,
        iconDescription = stringResource(R.string.auth_info_banner_cd),
        modifier = modifier,
    )
}

@Composable
private fun AuthBanner(
    text: String,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconDescription: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(SwRadii.Md),
        color = containerColor,
    ) {
        Row(
            modifier = Modifier.padding(SwSpacing.Md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SwSpacing.Sm),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = iconDescription,
                tint = contentColor,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
            )
        }
    }
}

/** Centered circular brand mark used by the welcome screen. */
@Composable
fun BrandMark(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.size(72.dp),
        shape = androidx.compose.foundation.shape.CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.app_name).take(1),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center,
            )
        }
    }
}
