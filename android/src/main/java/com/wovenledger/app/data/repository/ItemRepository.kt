package com.wovenledger.app.data.repository

import com.wovenledger.app.data.dao.ItemDao
import com.wovenledger.app.data.entities.Item
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ItemRepository @Inject constructor(private val dao: ItemDao) {

    suspend fun create(item: Item): Long = dao.insert(item)

    fun read(id: Long): Flow<Item?> = dao.getItem(id)

    suspend fun update(item: Item) = dao.update(item)

    suspend fun delete(item: Item) = dao.delete(item)

    fun getAll(): Flow<List<Item>> = dao.getAllItems()

    fun getByType(type: String): Flow<List<Item>> = dao.getItemsByType(type)

    fun search(q: String): Flow<List<Item>> = dao.getAllItems().map { items ->
        items.filter { item ->
            item.name.contains(q, ignoreCase = true) ||
            item.hsn.contains(q, ignoreCase = true) ||
            item.unit.contains(q, ignoreCase = true)
        }
    }

    fun findRecent(): Flow<List<Item>> = dao.getAllItems()
}
