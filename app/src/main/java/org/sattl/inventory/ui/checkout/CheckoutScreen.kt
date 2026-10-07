package org.sattl.inventory.ui.checkout

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
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import org.sattl.inventory.domain.CheckoutField
import org.sattl.inventory.session.SessionUser
import org.sattl.inventory.ui.components.AppDatePickerDialog
import org.sattl.inventory.ui.components.AppTextField
import org.sattl.inventory.ui.components.AppTopBar
import org.sattl.inventory.ui.components.DateField
import org.sattl.inventory.ui.components.Dimens
import org.sattl.inventory.ui.components.SuccessThenLogout

private enum class PickingDate { CHECKOUT, EXPECTED_RETURN }

/** Check out (spec 5.6): item summary and borrower on the left, the form on the right. */
@Composable
fun CheckoutScreen(
    viewModel: CheckoutViewModel,
    user: SessionUser,
    onLogout: () -> Unit,
    onCancel: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf<PickingDate?>(null) }

    val row = state.row
    if (state.done && row != null) {
        SuccessThenLogout(
            title = "${row.item.sattlTag} is checked out to ${user.name}",
            detail = "Destination: ${state.input.destination.trim()}",
            onLogout = onLogout,
        )
        return
    }

    Column(Modifier.fillMaxSize()) {
        AppTopBar(user = user, onLogout = onLogout, onChangePin = null)
        if (state.loading) return@Column

        // Title and buttons stay above the scrolling form, visible even with the keyboard open.
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.ScreenPadding, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Check out", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = onCancel, modifier = Modifier.heightIn(min = Dimens.TouchTarget)) { Text("Cancel") }
            Button(
                onClick = viewModel::confirm,
                enabled = !state.saving && state.blocked == null,
                modifier = Modifier.heightIn(min = Dimens.TouchTarget),
            ) { Text(if (state.saving) "Saving…" else "Confirm checkout") }
        }

        state.blocked?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = Dimens.ScreenPadding, vertical = 8.dp),
            )
        }
        if (row == null) return@Column
        val input = state.input
        val err = state.errors

        Row(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = Dimens.ScreenPadding, end = Dimens.ScreenPadding, bottom = Dimens.ScreenPadding),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Card(Modifier.weight(1f)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Item", style = MaterialTheme.typography.titleLarge)
                    Summary("SATTL tag", row.item.sattlTag)
                    Summary("Item", "${row.item.manufacturer} ${row.item.modelNumber}")
                    Summary("Serial number", row.item.serialNumber ?: "None")
                    Summary("Home location", row.item.homeLocation)
                    Text("Borrower", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp))
                    // Rule 6.3: always the logged-in user; not editable.
                    Text(user.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "You are responsible for this item until it is checked back in.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    DateField(
                        label = "Checkout date",
                        date = input.checkoutDate,
                        error = err[CheckoutField.CHECKOUT_DATE],
                        required = true,
                        onClick = { picking = PickingDate.CHECKOUT },
                    )
                    DateField(
                        label = "Expected return (optional)",
                        date = input.expectedReturnDate,
                        error = err[CheckoutField.EXPECTED_RETURN_DATE],
                        required = false,
                        onClick = { picking = PickingDate.EXPECTED_RETURN },
                        onClear = { viewModel.onExpectedReturnChange(null) },
                    )
                }
                AppTextField(
                    input.destination, viewModel::onDestinationChange, "Destination", required = true,
                    placeholder = "Where is it going? e.g. Hangar 3",
                    error = err[CheckoutField.DESTINATION], capitalization = KeyboardCapitalization.Words,
                    modifier = Modifier.fillMaxWidth(),
                )
                AppTextField(
                    input.reason, viewModel::onReasonChange, "Reason", required = true,
                    placeholder = "Why do you need it?",
                    singleLine = false, minLines = 2,
                    error = err[CheckoutField.REASON],
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    when (picking) {
        PickingDate.CHECKOUT -> AppDatePickerDialog(
            initial = state.input.checkoutDate,
            onPicked = { picking = null; viewModel.onCheckoutDateChange(it) },
            onDismiss = { picking = null },
            // No future checkout dates (see CheckoutValidator).
            isSelectable = { !it.isAfter(state.today) },
        )
        PickingDate.EXPECTED_RETURN -> AppDatePickerDialog(
            initial = state.input.expectedReturnDate ?: state.input.checkoutDate,
            onPicked = { picking = null; viewModel.onExpectedReturnChange(it) },
            onDismiss = { picking = null },
            // Spec section 4: not before the checkout date.
            isSelectable = { d -> state.input.checkoutDate?.let { !d.isBefore(it) } ?: true },
        )
        null -> Unit
    }
}

@Composable
private fun Summary(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(label, Modifier.weight(0.4f), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(0.6f), style = MaterialTheme.typography.bodyLarge)
    }
}
