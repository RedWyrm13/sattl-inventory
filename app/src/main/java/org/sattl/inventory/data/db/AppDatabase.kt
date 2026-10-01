package org.sattl.inventory.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import org.sattl.inventory.data.dao.CheckoutDao
import org.sattl.inventory.data.dao.ItemDao
import org.sattl.inventory.data.dao.SettingsDao
import org.sattl.inventory.data.dao.UserDao
import org.sattl.inventory.data.entity.AppSettings
import org.sattl.inventory.data.entity.Checkout
import org.sattl.inventory.data.entity.Item
import org.sattl.inventory.data.entity.User

/**
 * The app's single local SQLite database, in the app's private storage (spec sections 2, 4, 8).
 *
 * Sync-readiness (spec section 2): all reads and writes go through repositories in
 * org.sattl.inventory.data.repo, every row carries timestamps, and nothing is hard-deleted.
 * A future sync feature can be added behind the repositories without touching the UI.
 */
@Database(
    entities = [Item::class, User::class, Checkout::class, AppSettings::class],
    version = AppDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun itemDao(): ItemDao
    abstract fun userDao(): UserDao
    abstract fun checkoutDao(): CheckoutDao
    abstract fun settingsDao(): SettingsDao

    companion object {
        /** Current schema version. See [Migrations] before changing it. */
        const val VERSION = 1
        private const val FILE_NAME = "sattl_inventory.db"

        /** Adds the extra constraints from [DbConstraints] when the database is first created. */
        val callback = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                DbConstraints.create(db)
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, FILE_NAME)
                .addMigrations(*Migrations.ALL)
                .addCallback(callback)
                .build()

        /** In-memory database for unit tests. */
        fun inMemory(context: Context): AppDatabase =
            Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .addCallback(callback)
                .allowMainThreadQueries()
                .build()
    }
}
