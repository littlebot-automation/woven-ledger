package com.wovenledger.app.data.repository

import com.wovenledger.app.data.dao.PlantDao
import com.wovenledger.app.data.entities.Plant
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlantRepository @Inject constructor(private val dao: PlantDao) {

    suspend fun create(plant: Plant): Long = dao.insert(plant)

    fun read(id: Long): Flow<Plant?> = dao.getPlant(id)

    suspend fun update(plant: Plant) {
        dao.update(plant)
    }

    suspend fun delete(plant: Plant) = dao.delete(plant)

    fun getAll(): Flow<List<Plant>> = dao.getAllPlants()

    fun findRecent(): Flow<List<Plant>> = dao.getAllPlants()

    /**
     * Guarantees a plant row exists so records that reference it can insert.
     *
     * Staff, staff work, sales invoices and purchase bills all carry a foreign key to
     * a plant, and the API does not expose plants yet, so without this their inserts
     * fail. Remove every caller once /api/plants lands — this is scaffolding, not a
     * feature.
     */
    suspend fun ensureExists(id: Long) {
        if (dao.getPlant(id).first() == null) {
            dao.insert(Plant(id = id, name = "Plant $id", address = ""))
        }
    }
}
