package org.sattl.inventory.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.sattl.inventory.util.Formats
import java.time.LocalDate

/**
 * A labelled button showing a date; tapping it opens the date picker. Shows [error] under it
 * (spec 5.8, 10). When [onClear] is given, the date is optional and can be removed.
 */
@Composable
fun DateField(
    label: String,
    date: LocalDate?,
    error: String?,
    required: Boolean,
    onClick: () -> Unit,
    onClear: (() -> Unit)? = null,
) {
    Column {
        Text(
            if (required) "$label *" else label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onClick, modifier = Modifier.heightIn(min = Dimens.TouchTarget)) {
                Icon(Icons.Default.DateRange, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(date?.let(Formats::date) ?: "Choose a date")
            }
            if (onClear != null && date != null) {
                TextButton(onClick = onClear, modifier = Modifier.heightIn(min = Dimens.TouchTarget)) { Text("Clear") }
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
    }
}

/**
 * Material date picker in a dialog. [isSelectable] greys out dates that are not allowed; the
 * repository still validates, so this is only a convenience.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDatePickerDialog(
    initial: LocalDate?,
    onPicked: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    isSelectable: (LocalDate) -> Boolean = { true },
) {
    val selectable = remember(isSelectable) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = isSelectable(Formats.fromPickerMillis(utcTimeMillis))
        }
    }
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = initial?.let(Formats::toPickerMillis),
        selectableDates = selectable,
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { pickerState.selectedDateMillis?.let { onPicked(Formats.fromPickerMillis(it)) } ?: onDismiss() },
                modifier = Modifier.heightIn(min = Dimens.TouchTarget),
            ) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = Dimens.TouchTarget)) { Text("Cancel") }
        },
    ) {
        // The dialog is its own window, so report touches to the idle timer explicitly.
        DatePicker(state = pickerState, modifier = Modifier.reportsUserActivity())
    }
}
