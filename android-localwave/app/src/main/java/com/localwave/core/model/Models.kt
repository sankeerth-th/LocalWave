package com.localwave.core.model

import java.util.Locale
import java.util.UUID
import kotlinx.serialization.Serializable

@JvmInline
@Serializable
value class PeerId(val value: String) {
    override fun toString(): String = value
}

typealias MessageId = UUID
typealias PacketId = UUID
typealias ConversationId = UUID
typealias TransferId = UUID

data class ChannelCode(val rawValue: String) {
    val normalized: String = normalize(rawValue)
    val id: String get() = normalized

    companion object {
        fun parse(value: String): ChannelCode = ChannelCode(value)

        fun normalize(value: String): String {
            val collapsed = value
                .trim()
                .split(Regex("\\s+"))
                .filter { it.isNotEmpty() }
                .joinToString(" ")
                .uppercase(Locale.ROOT)
            require(collapsed.length >= 3) { "Enter a Frequency Code with at least 3 characters." }
            require(collapsed.length <= 32) { "Frequency Codes are limited to 32 characters in Android v1." }
            return collapsed
        }
    }

    override fun equals(other: Any?): Boolean = other is ChannelCode && normalized == other.normalized
    override fun hashCode(): Int = normalized.hashCode()
    override fun toString(): String = normalized
}

enum class PresenceState {
    AVAILABLE,
    RECENTLY_SEEN,
    CONNECTING,
    SLEEPING,
    PERMISSION_NEEDED,
    UNREACHABLE;

    val label: String get() = when (this) {
        AVAILABLE -> "Available"
        RECENTLY_SEEN -> "Recently Seen"
        CONNECTING -> "Connecting"
        SLEEPING -> "Sleeping"
        PERMISSION_NEEDED -> "Permission Needed"
        UNREACHABLE -> "Unreachable"
    }
}

enum class MessageDirection {
    INCOMING,
    OUTGOING
}

enum class MessageStatus {
    PENDING,
    SENT,
    DELIVERED,
    FAILED
}

enum class TransportPermissionState {
    UNKNOWN,
    ALLOWED,
    DENIED,
    UNAVAILABLE,
    MISSING_SCAN_PERMISSION,
    MISSING_ADVERTISE_PERMISSION,
    MISSING_CONNECT_PERMISSION,
    MISSING_NOTIFICATION_PERMISSION,
    BLUETOOTH_DISABLED,
    BLE_UNSUPPORTED,
    BLE_ADVERTISER_UNSUPPORTED,
    FOREGROUND_SERVICE_UNAVAILABLE,
    LOCATION_PERMISSION_NEEDED
}

enum class WakeResult {
    SENT,
    PEER_UNAVAILABLE,
    PERMISSION_NEEDED,
    NOTIFICATION_DENIED,
    FAILED
}

enum class PeerTrustState {
    UNVERIFIED,
    VERIFIED,
    CHANGED,
    BLOCKED
}

enum class TransportCapability {
    GATT_MESSAGING,
    L2CAP_COC,
    NATIVE_SHARE_PACKAGE,
    FIXED_RELAY,
    PHONE_RELAY_BEST_EFFORT,
    OBJECT_CACHE,
    RELAY_CACHE,
    FEC_PIECES
}

enum class DeliveryRoute {
    GATT,
    L2CAP,
    NATIVE_SHARE,
    FIXED_RELAY,
    PHONE_RELAY
}

enum class TransferStatus {
    QUEUED,
    NEGOTIATING,
    ANNOUNCED,
    ACCEPTED,
    MANIFEST_RECEIVED,
    SESSION_NEGOTIATED,
    TRANSFERRING,
    SENDING,
    EXPORTED,
    IMPORTING,
    WAITING_FOR_PEER,
    WAITING_FOR_RELAY,
    VERIFYING,
    RECONSTRUCTING,
    DECRYPTING,
    COMPLETED,
    DELIVERED,
    PENDING,
    FAILED,
    EXPIRED,
    CANCELLED
}

data class PeerProfile(
    val id: PeerId,
    val displayName: String,
    val fingerprint: String,
    val rssi: Int,
    val lastSeenEpochMillis: Long,
    val state: PresenceState,
    val trustState: PeerTrustState = PeerTrustState.UNVERIFIED,
    val l2capPsm: Int? = null,
    val publicKeyData: ByteArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PeerProfile) return false
        return id == other.id &&
            displayName == other.displayName &&
            fingerprint == other.fingerprint &&
            rssi == other.rssi &&
            lastSeenEpochMillis == other.lastSeenEpochMillis &&
            state == other.state &&
            trustState == other.trustState &&
            l2capPsm == other.l2capPsm &&
            publicKeyData.contentEquals(other.publicKeyData)
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + displayName.hashCode()
        result = 31 * result + fingerprint.hashCode()
        result = 31 * result + rssi
        result = 31 * result + lastSeenEpochMillis.hashCode()
        result = 31 * result + state.hashCode()
        result = 31 * result + trustState.hashCode()
        result = 31 * result + (l2capPsm ?: 0)
        result = 31 * result + (publicKeyData?.contentHashCode() ?: 0)
        return result
    }
}

