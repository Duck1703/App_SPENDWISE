package com.spendwise.android

import android.app.Application
import com.spendwise.android.core.session.SessionManager
import com.spendwise.android.core.session.SessionRefresher
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class SpendwiseApplication : Application() {

    @Inject
    lateinit var sessionManager: SessionManager

    @Inject
    lateinit var sessionRefresher: SessionRefresher

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        appScope.launch {
            sessionManager.restore()
            // M13 cold-start renewal: an expired access token with a live
            // refresh credential stays in Restoring until this resolves —
            // no stale protected data is shown while identity is unconfirmed.
            if (sessionManager.hasPendingRenewal()) {
                withContext(Dispatchers.IO) { sessionRefresher.renewPendingSession() }
            }
        }
    }
}
