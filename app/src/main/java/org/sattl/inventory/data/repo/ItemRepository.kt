package org.sattl.inventory.data.repo

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import org.sattl.inventory.data.db.AppDatabase
import org.sattl.inventory.data.entity.Item
import org.sattl.inventory.data.model.CheckoutHistoryRow
import org.sattl.inventory.data.model.InventoryRow
import org.sattl.inventory.domain.ItemField
import org.sattl.inventory.domain.ItemInput
import org.sattl.inventory.domain.ItemValidator
import org.sattl.inventory.session.SessionUser
import org.sattl.inventory.util.Clock

sealed interface ItemSaveResult {
    data class Saved(val itemId: Long) : ItemSaveResult
    data class Invalid(val errors: Map<ItemField, String>) : ItemSaveResult
}

sealed interface ItemActionResult {
    data object Done : ItemActionResult
    data class Blocked(val message: String) : ItemActionResult
}

/**
 * Items: browsing (spec 5.4, 5.5) and the admin's add / edit / retire / checkoutable actions
 * (spec 5.8, rules 6.7–6.9, 6.13). Every write runs in a transaction (rule 6.15).
 *
 * Admin-only methods take the acting [SessionUser] and refuse non-admins, as a safety net
 * behind the UI, which already hides those actions (spec section 3).
 */
class ItemRepository(
    private val db: AppDatabase,
    private val clock: Clock = Clock.SYSTEM,
) {
    private val items = db.itemDao()
    private val checkouts = db.checkoutDao()

    fun observeInventory(): Flow<List<InventoryRow>> = items.observeInventory()

    fun observeItem(id: Long): Flow<InventoryRow?> = items.observeInventoryRow(id)

    fun observeHistory(itemId: Long): Flow<List<CheckoutHistoryRow>> = checkouts.observeHistory(itemId)

    /**
     * Adds a new item ([itemId] null) or updates an existing one (spec 5.8).
     * Text is trimmed; blank optional fields are stored as null. A SATTL tag already used by
     * any other item, including a retired one, is rejected (rule 6.13).
     */
    suspend fun save(actor: SessionUser, itemId: Long?, input: ItemInput): ItemSaveResult {
        requireAdmin(actor)
        val i = input.trimmed()
        val errors = ItemValidator.validate(i)
        if (errors.isNotEmpty()) return ItemSaveResult.Invalid(errors)

        val now = clock.now()
        return try {
            db.withTransaction {
                duplicateTagError(i.sattlTag, itemId)?.let {
                    return@withTransaction ItemSaveResult.Invalid(mapOf(ItemField.SATTL_TAG to it))
                }
                if (itemId == null) {
                    val id = items.insert(
                        Item(
                            sattlTag = i.sattlTag,
                            manufacturer = i.manufacturer,
                            modelNumber = i.modelNumber,
                            serialNumber = i.serialNumber.ifEmpty { null },
                            homeLocation = i.homeLocation,
                            dateIntoInventory = i.dateIntoInventory!!,
                            notes = i.notes.ifEmpty { null },
                            isCheckoutable = i.isCheckoutable,
                            createdAt = now,
                            updatedAt = now,
                        )
                    )
                    ItemSaveResult.Saved(id)
                } else {
                    val existing = items.getById(itemId) ?: error("Item $itemId not found")
                    items.update(
                        existing.copy(
                            sattlTag = i.sattlTag,
                            manufacturer = i.manufacturer,
                            modelNumber = i.modelNumber,
                            serialNumber = i.serialNumber.ifEmpty { null },
                            homeLocation = i.homeLocation,
                            dateIntoInventory = i.dateIntoInventory!!,
                            notes = i.notes.ifEmpty { null },
                            // Rule 6.7: changing this never touches an open checkout.
                            isCheckoutable = i.isCheckoutable,
                            updatedAt = now,
                        )
                    )
                    ItemSaveResult.Saved(itemId)
                }
            }
        } catch (_: SQLiteConstraintException) {
            // Backstop: the unique index caught a duplicate the check above missed.
            ItemSaveResult.Invalid(mapOf(ItemField.SATTL_TAG to "That SATTL tag is already in use. Choose a different tag."))
        }
    }

    /**
     * Retire or un-retire an item (rules 6.8, 6.9). A checked-out item cannot be retired;
     * the admin must check it in first. History is always kept.
     */
    suspend fun setRetired(actor: SessionUser, itemId: Long, retired: Boolean): ItemActionResult {
        requireAdmin(actor)
        return db.withTransaction {
            val item = items.getById(itemId) ?: return@withTransaction ItemActionResult.Blocked("Item not found.")
            if (retired && checkouts.openCheckoutForItem(itemId) != null) {
                return@withTransaction ItemActionResult.Blocked(
                    "${item.sattlTag} is checked out, so it cannot be retired. Check it in first, then retire it."
                )
            }
            items.update(item.copy(isRetired = retired, updatedAt = clock.now()))
            ItemActionResult.Done
        }
    }

    /**
     * Mark an item checkoutable or not (spec 5.5). Rule 6.7: this only blocks future
     * checkouts; an open checkout is left alone.
     */
    suspend fun setCheckoutable(actor: SessionUser, itemId: Long, checkoutable: Boolean): ItemActionResult {
        requireAdmin(actor)
        return db.withTransaction {
            val item = items.getById(itemId) ?: return@withTransaction ItemActionResult.Blocked("Item not found.")
            items.update(item.copy(isCheckoutable = checkoutable, updatedAt = clock.now()))
            ItemActionResult.Done
        }
    }

    /** Rule 6.13: tags are unique ignoring case and surrounding spaces, across all items. */
    private suspend fun duplicateTagError(tag: String, editingId: Long?): String? {
        val other = items.findByTag(tag) ?: return null
        if (other.id == editingId) return null
        val retiredNote = if (other.isRetired) " (a retired item)" else ""
        return "SATTL tag \"${other.sattlTag}\" is already used by ${other.manufacturer} ${other.modelNumber}" +
            "$retiredNote. Choose a different tag."
    }

    private fun requireAdmin(actor: SessionUser) {
        check(actor.isAdmin) { "Only the admin can change items." }
    }
}
