package com.localwave.feature.settings

import com.localwave.app.AppEnvironment
import com.localwave.app.AppStateHolder
import com.localwave.app.PersistedAppState
import com.localwave.core.diagnostics.RedactedLogger
import com.localwave.core.model.ChannelCode
import com.localwave.core.model.LocalIdentity
import com.localwave.core.model.TransportState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val displayName: String = "",
    val channelText: String = "",
    val identity: LocalIdentity? = null,
    val transportState: TransportState = TransportState(),
    val nearbyPeerCount: Int = 0,
    val error: String? = null
)

class SettingsViewModel(
    private val environment: AppEnvironment,
    private val appStateHolder: AppStateHolder,
    persisted: PersistedAppState
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(SettingsUiState(displayName = persisted.displayName, channelText = persisted.channelText))
    val state: StateFlow<SettingsUiState> = _state

    fun start() {
        scope.launch { environment.engine.observeTransportState().collectLatest { t -> _state.update { it.copy(transportState = t) } } }
        scope.launch { environment.engine.observePeers().collectLatest { peers -> _state.update { it.copy(nearbyPeerCount = peers.size) } } }
        scope.launch { _state.update { it.copy(identity = environment.engine.localIdentity()) } }
    }

    fun updateDisplayName(value: String) = _state.update { it.copy(displayName = value) }
    fun updateChannel(value: String) = _state.update { it.copy(channelText = value) }

    fun saveDisplayName() = scope.launch {
        environment.engine.updateDisplayName(state.value.displayName)
        appStateHolder.updateDisplayName(state.value.displayName)
    }

    fun switchChannel() = scope.launch {
        try {
            val channel = ChannelCode(state.value.channelText)
            environment.engine.switchChannel(channel)
            appStateHolder.updateChannel(channel)
            _state.update { it.copy(channelText = channel.normalized, error = null) }
        } catch (error: IllegalArgumentException) {
            _state.update { it.copy(error = error.message) }
        }
    }

    fun redactedLastError(): String = state.value.transportState.lastError?.let { RedactedLogger.redact(it) } ?: "None"
}
