package com.wovenledger.app.data.outbox

import com.google.gson.Gson
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The queue of writes waiting for a connection.
 *
 * Repositories put work here; [OutboxUploader] takes it out. Nothing in this class
 * touches the network, which is what lets the enqueue rules be tested without one.
 */
@Singleton
class Outbox @Inject constructor(
    private val dao: OutboxDao,
    private val gson: Gson,
) {

    /**
     * Records a write to send later.
     *
     * An edit to a record that is still waiting replaces the payload already queued for
     * it rather than adding a second row: the server has never seen either version, so
     * sending both would upload a document and then immediately correct it. The original
     * operation and position in the queue are kept — a create that has been edited twice
     * is still one create, and it still belongs where it was first written.
     */
    suspend fun enqueue(
        target: OutboxTarget,
        operation: OutboxOperation,
        recordId: Long,
        body: Any,
    ) {
        val payload = gson.toJson(body)
        val queued = dao.pendingFor(target, recordId)

        if (queued == null) {
            dao.insert(
                OutboxEntry(
                    target = target,
                    // A negative record id has never existed on the server, so the
                    // only write that means anything for it is a create. An edit to
                    // something still waiting is an edit to the create itself — a PUT
                    // to /api/sales-invoices/-1 would be nonsense.
                    operation = if (recordId < 0) OutboxOperation.CREATE else operation,
                    payload = payload,
                    recordId = recordId,
                    createdAt = System.currentTimeMillis(),
                )
            )
        } else {
            dao.update(
                queued.copy(
                    payload = payload,
                    // A fresh payload deserves a fresh run of attempts: whatever went
                    // wrong last time was about the old one.
                    attempts = 0,
                    lastError = null,
                    nextAttemptAt = 0L,
                )
            )
        }
    }

    suspend fun pendingCount(): Int = dao.countPending()
}

/** The one status the server can return that a retry, later, will not repeat. */
private const val UNAUTHORIZED = 401

/**
 * Runs a write, telling "could not send this yet" apart from "the server said no".
 *
 * Returns null when the write is worth queueing, which is when the same request would
 * succeed unchanged later. Everything else is rethrown so the form can show it
 * immediately: a 422 is a bad payload, and queueing it would retry a doomed request
 * forever while telling the user their work was saved. A 5xx is likewise the server's
 * own answer and belongs on screen now, not in an hour.
 *
 * Two failures qualify. A connectivity failure, obviously. And an expired token — which
 * is a deliberate choice, because the alternative loses the user's work outright. By the
 * time a 401 reaches the ViewModel the interceptor has already ended the session, so the
 * app has swapped to the sign-in screen and the form that would have shown "nothing was
 * saved" no longer exists to show it. The user would watch a filled-in invoice vanish
 * with no explanation. Queued, it survives the sign-in and goes out on the drain that
 * follows — and unlike a 422, a 401 really will succeed unchanged once there is a token
 * again, which is the same test connectivity failures pass.
 *
 * The write is still attempted first, always. An immediate rejection is far more useful
 * than a queued write that fails silently later, so the queue is a fallback and never a
 * substitute for asking.
 */
suspend fun <T> attemptOnline(write: suspend () -> T): T? = try {
    write()
} catch (offline: IOException) {
    // UnknownHostException, ConnectException, SocketTimeoutException and the rest of
    // OkHttp's transport failures all arrive here. retrofit2.HttpException does not:
    // it is a RuntimeException carrying a status the server chose.
    null
} catch (refused: HttpException) {
    if (refused.code() == UNAUTHORIZED) null else throw refused
}
