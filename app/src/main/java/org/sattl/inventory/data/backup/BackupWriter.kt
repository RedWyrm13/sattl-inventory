package org.sattl.inventory.data.backup

import org.json.JSONObject
import org.sattl.inventory.data.backup.BackupFormat.timestamp
import java.io.OutputStream
import java.time.ZoneId
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Writes the full backup ZIP (spec 7.1). */
object BackupWriter {

    fun write(data: BackupData, manifest: Manifest, out: OutputStream, zone: ZoneId) {
        val itemTags = data.items.associate { it.id to it.sattlTag }
        val userNames = data.users.associate { it.id to it.name }

        val items = listOf(BackupFormat.ITEM_COLUMNS) + data.items.map {
            listOf(
                it.id.toString(), it.uuid, it.sattlTag, it.manufacturer, it.modelNumber, it.serialNumber,
                it.homeLocation, it.dateIntoInventory.toString(), it.notes, it.isCheckoutable.toString(),
                it.isRetired.toString(), timestamp(it.createdAt, zone), timestamp(it.updatedAt, zone),
            )
        }
        val checkouts = listOf(BackupFormat.CHECKOUT_COLUMNS) + data.checkouts.map {
            listOf(
                it.id.toString(), it.uuid, it.itemId.toString(), itemTags[it.itemId], it.userId.toString(),
                userNames[it.userId], it.checkoutDate.toString(), it.expectedReturnDate?.toString(),
                it.destination, it.reason, timestamp(it.createdAt, zone), it.checkedInAt?.let { t -> timestamp(t, zone) },
                it.checkedInByUserId?.toString(), it.checkedInByUserId?.let(userNames::get),
            )
        }
        val users = listOf(BackupFormat.USER_COLUMNS) + data.users.map {
            listOf(
                it.id.toString(), it.uuid, it.name, it.role.name, it.isActive.toString(), it.mustChangePin.toString(),
                it.pinHash, it.pinSalt, it.failedAttempts.toString(), it.lockedUntil?.let { t -> timestamp(t, zone) },
                timestamp(it.createdAt, zone),
            )
        }
        val s = data.settings
        val settings = listOf(
            BackupFormat.SETTINGS_COLUMNS,
            listOf(s.recoveryCodeHash, s.recoveryCodeSalt, s.keepScreenOn.toString()),
        )

        ZipOutputStream(out).use { zip ->
            fun entry(name: String, text: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            entry(BackupFormat.ITEMS, Csv.write(items))
            entry(BackupFormat.CHECKOUTS, Csv.write(checkouts))
            entry(BackupFormat.USERS, Csv.write(users))
            entry(BackupFormat.SETTINGS, Csv.write(settings))
            entry(BackupFormat.MANIFEST, manifestJson(manifest, zone))
        }
    }

    fun manifestJson(m: Manifest, zone: ZoneId): String = JSONObject().apply {
        put("app", "SATTL Lab Inventory")
        put("appVersion", m.appVersion)
        put("schemaVersion", m.schemaVersion)
        put("exportedAt", timestamp(m.exportedAt, zone))
        put("counts", JSONObject(m.counts))
    }.toString(2)
}
