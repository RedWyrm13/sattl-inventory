package org.sattl.inventory.ui.users

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.sattl.inventory.data.entity.Role
import org.sattl.inventory.data.model.UserRow
import org.sattl.inventory.session.SessionUser
import org.sattl.inventory.ui.components.AppTextField
import org.sattl.inventory.ui.components.AppTopBar
import org.sattl.inventory.ui.components.ConfirmDialog
import org.sattl.inventory.ui.components.Dimens
import org.sattl.inventory.ui.components.reportsUserActivity
import org.sattl.inventory.util.Formats

/** Manage users (spec 5.9), admin only. */
@Composable
fun ManageUsersScreen(
    viewModel: ManageUsersViewModel,
    user: SessionUser,
    onLogout: () -> Unit,
    onChangePin: () -> Unit,
    onBack: () -> Unit,
    onAddUser: () -> Unit,
    onResetPin: (userId: Long) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDeactivate by remember { mutableStateOf<UserRow?>(null) }
    var confirmReset by remember { mutableStateOf<UserRow?>(null) }

    Column(Modifier.fillMaxSize()) {
        AppTopBar(user = user, onLogout = onLogout, onChangePin = onChangePin)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.ScreenPadding, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedButton(onClick = onBack, modifier = Modifier.heightIn(min = Dimens.TouchTarget)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                Text("Inventory", Modifier.padding(start = 8.dp))
            }
            Text("Users", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            Button(onClick = onAddUser, modifier = Modifier.heightIn(min = Dimens.TouchTarget)) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text("Add user", Modifier.padding(start = 8.dp))
            }
        }
        state.message?.let { msg ->
            Row(Modifier.padding(horizontal = Dimens.ScreenPadding), verticalAlignment = Alignment.CenterVertically) {
                Text(msg, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = viewModel::dismissMessage, modifier = Modifier.heightIn(min = Dimens.TouchTarget)) { Text("OK") }
            }
        }
        if (state.loading) return@Column

        Column(Modifier.padding(horizontal = Dimens.ScreenPadding)) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Header("Name", ColName)
                Header("Role", ColRole)
                Header("Status", ColStatus)
                Header("Holding", ColHolding)
                Header("", ColActions)
            }
            HorizontalDivider(thickness = 2.dp)
            LazyColumn(Modifier.fillMaxSize()) {
                items(state.rows, key = { it.user.id }) { row ->
                    UserLine(
                        row = row,
                        now = state.now,
                        busy = state.busy,
                        onRename = { viewModel.startRename(row) },
                        onResetPin = { confirmReset = row },
                        // Rule 6.10: if they hold items, explain straight away instead of confirming.
                        onDeactivate = { if (row.openCheckouts > 0) viewModel.setActive(row, false) else confirmDeactivate = row },
                        onReactivate = { viewModel.setActive(row, true) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    state.rename?.let { r ->
        AlertDialog(
            onDismissRequest = viewModel::cancelRename,
            title = { Text("Edit name") },
            text = {
                Box(Modifier.reportsUserActivity()) {
                    AppTextField(
                        r.text, viewModel::onRenameText, "Name", required = true, error = r.error,
                        capitalization = KeyboardCapitalization.Words, modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(onClick = viewModel::saveRename, enabled = !state.busy, modifier = Modifier.heightIn(min = Dimens.TouchTarget)) {
                    Text("Save")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = viewModel::cancelRename, modifier = Modifier.heightIn(min = Dimens.TouchTarget)) { Text("Cancel") }
            },
        )
    }

    // Spec section 10: Reset PIN and Deactivate both need confirmation.
    confirmReset?.let { row ->
        ConfirmDialog(
            title = "Reset ${row.user.name}'s PIN?",
            message = "You will type a new default PIN. ${row.user.name}'s current PIN stops working, " +
                "any lockout is cleared, and they must choose their own PIN at the next login.",
            confirmLabel = "Reset PIN",
            onConfirm = { confirmReset = null; onResetPin(row.user.id) },
            onDismiss = { confirmReset = null },
        )
    }
    confirmDeactivate?.let { row ->
        ConfirmDialog(
            title = "Deactivate ${row.user.name}?",
            message = "${row.user.name} will disappear from the login list and cannot log in. " +
                "Their checkout history is kept, and you can reactivate them at any time.",
            confirmLabel = "Deactivate",
            onConfirm = { confirmDeactivate = null; viewModel.setActive(row, false) },
            onDismiss = { confirmDeactivate = null },
        )
    }
}

private const val ColName = 1.4f
private const val ColRole = 0.6f
private const val ColStatus = 1.3f
private const val ColHolding = 0.8f
private const val ColActions = 2.6f

@Composable
private fun RowScope.Header(text: String, weight: Float) {
    Text(text, Modifier.weight(weight), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
}

@Composable
private fun UserLine(
    row: UserRow,
    now: Long,
    busy: Boolean,
    onRename: () -> Unit,
    onResetPin: () -> Unit,
    onDeactivate: () -> Unit,
    onReactivate: () -> Unit,
) {
    val u = row.user
    val isAdmin = u.role == Role.ADMIN
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(u.name, Modifier.weight(ColName), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        Text(if (isAdmin) "Admin" else "User", Modifier.weight(ColRole), style = MaterialTheme.typography.bodyLarge)
        Column(Modifier.weight(ColStatus)) {
            Text(if (u.isActive) "Active" else "Deactivated", style = MaterialTheme.typography.bodyLarge)
            val lockedUntil = u.lockedUntil
            if (lockedUntil != null && lockedUntil > now) {
                Text("Locked until ${Formats.time(lockedUntil)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            } else if (u.mustChangePin && u.isActive) {
                Text("Has not chosen own PIN yet", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(
            when (row.openCheckouts) {
                0 -> "—"
                1 -> "1 item"
                else -> "${row.openCheckouts} items"
            },
            Modifier.weight(ColHolding),
            style = MaterialTheme.typography.bodyLarge,
        )
        // Left-aligned so each button sits in the same column on every row.
        Row(Modifier.weight(ColActions), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onRename, enabled = !busy, modifier = Modifier.heightIn(min = Dimens.TouchTarget)) { Text("Edit name") }
            // The admin changes their own PIN with Change PIN, and can never be deactivated (rule 6.12).
            if (!isAdmin) {
                OutlinedButton(onClick = onResetPin, enabled = !busy, modifier = Modifier.heightIn(min = Dimens.TouchTarget)) {
                    Text("Reset PIN")
                }
                if (u.isActive) {
                    OutlinedButton(onClick = onDeactivate, enabled = !busy, modifier = Modifier.heightIn(min = Dimens.TouchTarget)) {
                        Text("Deactivate")
                    }
                } else {
                    Button(onClick = onReactivate, enabled = !busy, modifier = Modifier.heightIn(min = Dimens.TouchTarget)) {
                        Text("Reactivate")
                    }
                }
            }
        }
    }
}
