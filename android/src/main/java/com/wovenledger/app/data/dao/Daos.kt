package com.wovenledger.app.data.dao

import androidx.room.*
import com.wovenledger.app.data.entities.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PlantDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(plant: Plant): Long

    @Query("SELECT * FROM plants")
    fun getAllPlants(): Flow<List<Plant>>

    @Query("SELECT * FROM plants WHERE id = :id")
    fun getPlant(id: Long): Flow<Plant?>

    @Update
    suspend fun update(plant: Plant)

    @Delete
    suspend fun delete(plant: Plant)
}

@Dao
interface SettingsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(settings: Settings)

    @Query("SELECT * FROM settings WHERE id = 1")
    fun getSettings(): Flow<Settings?>

    @Update
    suspend fun update(settings: Settings)
}

@Dao
interface PartyDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(party: Party): Long

    @Query("SELECT * FROM parties ORDER BY created_at DESC")
    fun getAllParties(): Flow<List<Party>>

    @Query("SELECT * FROM parties WHERE type IN ('CUSTOMER', 'BOTH') ORDER BY name")
    fun getCustomers(): Flow<List<Party>>

    @Query("SELECT * FROM parties WHERE type IN ('SUPPLIER', 'BOTH') ORDER BY name")
    fun getSuppliers(): Flow<List<Party>>

    @Query("SELECT * FROM parties WHERE id = :id")
    fun getParty(id: Long): Flow<Party?>

    @Query("SELECT * FROM parties WHERE name LIKE :q OR phone LIKE :q OR gstin LIKE :q ORDER BY name ASC")
    fun search(q: String): Flow<List<Party>>

    /**
     * Removes rows the server no longer has.
     *
     * Sync was upsert-only, so anything deleted on the portal stayed on the phone
     * for good. An empty list means the server has none of these at all, which
     * SQLite cannot express as `NOT IN ()` — [deleteAll] covers that case.
     */
    @Query("DELETE FROM parties WHERE id NOT IN (:keep)")
    suspend fun deleteMissing(keep: List<Long>)

    @Query("DELETE FROM parties")
    suspend fun deleteAll()

    @Query("SELECT DISTINCT party_id FROM (SELECT party_id FROM sales_invoices UNION SELECT party_id FROM purchase_bills UNION SELECT party_id FROM receipts UNION SELECT party_id FROM payments)")
    suspend fun findIdsWithTransactions(): List<Int>

    @Query("SELECT (SELECT COUNT(*) FROM sales_invoices WHERE party_id = :id) + (SELECT COUNT(*) FROM purchase_bills WHERE party_id = :id) + (SELECT COUNT(*) FROM receipts WHERE party_id = :id) + (SELECT COUNT(*) FROM payments WHERE party_id = :id)")
    suspend fun hasTransactions(id: Int): Int

    @Update
    suspend fun update(party: Party)

    @Delete
    suspend fun delete(party: Party)
}

@Dao
interface ItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: Item): Long

    @Query("SELECT * FROM items ORDER BY name")
    fun getAllItems(): Flow<List<Item>>

    @Query("SELECT * FROM items WHERE type = :type ORDER BY name")
    fun getItemsByType(type: String): Flow<List<Item>>

    /**
     * Removes rows the server no longer has.
     *
     * Sync was upsert-only, so anything deleted on the portal stayed on the phone
     * for good. An empty list means the server has none of these at all, which
     * SQLite cannot express as `NOT IN ()` — [deleteAll] covers that case.
     */
    @Query("DELETE FROM items WHERE id NOT IN (:keep)")
    suspend fun deleteMissing(keep: List<Long>)

    @Query("DELETE FROM items")
    suspend fun deleteAll()

    @Query("SELECT * FROM items WHERE id = :id")
    fun getItem(id: Long): Flow<Item?>

    @Update
    suspend fun update(item: Item)

    @Delete
    suspend fun delete(item: Item)
}

@Dao
interface ItemStockDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(stock: ItemStock): Long

    @Query("SELECT * FROM item_stocks WHERE plant_id = :plantId ORDER BY item_id")
    fun getStockByPlant(plantId: Long): Flow<List<ItemStock>>

    @Query("SELECT * FROM item_stocks WHERE item_id = :itemId AND plant_id = :plantId")
    fun getStock(itemId: Long, plantId: Long): Flow<ItemStock?>

    @Update
    suspend fun update(stock: ItemStock)

    @Delete
    suspend fun delete(stock: ItemStock)
}

@Dao
interface StaffDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(staff: Staff): Long

    @Query("SELECT * FROM staff ORDER BY name")
    fun getAllStaff(): Flow<List<Staff>>

    @Query("SELECT * FROM staff WHERE plant_id = :plantId ORDER BY name")
    fun getStaffByPlant(plantId: Long): Flow<List<Staff>>

    /**
     * Removes rows the server no longer has.
     *
     * Sync was upsert-only, so anything deleted on the portal stayed on the phone
     * for good. An empty list means the server has none of these at all, which
     * SQLite cannot express as `NOT IN ()` — [deleteAll] covers that case.
     */
    @Query("DELETE FROM staff WHERE id NOT IN (:keep)")
    suspend fun deleteMissing(keep: List<Long>)

    @Query("DELETE FROM staff")
    suspend fun deleteAll()

    @Query("SELECT * FROM staff WHERE id = :id")
    fun getStaff(id: Long): Flow<Staff?>

    @Update
    suspend fun update(staff: Staff)

    @Delete
    suspend fun delete(staff: Staff)
}

