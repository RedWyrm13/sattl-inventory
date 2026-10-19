package org.sattl.inventory.data.backup

import org.sattl.inventory.data.entity.Checkout
import org.sattl.inventory.data.entity.Item
import org.sattl.inventory.data.entity.User
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The full backup ZIP (spec 7.1): file names, CSV columns and value formats.
 *
 * The column names below ARE the file format. Renaming or removing one breaks restoring
 * older backups. When the database schema changes, add columns at the end and teach
 * [BackupReader] to cope with backups that lack them.
 */
object BackupFormat {
    const val ITEMS = "items.csv"
    const val CHECKOUTS = "checkouts.csv"
    const val USERS = "users.csv"
    const val SETTINGS = "settings.csv"
    const val MANIFEST = "manifest.json"

    val ITEM_COLUMNS = listOf(
        "id", "uuid", "sattlTag", "manufacturer", "modelNumber", "serialNumber", "homeLocation",
        "dateIntoInventory", "notes", "isCheckoutable", "isRetired", "createdAt", "updatedAt",
    )

    /** itemSattlTag, userName and checkedInByName are for reading in Excel only; restore uses the ids. */
    val CHECKOUT_COLUMNS = listOf(
        "id", "uuid", "itemId", "itemSattlTag", "userId", "userName", "checkoutDate", "expectedReturnDate",
        "destination", "reason", "createdAt", "checkedInAt", "checkedInByUserId", "checkedInByName",
    )

    /** PIN hash and salt only, never a plain PIN (spec 7.1, section 8). */
    val USER_COLUMNS = listOf(
        "id", "uuid", "name", "role", "isActive", "mustChangePin", "pinHash", "pinSalt",
        "failedAttempts", "lockedUntil", "createdAt",
    )

    val SETTINGS_COLUMNS = listOf("recoveryCodeHash", "recoveryCodeSalt", "keepScreenOn")

    /**
     * Timestamps are ISO 8601 in the tablet's local time, with milliseconds and the UTC offset,
     * e.g. 2026-10-01T14:30:00.123-05:00. Local time reads naturally in Excel; the offset and
     * milliseconds make restore exact, even across daylight-saving changes.
     */
    private val TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")

    fun timestamp(epochMillis: Long, zone: ZoneId): String =
        OffsetDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), zone).format(TIMESTAMP)

    /** Accepts any ISO 8601 date-time with an offset (milliseconds optional). */
    fun parseTimestamp(text: String): Long = OffsetDateTime.parse(text).toInstant().toEpochMilli()

    /** Spec 7.1 file name, e.g. sattl-inventory-2026-10-01-1430.zip, in local time. */
    fun backupFileName(epochMillis: Long, zone: ZoneId): String =
        "sattl-inventory-" + LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), zone)
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm")) + ".zip"
}

/** Settings that travel with a backup (spec 7.1). */
data class SettingsBackup(
    val recoveryCodeHash: String,
    val recoveryCodeSalt: String,
    val keepScreenOn: Boolean,
)

/** Everything a full backup contains. */
data class BackupData(
    val items: List<Item>,
    val users: List<User>,
    val checkouts: List<Checkout>,
    val settings: SettingsBackup,
)

/** manifest.json (spec 7.1). Counts are keyed by file name, e.g. "items.csv". */
data class Manifest(
    val appVersion: String,
    val schemaVersion: Int,
    val exportedAt: Long,
    val counts: Map<String, Int>,
)

/** A backup that has been read and fully validated, ready to restore. */
data class ValidBackup(val manifest: Manifest, val data: BackupData)
