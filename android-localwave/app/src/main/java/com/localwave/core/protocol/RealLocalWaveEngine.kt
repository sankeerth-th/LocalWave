package com.localwave.core.protocol

import com.localwave.core.bluetooth.BlePacketFramer
import com.localwave.core.bluetooth.BluetoothTransport
import com.localwave.core.bluetooth.BluetoothTransportEvent
import com.localwave.core.bluetooth.PacketReassembler
import com.localwave.core.crypto.IdentityKeyStore
import com.localwave.core.crypto.SessionCrypto
import com.localwave.core.model.ChannelCode
import com.localwave.core.model.ChatMessage
import com.localwave.core.model.LocalIdentity
import com.localwave.core.model.MessageDirection
import com.localwave.core.model.MessageId
import com.localwave.core.model.MessageStatus
import com.localwave.core.model.PeerId
import com.localwave.core.model.PeerProfile
import com.localwave.core.model.TransportPacketKind
import com.localwave.core.model.TransportState
import com.localwave.core.model.WakeEvent
import com.localwave.core.notifications.WakeNotificationManager
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.json.JSONObject

class RealLocalWaveEngine(
    private val identityStore: IdentityKeyStore,
    private val crypto: SessionCrypto,
    private val peerRepository: PeerRepository,
    private val messageRepository: MessageRepository,
    private val transport: BluetoothTransport,
    private val wakeNotificationManager: WakeNotificationManager
) : LocalWaveEngine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val peers = MutableStateFlow<List<PeerProfile>>(emptyList())
    private val transportState = MutableStateFlow(TransportState())
    private val framer = BlePacketFramer()
    private val reassembler = PacketReassembler()
    private var channel: ChannelCode? = null
    private var displayName: String = ""
    private var replayCounter = 1L

    override suspend fun start(channel: ChannelCode, displayName: String) {
        val trimmed = displayName.trim()
        require(trimmed.isNotEmpty()) { "Enter a display name before joining a channel." }
        this.channel = channel
        this.displayName = trimmed
        val identity = identityStore.loadOrCreateIdentity(trimmed)
        transport.start(channel, identity)
        scope.launch {
            transport.observeEvents().collect { event -> handle(event) }
        }
        scope.launch {
            peerRepository.observePeers().collect { peers.value = it }
        }
    }

    override suspend fun stop() {
        transport.stop()
        transportState.value = TransportState()
    }

    override suspend fun updateDisplayName(displayName: String) {
        this.displayName = displayName.trim()
        identityStore.updateDisplayName(this.displayName)
    }

    override suspend fun switchChannel(channel: ChannelCode) {
        stop()
        peerRepository.clearRecentlySeen()
        start(channel, displayName)
    }

    override suspend fun sendMessage(text: String, to: PeerId): MessageId {
        val activeChannel = channel ?: throw IllegalArgumentException("Channel is not active.")
        val peer = peers.value.firstOrNull { it.id == to } ?: peerRepository.peer(to) ?: throw IllegalArgumentException("Peer unavailable.")
        val message = ChatMessage(peerId = to, text = text, direction = MessageDirection.OUTGOING, status = MessageStatus.PENDING)
        messageRepository.save(message)
        val envelope = crypto.encryptMessage(text, peer, replayCounter++, activeChannel)
        val body = ProtocolJson.encodeMessageEnvelope(envelope)
        transport.send(body, TransportPacketKind.MESSAGE, to)
        messageRepository.updateStatus(message.id, MessageStatus.SENT)
        return message.id
    }

    override suspend fun sendWake(to: PeerId) {
        val activeChannel = channel ?: throw IllegalArgumentException("Channel is not active.")
        val peer = peers.value.firstOrNull { it.id == to } ?: peerRepository.peer(to) ?: throw IllegalArgumentException("Peer unavailable.")
        val wake = crypto.encryptWake(peer, replayCounter++, activeChannel)
        transport.send(ProtocolJson.encodeWakeEnvelope(wake), TransportPacketKind.WAKE, to)
    }

    override fun observePeers(): Flow<List<PeerProfile>> = peers
    override fun observeMessages(peerId: PeerId): Flow<List<ChatMessage>> = messageRepository.observeMessages(peerId)
    override fun observeTransportState(): Flow<TransportState> = combine(transport.state, transportState) { a, b -> if (b != TransportState()) b else a }
    override suspend fun localIdentity(): LocalIdentity = identityStore.loadOrCreateIdentity(displayName.ifBlank { "Local User" })

    private suspend fun handle(event: BluetoothTransportEvent) {
        when (event) {
            is BluetoothTransportEvent.PeerDiscovered -> {
                peerRepository.upsert(event.peer)
                peers.value = (peers.value.filterNot { it.id == event.peer.id } + event.peer).sortedBy { it.displayName.lowercase() }
            }
            is BluetoothTransportEvent.Packet -> handlePacket(event.data)
            is BluetoothTransportEvent.StateChanged -> transportState.value = event.state
        }
    }

    private suspend fun handlePacket(data: ByteArray) {
        val activeChannel = channel ?: return
        val packet = framer.decode(data)
        val assembled = reassembler.receive(packet) ?: return
        when (assembled.kind) {
            TransportPacketKind.MESSAGE -> {
                val envelope = ProtocolJson.decodeMessageEnvelope(assembled.body)
                val peer = peers.value.firstOrNull { it.id == envelope.senderId } ?: peerRepository.peer(envelope.senderId) ?: return
                val text = crypto.decryptMessage(envelope, peer, activeChannel)
                messageRepository.save(ChatMessage(envelope.messageId, peer.id, text, envelope.timestampEpochMillis, MessageDirection.INCOMING, MessageStatus.DELIVERED))
            }
            TransportPacketKind.WAKE -> {
                val envelope = ProtocolJson.decodeWakeEnvelope(assembled.body)
                val peer = peers.value.firstOrNull { it.id == envelope.senderId } ?: peerRepository.peer(envelope.senderId) ?: return
                crypto.decryptWake(envelope, peer, activeChannel)
                if (wakeNotificationManager.canNotify()) {
                    wakeNotificationManager.showWake(peer.displayName)
                } else {
                    transportState.value = transportState.value.copy(wakeFallback = WakeEvent(peer.id, peer.displayName))
                }
            }
            TransportPacketKind.PRESENCE,
            TransportPacketKind.RECEIPT -> Unit
        }
    }
}

