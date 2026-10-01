package org.sattl.inventory.data

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.sattl.inventory.data.db.AppDatabase
import org.sattl.inventory.data.entity.Checkout
import org.sattl.inventory.data.entity.Role
import org.sattl.inventory.data.entity.User
import org.sattl.inventory.data.repo.ItemActionResult
import org.sattl.inventory.data.repo.ItemRepository
import org.sattl.inventory.data.repo.ItemSaveResult
import org.sattl.inventory.domain.ItemField
import org.sattl.inventory.domain.ItemInput
import org.sattl.inventory.domain.ItemStatus
import org.sattl.inventory.session.SessionUser
import java.time.LocalDate

/** Spec 5.4, 5.5, 5.8 and rules 6.7–6.9, 6.13 on a real Room database. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class ItemRepositoryTest {

    private lateinit var db: AppDatabase
    private val clock = FakeClock()
    private lateinit var repo: ItemRepository
    private lateinit var admin: SessionUser
    private lateinit var sam: SessionUser

    @Before
    fun setUp() = runTest {
        db = newTestDb()
        repo = ItemRepository(db, clock)
        admin = addUser("Pat", Role.ADMIN)
        sam = addUser("Sam", Role.USER)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun addUser(name: String, role: Role): SessionUser {
        val id = db.userDao().insert(
            User(name = name, role = role, pinHash = "h", pinSalt = "s", mustChangePin = false, createdAt = 0)
        )
        return SessionUser(id, name, role)
    }

    private fun input(tag: String) = ItemInput(
        sattlTag = tag, manufacturer = "Ettus", modelNumber = "B210",
        homeLocation = "Room 214", dateIntoInventory = LocalDate.of(2026, 10, 1),
    )

    private suspend fun add(tag: String): Long =
        (repo.save(admin, null, input(tag)) as ItemSaveResult.Saved).itemId

    private suspend fun checkOut(itemId: Long, by: SessionUser, at: Long = clock.now()): Long =
        db.checkoutDao().insert(
            Checkout(
                itemId = itemId, userId = by.id, checkoutDate = LocalDate.of(2026, 10, 1),
                destination = "Hangar 3", reason = "Demo", createdAt = at,
            )
        )

    @Test
    fun addTrimsTextAndStoresBlankOptionalsAsNull() = runTest {
        val id = (repo.save(
            admin, null,
            input("  T-1  ").copy(manufacturer = " Dell ", serialNumber = "   ", notes = ""),
        ) as ItemSaveResult.Saved).itemId

        val item = db.itemDao().getById(id)!!
        assertEquals("T-1", item.sattlTag)
        assertEquals("Dell", item.manufacturer)
        assertNull(item.serialNumber)
        assertNull(item.notes)
        assertTrue(item.isCheckoutable)
        assertFalse(item.isRetired)
        assertEquals(clock.now(), item.createdAt)
    }

    @Test
    fun duplicateTagIsRejectedIgnoringCaseAndSpaces() = runTest {
        add("SATTL-001")
        val result = repo.save(admin, null, input("  sattl-001 "))
        assertTrue(result is ItemSaveResult.Invalid)
        val message = (result as ItemSaveResult.Invalid).errors[ItemField.SATTL_TAG]!!
        assertTrue(message, message.contains("SATTL-001"))
    }

    @Test
    fun retiredItemsStillReserveTheirTag() = runTest {
        val id = add("OLD-1")
        repo.setRetired(admin, id, true)
        val result = repo.save(admin, null, input("old-1")) as ItemSaveResult.Invalid
        assertTrue(result.errors[ItemField.SATTL_TAG]!!.contains("retired"))
    }

    @Test
    fun editKeepsOwnTagButCannotTakeAnother() = runTest {
        val a = add("A")
        add("B")
        clock.advance(1000)

        val keep = repo.save(admin, a, input("a").copy(notes = "Updated"))
        assertEquals(ItemSaveResult.Saved(a), keep)
        val edited = db.itemDao().getById(a)!!
        assertEquals("Updated", edited.notes)
        assertEquals(clock.now(), edited.updatedAt)
        assertTrue(edited.createdAt < edited.updatedAt)

        assertTrue(repo.save(admin, a, input("b")) is ItemSaveResult.Invalid)
    }

    @Test
    fun editPreservesUuidAndRetiredFlag() = runTest {
        val id = add("A")
        repo.setRetired(admin, id, true)
        val before = db.itemDao().getById(id)!!
        repo.save(admin, id, input("A2"))
        val after = db.itemDao().getById(id)!!
        assertEquals(before.uuid, after.uuid)
        assertEquals(before.createdAt, after.createdAt)
        assertTrue("editing must not un-retire", after.isRetired)
    }

    @Test
    fun invalidInputReturnsFieldErrors() = runTest {
        val result = repo.save(admin, null, ItemInput()) as ItemSaveResult.Invalid
        assertTrue(ItemField.SATTL_TAG in result.errors)
        assertTrue(ItemField.HOME_LOCATION in result.errors)
    }

    @Test(expected = IllegalStateException::class)
    fun nonAdminCannotAddItems() = runTest {
        repo.save(sam, null, input("X"))
    }

    @Test(expected = IllegalStateException::class)
    fun nonAdminCannotRetire() = runTest {
        repo.setRetired(sam, add("X"), true)
    }

    @Test
    fun checkedOutItemCannotBeRetired() = runTest {
        val id = add("A")
        checkOut(id, sam)
        val result = repo.setRetired(admin, id, true)
        assertTrue(result is ItemActionResult.Blocked)
        assertFalse(db.itemDao().getById(id)!!.isRetired)
    }

    @Test
    fun retireAndUnretire() = runTest {
        val id = add("A")
        assertEquals(ItemActionResult.Done, repo.setRetired(admin, id, true))
        assertEquals(ItemStatus.RETIRED, repo.observeItem(id).first()!!.status)
        assertEquals(ItemActionResult.Done, repo.setRetired(admin, id, false))
        assertEquals(ItemStatus.AVAILABLE, repo.observeItem(id).first()!!.status)
    }

    @Test
    fun notCheckoutableDoesNotAffectOpenCheckout() = runTest {
        val id = add("A")
        checkOut(id, sam)
        repo.setCheckoutable(admin, id, false)

        val row = repo.observeItem(id).first()!!
        assertFalse(row.item.isCheckoutable)
        assertEquals(ItemStatus.CHECKED_OUT, row.status)
        assertEquals("Sam", row.borrowerName)

        // Once returned, it shows as Not checkoutable.
        val open = db.checkoutDao().openCheckoutForItem(id)!!
        db.checkoutDao().update(open.copy(checkedInAt = clock.now(), checkedInByUserId = admin.id))
        assertEquals(ItemStatus.NOT_CHECKOUTABLE, repo.observeItem(id).first()!!.status)
    }

    @Test
    fun inventoryListIsSortedByTagWithCheckoutDetails() = runTest {
        val c = add("c-3")
        add("A-1")
        add("b-2")
        checkOut(c, sam)

        val rows = repo.observeInventory().first()
        assertEquals(listOf("A-1", "b-2", "c-3"), rows.map { it.item.sattlTag })
        val out = rows.last()
        assertEquals(ItemStatus.CHECKED_OUT, out.status)
        assertEquals("Sam", out.borrowerName)
        assertEquals(sam.id, out.borrowerId)
        assertEquals("Hangar 3", out.currentLocation)
        assertEquals("Demo", out.reason)
    }

    @Test
    fun historyIsNewestFirstWithWhoCheckedIn() = runTest {
        val id = add("A")
        val first = checkOut(id, sam, at = 1_000)
        val firstRow = db.checkoutDao().openCheckoutForItem(id)!!
        db.checkoutDao().update(firstRow.copy(checkedInAt = 2_000, checkedInByUserId = admin.id))
        val second = checkOut(id, sam, at = 3_000)

        val history = repo.observeHistory(id).first()
        assertEquals(listOf(second, first), history.map { it.checkout.id })
        assertNull(history[0].checkedInByName)
        assertEquals("Pat", history[1].checkedInByName)
        assertEquals("Sam", history[1].borrowerName)
    }
}
