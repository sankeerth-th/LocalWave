package com.localwave.mock

import com.localwave.core.model.ChannelCode
import com.localwave.core.model.ChatMessage
import com.localwave.core.model.DeliveryRoute
import com.localwave.core.model.EncryptedSharePackage
import com.localwave.core.model.ImportedSharePackage
import com.localwave.core.model.LocalIdentity
import com.localwave.core.model.MessageDirection
import com.localwave.core.model.MessageId
import com.localwave.core.model.MessageStatus
import com.localwave.core.model.OutboundAttachment
import com.localwave.core.model.PeerId
import com.localwave.core.model.PeerProfile
import com.localwave.core.model.PeerTrustState
import com.localwave.core.model.PresenceState
import com.localwave.core.model.AttachmentEnvelope
import com.localwave.core.model.TransferId
import com.localwave.core.model.TransferRecord
import com.localwave.core.model.TransferStatus
import com.localwave.core.model.TransportPermissionState
import com.localwave.core.model.TransportState
import com.localwave.core.protocol.LocalWaveEngine
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class MockLocalWaveEngine(
    private val testScheduler: Any? = null
) : LocalWaveEngine {
    private val autoDeliver = testScheduler == null
    private val peers = MutableStateFlow<List<PeerProfile>>(emptyList())
    private val messages = MutableStateFlow<Map<PeerId, List<ChatMessage>>>(emptyMap())
    private val transfers = MutableStateFlow<List<TransferRecord>>(emptyList())
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

    override suspend fun sendAttachment(attachment: OutboundAttachment, to: PeerId): TransferId {
        val transfer = TransferRecord(
            id = UUID.randomUUID(),
            peerId = to,
            fileName = attachment.fileName,
            byteCount = attachment.data.size,
            route = DeliveryRoute.L2CAP,
            status = TransferStatus.FAILED,
            failureReason = "Mock engine does not emulate BLE L2CAP attachment transfer."
        )
        transfers.update { it + transfer }
        throw IllegalStateException(transfer.failureReason)
    }

    override suspend fun exportEncryptedSharePackage(attachment: OutboundAttachment, to: PeerId): EncryptedSharePackage {
        val peer = peers.value.firstOrNull { it.id == to } ?: throw IllegalArgumentException("Peer unavailable.")
        val transferId = UUID.randomUUID()
        val envelope = AttachmentEnvelope(
            senderId = identity.peerId,
            recipientId = peer.id,
            timestampEpochMillis = System.currentTimeMillis(),
            transferId = transferId,
            replayCounter = 0uL,
            nonce = byteArrayOf(),
            ciphertext = attachment.data,
            tag = byteArrayOf()
        )
        val packageFile = EncryptedSharePackage(
            packageId = transferId,
            createdAtEpochMillis = envelope.timestampEpochMillis,
            route = DeliveryRoute.NATIVE_SHARE,
            senderId = identity.peerId,
            recipientId = peer.id,
            envelope = envelope
        )
        transfers.update {
            it + TransferRecord(transferId, to, attachment.fileName, attachment.data.size, DeliveryRoute.NATIVE_SHARE, TransferStatus.EXPORTED)
        }
        return packageFile
    }

    override suspend fun importEncryptedSharePackage(packageFile: EncryptedSharePackage): ImportedSharePackage {
        require(packageFile.version == 1.toUByte()) { "Unsupported LocalWave package version." }
        val attachment = OutboundAttachment("Mock Import", "application/octet-stream", packageFile.envelope.ciphertext)
        transfers.update {
            it + TransferRecord(packageFile.packageId, packageFile.senderId, attachment.fileName, attachment.data.size, DeliveryRoute.NATIVE_SHARE, TransferStatus.DELIVERED)
        }
        return ImportedSharePackage(packageFile.packageId, packageFile.senderId, attachment, byteArrayOf())
    }

    override suspend fun verifyPeer(peerId: PeerId, fingerprint: String) {
        var matched = false
        peers.update { current ->
            current.map { peer ->
                if (peer.id != peerId) {
                    peer
                } else {
                    matched = peer.fingerprint.equals(fingerprint.trim(), ignoreCase = true)
                    peer.copy(trustState = if (matched) PeerTrustState.VERIFIED else PeerTrustState.CHANGED)
                }
            }
        }
        if (!matched) throw IllegalArgumentException("This teammate's identity fingerprint does not match.")
    }

    override fun observePeers(): Flow<List<PeerProfile>> = peers
    override fun observeMessages(peerId: PeerId): Flow<List<ChatMessage>> = messages.map { it[peerId].orEmpty() }
    override fun observeTransfers(): Flow<List<TransferRecord>> = transfers
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
