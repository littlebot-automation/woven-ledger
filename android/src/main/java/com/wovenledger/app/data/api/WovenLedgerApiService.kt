package com.wovenledger.app.data.api

import com.google.gson.annotations.SerializedName
import retrofit2.http.*

// DTOs for API communication
data class PartyDto(
    @SerializedName("id")
    val id: Int,
    @SerializedName("name")
    val name: String,
    @SerializedName("type")
    val type: String,
    @SerializedName("address")
    val address: String?,
    @SerializedName("phone")
    val phone: String?,
    @SerializedName("email")
    val email: String?,
    @SerializedName("gst_number")
    val gstNumber: String?,
    @SerializedName("ledger_balance")
    val ledgerBalance: Long,
    @SerializedName("created_at")
    val createdAt: String,
    @SerializedName("updated_at")
    val updatedAt: String
)

data class ItemDto(
    @SerializedName("id")
    val id: Int,
    @SerializedName("name")
    val name: String,
    @SerializedName("type")
    val type: String,
    @SerializedName("unit")
    val unit: String,
    @SerializedName("hsn_code")
    val hsnCode: String?,
    @SerializedName("default_rate")
    val defaultRate: Long,
    @SerializedName("gst_rate")
    val gstRate: Double,
    @SerializedName("created_at")
    val createdAt: String?,
    @SerializedName("updated_at")
    val updatedAt: String?
)

data class SalesInvoiceDto(
    @SerializedName("id")
    val id: Int,
    @SerializedName("document_number")
    val documentNumber: String,
    @SerializedName("party_id")
    val partyId: Int,
    @SerializedName("invoice_date")
    val invoiceDate: String,
    @SerializedName("due_date")
    val dueDate: String?,
    @SerializedName("total_amount")
    val totalAmount: Long,
    @SerializedName("discount_amount")
    val discountAmount: Long,
    @SerializedName("gst_amount")
    val gstAmount: Long,
    @SerializedName("net_amount")
    val netAmount: Long,
    @SerializedName("status")
    val status: String,
    @SerializedName("notes")
    val notes: String?,
    @SerializedName("created_at")
    val createdAt: String,
    @SerializedName("updated_at")
    val updatedAt: String
)

data class PurchaseBillDto(
    @SerializedName("id")
    val id: Int,
    @SerializedName("document_number")
    val documentNumber: String,
    @SerializedName("party_id")
    val partyId: Int,
    @SerializedName("bill_date")
    val billDate: String,
    @SerializedName("due_date")
    val dueDate: String?,
    @SerializedName("total_amount")
    val totalAmount: Long,
    @SerializedName("discount_amount")
    val discountAmount: Long,
    @SerializedName("gst_amount")
    val gstAmount: Long,
    @SerializedName("net_amount")
    val netAmount: Long,
    @SerializedName("status")
    val status: String,
    @SerializedName("notes")
    val notes: String?,
    @SerializedName("created_at")
    val createdAt: String,
    @SerializedName("updated_at")
    val updatedAt: String
)

data class PaymentDto(
    @SerializedName("id")
    val id: Int,
    @SerializedName("document_number")
    val documentNumber: String,
    @SerializedName("party_id")
    val partyId: Int?,
    @SerializedName("amount")
    val amount: Long,
    @SerializedName("payment_date")
    val paymentDate: String,
    @SerializedName("payment_type")
    val paymentType: String,
    @SerializedName("reference_number")
    val referenceNumber: String?,
    @SerializedName("mode")
    val mode: String?,
    @SerializedName("notes")
    val notes: String?,
    @SerializedName("created_at")
    val createdAt: String,
    @SerializedName("updated_at")
    val updatedAt: String
)

// Retrofit API Service
interface WovenLedgerApiService {
    @GET("/api/parties")
    suspend fun getParties(): List<PartyDto>

    @GET("/api/parties/{id}")
    suspend fun getParty(@Path("id") id: Int): PartyDto

    @GET("/api/items")
    suspend fun getItems(): List<ItemDto>

    @GET("/api/items/{id}")
    suspend fun getItem(@Path("id") id: Int): ItemDto

    @GET("/api/sales-invoices")
    suspend fun getSalesInvoices(): List<SalesInvoiceDto>

    @GET("/api/sales-invoices/{id}")
    suspend fun getSalesInvoice(@Path("id") id: Int): SalesInvoiceDto

    @GET("/api/purchase-bills")
    suspend fun getPurchaseBills(): List<PurchaseBillDto>

    @GET("/api/purchase-bills/{id}")
    suspend fun getPurchaseBill(@Path("id") id: Int): PurchaseBillDto

    @GET("/api/payments")
    suspend fun getPayments(): List<PaymentDto>

    @GET("/api/payments/{id}")
    suspend fun getPayment(@Path("id") id: Int): PaymentDto
}
