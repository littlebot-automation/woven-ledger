package com.wovenledger.app.data.outbox

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One write that has not reached the server yet.
 *
 * Writes used to be online-only: a save posted and waited, and with no signal nothing
 * was recorded at all. That kept document numbers and stock provably correct, because
 * both are the server's to decide — and it still does. What changed is only *when* the
 * server is asked: a save with no connection is written here and sent later, instead of
 * being refused.
 *
 * The invariant the old design protected is untouched. Nothing in this table carries a
 * document number, because `SI-0015` is allocated inside a transaction from a counter in
 * Settings, and `no` is unique on the document tables. Two phones offline would hand out
 * the same number and the second upload would die on a constraint violation nobody could
 * read. So a queued document has no number until it uploads, and the UI says exactly
 * that rather than inventing one.
 */
@Entity(
    tableName = "outbox",
    indices = [Index(value = ["target", "record_id"])],
)
data class OutboxEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** Which endpoint this payload belongs to. */
    val target: OutboxTarget,
    val operation: OutboxOperation,
    /** The request body as JSON — exactly what would have been posted. */
    val payload: String,
    /**
     * The Room row this write shows up as while it waits.
     *
     * Negative for a queued create, because the local echo is inserted under a
     * locally-allocated negative id that no server id can ever collide with. Positive
     * for an update, where it is the server's own id and also the path of the PUT.
     */
    @ColumnInfo(name = "record_id")
    val recordId: Long,
    /** Epoch millis. Drain order, so a create is never overtaken by its own edit. */
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    val attempts: Int = 0,
    @ColumnInfo(name = "last_error")
    val lastError: String? = null,
    /**
     * Epoch millis before which this must not be retried, so a server that is up but
     * unhappy is not hammered. Zero means "now".
     */
    @ColumnInfo(name = "next_attempt_at")
    val nextAttemptAt: Long = 0,
    val status: OutboxStatus = OutboxStatus.PENDING,
    /**
     * Soft stock warnings the server returned when this finally uploaded, held as a
     * JSON array until the user has seen them.
     *
     * A queued sale cannot be checked against stock while it waits — stock is the
     * server's figure — so its warnings arrive long after the form that would have
     * shown them has gone. Dropping them there would make a shortfall invisible.
     */
    val warnings: String? = null,
)

/** Which of the six writable objects a queued payload belongs to. */
enum class OutboxTarget(val label: String) {
    PARTY("Party"),
    SALES_INVOICE("Sales invoice"),
    PURCHASE_BILL("Purchase bill"),
    RECEIPT("Receipt"),
    PAYMENT("Payment"),
    STAFF_WORK("Work entry"),
}

enum class OutboxOperation { CREATE, UPDATE }

enum class OutboxStatus {
    /** Waiting to be sent. */
    PENDING,

    /** Given up on, or refused by the server. Shown to the user; never retried on its own. */
    FAILED,

    /** Sent successfully, but carrying warnings the user has not read yet. */
    UPLOADED,
}

/**
 * The next free local id for a table whose lowest id is [lowest].
 *
 * Local ids run negative and downwards. Server ids are always positive, so the two
 * ranges can never meet however long the phone stays offline — which is the whole
 * point: a queued row has to be visible immediately, and it must not squat on an id
 * the server will hand out to something else.
 */
fun nextLocalId(lowest: Long?): Long = if (lowest == null || lowest >= 0L) -1L else lowest - 1L
