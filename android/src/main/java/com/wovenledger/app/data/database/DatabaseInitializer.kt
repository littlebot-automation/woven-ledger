package com.wovenledger.app.data.database

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import com.wovenledger.app.data.entities.*
import kotlinx.coroutines.*
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Database initializer that seeds demo data matching Symfony fixtures.
 * Deterministic — no randomness — so the dashboard, reports and ledgers
 * look the same on every load.
 */
object DatabaseInitializer {
    fun initializeDatabase(context: Context, db: SupportSQLiteDatabase) {
        CoroutineScope(Dispatchers.IO).launch {
            val database = WovenLedgerDatabase.getDatabase(context)
            seedDemoData(database)
        }
    }

    private suspend fun seedDemoData(db: WovenLedgerDatabase) {
        val today = LocalDate.now()
        val ago = fun(days: Int): LocalDate = today.minusDays(days.toLong())

        // ================================================================ Plants
        val main = Plant(name = "Main Plant", address = "Plot 14, GIDC Industrial Estate, Vatva, Ahmedabad")
        val unit2 = Plant(name = "Unit II", address = "Survey 88, Kathwada Road, Ahmedabad")

        val mainId = db.plantDao().insert(main)
        val unit2Id = db.plantDao().insert(unit2)

        // ================================================================ Settings
        val settings = Settings(
            companyName = "Sahyog Poly Weaves Pvt. Ltd.",
            address = "Plot 14, GIDC Industrial Estate\nVatva, Ahmedabad 382445",
            phone = "+91 79 2583 4410",
            gstin = "24AABCS1429P1ZR",
            lowStockDefault = 50,
            defaultWage = 50000L,
            currentPlantId = mainId,
            nextInvoiceNo = 16,
            nextPurchaseNo = 11,
            nextReceiptNo = 8,
            nextPaymentNo = 13
        )
        db.settingsDao().insert(settings)

        // ================================================================ Parties
        data class PartyData(val name: String, val type: PartyType, val phone: String, val gstin: String?, val opening: Long, val openingType: BalanceType)

        val partyData = listOf(
            PartyData("Shree Balaji Traders", PartyType.CUSTOMER, "+91 98250 11234", "24AAACS1234A1Z5", 0L, BalanceType.TO_RECEIVE),
            PartyData("Gujarat Agro Packaging", PartyType.CUSTOMER, "+91 99049 55120", "24AABCG7788L1ZQ", 1850000L, BalanceType.TO_RECEIVE),
            PartyData("Kisan Seeds & Fertilisers", PartyType.CUSTOMER, "+91 94260 33410", "24AADCK1122M1Z8", 0L, BalanceType.TO_RECEIVE),
            PartyData("Deep Cement Works", PartyType.CUSTOMER, "+91 90999 71200", "24AAECD4455N1ZT", 4200000L, BalanceType.TO_RECEIVE),
            PartyData("Raj Polymers", PartyType.SUPPLIER, "+91 98795 22110", "24AAFCR9900P1ZV", 2600000L, BalanceType.TO_PAY),
            PartyData("Nirmal Granules LLP", PartyType.SUPPLIER, "+91 97250 87451", "24AAGCN3344Q1ZW", 0L, BalanceType.TO_PAY),
            PartyData("Sunrise Packaging Co.", PartyType.BOTH, "+91 93270 66890", "24AAHCS5566R1ZX", 950000L, BalanceType.TO_RECEIVE),
            PartyData("Ambica Threads", PartyType.SUPPLIER, "+91 96870 41235", null, 0L, BalanceType.TO_PAY)
        )

        val parties = mutableListOf<Party>()
        partyData.forEachIndexed { i, data ->
            val party = Party(
                name = data.name,
                type = data.type,
                phone = data.phone,
                gstin = data.gstin,
                address = "Ahmedabad, Gujarat",
                openingBalance = data.opening,
                openingBalanceType = data.openingType,
                createdAt = LocalDateTime.of(ago(180 - i * 5).atStartOfDay().toLocalDate(), LocalDateTime.now().toLocalTime())
            )
            val partyId = db.partyDao().insert(party)
            parties.add(party.copy(id = partyId))
        }

        // ================================================================ Items
        val itemData = listOf(
            listOf("PP Woven Fabric Roll 90 GSM", "raw_material", "kg", 92.50, "54072090", null, 1240, 480),
            listOf("PP Woven Fabric Roll 120 GSM", "raw_material", "kg", 98.00, "54072090", null, 860, 310),
            listOf("PP Woven Fabric Roll 150 GSM", "raw_material", "kg", 104.75, "54072090", 80, 42, 25),
            listOf("PP Granules — Natural", "raw_material", "kg", 88.20, "39021000", 500, 3200, 1150),
            listOf("Master Batch — White", "raw_material", "kg", 145.00, "32041790", 40, 28, 12),
            listOf("Sewing Thread Cone", "raw_material", "pcs", 62.00, "54011000", 100, 340, 120),
            listOf("Cement Bag 50 kg — Laminated", "finished_good", "pcs", 14.80, "63053200", 2000, 18400, 7600),
            listOf("Fertiliser Bag 50 kg", "finished_good", "pcs", 12.60, "63053200", 2000, 12250, 4300),
            listOf("Grain Bag 100 kg", "finished_good", "pcs", 21.40, "63053200", 1000, 4870, 1980),
            listOf("BOPP Laminated Bag — Printed", "finished_good", "pcs", 27.90, "63053200", 800, 640, 210)
        )

        val items = mutableListOf<Item>()
        itemData.forEach { data ->
            val name = data[0] as String
            val typeStr = data[1] as String
            val unit = data[2] as String
            val rate = data[3] as Double
            val hsn = data[4] as String
            val threshold = data[5] as Int?
            val qMain = data[6] as Int
            val qUnit2 = data[7] as Int

            val type = when (typeStr) {
                "raw_material" -> ItemType.RAW_MATERIAL
                "finished_good" -> ItemType.FINISHED_GOOD
                else -> ItemType.RAW_MATERIAL
            }
            val item = Item(name = name, type = type, unit = unit, defaultRate = (rate * 100).toLong(), hsn = hsn, lowStockThreshold = threshold)
            val itemId = db.itemDao().insert(item)
            items.add(item.copy(id = itemId))

            // Stock for main plant
            val mainStock = ItemStock(itemId = itemId, plantId = mainId, qty = qMain.toDouble())
            db.itemStockDao().insert(mainStock)

            // Stock for unit 2
            val unit2Stock = ItemStock(itemId = itemId, plantId = unit2Id, qty = qUnit2.toDouble())
            db.itemStockDao().insert(unit2Stock)
        }

        // ================================================================ Staff
        val staffData = listOf(
            listOf("Ramesh Patel", "Loom Operator", mainId, "daily", 62000L, 0L),
            listOf("Sunita Devi", "Stitching", mainId, "piece", 0L, 185L),
            listOf("Imran Shaikh", "Lamination Helper", mainId, "daily", 54000L, 0L),
            listOf("Kiran Solanki", "Printing", unit2Id, "piece", 0L, 240L),
            listOf("Mahesh Vaghela", "Loading & Dispatch", unit2Id, "daily", 50000L, 0L),
            listOf("Priya Chauhan", "Cutting & Bundling", mainId, "piece", 0L, 140L)
        )

        val staffMembers = mutableListOf<Staff>()
        staffData.forEachIndexed { i, data ->
            val name = data[0] as String
            val role = data[1] as String
            val plantId = data[2] as Long
            val wageTypeStr = data[3] as String
            val daily = data[4] as Long
            val piece = data[5] as Long

            val wageType = when (wageTypeStr) {
                "daily" -> StaffWageType.DAILY
                "piece" -> StaffWageType.PIECE
                else -> StaffWageType.DAILY
            }

            val staff = Staff(
                name = name,
                role = role,
                plantId = plantId,
                phone = "+91 9${82500000 + i * 13571}",
                wageType = wageType,
                dailyRate = daily,
                pieceRate = piece
            )
            val staffId = db.staffDao().insert(staff)
            staffMembers.add(staff.copy(id = staffId))
        }

        // ================================================================ Sales Invoices
        val salesSpecs = listOf(
            listOf(92, 0, mainId, listOf(listOf(6, 5000, 14.80), listOf(7, 2000, 12.60)), 0),
            listOf(88, 1, mainId, listOf(listOf(8, 1200, 21.40)), 500),
            listOf(81, 3, unit2Id, listOf(listOf(6, 8000, 15.10)), 0),
            listOf(74, 2, mainId, listOf(listOf(7, 4500, 12.90), listOf(8, 800, 21.40)), 1200),
            listOf(70, 0, mainId, listOf(listOf(9, 600, 27.90)), 0),
            listOf(63, 6, unit2Id, listOf(listOf(6, 3000, 14.95)), 0),
            listOf(58, 1, mainId, listOf(listOf(7, 6000, 12.75), listOf(6, 1500, 14.80)), 900),
            listOf(51, 3, unit2Id, listOf(listOf(8, 2200, 21.90)), 0),
            listOf(45, 2, mainId, listOf(listOf(6, 4000, 15.00)), 0),
            listOf(38, 0, mainId, listOf(listOf(9, 900, 28.40), listOf(8, 500, 21.40)), 600),
            listOf(31, 1, unit2Id, listOf(listOf(7, 3500, 12.80)), 0),
            listOf(25, 6, mainId, listOf(listOf(6, 2500, 14.90)), 0),
            listOf(18, 3, unit2Id, listOf(listOf(8, 1800, 21.60), listOf(9, 400, 27.90)), 0),
            listOf(11, 2, mainId, listOf(listOf(7, 5200, 12.95)), 1500),
            listOf(4, 0, mainId, listOf(listOf(6, 6000, 15.20)), 0)
        )

        var invoiceNo = 1L
        salesSpecs.forEach { spec ->
            val daysAgo = spec[0] as Int
            val partyIdx = spec[1] as Int
            val plantId = spec[2] as Long
            @Suppress("UNCHECKED_CAST")
            val lines = spec[3] as List<List<Number>>
            val discount = spec[4] as Int

            var subtotal = 0.0
            val invoice = SalesInvoice(
                no = "SI-${invoiceNo.toString().padStart(4, '0')}",
                date = ago(daysAgo),
                partyId = parties[partyIdx].id,
                plantId = plantId,
                discount = (discount * 100).toLong()
            )
            val invoiceId = db.salesInvoiceDao().insert(invoice)

            lines.forEach { line ->
                val itemIdx = line[0].toInt()
                val qty = line[1].toDouble()
                val rate = line[2].toDouble()
                val amount = qty * rate
                subtotal += amount

                val invoiceLine = SalesInvoiceLine(
                    invoiceId = invoiceId,
                    itemId = items[itemIdx].id,
                    qty = qty,
                    rate = (rate * 100).toLong(),
                    amount = (amount * 100).toLong()
                )
                db.salesInvoiceLineDao().insert(invoiceLine)
            }

            val total = subtotal - discount
            val updatedInvoice = invoice.copy(id = invoiceId, subtotal = (subtotal * 100).toLong(), total = (total * 100).toLong())
            db.salesInvoiceDao().update(updatedInvoice)
            invoiceNo++
        }

        // ================================================================ Purchase Bills
        val purchaseSpecs = listOf(
            listOf(95, 4, mainId, listOf(listOf(3, 5000, 86.50), listOf(4, 200, 142.00)), 0),
            listOf(86, 5, mainId, listOf(listOf(0, 2000, 90.00)), 0),
            listOf(78, 4, unit2Id, listOf(listOf(3, 3000, 87.40)), 1000),
            listOf(69, 7, mainId, listOf(listOf(5, 500, 60.50)), 0),
            listOf(60, 5, mainId, listOf(listOf(1, 1500, 96.50)), 0),
            listOf(49, 4, unit2Id, listOf(listOf(3, 4000, 88.90)), 2000),
            listOf(40, 5, mainId, listOf(listOf(2, 800, 103.00)), 0),
            listOf(29, 7, unit2Id, listOf(listOf(5, 400, 61.20)), 0),
            listOf(16, 4, mainId, listOf(listOf(3, 3500, 89.60), listOf(4, 150, 146.00)), 0),
            listOf(6, 5, mainId, listOf(listOf(0, 1800, 92.75)), 0)
        )

        var billNo = 1L
        purchaseSpecs.forEach { spec ->
            val daysAgo = spec[0] as Int
            val partyIdx = spec[1] as Int
            val plantId = spec[2] as Long
            @Suppress("UNCHECKED_CAST")
            val lines = spec[3] as List<List<Number>>
            val discount = spec[4] as Int

            var subtotal = 0.0
            val bill = PurchaseBill(
                no = "PB-${billNo.toString().padStart(4, '0')}",
                date = ago(daysAgo),
                partyId = parties[partyIdx].id,
                plantId = plantId,
                discount = (discount * 100).toLong()
            )
            val billId = db.purchaseBillDao().insert(bill)

            lines.forEach { line ->
                val itemIdx = line[0].toInt()
                val qty = line[1].toDouble()
                val rate = line[2].toDouble()
                val amount = qty * rate
                subtotal += amount

                val billLine = PurchaseBillLine(
                    billId = billId,
                    itemId = items[itemIdx].id,
                    qty = qty,
                    rate = (rate * 100).toLong(),
                    amount = (amount * 100).toLong()
                )
                db.purchaseBillLineDao().insert(billLine)
            }

            val total = subtotal - discount
            val updatedBill = bill.copy(id = billId, subtotal = (subtotal * 100).toLong(), total = (total * 100).toLong())
            db.purchaseBillDao().update(updatedBill)
            billNo++
        }

        // ================================================================ Receipts
        val receiptSpecs = listOf(
            listOf(80, 0, 70000, "Bank Transfer"),
            listOf(66, 3, 110000, "Cheque"),
            listOf(55, 1, 60000, "UPI"),
            listOf(42, 2, 45000, "Bank Transfer"),
            listOf(27, 0, 55000, "Cash"),
            listOf(14, 6, 30000, "UPI"),
            listOf(5, 3, 40000, "Bank Transfer")
        )

        var receiptNo = 1L
        receiptSpecs.forEach { spec ->
            val daysAgo = spec[0] as Int
            val partyIdx = spec[1] as Int
            val amount = spec[2] as Int
            val modeStr = spec[3] as String

            val mode = when (modeStr) {
                "Bank Transfer" -> PaymentMode.BANK_TRANSFER
                "Cheque" -> PaymentMode.CHEQUE
                "UPI" -> PaymentMode.UPI
                else -> PaymentMode.CASH
            }

            val receipt = Receipt(
                no = "RC-${receiptNo.toString().padStart(4, '0')}",
                date = ago(daysAgo),
                partyId = parties[partyIdx].id,
                amount = (amount * 100).toLong(),
                mode = mode,
                notes = "Against outstanding invoices"
            )
            db.receiptDao().insert(receipt)
            receiptNo++
        }

        // ================================================================ Payments
        val paymentSpecs = listOf(
            listOf(83, 4, 300000, "Bank Transfer"),
            listOf(64, 5, 120000, "Cheque"),
            listOf(44, 4, 250000, "Bank Transfer"),
            listOf(21, 7, 40000, "UPI"),
            listOf(8, 5, 90000, "Bank Transfer")
        )

        var paymentNo = 1L
        paymentSpecs.forEach { spec ->
            val daysAgo = spec[0] as Int
            val partyIdx = spec[1] as Int
            val amount = spec[2] as Int
            val modeStr = spec[3] as String

            val mode = when (modeStr) {
                "Bank Transfer" -> PaymentMode.BANK_TRANSFER
                "Cheque" -> PaymentMode.CHEQUE
                "UPI" -> PaymentMode.UPI
                else -> PaymentMode.CASH
            }

            val payment = Payment(
                no = "PY-${paymentNo.toString().padStart(4, '0')}",
                date = ago(daysAgo),
                type = PaymentType.PARTY,
                partyId = parties[partyIdx].id,
                amount = (amount * 100).toLong(),
                mode = mode,
                notes = "Against purchase bills"
            )
            db.paymentDao().insert(payment)
            paymentNo++
        }

        // ================================================================ Staff Work & Wages
        val workTypes = listOf("Stitching", "Loom Operation", "Lamination", "Printing", "Cutting & Bundling", "Loading")

        staffMembers.forEachIndexed { sIdx, staff ->
            val settled = mutableListOf<StaffWork>()

            repeat(5) { n ->
                val daysAgo = 60 - (sIdx * 3) - (n * 7)
                val qty = if (staff.wageType == StaffWageType.DAILY) 1.0 else (120 + n * 35 + sIdx * 10).toDouble()

                val work = StaffWork(
                    date = ago(maxOf(1, daysAgo)),
                    staffId = staff.id,
                    plantId = staff.plantId,
                    workType = workTypes[(sIdx + n) % workTypes.size],
                    qty = qty,
                    rate = if (staff.wageType == StaffWageType.DAILY) staff.dailyRate else staff.pieceRate
                )
                val workId = db.staffWorkDao().insert(work)
                settled.add(work.copy(id = workId))
            }

            var total = 0.0
            settled.forEach { work ->
                total += work.getAmount()
            }

            val voucher = Payment(
                no = "PY-${paymentNo.toString().padStart(4, '0')}",
                date = ago(maxOf(1, 20 - sIdx)),
                type = PaymentType.STAFF,
                staffId = staff.id,
                amount = (total * 100).toLong(),
                mode = PaymentMode.CASH,
                notes = "Wage settlement — ${settled.size} entries"
            )
            val voucherId = db.paymentDao().insert(voucher)
            paymentNo++

            settled.forEach { work ->
                val updatedWork = work.copy(paid = true, paymentVoucherId = voucherId)
                db.staffWorkDao().update(updatedWork)
            }

            // Unpaid tail
            repeat(3) { n ->
                val qty = if (staff.wageType == StaffWageType.DAILY) 1.0 else (140 + n * 25).toDouble()

                val work = StaffWork(
                    date = ago(maxOf(1, 12 - n * 4)),
                    staffId = staff.id,
                    plantId = staff.plantId,
                    workType = workTypes[(sIdx + n + 2) % workTypes.size],
                    qty = qty,
                    rate = if (staff.wageType == StaffWageType.DAILY) staff.dailyRate else staff.pieceRate
                )
                db.staffWorkDao().insert(work)
            }
        }
    }
}
