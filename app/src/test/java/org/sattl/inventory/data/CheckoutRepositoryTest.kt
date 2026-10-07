package org.sattl.inventory.data

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.sattl.inventory.data.db.AppDatabase
import org.sattl.inventory.data.entity.Role
import org.sattl.inventory.data.entity.User
import org.sattl.inventory.data.repo.CheckInResult
import org.sattl.inventory.data.repo.CheckoutRepository
import org.sattl.inventory.data.repo.CheckoutResult
import org.sattl.inventory.data.repo.ItemActionResult
import org.sattl.inventory.data.repo.ItemRepository
import org.sattl.inventory.data.repo.ItemSaveResult
import org.sattl.inventory.domain.CheckoutField
import org.sattl.inventory.domain.CheckoutInput
import org.sattl.inventory.domain.ItemInput
import org.sattl.inventory.domain.ItemStatus
import org.sattl.inventory.session.SessionUser
import org.sattl.inventory.util.today
import java.time.LocalDate

/** Spec 5.6, 5.7 and section 6 rules 6.1–6.8 on a real Room database. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class CheckoutRepositoryTest {

    private lateinit var db: AppDatabase
    private val clock = FakeClock()
    private lateinit var items: ItemRepository
    private lateinit var repo: CheckoutRepository
    private lateinit var admin: SessionUser
    private lateinit var sam: SessionUser
    private lateinit var alex: SessionUser
    private var itemId = 0L

    @Before
    fun setUp() = runTest {
        db = newTestDb()
        items = ItemRepository(db, clock)
        repo = CheckoutRepository(db, clock)
        admin = addUser("Pat", Role.ADMIN)
        sam = addUser("Sam", Role.USER)
        alex = addUser("Alex", Role.USER)
        itemId = (items.save(
            admin, null,
            ItemInput(
                sattlTag = "SDR-1", manufacturer = "Ettus", modelNumber = "B210",
                homeLocation = "Room 214", dateIntoInventory = LocalDate.of(2026, 1, 1),
            ),
        ) as ItemSaveResult.Saved).itemId
    }

    @After
    fun tearDown() = db.close()

    private suspend fun addUser(name: String, role: Role): SessionUser {
        val id = db.userDao().insert(
            User(name = name, role = role, pinHash = "h", pinSalt = "s", mustChangePin = false, createdAt = 0)
        )
        return SessionUser(id, name, role)
    }

    private fun form(destination: String = "Hangar 3", reason: String = "Field test") =
        CheckoutInput(checkoutDate = clock.today(), destination = destination, reason = reason)

    private suspend fun checkOut(by: SessionUser): Long =
        (repo.checkOut(by, itemId, form()) as CheckoutResult.Done).checkoutId

    private suspend fun row() = items.observeItem(itemId).first()!!

    @Test
    fun checkoutRecordsLoggedInUserAsBorrowerWithTrimmedText() = runTest {
        val result = repo.checkOut(sam, itemId, form(destination = "  Hangar 3 ", reason = " Demo  "))
        val id = (result as CheckoutResult.Done).checkoutId

        val c = db.checkoutDao().getById(id)!!
        assertEquals(sam.id, c.userId)
        assertEquals("Hangar 3", c.destination)
        assertEquals("Demo", c.reason)
        assertEquals(clock.now(), c.createdAt)
        assertNull(c.checkedInAt)

        val r = row()
        assertEquals(ItemStatus.CHECKED_OUT, r.status)
        assertEquals("Sam", r.borrowerName)
        assertEquals("Hangar 3", r.currentLocation)
    }

    @Test
    fun invalidFormIsRejectedWithFieldErrors() = runTest {
        val result = repo.checkOut(sam, itemId, form(destination = "", reason = "")) as CheckoutResult.Invalid
        assertEquals(setOf(CheckoutField.DESTINATION, CheckoutField.REASON), result.errors.keys)
        assertEquals(ItemStatus.AVAILABLE, row().status)
    }

    @Test
    fun cannotCheckOutAnItemThatIsAlreadyOut() = runTest {
        checkOut(sam)
        assertTrue(repo.checkOut(alex, itemId, form()) is CheckoutResult.Blocked)
        assertTrue(repo.checkOut(sam, itemId, form()) is CheckoutResult.Blocked)
        assertEquals("Sam", row().borrowerName)
    }

    @Test
    fun notCheckoutableItemCannotBeCheckedOutByAnyone() = runTest {
        items.setCheckoutable(admin, itemId, false)
        assertTrue(repo.checkOut(sam, itemId, form()) is CheckoutResult.Blocked)
        assertTrue(repo.checkOut(admin, itemId, form()) is CheckoutResult.Blocked)
    }

    @Test
    fun retiredItemCannotBeCheckedOut() = runTest {
        items.setRetired(admin, itemId, true)
        assertTrue(repo.checkOut(admin, itemId, form()) is CheckoutResult.Blocked)
    }

    @Test
    fun borrowerCanCheckIn() = runTest {
        val id = checkOut(sam)
        clock.advance(60_000)
        val result = repo.checkIn(sam, id)
        assertEquals(CheckInResult.Done("SDR-1", "Room 214"), result)

        val c = db.checkoutDao().getById(id)!!
        assertEquals(clock.now(), c.checkedInAt)
        assertEquals(sam.id, c.checkedInByUserId)
    }

    @Test
    fun otherUserCannotCheckInButAdminCan() = runTest {
        val id = checkOut(sam)
        assertTrue(repo.checkIn(alex, id) is CheckInResult.Blocked)
        assertNull(db.checkoutDao().getById(id)!!.checkedInAt)

        assertTrue(repo.checkIn(admin, id) is CheckInResult.Done)
        val c = db.checkoutDao().getById(id)!!
        assertNotNull(c.checkedInAt)
        assertEquals("check-in records who did it", admin.id, c.checkedInByUserId)
        assertEquals("borrower is unchanged", sam.id, c.userId)
    }

    @Test
    fun checkInReturnsItemToHomeLocationAndAvailability() = runTest {
        val id = checkOut(sam)
        repo.checkIn(sam, id)
        val r = row()
        assertEquals(ItemStatus.AVAILABLE, r.status)
        assertEquals("Room 214", r.currentLocation)
        assertNull(r.borrowerName)
    }

    @Test
    fun cannotCheckInTwice() = runTest {
        val id = checkOut(sam)
        repo.checkIn(sam, id)
        assertTrue(repo.checkIn(sam, id) is CheckInResult.Blocked)
        assertTrue(repo.checkIn(admin, id) is CheckInResult.Blocked)
    }

    @Test
    fun itemCanBeCheckedOutAgainAfterReturnAndHistoryKeepsBoth() = runTest {
        val first = checkOut(sam)
        clock.advance(1_000)
        repo.checkIn(admin, first)
        clock.advance(1_000)
        val second = checkOut(alex)

        val history = items.observeHistory(itemId).first()
        assertEquals(listOf(second, first), history.map { it.checkout.id })
        assertEquals("Pat", history[1].checkedInByName)
    }

    @Test
    fun notCheckoutableDoesNotCloseAnOpenCheckout() = runTest {
        val id = checkOut(sam)
        items.setCheckoutable(admin, itemId, false)
        assertEquals(ItemStatus.CHECKED_OUT, row().status)
        // The borrower can still return it.
        assertTrue(repo.checkIn(sam, id) is CheckInResult.Done)
        assertEquals(ItemStatus.NOT_CHECKOUTABLE, row().status)
    }

    @Test
    fun checkedOutItemMustBeCheckedInBeforeRetiring() = runTest {
        val id = checkOut(sam)
        assertTrue(items.setRetired(admin, itemId, true) is ItemActionResult.Blocked)
        repo.checkIn(admin, id)
        assertEquals(ItemActionResult.Done, items.setRetired(admin, itemId, true))
    }
}
