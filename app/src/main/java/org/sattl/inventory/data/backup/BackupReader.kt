package org.sattl.inventory.data.backup

import org.json.JSONException
import org.json.JSONObject
import org.sattl.inventory.data.entity.Checkout
import org.sattl.inventory.data.entity.Item
import org.sattl.inventory.data.entity.Role
import org.sattl.inventory.data.entity.User
import java.io.IOException
import java.io.InputStream
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.UUID
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

sealed interface ReadResult {
    data class Valid(val backup: ValidBackup) : ReadResult

    /** Plain-language problems, each naming the file and line (spec 7.3 step 3). */
    data class Invalid(val problems: List<String>) : ReadResult
}

/**
 * Reads and validates a full backup ZIP (spec 7.3 step 2). Checks: manifest present, schema
 * version supported, every CSV parses with the expected columns and value types, row counts
 * match the manifest, references between tables are valid, no duplicate ids, UUIDs, SATTL
 * tags or user names, exactly one admin, and at most one open checkout per item.
 *
 * It never touches the database: restoring is a separate step, so an invalid file changes
 * nothing (spec 7.3 step 3).
 */
object BackupReader {
    /** Guards against huge or malicious files; a real backup is a few hundred KB. */
    private const val MAX_FILE_BYTES = 50L * 1024 * 1024

    fun read(input: InputStream, supportedSchemaVersion: Int): ReadResult {
        val files = try {
            unzip(input)
        } catch (e: ZipException) {
            return invalid("This file is not a valid ZIP file. Choose a backup made with Export (sattl-inventory-….zip).")
        } catch (e: IOException) {
            return invalid("The file could not be read: ${e.message ?: "unknown error"}. Check the USB drive and try again.")
        }
        if (files.isEmpty()) {
            return invalid("This file is not a ZIP file, or it is empty. Choose a backup made with Export (sattl-inventory-….zip).")
        }

        val manifestText = files[BackupFormat.MANIFEST]
            ?: return invalid("${BackupFormat.MANIFEST} is missing, so this does not look like a SATTL Inventory backup.")
        val manifest = try {
            parseManifest(manifestText)
        } catch (e: Exception) {
            return invalid("${BackupFormat.MANIFEST} could not be read (${e.message}). The backup may be damaged.")
        }
        if (manifest.schemaVersion > supportedSchemaVersion) {
            return invalid(
                "This backup was made by a newer version of the app (database version ${manifest.schemaVersion}; " +
                    "this tablet supports up to $supportedSchemaVersion). Update the app on this tablet first."
            )
        }
        if (manifest.schemaVersion < 1) return invalid("${BackupFormat.MANIFEST} has an invalid schemaVersion.")

        val problems = mutableListOf<String>()
        val items = readTable(files, BackupFormat.ITEMS, BackupFormat.ITEM_COLUMNS, manifest, problems, ::item)
        val users = readTable(files, BackupFormat.USERS, BackupFormat.USER_COLUMNS, manifest, problems, ::user)
        val checkouts = readTable(files, BackupFormat.CHECKOUTS, BackupFormat.CHECKOUT_COLUMNS, manifest, problems, ::checkout)
        val settingsRows = readTable(files, BackupFormat.SETTINGS, BackupFormat.SETTINGS_COLUMNS, manifest, problems, ::settings)
        if (files.containsKey(BackupFormat.SETTINGS) && settingsRows.size != 1) {
            problems += "${BackupFormat.SETTINGS} must have exactly one row of values (found ${settingsRows.size})."
        }
        if (problems.isNotEmpty()) return ReadResult.Invalid(problems)

        crossCheck(items, users, checkouts, problems)
        if (problems.isNotEmpty()) return ReadResult.Invalid(problems)

        return ReadResult.Valid(ValidBackup(manifest, BackupData(items, users, checkouts, settingsRows.single())))
    }

    private fun invalid(message: String) = ReadResult.Invalid(listOf(message))

