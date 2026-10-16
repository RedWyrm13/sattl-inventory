package org.sattl.inventory.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/** One entry in the Admin menu. */
data class AdminMenuEntry(val label: String, val onClick: () -> Unit)

/**
 * The admin's menu on the Inventory screen (spec 5.4). Spec lists Users, Export / Restore,
 * Settings and Exit kiosk; each entry is added when its feature exists.
 */
@Composable
fun AdminMenu(entries: List<AdminMenuEntry>) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.heightIn(min = Dimens.TouchTarget)) {
            Text("Admin")
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            entries.forEach { entry ->
                DropdownMenuItem(
                    text = { Text(entry.label, style = MaterialTheme.typography.titleMedium) },
                    onClick = {
                        open = false
                        entry.onClick()
                    },
                    modifier = Modifier.heightIn(min = Dimens.TouchTarget),
                )
            }
        }
    }
}
