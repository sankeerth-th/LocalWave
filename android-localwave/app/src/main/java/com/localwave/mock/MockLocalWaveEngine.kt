package com.localwave.mock

import com.localwave.core.model.ChannelCode
import com.localwave.core.model.ChatMessage
import com.localwave.core.model.LocalIdentity
import com.localwave.core.model.MessageDirection
import com.localwave.core.model.MessageId
import com.localwave.core.model.MessageStatus
import com.localwave.core.model.PeerId
import com.localwave.core.model.PeerProfile
import com.localwave.core.model.PresenceState
import com.localwave.core.model.TransportPermissionState
import com.localwave.core.model.TransportState
import com.localwave.core.protocol.LocalWaveEngine
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class MockLocalWaveEngine(
    private val testScheduler: Any? = null
) : LocalWaveEngine {
    private val autoDeliver = testScheduler == null
    private val peers = MutableStateFlow<List<PeerProfile>>(emptyList())
    private val messages = MutableStateFlow<Map<PeerId, List<ChatMessage>>>(emptyMap())
    private val state = MutableStateFlow(TransportState(permission = TransportPermissionState.UNKNOWN))
    private var identity = LocalIdentity(
        peerId = PeerId("android-local"),
        displayName = "Android",
        agreementPublicKey = byteArrayOf(1, 2, 3),
        signingPublicKey = byteArrayOf(4, 5, 6),
        fingerprint = "android-mock-fingerprint"
    )

    override suspend fun start(channel: ChannelCode, displayName: String) {
        identity = identity.copy(displayName = displayName)
        peers.value = if (channel.normalized.startsWith("DOCK-B")) {
            listOf(mockPeer("mock-jules", "Jules", -51))
        } else {
            listOf(mockPeer("mock-alex", "Alex", -43), mockPeer("mock-sam", "Sam", -58))
        }
        state.value = TransportState(isRunning = true, isScanning = true, isAdvertising = true, permission = TransportPermissionState.ALLOWED)
    }

    override suspend fun stop() {
        state.value = TransportState(permission = TransportPermissionState.ALLOWED)
    }

    override suspend fun updateDisplayName(displayName: String) {
        identity = identity.copy(displayName = displayName)
    }

    override suspend fun switchChannel(channel: ChannelCode) {
        peers.value = emptyList()
        start(channel, identity.displayName)
    }

    override suspend fun sendMessage(text: String, to: PeerId): MessageId {
        val message = ChatMessage(peerId = to, text = text, direction = MessageDirection.OUTGOING, status = MessageStatus.PENDING)
        messages.update { current -> current + (to to (current[to].orEmpty() + message)) }
        if (autoDeliver) {
            messages.update { current ->
                current + (to to current[to].orEmpty().map { if (it.id == message.id) it.copy(status = MessageStatus.DELIVERED) else it })
            }
        }
        return message.id
    }

    override suspend fun sendWake(to: PeerId) {
        if (state.value.permission != TransportPermissionState.ALLOWED) throw IllegalStateException("Wake unavailable.")
    }

    override fun observePeers(): Flow<List<PeerProfile>> = peers
    override fun observeMessages(peerId: PeerId): Flow<List<ChatMessage>> = MutableStateFlow(messages.value[peerId].orEmpty())
    override fun observeTransportState(): Flow<TransportState> = state
    override suspend fun localIdentity(): LocalIdentity = identity

    fun currentPeers(): List<PeerProfile> = peers.value
    fun currentMessages(peerId: PeerId): List<ChatMessage> = messages.value[peerId].orEmpty()

    private fun mockPeer(id: String, name: String, rssi: Int): PeerProfile = PeerProfile(
        id = PeerId(id),
        displayName = name,
        fingerprint = "$id-fingerprint",
        rssi = rssi,
        lastSeenEpochMillis = System.currentTimeMillis(),
        state = PresenceState.AVAILABLE,
        publicKeyData = byteArrayOf(7, 8, 9)
    )
}
