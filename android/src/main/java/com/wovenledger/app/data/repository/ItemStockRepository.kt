package com.wovenledger.app.data.repository

import com.wovenledger.app.data.dao.ItemStockDao
import com.wovenledger.app.data.entities.ItemStock
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ItemStockRepository @Inject constructor(private val dao: ItemStockDao) {

    suspend fun create(stock: ItemStock): Long = dao.insert(stock)

    fun read(itemId: Long, plantId: Long): Flow<ItemStock?> = dao.getStock(itemId, plantId)

    suspend fun update(stock: ItemStock) = dao.update(stock)

    suspend fun delete(stock: ItemStock) = dao.delete(stock)

    fun getByPlant(plantId: Long): Flow<List<ItemStock>> = dao.getStockByPlant(plantId)

    fun getStock(itemId: Long, plantId: Long): Flow<ItemStock?> = dao.getStock(itemId, plantId)

    fun hasEntriesFor(plantId: Long): Flow<List<ItemStock>> = dao.getStockByPlant(plantId)
}
