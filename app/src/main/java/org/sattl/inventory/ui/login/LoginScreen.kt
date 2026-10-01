package org.sattl.inventory.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.sattl.inventory.ui.components.Dimens
import org.sattl.inventory.ui.components.PinDots
import org.sattl.inventory.ui.components.PinPad
import org.sattl.inventory.ui.components.SecureScreen

/**
 * Login screen (spec section 5.2). Name dropdown and messages on the left, PIN pad on the right.
 * The "Admin PIN recovery" link (spec 5.2 step 5) is added with the Recovery screen in milestone 4.
 */
@Composable
fun LoginScreen(viewModel: LoginViewModel, onLoggedIn: (mustChangePin: Boolean) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SecureScreen()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is LoginEvent.LoggedIn -> onLoggedIn(event.mustChangePin)
            }
        }
    }

    Row(
        Modifier
            .fillMaxSize()
            .padding(Dimens.ScreenPadding),
        horizontalArrangement = Arrangement.spacedBy(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Text("SATTL Lab Inventory", style = MaterialTheme.typography.headlineMedium)
            UserDropdown(state, viewModel::onSelectUser)
            if (state.selectedUserId != null) {
                Text("Enter your PIN", style = MaterialTheme.typography.bodyLarge)
                PinDots(state.pinLength)
            }
            val locked = state.lockedSecondsLeft
            when {
                locked != null -> Text(
                    "Too many incorrect PINs. ${state.selectedName} is locked for " +
                        "${formatCountdown(locked)}. Try again when the timer reaches zero.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge,
                )
                state.message != null -> Text(
                    state.message!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge,
                )
                state.busy -> Text("Checking…", style = MaterialTheme.typography.bodyLarge)
            }
        }
        PinPad(
            onDigit = viewModel::onDigit,
            onBackspace = viewModel::onBackspace,
            onSubmit = viewModel::onSubmit,
            enabled = state.selectedUserId != null && state.lockedSecondsLeft == null && !state.busy,
            submitEnabled = state.pinLength > 0,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UserDropdown(state: LoginUiState, onSelect: (Long) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = state.selectedName ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text("Your name") },
            placeholder = { Text("Tap to choose your name") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            textStyle = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            state.users.forEach { user ->
                DropdownMenuItem(
                    text = { Text(user.name, style = MaterialTheme.typography.titleMedium) },
                    onClick = {
                        onSelect(user.id)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
    }
}

/** 272 -> "4:32". */
internal fun formatCountdown(seconds: Long): String = "%d:%02d".format(seconds / 60, seconds % 60)
