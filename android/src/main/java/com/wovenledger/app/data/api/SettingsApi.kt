package com.wovenledger.app.data.api

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET

/**
 * Company settings, served as a single object by /api/settings.
 *
 * The Room `Settings` entity has no columns for the document-number prefixes, so those
 * live only here. That is why this screen reads the API rather than the local cache.
 */
data class SettingsDto(
    @SerializedName("company_name")
    val companyName: String?,
    @SerializedName("address")
    val address: String?,
    @SerializedName("gstin")
    val gstin: String?,
    @SerializedName("phone")
    val phone: String?,
    @SerializedName("invoice_prefix")
    val invoicePrefix: String?,
    @SerializedName("purchase_prefix")
    val purchasePrefix: String?,
    @SerializedName("receipt_prefix")
    val receiptPrefix: String?,
    @SerializedName("payment_prefix")
    val paymentPrefix: String?,
    @SerializedName("next_invoice_no")
    val nextInvoiceNo: Long?,
    @SerializedName("next_purchase_no")
    val nextPurchaseNo: Long?,
    @SerializedName("next_receipt_no")
    val nextReceiptNo: Long?,
    @SerializedName("next_payment_no")
    val nextPaymentNo: Long?,
    @SerializedName("default_wage")
    val defaultWage: Long?,
    @SerializedName("current_plant_id")
    val currentPlantId: Long?
)

interface SettingsApiService {
    @GET("/api/settings")
    suspend fun getSettings(): SettingsDto
}
