package com.localwave.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.localwave.app.AppEnvironment
import com.localwave.core.model.LocalIdentity
import com.localwave.design.LWSpacing
import com.localwave.design.components.GlassCard

@Composable
fun PrivacyExplanationScreen(onBack: () -> Unit) {
    DetailScaffold("Privacy", onBack) {
        Text("LocalWave does not use accounts, phone numbers, email, servers, cloud sync, analytics, or tracking SDKs.")
        Text("Messages are sent locally over Bluetooth to nearby devices using the same Frequency Code.")
        Text("The Frequency Code is a logical Bluetooth channel code, not a real radio frequency tuner.")
        Text("Wake is best-effort and depends on Bluetooth reachability, notification permission, background behavior, and device battery settings.")
        Text("LocalWave is not an emergency or public-safety communication system.")
    }
}

@Composable
fun IdentityFingerprintScreen(environment: AppEnvironment, onBack: () -> Unit) {
    var identity by remember { mutableStateOf<LocalIdentity?>(null) }
    LaunchedEffect(Unit) { identity = environment.engine.localIdentity() }
    DetailScaffold("Identity", onBack) {
        Text("My identity fingerprint")
        Text(identity?.fingerprint ?: "Unavailable")
        Text("Use fingerprints for manual trust checks in high-trust environments.")
    }
}

@Composable
fun DiagnosticsScreen(environment: AppEnvironment, onBack: () -> Unit) {
    val transport by environment.engine.observeTransportState().collectAsStateWithLifecycle(initialValue = com.localwave.core.model.TransportState())
    val peers by environment.engine.observePeers().collectAsStateWithLifecycle(initialValue = emptyList())
    DetailScaffold("Diagnostics", onBack) {
        Text("Engine mode: ${environment.mode.name}")
        Text("Transport: ${if (transport.isRunning) "Running" else "Stopped"}")
        Text("Scanning: ${if (transport.isScanning) "On" else "Off"}")
        Text("Advertising: ${if (transport.isAdvertising) "On" else "Off"}")
        Text("Nearby peer count: ${peers.size}")
        Text("Bluetooth permission: ${transport.permission.name.lowercase()}")
        Text("Last redacted BLE error: ${transport.lastError?.take(24) ?: "None"}")
        Text("Diagnostics do not show plaintext messages, private keys, shared secrets, or full private channel passwords.")
    }
}

@Composable
fun PermissionStatusScreen(environment: AppEnvironment, onBack: () -> Unit) {
    val state = environment.permissionController.currentState()
    DetailScaffold("Permissions", onBack) {
        Text("Bluetooth is required to find nearby LocalWave users.")
        Text("Bluetooth enabled: ${state.bluetoothEnabled}")
        Text("Scan permission: ${state.scanPermission}")
        Text("Advertise permission: ${state.advertisePermission}")
        Text("Connect permission: ${state.connectPermission}")
        Text("Notifications are optional, but Wake alerts need notification permission.")
        Text("Notification permission: ${state.notificationPermission}")
        Text("BLE supported: ${state.bleSupported}")
        Text("BLE advertiser supported: ${state.advertiserSupported}")
        Text("Android battery settings may affect background discovery.")
    }
}

@Composable
private fun DetailScaffold(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().padding(LWSpacing.screen), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Button(onClick = onBack) { Text("Back") }
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
        }
    }
}
