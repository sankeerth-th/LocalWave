package com.localwave.feature.people

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.localwave.design.LWIcons
import com.localwave.design.LWTheme
import com.localwave.design.components.EmptyStateView
import com.localwave.design.components.FrequencyPill
import com.localwave.design.components.PeerSummary
import com.localwave.design.components.PermissionBanner
import com.localwave.design.components.SectionHeader
import com.localwave.mock.MockLocalWaveEngine

@Composable
fun PeopleScreen(viewModel: PeopleViewModel, channelText: String, engineMode: String, openChat: (PeerId) -> Unit, openSettings: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(LWSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row {
                    Column(Modifier.weight(1f)) {
                        Text("LocalWave", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
                        Text(statusText(state.transportState.isScanning, state.peers.size), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = openSettings) {
                        Icon(LWIcons.Settings, contentDescription = "Settings")
                    }
                }
                FrequencyHeader(channelText)
            }
        }
        if (state.transportState.permission.name != "ALLOWED") {
            item { PermissionBanner("Turn on Bluetooth permission to find nearby people.") }
        }
        item { SectionHeader("Nearby") }
        if (state.peers.isEmpty()) {
            item { EmptyStateView("No one nearby yet.", "Keep LocalWave open on both phones with the same Frequency Code.") }
        } else {
            items(state.peers, key = { it.id.value }) { peer ->
                PeerSummary(peer, onOpen = { openChat(peer.id) }, wakeState = viewModel.wakeState(peer), onWake = { viewModel.sendWake(peer) })
            }
        }
    }
}

@Composable
fun FrequencyHeader(channelText: String) {
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text("Channel", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(10.dp))
        FrequencyPill(channelText)
    }
}

private fun statusText(isScanning: Boolean, count: Int): String {
    return when {
        count > 0 -> "$count nearby"
        isScanning -> "Looking nearby"
        else -> "Ready. No one nearby"
    }
}

@Preview
@Composable
private fun PeoplePreview() {
    val engine = MockLocalWaveEngine()
    LWTheme { PeopleScreen(PeopleViewModel(engine), "DOCK-A", "MOCK", {}, {}) }
}
