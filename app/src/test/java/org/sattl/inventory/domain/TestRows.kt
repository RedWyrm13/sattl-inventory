package org.sattl.inventory.domain

import org.sattl.inventory.data.entity.Item
import org.sattl.inventory.data.entity.Role
import org.sattl.inventory.data.model.InventoryRow
import org.sattl.inventory.session.SessionUser
import java.time.LocalDate

val admin = SessionUser(id = 1, name = "Pat", role = Role.ADMIN)
val sam = SessionUser(id = 2, name = "Sam", role = Role.USER)
val alex = SessionUser(id = 3, name = "Alex", role = Role.USER)

fun testItem(
    tag: String,
    manufacturer: String = "Ettus",
    model: String = "B210",
    serial: String? = "SN-$tag",
    home: String = "Room 214",
    checkoutable: Boolean = true,
    retired: Boolean = false,
) = Item(
    id = tag.hashCode().toLong(), sattlTag = tag, manufacturer = manufacturer, modelNumber = model,
    serialNumber = serial, homeLocation = home, dateIntoInventory = LocalDate.of(2026, 1, 1),
    isCheckoutable = checkoutable, isRetired = retired, createdAt = 0, updatedAt = 0,
)

/** A row as the DAO returns it; [borrower] non-null means checked out. */
fun testRow(item: Item, borrower: SessionUser? = null, destination: String = "Field site") = InventoryRow(
    item = item,
    openCheckoutId = borrower?.let { 100L },
    borrowerId = borrower?.id,
    borrowerName = borrower?.name,
    checkoutDate = borrower?.let { LocalDate.of(2026, 10, 1) },
    expectedReturnDate = null,
    destination = borrower?.let { destination },
    reason = borrower?.let { "Demo" },
)
