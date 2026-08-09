package com.wovenledger.app.data.api

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * Plants and plant-wise stock.
 *
 * These ride their own Retrofit interface rather than [WovenLedgerApiService] so the
 * stock vertical can move without touching the shared one.
 *
 * Unlike every other DTO in this package, the numbers here are **not** paise:
 * quantities are decimals with three places, in the item's own unit. Rendering one
 * through `MoneyText`/`formatMoney` would divide it by 100 and label kilograms as
 * rupees.
 */
data class PlantDto(
    @SerializedName("id")
    val id: Int,
    @SerializedName("name")
    val name: String,
    /** Free text, and frequently blank on the portal. */
    @SerializedName("address")
    val address: String?
)

/** One plant's holding of a single item. */
data class PlantStockDto(
    @SerializedName("plant_id")
    val plantId: Int,
    @SerializedName("plant_name")
    val plantName: String,
    /** A quantity in the item's unit — never money. May be negative: the portal lets
     *  stock go short rather than blocking a sale that already shipped. */
    @SerializedName("qty")
    val qty: Double
)

/**
 * One line of the Stock Report (spec §6.3): an item, a column per plant, a total.
 *
 * [isLow] is the server's verdict under spec §4.3 — low when the quantity *at any one
 * plant* is at or below the threshold — so the phone never has to re-derive a rule
 * that already lives in `StockService`. [lowStockThreshold] is the item's own
 * override and is null when it inherits the company-wide default.
 */
data class StockRowDto(
    @SerializedName("item_id")
    val itemId: Int,
    @SerializedName("item_name")
    val itemName: String,
    /** "raw_material" or "finished_good". */
    @SerializedName("item_type")
    val itemType: String,
    /** kg, pcs, … — the label every quantity on this row is measured in. */
    @SerializedName("unit")
    val unit: String,
    @SerializedName("total_qty")
    val totalQty: Double,
    @SerializedName("low_stock_threshold")
    val lowStockThreshold: Double?,
    @SerializedName("is_low")
    val isLow: Boolean,
    @SerializedName("by_plant")
    val byPlant: List<PlantStockDto> = emptyList()
)

interface StockApiService {
    @GET("/api/plants")
    suspend fun getPlants(): List<PlantDto>

    @GET("/api/plants/{id}")
    suspend fun getPlant(@Path("id") id: Int): PlantDto

    /** Every item, whether or not it has ever been stocked. */
    @GET("/api/stock")
    suspend fun getStock(): List<StockRowDto>

    @GET("/api/stock/{itemId}")
    suspend fun getItemStock(@Path("itemId") itemId: Int): StockRowDto
}
