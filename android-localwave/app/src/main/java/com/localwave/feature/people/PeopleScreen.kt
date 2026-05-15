package com.localwave.feature.people

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.localwave.core.model.PeerId
import com.localwave.design.LWSpacing
import com.localwave.design.LWTheme
import com.localwave.design.components.EmptyStateView
import com.localwave.design.components.FrequencyPill
import com.localwave.design.components.GlassCard
import com.localwave.design.components.PeerSummary
import com.localwave.design.components.PermissionBanner
import com.localwave.design.components.SectionHeader
import com.localwave.mock.MockLocalWaveEngine

@Composable
fun PeopleScreen(viewModel: PeopleViewModel, channelText: String, openChat: (PeerId) -> Unit, openSettings: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(LWSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("LocalWave", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
                FrequencyHeader(channelText, if (state.transportState.isScanning) "Scanning nearby..." else "Offline local mode")
                Button(onClick = openSettings) { Text("Settings") }
            }
        }
        if (state.transportState.permission.name != "ALLOWED") {
            item { PermissionBanner("Bluetooth is required to find nearby LocalWave users.") }
        }
        if (!state.transportState.notificationPermission) {
            item { PermissionBanner("Notifications are optional, but Wake alerts need notification permission.") }
        }
        item { ActiveModeCard(state.transportState.activeModeAvailable) }
        item { SectionHeader("Nearby People", "${state.peers.size} available on this Frequency Code") }
        if (state.peers.isEmpty()) {
            item { EmptyStateView("No one on this Frequency Code yet.", "Ask teammates to install LocalWave and enter the same code.") }
        } else {
            items(state.peers, key = { it.id.value }) { peer ->
                PeerSummary(peer, onOpen = { openChat(peer.id) }, wakeState = viewModel.wakeState(peer), onWake = { viewModel.sendWake(peer) })
            }
        }
    }
}

@Composable
fun FrequencyHeader(channelText: String, status: String) {
    GlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Frequency Code", style = MaterialTheme.typography.labelLarge)
            FrequencyPill(channelText)
            Text("Private local Bluetooth channel")
            Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ActiveModeCard(activeModeAvailable: Boolean) {
    GlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Active LocalWave Mode", fontWeight = FontWeight.Bold)
            Text(if (activeModeAvailable) "Available from the core engine." else "Unavailable until Thread 3 exposes service hooks.")
            Text("Android battery settings may affect background discovery.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Preview
@Composable
private fun PeoplePreview() {
    val engine = MockLocalWaveEngine()
    LWTheme { PeopleScreen(PeopleViewModel(engine), "DOCK-A", {}, {}) }
}
