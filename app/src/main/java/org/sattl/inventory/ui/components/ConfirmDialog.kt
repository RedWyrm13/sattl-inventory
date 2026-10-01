package org.sattl.inventory.ui.components

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Confirmation for destructive or important actions (spec section 10). */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.reportsUserActivity()) },
        confirmButton = {
            Button(onClick = onConfirm, modifier = Modifier.heightIn(min = Dimens.TouchTarget)) { Text(confirmLabel) }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, modifier = Modifier.heightIn(min = Dimens.TouchTarget)) { Text("Cancel") }
        },
    )
}
