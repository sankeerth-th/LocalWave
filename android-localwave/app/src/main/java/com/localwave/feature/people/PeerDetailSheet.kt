package com.localwave.feature.people

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.localwave.core.model.PeerProfile

@Composable
fun PeerDetailSheet(peer: PeerProfile) {
    Text("${peer.displayName} fingerprint ${peer.fingerprint.take(19)}")
}
