package com.spendwise.android.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.spendwise.android.R
import com.spendwise.android.core.designsystem.SwColors
import com.spendwise.android.core.designsystem.SwHeader
import com.spendwise.android.core.designsystem.SwSpacing

/**
 * Home (S05) — M3 scope is deliberately minimal (M4 owns the dashboard):
 * the screen hosts the app-wide primary action. Per AD-019 the FAB is
 * extended near the top ("+ Add transaction") and collapses to a compact
 * icon button after meaningful scroll — plain scroll state, no
 * animation framework.
 */
@Composable
fun HomeScreen(
    onAddTransaction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    // Collapse once the user has scrolled past roughly the header.
    val collapsed by remember { derivedStateOf { scrollState.value > 96 } }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = SwColors.Ground,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddTransaction,
                expanded = !collapsed,
                icon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_lucide_plus),
                        contentDescription = null,
                    )
                },
                text = { Text(stringResource(R.string.home_add_transaction)) },
                containerColor = SwColors.Brand,
                contentColor = SwColors.OnAccent,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(innerPadding)
                .padding(horizontal = SwSpacing.Lg),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SwHeader(title = stringResource(R.string.home_title))
            Text(
                text = stringResource(R.string.home_placeholder),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            // Spacer gives the column real scroll range so the extended
            // to compact FAB transition is exercisable pre-M4.
            Spacer(Modifier.height(480.dp))
        }
    }
}
