package org.sattl.inventory.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/**
 * Spec section 8: no screenshots of PIN entry screens. Put `SecureScreen()` at the top of
 * any screen that shows a PIN pad or the recovery code; it sets FLAG_SECURE while the
 * screen is visible.
 *
 * A counter is used because during navigation the old and new screens briefly overlap; the
 * flag is only cleared when the last secure screen leaves.
 */
@Composable
fun SecureScreen() {
    val activity = LocalContext.current.findActivity() ?: return
    DisposableEffect(activity) {
        SecureFlag.acquire(activity)
        onDispose { SecureFlag.release(activity) }
    }
}

private object SecureFlag {
    private var count = 0

    fun acquire(activity: Activity) {
        if (count++ == 0) activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    fun release(activity: Activity) {
        if (--count <= 0) {
            count = 0
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