@Dao
interface StaffWorkDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(work: StaffWork): Long

    @Query("SELECT * FROM staff_work ORDER BY date DESC, id DESC")
    fun getAllWork(): Flow<List<StaffWork>>

    @Query("SELECT * FROM staff_work WHERE staff_id = :staffId ORDER BY date DESC")
    fun getWorkByStaff(staffId: Long): Flow<List<StaffWork>>

    @Query("SELECT * FROM staff_work WHERE paid = 0 ORDER BY staff_id, date")
    fun getUnpaidWork(): Flow<List<StaffWork>>

    /**
     * Removes rows the server no longer has.
     *
     * Sync was upsert-only, so anything deleted on the portal stayed on the phone
     * for good. An empty list means the server has none of these at all, which
     * SQLite cannot express as `NOT IN ()` — [deleteAll] covers that case.
     */
    @Query("DELETE FROM staff_work WHERE id NOT IN (:keep)")
    suspend fun deleteMissing(keep: List<Long>)

    @Query("DELETE FROM staff_work")
    suspend fun deleteAll()

    @Query("SELECT * FROM staff_work WHERE id = :id")
    fun getWork(id: Long): Flow<StaffWork?>

    @Query("SELECT * FROM staff_work WHERE staff_id = :staffId AND paid = 0 ORDER BY date ASC")
    fun findUnpaidFor(staffId: Int): Flow<List<StaffWork>>

    @Query("SELECT SUM(qty * rate) FROM staff_work WHERE paid = 0")
    suspend fun sumUnpaid(): Long

    @Query("SELECT SUM(qty * rate) FROM staff_work WHERE paid = 1")
    suspend fun sumPaid(): Long

    @Query("SELECT staff_id, SUM(qty * rate) as paid, SUM(CASE WHEN paid=0 THEN qty * rate ELSE 0 END) as unpaid FROM staff_work GROUP BY staff_id ORDER BY staff_id ASC")
    fun sumByStaff(): Flow<List<StaffWorkSum>>

    @Query("SELECT COUNT(*) FROM staff_work WHERE staff_id = :staffId")
    suspend fun hasEntriesFor(staffId: Int): Int

    @Update
    suspend fun update(work: StaffWork)

    @Delete
    suspend fun delete(work: StaffWork)
}

@Dao
interface SalesInvoiceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(invoice: SalesInvoice): Long

    @Query("SELECT * FROM sales_invoices ORDER BY date DESC")
    fun getAllInvoices(): Flow<List<SalesInvoice>>

    @Query("SELECT * FROM sales_invoices WHERE party_id = :partyId ORDER BY date DESC")
    fun getInvoicesByParty(partyId: Long): Flow<List<SalesInvoice>>

    @Query("SELECT * FROM sales_invoices WHERE id = :id")
    fun getInvoice(id: Long): Flow<SalesInvoice?>

    /**
     * Removes rows the server no longer has.
     *
     * Sync was upsert-only, so anything deleted on the portal stayed on the phone
     * for good. An empty list means the server has none of these at all, which
     * SQLite cannot express as `NOT IN ()` — [deleteAll] covers that case.
     */
    @Query("DELETE FROM sales_invoices WHERE id NOT IN (:keep)")
    suspend fun deleteMissing(keep: List<Long>)

    @Query("DELETE FROM sales_invoices")
    suspend fun deleteAll()

    @Query("SELECT SUM(total) FROM sales_invoices")
    suspend fun sumTotal(): Long

    @Query("SELECT * FROM sales_invoices ORDER BY date DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<SalesInvoice>

    @Query("SELECT * FROM sales_invoices WHERE party_id = :partyId ORDER BY date ASC")
    fun findForParty(partyId: Int): Flow<List<SalesInvoice>>

    @Update
    suspend fun update(invoice: SalesInvoice)

    @Delete
    suspend fun delete(invoice: SalesInvoice)
}

@Dao
interface SalesInvoiceLineDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(line: SalesInvoiceLine): Long

    @Query("SELECT * FROM sales_invoice_lines WHERE invoice_id = :invoiceId")
    fun getLinesByInvoice(invoiceId: Long): Flow<List<SalesInvoiceLine>>

    @Query("DELETE FROM sales_invoice_lines WHERE invoice_id = :invoiceId")
    suspend fun deleteForInvoice(invoiceId: Long)

    @Delete
    suspend fun delete(line: SalesInvoiceLine)
}

