package com.wovenledger.app.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.wovenledger.app.data.auth.AuthToken
import com.wovenledger.app.data.auth.AuthTokenDao
import com.wovenledger.app.data.dao.*
import com.wovenledger.app.data.entities.*
import com.wovenledger.app.data.outbox.OutboxDao
import com.wovenledger.app.data.outbox.OutboxEntry

/**
 * Adds the table holding the JWT.
 *
 * A real migration rather than another destructive upgrade: v4 introduced the outbox,
 * which holds writes the server has never seen, so dropping the database now would
 * throw away work the user believes is saved.
 */
private val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `auth_token` (" +
                "`id` INTEGER NOT NULL, " +
                "`token` TEXT NOT NULL, " +
                "`issued_at` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))"
        )
    }
}

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
        Payment::class,
        OutboxEntry::class,
        AuthToken::class
    ],
    // v2 dropped the local demo seed. v3 separates a party's opening balance from
    // its running ledger balance, which the API now sends as two distinct figures.
    // v4 adds the outbox, so a save with no signal is queued instead of refused.
    // v5 adds the JWT the API is now behind — and is the first upgrade to migrate
    // rather than drop, because v4's queue would go with the tables.
    version = 5,
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
    abstract fun outboxDao(): OutboxDao
    abstract fun authTokenDao(): AuthTokenDao

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
                    // Every table but one is a cache of the server, so throwing them
                    // away and re-syncing is simpler than migrating them.
                    //
                    // The outbox is the exception and the reason to be careful from
                    // here on: it holds writes the server has never seen, so a
                    // destructive upgrade now destroys real work. Bumping this version
                    // again means either draining the queue first or writing a real
                    // migration for the `outbox` table — which is what MIGRATION_4_5
                    // does, and what every version from here on has to do.
                    .addMigrations(MIGRATION_4_5)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
