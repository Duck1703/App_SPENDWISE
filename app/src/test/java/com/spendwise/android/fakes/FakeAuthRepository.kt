package com.spendwise.android.fakes

import com.spendwise.android.data.repository.AuthRepository
import com.spendwise.android.data.repository.RegisteredUser

/**
 * Scripted [AuthRepository] for ViewModel tests — records calls and runs
 * the configured suspending results so submit-lock behavior is observable.
 */
class FakeAuthRepository : AuthRepository {

    var registerResult: suspend (username: String) -> RegisteredUser =
        { username -> RegisteredUser(id = "user-1", username = username) }
    var loginResult: suspend (username: String, password: String) -> Unit = { _, _ -> }
    var logoutResult: suspend () -> Unit = {}

    val registerCalls = mutableListOf<Pair<String, String>>()
    val loginCalls = mutableListOf<Pair<String, String>>()
    var logoutCalls = 0

    override suspend fun register(username: String, password: String): RegisteredUser {
        registerCalls += username to password
        return registerResult(username)
    }

    override suspend fun login(username: String, password: String) {
        loginCalls += username to password
        loginResult(username, password)
    }

    override suspend fun logout() {
        logoutCalls++
        logoutResult()
    }
}
