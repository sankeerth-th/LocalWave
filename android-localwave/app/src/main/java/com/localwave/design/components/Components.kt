package com.localwave.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.localwave.core.model.ChatMessage
import com.localwave.core.model.MessageDirection
import com.localwave.core.model.PeerProfile
import com.localwave.core.model.PresenceState
import com.localwave.core.model.WakeButtonState

@Composable
fun GlassCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f))
    ) {
        Box(Modifier.padding(16.dp)) { content() }
    }
}

@Composable
fun FrequencyPill(code: String, modifier: Modifier = Modifier) {
    AssistChip(
        modifier = modifier.semantics { contentDescription = "Frequency Code $code. Private local Bluetooth channel." },
        onClick = {},
        label = { Text(code, fontWeight = FontWeight.Bold) }
    )
}

@Composable
fun PeerAvatar(name: String) {
    Box(
        modifier = Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center
    ) {
        Text(name.initials(), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SignalStrengthView(rssi: Int) {
    val bars = when {
        rssi >= -55 -> 4
        rssi >= -68 -> 3
        rssi >= -80 -> 2
        else -> 1
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.semantics { contentDescription = "Signal strength $bars of 4" }
    ) {
        repeat(4) { index ->
            Box(
                Modifier
                    .width(5.dp)
                    .height((8 + index * 5).dp)
                    .background(if (index < bars) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
            )
        }
    }
}

@Composable
fun WakeButton(state: WakeButtonState, onWake: () -> Unit, modifier: Modifier = Modifier) {
    val label = when (state) {
        WakeButtonState.IDLE -> "Wake"
        WakeButtonState.SENDING -> "Sending"
        WakeButtonState.SENT -> "Sent"
        WakeButtonState.UNAVAILABLE -> "Unavailable"
        WakeButtonState.PERMISSION_NEEDED -> "Permission"
        WakeButtonState.FAILED -> "Retry Wake"
    }
    val enabled = state == WakeButtonState.IDLE || state == WakeButtonState.FAILED
    OutlinedButton(
        onClick = onWake,
        enabled = enabled,
        modifier = modifier.semantics {
            role = Role.Button
            contentDescription = "$label. Wake sends a local Bluetooth ping when reachable."
        }
    ) { Text(label) }
}

@Composable
fun PermissionBanner(text: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.fillMaxWidth().semantics { contentDescription = "Permission notice. $text" }
    ) {
        Text(text, modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun EmptyStateView(title: String, message: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SectionHeader(title: String, subtitle: String? = null) {
    Column(Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun MessageBubble(message: ChatMessage) {
    val outgoing = message.direction == MessageDirection.OUTGOING
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (outgoing) Arrangement.End else Arrangement.Start) {
        Surface(
            color = if (outgoing) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth(0.82f)
        ) {
            Column(Modifier.padding(12.dp)) {
                Text(message.text)
                Text(message.status.name.lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
fun PeerSummary(peer: PeerProfile, onOpen: () -> Unit, wakeState: WakeButtonState, onWake: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PeerAvatar(peer.displayName)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(peer.displayName, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(peer.state.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Last seen ${lastSeenText(peer.lastSeenEpochMillis)}", style = MaterialTheme.typography.labelSmall)
            }
            SignalStrengthView(peer.rssi)
            Spacer(Modifier.width(8.dp))
            WakeButton(wakeState, onWake)
        }
        Spacer(Modifier.height(10.dp))
        Button(onClick = onOpen, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Open chat with ${peer.displayName}" }) {
            Text("Open Chat")
        }
    }
}

fun String.initials(): String = trim().split(Regex("\\s+")).filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifBlank { "LW" }

fun lastSeenText(epochMillis: Long): String {
    val delta = ((System.currentTimeMillis() - epochMillis) / 1000).coerceAtLeast(0)
    return when {
        delta < 60 -> "just now"
        delta < 3600 -> "${delta / 60} min ago"
        else -> "${delta / 3600} hr ago"
    }
}
