package com.wovenledger.app.data.repository

import com.wovenledger.app.data.api.WovenLedgerApiService
import com.wovenledger.app.data.dao.ItemDao
import com.wovenledger.app.data.entities.Item
import com.wovenledger.app.data.entities.ItemType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ItemRepository @Inject constructor(
    private val dao: ItemDao,
    private val api: WovenLedgerApiService
) {

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

    /** Throws on failure so [com.wovenledger.app.data.sync.SyncManager] can report it. */
    suspend fun syncFromApi() {
        val apiItems = api.getItems()
        apiItems.forEach { dto ->
            val item = Item(
                id = dto.id.toLong(),
                name = dto.name,
                type = ItemType.valueOf(dto.type.uppercase()),
                unit = dto.unit,
                defaultRate = dto.defaultRate, // already paise
                hsn = dto.hsnCode ?: ""
            )
            val existing = dao.getItem(item.id).first()
            if (existing == null) {
                dao.insert(item)
            } else {
                dao.update(item)
            }
        }

        // Upserting alone left items deleted on the portal on the phone for good.
        // Items are read-only on mobile, so there is never a locally queued one here.
        val ids = apiItems.map { it.id.toLong() }
        if (ids.isEmpty()) dao.deleteSynced() else dao.deleteMissing(ids)
    }
}
