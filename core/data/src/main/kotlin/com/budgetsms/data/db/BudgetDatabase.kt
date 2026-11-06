package com.budgetsms.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.budgetsms.data.security.DatabaseKeyManager
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    version = 1,
    exportSchema = true,
    entities = [
        RawSmsEntity::class, SourceRecordEntity::class, DiscoveredSenderEntity::class, ApprovedSenderEntity::class,
        BankEntity::class, ProfileEntity::class, AccountEntity::class, AccountAssignmentEntity::class,
        FinancialCycleEntity::class, BankEventEntity::class, CategoryEntity::class, CategoryLimitEntity::class,
        BudgetAllocationEntity::class, TransferMatchEntity::class, TransferRejectionEntity::class,
        SavingsGoalEntity::class, SavingsInstallmentEntity::class, ForecastEntity::class,
        RecurringObligationEntity::class, ObligationOccurrenceEntity::class, ObligationPaymentMatchEntity::class,
        UserAppearancePreferenceEntity::class, AuditLogEntity::class, AppSettingEntity::class, AlertSentEntity::class,
    ],
)
abstract class BudgetDatabase : RoomDatabase() {
    abstract fun sms(): SmsDao
    abstract fun ledger(): LedgerDao
    abstract fun plan(): PlanDao
    abstract fun settings(): SettingsDao

    companion object {
        const val NAME = "budgetsms.db"

        /** All future schema changes must be added here as explicit migrations (see docs/SCHEMA.md). */
        val MIGRATIONS = emptyArray<androidx.room.migration.Migration>()

        @Volatile private var instance: BudgetDatabase? = null

        /**
         * Opens the SQLCipher-encrypted database. Never deletes or silently recreates an existing
         * database: a missing key or corruption surfaces as [DatabaseUnavailableException].
         */
        fun open(context: Context): BudgetDatabase = instance ?: synchronized(this) {
            instance ?: build(context.applicationContext).also { instance = it }
        }

        private fun build(context: Context): BudgetDatabase {
            System.loadLibrary("sqlcipher")
            val dbFile = context.getDatabasePath(NAME)
            val passphrase = DatabaseKeyManager(context).passphrase(databaseExists = dbFile.exists())
            val factory = NonDestructiveFactory(SupportOpenHelperFactory(passphrase))
            val db = Room.databaseBuilder(context, BudgetDatabase::class.java, NAME)
                .openHelperFactory(factory)
                .addMigrations(*MIGRATIONS)
                .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                .build()
            try {
                db.openHelper.writableDatabase // fail fast while the UI can show a safe error screen
            } catch (t: Throwable) {
                db.close()
                throw DatabaseUnavailableException(DatabaseUnavailableException.Reason.CANNOT_OPEN, t)
            }
            return db
        }

        fun closeForTests() { instance?.close(); instance = null }
    }
}

class DatabaseUnavailableException(val reason: Reason, cause: Throwable? = null) : Exception(reason.name, cause) {
    enum class Reason { KEY_MISSING, KEY_INVALIDATED, CANNOT_OPEN, CORRUPTED }
}

/** Wraps the open-helper callback so corruption never triggers the default delete-the-file behaviour. */
private class NonDestructiveFactory(private val delegate: SupportSQLiteOpenHelper.Factory) : SupportSQLiteOpenHelper.Factory {
    override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper {
        val original = configuration.callback
        val safe = object : SupportSQLiteOpenHelper.Callback(original.version) {
            override fun onConfigure(db: SupportSQLiteDatabase) = original.onConfigure(db)
            override fun onCreate(db: SupportSQLiteDatabase) = original.onCreate(db)
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = original.onUpgrade(db, oldVersion, newVersion)
            override fun onDowngrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = original.onDowngrade(db, oldVersion, newVersion)
            override fun onOpen(db: SupportSQLiteDatabase) = original.onOpen(db)
            override fun onCorruption(db: SupportSQLiteDatabase) {
                throw DatabaseUnavailableException(DatabaseUnavailableException.Reason.CORRUPTED)
            }
        }
        val cfg = SupportSQLiteOpenHelper.Configuration.builder(configuration.context)
            .name(configuration.name)
            .callback(safe)
            .noBackupDirectory(configuration.useNoBackupDirectory)
            .allowDataLossOnRecovery(false)
            .build()
        return delegate.create(cfg)
    }
}
