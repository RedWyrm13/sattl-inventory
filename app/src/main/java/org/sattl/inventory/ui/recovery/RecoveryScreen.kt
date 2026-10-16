package org.sattl.inventory.ui.recovery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.sattl.inventory.ui.components.AppTextField
import org.sattl.inventory.ui.components.Dimens
import org.sattl.inventory.ui.components.PinEntryPanel
import org.sattl.inventory.ui.components.SecureScreen

/** Admin PIN recovery (spec 5.10). Reached from the small link on the Login screen. */
@Composable
fun RecoveryScreen(viewModel: RecoveryViewModel, onExit: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Recovery codes and PINs are on screen (spec section 8).
    SecureScreen()

    when (state.step) {
        RecoveryStep.CODE -> Page {
            Text("Admin PIN recovery", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Enter the 12-character recovery code you wrote down when the tablet was set up " +
                    "(or the newest one, if you have recovered before).",
                style = MaterialTheme.typography.bodyLarge,
            )
            AppTextField(
                state.codeText, viewModel::onCodeChange, "Recovery code", required = true, error = state.error,
                placeholder = "ABCD-EFGH-JKMN", capitalization = KeyboardCapitalization.Characters,
                modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedButton(onClick = onExit, modifier = Modifier.heightIn(min = Dimens.TouchTarget)) { Text("Back to login") }
                Button(
                    onClick = viewModel::onCodeSubmit,
                    enabled = !state.busy && state.codeText.isNotBlank(),
                    modifier = Modifier.heightIn(min = Dimens.TouchTarget),
                ) { Text(if (state.busy) "Checking…" else "Next") }
            }
        }
        RecoveryStep.PIN, RecoveryStep.CONFIRM_PIN -> PinEntryPanel(
            title = "Choose a new admin PIN",
            instruction = if (state.step == RecoveryStep.PIN) "Enter a new PIN of 4 to 6 digits." else "Enter the same PIN again to confirm it.",
            pinLength = state.pinLength,
            error = state.error,
            busy = false,
            onDigit = viewModel::onDigit,
            onBackspace = viewModel::onBackspace,
            onSubmit = viewModel::onPinSubmit,
            modifier = Modifier.fillMaxSize(),
        ) {
            OutlinedButton(onClick = onExit, modifier = Modifier.heightIn(min = Dimens.TouchTarget)) { Text("Cancel") }
        }
        RecoveryStep.NEW_CODE -> Page {
            Text("Your new recovery code", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Your old recovery code will stop working. Write this new one down and keep it somewhere safe. " +
                    "It will NOT be shown again.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(state.newCode, style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(vertical = 16.dp))
            Row(
                Modifier
                    .heightIn(min = Dimens.TouchTarget)
                    .toggleable(value = state.writtenDown, role = Role.Checkbox, onValueChange = viewModel::onWrittenDownChange)
                    .padding(end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = state.writtenDown, onCheckedChange = null)
                Spacer(Modifier.width(12.dp))
                Text("I have written this down", style = MaterialTheme.typography.bodyLarge)
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge) }
            Button(
                onClick = viewModel::onFinish,
                enabled = state.writtenDown && !state.busy,
                modifier = Modifier.heightIn(min = Dimens.TouchTarget),
            ) { Text(if (state.busy) "Saving…" else "Save new PIN and code") }
        }
        RecoveryStep.DONE -> Page {
            Text("Admin PIN changed", style = MaterialTheme.typography.headlineMedium)
            Text("You can now log in with your new PIN. Keep the new recovery code safe.", style = MaterialTheme.typography.bodyLarge)
            Button(onClick = onExit, modifier = Modifier.heightIn(min = Dimens.TouchTarget)) { Text("Go to login") }
        }
    }
}

/** Scrollable page so content stays reachable above the on-screen keyboard. */
@Composable
private fun Page(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Dimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) { content() }
}
