package org.sattl.inventory

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.sattl.inventory.data.db.AppDatabase
import org.sattl.inventory.data.repo.AuthRepository
import org.sattl.inventory.session.SessionManager

/**
 * Application class. Creates the app's long-lived objects once (simple manual dependency
 * injection, no framework). ViewModels get what they need from [container].
 */
class SattlApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(app: Application) {
    /** Lives as long as the app process; used for the session idle timer. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val database: AppDatabase = AppDatabase.build(app)
    val authRepository = AuthRepository(database)
    val sessionManager = SessionManager(appScope)
}
