package com.wovenledger.app.data.repository

import com.wovenledger.app.data.api.StockApiService
import com.wovenledger.app.data.dao.PlantDao
import com.wovenledger.app.data.entities.Plant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlantRepository @Inject constructor(
    private val dao: PlantDao,
    private val api: StockApiService
) {

    suspend fun create(plant: Plant): Long = dao.insert(plant)

    fun read(id: Long): Flow<Plant?> = dao.getPlant(id)

    suspend fun update(plant: Plant) {
        dao.update(plant)
    }

    suspend fun delete(plant: Plant) = dao.delete(plant)

    fun getAll(): Flow<List<Plant>> = dao.getAllPlants()

    fun findRecent(): Flow<List<Plant>> = dao.getAllPlants()

    /**
     * Pulls the real plants into Room.
     *
     * Everything else that names a plant — staff, staff work, invoices, bills and the
     * plant-wise stock rows — has a foreign key onto this table, so this has to run
     * before those syncs, not after.
     *
     * Throws on failure so [com.wovenledger.app.data.sync.SyncManager] can report it.
     */
    suspend fun syncFromApi() {
        api.getPlants().forEach { dto ->
            val id = dto.id.toLong()
            val existing = dao.getPlant(id).first()
            val plant = Plant(
                id = id,
                name = dto.name,
                address = dto.address.orEmpty(),
                // A refresh is not a re-creation; the original timestamp stands.
                createdAt = existing?.createdAt ?: java.time.LocalDateTime.now()
            )

            if (existing == null) {
                dao.insert(plant)
            } else {
                dao.update(plant)
            }
        }
    }

    /**
     * Guarantees a plant row exists so records that reference it can insert.
     *
     * Superseded by [syncFromApi] now that /api/plants is live — a synced plant carries
     * its real name and address, where this invents "Plant 1". Kept because callers
     * still reach for it as a pre-flight before inserting a foreign key, and because it
     * is harmless once the real row is present: it only ever fills a gap.
     */
    suspend fun ensureExists(id: Long) {
        if (dao.getPlant(id).first() == null) {
            dao.insert(Plant(id = id, name = "Plant $id", address = ""))
        }
    }
}
