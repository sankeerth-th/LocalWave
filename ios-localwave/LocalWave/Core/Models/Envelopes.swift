import Foundation

public struct MessageEnvelope: Codable, Sendable, Equatable {
    public var version: UInt8
    public var senderId: PeerID
    public var recipientId: PeerID
    public var timestamp: Date
    public var messageId: MessageID
    public var replayCounter: UInt64
    public var nonce: Data
    public var ciphertext: Data
    public var tag: Data
}

public struct WakeEnvelope: Codable, Sendable, Equatable {
    public var version: UInt8
    public var senderId: PeerID
    public var recipientId: PeerID
    public var timestamp: Date
    public var replayCounter: UInt64
    public var nonce: Data
    public var ciphertext: Data
    public var tag: Data
}

public enum TransportCapability: String, Codable, Sendable, CaseIterable {
    case gattMessaging
    case l2capCoc
    case nativeSharePackage
    case fixedRelay
    case phoneRelayBestEffort
    case objectCache
    case relayCache
    case fecPieces
}

public enum DeliveryRoute: String, Codable, Sendable, CaseIterable {
    case gatt
    case l2cap
    case nativeShare
    case fixedRelay
    case phoneRelay
}

public enum TransferStatus: String, Codable, Sendable, CaseIterable {
    case queued
    case negotiating
    case announced
    case accepted
    case manifestReceived
    case sessionNegotiated
    case transferring
    case sending
    case exported
    case importing
    case waitingForPeer
    case waitingForRelay
    case verifying
    case reconstructing
    case decrypting
    case completed
    case delivered
    case pending
    case failed
    case expired
    case cancelled
}

public struct OutboundAttachment: Codable, Sendable, Equatable {
    public static let maxInlineBytes = 64 * 1024
    public static let maxNativeShareBytes = 50 * 1024 * 1024

    public var fileName: String
    public var contentType: String
    public var data: Data

    public init(fileName: String, contentType: String, data: Data) throws {
        let cleanName = fileName.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanName.isEmpty else { throw LocalWaveError.malformedPacket }
        guard data.count <= Self.maxNativeShareBytes else {
            throw LocalWaveError.attachmentTooLarge(limitBytes: Self.maxNativeShareBytes)
        }
        self.fileName = cleanName
        self.contentType = contentType.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "application/octet-stream" : contentType
        self.data = data
    }
}

public struct AttachmentPlaintext: Codable, Sendable, Equatable {
    public var fileName: String
    public var contentType: String
    public var byteCount: Int
    public var sha256: Data
    public var payload: Data
}

public struct AttachmentEnvelope: Codable, Sendable, Equatable {
    public var version: UInt8
    public var senderId: PeerID
    public var recipientId: PeerID
    public var timestamp: Date
    public var transferId: TransferID
    public var replayCounter: UInt64
    public var nonce: Data
    public var ciphertext: Data
    public var tag: Data
}

public struct EncryptedSharePackage: Codable, Sendable, Equatable {
    public static let fileExtension = "localwavepkg"

    public var version: UInt8
    public var packageId: TransferID
    public var createdAt: Date
    public var route: DeliveryRoute
    public var senderId: PeerID
    public var recipientId: PeerID
    public var envelope: AttachmentEnvelope
    public var objectManifest: EncryptedObjectManifest? = nil
    public var objectPieces: [ObjectPiece]? = nil
    public var senderFingerprint: String? = nil
    public var senderAgreementPublicKey: Data? = nil
}

public struct ImportedSharePackage: Sendable, Equatable {
    public var transferId: TransferID
    public var senderId: PeerID
    public var attachment: OutboundAttachment
    public var verifiedHash: Data
}

public struct TransferRecord: Identifiable, Codable, Sendable, Equatable {
    public var id: TransferID
    public var peerId: PeerID
    public var fileName: String
    public var byteCount: Int
    public var route: DeliveryRoute
    public var status: TransferStatus
    public var updatedAt: Date
    public var failureReason: String?
}

public struct DeliveryReceipt: Codable, Sendable, Equatable {
    public var messageId: MessageID
    public var senderId: PeerID
    public var recipientId: PeerID
    public var deliveredAt: Date
}

public struct PeerIntroEnvelope: Codable, Sendable, Equatable {
    public var version: UInt8
    public var peerId: PeerID
    public var displayName: String
    public var fingerprint: String
    public var agreementPublicKey: Data
    public var sentAt: Date

    public init(
        version: UInt8 = 1,
        peerId: PeerID,
        displayName: String,
        fingerprint: String,
        agreementPublicKey: Data,
        sentAt: Date = Date()
    ) {
        self.version = version
        self.peerId = peerId
        self.displayName = displayName
        self.fingerprint = fingerprint
        self.agreementPublicKey = agreementPublicKey
        self.sentAt = sentAt
    }
}

public struct InviteAuthProof: Codable, Sendable, Equatable {
    public var version: UInt8
    public var senderId: PeerID
    public var recipientId: PeerID
    public var nonce: Data
    public var proof: Data

    public init(version: UInt8 = 1, senderId: PeerID, recipientId: PeerID, nonce: Data, proof: Data) {
        self.version = version
        self.senderId = senderId
        self.recipientId = recipientId
        self.nonce = nonce
        self.proof = proof
    }
}

public struct RelayChunk: Identifiable, Codable, Sendable, Equatable {
    public var id: TransferID
    public var sourcePeerId: PeerID
    public var destinationPeerId: PeerID
    public var route: DeliveryRoute
    public var expiresAt: Date
    public var payload: Data

    public init(id: TransferID, sourcePeerId: PeerID, destinationPeerId: PeerID, route: DeliveryRoute, expiresAt: Date, payload: Data) {
        self.id = id
        self.sourcePeerId = sourcePeerId
        self.destinationPeerId = destinationPeerId
        self.route = route
        self.expiresAt = expiresAt
        self.payload = payload
    }
}

public struct RelayChunkStore: Sendable {
    private let maxChunks: Int
    private var chunksById: [TransferID: RelayChunk] = [:]

    public init(maxChunks: Int) {
        self.maxChunks = max(1, maxChunks)
    }

    public mutating func insert(_ chunk: RelayChunk, now: Date = Date()) throws {
        pruneExpired(now: now)
        guard chunk.expiresAt > now else { throw LocalWaveError.transferUnavailable("Relay chunk is already expired.") }
        chunksById[chunk.id] = chunk
        if chunksById.count > maxChunks {
            let overflow = chunksById.values.sorted { $0.expiresAt < $1.expiresAt }.prefix(chunksById.count - maxChunks)
            overflow.forEach { chunksById[$0.id] = nil }
        }
    }

    public mutating func chunks(for destinationPeerId: PeerID, now: Date = Date()) -> [RelayChunk] {
        pruneExpired(now: now)
        return chunksById.values
            .filter { $0.destinationPeerId == destinationPeerId }
            .sorted { $0.expiresAt < $1.expiresAt }
    }

    public mutating func remove(_ ids: [TransferID]) {
        ids.forEach { chunksById[$0] = nil }
    }

    private mutating func pruneExpired(now: Date) {
        chunksById = chunksById.filter { $0.value.expiresAt > now }
    }
}