    /** Reads every entry into memory as UTF-8 text, keyed by file name (folders ignored). */
    private fun unzip(input: InputStream): Map<String, String> {
        val files = mutableMapOf<String, String>()
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue
                val name = entry.name.substringAfterLast('/')
                val bytes = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val n = zip.read(buffer)
                    if (n < 0) break
                    bytes.write(buffer, 0, n)
                    if (bytes.size() > MAX_FILE_BYTES) throw IOException("$name is far too large to be a SATTL backup")
                }
                files[name] = bytes.toString(Charsets.UTF_8.name())
            }
        }
        return files
    }

    private fun parseManifest(text: String): Manifest {
        val json = try {
            JSONObject(text.removePrefix(Csv.BOM.toString()))
        } catch (e: JSONException) {
            throw IllegalArgumentException("it is not valid JSON")
        }
        val countsJson = json.getJSONObject("counts")
        return Manifest(
            appVersion = json.optString("appVersion"),
            schemaVersion = json.getInt("schemaVersion"),
            exportedAt = BackupFormat.parseTimestamp(json.getString("exportedAt")),
            counts = countsJson.keys().asSequence().associateWith { countsJson.getInt(it) },
        )
    }

    /** Parses one CSV file into rows, collecting problems. Rows with any problem are dropped. */
    private fun <T> readTable(
        files: Map<String, String>,
        file: String,
        columns: List<String>,
        manifest: Manifest,
        problems: MutableList<String>,
        parse: (Row) -> T,
    ): List<T> {
        val text = files[file] ?: run { problems += "$file is missing from the backup."; return emptyList() }
        val rows = try {
            Csv.parse(text)
        } catch (e: CsvException) {
            problems += "$file line ${e.line}: ${e.message}"
            return emptyList()
        }
        if (rows.isEmpty()) {
            problems += "$file is empty; it should at least have a header row."
            return emptyList()
        }
        val header = rows.first().map { it.trim() }
        val missing = columns.filter { it !in header }
        if (missing.isNotEmpty()) {
            problems += "$file is missing the column(s): ${missing.joinToString()}."
            return emptyList()
        }
        val index = header.withIndex().associate { it.value to it.index }
        val dataRows = rows.drop(1)

        val expected = manifest.counts[file]
        if (expected != null && expected != dataRows.size) {
            problems += "$file has ${dataRows.size} rows but the manifest says $expected. The file may be incomplete or edited."
        }

        return dataRows.mapIndexedNotNull { i, cells ->
            // Line numbers as Excel shows them: the header is line 1.
            val row = Row(file, i + 2, cells, index, problems)
            if (cells.size != header.size) {
                row.fail(null, "has ${cells.size} values but the header has ${header.size}")
                return@mapIndexedNotNull null
            }
            val value = parse(row)
            if (row.ok) value else null
        }
    }

    private fun item(r: Row) = Item(
        id = r.long("id"), uuid = r.uuid("uuid"), sattlTag = r.text("sattlTag"),
        manufacturer = r.text("manufacturer"), modelNumber = r.text("modelNumber"),
        serialNumber = r.optText("serialNumber"), homeLocation = r.text("homeLocation"),
        dateIntoInventory = r.date("dateIntoInventory"), notes = r.optText("notes"),
        isCheckoutable = r.bool("isCheckoutable"), isRetired = r.bool("isRetired"),
        createdAt = r.time("createdAt"), updatedAt = r.time("updatedAt"),
    )

    private fun user(r: Row) = User(
        id = r.long("id"), uuid = r.uuid("uuid"), name = r.text("name"), role = r.role("role"),
        isActive = r.bool("isActive"), mustChangePin = r.bool("mustChangePin"),
        pinHash = r.text("pinHash"), pinSalt = r.text("pinSalt"),
        failedAttempts = r.long("failedAttempts").toInt(), lockedUntil = r.optTime("lockedUntil"),
        createdAt = r.time("createdAt"),
    )

    private fun checkout(r: Row) = Checkout(
        id = r.long("id"), uuid = r.uuid("uuid"), itemId = r.long("itemId"), userId = r.long("userId"),
        checkoutDate = r.date("checkoutDate"), expectedReturnDate = r.optDate("expectedReturnDate"),
        destination = r.text("destination"), reason = r.text("reason"), createdAt = r.time("createdAt"),
        checkedInAt = r.optTime("checkedInAt"), checkedInByUserId = r.optLong("checkedInByUserId"),
    )

    private fun settings(r: Row) = SettingsBackup(
        recoveryCodeHash = r.text("recoveryCodeHash"),
        recoveryCodeSalt = r.text("recoveryCodeSalt"),
        keepScreenOn = r.bool("keepScreenOn"),
    )

    /** Rules that span rows or tables (spec 7.3 step 2, rules 6.1 and 6.13, section 3). */
    private fun crossCheck(items: List<Item>, users: List<User>, checkouts: List<Checkout>, problems: MutableList<String>) {
        fun <T> duplicates(file: String, what: String, rows: List<T>, key: (T) -> Any) {
            rows.groupBy(key).filter { it.value.size > 1 }.keys.forEach {
                problems += "$file: $what \"$it\" appears more than once."
            }
        }
        duplicates(BackupFormat.ITEMS, "id", items) { it.id }
        duplicates(BackupFormat.ITEMS, "uuid", items) { it.uuid }
        // Rule 6.13: unique ignoring case and surrounding spaces.
        duplicates(BackupFormat.ITEMS, "SATTL tag", items) { it.sattlTag.trim().lowercase() }
        duplicates(BackupFormat.USERS, "id", users) { it.id }
        duplicates(BackupFormat.USERS, "uuid", users) { it.uuid }
        duplicates(BackupFormat.USERS, "name", users) { it.name.trim().lowercase() }
        duplicates(BackupFormat.CHECKOUTS, "id", checkouts) { it.id }
        duplicates(BackupFormat.CHECKOUTS, "uuid", checkouts) { it.uuid }

        val admins = users.count { it.role == Role.ADMIN }
        if (admins != 1) problems += "${BackupFormat.USERS} must contain exactly one ADMIN (found $admins)."

        val itemIds = items.map { it.id }.toSet()
        val userIds = users.map { it.id }.toSet()
        checkouts.forEach { c ->
            val where = "${BackupFormat.CHECKOUTS} checkout id ${c.id}"
            if (c.itemId !in itemIds) problems += "$where refers to item id ${c.itemId}, which is not in ${BackupFormat.ITEMS}."
            if (c.userId !in userIds) problems += "$where refers to user id ${c.userId}, which is not in ${BackupFormat.USERS}."
            c.checkedInByUserId?.let {
                if (it !in userIds) problems += "$where was checked in by user id $it, which is not in ${BackupFormat.USERS}."
            }
            if ((c.checkedInAt == null) != (c.checkedInByUserId == null)) {
                problems += "$where must have both checkedInAt and checkedInByUserId, or neither."
            }
            if (c.expectedReturnDate != null && c.expectedReturnDate.isBefore(c.checkoutDate)) {
                problems += "$where has an expected return date before its checkout date."
            }
        }
        // Rule 6.1: at most one open checkout per item.
        checkouts.filter { it.checkedInAt == null }.groupBy { it.itemId }.filter { it.value.size > 1 }.keys.forEach {
            problems += "${BackupFormat.CHECKOUTS}: item id $it has more than one open checkout."
        }
    }

    /** One CSV data row; each getter records a problem (and marks the row bad) instead of throwing. */
    private class Row(
        private val file: String,
        private val line: Int,
        private val cells: List<String>,
        private val index: Map<String, Int>,
        private val problems: MutableList<String>,
    ) {
        var ok = true
            private set

        fun fail(column: String?, message: String) {
            ok = false
            problems += if (column == null) "$file line $line $message." else "$file line $line, $column: $message."
        }

        private fun raw(column: String): String = cells[index.getValue(column)]

        fun text(c: String): String = raw(c).also { if (it.isEmpty()) fail(c, "is empty but is required") }

        fun optText(c: String): String? = raw(c).ifEmpty { null }

        fun long(c: String): Long = raw(c).toLongOrNull() ?: 0L.also { fail(c, "must be a whole number (found \"${raw(c)}\")") }

        fun optLong(c: String): Long? = optText(c)?.let { it.toLongOrNull() ?: fail(c, "must be a whole number (found \"$it\")").let { null } }

        fun bool(c: String): Boolean = when (raw(c).lowercase()) {
            "true" -> true
            "false" -> false
            else -> false.also { fail(c, "must be true or false (found \"${raw(c)}\")") }
        }

        fun date(c: String): LocalDate = parseDate(c, raw(c)) ?: LocalDate.MIN

        fun optDate(c: String): LocalDate? = optText(c)?.let { parseDate(c, it) }

        private fun parseDate(c: String, v: String): LocalDate? = try {
            LocalDate.parse(v)
        } catch (_: DateTimeParseException) {
            fail(c, "must be a date like 2026-10-01 (found \"$v\")")
            null
        }

        fun time(c: String): Long = parseTime(c, raw(c)) ?: 0L

        fun optTime(c: String): Long? = optText(c)?.let { parseTime(c, it) }

        private fun parseTime(c: String, v: String): Long? = try {
            BackupFormat.parseTimestamp(v)
        } catch (_: DateTimeParseException) {
            fail(c, "must be a date and time like 2026-10-01T14:30:00.000-05:00 (found \"$v\")")
            null
        }

        fun uuid(c: String): String = raw(c).also {
            try {
                UUID.fromString(it)
            } catch (_: IllegalArgumentException) {
                fail(c, "must be a UUID (found \"$it\")")
            }
        }

        fun role(c: String): Role = Role.entries.firstOrNull { it.name == raw(c) }
            ?: Role.USER.also { fail(c, "must be ADMIN or USER (found \"${raw(c)}\")") }
    }
}
