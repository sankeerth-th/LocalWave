package com.localwave.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.OutlinedButton
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
    LazyColumn(Modifier.fillMaxSize().padding(LWSpacing.screen), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() }) }
        item {
            GlassCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeader("Profile")
                    OutlinedTextField(state.displayName, viewModel::updateDisplayName, label = { Text("Display name") }, modifier = Modifier.fillMaxWidth())
                    OutlinedButton(onClick = viewModel::saveDisplayName, modifier = Modifier.fillMaxWidth()) { Text("Save name") }
                }
            }
        }
        item {
            GlassCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeader("Channel")
                    OutlinedTextField(state.channelText, viewModel::updateChannel, label = { Text("Frequency Code") }, modifier = Modifier.fillMaxWidth())
                    OutlinedButton(onClick = viewModel::switchChannel, modifier = Modifier.fillMaxWidth()) { Text("Change code") }
                }
            }
        }
        item { SettingsButton("Identity", "Fingerprint", openIdentity) }
        item { SettingsButton("Permissions", "Bluetooth and notifications", openPermissions) }
        item { SettingsButton("Privacy", "How LocalWave works", openPrivacy) }
        item { SettingsButton("Diagnostics", "Mode: $engineMode, peers: ${state.nearbyPeerCount}", openDiagnostics) }
        state.error?.let { item { PermissionBanner(it) } }
    }
}

@Composable
private fun SettingsButton(title: String, body: String, onClick: () -> Unit) {
    GlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(body, style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text("Open") }
        }
    }
}
