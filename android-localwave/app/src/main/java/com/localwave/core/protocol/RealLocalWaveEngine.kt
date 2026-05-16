package com.localwave.core.protocol

import com.localwave.core.bluetooth.BlePacketFramer
import com.localwave.core.bluetooth.BluetoothTransport
import com.localwave.core.bluetooth.BluetoothTransportEvent
import com.localwave.core.bluetooth.PacketReassembler
import com.localwave.core.crypto.IdentityKeyStore
import com.localwave.core.crypto.SessionCrypto
import com.localwave.core.model.AttachmentEnvelope
import com.localwave.core.model.AttachmentPlaintext
import com.localwave.core.model.ChannelCode
import com.localwave.core.model.ChatMessage
import com.localwave.core.model.DeliveryRoute
import com.localwave.core.model.EncryptedSharePackage
import com.localwave.core.model.ImportedSharePackage
import com.localwave.core.model.LocalIdentity
import com.localwave.core.model.LocalWaveObjectPackage
import com.localwave.core.model.MessageDirection
import com.localwave.core.model.MessageId
import com.localwave.core.model.MessageStatus
import com.localwave.core.model.ObjectPiece
import com.localwave.core.model.ObjectPieceBatch
import com.localwave.core.model.OutboundAttachment
import com.localwave.core.model.PeerId
import com.localwave.core.model.PeerProfile
import com.localwave.core.model.PresenceState
import com.localwave.core.model.EncryptedObjectManifest
import com.localwave.core.model.TransferId
import com.localwave.core.model.TransferRecord
import com.localwave.core.model.TransferStatus
import com.localwave.core.model.TransportPacketKind
import com.localwave.core.model.TransportState
import com.localwave.core.model.WakeEvent
import java.security.MessageDigest
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
    private val wakeNotificationManager: WakeNotificationManager,
    private val objectStore: LocalWaveObjectFileStore? = null
) : LocalWaveEngine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val peers = MutableStateFlow<List<PeerProfile>>(emptyList())
    private val transportState = MutableStateFlow(TransportState())
    private val transfers = MutableStateFlow<List<TransferRecord>>(emptyList())
    private val objectTransfer = ObjectTransferCrypto(crypto)
    private val incomingObjects = mutableMapOf<String, IncomingObjectState>()
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

    override suspend fun sendAttachment(attachment: OutboundAttachment, to: PeerId): TransferId {
        val activeChannel = channel ?: throw IllegalArgumentException("Channel is not active.")
        val peer = peers.value.firstOrNull { it.id == to } ?: peerRepository.peer(to) ?: throw IllegalArgumentException("Peer unavailable.")
        val identity = identityStore.loadOrCreateIdentity(displayName)
        val packageFile = objectTransfer.createPackage(attachment, peer, identity, activeChannel)
        objectStore?.store(packageFile)
        val transferId = transferIdFor(packageFile.manifest.objectId)
        val transfer = TransferRecord(
            id = transferId,
            peerId = to,
            fileName = attachment.fileName,
            byteCount = attachment.data.size,
            route = DeliveryRoute.L2CAP,
            status = TransferStatus.TRANSFERRING
        )
        upsertTransfer(transfer)
        transport.send(ObjectTransferJson.encodeManifest(packageFile.manifest).toString().encodeToByteArray(), TransportPacketKind.OBJECT_MANIFEST, to)
        packageFile.pieces.chunked(4).forEach { pieces ->
            transport.sendBulk(ObjectTransferJson.encodePieceBatch(ObjectPieceBatch(objectId = packageFile.manifest.objectId, pieces = pieces)), to)
        }
        upsertTransfer(transfer.copy(status = TransferStatus.PENDING, updatedAtEpochMillis = System.currentTimeMillis()))
        return transferId
    }

    override suspend fun exportEncryptedSharePackage(attachment: OutboundAttachment, to: PeerId): EncryptedSharePackage {
        val activeChannel = channel ?: throw IllegalArgumentException("Channel is not active.")
        val peer = peers.value.firstOrNull { it.id == to } ?: peerRepository.peer(to) ?: throw IllegalArgumentException("Peer unavailable.")
        val identity = identityStore.loadOrCreateIdentity(displayName)
        val objectPackage = objectTransfer.createPackage(attachment, peer, identity, activeChannel)
        objectStore?.store(objectPackage)
        val transferId = transferIdFor(objectPackage.manifest.objectId)
        val packageFile = EncryptedSharePackage(
            version = 2.toUByte(),
            packageId = transferId,
            createdAtEpochMillis = objectPackage.manifest.createdAtEpochMillis,
            route = DeliveryRoute.NATIVE_SHARE,
            senderId = objectPackage.manifest.senderId,
            recipientId = objectPackage.manifest.recipientId,
            envelope = placeholderEnvelope(transferId, objectPackage.manifest),
            objectManifest = objectPackage.manifest,
            objectPieces = objectPackage.pieces,
            senderFingerprint = identity.fingerprint,
            senderAgreementPublicKey = identity.agreementPublicKey
        )
        upsertTransfer(
            TransferRecord(
                id = packageFile.packageId,
                peerId = to,
                fileName = attachment.fileName,
                byteCount = attachment.data.size,
                route = DeliveryRoute.NATIVE_SHARE,
                status = TransferStatus.EXPORTED
            )
        )
        return packageFile
    }

    override suspend fun importEncryptedSharePackage(packageFile: EncryptedSharePackage): ImportedSharePackage {
        val activeChannel = channel ?: throw IllegalArgumentException("Channel is not active.")
        require(packageFile.version == 1.toUByte() || packageFile.version == 2.toUByte()) { "Unsupported LocalWave package version." }
        if (packageFile.objectManifest != null && packageFile.objectPieces != null) {
            objectStore?.store(LocalWaveObjectPackage(packageFile.objectManifest, packageFile.objectPieces))
            val peer = peerForImportedObject(packageFile, packageFile.objectManifest)
            val result = objectTransfer.decryptPackage(LocalWaveObjectPackage(packageFile.objectManifest, packageFile.objectPieces), peer, activeChannel)
            upsertTransfer(
                TransferRecord(
                    id = packageFile.packageId,
                    peerId = packageFile.senderId,
                    fileName = result.attachment.fileName,
                    byteCount = result.attachment.data.size,
                    route = DeliveryRoute.NATIVE_SHARE,
                    status = TransferStatus.COMPLETED
                )
            )
            return ImportedSharePackage(packageFile.packageId, packageFile.senderId, result.attachment, result.receipt.verifiedPlainSha256)
        }
        val peer = peers.value.firstOrNull { it.id == packageFile.senderId } ?: peerRepository.peer(packageFile.senderId) ?: throw IllegalArgumentException("Peer unavailable.")
        val attachment = crypto.decryptAttachment(packageFile.envelope, peer, activeChannel)
        val verifiedHash = MessageDigest.getInstance("SHA-256").digest(attachment.data)
        upsertTransfer(
            TransferRecord(
                id = packageFile.packageId,
                peerId = packageFile.senderId,
                fileName = attachment.fileName,
                byteCount = attachment.data.size,
                route = DeliveryRoute.NATIVE_SHARE,
                status = TransferStatus.COMPLETED
            )
        )
        return ImportedSharePackage(packageFile.packageId, packageFile.senderId, attachment, verifiedHash)
    }

    override suspend fun verifyPeer(peerId: PeerId, fingerprint: String) {
        val current = peers.value.firstOrNull { it.id == peerId } ?: peerRepository.peer(peerId) ?: throw IllegalArgumentException("Peer unavailable.")
        if (!current.fingerprint.equals(fingerprint.trim(), ignoreCase = true)) {
            val changed = current.copy(trustState = com.localwave.core.model.PeerTrustState.CHANGED)
            peerRepository.upsert(changed)
            peers.value = peers.value.map { if (it.id == peerId) changed else it }
            throw IllegalArgumentException("This teammate's identity fingerprint does not match.")
        }
        val verified = current.copy(trustState = com.localwave.core.model.PeerTrustState.VERIFIED)
        peerRepository.upsert(verified)
        peers.value = peers.value.map { if (it.id == peerId) verified else it }
    }

    override fun observePeers(): Flow<List<PeerProfile>> = peers
    override fun observeMessages(peerId: PeerId): Flow<List<ChatMessage>> = messageRepository.observeMessages(peerId)
    override fun observeTransfers(): Flow<List<TransferRecord>> = transfers
    override fun observeTransportState(): Flow<TransportState> = combine(transport.state, transportState) { a, b -> if (b != TransportState()) b else a }
    override suspend fun localIdentity(): LocalIdentity = identityStore.loadOrCreateIdentity(displayName.ifBlank { "Local User" })

    private suspend fun handle(event: BluetoothTransportEvent) {
        when (event) {
            is BluetoothTransportEvent.PeerDiscovered -> {
                peerRepository.upsert(event.peer)
                peers.value = (peers.value.filterNot { it.id == event.peer.id } + event.peer).sortedBy { it.displayName.lowercase() }
            }
            is BluetoothTransportEvent.Packet -> handlePacket(event.data)
            is BluetoothTransportEvent.Bulk -> handleBulk(event.data)
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
            TransportPacketKind.OBJECT_MANIFEST -> {
                val manifest = ObjectTransferJson.decodeManifest(JSONObject(assembled.body.decodeToString()))
                incomingObjects.getOrPut(manifest.objectId) { IncomingObjectState() }.manifest = manifest
                objectStore?.store(manifest)
                upsertTransfer(
                    TransferRecord(
                        id = transferIdFor(manifest.objectId),
                        peerId = manifest.senderId,
                        fileName = "Encrypted object",
                        byteCount = manifest.ciphertext.size,
                        route = DeliveryRoute.L2CAP,
                        status = TransferStatus.MANIFEST_RECEIVED
                    )
                )
                tryCompleteObject(manifest.objectId)
            }
            TransportPacketKind.RECEIPT -> {
                val receipt = ObjectTransferJson.decodeReceipt(assembled.body)
                transfers.value = transfers.value.map {
                    if (it.id == receipt.transferId) it.copy(status = TransferStatus.COMPLETED, updatedAtEpochMillis = System.currentTimeMillis()) else it
                }
            }
            TransportPacketKind.PRESENCE,
            TransportPacketKind.OBJECT_CONTROL -> Unit
        }
    }

    private suspend fun handleBulk(data: ByteArray) {
        val batch = runCatching { ObjectTransferJson.decodePieceBatch(data) }.getOrNull()
        if (batch != null) {
            incomingObjects.getOrPut(batch.objectId) { IncomingObjectState() }.pieces.addAll(batch.pieces)
            objectStore?.storePieces(batch.pieces)
            incomingObjects[batch.objectId]?.manifest?.let { manifest ->
                upsertTransfer(
                    TransferRecord(
                        id = transferIdFor(batch.objectId),
                        peerId = manifest.senderId,
                        fileName = "Encrypted object",
                        byteCount = batch.pieces.sumOf { it.ciphertext.size },
                        route = DeliveryRoute.L2CAP,
                        status = TransferStatus.TRANSFERRING
                    )
                )
            }
            tryCompleteObject(batch.objectId)
            return
        }
        handleLegacyBulk(data)
    }

    private suspend fun handleLegacyBulk(data: ByteArray) {
        val activeChannel = channel ?: return
        val envelope = ProtocolJson.decodeAttachmentEnvelope(data)
        val peer = peers.value.firstOrNull { it.id == envelope.senderId } ?: peerRepository.peer(envelope.senderId) ?: return
        val attachment = crypto.decryptAttachment(envelope, peer, activeChannel)
        upsertTransfer(
            TransferRecord(
                id = envelope.transferId,
                peerId = peer.id,
                fileName = attachment.fileName,
                byteCount = attachment.data.size,
                route = DeliveryRoute.L2CAP,
                status = TransferStatus.COMPLETED
            )
        )
    }

    private suspend fun tryCompleteObject(objectId: String) {
        val activeChannel = channel ?: return
        val storedPackage = objectStore?.packageFor(objectId)
        val state = incomingObjects[objectId]
        val manifest = state?.manifest ?: storedPackage?.manifest ?: return
        val pieces = state?.pieces ?: storedPackage?.pieces ?: emptyList()
        runCatching {
            val peer = peerForImportedObject(null, manifest)
            val result = objectTransfer.decryptPackage(LocalWaveObjectPackage(manifest, pieces), peer, activeChannel)
            upsertTransfer(
                TransferRecord(
                    id = result.transferId,
                    peerId = peer.id,
                    fileName = result.attachment.fileName,
                    byteCount = result.attachment.data.size,
                    route = DeliveryRoute.L2CAP,
                    status = TransferStatus.COMPLETED
                )
            )
            incomingObjects.remove(objectId)
            transport.send(ObjectTransferJson.encodeReceipt(result.receipt), TransportPacketKind.RECEIPT, peer.id)
        }.onFailure {
            incomingObjects[objectId]?.lastError = it.message
        }
    }

    private suspend fun peerForImportedObject(packageFile: EncryptedSharePackage?, manifest: EncryptedObjectManifest): PeerProfile {
        val existing = peers.value.firstOrNull { it.id == manifest.senderId } ?: peerRepository.peer(manifest.senderId)
        if (existing != null) return existing
        val peer = PeerProfile(
            id = manifest.senderId,
            displayName = "Imported Peer",
            fingerprint = packageFile?.senderFingerprint ?: manifest.senderFingerprint,
            rssi = 0,
            lastSeenEpochMillis = System.currentTimeMillis(),
            state = PresenceState.RECENTLY_SEEN,
            trustState = com.localwave.core.model.PeerTrustState.UNVERIFIED,
            publicKeyData = packageFile?.senderAgreementPublicKey ?: manifest.senderAgreementPublicKey
        )
        peerRepository.upsert(peer)
        peers.value = (peers.value + peer).distinctBy { it.id }
        return peer
    }

    private fun upsertTransfer(record: TransferRecord) {
        transfers.value = transfers.value.filterNot { it.id == record.id } + record
    }

    private fun transferIdFor(objectId: String): TransferId =
        UUID.fromString(objectId.take(32).padEnd(32, '0').uuidFormat())

    private fun placeholderEnvelope(transferId: TransferId, manifest: EncryptedObjectManifest): AttachmentEnvelope =
        AttachmentEnvelope(
            senderId = manifest.senderId,
            recipientId = manifest.recipientId,
            timestampEpochMillis = manifest.createdAtEpochMillis,
            transferId = transferId,
            replayCounter = 0uL,
            nonce = ByteArray(0),
            ciphertext = ByteArray(0),
            tag = ByteArray(0)
        )
}

