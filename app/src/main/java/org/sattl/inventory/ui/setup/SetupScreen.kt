package org.sattl.inventory.ui.setup

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import org.sattl.inventory.ui.components.Dimens
import org.sattl.inventory.ui.components.PinDots
import org.sattl.inventory.ui.components.PinPad
import org.sattl.inventory.ui.components.SecureScreen
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** First-time setup screen (spec section 5.1). See [SetupViewModel] for the flow. */
@Composable
fun SetupScreen(viewModel: SetupViewModel, onFinished: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // PINs and the recovery code are on screen during setup (spec section 8).
    SecureScreen()

    // Scrollable so nothing is hidden behind the on-screen keyboard in landscape.
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Dimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("First-time setup", style = MaterialTheme.typography.headlineMedium)
        when (state.step) {
            SetupStep.NAME -> NameStep(state, viewModel)
            SetupStep.PIN, SetupStep.CONFIRM_PIN -> PinStep(state, viewModel)
            SetupStep.RECOVERY_CODE -> RecoveryCodeStep(state, viewModel)
            SetupStep.DATE_TIME -> DateTimeStep(onFinished)
        }
    }
}

@Composable
private fun NameStep(state: SetupUiState, vm: SetupViewModel) {
    Text(
        "Welcome. This tablet has not been set up yet. First, create the admin (lab manager) account.",
        style = MaterialTheme.typography.bodyLarge,
    )
    OutlinedTextField(
        value = state.name,
        onValueChange = vm::onNameChange,
        label = { Text("Admin's name") },
        singleLine = true,
        isError = state.error != null,
        supportingText = state.error?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Words,
            imeAction = ImeAction.Next,
        ),
        keyboardActions = KeyboardActions(onNext = { vm.onNameNext() }),
        textStyle = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
    )
    Button(onClick = vm::onNameNext, modifier = Modifier.heightIn(min = Dimens.TouchTarget)) {
        Text("Next")
    }
}

@Composable
private fun PinStep(state: SetupUiState, vm: SetupViewModel) {
    Row(horizontalArrangement = Arrangement.spacedBy(48.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Admin: ${state.name.trim()}", style = MaterialTheme.typography.titleLarge)
            Text(
                if (state.step == SetupStep.PIN) "Choose a PIN of 4 to 6 digits."
                else "Enter the same PIN again to confirm it.",
                style = MaterialTheme.typography.bodyLarge,
            )
            PinDots(state.pinLength)
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            OutlinedButton(onClick = vm::onBackToName, modifier = Modifier.heightIn(min = Dimens.TouchTarget)) {
                Text("Back")
            }
        }
        PinPad(
            onDigit = vm::onDigit,
            onBackspace = vm::onBackspace,
            onSubmit = vm::onPinSubmit,
            enabled = true,
            submitEnabled = state.pinLength > 0,
        )
    }
}

@Composable
private fun RecoveryCodeStep(state: SetupUiState, vm: SetupViewModel) {
    Text("Your admin recovery code", style = MaterialTheme.typography.titleLarge)
    Text(
        "If you ever forget your admin PIN, this code lets you set a new one. " +
            "Write it down now and keep it somewhere safe. It will NOT be shown again.",
        style = MaterialTheme.typography.bodyLarge,
    )
    Text(
        state.recoveryCode,
        style = MaterialTheme.typography.displaySmall,
        modifier = Modifier.padding(vertical = 16.dp),
    )
    // The whole row (box and label) is one large touch target.
    Row(
        Modifier
            .heightIn(min = Dimens.TouchTarget)
            .toggleable(
                value = state.writtenDown,
                role = Role.Checkbox,
                onValueChange = vm::onWrittenDownChange,
            )
            .padding(end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = state.writtenDown, onCheckedChange = null)
        Spacer(Modifier.width(12.dp))
        Text("I have written this down", style = MaterialTheme.typography.bodyLarge)
    }
    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    Button(
        onClick = vm::onRecoveryContinue,
        enabled = state.writtenDown && !state.saving,
        modifier = Modifier.heightIn(min = Dimens.TouchTarget),
    ) { Text(if (state.saving) "Saving…" else "Continue") }
}

@Composable
private fun DateTimeStep(onFinished: () -> Unit) {
    val context = LocalContext.current
    // Refresh the shown clock every second so the admin sees any change they make.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }
    val formatted = remember(now) {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.FULL, FormatStyle.MEDIUM)
            .format(Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()))
    }

    Text("Check the date and time", style = MaterialTheme.typography.titleLarge)
    Text(
        "Every checkout is stamped with the tablet's clock, so it must be correct. " +
            "The tablet currently says:",
        style = MaterialTheme.typography.bodyLarge,
    )
    Text(formatted, style = MaterialTheme.typography.headlineSmall)
    Text(
        "If this is wrong, open the date and time settings, correct it, then come back here.",
        style = MaterialTheme.typography.bodyLarge,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedButton(
            onClick = {
                try {
                    context.startActivity(Intent(Settings.ACTION_DATE_SETTINGS))
                } catch (_: ActivityNotFoundException) {
                    // Some tablets lack this screen; the admin can use the Settings app instead.
                }
            },
            modifier = Modifier.heightIn(min = Dimens.TouchTarget),
        ) { Text("Open date & time settings") }
        Spacer(Modifier.width(8.dp))
        Button(onClick = onFinished, modifier = Modifier.heightIn(min = Dimens.TouchTarget)) {
            Text("Date and time are correct — go to login")
        }
    }
}
