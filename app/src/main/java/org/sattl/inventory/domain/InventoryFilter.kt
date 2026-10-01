package org.sattl.inventory.domain

import org.sattl.inventory.data.model.InventoryRow
import org.sattl.inventory.session.SessionUser

/** The filter chips on the Inventory screen (spec section 5.4). */
enum class InventoryFilter(val label: String, val adminOnly: Boolean = false) {
    ALL("All"),
    AVAILABLE("Available"),
    CHECKED_OUT("Checked out"),
    MY_CHECKOUTS("My checkouts"),
    NOT_CHECKOUTABLE("Not checkoutable"),
    RETIRED("Retired", adminOnly = true);

    companion object {
        /** The chips this user may see: Retired is admin-only (spec 5.4, rule 6.9). */
        fun visibleTo(user: SessionUser): List<InventoryFilter> = entries.filter { !it.adminOnly || user.isAdmin }
    }
}

/**
 * Filtering and search for the Inventory list (spec section 5.4). Pure functions, so the same
 * logic can drive "Export this view" (spec 7.2) and is easy to test.
 *
 * With ~100 items, filtering in memory is instant and much simpler than building SQL.
 */
object InventoryQuery {

    /** Rows matching [filter] and [search], in the input order (the DAO sorts by SATTL tag). */
    fun apply(rows: List<InventoryRow>, filter: InventoryFilter, search: String, user: SessionUser): List<InventoryRow> =
        rows.filter { matchesFilter(it, filter, user) && matchesSearch(it, search) }

    fun matchesFilter(row: InventoryRow, filter: InventoryFilter, user: SessionUser): Boolean {
        val status = row.status
        return when (filter) {
            // Retired items appear only under the admin's Retired filter (rule 6.9).
            InventoryFilter.ALL -> status != ItemStatus.RETIRED
            InventoryFilter.AVAILABLE -> status == ItemStatus.AVAILABLE
            InventoryFilter.CHECKED_OUT -> status == ItemStatus.CHECKED_OUT
            InventoryFilter.MY_CHECKOUTS -> status == ItemStatus.CHECKED_OUT && row.borrowerId == user.id
            InventoryFilter.NOT_CHECKOUTABLE -> status == ItemStatus.NOT_CHECKOUTABLE
            InventoryFilter.RETIRED -> user.isAdmin && status == ItemStatus.RETIRED
        }
    }

    /**
     * Case-insensitive "contains" match on SATTL tag, manufacturer, model number, serial
     * number, home location and current location (spec 5.4). Matching both locations means
     * searching "Room 214" also finds items from that room that are currently checked out.
     * A blank search matches everything.
     */
    fun matchesSearch(row: InventoryRow, search: String): Boolean {
        val q = search.trim()
        if (q.isEmpty()) return true
        val item = row.item
        return listOfNotNull(
            item.sattlTag, item.manufacturer, item.modelNumber, item.serialNumber,
            item.homeLocation, row.currentLocation,
        ).any { it.contains(q, ignoreCase = true) }
    }
}
