package org.sattl.inventory.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Reports "the user is doing something" to the inactivity timer (rule 6.14).
 * Provided by MainActivity; a no-op in previews and tests.
 *
 * MainActivity already sees every touch on its own window. Two things bypass it:
 *  - typing on the on-screen keyboard: [AppTextField] reports each change;
 *  - touches inside dialogs (separate windows): add [reportsUserActivity] to dialog content.
 */
val LocalUserActivity = staticCompositionLocalOf<() -> Unit> { {} }

/** Reports every touch inside this element without consuming it. Use on dialog content. */
@Composable
fun Modifier.reportsUserActivity(): Modifier {
    val report = LocalUserActivity.current
    return pointerInput(report) {
        awaitPointerEventScope {
            while (true) {
                awaitPointerEvent(PointerEventPass.Initial)
                report()
            }
        }
    }
}
