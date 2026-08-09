package com.wovenledger.app.data.repository

import com.wovenledger.app.data.api.StockApiService
import com.wovenledger.app.data.api.StockRowDto
import com.wovenledger.app.data.dao.ItemStockDao
import com.wovenledger.app.data.entities.ItemStock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ItemStockRepository @Inject constructor(
    private val dao: ItemStockDao,
    private val api: StockApiService
) {

    suspend fun create(stock: ItemStock): Long = dao.insert(stock)

    fun read(itemId: Long, plantId: Long): Flow<ItemStock?> = dao.getStock(itemId, plantId)

    suspend fun update(stock: ItemStock) = dao.update(stock)

    suspend fun delete(stock: ItemStock) = dao.delete(stock)

    fun getByPlant(plantId: Long): Flow<List<ItemStock>> = dao.getStockByPlant(plantId)

    fun getStock(itemId: Long, plantId: Long): Flow<ItemStock?> = dao.getStock(itemId, plantId)

    fun hasEntriesFor(plantId: Long): Flow<List<ItemStock>> = dao.getStockByPlant(plantId)

    /**
     * The server's stock report, row per item, without touching Room.
     *
     * The report carries figures Room cannot reproduce on its own — the low-stock
     * verdict and the plant names — so screens read it directly rather than
     * re-deriving spec §4.3 on the phone. Throws so the caller can say the server
     * was unreachable instead of showing an empty table.
     */
    suspend fun fetchReport(): List<StockRowDto> = api.getStock()

    /** One item's report row. Throws; a 404 surfaces as an HttpException. */
    suspend fun fetchReportFor(itemId: Long): StockRowDto = api.getItemStock(itemId.toInt())

    /**
     * Pulls every plant-wise quantity into Room.
     *
     * Throws on failure so [com.wovenledger.app.data.sync.SyncManager] can report it.
     *
     * `item_stocks` has foreign keys onto both `items` and `plants`, so items and
     * plants must already have synced or these inserts are rejected.
     */
    suspend fun syncFromApi() {
        api.getStock().forEach { row ->
            row.byPlant.forEach { plantQty ->
                val itemId = row.itemId.toLong()
                val plantId = plantQty.plantId.toLong()
                val existing = dao.getStock(itemId, plantId).first()

                if (existing == null) {
                    dao.insert(ItemStock(itemId = itemId, plantId = plantId, qty = plantQty.qty))
                } else {
                    // Keeping the local row id matters: replacing it would churn the
                    // primary key of a row nothing else references, for no gain.
                    dao.update(existing.copy(qty = plantQty.qty, updatedAt = LocalDateTime.now()))
                }
            }
        }
    }
}
