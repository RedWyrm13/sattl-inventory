package org.sattl.inventory.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import org.sattl.inventory.data.entity.AppSettings

/** Reads and writes the single [AppSettings] row. */
@Dao
interface SettingsDao {
    @Query("SELECT * FROM app_settings WHERE id = ${AppSettings.SINGLETON_ID}")
    suspend fun get(): AppSettings?

    @Query("SELECT * FROM app_settings WHERE id = ${AppSettings.SINGLETON_ID}")
    fun observe(): Flow<AppSettings?>

    @Upsert
    suspend fun upsert(settings: AppSettings)
}
