package org.sattl.inventory.ui.inventory

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.sattl.inventory.data.model.InventoryRow
import org.sattl.inventory.domain.InventoryFilter
import org.sattl.inventory.domain.ItemStatus
import org.sattl.inventory.session.SessionUser
import org.sattl.inventory.ui.components.AppTextField
import org.sattl.inventory.ui.components.AppTopBar
import org.sattl.inventory.ui.components.Dimens
import org.sattl.inventory.ui.components.StatusBadge
import org.sattl.inventory.util.Formats

/**
 * Inventory home screen (spec 5.4).
 *
 * Still to come: "Export this view" and the Admin menu (Users, Export / Restore, Settings,
 * Exit kiosk) arrive with those features in milestones 4–6.
 */
@Composable
fun InventoryScreen(
    viewModel: InventoryViewModel,
    user: SessionUser,
    onLogout: () -> Unit,
    onChangePin: () -> Unit,
    onOpenItem: (Long) -> Unit,
    onAddItem: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        AppTopBar(user = user, onLogout = onLogout, onChangePin = onChangePin)
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = Dimens.ScreenPadding, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                AppTextField(
                    value = state.search,
                    onValueChange = viewModel::onSearchChange,
                    label = "Search",
                    placeholder = "Tag, manufacturer, model, serial or location",
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = if (state.search.isNotEmpty()) {
                        {
                            IconButton(onClick = { viewModel.onSearchChange("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear search")
                            }
                        }
                    } else null,
                    modifier = Modifier.weight(1f),
                )
                if (user.isAdmin) {
                    Button(onClick = onAddItem, modifier = Modifier.heightIn(min = Dimens.TouchTarget)) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Text("Add item", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }

            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                state.filters.forEach { f ->
                    FilterChip(
                        selected = f == state.filter,
                        onClick = { viewModel.onFilterChange(f) },
                        label = {
                            Text("${f.label} (${state.counts[f] ?: 0})", style = MaterialTheme.typography.bodyLarge)
                        },
                        // A check mark as well as colour marks the selected filter (spec section 10).
                        leadingIcon = if (f == state.filter) {
                            { Icon(Icons.Default.Check, contentDescription = null) }
                        } else null,
                        modifier = Modifier.heightIn(min = Dimens.TouchTarget),
                    )
                }
            }

            val showCheckout = state.filter == InventoryFilter.CHECKED_OUT || state.filter == InventoryFilter.MY_CHECKOUTS
            HeaderRow()
            HorizontalDivider(thickness = 2.dp)
            when {
                state.loading -> Unit
                state.rows.isEmpty() -> EmptyMessage(
                    when {
                        state.inventoryEmpty && user.isAdmin -> "No items yet. Tap \"Add item\" to add the first one."
                        state.inventoryEmpty -> "No items yet. The admin adds items."
                        state.search.isNotBlank() -> "No items match \"${state.search.trim()}\" in ${state.filter.label}."
                        else -> "No items in ${state.filter.label}."
                    }
                )
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(state.rows, key = { it.item.id }) { row ->
                        ItemRow(row, showCheckout, onClick = { onOpenItem(row.item.id) })
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

// Column widths shared by the header and rows so they line up.
private val ColTag = 1.1f
private val ColManufacturer = 1.2f
private val ColModel = 1.2f
private val ColSerial = 1.2f
private val ColLocation = 1.5f
private val ColStatus = 1.1f

@Composable
private fun HeaderRow() {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Header("SATTL tag", ColTag)
        Header("Manufacturer", ColManufacturer)
        Header("Model", ColModel)
        Header("Serial", ColSerial)
        Header("Current location", ColLocation)
        Header("Status", ColStatus)
    }
}

@Composable
private fun RowScope.Header(text: String, weight: Float) {
    Text(text, Modifier.weight(weight), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
}

@Composable
private fun ItemRow(row: InventoryRow, showCheckout: Boolean, onClick: () -> Unit) {
    val item = row.item
    val retired = row.status == ItemStatus.RETIRED
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = Dimens.TouchTarget)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Cell(
                item.sattlTag, ColTag, bold = true,
                // Spec section 10: retired items show their tag struck through.
                decoration = if (retired) TextDecoration.LineThrough else null,
            )
            Cell(item.manufacturer, ColManufacturer)
            Cell(item.modelNumber, ColModel)
            Cell(item.serialNumber ?: "—", ColSerial)
            Cell(row.currentLocation, ColLocation)
            Box(Modifier.weight(ColStatus)) { StatusBadge(row.status) }
        }
        // Spec 5.4: in the Checked out filter, rows also show who has it, where, since when and why.
        if (showCheckout && row.isCheckedOut) {
            Text(
                buildString {
                    append("With ${row.borrowerName}")
                    row.checkoutDate?.let { append(" since ${Formats.date(it)}") }
                    append("  ·  To: ${row.destination}")
                    append("  ·  Reason: ${row.reason}")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun RowScope.Cell(text: String, weight: Float, bold: Boolean = false, decoration: TextDecoration? = null) {
    Text(
        text,
        Modifier.weight(weight),
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = if (bold) FontWeight.SemiBold else null,
        textDecoration = decoration,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun EmptyMessage(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.TopCenter) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
