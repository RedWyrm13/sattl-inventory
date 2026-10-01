package org.sattl.inventory.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * App-wide settings, stored as a single row with id = [SINGLETON_ID] (spec section 4).
 */
@Entity(tableName = "app_settings")
data class AppSettings(
    @PrimaryKey val id: Int = SINGLETON_ID,
    /** True once first-time setup (spec section 5.1) has created the admin. */
    val setupComplete: Boolean = false,
    /** Base64 PBKDF2 hash of the admin recovery code (spec section 8). */
    val recoveryCodeHash: String? = null,
    val recoveryCodeSalt: String? = null,
    /** UTC epoch ms of the last successful full export, or null if never (spec section 7.1). */
    val lastExportAt: Long? = null,
    val keepScreenOn: Boolean = true,
    val schemaVersion: Int,
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}
