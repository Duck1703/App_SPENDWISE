package com.spendwise.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.spendwise.android.app.SpendwiseApp
import com.spendwise.android.core.designsystem.SpendwiseTheme
import com.spendwise.android.core.session.SessionManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Session restore is kicked off in SpendwiseApplication.onCreate so
        // the graph picks a branch before the first frame (§13).
        setContent {
            SpendwiseTheme {
                val session by sessionManager.session.collectAsStateWithLifecycle()
                SpendwiseApp(
                    session = session,
                    onSignOut = { lifecycleScope.launch { sessionManager.signOut() } },
                )
            }
        }
    }
}