@Dao
interface PurchaseBillDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bill: PurchaseBill): Long

    @Query("SELECT * FROM purchase_bills ORDER BY date DESC")
    fun getAllBills(): Flow<List<PurchaseBill>>

    @Query("SELECT * FROM purchase_bills WHERE party_id = :partyId ORDER BY date DESC")
    fun getBillsByParty(partyId: Long): Flow<List<PurchaseBill>>

    @Query("SELECT * FROM purchase_bills WHERE id = :id")
    fun getBill(id: Long): Flow<PurchaseBill?>

    /**
     * Removes rows the server no longer has.
     *
     * Sync was upsert-only, so anything deleted on the portal stayed on the phone
     * for good. An empty list means the server has none of these at all, which
     * SQLite cannot express as `NOT IN ()` — [deleteAll] covers that case.
     */
    @Query("DELETE FROM purchase_bills WHERE id NOT IN (:keep)")
    suspend fun deleteMissing(keep: List<Long>)

    @Query("DELETE FROM purchase_bills")
    suspend fun deleteAll()

    @Query("SELECT SUM(total) FROM purchase_bills")
    suspend fun sumTotal(): Long

    @Query("SELECT * FROM purchase_bills ORDER BY date DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<PurchaseBill>

    @Query("SELECT * FROM purchase_bills WHERE party_id = :partyId ORDER BY date ASC")
    fun findForParty(partyId: Int): Flow<List<PurchaseBill>>

    @Update
    suspend fun update(bill: PurchaseBill)

    @Delete
    suspend fun delete(bill: PurchaseBill)
}

@Dao
interface PurchaseBillLineDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(line: PurchaseBillLine): Long

    @Query("SELECT * FROM purchase_bill_lines WHERE bill_id = :billId")
    fun getLinesByBill(billId: Long): Flow<List<PurchaseBillLine>>

    @Query("DELETE FROM purchase_bill_lines WHERE bill_id = :billId")
    suspend fun deleteForBill(billId: Long)

    @Delete
    suspend fun delete(line: PurchaseBillLine)
}

@Dao
interface ReceiptDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(receipt: Receipt): Long

    @Query("SELECT * FROM receipts ORDER BY date DESC")
    fun getAllReceipts(): Flow<List<Receipt>>

    @Query("SELECT * FROM receipts WHERE party_id = :partyId ORDER BY date DESC")
    fun getReceiptsByParty(partyId: Long): Flow<List<Receipt>>

    @Query("SELECT * FROM receipts WHERE id = :id")
    fun getReceipt(id: Long): Flow<Receipt?>

    /**
     * Removes rows the server no longer has.
     *
     * Sync was upsert-only, so anything deleted on the portal stayed on the phone
     * for good. An empty list means the server has none of these at all, which
     * SQLite cannot express as `NOT IN ()` — [deleteAll] covers that case.
     */
    @Query("DELETE FROM receipts WHERE id NOT IN (:keep)")
    suspend fun deleteMissing(keep: List<Long>)

    @Query("DELETE FROM receipts")
    suspend fun deleteAll()

    @Query("SELECT SUM(amount) FROM receipts")
    suspend fun sumAmount(): Long

    @Query("SELECT * FROM receipts ORDER BY date DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<Receipt>

    @Query("SELECT * FROM receipts WHERE party_id = :partyId ORDER BY date ASC")
    fun findForParty(partyId: Int): Flow<List<Receipt>>

    @Update
    suspend fun update(receipt: Receipt)

    @Delete
    suspend fun delete(receipt: Receipt)
}

@Dao
interface PaymentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(payment: Payment): Long

    @Query("SELECT * FROM payments ORDER BY date DESC")
    fun getAllPayments(): Flow<List<Payment>>

    @Query("SELECT * FROM payments WHERE party_id = :partyId AND type = 'PARTY' ORDER BY date DESC")
    fun getPaymentsByParty(partyId: Long): Flow<List<Payment>>

    @Query("SELECT * FROM payments WHERE staff_id = :staffId AND type = 'STAFF' ORDER BY date DESC")
    fun getPaymentsByStaff(staffId: Long): Flow<List<Payment>>

    @Query("SELECT * FROM payments WHERE id = :id")
    fun getPayment(id: Long): Flow<Payment?>

    /**
     * Removes rows the server no longer has.
     *
     * Sync was upsert-only, so anything deleted on the portal stayed on the phone
     * for good. An empty list means the server has none of these at all, which
     * SQLite cannot express as `NOT IN ()` — [deleteAll] covers that case.
     */
    @Query("DELETE FROM payments WHERE id NOT IN (:keep)")
    suspend fun deleteMissing(keep: List<Long>)

    @Query("DELETE FROM payments")
    suspend fun deleteAll()

    @Query("SELECT SUM(amount) FROM payments")
    suspend fun sumAmount(): Long

    @Query("SELECT * FROM payments ORDER BY date DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<Payment>

    @Query("SELECT * FROM payments WHERE party_id = :partyId AND type = 'PARTY' ORDER BY date ASC")
    fun findPartyPayments(partyId: Int): Flow<List<Payment>>

    @Update
    suspend fun update(payment: Payment)

    @Delete
    suspend fun delete(payment: Payment)
}

// Helper data class for aggregated staff work totals
data class StaffWorkSum(
    val staff_id: Int,
    val paid: Long,
    val unpaid: Long
)
