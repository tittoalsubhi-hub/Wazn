package com.budgetsms.app

import android.app.Application
import com.budgetsms.app.notify.Notifier
import com.budgetsms.data.BackupManager
import com.budgetsms.data.BudgetRepository
import com.budgetsms.data.DefaultCategories
import com.budgetsms.data.db.BudgetDatabase
import com.budgetsms.data.db.DatabaseUnavailableException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Manual dependency container; keeps the app free of reflection-based DI. */
class AppContainer(app: Application) {
    val database: BudgetDatabase = BudgetDatabase.open(app)
    val repository = BudgetRepository(database)
    val backup = BackupManager(database)
    val notifier = Notifier(app)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}

class BudgetApp : Application() {
    /** Null when the encrypted database cannot be opened; the UI then shows a safe, non-destructive error. */
    var container: AppContainer? = null; private set
    var startupError: DatabaseUnavailableException? = null; private set

    override fun onCreate() {
        super.onCreate()
        try {
            val c = AppContainer(this)
            container = c
            c.notifier.createChannels()
            c.scope.launch { c.repository.ensureDefaults(DefaultCategories.all()); c.repository.reprocessUndecided() }
        } catch (e: DatabaseUnavailableException) {
            startupError = e
        } catch (t: Throwable) {
            startupError = DatabaseUnavailableException(DatabaseUnavailableException.Reason.CANNOT_OPEN, t)
        }
    }
}
