package org.sattl.inventory.ui.pin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.sattl.inventory.session.SessionUser
import org.sattl.inventory.ui.components.AppTopBar
import org.sattl.inventory.ui.components.Dimens
import org.sattl.inventory.ui.components.PinDots
import org.sattl.inventory.ui.components.PinPad
import org.sattl.inventory.ui.components.SecureScreen

/** Change PIN screen (spec section 5.3). See [ChangePinViewModel]. */
@Composable
fun ChangePinScreen(
    viewModel: ChangePinViewModel,
    user: SessionUser,
    onLogout: () -> Unit,
    onDone: () -> Unit,
    onCancel: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SecureScreen()

    LaunchedEffect(viewModel) { viewModel.done.collect { onDone() } }

    val forced = state.forced ?: return

    Column(Modifier.fillMaxSize()) {
        AppTopBar(user = user, onLogout = onLogout, onChangePin = null)
        Row(
            Modifier
                .fillMaxSize()
                .padding(Dimens.ScreenPadding),
            horizontalArrangement = Arrangement.spacedBy(48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Text("Change PIN", style = MaterialTheme.typography.headlineMedium)
                if (forced) {
                    Text(
                        "You are using a PIN the admin gave you. Choose your own PIN to continue.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                Text(
                    when (state.step) {
                        ChangePinStep.CURRENT -> "Enter your current PIN."
                        ChangePinStep.NEW -> "Enter a new PIN of 4 to 6 digits."
                        ChangePinStep.CONFIRM -> "Enter the new PIN again to confirm it."
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                PinDots(state.pinLength)
                state.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
                }
                if (state.busy) Text("Saving…", style = MaterialTheme.typography.bodyLarge)
                // A forced change cannot be skipped; the only way out is Log out.
                if (!forced) {
                    OutlinedButton(onClick = onCancel, modifier = Modifier.heightIn(min = Dimens.TouchTarget)) {
                        Text("Cancel")
                    }
                }
            }
            PinPad(
                onDigit = viewModel::onDigit,
                onBackspace = viewModel::onBackspace,
                onSubmit = viewModel::onSubmit,
                enabled = !state.busy,
                submitEnabled = state.pinLength > 0,
            )
        }
    }
}
