package org.sattl.inventory.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.sattl.inventory.security.PinRules

/**
 * Large on-screen number pad for PIN entry (spec sections 5.2, 10: keys at least 72 dp).
 * The system keyboard is never used for PINs.
 */
@Composable
fun PinPad(
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onSubmit: () -> Unit,
    enabled: Boolean,
    submitEnabled: Boolean,
    modifier: Modifier = Modifier,
    submitLabel: String = "OK",
) {
    val rows = listOf("123", "456", "789")
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { digit -> DigitKey(digit, enabled) { onDigit(digit) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilledTonalButton(
                onClick = onBackspace,
                enabled = enabled,
                modifier = Modifier
                    .size(Dimens.PinKey)
                    .semantics { contentDescription = "Delete last digit" },
            ) { Text("⌫", fontSize = 28.sp) }
            DigitKey('0', enabled) { onDigit('0') }
            Button(
                onClick = onSubmit,
                enabled = enabled && submitEnabled,
                modifier = Modifier.size(Dimens.PinKey),
            ) { Text(submitLabel, fontSize = 22.sp) }
        }
    }
}

@Composable
private fun DigitKey(digit: Char, enabled: Boolean, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(Dimens.PinKey)) {
        Text(digit.toString(), fontSize = 32.sp)
    }
}

/** Masked PIN display: one filled dot per digit typed, empty dots up to the maximum length. */
@Composable
fun PinDots(length: Int, modifier: Modifier = Modifier) {
    Row(
        modifier.semantics { contentDescription = "$length digits entered" },
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        repeat(PinRules.MAX_LENGTH) { i ->
            val filled = i < length
            Box(
                Modifier
                    .size(24.dp)
                    .border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                    .background(
                        if (filled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surface,
                        CircleShape,
                    )
            )
        }
    }
}

/** Appends a digit to a PIN being typed, ignoring input beyond the maximum length. */
fun String.plusPinDigit(d: Char): String = if (length < PinRules.MAX_LENGTH) this + d else this
