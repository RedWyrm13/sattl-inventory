package org.sattl.inventory.data.repo

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.sattl.inventory.data.backup.BackupData
import org.sattl.inventory.data.backup.BackupFormat
import org.sattl.inventory.data.backup.BackupReader
import org.sattl.inventory.data.backup.BackupWriter
import org.sattl.inventory.data.backup.Manifest
import org.sattl.inventory.data.backup.ReadResult
import org.sattl.inventory.data.backup.SettingsBackup
import org.sattl.inventory.data.backup.ValidBackup
import org.sattl.inventory.data.db.AppDatabase
import org.sattl.inventory.data.entity.AppSettings
import org.sattl.inventory.security.PinHasher
import org.sattl.inventory.session.SessionUser
import org.sattl.inventory.util.Clock
import java.io.InputStream
import java.io.OutputStream
import java.time.ZoneId

sealed interface RestoreResult {
    data object Done : RestoreResult
    data class Failed(val message: String) : RestoreResult
}

/**
 * Full export and restore (spec 7.1, 7.3), admin only.
 *
 * Export reads every table inside one transaction, so the backup is a consistent snapshot.
 * Restore replaces every table inside one transaction, so a failure leaves the old data
 * exactly as it was (spec 7.3 step 5).
 */
class BackupRepository(
    private val db: AppDatabase,
    private val appVersion: String,
    private val clock: Clock = Clock.SYSTEM,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) {
    private val items = db.itemDao()
    private val users = db.userDao()
    private val checkouts = db.checkoutDao()
    private val settings = db.settingsDao()

    /** When the last full export finished, or null if never (spec 7.1 reminder banner). */
    val lastExportAt: Flow<Long?> = settings.observe().map { it?.lastExportAt }

    fun suggestedFileName(): String = BackupFormat.backupFileName(clock.now(), zone())

    /**
     * Writes the full backup ZIP to [out] and closes it. lastExportAt is updated only after
     * the whole file has been written (spec 7.1).
     */
    suspend fun exportFull(actor: SessionUser, out: OutputStream) {
        requireAdmin(actor)
        val now = clock.now()
        val data = db.withTransaction {
            val s = settings.get() ?: error("The app has not been set up.")
            BackupData(
                items = items.getAll(),
                users = users.getAll(),
                checkouts = checkouts.getAll(),
                settings = SettingsBackup(
                    recoveryCodeHash = s.recoveryCodeHash.orEmpty(),
                    recoveryCodeSalt = s.recoveryCodeSalt.orEmpty(),
                    keepScreenOn = s.keepScreenOn,
                ),
            )
        }
        val manifest = Manifest(
            appVersion = appVersion,
            schemaVersion = AppDatabase.VERSION,
            exportedAt = now,
            counts = mapOf(
                BackupFormat.ITEMS to data.items.size,
                BackupFormat.CHECKOUTS to data.checkouts.size,
                BackupFormat.USERS to data.users.size,
                BackupFormat.SETTINGS to 1,
            ),
        )
        withContext(Dispatchers.IO) {
            out.use { BackupWriter.write(data, manifest, it, zone()) }
        }
        db.withTransaction {
            settings.get()?.let { settings.upsert(it.copy(lastExportAt = now)) }
        }
    }

    /** Spec 7.3 step 2: reads and validates a backup. Changes nothing. */
    suspend fun read(input: InputStream): ReadResult = withContext(Dispatchers.IO) {
        input.use { BackupReader.read(it, AppDatabase.VERSION) }
    }

    /**
     * Spec 7.3 steps 4–5: after the admin re-enters their current PIN, replaces ALL data with
     * [backup] in a single transaction. The caller then logs everyone out (step 6).
     */
    suspend fun restore(actor: SessionUser, adminPin: String, backup: ValidBackup): RestoreResult {
        requireAdmin(actor)
        val admin = users.getById(actor.id) ?: return RestoreResult.Failed("Your account was not found.")
        val pinOk = withContext(Dispatchers.Default) { PinHasher.verify(adminPin, admin.pinHash, admin.pinSalt) }
        if (!pinOk) return RestoreResult.Failed("That PIN is not correct. Nothing has been changed.")
        return try {
            replaceAll(backup)
            RestoreResult.Done
        } catch (e: Exception) {
            RestoreResult.Failed("The restore failed and nothing was changed: ${e.message}")
        }
    }

    /** Deletes every row and inserts the backup's rows with their original ids, all or nothing. */
    internal suspend fun replaceAll(backup: ValidBackup) {
        val d = backup.data
        db.withTransaction {
            // Children first when deleting, parents first when inserting (foreign keys).
            checkouts.deleteAll()
            items.deleteAll()
            users.deleteAll()
            settings.deleteAll()

            users.insertAll(d.users)
            items.insertAll(d.items)
            checkouts.insertAll(d.checkouts)
            settings.upsert(
                AppSettings(
                    setupComplete = true,
                    recoveryCodeHash = d.settings.recoveryCodeHash,
                    recoveryCodeSalt = d.settings.recoveryCodeSalt,
                    // This tablet now holds exactly what that backup holds.
                    lastExportAt = backup.manifest.exportedAt,
                    keepScreenOn = d.settings.keepScreenOn,
                    schemaVersion = AppDatabase.VERSION,
                )
            )
        }
    }

    private fun requireAdmin(actor: SessionUser) {
        check(actor.isAdmin) { "Only the admin can export or restore." }
    }
}
