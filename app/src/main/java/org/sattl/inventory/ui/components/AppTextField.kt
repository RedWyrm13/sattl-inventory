package org.sattl.inventory.ui.components

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization

/**
 * The app's standard text field: large text, the error shown right under the field
 * (spec 5.8, 10), and every keystroke reported to the inactivity timer (rule 6.14).
 * Required fields get a "*" after the label.
 */
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    required: Boolean = false,
    error: String? = null,
    placeholder: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    val reportActivity = LocalUserActivity.current
    OutlinedTextField(
        value = value,
        onValueChange = {
            reportActivity()
            onValueChange(it)
        },
        label = { Text(if (required) "$label *" else label) },
        placeholder = placeholder?.let { { Text(it) } },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        singleLine = singleLine,
        minLines = minLines,
        textStyle = MaterialTheme.typography.bodyLarge,
        keyboardOptions = KeyboardOptions(
            capitalization = capitalization,
            imeAction = if (singleLine) ImeAction.Next else ImeAction.Default,
        ),
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        modifier = modifier,
    )
}
