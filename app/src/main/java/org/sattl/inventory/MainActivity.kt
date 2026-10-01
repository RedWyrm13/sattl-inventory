package org.sattl.inventory

import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.first
import org.sattl.inventory.ui.AppNavHost
import org.sattl.inventory.ui.Routes
import org.sattl.inventory.ui.components.IdleWarningDialog
import org.sattl.inventory.ui.components.LocalUserActivity
import org.sattl.inventory.ui.theme.SattlTheme

/**
 * The app's only activity (spec section 11: single-activity). Kiosk mode (spec section 9)
 * is added in milestone 6.
 */
class MainActivity : ComponentActivity() {

    private val container: AppContainer get() = (application as SattlApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SattlTheme {
                // Android 15 draws apps edge-to-edge; keep content clear of the system bars.
                Surface(
                    Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    // Lets text fields and dialogs reset the idle timer too (see LocalUserActivity).
                    CompositionLocalProvider(LocalUserActivity provides container.sessionManager::onUserInteraction) {
                        Box(Modifier.safeDrawingPadding()) { AppRoot() }
                    }
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun AppRoot() {
        val session = container.sessionManager
        val user by session.currentUser.collectAsStateWithLifecycle()
        val idleWarning by session.idleWarningSeconds.collectAsStateWithLifecycle()

        // Spec section 9: Back never leaves the app. Screens with somewhere to go back to
        // are handled by the navigation library first; anything left is swallowed here.
        BackHandler(enabled = true) {}

        // Choose the first screen once: Setup on first launch, otherwise Login (spec 5.1).
        var start by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(Unit) {
            start = if (container.authRepository.setupComplete.first()) Routes.LOGIN else Routes.SETUP
        }
        val startDestination = start ?: return

        AppNavHost(container = container, startDestination = startDestination, user = user)

        idleWarning?.let { seconds ->
            IdleWarningDialog(secondsLeft = seconds, onStillHere = session::onUserInteraction)
        }
    }

    /**
     * Every touch on the app's window resets the inactivity timer (rule 6.14).
     * Keyboard typing and touches inside dialogs do not pass through here; those are
     * reported through LocalUserActivity instead.
     */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) container.sessionManager.onUserInteraction()
        return super.dispatchTouchEvent(ev)
    }
}
