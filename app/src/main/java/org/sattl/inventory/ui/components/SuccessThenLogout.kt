package org.sattl.inventory.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** How long the success message stays up before logout (spec 5.6). */
const val SUCCESS_MESSAGE_MILLIS = 2_000L

/**
 * Full-screen success message after a checkout or check-in, then automatic logout
 * (spec 5.6 step 3, 5.7 step 2, rule 6.14). The data is already saved before this appears,
 * so nothing is lost by the logout (rule 6.15).
 */
@Composable
fun SuccessThenLogout(title: String, detail: String, onLogout: () -> Unit) {
    val logout by rememberUpdatedState(onLogout)
    LaunchedEffect(Unit) {
        delay(SUCCESS_MESSAGE_MILLIS)
        logout()
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF1B7F3B), modifier = Modifier.size(96.dp))
            Text(title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Text(detail, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Text("Logging you out…", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
