package com.wovenledger.app.data.outbox

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * An in-memory stand-in for the outbox table.
 *
 * Every method mirrors what its `@Query` says, ordering included — the drain's whole
 * contract is about order, and a mock returning canned rows in the order the test
 * happened to stub them would prove nothing about it.
 */
class FakeOutboxDao : OutboxDao {

    val rows = mutableListOf<OutboxEntry>()
    private var nextId = 1L

    override suspend fun insert(entry: OutboxEntry): Long {
        val stored = entry.copy(id = nextId++)
        rows += stored

        return stored.id
    }

    override suspend fun update(entry: OutboxEntry) {
        val at = rows.indexOfFirst { it.id == entry.id }
        if (at >= 0) {
            rows[at] = entry
        }
    }

    override suspend fun delete(entry: OutboxEntry) {
        rows.removeAll { it.id == entry.id }
    }

    override fun observeAll(): Flow<List<OutboxEntry>> = flowOf(rows.sortedBy { it.createdAt })

    /** `WHERE status = 'PENDING' ORDER BY created_at ASC, id ASC LIMIT 1`. */
    override suspend fun oldestPending(): OutboxEntry? = rows
        .filter { it.status == OutboxStatus.PENDING }
        .minWithOrNull(compareBy({ it.createdAt }, { it.id }))

    override suspend fun pendingFor(target: OutboxTarget, recordId: Long): OutboxEntry? = rows
        .filter { it.target == target && it.recordId == recordId && it.status == OutboxStatus.PENDING }
        .minWithOrNull(compareBy({ it.createdAt }, { it.id }))

    override suspend fun countPending(): Int = rows.count { it.status == OutboxStatus.PENDING }

    /** `UPDATE outbox SET next_attempt_at = 0 WHERE status = 'PENDING'`. */
    override suspend fun clearBackoff() {
        rows.replaceAll { row ->
            if (row.status == OutboxStatus.PENDING) row.copy(nextAttemptAt = 0L) else row
        }
    }

    override suspend fun find(id: Long): OutboxEntry? = rows.firstOrNull { it.id == id }
}
