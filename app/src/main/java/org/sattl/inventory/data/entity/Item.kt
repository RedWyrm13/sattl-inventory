package org.sattl.inventory.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.util.UUID

/**
 * One piece of lab equipment (spec section 4, "Item").
 *
 * Status (Available / Checked out / Not checkoutable / Retired) and current location are
 * DERIVED from this row plus any open [Checkout]; they are never stored (spec section 4).
 * Rows are never hard-deleted; retiring sets [isRetired] (spec section 4, rule 6.9).
 */
@Entity(
    tableName = "items",
    // Rule 6.13: SATTL tags are unique, compared case-insensitively after trimming.
    // The column uses NOCASE collation, so this unique index ignores case. The repository
    // trims the tag before saving.
    indices = [Index(value = ["sattlTag"], unique = true), Index(value = ["uuid"], unique = true)],
)
data class Item(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /**
     * Globally unique id for a future sync feature (spec section 2). Generated once when the
     * row is created, never changed, and included in the full export (spec section 7.1).
     * [id] stays the local key used by foreign keys on this tablet.
     */
    val uuid: String = UUID.randomUUID().toString(),
    @ColumnInfo(collate = ColumnInfo.NOCASE) val sattlTag: String,
    val manufacturer: String,
    val modelNumber: String,
    /** Optional: some items have no serial number. */
    val serialNumber: String? = null,
    /** Room number or description, e.g. "Room 214". */
    val homeLocation: String,
    val dateIntoInventory: LocalDate,
    val notes: String? = null,
    val isCheckoutable: Boolean = true,
    val isRetired: Boolean = false,
    /** UTC epoch milliseconds. */
    val createdAt: Long,
    /** UTC epoch milliseconds. */
    val updatedAt: Long,
)
