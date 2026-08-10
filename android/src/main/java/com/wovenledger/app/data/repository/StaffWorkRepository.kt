package com.wovenledger.app.data.repository

import com.wovenledger.app.data.api.LabourApiService
import com.wovenledger.app.data.api.NewStaffWork
import com.wovenledger.app.data.api.StaffWorkDto
import com.wovenledger.app.data.dao.StaffDao
import com.wovenledger.app.data.dao.StaffWorkDao
import com.wovenledger.app.data.entities.Plant
import com.wovenledger.app.data.entities.StaffWork
import com.wovenledger.app.data.outbox.Outbox
import com.wovenledger.app.data.outbox.OutboxOperation
import com.wovenledger.app.data.outbox.OutboxTarget
import com.wovenledger.app.data.outbox.attemptOnline
import com.wovenledger.app.data.outbox.nextLocalId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import java.time.LocalDate

@Singleton
class StaffWorkRepository @Inject constructor(
    private val dao: StaffWorkDao,
    private val staffDao: StaffDao,
    private val api: LabourApiService,
    private val plants: PlantRepository,
    private val outbox: Outbox
) {

    suspend fun create(work: StaffWork): Long = dao.insert(work)

    fun read(id: Long): Flow<StaffWork?> = dao.getWork(id)

    suspend fun update(work: StaffWork) = dao.update(work)

    suspend fun delete(work: StaffWork) = dao.delete(work)

    fun getByStaff(staffId: Long): Flow<List<StaffWork>> = dao.getWorkByStaff(staffId)

    /**
     * Every entry, newest first.
     *
     * StaffWorkDao only exposes work per staff member and unpaid work, so this stitches
     * the per-staff flows together rather than adding a query to a DAO shared with the
     * rest of the app.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun getAll(): Flow<List<StaffWork>> = dao.getAllWork()

    fun getUnpaidWork(): Flow<List<StaffWork>> = dao.getUnpaidWork()

    fun findUnpaidFor(staffId: Long): Flow<List<StaffWork>> = dao.getWorkByStaff(staffId).map { works ->
        works.filter { !it.paid }
    }

    fun sumAmountForStaff(staffId: Long): Flow<Long> = dao.getWorkByStaff(staffId).map { works ->
        works.sumOf { it.getAmount() }
    }

    fun sumUnpaidForStaff(staffId: Long): Flow<Long> = dao.getWorkByStaff(staffId).map { works ->
        works.filter { !it.paid }.sumOf { it.getAmount() }
    }

    fun sumUnpaidAmount(): Flow<Long> = dao.getUnpaidWork().map { works ->
        works.sumOf { it.getAmount() }
    }

    fun findRecent(): Flow<List<StaffWork>> = dao.getUnpaidWork()

    /**
     * Records a day's work on the server and mirrors the created row into Room;
     * queues it if the phone cannot reach the server.
     *
     * This is the write most likely to happen with no signal — it is made on the shop
     * floor, at the loom, at the end of a shift — so being refused for want of a
     * connection was the least useful thing it could do. A 422 still surfaces
     * immediately; only a connectivity failure is queued.
     */
    suspend fun createOnServer(entry: NewStaffWork): SavedDocument<StaffWork> {
        val dto = attemptOnline { api.createStaffWork(entry) } ?: return queueCreate(entry)

        return SavedDocument(store(dto))
    }

    /**
     * Corrects an entry the server still lets us touch.
     *
     * A settled entry comes back as a 422 keyed `paid`, because its amount is
     * already inside a wage voucher that would otherwise disagree with it. That
     * refusal is a decision, not a connection problem, so it is never queued — the
     * queue would retry it forever while telling the user the correction was saved.
     */
    suspend fun updateOnServer(id: Long, entry: NewStaffWork): SavedDocument<StaffWork> {
        // A document that has not uploaded has no server id to PUT to: editing it
        // rewrites the write already queued for it instead of asking the server about
        // a record it has never seen.
        if (id < 0) {
            return queueUpdate(id, entry)
        }

        val dto = attemptOnline { api.updateStaffWork(id.toInt(), entry) }
            ?: return queueUpdate(id, entry)

        return SavedDocument(store(dto))
    }

    private suspend fun queueCreate(body: NewStaffWork): SavedDocument<StaffWork> {
        val localId = nextLocalId(dao.minId())
        val echo = echoOf(localId, body)

        ensurePlant(echo.plantId)
        dao.insert(echo)
        outbox.enqueue(OutboxTarget.STAFF_WORK, OutboxOperation.CREATE, localId, body)

        return SavedDocument(echo, queued = true)
    }

    private suspend fun queueUpdate(id: Long, body: NewStaffWork): SavedDocument<StaffWork> {
        // Whether the entry is settled, and which voucher settled it, are the server's
        // to say; an offline correction must not claim either way.
        val stored = dao.getWork(id).first()
        val edited = echoOf(id, body).copy(
            paid = stored?.paid ?: false,
            paymentVoucherId = stored?.paymentVoucherId,
        )

        ensurePlant(edited.plantId)
        dao.insert(edited)
        outbox.enqueue(OutboxTarget.STAFF_WORK, OutboxOperation.UPDATE, id, body)

        return SavedDocument(edited, queued = true)
    }

    /**
     * Removes a locally written work entry that is never going to be uploaded.
     *
     * Only the outbox calls this, when the user abandons a queued write.
     */
    suspend fun deleteLocal(localId: Long) = dao.deleteById(localId)

    /** Sends a queued create, then swaps the local echo for what the server stored. */
    suspend fun uploadCreate(localId: Long, body: NewStaffWork): List<String> {
        val dto = api.createStaffWork(body)

        dao.deleteById(localId)
        store(dto)

        return emptyList()
    }

    suspend fun uploadUpdate(id: Long, body: NewStaffWork): List<String> {
        store(api.updateStaffWork(id.toInt(), body))

        return emptyList()
    }

    /**
     * What a queued work entry looks like in the app while it waits.
     *
     * A blank rate means "use the staff member's own", which only the server can
     * resolve, so it shows as zero here and corrects itself on upload.
     */
    private fun echoOf(id: Long, body: NewStaffWork) = StaffWork(
        id = id,
        date = LocalDate.parse(body.date),
        staffId = body.staffId.toLong(),
        plantId = body.plantId?.toLong() ?: DEFAULT_PLANT_ID,
        workType = body.workType,
        qty = body.qty,
        rate = body.rate ?: 0L,
        paid = false,
    )

    private suspend fun store(dto: StaffWorkDto): StaffWork {
        val work = toEntity(dto)
        ensurePlant(work.plantId)
        dao.insert(work)

        return work
    }

    /**
     * Pulls /api/staff-work into Room.
     *
     * Throws on failure so [com.wovenledger.app.data.sync.SyncManager] can report it.
     * Staff must be synced first: each entry points at a staff row.
     */
    suspend fun syncFromApi() {
        val dtos = api.getStaffWork()

        dtos.forEach { dto ->
            val work = toEntity(dto)
            ensurePlant(work.plantId)

            val existing = dao.getWork(work.id).first()
            if (existing == null) {
                dao.insert(work)
            } else {
                dao.update(work)
            }
        }

        // Upserting alone left entries deleted on the portal on the phone for good —
        // the symptom that started this: a work row removed on the server stayed
        // visible in the app until the database was wiped.
        //
        // Entries recorded here and not uploaded yet are kept: they are missing from
        // the server's list because it has never been told about them, and pruning
        // them would throw away a shift nobody else has a record of.
        val keep = dtos.map { it.id.toLong() } + dao.pendingIds()
        if (keep.isEmpty()) dao.deleteSynced() else dao.deleteMissing(keep)
    }

    /**
     * The server sends the amount as well, but Room derives it from qty × rate, so it
     * is read back rather than stored — one number, one home.
     */
    private fun toEntity(dto: StaffWorkDto) = StaffWork(
        id = dto.id.toLong(),
        date = LocalDate.parse(dto.date),
        staffId = dto.staffId.toLong(),
        plantId = dto.plantId?.toLong() ?: DEFAULT_PLANT_ID,
        workType = dto.workType,
        qty = dto.qty,
        rate = dto.rate,
        paid = dto.paid,
        paymentVoucherId = dto.paymentVoucherId?.toLong()
    )

    private suspend fun ensurePlant(plantId: Long) = plants.ensureExists(plantId)

    private companion object {
        const val DEFAULT_PLANT_ID = 1L
    }
}
