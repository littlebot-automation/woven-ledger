package com.wovenledger.app.data.outbox

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decides when the outbox is drained.
 *
 * Two moments matter: the app starting, and a connection coming back. The second is a
 * callback from the platform rather than a poll — a phone that spends the morning out
 * of signal should not be waking up every thirty seconds to find out, and
 * [ConnectivityManager.registerDefaultNetworkCallback] tells us the instant it changes.
 *
 * Everything that decides *what* to send lives in [OutboxUploader]; this only decides
 * when, which is why the two are apart. The uploader can then be tested without a
 * network stack.
 */
@Singleton
class OutboxManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val uploader: OutboxUploader,
    private val dao: OutboxDao,
) {

    private companion object {
        const val TAG = "WovenLedgerOutbox"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val watching = AtomicBoolean(false)

    /** The scheduled retry, if one is waiting. Replaced rather than stacked. */
    private var retry: Job? = null

    /** Everything queued, failed or warning — what the pending screen shows. */
    fun entries(): Flow<List<OutboxEntry>> = dao.observeAll()

    /**
     * Starts listening for the network coming back, and drains once now.
     *
     * Safe to call more than once; only the first call registers a callback.
     */
    fun start() {
        drainSoon()

        if (!watching.compareAndSet(false, true)) {
            return
        }

        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return

        manager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.i(TAG, "Network available — draining the outbox")

                scope.launch {
                    // The backoff was a guess that the next few minutes would look
                    // like the last few. This is evidence otherwise, so it is dropped
                    // rather than left to hold the queue on a working connection.
                    dao.clearBackoff()
                    drainNow()
                }
            }
        })
    }

    /** Drains in the background, and schedules another go if it stopped short. */
    fun drainSoon() {
        scope.launch { drainNow() }
    }

    /**
     * Drains and waits.
     *
     * Startup uses this before pulling from the server: a pull overwrites Room with
     * what the server holds, so an edit that has not uploaded yet would be replaced on
     * screen by the version it was meant to correct. Sending first avoids that.
     */
    suspend fun drainNow(): DrainResult {
        val result = uploader.drain()

        retry?.cancel()
        retry = if (result is DrainResult.Offline) {
            scope.launch {
                delay(result.retryInMillis)
                drainNow()
            }
        } else {
            null
        }

        return result
    }

    suspend fun retryFailed(id: Long) {
        uploader.retry(id)
        drainNow()
    }

    suspend fun discard(id: Long) = uploader.discard(id)
}
