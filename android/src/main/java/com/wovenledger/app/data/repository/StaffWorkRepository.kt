package com.wovenledger.app.data.repository

import com.wovenledger.app.data.api.LabourApiService
import com.wovenledger.app.data.api.NewStaffWork
import com.wovenledger.app.data.api.StaffWorkDto
import com.wovenledger.app.data.dao.StaffDao
import com.wovenledger.app.data.dao.StaffWorkDao
import com.wovenledger.app.data.entities.Plant
import com.wovenledger.app.data.entities.StaffWork
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
    private val plants: PlantRepository
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
     * Records a day's work on the server and mirrors the created row into Room.
     *
     * Writes are online-only by design, so this suspends until the server answers and
     * throws — including [retrofit2.HttpException] on a 422 — rather than queueing.
     * The caller reads the field errors off the 422 body.
     */
    suspend fun createOnServer(entry: NewStaffWork): StaffWork = store(api.createStaffWork(entry))

    /**
     * Corrects an entry the server still lets us touch.
     *
     * A settled entry comes back as a 422 keyed `paid`, because its amount is
     * already inside a wage voucher that would otherwise disagree with it.
     */
    suspend fun updateOnServer(id: Long, entry: NewStaffWork): StaffWork =
        store(api.updateStaffWork(id.toInt(), entry))

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
        val ids = dtos.map { it.id.toLong() }
        if (ids.isEmpty()) dao.deleteAll() else dao.deleteMissing(ids)
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
