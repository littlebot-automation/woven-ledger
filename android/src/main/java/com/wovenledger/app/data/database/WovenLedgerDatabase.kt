package com.wovenledger.app.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.wovenledger.app.data.dao.*
import com.wovenledger.app.data.entities.*

@Database(
    entities = [
        Plant::class,
        Settings::class,
        Party::class,
        Item::class,
        ItemStock::class,
        Staff::class,
        StaffWork::class,
        SalesInvoice::class,
        SalesInvoiceLine::class,
        PurchaseBill::class,
        PurchaseBillLine::class,
        Receipt::class,
        Payment::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class WovenLedgerDatabase : RoomDatabase() {
    abstract fun plantDao(): PlantDao
    abstract fun settingsDao(): SettingsDao
    abstract fun partyDao(): PartyDao
    abstract fun itemDao(): ItemDao
    abstract fun itemStockDao(): ItemStockDao
    abstract fun staffDao(): StaffDao
    abstract fun staffWorkDao(): StaffWorkDao
    abstract fun salesInvoiceDao(): SalesInvoiceDao
    abstract fun salesInvoiceLineDao(): SalesInvoiceLineDao
    abstract fun purchaseBillDao(): PurchaseBillDao
    abstract fun purchaseBillLineDao(): PurchaseBillLineDao
    abstract fun receiptDao(): ReceiptDao
    abstract fun paymentDao(): PaymentDao

    companion object {
        @Volatile
        private var INSTANCE: WovenLedgerDatabase? = null

        fun getDatabase(context: Context): WovenLedgerDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    WovenLedgerDatabase::class.java,
                    "woven_ledger_database"
                )
                    .addCallback(DatabaseCallback(context))
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class DatabaseCallback(private val context: Context) : RoomDatabase.Callback() {
        override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            super.onCreate(db)
            DatabaseInitializer.initializeDatabase(context, db)
        }
    }
}