private data class IncomingObjectState(
    var manifest: EncryptedObjectManifest? = null,
    val pieces: MutableList<ObjectPiece> = mutableListOf(),
    var lastError: String? = null
)

private fun String.uuidFormat(): String = "${substring(0, 8)}-${substring(8, 12)}-${substring(12, 16)}-${substring(16, 20)}-${substring(20, 32)}"

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

    fun encodeAttachmentPlaintext(plaintext: AttachmentPlaintext): ByteArray =
        JSONObject()
            .put("fileName", plaintext.fileName)
            .put("contentType", plaintext.contentType)
            .put("byteCount", plaintext.byteCount)
            .put("sha256", Base64.getEncoder().encodeToString(plaintext.sha256))
            .put("payload", Base64.getEncoder().encodeToString(plaintext.payload))
            .toString()
            .encodeToByteArray()

    fun decodeAttachmentPlaintext(bytes: ByteArray): AttachmentPlaintext =
        JSONObject(bytes.decodeToString()).let { json ->
            AttachmentPlaintext(
                fileName = json.getString("fileName"),
                contentType = json.getString("contentType"),
                byteCount = json.getInt("byteCount"),
                sha256 = Base64.getDecoder().decode(json.getString("sha256")),
                payload = Base64.getDecoder().decode(json.getString("payload"))
            )
        }

    fun encodeAttachmentEnvelope(envelope: AttachmentEnvelope): ByteArray =
        JSONObject()
            .put("version", envelope.version.toInt())
            .put("senderId", envelope.senderId.value)
            .put("recipientId", envelope.recipientId.value)
            .put("timestamp", DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(envelope.timestampEpochMillis)))
            .put("transferId", envelope.transferId.toString().uppercase())
            .put("replayCounter", envelope.replayCounter.toLong())
            .put("nonce", Base64.getEncoder().encodeToString(envelope.nonce))
            .put("ciphertext", Base64.getEncoder().encodeToString(envelope.ciphertext))
            .put("tag", Base64.getEncoder().encodeToString(envelope.tag))
            .toString()
            .encodeToByteArray()

    fun decodeAttachmentEnvelope(bytes: ByteArray): AttachmentEnvelope =
        JSONObject(bytes.decodeToString()).let { json ->
            AttachmentEnvelope(
                version = json.getInt("version").toUByte(),
                senderId = PeerId(json.getString("senderId")),
                recipientId = PeerId(json.getString("recipientId")),
                timestampEpochMillis = Instant.parse(json.getString("timestamp")).toEpochMilli(),
                transferId = UUID.fromString(json.getString("transferId")),
                replayCounter = json.getLong("replayCounter").toULong(),
                nonce = Base64.getDecoder().decode(json.getString("nonce")),
                ciphertext = Base64.getDecoder().decode(json.getString("ciphertext")),
                tag = Base64.getDecoder().decode(json.getString("tag"))
            )
        }

    fun encodeEncryptedSharePackage(packageFile: EncryptedSharePackage): ByteArray =
        JSONObject()
            .put("version", packageFile.version.toInt())
            .put("packageId", packageFile.packageId.toString().uppercase())
            .put("createdAt", DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(packageFile.createdAtEpochMillis)))
            .put("route", packageFile.route.wireName())
            .put("senderId", packageFile.senderId.value)
            .put("recipientId", packageFile.recipientId.value)
            .put("envelope", JSONObject(encodeAttachmentEnvelope(packageFile.envelope).decodeToString()))
            .also { json ->
                packageFile.objectManifest?.let { json.put("objectManifest", ObjectTransferJson.encodeManifest(it)) }
                packageFile.objectPieces?.let { pieces -> json.put("objectPieces", org.json.JSONArray(pieces.map(ObjectTransferJson::encodePiece))) }
                packageFile.senderFingerprint?.let { json.put("senderFingerprint", it) }
                packageFile.senderAgreementPublicKey?.let { json.put("senderAgreementPublicKey", Base64.getEncoder().encodeToString(it)) }
            }
            .toString()
            .encodeToByteArray()

    fun decodeEncryptedSharePackage(bytes: ByteArray): EncryptedSharePackage =
        JSONObject(bytes.decodeToString()).let { json ->
            EncryptedSharePackage(
                version = json.getInt("version").toUByte(),
                packageId = UUID.fromString(json.getString("packageId")),
                createdAtEpochMillis = Instant.parse(json.getString("createdAt")).toEpochMilli(),
                route = deliveryRouteFromWire(json.getString("route")),
                senderId = PeerId(json.getString("senderId")),
                recipientId = PeerId(json.getString("recipientId")),
                envelope = decodeAttachmentEnvelope(json.getJSONObject("envelope").toString().encodeToByteArray()),
                objectManifest = json.optJSONObject("objectManifest")?.let(ObjectTransferJson::decodeManifest),
                objectPieces = json.optJSONArray("objectPieces")?.let { array ->
                    (0 until array.length()).map { ObjectTransferJson.decodePiece(array.getJSONObject(it)) }
                },
                senderFingerprint = json.optString("senderFingerprint").takeIf { it.isNotBlank() },
                senderAgreementPublicKey = json.optString("senderAgreementPublicKey").takeIf { it.isNotBlank() }?.let { Base64.getDecoder().decode(it) }
            )
        }
}

private fun DeliveryRoute.wireName(): String = when (this) {
    DeliveryRoute.GATT -> "gatt"
    DeliveryRoute.L2CAP -> "l2cap"
    DeliveryRoute.NATIVE_SHARE -> "nativeShare"
    DeliveryRoute.FIXED_RELAY -> "fixedRelay"
    DeliveryRoute.PHONE_RELAY -> "phoneRelay"
}

private fun deliveryRouteFromWire(value: String): DeliveryRoute = when (value) {
    "gatt", "GATT" -> DeliveryRoute.GATT
    "l2cap", "L2CAP" -> DeliveryRoute.L2CAP
    "nativeShare", "NATIVE_SHARE" -> DeliveryRoute.NATIVE_SHARE
    "fixedRelay", "FIXED_RELAY" -> DeliveryRoute.FIXED_RELAY
    "phoneRelay", "PHONE_RELAY" -> DeliveryRoute.PHONE_RELAY
    else -> DeliveryRoute.valueOf(value)
}
