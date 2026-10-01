package org.sattl.inventory.ui.item

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.sattl.inventory.data.model.CheckoutHistoryRow
import org.sattl.inventory.data.model.InventoryRow
import org.sattl.inventory.domain.ItemStatus
import org.sattl.inventory.session.SessionUser
import org.sattl.inventory.ui.components.AppTopBar
import org.sattl.inventory.ui.components.ConfirmDialog
import org.sattl.inventory.ui.components.Dimens
import org.sattl.inventory.ui.components.StatusBadge
import org.sattl.inventory.util.Formats

/** Item detail (spec 5.5): fields on the left, checkout info and admin history on the right. */
@Composable
fun ItemDetailScreen(
    viewModel: ItemDetailViewModel,
    user: SessionUser,
    onLogout: () -> Unit,
    onChangePin: () -> Unit,
    onBack: () -> Unit,
    onEdit: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmRetire by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        AppTopBar(user = user, onLogout = onLogout, onChangePin = onChangePin)
        if (state.loading) return@Column
        val row = state.row
        if (row == null) {
            Column(Modifier.padding(Dimens.ScreenPadding), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                BackButton(onBack)
                Text("This item is not available.", style = MaterialTheme.typography.titleLarge)
            }
            return@Column
        }
        val item = row.item

        // Title row with Back and the admin actions.
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.ScreenPadding, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BackButton(onBack)
            Text(
                item.sattlTag,
                style = MaterialTheme.typography.headlineMedium,
                textDecoration = if (item.isRetired) TextDecoration.LineThrough else null,
            )
            StatusBadge(row.status)
            Spacer(Modifier.weight(1f))
            if (user.isAdmin) {
                OutlinedButton(onClick = onEdit, modifier = Modifier.heightIn(min = Dimens.TouchTarget)) {
                    Icon(Icons.Default.Edit, contentDescription = null)
                    Text("Edit", Modifier.padding(start = 8.dp))
                }
                if (item.isRetired) {
                    Button(
                        onClick = { viewModel.setRetired(false) },
                        enabled = !state.busy,
                        modifier = Modifier.heightIn(min = Dimens.TouchTarget),
                    ) { Text("Un-retire") }
                } else {
                    // Rule 6.8: a checked-out item cannot be retired (repository also enforces this).
                    OutlinedButton(
                        onClick = { confirmRetire = true },
                        enabled = !state.busy && !row.isCheckedOut,
                        modifier = Modifier.heightIn(min = Dimens.TouchTarget),
                    ) { Text("Retire") }
                }
            }
        }
        state.message?.let { msg ->
            Row(
                Modifier.padding(horizontal = Dimens.ScreenPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(msg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = viewModel::dismissMessage) { Text("OK") }
            }
        }

        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = Dimens.ScreenPadding, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Card(Modifier.weight(1f)) {
                Column(
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Field("SATTL tag", item.sattlTag)
                    Field("Manufacturer", item.manufacturer)
                    Field("Model number", item.modelNumber)
                    Field("Serial number", item.serialNumber ?: "None")
                    Field("Home location", item.homeLocation)
                    Field("Current location", row.currentLocation)
                    Field("Date into inventory", Formats.date(item.dateIntoInventory))
                    Field("Notes", item.notes ?: "None")
                    if (!user.isAdmin) Field("Can be checked out", if (item.isCheckoutable) "Yes" else "No")
                }
            }
            Card(Modifier.weight(1f)) {
                Column(
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Admin control at the top so it is visible without scrolling.
                    if (user.isAdmin) {
                        CheckoutableToggle(item.isCheckoutable, row.isCheckedOut, !state.busy, viewModel::setCheckoutable)
                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    }
                    CurrentCheckout(row)
                    if (user.isAdmin) {
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        History(state.history)
                    }
                }
            }
        }
    }

    if (confirmRetire) {
        val tag = state.row?.item?.sattlTag.orEmpty()
        ConfirmDialog(
            title = "Retire $tag?",
            message = "Retired items are hidden from users and cannot be checked out. Their history is kept, " +
                "and you can un-retire the item later from the Retired filter.",
            confirmLabel = "Retire",
            onConfirm = {
                confirmRetire = false
                viewModel.setRetired(true)
            },
            onDismiss = { confirmRetire = false },
        )
    }
}

@Composable
private fun BackButton(onBack: () -> Unit) {
    OutlinedButton(onClick = onBack, modifier = Modifier.heightIn(min = Dimens.TouchTarget)) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
        Text("Inventory", Modifier.padding(start = 8.dp))
    }
}

/** One label/value line: label on the left, value on the right, to keep the list short. */
@Composable
private fun Field(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            label,
            Modifier.weight(0.4f),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, Modifier.weight(0.6f), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun CheckoutableToggle(checkoutable: Boolean, checkedOut: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Can be checked out", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            // Rule 6.7: explain that the current checkout is unaffected.
            if (!checkoutable && checkedOut) {
                Text(
                    "The current checkout is not affected. Future checkouts are blocked.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Switch(checked = checkoutable, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun ColumnScope.CurrentCheckout(row: InventoryRow) {
    Text("Current checkout", style = MaterialTheme.typography.titleLarge)
    if (row.status == ItemStatus.CHECKED_OUT) {
        Field("Checked out by", row.borrowerName.orEmpty())
        row.checkoutDate?.let { Field("Checkout date", Formats.date(it)) }
        Field("Expected return", row.expectedReturnDate?.let(Formats::date) ?: "Not given")
        Field("Destination", row.destination.orEmpty())
        Field("Reason", row.reason.orEmpty())
    } else {
        Text(
            "Not checked out. It should be at ${row.item.homeLocation}.",
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

/** Admin-only full checkout history, newest first, including who checked each one in (spec 5.5). */
@Composable
private fun ColumnScope.History(history: List<CheckoutHistoryRow>) {
    Text("Checkout history", style = MaterialTheme.typography.titleLarge)
    if (history.isEmpty()) {
        Text("This item has never been checked out.", style = MaterialTheme.typography.bodyLarge)
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        HistoryCell("Borrower", bold = true)
        HistoryCell("Out", bold = true)
        HistoryCell("Destination / reason", weight = 2f, bold = true)
        HistoryCell("Returned", bold = true)
    }
    HorizontalDivider()
    history.forEach { h ->
        val c = h.checkout
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HistoryCell(h.borrowerName)
            HistoryCell(Formats.date(c.checkoutDate))
            HistoryCell("${c.destination}\n${c.reason}", weight = 2f)
            HistoryCell(
                if (c.checkedInAt == null) "Still out"
                else "${Formats.dateTime(c.checkedInAt)}\nby ${h.checkedInByName.orEmpty()}"
            )
        }
        HorizontalDivider()
    }
}

@Composable
private fun RowScope.HistoryCell(text: String, weight: Float = 1f, bold: Boolean = false) {
    Text(
        text,
        Modifier.weight(weight),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = if (bold) FontWeight.Bold else null,
    )
}
