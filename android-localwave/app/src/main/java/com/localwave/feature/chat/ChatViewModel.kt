package com.localwave.feature.chat

import com.localwave.core.model.ChatMessage
import com.localwave.core.model.MessageStatus
import com.localwave.core.model.PeerId
import com.localwave.core.model.PeerProfile
import com.localwave.core.model.PresenceState
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

data class ChatUiState(
    val peer: PeerProfile? = null,
    val messages: List<ChatMessage> = emptyList(),
    val draft: String = "",
    val wakeState: WakeButtonState = WakeButtonState.IDLE,
    val error: String? = null
) {
    val isPeerReachable: Boolean get() = peer?.state in setOf(PresenceState.AVAILABLE, PresenceState.CONNECTING)
    val canSend: Boolean get() = draft.isNotBlank() && draft.encodeToByteArray().size <= 4096 && isPeerReachable
}

class ChatViewModel(private val engine: LocalWaveEngine, private val peerId: PeerId) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state

    fun start() {
        scope.launch {
            engine.observePeers().collectLatest { peers ->
                _state.update { it.copy(peer = peers.firstOrNull { peer -> peer.id == peerId }) }
            }
        }
        scope.launch {
            engine.observeMessages(peerId).collectLatest { messages ->
                _state.update { it.copy(messages = messages.sortedBy { message -> message.sentAtEpochMillis }) }
            }
        }
    }

    fun updateDraft(value: String) {
        _state.update { it.copy(draft = value, error = if (value.encodeToByteArray().size > 4096) "Messages are limited to 4 KB in this version." else null) }
    }

    fun sendDraft() {
        val text = state.value.draft.trim()
        if (text.isEmpty()) return
        if (!state.value.isPeerReachable) {
            _state.update { it.copy(error = "That teammate is not currently reachable.") }
            return
        }
        if (text.encodeToByteArray().size > 4096) {
            _state.update { it.copy(error = "Messages are limited to 4 KB in this version.") }
            return
        }
        _state.update { it.copy(draft = "") }
        scope.launch {
            try {
                engine.sendMessage(text, peerId)
            } catch (error: Throwable) {
                _state.update { it.copy(error = error.message ?: "Message could not be sent.") }
            }
        }
    }

    fun retry(message: ChatMessage) {
        if (message.status == MessageStatus.FAILED) {
            updateDraft(message.text)
            sendDraft()
        }
    }

    fun sendWake() {
        val peer = state.value.peer
        if (peer?.state !in setOf(PresenceState.AVAILABLE, PresenceState.CONNECTING)) {
            _state.update { it.copy(wakeState = WakeButtonState.UNAVAILABLE) }
            return
        }
        _state.update { it.copy(wakeState = WakeButtonState.SENDING) }
        scope.launch {
            try {
                engine.sendWake(peerId)
                _state.update { it.copy(wakeState = WakeButtonState.SENT) }
            } catch (_: Throwable) {
                _state.update { it.copy(wakeState = WakeButtonState.FAILED, error = "Wake could not be sent. This person may not be reachable right now.") }
            }
        }
    }
}
