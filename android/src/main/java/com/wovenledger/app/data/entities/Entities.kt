package com.wovenledger.app.data.entities

import androidx.room.*
import com.wovenledger.app.data.outbox.OutboxOperation
import com.wovenledger.app.data.outbox.OutboxStatus
import com.wovenledger.app.data.outbox.OutboxTarget
import java.time.LocalDate
import java.time.LocalDateTime

// ===================== ENUM CLASSES =====================

enum class PartyType {
    CUSTOMER, SUPPLIER, BOTH
}

enum class StaffWageType {
    DAILY, PIECE
}

enum class PaymentMode {
    CASH, BANK_TRANSFER, UPI, CHEQUE
}

enum class PaymentType {
    PARTY, STAFF
}

enum class ItemType {
    RAW_MATERIAL, FINISHED_GOOD
}

enum class BalanceType {
    TO_RECEIVE, TO_PAY
}

/**
 * Money derived from a quantity, rounded to the nearest paise.
 *
 * Truncating loses up to a paise on every line and compounds across a document, and
 * the server rounds rather than truncates — so a truncating client would disagree
 * with the portal about the same record. Quantities and rates are non-negative here,
 * where rounding half-up and PHP's round() agree.
 */
internal fun amountOf(qty: Double, rate: Long): Long = Math.round(qty * rate)

// ===================== ENTITIES =====================

