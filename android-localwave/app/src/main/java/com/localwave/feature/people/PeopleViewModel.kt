package com.localwave.feature.people

import com.localwave.core.model.PeerId
import com.localwave.core.model.PeerProfile
import com.localwave.core.model.PresenceState
import com.localwave.core.model.TransportPermissionState
import com.localwave.core.model.TransportState
import com.localwave.core.model.WakeButtonState
import com.localwave.core.protocol.LocalWaveEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PeopleUiState(
    val peers: List<PeerProfile> = emptyList(),
    val transportState: TransportState = TransportState(),
    val wakeStates: Map<PeerId, WakeButtonState> = emptyMap(),
    val error: String? = null
)

class PeopleViewModel(private val engine: LocalWaveEngine) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(PeopleUiState())
    val state: StateFlow<PeopleUiState> = _state

    fun start() {
        scope.launch {
            engine.observePeers().collectLatest { peers ->
                _state.update { it.copy(peers = peers.sortedBy { peer -> peer.state.sortOrder }) }
            }
        }
        scope.launch {
            engine.observeTransportState().collectLatest { transport ->
                _state.update { it.copy(transportState = transport) }
            }
        }
    }

    fun wakeState(peer: PeerProfile): WakeButtonState {
        val transport = state.value.transportState
        if (transport.permission != TransportPermissionState.ALLOWED) return WakeButtonState.PERMISSION_NEEDED
        if (peer.state !in setOf(PresenceState.AVAILABLE, PresenceState.CONNECTING)) return WakeButtonState.UNAVAILABLE
        return state.value.wakeStates[peer.id] ?: WakeButtonState.IDLE
    }

    fun sendWake(peer: PeerProfile) {
        if (wakeState(peer) !in setOf(WakeButtonState.IDLE, WakeButtonState.FAILED)) return
        _state.update { it.copy(wakeStates = it.wakeStates + (peer.id to WakeButtonState.SENDING), error = null) }
        scope.launch {
            try {
                engine.sendWake(peer.id)
                _state.update { it.copy(wakeStates = it.wakeStates + (peer.id to WakeButtonState.SENT)) }
            } catch (_: Throwable) {
                _state.update { it.copy(wakeStates = it.wakeStates + (peer.id to WakeButtonState.FAILED), error = "Wake could not be sent. This person may not be reachable right now.") }
            }
        }
    }

    private val PresenceState.sortOrder: Int get() = when (this) {
        PresenceState.AVAILABLE -> 0
        PresenceState.CONNECTING -> 1
        PresenceState.RECENTLY_SEEN -> 2
        PresenceState.SLEEPING -> 3
        PresenceState.PERMISSION_NEEDED -> 4
        PresenceState.UNREACHABLE -> 5
    }
}
