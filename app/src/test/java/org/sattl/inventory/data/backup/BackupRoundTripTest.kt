package org.sattl.inventory.data.backup

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.sattl.inventory.data.FakeClock
import org.sattl.inventory.data.db.AppDatabase
import org.sattl.inventory.data.newTestDb
import org.sattl.inventory.data.repo.AuthRepository
import org.sattl.inventory.data.repo.BackupRepository
import org.sattl.inventory.data.repo.CheckoutRepository
import org.sattl.inventory.data.repo.CheckoutResult
import org.sattl.inventory.data.repo.ItemRepository
import org.sattl.inventory.data.repo.ItemSaveResult
import org.sattl.inventory.data.repo.LoginResult
import org.sattl.inventory.data.repo.RestoreResult
import org.sattl.inventory.data.repo.UserRepository
import org.sattl.inventory.data.repo.UserSaveResult
import org.sattl.inventory.domain.CheckoutInput
import org.sattl.inventory.domain.ItemInput
import org.sattl.inventory.security.RecoveryCode
import org.sattl.inventory.session.SessionUser
import org.sattl.inventory.util.today
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.ZoneId
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Spec 7.1 and 7.3, and the milestone 5 round-trip test: export, wipe, restore, compare.
 * Also checks that bad files are rejected with a reason and change nothing.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class BackupRoundTripTest {

    // A zone with daylight saving, to prove timestamps survive exactly.
    private val zone = ZoneId.of("America/Chicago")
    private val clock = FakeClock(1_790_000_000_123L)

    private lateinit var source: AppDatabase
    private lateinit var target: AppDatabase
    private lateinit var sourceAdmin: SessionUser
    private lateinit var targetAdmin: SessionUser
    private val recoveryCode = RecoveryCode.generate()
    private var samId = 0L

    @Before
    fun setUp() = runTest {
        source = newTestDb()
        target = newTestDb()
        sourceAdmin = populate(source)
        // The target is a "freshly installed" tablet: someone ran setup with a temporary admin.
        val t = AuthRepository(target, clock).completeSetup("Temporary", "0000", RecoveryCode.generate())
        targetAdmin = SessionUser(t.id, t.name, t.role)
    }

    @After
    fun tearDown() {
        source.close()
        target.close()
    }

    private fun backups(db: AppDatabase) = BackupRepository(db, "test-1.0", clock) { zone }

    /** Realistic data with every awkward case we can think of. */
    private suspend fun populate(db: AppDatabase): SessionUser {
        val auth = AuthRepository(db, clock)
        val a = auth.completeSetup("Pat Lee", "2468", recoveryCode)
        val admin = SessionUser(a.id, a.name, a.role)
        val users = UserRepository(db, clock)
        val items = ItemRepository(db, clock)
        val checkouts = CheckoutRepository(db, clock)

        clock.advance(1_001)
        val sam = (users.addUser(admin, "Sam O'Brien", "1111") as UserSaveResult.Saved).user
        samId = sam.id
        auth.changePin(sam.id, null, "4444")
        val alex = (users.addUser(admin, "Álex, \"Al\"", "2222") as UserSaveResult.Saved).user
        repeat(5) { auth.login(alex.id, "9999") } // Alex is locked out
        val gone = (users.addUser(admin, "Former Member", "3333") as UserSaveResult.Saved).user
        users.setActive(admin, gone.id, false)

        suspend fun item(tag: String, serial: String = "", notes: String = "") =
            (items.save(admin, null, ItemInput(tag, "Ettus", "B210", serial, "Room 214", LocalDate.of(2025, 3, 9), notes))
                as ItemSaveResult.Saved).itemId
        val sdr = item("SDR-001", serial = "SN 001", notes = "Comma, \"quotes\"\nand a second line ✓")
        val server = item("SRV-002")
        val retired = item("OLD-003")
        items.setRetired(admin, retired, true)
        val noCheckout = item("NC-004")
        items.setCheckoutable(admin, noCheckout, false)

        val samUser = SessionUser(sam.id, sam.name, sam.role)
        clock.advance(7_777)
        val closed = (checkouts.checkOut(samUser, sdr, CheckoutInput(clock.today(), clock.today().plusDays(3), "Hangar 3", "Range test")) as CheckoutResult.Done).checkoutId
        clock.advance(86_400_123)
        checkouts.checkIn(admin, closed)
        checkouts.checkOut(samUser, server, CheckoutInput(clock.today(), null, "Bldg 5, lab 2", "Demo for visitors"))

        db.settingsDao().get()!!.let { db.settingsDao().upsert(it.copy(keepScreenOn = false)) }
        return admin
    }

    private suspend fun exportBytes(): ByteArray {
        val out = ByteArrayOutputStream()
        backups(source).exportFull(sourceAdmin, out)
        return out.toByteArray()
    }

    private suspend fun read(bytes: ByteArray) = backups(target).read(ByteArrayInputStream(bytes))

    @Test
    fun exportWipeRestoreCompare() = runTest {
        val bytes = exportBytes()
        val result = read(bytes)
        assertTrue("valid backup: $result", result is ReadResult.Valid)
        val backup = (result as ReadResult.Valid).backup
        assertEquals(4, backup.data.items.size)
        assertEquals(4, backup.data.users.size)
        assertEquals(2, backup.data.checkouts.size)

        assertEquals(RestoreResult.Done, backups(target).restore(targetAdmin, "0000", backup))

        // Every row identical, field for field (ids, UUIDs, hashes, timestamps to the ms).
        assertEquals(source.itemDao().getAll(), target.itemDao().getAll())
        assertEquals(source.userDao().getAll(), target.userDao().getAll())
        assertEquals(source.checkoutDao().getAll(), target.checkoutDao().getAll())
        val s = source.settingsDao().get()!!
        val t = target.settingsDao().get()!!
        assertEquals(s.recoveryCodeHash, t.recoveryCodeHash)
        assertEquals(s.recoveryCodeSalt, t.recoveryCodeSalt)
        assertEquals(false, t.keepScreenOn)
        assertEquals(backup.manifest.exportedAt, t.lastExportAt)

        // Spec 7.3 step 6: everyone logs in with the PINs from the backup.
        val auth = AuthRepository(target, clock)
        assertTrue(auth.login(sourceAdmin.id, "2468") is LoginResult.Success)
        assertTrue(auth.login(samId, "4444") is LoginResult.Success)
        assertEquals(listOf("Pat Lee", "Sam O'Brien", "Álex, \"Al\""), auth.activeUsers.first().map { it.name }.sortedBy { it != "Pat Lee" })
        assertTrue("old recovery code works", auth.checkRecoveryCode(recoveryCode))
        assertTrue("temporary admin is gone", target.userDao().findByName("Temporary") == null)
    }

    @Test
    fun appKeepsWorkingAfterRestore() = runTest {
        backups(target).restore(targetAdmin, "0000", (read(exportBytes()) as ReadResult.Valid).backup)
        val newId = (ItemRepository(target, clock).save(
            sourceAdmin, null, ItemInput("NEW-1", "Dell", "R740", "", "Room 1", LocalDate.of(2026, 1, 1)),
        ) as ItemSaveResult.Saved).itemId
        assertTrue("new ids do not collide with restored ones", source.itemDao().getAll().none { it.id == newId })
    }

    @Test
    fun exportUpdatesLastExportAtAndManifestCounts() = runTest {
        val before = source.settingsDao().get()!!.lastExportAt
        val backup = (read(exportBytes()) as ReadResult.Valid).backup
        assertEquals(null, before)
        assertEquals(clock.now(), source.settingsDao().get()!!.lastExportAt)
        assertEquals(mapOf("items.csv" to 4, "checkouts.csv" to 2, "users.csv" to 4, "settings.csv" to 1), backup.manifest.counts)
        assertEquals(AppDatabase.VERSION, backup.manifest.schemaVersion)
    }

    @Test
    fun zipContainsReadableExcelFriendlyFiles() = runTest {
        val files = unzip(exportBytes())
        assertEquals(setOf("items.csv", "checkouts.csv", "users.csv", "settings.csv", "manifest.json"), files.keys)
        val checkouts = files.getValue("checkouts.csv")
        assertTrue(checkouts.startsWith("﻿"))
        // Names and tags alongside ids, so it reads well in Excel (spec 7.1).
        assertTrue(checkouts.contains("SDR-001") && checkouts.contains("Sam O'Brien"))
        // Never a plain PIN (spec 8).
        val users = files.getValue("users.csv")
        listOf("2468", "4444", "1111", "2222", "3333").forEach { assertTrue(it, !users.contains(",$it,")) }
        // ISO 8601 dates and date-times.
        assertTrue(files.getValue("items.csv").contains("2025-03-09"))
        assertTrue(Regex("""\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}-0[56]:00""").containsMatchIn(checkouts))
    }

    @Test
    fun fileNameFollowsSpec() {
        assertTrue(Regex("""sattl-inventory-\d{4}-\d{2}-\d{2}-\d{4}\.zip""").matches(backups(source).suggestedFileName()))
    }

    // --- Invalid files are rejected with a reason (spec 7.3 step 3) ---------------------------

    private fun unzip(bytes: ByteArray): MutableMap<String, String> {
        val files = linkedMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                files[e.name] = z.readBytes().toString(Charsets.UTF_8)
            }
        }
        return files
    }

    private fun zip(files: Map<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            files.forEach { (name, text) ->
                z.putNextEntry(ZipEntry(name)); z.write(text.toByteArray()); z.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private suspend fun problemsAfter(edit: (MutableMap<String, String>) -> Unit): List<String> {
        val files = unzip(exportBytes()).also(edit)
        val result = read(zip(files))
        assertTrue("expected invalid, got $result", result is ReadResult.Invalid)
        return (result as ReadResult.Invalid).problems
    }

    private fun List<String>.mentions(text: String) = assertTrue("$this should mention \"$text\"", any { it.contains(text) })

    @Test
    fun notAZip() = runTest {
        val result = read("this is not a zip".toByteArray()) as ReadResult.Invalid
        result.problems.mentions("not a ZIP")
    }

    @Test
    fun missingManifest() = runTest {
        problemsAfter { it.remove("manifest.json") }.mentions("manifest.json is missing")
    }

    @Test
    fun newerSchemaVersion() = runTest {
        problemsAfter { it["manifest.json"] = it.getValue("manifest.json").replace(Regex("\"schemaVersion\": \\d+"), "\"schemaVersion\": 99") }
            .mentions("newer version")
    }

    @Test
    fun missingCsv() = runTest {
        problemsAfter { it.remove("users.csv") }.mentions("users.csv is missing")
    }

    @Test
    fun brokenCsv() = runTest {
        problemsAfter { it["items.csv"] = it.getValue("items.csv") + "1,\"unclosed\r\n" }.mentions("items.csv line")
    }

    @Test
    fun rowCountMismatch() = runTest {
        problemsAfter { f ->
            val lines = f.getValue("items.csv").split("\r\n")
            f["items.csv"] = (lines.take(2) + lines.drop(3)).joinToString("\r\n")
        }.mentions("manifest says 4")
    }

    @Test
    fun danglingReference() = runTest {
        problemsAfter { f ->
            val c = Csv.parse(f.getValue("checkouts.csv")).toMutableList()
            c[1] = c[1].toMutableList().also { it[2] = "999" }
            f["checkouts.csv"] = Csv.write(c)
        }.mentions("item id 999")
    }

    @Test
    fun duplicateTagIgnoringCase() = runTest {
        problemsAfter { f -> f["items.csv"] = f.getValue("items.csv").replace("SRV-002", "sdr-001") }.mentions("SATTL tag")
    }

    @Test
    fun duplicateUserName() = runTest {
        problemsAfter { f -> f["users.csv"] = f.getValue("users.csv").replace("Former Member", "SAM O'BRIEN") }.mentions("name")
    }

    @Test
    fun badValue() = runTest {
        problemsAfter { f -> f["users.csv"] = f.getValue("users.csv").replace(",USER,", ",BOSS,") }.mentions("must be ADMIN or USER")
    }

    // --- Restore safety --------------------------------------------------------------------

    @Test
    fun wrongPinChangesNothing() = runTest {
        val before = target.userDao().getAll()
        val backup = (read(exportBytes()) as ReadResult.Valid).backup
        assertTrue(backups(target).restore(targetAdmin, "9999", backup) is RestoreResult.Failed)
        assertEquals(before, target.userDao().getAll())
        assertTrue(target.itemDao().getAll().isEmpty())
    }

    @Test
    fun failureHalfwayLeavesOldDataIntact() = runTest {
        // Put some data on the target so there is something to lose.
        ItemRepository(target, clock).save(targetAdmin, null, ItemInput("KEEP-1", "Dell", "R740", "", "Room 1", LocalDate.of(2026, 1, 1)))
        val beforeItems = target.itemDao().getAll()
        val beforeUsers = target.userDao().getAll()

        // A backup that slipped past validation with two open checkouts on one item: the
        // database trigger rejects the second insert part way through the restore.
        val good = (read(exportBytes()) as ReadResult.Valid).backup
        val open = good.data.checkouts.first { it.checkedInAt == null }
        val bad = good.copy(data = good.data.copy(checkouts = good.data.checkouts + open.copy(id = 500, uuid = java.util.UUID.randomUUID().toString())))

        val result = backups(target).restore(targetAdmin, "0000", bad)
        assertTrue(result is RestoreResult.Failed)
        assertEquals(beforeItems, target.itemDao().getAll())
        assertEquals(beforeUsers, target.userDao().getAll())
        assertTrue(target.checkoutDao().getAll().isEmpty())
        assertNotEquals(null, target.settingsDao().get())
    }

    @Test(expected = IllegalStateException::class)
    fun nonAdminCannotExport() = runTest {
        backups(source).exportFull(SessionUser(samId, "Sam", org.sattl.inventory.data.entity.Role.USER), ByteArrayOutputStream())
    }
}
