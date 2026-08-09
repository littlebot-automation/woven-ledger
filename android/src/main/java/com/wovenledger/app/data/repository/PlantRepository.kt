package com.wovenledger.app.data.repository

import com.wovenledger.app.data.dao.PlantDao
import com.wovenledger.app.data.entities.Plant
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
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
}
