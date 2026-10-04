package com.spendwise.android.feature.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Hilt-wired route entry points (AD-021 seam): the screens below are
 * stateless so UI tests drive them directly; these wrappers only bind
 * ViewModel state and effects for the real app.
 */

@Composable
fun LoginDestination(
    onNavigateBack: () -> Unit,
    onNavigateToRegister: () -> Unit,
) {
    val viewModel: LoginViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LoginScreen(
        state = state,
        onUsernameChanged = viewModel::onUsernameChanged,
        onPasswordChanged = viewModel::onPasswordChanged,
        onTogglePasswordVisibility = viewModel::onTogglePasswordVisibility,
        onSubmit = viewModel::submit,
        onNavigateBack = onNavigateBack,
        onNavigateToRegister = onNavigateToRegister,
    )
}

@Composable
fun RegisterDestination(
    onRegistered: (username: String) -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToLogin: () -> Unit,
) {
    val viewModel: RegisterViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is RegisterViewModel.Event.Registered -> onRegistered(event.username)
            }
        }
    }
    RegisterScreen(
        state = state,
        onUsernameChanged = viewModel::onUsernameChanged,
        onPasswordChanged = viewModel::onPasswordChanged,
        onTogglePasswordVisibility = viewModel::onTogglePasswordVisibility,
        onSubmit = viewModel::submit,
        onNavigateBack = onNavigateBack,
        onNavigateToLogin = onNavigateToLogin,
    )
}
