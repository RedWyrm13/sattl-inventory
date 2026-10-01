package org.sattl.inventory.data

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.sattl.inventory.data.db.AppDatabase
import org.sattl.inventory.util.Clock

/** Fresh in-memory database, with the same triggers as the real one. */
fun newTestDb(): AppDatabase = AppDatabase.inMemory(ApplicationProvider.getApplicationContext<Application>())

/** A clock the test can move forward. */
class FakeClock(var nowMillis: Long = 1_790_000_000_000L) : Clock {
    override fun now() = nowMillis
    fun advance(millis: Long) { nowMillis += millis }
}
