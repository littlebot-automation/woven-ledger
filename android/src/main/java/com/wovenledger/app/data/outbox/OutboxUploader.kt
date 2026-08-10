package com.wovenledger.app.data.outbox

import android.util.Log
import com.google.gson.Gson
import com.wovenledger.app.data.api.NewParty
import com.wovenledger.app.data.api.NewPayment
import com.wovenledger.app.data.api.NewPurchaseBill
import com.wovenledger.app.data.api.NewReceipt
import com.wovenledger.app.data.api.NewSalesInvoice
import com.wovenledger.app.data.api.NewStaffWork
import com.wovenledger.app.data.repository.PartyRepository
import com.wovenledger.app.data.repository.PaymentRepository
import com.wovenledger.app.data.repository.PurchaseBillRepository
import com.wovenledger.app.data.repository.ReceiptRepository
import com.wovenledger.app.data.repository.SalesInvoiceRepository
import com.wovenledger.app.data.repository.StaffWorkRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** How a drain ended, which is what decides whether it is worth trying again soon. */
sealed interface DrainResult {
    /** The queue is empty, or everything left in it has been given up on. */
    data class Done(val uploaded: Int) : DrainResult

    /**
     * Stopped because the phone still cannot reach the server. [retryInMillis] is how
     * long to wait before the next attempt.
     */
    data class Offline(val uploaded: Int, val retryInMillis: Long) : DrainResult

    /**
     * Stopped because the server no longer accepts the token.
     *
     * Distinct from [Offline] because retrying is pointless until somebody signs in
     * again — a scheduled retry would spend the next half hour asking the same
     * question. The queue is untouched, and a successful sign-in restarts the drain.
     */
    data class Unauthorized(val uploaded: Int) : DrainResult
}

/**
 * Sends what the outbox is holding, oldest first.
 *
 * Order is the whole point of doing them one at a time and stopping at the first
 * connectivity failure: writes are sent in the order they were made, so a create can
 * never be overtaken by the edit that followed it. Pushing on past a failure would
 * also mean hammering an unreachable server once per queued row.
 */
