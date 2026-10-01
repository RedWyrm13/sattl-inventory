package org.sattl.inventory.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.sattl.inventory.session.SessionUser

/**
 * Spec section 5: every logged-in screen has a persistent top bar with the logged-in name
 * and a large Log out button. Change PIN is reachable from here at any time (spec 5.3).
 *
 * [actions] lets a screen add its own buttons (e.g. the Admin menu in milestone 2).
 */
@Composable
fun AppTopBar(
    user: SessionUser,
    onLogout: () -> Unit,
    onChangePin: (() -> Unit)?,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = if (user.isAdmin) "${user.name} (Admin)" else user.name,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            actions()
            if (onChangePin != null) {
                OutlinedButton(onClick = onChangePin, modifier = Modifier.heightIn(min = Dimens.TouchTarget)) {
                    Text("Change PIN")
                }
            }
            Button(onClick = onLogout, modifier = Modifier.heightIn(min = 64.dp)) {
                Text("Log out", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
