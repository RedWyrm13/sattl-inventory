package org.sattl.inventory.ui.item

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.sattl.inventory.domain.ItemField
import org.sattl.inventory.session.SessionUser
import org.sattl.inventory.ui.components.AppDatePickerDialog
import org.sattl.inventory.ui.components.AppTextField
import org.sattl.inventory.ui.components.DateField
import org.sattl.inventory.ui.components.AppTopBar
import org.sattl.inventory.ui.components.Dimens

/** Add / edit item form (spec 5.8). Errors appear under each field. Fields marked * are required. */
@Composable
fun ItemFormScreen(
    viewModel: ItemFormViewModel,
    user: SessionUser,
    onLogout: () -> Unit,
    onSaved: (itemId: Long) -> Unit,
    onCancel: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pickingDate by remember { mutableStateOf(false) }
    LaunchedEffect(viewModel) { viewModel.saved.collect(onSaved) }

    Column(Modifier.fillMaxSize()) {
        AppTopBar(user = user, onLogout = onLogout, onChangePin = null)
        if (state.loading) return@Column
        if (state.notFound) {
            Text("This item no longer exists.", Modifier.padding(Dimens.ScreenPadding), style = MaterialTheme.typography.titleLarge)
            return@Column
        }
        val input = state.input
        val err = state.errors

        // Title and buttons stay fixed above the fields, so Save is always visible even when
        // the on-screen keyboard covers the lower half of the screen.
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.ScreenPadding, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                if (state.isNew) "Add item" else "Edit item",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = onCancel, modifier = Modifier.heightIn(min = Dimens.TouchTarget)) {
                Text("Cancel")
            }
            Button(
                onClick = viewModel::save,
                enabled = !state.saving,
                modifier = Modifier.heightIn(min = Dimens.TouchTarget),
            ) { Text(if (state.saving) "Saving…" else if (state.isNew) "Add item" else "Save changes") }
        }

        // The fields scroll, so every one can be reached above the keyboard.
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = Dimens.ScreenPadding, end = Dimens.ScreenPadding, bottom = Dimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppTextField(
                        input.sattlTag, viewModel::onTagChange, "SATTL tag", required = true,
                        error = err[ItemField.SATTL_TAG], capitalization = KeyboardCapitalization.Characters,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    AppTextField(
                        input.manufacturer, viewModel::onManufacturerChange, "Manufacturer", required = true,
                        error = err[ItemField.MANUFACTURER], capitalization = KeyboardCapitalization.Words,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    AppTextField(
                        input.modelNumber, viewModel::onModelChange, "Model number", required = true,
                        error = err[ItemField.MODEL_NUMBER], capitalization = KeyboardCapitalization.Characters,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    AppTextField(
                        input.serialNumber, viewModel::onSerialChange, "Serial number",
                        placeholder = "Leave blank if the item has none",
                        error = err[ItemField.SERIAL_NUMBER], capitalization = KeyboardCapitalization.Characters,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppTextField(
                        input.homeLocation, viewModel::onHomeLocationChange, "Home location", required = true,
                        placeholder = "e.g. Room 214", error = err[ItemField.HOME_LOCATION],
                        capitalization = KeyboardCapitalization.Words,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    DateField(
                        label = "Date into inventory",
                        date = input.dateIntoInventory,
                        error = err[ItemField.DATE_INTO_INVENTORY],
                        required = true,
                        onClick = { pickingDate = true },
                    )
                    Row(
                        Modifier.heightIn(min = Dimens.TouchTarget),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Can be checked out", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Switch(checked = input.isCheckoutable, onCheckedChange = viewModel::onCheckoutableChange)
                    }
                    AppTextField(
                        input.notes, viewModel::onNotesChange, "Notes",
                        singleLine = false, minLines = 3, error = err[ItemField.NOTES],
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

    if (pickingDate) {
        AppDatePickerDialog(
            initial = state.input.dateIntoInventory,
            onPicked = {
                pickingDate = false
                viewModel.onDateChange(it)
            },
            onDismiss = { pickingDate = false },
        )
    }
}
