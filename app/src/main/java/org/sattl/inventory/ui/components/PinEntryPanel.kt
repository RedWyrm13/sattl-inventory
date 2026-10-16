package org.sattl.inventory.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Landscape PIN entry: instructions, masked dots and any error on the left, the large
 * number pad on the right (spec 5.2, 10). [extra] adds content under the error, e.g. a
 * Back button. The caller must also call [SecureScreen] (spec section 8).
 */
@Composable
fun PinEntryPanel(
    title: String,
    instruction: String,
    pinLength: Int,
    error: String?,
    busy: Boolean,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    extra: @Composable ColumnScope.() -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(Dimens.ScreenPadding),
        horizontalArrangement = Arrangement.spacedBy(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            Text(instruction, style = MaterialTheme.typography.titleMedium)
            PinDots(pinLength)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge) }
            if (busy) Text("Saving…", style = MaterialTheme.typography.bodyLarge)
            extra()
        }
        PinPad(
            onDigit = onDigit,
            onBackspace = onBackspace,
            onSubmit = onSubmit,
            enabled = !busy,
            submitEnabled = pinLength > 0,
        )
    }
}