data class ChatMessage(
    val id: MessageId = UUID.randomUUID(),
    val peerId: PeerId,
    val text: String,
    val sentAtEpochMillis: Long = System.currentTimeMillis(),
    val direction: MessageDirection,
    val status: MessageStatus
)

data class TransportState(
    val isRunning: Boolean = false,
    val isScanning: Boolean = false,
    val isAdvertising: Boolean = false,
    val permission: TransportPermissionState = TransportPermissionState.UNKNOWN,
    val bluetoothEnabled: Boolean = true,
    val scanPermission: Boolean = true,
    val advertisePermission: Boolean = true,
    val connectPermission: Boolean = true,
    val notificationPermission: Boolean = false,
    val bleSupported: Boolean = true,
    val advertiserSupported: Boolean = true,
    val activeModeAvailable: Boolean = false,
    val batteryRestricted: Boolean = false,
    val lastError: String? = null,
    val wakeFallback: WakeEvent? = null
)

data class WakeEvent(
    val peerId: PeerId,
    val displayName: String,
    val receivedAtEpochMillis: Long = System.currentTimeMillis()
)

data class LocalIdentity(
    val peerId: PeerId,
    val displayName: String,
    val agreementPublicKey: ByteArray,
    val signingPublicKey: ByteArray,
    val fingerprint: String
)

data class MessageEnvelope(
    val version: UByte = 1u,
    val senderId: PeerId,
    val recipientId: PeerId,
    val timestampEpochMillis: Long,
    val messageId: MessageId,
    val replayCounter: ULong,
    val nonce: ByteArray,
    val ciphertext: ByteArray,
    val tag: ByteArray
)

data class WakeEnvelope(
    val version: UByte = 1u,
    val senderId: PeerId,
    val recipientId: PeerId,
    val timestampEpochMillis: Long,
    val replayCounter: ULong,
    val nonce: ByteArray,
    val ciphertext: ByteArray,
    val tag: ByteArray
)

data class OutboundAttachment(
    val fileName: String,
    val contentType: String,
    val data: ByteArray
) {
    init {
        require(fileName.trim().isNotEmpty()) { "Attachment file name is required." }
        require(data.size <= MAX_NATIVE_SHARE_BYTES) { "Attachments are limited to 50 MB for Bluetooth-safe sharing." }
    }

    companion object {
        const val MAX_INLINE_BYTES: Int = 64 * 1024
        const val MAX_NATIVE_SHARE_BYTES: Int = 50 * 1024 * 1024
    }
}

data class AttachmentPlaintext(
    val fileName: String,
    val contentType: String,
    val byteCount: Int,
    val sha256: ByteArray,
    val payload: ByteArray
)

data class AttachmentEnvelope(
    val version: UByte = 1u,
    val senderId: PeerId,
    val recipientId: PeerId,
    val timestampEpochMillis: Long,
    val transferId: TransferId,
    val replayCounter: ULong,
    val nonce: ByteArray,
    val ciphertext: ByteArray,
    val tag: ByteArray
)

data class EncryptedSharePackage(
    val version: UByte = 1u,
    val packageId: TransferId,
    val createdAtEpochMillis: Long,
    val route: DeliveryRoute,
    val senderId: PeerId,
    val recipientId: PeerId,
    val envelope: AttachmentEnvelope,
    val objectManifest: EncryptedObjectManifest? = null,
    val objectPieces: List<ObjectPiece>? = null,
    val senderFingerprint: String? = null,
    val senderAgreementPublicKey: ByteArray? = null
) {
    companion object {
        const val FILE_EXTENSION: String = "localwavepkg"
    }
}

enum class ObjectPieceKind {
    DATA,
    RECOVERY
}

enum class ObjectRelayPolicy {
    DIRECT_ONLY,
    TRUSTED_PEERS_ONLY
}

data class ObjectManifestPlaintext(
    val objectProtocolVersion: UByte = 1u,
    val senderId: PeerId,
    val recipientIds: List<PeerId>,
    val objectType: String,
    val fileName: String,
    val mimeType: String,
    val plainSize: Int,
    val encryptedSize: Int,
    val pieceSize: Int,
    val pieceCount: Int,
    val recoveryPieceCount: Int,
    val dataPieceHashes: List<ByteArray>,
    val plainPieceHashes: List<ByteArray>,
    val merkleRoot: ByteArray,
    val plainSha256: ByteArray,
    val createdAtEpochMillis: Long,
    val expiresAtEpochMillis: Long,
    val relayPolicy: ObjectRelayPolicy,
    val previewPolicy: String
)

data class EncryptedObjectManifest(
    val objectProtocolVersion: UByte = 1u,
    val objectId: String,
    val senderId: PeerId,
    val recipientId: PeerId,
    val createdAtEpochMillis: Long,
    val expiresAtEpochMillis: Long,
    val nonce: ByteArray,
    val ciphertext: ByteArray,
    val tag: ByteArray,
    val wrappedObjectKeyNonce: ByteArray,
    val wrappedObjectKeyCiphertext: ByteArray,
    val wrappedObjectKeyTag: ByteArray,
    val senderFingerprint: String,
    val senderAgreementPublicKey: ByteArray
)