@Singleton
class OutboxUploader @Inject constructor(
    private val dao: OutboxDao,
    private val gson: Gson,
    private val parties: PartyRepository,
    private val salesInvoices: SalesInvoiceRepository,
    private val purchaseBills: PurchaseBillRepository,
    private val receipts: ReceiptRepository,
    private val payments: PaymentRepository,
    private val staffWork: StaffWorkRepository,
) {

    private companion object {
        const val TAG = "WovenLedgerOutbox"

        /**
         * After this many failed attempts a row is marked failed and shown to the
         * user instead of being retried on its own. Retrying forever would hide a
         * write that is never going to succeed behind a spinner nobody is watching.
         */
        const val MAX_ATTEMPTS = 10

        const val FIRST_BACKOFF_MILLIS = 15_000L
        const val MAX_BACKOFF_MILLIS = 30 * 60_000L

        const val UNAUTHORIZED = 401
    }

    /** One drain at a time: two would race on the same row and send it twice. */
    private val draining = Mutex()

    suspend fun drain(): DrainResult = draining.withLock { drainQueue() }

    private suspend fun drainQueue(): DrainResult {
        var uploaded = 0

        while (true) {
            val entry = dao.oldestPending() ?: return DrainResult.Done(uploaded)
            val now = System.currentTimeMillis()

            // The oldest entry is the one that has to go first, so a backoff on it
            // holds the whole queue rather than letting the next one jump ahead.
            if (entry.nextAttemptAt > now) {
                return DrainResult.Offline(uploaded, entry.nextAttemptAt - now)
            }

            try {
                val warnings = send(entry)
                settle(entry, warnings)
                uploaded += 1
            } catch (cancelled: CancellationException) {
                // The drain was called off, not refused. Leave the row alone.
                throw cancelled
            } catch (offline: IOException) {
                // Still no connection. Record the attempt and stop: everything behind
                // this row would fail the same way, and order must hold.
                Log.i(TAG, "Upload postponed for ${entry.target} #${entry.recordId}", offline)

                val attempts = entry.attempts + 1
                val backoff = backoffFor(attempts)
                dao.update(
                    entry.copy(
                        attempts = attempts,
                        lastError = offline.message ?: "Could not reach the server",
                        nextAttemptAt = now + backoff,
                        status = if (attempts >= MAX_ATTEMPTS) OutboxStatus.FAILED else entry.status,
                    )
                )

                return DrainResult.Offline(uploaded, backoff)
            } catch (refused: HttpException) {
                if (refused.code() != UNAUTHORIZED) {
                    giveUp(entry, refused)

                    continue
                }

                // The token has expired or been revoked. This row is not at fault and
                // nothing about it needs changing: no attempt is counted, the status
                // stays PENDING, and the loop stops rather than walking the queue and
                // marking every remaining write as refused — which is what the generic
                // arm below would do, one doomed request at a time, leaving the user a
                // per-row Retry button as the only way back.
                Log.w(TAG, "Upload unauthorized for ${entry.target} #${entry.recordId}", refused)

                return DrainResult.Unauthorized(uploaded)
            } catch (refused: Exception) {
                giveUp(entry, refused)
            }
        }
    }

    /**
     * The server answered and said no — a 422 on a payload it will never accept, or a
     * 404 for a record that has since been deleted. Retrying cannot help, so it is
     * marked failed and surfaced. The rest of the queue still goes: each record's
     * writes are coalesced into a single row, so nothing behind this one depends on it.
     */
    private suspend fun giveUp(entry: OutboxEntry, refused: Exception) {
        Log.w(TAG, "Upload refused for ${entry.target} #${entry.recordId}", refused)

        dao.update(
            entry.copy(
                attempts = entry.attempts + 1,
                lastError = refused.message ?: refused::class.simpleName ?: "Refused",
                status = OutboxStatus.FAILED,
            )
        )
    }

    /** Puts a failed row back in the queue, at the user's asking. */
    suspend fun retry(id: Long) {
        val entry = dao.find(id) ?: return

        dao.update(
            entry.copy(
                status = OutboxStatus.PENDING,
                attempts = 0,
                lastError = null,
                nextAttemptAt = 0L,
            )
        )
    }

    /** Forgets a row: a failed write the user has decided not to send, or a read warning. */
    suspend fun discard(id: Long) {
        val entry = dao.find(id) ?: return

        // Abandoning a create takes its local echo with it. Leaving the echo behind
        // would show a document in the lists forever that nothing is going to upload —
        // which is the same lie as a silent queue, told the other way round.
        if (entry.operation == OutboxOperation.CREATE && entry.recordId < 0) {
            deleteEcho(entry)
        }

        dao.delete(entry)
    }

    private suspend fun deleteEcho(entry: OutboxEntry) = when (entry.target) {
        OutboxTarget.PARTY -> parties.deleteLocal(entry.recordId)
        OutboxTarget.SALES_INVOICE -> salesInvoices.deleteLocal(entry.recordId)
        OutboxTarget.PURCHASE_BILL -> purchaseBills.deleteLocal(entry.recordId)
        OutboxTarget.RECEIPT -> receipts.deleteLocal(entry.recordId)
        OutboxTarget.PAYMENT -> payments.deleteLocal(entry.recordId)
        OutboxTarget.STAFF_WORK -> staffWork.deleteLocal(entry.recordId)
    }

    /**
     * A successful upload either disappears or waits to be read.
     *
     * Warnings are the reason for the second case. The server reports a stock
     * shortfall beside a save it accepted, and for a queued document that answer
     * arrives long after the form that would have shown it has gone. Deleting the row
     * here would drop the warning silently, so it stays until the user has seen it.
     */
    private suspend fun settle(entry: OutboxEntry, warnings: List<String>) {
        if (warnings.isEmpty()) {
            dao.delete(entry)
        } else {
            dao.update(
                entry.copy(
                    status = OutboxStatus.UPLOADED,
                    lastError = null,
                    warnings = gson.toJson(warnings),
                )
            )
        }
    }

    /** @return the soft warnings the server sent back, if the target has any */
    private suspend fun send(entry: OutboxEntry): List<String> {
        val create = entry.operation == OutboxOperation.CREATE

        return when (entry.target) {
            OutboxTarget.PARTY -> body<NewParty>(entry).let {
                if (create) parties.uploadCreate(entry.recordId, it)
                else parties.uploadUpdate(entry.recordId, it)
            }

            OutboxTarget.SALES_INVOICE -> body<NewSalesInvoice>(entry).let {
                if (create) salesInvoices.uploadCreate(entry.recordId, it)
                else salesInvoices.uploadUpdate(entry.recordId, it)
            }

            OutboxTarget.PURCHASE_BILL -> body<NewPurchaseBill>(entry).let {
                if (create) purchaseBills.uploadCreate(entry.recordId, it)
                else purchaseBills.uploadUpdate(entry.recordId, it)
            }

            OutboxTarget.RECEIPT -> body<NewReceipt>(entry).let {
                if (create) receipts.uploadCreate(entry.recordId, it)
                else receipts.uploadUpdate(entry.recordId, it)
            }

            OutboxTarget.PAYMENT -> body<NewPayment>(entry).let {
                if (create) payments.uploadCreate(entry.recordId, it)
                else payments.uploadUpdate(entry.recordId, it)
            }

            OutboxTarget.STAFF_WORK -> body<NewStaffWork>(entry).let {
                if (create) staffWork.uploadCreate(entry.recordId, it)
                else staffWork.uploadUpdate(entry.recordId, it)
            }
        }
    }

    private inline fun <reified T> body(entry: OutboxEntry): T =
        gson.fromJson(entry.payload, T::class.java)

    /** Doubling, capped: a phone in a shed must not retry every fifteen seconds all day. */
    private fun backoffFor(attempts: Int): Long =
        minOf(MAX_BACKOFF_MILLIS, FIRST_BACKOFF_MILLIS shl (attempts - 1).coerceIn(0, 20))
}
