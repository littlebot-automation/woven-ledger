package com.wovenledger.app.data.repository

import com.wovenledger.app.data.api.LabourApiService
import com.wovenledger.app.data.dao.StaffDao
import com.wovenledger.app.data.entities.Plant
import com.wovenledger.app.data.entities.Staff
import com.wovenledger.app.data.entities.StaffWageType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StaffRepository @Inject constructor(
    private val dao: StaffDao,
    private val api: LabourApiService,
    private val plants: PlantRepository
) {

    suspend fun create(staff: Staff): Long = dao.insert(staff)

    fun read(id: Long): Flow<Staff?> = dao.getStaff(id)

    suspend fun update(staff: Staff) = dao.update(staff)

    suspend fun delete(staff: Staff) = dao.delete(staff)

    fun getAll(): Flow<List<Staff>> = dao.getAllStaff()

    fun getByPlant(plantId: Long): Flow<List<Staff>> = dao.getStaffByPlant(plantId)

    fun findForPlant(plantId: Long): Flow<List<Staff>> = dao.getStaffByPlant(plantId)

    fun findRecent(): Flow<List<Staff>> = dao.getAllStaff()

    /**
     * Pulls /api/staff into Room.
     *
     * Throws on failure so [com.wovenledger.app.data.sync.SyncManager] can report it:
     * an unreachable server and an empty staff list must not look the same on screen.
     */
    suspend fun syncFromApi() {
        val dtos = api.getStaff()

        dtos.forEach { dto ->
            val plantId = (dto.plantId?.toLong() ?: DEFAULT_PLANT_ID).also { ensurePlant(it) }

            val staff = Staff(
                id = dto.id.toLong(),
                name = dto.name,
                role = dto.role.orEmpty(),
                plantId = plantId,
                phone = dto.phone.orEmpty(),
                wageType = wageTypeOf(dto.wageType),
                dailyRate = dto.dailyRate,
                pieceRate = dto.pieceRate
            )

            val existing = dao.getStaff(staff.id).first()
            if (existing == null) {
                dao.insert(staff)
            } else {
                // Keep the row's original createdAt rather than stamping "now" on
                // every sync.
                dao.update(staff.copy(createdAt = existing.createdAt))
            }
        }

        // Upserting alone left staff removed on the portal on the phone for good.
        // Staff are read-only on mobile, so there is never a locally queued one here.
        val ids = dtos.map { it.id.toLong() }
        if (ids.isEmpty()) dao.deleteSynced() else dao.deleteMissing(ids)
    }

    /** The API sends 'daily' / 'piece'; anything unexpected falls back to daily. */
    private fun wageTypeOf(wire: String): StaffWageType = when (wire.uppercase()) {
        StaffWageType.PIECE.name -> StaffWageType.PIECE
        else -> StaffWageType.DAILY
    }

    /**
     * Staff carry a foreign key to a plant and the API does not expose plants yet, so a
     * member at plant 2 would fail to insert with nothing to point at. This holds a
     * placeholder open until /api/plants exists, mirroring what SyncManager does for
     * plant 1.
     */
    private suspend fun ensurePlant(plantId: Long) = plants.ensureExists(plantId)

    private companion object {
        const val DEFAULT_PLANT_ID = 1L
    }
}
