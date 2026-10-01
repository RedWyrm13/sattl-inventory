package org.sattl.inventory.data

import android.app.Application
import android.database.sqlite.SQLiteConstraintException
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.sattl.inventory.data.db.AppDatabase
import org.sattl.inventory.data.entity.Checkout
import org.sattl.inventory.data.entity.Item
import org.sattl.inventory.data.entity.Role
import org.sattl.inventory.data.entity.User
import java.time.LocalDate
import java.util.UUID

/** Spec section 4 and rules 6.1 / 6.13: constraints enforced by the database itself. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class DatabaseConstraintsTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = newTestDb()
    }

    @After
    fun tearDown() = db.close()

    private fun item(tag: String) = Item(
        sattlTag = tag, manufacturer = "Ettus", modelNumber = "B210", serialNumber = null,
        homeLocation = "Room 214", dateIntoInventory = LocalDate.of(2026, 10, 1),
        createdAt = 1L, updatedAt = 1L,
    )

    private fun user(name: String) = User(
        name = name, role = Role.USER, pinHash = "h", pinSalt = "s", mustChangePin = true, createdAt = 1L,
    )

    private fun checkout(itemId: Long, userId: Long) = Checkout(
        itemId = itemId, userId = userId, checkoutDate = LocalDate.of(2026, 10, 1),
        destination = "Field test site", reason = "Demo", createdAt = 1L,
    )

    private inline fun assertRejected(block: () -> Unit) {
        try {
            block()
            fail("Expected the database to reject this write")
        } catch (_: SQLiteConstraintException) {
        }
    }

    @Test
    fun onlyOneOpenCheckoutPerItem() = runTest {
        val itemId = db.itemDao().insert(item("SATTL-001"))
        val userA = db.userDao().insert(user("A"))
        val userB = db.userDao().insert(user("B"))

        val first = db.checkoutDao().insert(checkout(itemId, userA))
        assertRejected { db.checkoutDao().insert(checkout(itemId, userB)) }

        // After check-in, the item can be checked out again.
        val open = db.checkoutDao().openCheckoutForItem(itemId)!!
        assertEquals(first, open.id)
        db.checkoutDao().update(open.copy(checkedInAt = 2L, checkedInByUserId = userA))
        val second = db.checkoutDao().insert(checkout(itemId, userB))
        assertEquals(second, db.checkoutDao().openCheckoutForItem(itemId)!!.id)

        // Re-opening the old, closed checkout would make two open ones: rejected.
        val closed = open.copy(checkedInAt = 2L, checkedInByUserId = userA)
        assertRejected { db.checkoutDao().update(closed.copy(checkedInAt = null, checkedInByUserId = null)) }
    }

    @Test
    fun differentItemsCanEachHaveAnOpenCheckout() = runTest {
        val u = db.userDao().insert(user("A"))
        val i1 = db.itemDao().insert(item("T1"))
        val i2 = db.itemDao().insert(item("T2"))
        db.checkoutDao().insert(checkout(i1, u))
        db.checkoutDao().insert(checkout(i2, u))
        assertNotNull(db.checkoutDao().openCheckoutForItem(i1))
        assertNotNull(db.checkoutDao().openCheckoutForItem(i2))
    }

    @Test
    fun sattlTagIsUniqueIgnoringCase() = runTest {
        db.itemDao().insert(item("SATTL-001"))
        assertRejected { db.itemDao().insert(item("sattl-001")) }
        assertEquals("SATTL-001", db.itemDao().findByTag("Sattl-001")?.sattlTag)
    }

    @Test
    fun userNameIsUniqueIgnoringCase() = runTest {
        db.userDao().insert(user("Jordan"))
        assertRejected { db.userDao().insert(user("JORDAN")) }
        assertEquals("Jordan", db.userDao().findByName("jordan")?.name)
    }

    @Test
    fun everyRowGetsADistinctStoredUuid() = runTest {
        val u = db.userDao().insert(user("A"))
        val i1 = db.itemDao().insert(item("T1"))
        val i2 = db.itemDao().insert(item("T2"))
        val c = db.checkoutDao().insert(checkout(i1, u))

        val uuids = listOf(
            db.userDao().getById(u)!!.uuid,
            db.itemDao().getById(i1)!!.uuid,
            db.itemDao().getById(i2)!!.uuid,
            db.checkoutDao().openCheckoutForItem(i1)!!.also { assertEquals(c, it.id) }.uuid,
        )
        assertEquals(uuids.size, uuids.toSet().size)
        uuids.forEach { UUID.fromString(it) } // throws if not a valid UUID
    }

    @Test
    fun uuidIsUniqueInEachTable() = runTest {
        val shared = UUID.randomUUID().toString()
        db.itemDao().insert(item("T1").copy(uuid = shared))
        assertRejected { db.itemDao().insert(item("T2").copy(uuid = shared)) }

        db.userDao().insert(user("A").copy(uuid = shared))
        assertRejected { db.userDao().insert(user("B").copy(uuid = shared)) }

        val u = db.userDao().getById(1)!!.id
        val i1 = db.itemDao().findByTag("T1")!!.id
        val i2 = db.itemDao().insert(item("T3"))
        db.checkoutDao().insert(checkout(i1, u).copy(uuid = shared))
        assertRejected { db.checkoutDao().insert(checkout(i2, u).copy(uuid = shared)) }
    }

    @Test
    fun checkoutMustReferenceRealItemAndUser() = runTest {
        assertRejected { db.checkoutDao().insert(checkout(itemId = 999, userId = 999)) }
    }

    @Test
    fun datesRoundTripAsIsoText() = runTest {
        val id = db.itemDao().insert(item("T1").copy(dateIntoInventory = LocalDate.of(2025, 2, 28)))
        assertEquals(LocalDate.of(2025, 2, 28), db.itemDao().getById(id)!!.dateIntoInventory)

        db.query("SELECT dateIntoInventory FROM items WHERE id = $id", null).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("2025-02-28", c.getString(0))
        }
    }
}