@Entity(tableName = "plants")
data class Plant(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val address: String,
    @ColumnInfo(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now()
)

@Entity(tableName = "settings")
data class Settings(
    @PrimaryKey
    val id: Int = 1,
    val companyName: String = "",
    val address: String = "",
    val phone: String = "",
    val gstin: String = "",
    @ColumnInfo(name = "low_stock_default")
    val lowStockDefault: Int = 50,
    @ColumnInfo(name = "default_wage")
    val defaultWage: Long = 50000, // paise (500.00)
    @ColumnInfo(name = "current_plant_id")
    val currentPlantId: Long = 1,
    @ColumnInfo(name = "next_invoice_no")
    val nextInvoiceNo: Long = 1,
    @ColumnInfo(name = "next_purchase_no")
    val nextPurchaseNo: Long = 1,
    @ColumnInfo(name = "next_receipt_no")
    val nextReceiptNo: Long = 1,
    @ColumnInfo(name = "next_payment_no")
    val nextPaymentNo: Long = 1
)

@Entity(tableName = "parties")
data class Party(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val type: PartyType,
    val phone: String = "",
    val gstin: String? = null,
    val address: String = "",
    @ColumnInfo(name = "opening_balance")
    val openingBalance: Long = 0, // paise
    @ColumnInfo(name = "opening_balance_type")
    val openingBalanceType: BalanceType = BalanceType.TO_RECEIVE,
    /**
     * What the party owes right now, in paise, signed: positive means they owe us.
     *
     * Derived by the portal from every document on their ledger, so it is cached
     * here rather than computed — and kept apart from [openingBalance], which is
     * the only one of the two an edit form may rewrite.
     */
    @ColumnInfo(name = "ledger_balance")
    val ledgerBalance: Long = 0,
    @ColumnInfo(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now()
)

@Entity(tableName = "items")
data class Item(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val type: ItemType,
    val unit: String, // kg, pcs, etc.
    @ColumnInfo(name = "default_rate")
    val defaultRate: Long, // paise
    val hsn: String = "",
    @ColumnInfo(name = "low_stock_threshold")
    val lowStockThreshold: Int? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now()
)

@Entity(
    tableName = "item_stocks",
    indices = [Index("item_id", "plant_id", unique = true)],
    foreignKeys = [
        ForeignKey(
            entity = Item::class,
            parentColumns = ["id"],
            childColumns = ["item_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Plant::class,
            parentColumns = ["id"],
            childColumns = ["plant_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ]
)
data class ItemStock(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "item_id")
    val itemId: Long,
    @ColumnInfo(name = "plant_id")
    val plantId: Long,
    val qty: Double = 0.0,
    @ColumnInfo(name = "updated_at")
    val updatedAt: LocalDateTime = LocalDateTime.now()
)

@Entity(
    tableName = "staff",
    foreignKeys = [
        ForeignKey(
            entity = Plant::class,
            parentColumns = ["id"],
            childColumns = ["plant_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ]
)
data class Staff(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val role: String,
    @ColumnInfo(name = "plant_id")
    val plantId: Long,
    val phone: String = "",
    @ColumnInfo(name = "wage_type")
    val wageType: StaffWageType = StaffWageType.DAILY,
    @ColumnInfo(name = "daily_rate")
    val dailyRate: Long = 0, // paise
    @ColumnInfo(name = "piece_rate")
    val pieceRate: Long = 0, // paise
    @ColumnInfo(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now()
) {
    fun getEffectiveRate(): Long = if (wageType == StaffWageType.DAILY) dailyRate else pieceRate
}

@Entity(
    tableName = "staff_work",
    foreignKeys = [
        ForeignKey(
            entity = Staff::class,
            parentColumns = ["id"],
            childColumns = ["staff_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Plant::class,
            parentColumns = ["id"],
            childColumns = ["plant_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ]
)
data class StaffWork(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: LocalDate,
    @ColumnInfo(name = "staff_id")
    val staffId: Long,
    @ColumnInfo(name = "plant_id")
    val plantId: Long,
    @ColumnInfo(name = "work_type")
    val workType: String,
    val qty: Double,
    val rate: Long, // paise
    val paid: Boolean = false,
    @ColumnInfo(name = "payment_voucher_id")
    val paymentVoucherId: Long? = null
) {
    fun getAmount(): Long = amountOf(qty, rate)
}

@Entity(
    tableName = "sales_invoices",
    foreignKeys = [
        ForeignKey(
            entity = Party::class,
            parentColumns = ["id"],
            childColumns = ["party_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Plant::class,
            parentColumns = ["id"],
            childColumns = ["plant_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ]
)
data class SalesInvoice(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val no: String,
    val date: LocalDate,
    @ColumnInfo(name = "party_id")
    val partyId: Long,
    @ColumnInfo(name = "plant_id")
    val plantId: Long,
    val subtotal: Long = 0, // paise
    val discount: Long = 0, // paise
    val total: Long = 0, // paise
    @ColumnInfo(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now()
)

@Entity(
    tableName = "sales_invoice_lines",
    foreignKeys = [
        ForeignKey(
            entity = SalesInvoice::class,
            parentColumns = ["id"],
            childColumns = ["invoice_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Item::class,
            parentColumns = ["id"],
            childColumns = ["item_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ]
)
data class SalesInvoiceLine(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "invoice_id")
    val invoiceId: Long,
    @ColumnInfo(name = "item_id")
    val itemId: Long,
    val qty: Double,
    val rate: Long, // paise
    val amount: Long = amountOf(qty, rate) // paise
)

@Entity(
    tableName = "purchase_bills",
    foreignKeys = [
        ForeignKey(
            entity = Party::class,
            parentColumns = ["id"],
            childColumns = ["party_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Plant::class,
            parentColumns = ["id"],
            childColumns = ["plant_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ]
)
data class PurchaseBill(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val no: String,
    val date: LocalDate,
    @ColumnInfo(name = "party_id")
    val partyId: Long,
    @ColumnInfo(name = "plant_id")
    val plantId: Long,
    val subtotal: Long = 0, // paise
    val discount: Long = 0, // paise
    val total: Long = 0, // paise
    @ColumnInfo(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now()
)

@Entity(
    tableName = "purchase_bill_lines",
    foreignKeys = [
        ForeignKey(
            entity = PurchaseBill::class,
            parentColumns = ["id"],
            childColumns = ["bill_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Item::class,
            parentColumns = ["id"],
            childColumns = ["item_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ]
)
data class PurchaseBillLine(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "bill_id")
    val billId: Long,
    @ColumnInfo(name = "item_id")
    val itemId: Long,
    val qty: Double,
    val rate: Long, // paise
    val amount: Long = amountOf(qty, rate) // paise
)

@Entity(
    tableName = "receipts",
    foreignKeys = [
        ForeignKey(
            entity = Party::class,
            parentColumns = ["id"],
            childColumns = ["party_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ]
)
data class Receipt(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val no: String,
    val date: LocalDate,
    @ColumnInfo(name = "party_id")
    val partyId: Long,
    val amount: Long, // paise
    val mode: PaymentMode,
    val notes: String = "",
    @ColumnInfo(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now()
)

@Entity(
    tableName = "payments",
    foreignKeys = [
        ForeignKey(
            entity = Party::class,
            parentColumns = ["id"],
            childColumns = ["party_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Staff::class,
            parentColumns = ["id"],
            childColumns = ["staff_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ]
)
data class Payment(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val no: String,
    val date: LocalDate,
    val type: PaymentType,
    @ColumnInfo(name = "party_id")
    val partyId: Long? = null,
    @ColumnInfo(name = "staff_id")
    val staffId: Long? = null,
    val amount: Long, // paise
    val mode: PaymentMode,
    val notes: String = "",
    @ColumnInfo(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now()
)

// ===================== RELATION DATA CLASSES =====================

data class SalesInvoiceWithLines(
    @Embedded
    val salesInvoice: SalesInvoice,
    @Relation(
        parentColumn = "id",
        entityColumn = "invoice_id"
    )
    val lines: List<SalesInvoiceLine>
)

data class PurchaseBillWithLines(
    @Embedded
    val purchaseBill: PurchaseBill,
    @Relation(
        parentColumn = "id",
        entityColumn = "bill_id"
    )
    val lines: List<PurchaseBillLine>
)

data class ItemWithStock(
    @Embedded
    val item: Item,
    @Relation(
        parentColumn = "id",
        entityColumn = "item_id"
    )
    val stocks: List<ItemStock>
)

data class PartyWithTransactions(
    @Embedded
    val party: Party,
    @Relation(
        parentColumn = "id",
        entityColumn = "party_id"
    )
    val salesInvoices: List<SalesInvoice>,
    @Relation(
        parentColumn = "id",
        entityColumn = "party_id"
    )
    val purchaseBills: List<PurchaseBill>,
    @Relation(
        parentColumn = "id",
        entityColumn = "party_id"
    )
    val receipts: List<Receipt>,
    @Relation(
        parentColumn = "id",
        entityColumn = "party_id"
    )
    val payments: List<Payment>
)

data class StaffWithWork(
    @Embedded
    val staff: Staff,
    @Relation(
        parentColumn = "id",
        entityColumn = "staff_id"
    )
    val workEntries: List<StaffWork>
)

// ===================== TYPE CONVERTERS =====================

@TypeConverters
object Converters {
    @TypeConverter
    fun fromLocalDate(date: LocalDate?): String? = date?.toString()

    @TypeConverter
    fun toLocalDate(dateString: String?): LocalDate? =
        dateString?.let { LocalDate.parse(it) }

    @TypeConverter
    fun fromLocalDateTime(dateTime: LocalDateTime?): String? = dateTime?.toString()

    @TypeConverter
    fun toLocalDateTime(dateTimeString: String?): LocalDateTime? =
        dateTimeString?.let { LocalDateTime.parse(it) }

    @TypeConverter
    fun fromPartyType(value: PartyType?): String? = value?.name

    @TypeConverter
    fun toPartyType(value: String?): PartyType? =
        value?.let { PartyType.valueOf(it) }

    @TypeConverter
    fun fromStaffWageType(value: StaffWageType?): String? = value?.name

    @TypeConverter
    fun toStaffWageType(value: String?): StaffWageType? =
        value?.let { StaffWageType.valueOf(it) }

    @TypeConverter
    fun fromPaymentMode(value: PaymentMode?): String? = value?.name

    @TypeConverter
    fun toPaymentMode(value: String?): PaymentMode? =
        value?.let { PaymentMode.valueOf(it) }

    @TypeConverter
    fun fromPaymentType(value: PaymentType?): String? = value?.name

    @TypeConverter
    fun toPaymentType(value: String?): PaymentType? =
        value?.let { PaymentType.valueOf(it) }

    @TypeConverter
    fun fromItemType(value: ItemType?): String? = value?.name

    @TypeConverter
    fun toItemType(value: String?): ItemType? =
        value?.let { ItemType.valueOf(it) }

    @TypeConverter
    fun fromBalanceType(value: BalanceType?): String? = value?.name

    @TypeConverter
    fun toBalanceType(value: String?): BalanceType? =
        value?.let { BalanceType.valueOf(it) }

    // The outbox stores its three enums as their own names, so the queue can be read
    // with a plain SQL client while diagnosing a phone that will not upload.

    @TypeConverter
    fun fromOutboxTarget(value: OutboxTarget?): String? = value?.name

    @TypeConverter
    fun toOutboxTarget(value: String?): OutboxTarget? =
        value?.let { OutboxTarget.valueOf(it) }

    @TypeConverter
    fun fromOutboxOperation(value: OutboxOperation?): String? = value?.name

    @TypeConverter
    fun toOutboxOperation(value: String?): OutboxOperation? =
        value?.let { OutboxOperation.valueOf(it) }

    @TypeConverter
    fun fromOutboxStatus(value: OutboxStatus?): String? = value?.name

    @TypeConverter
    fun toOutboxStatus(value: String?): OutboxStatus? =
        value?.let { OutboxStatus.valueOf(it) }
}
