package org.sattl.inventory.ui.components

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Rule 6.14: the 15-second "Still there?" warning shown before inactivity logout.
 * A dialog is its own window, so touches on it do not reach MainActivity; the button
 * therefore reports the interaction itself via [onStillHere].
 */
@Composable
fun IdleWarningDialog(secondsLeft: Int, onStillHere: () -> Unit) {
    AlertDialog(
        onDismissRequest = onStillHere,
        title = { Text("Still there?") },
        text = {
            Text(
                "You will be logged out in $secondsLeft seconds.",
                style = MaterialTheme.typography.bodyLarge,
            )
        },
        confirmButton = {
            Button(onClick = onStillHere, modifier = Modifier.heightIn(min = Dimens.TouchTarget)) {
                Text("I'm still here")
            }
        },
    )
}
