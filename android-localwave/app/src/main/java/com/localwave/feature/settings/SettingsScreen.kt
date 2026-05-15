package com.localwave.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.localwave.design.LWSpacing
import com.localwave.design.components.GlassCard
import com.localwave.design.components.PermissionBanner
import com.localwave.design.components.SectionHeader

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    engineMode: String,
    openPrivacy: () -> Unit,
    openIdentity: () -> Unit,
    openDiagnostics: () -> Unit,
    openPermissions: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize().padding(LWSpacing.screen), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() }) }
        item {
            GlassCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeader("Profile")
                    OutlinedTextField(state.displayName, viewModel::updateDisplayName, label = { Text("Display name") }, modifier = Modifier.fillMaxWidth())
                    Button(onClick = viewModel::saveDisplayName) { Text("Save Display Name") }
                }
            }
        }
        item {
            GlassCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeader("Frequency", "Current Frequency Code. This is a logical Bluetooth channel, not a radio tuner.")
                    OutlinedTextField(state.channelText, viewModel::updateChannel, label = { Text("Frequency Code") }, modifier = Modifier.fillMaxWidth())
                    Button(onClick = viewModel::switchChannel) { Text("Change Frequency Code") }
                }
            }
        }
        item {
            GlassCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeader("Privacy")
                    Text("No account. No internet. No server. Bluetooth only.")
                    Text("E2E encryption is designed by the core engine.")
                    Button(onClick = openPrivacy) { Text("Open Privacy Explanation") }
                }
            }
        }
        item { SettingsButton("Identity", "My identity fingerprint", openIdentity) }
        item { SettingsButton("Permissions", "Bluetooth, Notifications, Active mode/background", openPermissions) }
        item { SettingsButton("Diagnostics", "Mode: $engineMode. Nearby peers: ${state.nearbyPeerCount}.", openDiagnostics) }
        state.error?.let { item { PermissionBanner(it) } }
    }
}

@Composable
private fun SettingsButton(title: String, body: String, onClick: () -> Unit) {
    GlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(body, style = MaterialTheme.typography.bodySmall)
            Button(onClick = onClick) { Text("Open") }
        }
    }
}