object ProtocolJson {
    fun encodeMessageEnvelope(envelope: com.localwave.core.model.MessageEnvelope): ByteArray =
        JSONObject()
            .put("version", envelope.version.toInt())
            .put("senderId", envelope.senderId.value)
            .put("recipientId", envelope.recipientId.value)
            .put("timestamp", DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(envelope.timestampEpochMillis)))
            .put("messageId", envelope.messageId.toString().uppercase())
            .put("replayCounter", envelope.replayCounter.toLong())
            .put("nonce", Base64.getEncoder().encodeToString(envelope.nonce))
            .put("ciphertext", Base64.getEncoder().encodeToString(envelope.ciphertext))
            .put("tag", Base64.getEncoder().encodeToString(envelope.tag))
            .toString()
            .encodeToByteArray()

    fun decodeMessageEnvelope(bytes: ByteArray): com.localwave.core.model.MessageEnvelope =
        JSONObject(bytes.decodeToString()).let { json ->
            com.localwave.core.model.MessageEnvelope(
                version = json.getInt("version").toUByte(),
                senderId = PeerId(json.getString("senderId")),
                recipientId = PeerId(json.getString("recipientId")),
                timestampEpochMillis = Instant.parse(json.getString("timestamp")).toEpochMilli(),
                messageId = UUID.fromString(json.getString("messageId")),
                replayCounter = json.getLong("replayCounter").toULong(),
                nonce = Base64.getDecoder().decode(json.getString("nonce")),
                ciphertext = Base64.getDecoder().decode(json.getString("ciphertext")),
                tag = Base64.getDecoder().decode(json.getString("tag"))
            )
        }

    fun encodeWakeEnvelope(envelope: com.localwave.core.model.WakeEnvelope): ByteArray =
        JSONObject()
            .put("version", envelope.version.toInt())
            .put("senderId", envelope.senderId.value)
            .put("recipientId", envelope.recipientId.value)
            .put("timestamp", DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(envelope.timestampEpochMillis)))
            .put("replayCounter", envelope.replayCounter.toLong())
            .put("nonce", Base64.getEncoder().encodeToString(envelope.nonce))
            .put("ciphertext", Base64.getEncoder().encodeToString(envelope.ciphertext))
            .put("tag", Base64.getEncoder().encodeToString(envelope.tag))
            .toString()
            .encodeToByteArray()

    fun decodeWakeEnvelope(bytes: ByteArray): com.localwave.core.model.WakeEnvelope =
        JSONObject(bytes.decodeToString()).let { json ->
            com.localwave.core.model.WakeEnvelope(
                version = json.getInt("version").toUByte(),
                senderId = PeerId(json.getString("senderId")),
                recipientId = PeerId(json.getString("recipientId")),
                timestampEpochMillis = Instant.parse(json.getString("timestamp")).toEpochMilli(),
                replayCounter = json.getLong("replayCounter").toULong(),
                nonce = Base64.getDecoder().decode(json.getString("nonce")),
                ciphertext = Base64.getDecoder().decode(json.getString("ciphertext")),
                tag = Base64.getDecoder().decode(json.getString("tag"))
            )
        }
}
