package org.sattl.inventory.ui.inventory

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.sattl.inventory.session.SessionUser
import org.sattl.inventory.ui.components.AppTopBar

/**
 * Inventory home screen (spec section 5.4).
 * Milestone 1 placeholder: the item list, search and filters arrive in milestone 2.
 */
@Composable
fun InventoryScreen(user: SessionUser, onLogout: () -> Unit, onChangePin: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        AppTopBar(user = user, onLogout = onLogout, onChangePin = onChangePin)
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "Welcome, ${user.name}. The inventory list is coming in milestone 2.",
                style = MaterialTheme.typography.titleLarge,
            )
        }
    }
}
