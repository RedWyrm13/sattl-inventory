package org.sattl.inventory.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.util.UUID

/**
 * One checkout of one item by one user (spec section 4, "Checkout"; rule 6.2).
 * A checkout is "open" while [checkedInAt] is null.
 *
 * "At most one open checkout per item" (rule 6.1) is enforced in the database by triggers;
 * see [org.sattl.inventory.data.db.DbConstraints].
 */
@Entity(
    tableName = "checkouts",
    foreignKeys = [
        ForeignKey(entity = Item::class, parentColumns = ["id"], childColumns = ["itemId"]),
        ForeignKey(entity = User::class, parentColumns = ["id"], childColumns = ["userId"]),
        ForeignKey(entity = User::class, parentColumns = ["id"], childColumns = ["checkedInByUserId"]),
    ],
    indices = [
        Index("itemId"),
        Index("userId"),
        Index("checkedInByUserId"),
        Index(value = ["uuid"], unique = true),
    ],
)
data class Checkout(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /**
     * Globally unique id for a future sync feature (spec section 2). Generated once when the
     * row is created, never changed, and included in the full export (spec section 7.1).
     * [id] stays the local key used by foreign keys on this tablet.
     */
    val uuid: String = UUID.randomUUID().toString(),
    val itemId: Long,
    /** Always the logged-in user at checkout time; this person is responsible (rule 6.3). */
    val userId: Long,
    val checkoutDate: LocalDate,
    /** Informational only; never triggers anything (rule 6.6). Not before [checkoutDate]. */
    val expectedReturnDate: LocalDate? = null,
    val destination: String,
    val reason: String,
    /** Exact time the checkout was saved, UTC epoch ms. */
    val createdAt: Long,
    /** UTC epoch ms; null while the checkout is open. */
    val checkedInAt: Long? = null,
    /** Who performed the check-in: the borrower or the admin (rule 6.4). */
    val checkedInByUserId: Long? = null,
)
