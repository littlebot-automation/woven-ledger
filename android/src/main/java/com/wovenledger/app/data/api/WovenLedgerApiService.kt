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
    /**
     * The figure a party started with, in paise, and which side of the ledger it
     * sits on. Distinct from [ledgerBalance], which is the live total the portal
     * derives; only the opening figure is editable.
     */
    @SerializedName("opening_balance")
    val openingBalance: Long = 0,
    @SerializedName("opening_balance_type")
    val openingBalanceType: String? = null,
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

/**
 * One line of an invoice or a bill.
 *
 * [qty] is a count in the item's own unit, so it stays a decimal; [rate] and
 * [amount] are money and are therefore paise, like everything else on the wire.
 */
data class DocumentLineDto(
    @SerializedName("id")
    val id: Int,
    @SerializedName("item_id")
    val itemId: Int,
    @SerializedName("qty")
    val qty: Double,
    @SerializedName("rate")
    val rate: Long,
    @SerializedName("amount")
    val amount: Long
)

data class SalesInvoiceDto(
    @SerializedName("id")
    val id: Int,
    @SerializedName("document_number")
    val documentNumber: String,
    @SerializedName("party_id")
    val partyId: Int,
    @SerializedName("plant_id")
    val plantId: Int?,
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
    /** A document and its lines travel together; they are never fetched apart. */
    @SerializedName("lines")
    val lines: List<DocumentLineDto> = emptyList(),
    /**
     * Soft stock shortfalls reported alongside a successful save. Never a reason
     * the save failed — the portal does not block a sale and neither does this.
     */
    @SerializedName("warnings")
    val warnings: List<String> = emptyList(),
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
    @SerializedName("plant_id")
    val plantId: Int?,
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
    @SerializedName("lines")
    val lines: List<DocumentLineDto> = emptyList(),
    @SerializedName("warnings")
    val warnings: List<String> = emptyList(),
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
    /** Wage payments carry a staff member where a party payment carries a party. */
    @SerializedName("staff_id")
    val staffId: Int? = null,
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

data class ReceiptDto(
    @SerializedName("id")
    val id: Int,
    @SerializedName("document_number")
    val documentNumber: String,
    @SerializedName("party_id")
    val partyId: Int?,
    @SerializedName("amount")
    val amount: Long,
    @SerializedName("receipt_date")
    val receiptDate: String,
    @SerializedName("mode")
    val mode: String?,
    @SerializedName("notes")
    val notes: String?,
    @SerializedName("created_at")
    val createdAt: String?,
    @SerializedName("updated_at")
    val updatedAt: String?
)

// ---------------------------------------------------------------- Write bodies

/**
 * What the app sends when a form is saved.
 *
 * Server-owned fields are absent by design: no id, no document number, no
 * computed amount. Writes are online-only, so there is nothing to number or
 * total locally — the server answers with the record it stored.
 */
data class NewParty(
    @SerializedName("name")
    val name: String,
    /** "customer", "supplier" or "both". */
    @SerializedName("type")
    val type: String,
    @SerializedName("phone")
    val phone: String?,
    @SerializedName("gst_number")
    val gstNumber: String?,
    @SerializedName("address")
    val address: String?,
    /** Paise. */
    @SerializedName("opening_balance")
    val openingBalance: Long,
    /** "to_receive" or "to_pay". */
    @SerializedName("opening_balance_type")
    val openingBalanceType: String
)

/** A line as written: the amount is the server's to compute. */
data class NewDocumentLine(
    @SerializedName("item_id")
    val itemId: Int,
    @SerializedName("qty")
    val qty: Double,
    /** Paise. Null takes the item's own default rate. */
    @SerializedName("rate")
    val rate: Long?
)

data class NewSalesInvoice(
    @SerializedName("party_id")
    val partyId: Int,
    @SerializedName("plant_id")
    val plantId: Int?,
    /** ISO 'yyyy-MM-dd'. */
    @SerializedName("invoice_date")
    val invoiceDate: String,
    /** Paise. */
    @SerializedName("discount_amount")
    val discountAmount: Long,
    @SerializedName("notes")
    val notes: String?,
    @SerializedName("lines")
    val lines: List<NewDocumentLine>
)

data class NewPurchaseBill(
    @SerializedName("party_id")
    val partyId: Int,
    @SerializedName("plant_id")
    val plantId: Int?,
    @SerializedName("bill_date")
    val billDate: String,
    @SerializedName("discount_amount")
    val discountAmount: Long,
    @SerializedName("notes")
    val notes: String?,
    @SerializedName("lines")
    val lines: List<NewDocumentLine>
)

data class NewReceipt(
    @SerializedName("party_id")
    val partyId: Int,
    /** Paise. */
    @SerializedName("amount")
    val amount: Long,
    @SerializedName("receipt_date")
    val receiptDate: String,
    /** A portal label: "Cash", "Bank Transfer", "UPI" or "Cheque". */
    @SerializedName("mode")
    val mode: String,
    @SerializedName("notes")
    val notes: String?
)

data class NewPayment(
    /** Set for a party payment, null for wages. */
    @SerializedName("party_id")
    val partyId: Int?,
    /** Set for wages, null for a party payment. */
    @SerializedName("staff_id")
    val staffId: Int?,
    @SerializedName("amount")
    val amount: Long,
    @SerializedName("payment_date")
    val paymentDate: String,
    /** "party" or "staff". */
    @SerializedName("payment_type")
    val paymentType: String,
    @SerializedName("mode")
    val mode: String,
    @SerializedName("notes")
    val notes: String?
)

/**
 * Body of a 422: field name to message, e.g. {"errors": {"qty": "Qty is required"}}.
 *
 * The keys are the server's field names, which is why every form keys its own
 * error map the same way — a rename on one side would otherwise silently stop
 * showing the message.
 */
data class ApiErrors(
    @SerializedName("errors")
    val errors: Map<String, String> = emptyMap()
)

// Retrofit API Service
interface WovenLedgerApiService {
    @GET("/api/parties")
    suspend fun getParties(): List<PartyDto>

    @GET("/api/parties/{id}")
    suspend fun getParty(@Path("id") id: Int): PartyDto

    /** 201 with the created party, or 422 with a per-field error map. */
    @POST("/api/parties")
    suspend fun createParty(@Body party: NewParty): PartyDto

    @PUT("/api/parties/{id}")
    suspend fun updateParty(@Path("id") id: Int, @Body party: NewParty): PartyDto

    @GET("/api/items")
    suspend fun getItems(): List<ItemDto>

    @GET("/api/items/{id}")
    suspend fun getItem(@Path("id") id: Int): ItemDto

    @GET("/api/sales-invoices")
    suspend fun getSalesInvoices(): List<SalesInvoiceDto>

    @GET("/api/sales-invoices/{id}")
    suspend fun getSalesInvoice(@Path("id") id: Int): SalesInvoiceDto

    @POST("/api/sales-invoices")
    suspend fun createSalesInvoice(@Body invoice: NewSalesInvoice): SalesInvoiceDto

    @PUT("/api/sales-invoices/{id}")
    suspend fun updateSalesInvoice(
        @Path("id") id: Int,
        @Body invoice: NewSalesInvoice
    ): SalesInvoiceDto

    @GET("/api/purchase-bills")
    suspend fun getPurchaseBills(): List<PurchaseBillDto>

    @GET("/api/purchase-bills/{id}")
    suspend fun getPurchaseBill(@Path("id") id: Int): PurchaseBillDto

    @POST("/api/purchase-bills")
    suspend fun createPurchaseBill(@Body bill: NewPurchaseBill): PurchaseBillDto

    @PUT("/api/purchase-bills/{id}")
    suspend fun updatePurchaseBill(
        @Path("id") id: Int,
        @Body bill: NewPurchaseBill
    ): PurchaseBillDto

    @GET("/api/payments")
    suspend fun getPayments(): List<PaymentDto>

    @GET("/api/payments/{id}")
    suspend fun getPayment(@Path("id") id: Int): PaymentDto

    @POST("/api/payments")
    suspend fun createPayment(@Body payment: NewPayment): PaymentDto

    @PUT("/api/payments/{id}")
    suspend fun updatePayment(@Path("id") id: Int, @Body payment: NewPayment): PaymentDto

    @GET("/api/receipts")
    suspend fun getReceipts(): List<ReceiptDto>

    @GET("/api/receipts/{id}")
    suspend fun getReceipt(@Path("id") id: Int): ReceiptDto

    @POST("/api/receipts")
    suspend fun createReceipt(@Body receipt: NewReceipt): ReceiptDto

    @PUT("/api/receipts/{id}")
    suspend fun updateReceipt(@Path("id") id: Int, @Body receipt: NewReceipt): ReceiptDto
}
