package org.sattl.inventory.ui.users

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.sattl.inventory.session.SessionUser
import org.sattl.inventory.ui.components.AppTextField
import org.sattl.inventory.ui.components.AppTopBar
import org.sattl.inventory.ui.components.Dimens
import org.sattl.inventory.ui.components.PinEntryPanel
import org.sattl.inventory.ui.components.SecureScreen

/** Add user or Reset PIN (spec 5.9). See [UserPinViewModel]. */
@Composable
fun UserPinScreen(
    viewModel: UserPinViewModel,
    user: SessionUser,
    onLogout: () -> Unit,
    onFinished: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // A PIN is typed here (spec section 8).
    SecureScreen()

    Column(Modifier.fillMaxSize()) {
        AppTopBar(user = user, onLogout = onLogout, onChangePin = null)
        if (state.loading) return@Column
        val title = if (state.adding) "Add user" else "Reset PIN for ${state.name}"
        when (state.step) {
            UserPinStep.NAME -> Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(Dimens.ScreenPadding),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(title, style = MaterialTheme.typography.headlineMedium)
                AppTextField(
                    state.name, viewModel::onNameChange, "Name", required = true,
                    error = state.error, capitalization = KeyboardCapitalization.Words,
                    modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
                )
                Text(
                    "This name appears in the login list. Next you will choose a default PIN for them.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedButton(onClick = onFinished, modifier = Modifier.heightIn(min = Dimens.TouchTarget)) { Text("Cancel") }
                    Button(onClick = viewModel::onNameNext, enabled = !state.busy, modifier = Modifier.heightIn(min = Dimens.TouchTarget)) {
                        Text("Next")
                    }
                }
            }
            UserPinStep.PIN, UserPinStep.CONFIRM -> PinEntryPanel(
                title = title,
                instruction = if (state.step == UserPinStep.PIN)
                    "Type a default PIN of 4 to 6 digits. ${if (state.adding) "They" else state.name} will choose their own PIN at the next login."
                else "Type the same default PIN again to confirm it.",
                pinLength = state.pinLength,
                error = state.error,
                busy = state.busy,
                onDigit = viewModel::onDigit,
                onBackspace = viewModel::onBackspace,
                onSubmit = viewModel::onSubmit,
            ) {
                OutlinedButton(
                    onClick = if (state.adding) viewModel::onBackToName else onFinished,
                    modifier = Modifier.heightIn(min = Dimens.TouchTarget),
                ) { Text(if (state.adding) "Back" else "Cancel") }
            }
            UserPinStep.DONE -> Column(
                Modifier.padding(Dimens.ScreenPadding),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    if (state.adding) "${state.name} has been added" else "${state.name}'s PIN has been reset",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    "Tell ${state.name} the default PIN. They will be asked to choose their own PIN when they next log in." +
                        if (state.adding) "" else " Any lockout has been cleared.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Button(onClick = onFinished, modifier = Modifier.heightIn(min = Dimens.TouchTarget)) { Text("Back to users") }
            }
        }
    }
}
