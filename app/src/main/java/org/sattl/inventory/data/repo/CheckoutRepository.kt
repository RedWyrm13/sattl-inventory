package org.sattl.inventory.data.repo

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import org.sattl.inventory.data.db.AppDatabase
import org.sattl.inventory.data.entity.Checkout
import org.sattl.inventory.domain.CheckoutField
import org.sattl.inventory.domain.CheckoutInput
import org.sattl.inventory.domain.CheckoutValidator
import org.sattl.inventory.domain.ItemStatus
import org.sattl.inventory.session.SessionUser
import org.sattl.inventory.util.Clock
import org.sattl.inventory.util.today

sealed interface CheckoutResult {
    data class Done(val checkoutId: Long) : CheckoutResult
    data class Invalid(val errors: Map<CheckoutField, String>) : CheckoutResult
    /** The item cannot be checked out right now; [message] says why and what to do. */
    data class Blocked(val message: String) : CheckoutResult
}

sealed interface CheckInResult {
    /** Checked in. [homeLocation] is where the item should now go back to (rule 6.5). */
    data class Done(val sattlTag: String, val homeLocation: String) : CheckInResult
    data class Blocked(val message: String) : CheckInResult
}

/**
 * Checking items out and in (spec 5.6, 5.7, section 6). Every rule is checked again inside
 * the transaction that writes, so a stale screen can never break a rule (rule 6.15).
 */
class CheckoutRepository(
    private val db: AppDatabase,
    private val clock: Clock = Clock.SYSTEM,
) {
    private val items = db.itemDao()
    private val checkouts = db.checkoutDao()

    /**
     * Checks [itemId] out to [actor]. Rule 6.3: the borrower is always the logged-in user,
     * so there is no borrower parameter. Rule 6.2: one item per checkout.
     */
    suspend fun checkOut(actor: SessionUser, itemId: Long, input: CheckoutInput): CheckoutResult {
        val i = input.trimmed()
        val errors = CheckoutValidator.validate(i, clock.today())
        if (errors.isNotEmpty()) return CheckoutResult.Invalid(errors)

        return try {
            db.withTransaction {
                val item = items.getById(itemId)
                    ?: return@withTransaction CheckoutResult.Blocked("This item no longer exists.")
                val open = checkouts.openCheckoutForItem(itemId)
                // Rule 6.1: checking out an item that is not Available is blocked.
                when (ItemStatus.of(item, open != null)) {
                    ItemStatus.AVAILABLE -> Unit
                    ItemStatus.CHECKED_OUT -> return@withTransaction alreadyOut()
                    ItemStatus.NOT_CHECKOUTABLE -> return@withTransaction CheckoutResult.Blocked(
                        "${item.sattlTag} is marked as not checkoutable. Ask the admin if you need it."
                    )
                    ItemStatus.RETIRED -> return@withTransaction CheckoutResult.Blocked(
                        "${item.sattlTag} has been retired and cannot be checked out."
                    )
                }
                val id = checkouts.insert(
                    Checkout(
                        itemId = itemId,
                        userId = actor.id,
                        checkoutDate = i.checkoutDate!!,
                        expectedReturnDate = i.expectedReturnDate,
                        destination = i.destination,
                        reason = i.reason,
                        createdAt = clock.now(),
                    )
                )
                CheckoutResult.Done(id)
            }
        } catch (_: SQLiteConstraintException) {
            // Backstop: the one-open-checkout trigger (DbConstraints) rejected the insert.
            alreadyOut()
        }
    }

    /**
     * Closes checkout [checkoutId] (spec 5.7). Rule 6.4: only the borrower or the admin may do
     * this, and the check-in records who did it. Rule 6.5: the item's location reverts to its
     * home location automatically, because current location is derived from open checkouts.
     */
    suspend fun checkIn(actor: SessionUser, checkoutId: Long): CheckInResult = db.withTransaction {
        val checkout = checkouts.getById(checkoutId)
        if (checkout == null || checkout.checkedInAt != null) {
            return@withTransaction CheckInResult.Blocked("This item has already been checked in.")
        }
        if (checkout.userId != actor.id && !actor.isAdmin) {
            return@withTransaction CheckInResult.Blocked(
                "Only the person who checked this item out, or the admin, can check it in."
            )
        }
        checkouts.update(checkout.copy(checkedInAt = clock.now(), checkedInByUserId = actor.id))
        val item = items.getById(checkout.itemId)!!
        CheckInResult.Done(item.sattlTag, item.homeLocation)
    }

    private fun alreadyOut() =
        CheckoutResult.Blocked("This item has just been checked out by someone else, so it is no longer available.")
}
