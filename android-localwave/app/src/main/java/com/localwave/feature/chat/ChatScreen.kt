package com.localwave.feature.chat

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.localwave.design.LWSpacing
import com.localwave.design.components.MessageBubble
import com.localwave.design.components.PermissionBanner
import com.localwave.design.components.SignalStrengthView
import com.localwave.design.components.WakeButton

@Composable
fun ChatScreen(viewModel: ChatViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
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
        MessageComposer(state.draft, state.canSend, viewModel::updateDraft, viewModel::sendDraft)
    }
}

@Composable
fun ChatHeader(name: String, status: String, rssi: Int?, wakeState: com.localwave.core.model.WakeButtonState, onWake: () -> Unit, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = onBack) { Text("Back") }
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Wake sends a local Bluetooth ping when reachable.", style = MaterialTheme.typography.bodySmall)
        }
        rssi?.let { SignalStrengthView(it) }
        WakeButton(wakeState, onWake)
    }
}

@Composable
fun MessageComposer(draft: String, canSend: Boolean, onChange: (String) -> Unit, onSend: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(value = draft, onValueChange = onChange, placeholder = { Text("Message over LocalWave...") }, modifier = Modifier.weight(1f))
        Button(onClick = onSend, enabled = canSend) { Text("Send") }
    }
}
