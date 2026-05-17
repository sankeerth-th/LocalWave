package com.localwave.feature.chat

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.localwave.design.LWSpacing
import com.localwave.design.components.MessageBubble
import com.localwave.design.components.PermissionBanner
import com.localwave.design.components.SignalStrengthView
import com.localwave.design.components.WakeButton
import com.localwave.core.model.DeliveryRoute
import com.localwave.core.model.TransferRecord
import com.localwave.core.model.TransferStatus

import kotlinx.coroutines.launch

@Composable
fun ChatScreen(viewModel: ChatViewModel, engineMode: String, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val directAttachmentPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch { viewModel.sendAttachment(context, uri) }
    }
    val sharePackagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                val packageUri = viewModel.exportSharePackage(context, uri) ?: return@launch
                val share = Intent(Intent.ACTION_SEND)
                    .setType("application/vnd.localwave.package")
                    .putExtra(Intent.EXTRA_STREAM, packageUri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                context.startActivity(Intent.createChooser(share, "Share encrypted LocalWave package"))
            }
        }
    }
    val importPackagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { viewModel.importSharePackage(context, uri) }
    }
    Column(Modifier.fillMaxSize()) {
        ChatHeader(
            name = state.peer?.displayName ?: "Nearby person",
            status = state.peer?.state?.label ?: "Unknown",
            rssi = state.peer?.rssi,
            wakeState = state.wakeState,
            onWake = viewModel::sendWake,
            onBack = onBack
        )
        if (!state.isPeerReachable) {
            PermissionBanner("Messages will send when this person is nearby again.", Modifier.padding(horizontal = 16.dp))
        }
        state.error?.let { PermissionBanner(it, Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
        LazyColumn(Modifier.weight(1f).padding(LWSpacing.screen), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(state.messages, key = { it.id.toString() }) { message -> MessageBubble(message) }
        }
        state.transfers.firstOrNull()?.let { transfer ->
            TransferStatusBanner(transfer, Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        }
        AttachmentActions(
            sendDirect = { directAttachmentPicker.launch("*/*") },
            sharePackage = { sharePackagePicker.launch("*/*") },
            importPackage = { importPackagePicker.launch(arrayOf("application/vnd.localwave.package", "application/octet-stream", "*/*")) }
        )
        MessageComposer(state.draft, state.canSend, viewModel::updateDraft, viewModel::sendDraft)
    }
}

@Composable
fun TransferStatusBanner(transfer: TransferRecord, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(transfer.statusLabel(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        Text(transfer.routeLabel(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun TransferRecord.statusLabel(): String = when (status) {
    TransferStatus.QUEUED, TransferStatus.NEGOTIATING, TransferStatus.ANNOUNCED -> "Preparing $fileName"
    TransferStatus.ACCEPTED, TransferStatus.SESSION_NEGOTIATED, TransferStatus.SENDING, TransferStatus.TRANSFERRING -> "Sending $fileName"
    TransferStatus.MANIFEST_RECEIVED -> "Receiving encrypted manifest"
    TransferStatus.WAITING_FOR_PEER -> "Waiting for peer receipt"
    TransferStatus.WAITING_FOR_RELAY -> "Waiting for relay"
    TransferStatus.VERIFYING, TransferStatus.RECONSTRUCTING, TransferStatus.DECRYPTING, TransferStatus.IMPORTING -> "Verifying secure transfer"
    TransferStatus.EXPORTED -> "Encrypted package ready to share"
    TransferStatus.COMPLETED, TransferStatus.DELIVERED -> "Downloaded"
    TransferStatus.PENDING -> "Pending"
    TransferStatus.FAILED -> failureReason ?: "Transfer failed"
    TransferStatus.EXPIRED -> "Transfer expired"
    TransferStatus.CANCELLED -> "Transfer cancelled"
}

private fun TransferRecord.routeLabel(): String = when (route) {
    DeliveryRoute.L2CAP -> "Route: Direct L2CAP"
    DeliveryRoute.GATT -> "Route: GATT control only"
    DeliveryRoute.NATIVE_SHARE -> "Route: Native Share package"
    DeliveryRoute.FIXED_RELAY -> "Route: Fixed relay"
    DeliveryRoute.PHONE_RELAY -> "Route: Best-effort phone relay"
}

@Composable
fun ChatHeader(name: String, status: String, rssi: Int?, wakeState: com.localwave.core.model.WakeButtonState, onWake: () -> Unit, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = onBack) { Text("Back") }
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        rssi?.let { SignalStrengthView(it) }
        WakeButton(wakeState, onWake)
    }
}

@Composable
fun AttachmentActions(sendDirect: () -> Unit, sharePackage: () -> Unit, importPackage: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = sendDirect) { Text("Send File") }
        Button(onClick = sharePackage) { Text("Share Package") }
        Button(onClick = importPackage) { Text("Import") }
    }
}

@Composable
fun MessageComposer(draft: String, canSend: Boolean, onChange: (String) -> Unit, onSend: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(value = draft, onValueChange = onChange, placeholder = { Text("Message over LocalWave...") }, modifier = Modifier.weight(1f))
        Button(onClick = onSend, enabled = canSend) { Text("Send") }
    }
}
