package org.sattl.inventory.ui.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.sattl.inventory.session.SessionUser
import org.sattl.inventory.ui.components.AppTopBar
import org.sattl.inventory.ui.components.Dimens
import org.sattl.inventory.ui.components.PinEntryPanel
import org.sattl.inventory.ui.components.SecureScreen
import org.sattl.inventory.ui.components.SuccessThenLogout
import org.sattl.inventory.util.Documents
import org.sattl.inventory.util.Formats

/** Show the restore result long enough to read the longer message. */
private const val RESTORE_MESSAGE_MILLIS = 6_000L

/** How many problems to list before summarising the rest. */
private const val MAX_PROBLEMS_SHOWN = 15

/** Export / Restore (spec 7.1, 7.3), admin only. Files go through Android's file picker. */
@Composable
fun BackupScreen(
    viewModel: BackupViewModel,
    user: SessionUser,
    onLogout: () -> Unit,
    onChangePin: () -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) viewModel.export(Documents.displayName(context, uri)) { Documents.openForWriting(context, uri) }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.read { Documents.openForReading(context, uri) }
    }

    when (val stage = state.stage) {
        BackupStage.Restored -> {
            SuccessThenLogout(
                title = "Restore complete",
                detail = "Everyone now logs in with the PINs they had when the backup was made.",
                onLogout = onLogout,
                durationMillis = RESTORE_MESSAGE_MILLIS,
            )
            return
        }
        is BackupStage.Confirm -> {
            ConfirmRestore(stage, user, viewModel, onLogout)
            return
        }
        else -> Unit
    }

    Column(Modifier.fillMaxSize()) {
        AppTopBar(user = user, onLogout = onLogout, onChangePin = onChangePin)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.ScreenPadding, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedButton(onClick = onBack, modifier = Modifier.heightIn(min = Dimens.TouchTarget)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                Text("Inventory", Modifier.padding(start = 8.dp))
            }
            Text("Export / Restore", style = MaterialTheme.typography.headlineMedium)
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StageMessage(state.stage, onDismiss = viewModel::reset)
            val busy = state.stage is BackupStage.Working
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Panel("Full backup", Modifier.weight(1f)) {
                    Text(
                        "Saves everything on this tablet (items, checkout history, users and settings) to one ZIP file. " +
                            "Plug in the USB drive, tap the button, and choose the drive in the file picker.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        "Last backup: " + (state.lastExportAt?.let(Formats::dateTime) ?: "never"),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Button(
                        onClick = { exportLauncher.launch(viewModel.suggestedFileName()) },
                        enabled = !busy,
                        modifier = Modifier.heightIn(min = Dimens.TouchTarget),
                    ) { Text("Export full backup") }
                }
                Panel("Restore", Modifier.weight(1f)) {
                    Text(
                        "Replaces ALL data on this tablet with a backup ZIP made by Export. The file is checked first; " +
                            "nothing changes unless it is valid and you confirm with your PIN.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    OutlinedButton(
                        onClick = {
                            // Some USB file browsers label ZIPs loosely, so accept any file; the app checks it.
                            restoreLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream", "*/*"))
                        },
                        enabled = !busy,
                        modifier = Modifier.heightIn(min = Dimens.TouchTarget),
                    ) { Text("Restore from backup…") }
                }
            }
        }
    }
}

@Composable
private fun Panel(title: String, modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}

/** Progress, success or problems for the last action. */
@Composable
private fun StageMessage(stage: BackupStage, onDismiss: () -> Unit) {
    val error = MaterialTheme.colorScheme.error
    when (stage) {
        is BackupStage.Working -> Text(stage.message, style = MaterialTheme.typography.titleMedium)
        is BackupStage.ExportDone -> Banner(
            "Backup saved" + (stage.fileName?.let { " as $it" } ?: "") + ". Keep the USB drive somewhere safe.",
            onDismiss,
        )
        is BackupStage.Failed -> Banner(stage.message, onDismiss, isError = true)
        is BackupStage.Invalid -> Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("This file cannot be restored. Nothing on this tablet was changed.", style = MaterialTheme.typography.titleMedium, color = error)
                stage.problems.take(MAX_PROBLEMS_SHOWN).forEach { Text("• $it", style = MaterialTheme.typography.bodyLarge) }
                if (stage.problems.size > MAX_PROBLEMS_SHOWN) {
                    Text("…and ${stage.problems.size - MAX_PROBLEMS_SHOWN} more problems.", style = MaterialTheme.typography.bodyLarge)
                }
                Text(
                    "Choose a backup file made by this app's Export, without editing it in Excel.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                OutlinedButton(onClick = onDismiss, modifier = Modifier.heightIn(min = Dimens.TouchTarget)) { Text("OK") }
            }
        }
        else -> Unit
    }
}

@Composable
private fun Banner(text: String, onDismiss: () -> Unit, isError: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        OutlinedButton(onClick = onDismiss, modifier = Modifier.heightIn(min = Dimens.TouchTarget)) { Text("OK") }
    }
}

/** Spec 7.3 step 4: summary, warning, and the admin's current PIN to confirm. */
@Composable
private fun ConfirmRestore(stage: BackupStage.Confirm, user: SessionUser, viewModel: BackupViewModel, onLogout: () -> Unit) {
    SecureScreen()
    val m = stage.backup.manifest
    val d = stage.backup.data
    Column(Modifier.fillMaxSize()) {
        AppTopBar(user = user, onLogout = onLogout, onChangePin = null)
        PinEntryPanel(
            title = "Restore this backup?",
            instruction = "Enter your current admin PIN to replace all data.",
            pinLength = stage.pinLength,
            error = stage.error,
            busy = stage.busy,
            onDigit = viewModel::onDigit,
            onBackspace = viewModel::onBackspace,
            onSubmit = viewModel::confirmRestore,
        ) {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Backup made ${Formats.dateTime(m.exportedAt)}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${count(d.items.size, "item")} · ${count(d.checkouts.size, "checkout")} · ${count(d.users.size, "user")}",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text("Made by app version ${m.appVersion.ifEmpty { "unknown" }}", style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text(
                "This replaces ALL data on this tablet.",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Afterwards everyone is logged out and logs in with the PINs they had when the backup was made. " +
                    "The admin recovery code also goes back to the one from that time.",
                style = MaterialTheme.typography.bodyLarge,
            )
            OutlinedButton(onClick = viewModel::reset, enabled = !stage.busy, modifier = Modifier.heightIn(min = Dimens.TouchTarget)) {
                Text("Cancel")
            }
        }
    }
}

/** "1 item", "3 items". */
private fun count(n: Int, noun: String) = if (n == 1) "1 $noun" else "$n ${noun}s"