data class ObjectPiece(
    val objectProtocolVersion: UByte = 1u,
    val objectId: String,
    val pieceIndex: Int,
    val pieceKind: ObjectPieceKind,
    val nonce: ByteArray,
    val ciphertext: ByteArray,
    val tag: ByteArray,
    val pieceHash: ByteArray,
    val merkleProof: List<ByteArray> = emptyList()
)

data class ObjectPieceBatch(
    val objectProtocolVersion: UByte = 1u,
    val objectId: String,
    val pieces: List<ObjectPiece>
)

data class PieceInventory(
    val objectProtocolVersion: UByte = 1u,
    val peerId: PeerId,
    val objectId: String,
    val dataPieceIndexes: List<Int>,
    val recoveryPieceIndexes: List<Int>,
    val updatedAtEpochMillis: Long
)

data class ResumeToken(
    val objectProtocolVersion: UByte = 1u,
    val objectId: String,
    val receiverId: PeerId,
    val receivedPieceIndexes: List<Int>,
    val receivedBitmapHash: ByteArray,
    val lastVerifiedPiece: Int,
    val timestampEpochMillis: Long
)

data class RelayToken(
    val objectProtocolVersion: UByte = 1u,
    val objectId: String,
    val allowedRelayPeerId: PeerId,
    val recipientId: PeerId,
    val expiresAtEpochMillis: Long,
    val maxBytes: Int,
    val signature: ByteArray
)

data class TransferReceipt(
    val objectProtocolVersion: UByte = 1u,
    val transferId: TransferId,
    val objectId: String,
    val senderId: PeerId,
    val recipientId: PeerId,
    val completedAtEpochMillis: Long,
    val verifiedPlainSha256: ByteArray
)

data class LocalWaveObjectPackage(
    val manifest: EncryptedObjectManifest,
    val pieces: List<ObjectPiece>
)

data class ObjectTransferResult(
    val transferId: TransferId,
    val objectId: String,
    val attachment: OutboundAttachment,
    val receipt: TransferReceipt
)

data class ImportedSharePackage(
    val transferId: TransferId,
    val senderId: PeerId,
    val attachment: OutboundAttachment,
    val verifiedHash: ByteArray
)

data class TransferRecord(
    val id: TransferId,
    val peerId: PeerId,
    val fileName: String,
    val byteCount: Int,
    val route: DeliveryRoute,
    val status: TransferStatus,
    val updatedAtEpochMillis: Long = System.currentTimeMillis(),
    val failureReason: String? = null
)

data class RelayChunk(
    val id: TransferId,
    val sourcePeerId: PeerId,
    val destinationPeerId: PeerId,
    val route: DeliveryRoute,
    val expiresAtEpochMillis: Long,
    val payload: ByteArray
)

class RelayChunkStore(private val maxChunks: Int) {
    private val chunksById = linkedMapOf<TransferId, RelayChunk>()

    @Synchronized
    fun insert(chunk: RelayChunk, now: Long = System.currentTimeMillis()) {
        pruneExpired(now)
        require(chunk.expiresAtEpochMillis > now) { "Relay chunk is already expired." }
        chunksById[chunk.id] = chunk
        while (chunksById.size > maxOf(1, maxChunks)) {
            val oldest = chunksById.values.minBy { it.expiresAtEpochMillis }
            chunksById.remove(oldest.id)
        }
    }

    @Synchronized
    fun chunksFor(destinationPeerId: PeerId, now: Long = System.currentTimeMillis()): List<RelayChunk> {
        pruneExpired(now)
        return chunksById.values
            .filter { it.destinationPeerId == destinationPeerId }
            .sortedBy { it.expiresAtEpochMillis }
    }

    @Synchronized
    fun remove(ids: List<TransferId>) {
        ids.forEach { chunksById.remove(it) }
    }

    private fun pruneExpired(now: Long) {
        chunksById.values.removeAll { it.expiresAtEpochMillis <= now }
    }
}

data class DeliveryReceipt(
    val messageId: MessageId,
    val senderId: PeerId,
    val recipientId: PeerId,
    val deliveredAtEpochMillis: Long
)

data class PeerIntroEnvelope(
    val version: UByte = 1u,
    val peerId: PeerId,
    val displayName: String,
    val fingerprint: String,
    val agreementPublicKey: ByteArray,
    val sentAtEpochMillis: Long = System.currentTimeMillis()
)

enum class TransportPacketKind(val wireValue: UByte) {
    PRESENCE(1u),
    MESSAGE(2u),
    WAKE(3u),
    RECEIPT(4u),
    OBJECT_MANIFEST(5u),
    OBJECT_CONTROL(6u);

    companion object {
        fun fromWire(value: UByte): TransportPacketKind =
            entries.firstOrNull { it.wireValue == value } ?: throw IllegalArgumentException("Unknown packet kind.")
    }
}

enum class WakeButtonState {
    IDLE,
    SENDING,
    SENT,
    UNAVAILABLE,
    PERMISSION_NEEDED,
    FAILED
}
