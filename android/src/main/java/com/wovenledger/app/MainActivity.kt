package com.wovenledger.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.wovenledger.app.data.auth.SessionManager
import com.wovenledger.app.data.outbox.OutboxManager
import com.wovenledger.app.data.sync.SyncManager
import com.wovenledger.app.ui.App
import com.wovenledger.app.ui.theme.WovenLedgerTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var syncManager: SyncManager

    @Inject
    lateinit var outbox: OutboxManager

    @Inject
    lateinit var session: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            // Nothing may touch /api before the stored token is back in memory: every
            // call would 401, and a 401 walking the outbox used to mark each queued
            // write as refused, one doomed request per row. A signed-out launch does
            // none of this and simply shows the sign-in screen; LoginViewModel runs
            // the same catch-up the moment there is a token.
            if (!session.restore()) {
                return@launch
            }

            // Send before pulling, deliberately. A pull replaces each row with the
            // server's copy, so an edit that has not uploaded yet would be overwritten
            // on screen by the very version it was meant to correct. The queue survives
            // that either way — the write would still go out — but the user would watch
            // their correction disappear and reasonably conclude it had been lost.
            outbox.drainNow()
            syncManager.sync()

            // And keep draining whenever a connection comes back, for as long as the
            // app is running.
            outbox.start()
        }

        setContent {
            WovenLedgerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    App()
                }
            }
        }
    }
}
