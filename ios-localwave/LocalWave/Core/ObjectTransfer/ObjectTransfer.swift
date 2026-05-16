import CryptoKit
import Foundation

public enum ObjectPieceKind: String, Codable, Sendable, CaseIterable {
    case data
    case recovery
}

public enum ObjectRelayPolicy: String, Codable, Sendable, CaseIterable {
    case directOnly
    case trustedPeersOnly
}

public struct ObjectManifestPlaintext: Codable, Sendable, Equatable {
    public static let protocolVersion: UInt8 = 1

    public var objectProtocolVersion: UInt8
    public var senderId: PeerID
    public var recipientIds: [PeerID]
    public var objectType: String
    public var fileName: String
    public var mimeType: String
    public var plainSize: Int
    public var encryptedSize: Int
    public var pieceSize: Int
    public var pieceCount: Int
    public var recoveryPieceCount: Int
    public var dataPieceHashes: [Data]
    public var plainPieceHashes: [Data]
    public var merkleRoot: Data
    public var plainSHA256: Data
    public var createdAtEpochMillis: Int64
    public var expiresAtEpochMillis: Int64
    public var relayPolicy: ObjectRelayPolicy
    public var previewPolicy: String
}

public struct EncryptedObjectManifest: Codable, Sendable, Equatable {
    public var objectProtocolVersion: UInt8
    public var objectId: String
    public var senderId: PeerID
    public var recipientId: PeerID
    public var createdAtEpochMillis: Int64
    public var expiresAtEpochMillis: Int64
    public var nonce: Data
    public var ciphertext: Data
    public var tag: Data
    public var wrappedObjectKeyNonce: Data
    public var wrappedObjectKeyCiphertext: Data
    public var wrappedObjectKeyTag: Data
    public var senderFingerprint: String
    public var senderAgreementPublicKey: Data
}

public struct ObjectPiece: Codable, Sendable, Equatable {
    public var objectProtocolVersion: UInt8
    public var objectId: String
    public var pieceIndex: Int
    public var pieceKind: ObjectPieceKind
    public var nonce: Data
    public var ciphertext: Data
    public var tag: Data
    public var pieceHash: Data
    public var merkleProof: [Data]
}

public struct ObjectPieceBatch: Codable, Sendable, Equatable {
    public var objectProtocolVersion: UInt8
    public var objectId: String
    public var pieces: [ObjectPiece]
}

public struct PieceInventory: Codable, Sendable, Equatable {
    public var objectProtocolVersion: UInt8
    public var peerId: PeerID
    public var objectId: String
    public var dataPieceIndexes: [Int]
    public var recoveryPieceIndexes: [Int]
    public var updatedAtEpochMillis: Int64
}

public struct ResumeToken: Codable, Sendable, Equatable {
    public var objectProtocolVersion: UInt8
    public var objectId: String
    public var receiverId: PeerID
    public var receivedPieceIndexes: [Int]
    public var receivedBitmapHash: Data
    public var lastVerifiedPiece: Int
    public var timestampEpochMillis: Int64
}

public struct RelayToken: Codable, Sendable, Equatable {
    public var objectProtocolVersion: UInt8
    public var objectId: String
    public var allowedRelayPeerId: PeerID
    public var recipientId: PeerID
    public var expiresAtEpochMillis: Int64
    public var maxBytes: Int
    public var signature: Data
}

public struct TransferReceipt: Codable, Sendable, Equatable {
    public var objectProtocolVersion: UInt8
    public var transferId: TransferID
    public var objectId: String
    public var senderId: PeerID
    public var recipientId: PeerID
    public var completedAtEpochMillis: Int64
    public var verifiedPlainSHA256: Data
}

public struct LocalWaveObjectPackage: Codable, Sendable, Equatable {
    public var manifest: EncryptedObjectManifest
    public var pieces: [ObjectPiece]
}

public struct ObjectTransferResult: Sendable, Equatable {
    public var transferId: TransferID
    public var objectId: String
    public var attachment: OutboundAttachment
    public var receipt: TransferReceipt
}

public enum ObjectTransferCodec {
    public static func encode<T: Encodable>(_ value: T) throws -> Data {
        try SecureEnvelopeCodec.encode(value)
    }

    public static func decode<T: Decodable>(_ type: T.Type, from data: Data) throws -> T {
        try SecureEnvelopeCodec.decode(type, from: data)
    }
}

public struct PieceScheduler: Sendable {
    public init() {}

    public func orderedMissingPieces(
        pieceCount: Int,
        received: Set<Int>,
        inventories: [PieceInventory],
        throughputScores: [PeerID: Double] = [:]
    ) -> [Int] {
        let missing = Set(0..<pieceCount).subtracting(received)
        let rarity = Dictionary(grouping: inventories.flatMap(\.dataPieceIndexes), by: { $0 }).mapValues(\.count)
        return missing.sorted { lhs, rhs in
            let lhsRarity = rarity[lhs] ?? Int.max
            let rhsRarity = rarity[rhs] ?? Int.max
            if lhsRarity != rhsRarity { return lhsRarity < rhsRarity }
            return lhs < rhs
        }
    }
}

public struct ObjectRelayCache: Sendable {
    private var chunksByKey: [String: RelayChunk] = [:]
    private let maxBytes: Int

    public init(maxBytes: Int) {
        self.maxBytes = max(1, maxBytes)
    }

    public mutating func insert(_ chunk: RelayChunk, now: Date = Date()) throws {
        pruneExpired(now: now)
        guard chunk.expiresAt > now else { throw LocalWaveError.transferUnavailable("Relay chunk is expired.") }
        chunksByKey[relayKey(chunk)] = chunk
        trimToLimit()
    }

    public mutating func chunks(for destinationPeerId: PeerID, now: Date = Date()) -> [RelayChunk] {
        pruneExpired(now: now)
        return chunksByKey.values
            .filter { $0.destinationPeerId == destinationPeerId }
            .sorted { $0.expiresAt < $1.expiresAt }
    }

    private mutating func pruneExpired(now: Date) {
        chunksByKey = chunksByKey.filter { $0.value.expiresAt > now }
    }

    private mutating func trimToLimit() {
        while chunksByKey.values.reduce(0, { $0 + $1.payload.count }) > maxBytes,
              let oldest = chunksByKey.values.min(by: { $0.expiresAt < $1.expiresAt }) {
            chunksByKey[relayKey(oldest)] = nil
        }
    }

    private func relayKey(_ chunk: RelayChunk) -> String {
        "\(chunk.id.uuidString)|\(chunk.destinationPeerId)"
    }
}

public actor LocalWaveObjectStore {
    private let root: URL
    private let fileManager: FileManager

    public init(directory: URL? = nil, fileManager: FileManager = .default) {
        self.fileManager = fileManager
        self.root = directory ?? fileManager.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("LocalWave", isDirectory: true)
            .appendingPathComponent("Objects", isDirectory: true)
    }

    public func store(_ package: LocalWaveObjectPackage) throws {
        try store(package.manifest)
        try store(package.pieces)
    }

    public func store(_ manifest: EncryptedObjectManifest) throws {
        let directory = objectDirectory(manifest.objectId)
        try fileManager.createDirectory(at: directory.appendingPathComponent("pieces", isDirectory: true), withIntermediateDirectories: true)
        try ObjectTransferCodec.encode(manifest)
            .write(to: directory.appendingPathComponent("manifest.json"), options: [.atomic, .completeFileProtection])
    }

    public func store(_ pieces: [ObjectPiece]) throws {
        for piece in pieces {
            let directory = objectDirectory(piece.objectId).appendingPathComponent("pieces", isDirectory: true)
            try fileManager.createDirectory(at: directory, withIntermediateDirectories: true)
            let suffix = piece.pieceKind == .data ? "data" : "recovery"
            try ObjectTransferCodec.encode(piece)
                .write(to: directory.appendingPathComponent("\(piece.pieceIndex)-\(suffix).json"), options: [.atomic, .completeFileProtection])
        }
    }

    public func package(objectId: String) throws -> LocalWaveObjectPackage? {
        let directory = objectDirectory(objectId)
        let manifestURL = directory.appendingPathComponent("manifest.json")
        guard fileManager.fileExists(atPath: manifestURL.path) else { return nil }
        let manifest = try ObjectTransferCodec.decode(EncryptedObjectManifest.self, from: Data(contentsOf: manifestURL))
        let piecesURL = directory.appendingPathComponent("pieces", isDirectory: true)
        let pieces = try fileManager.contentsOfDirectory(at: piecesURL, includingPropertiesForKeys: nil, options: [.skipsHiddenFiles])
            .filter { $0.pathExtension == "json" }
            .map { try ObjectTransferCodec.decode(ObjectPiece.self, from: Data(contentsOf: $0)) }
            .sorted { $0.pieceIndex < $1.pieceIndex }
        return LocalWaveObjectPackage(manifest: manifest, pieces: pieces)
    }

    public func pruneExpired(now: Date = Date()) throws {
        guard fileManager.fileExists(atPath: root.path) else { return }
        for objectURL in try fileManager.contentsOfDirectory(at: root, includingPropertiesForKeys: nil, options: [.skipsHiddenFiles]) {
            let manifestURL = objectURL.appendingPathComponent("manifest.json")
            guard fileManager.fileExists(atPath: manifestURL.path) else { continue }
            let manifest = try ObjectTransferCodec.decode(EncryptedObjectManifest.self, from: Data(contentsOf: manifestURL))
            if Date(timeIntervalSince1970: TimeInterval(manifest.expiresAtEpochMillis) / 1_000) <= now {
                try fileManager.removeItem(at: objectURL)
            }
        }
    }

    private func objectDirectory(_ objectId: String) -> URL {
        root.appendingPathComponent(objectId, isDirectory: true)
    }
}

public actor ObjectTransferCrypto {
    public static let defaultPieceSize = 32 * 1024
    private static let recoveryStripeSize = 10
    private let sessionCrypto: SessionCrypto

    public init(sessionCrypto: SessionCrypto) {
        self.sessionCrypto = sessionCrypto
    }

    public func createPackage(
        attachment: OutboundAttachment,
        to peer: PeerProfile,
        localIdentity: LocalIdentity,
        channel: ChannelCode,
        now: Date = Date(),
        expiresIn: TimeInterval = 24 * 60 * 60
    ) async throws -> LocalWaveObjectPackage {
        let pieceSize = Self.defaultPieceSize
        let objectKeyBytes = Data((0..<32).map { _ in UInt8.random(in: 0...255) })
        let objectKey = SymmetricKey(data: objectKeyBytes)
        let dataChunks = attachment.data.chunked(size: pieceSize)
        let recoveryChunks = Self.recoveryChunks(from: dataChunks, stripeSize: Self.recoveryStripeSize)

        let encryptedDataPieces = try dataChunks.enumerated().map { index, chunk in
            try Self.encryptPiece(chunk, index: index, kind: .data, objectId: "", objectKey: objectKey)
        }
        let encryptedRecoveryPieces = try recoveryChunks.enumerated().map { offset, chunk in
            try Self.encryptPiece(chunk, index: dataChunks.count + offset, kind: .recovery, objectId: "", objectKey: objectKey)
        }
        let dataPieceHashes = encryptedDataPieces.map(\.pieceHash)
        let plainPieceHashes = dataChunks.map { Data(SHA256.hash(data: $0)) }
        let merkleRoot = Self.merkleRoot(for: dataPieceHashes)
        let createdAt = Int64(now.timeIntervalSince1970 * 1_000)
        let expiresAt = Int64(now.addingTimeInterval(expiresIn).timeIntervalSince1970 * 1_000)
        let manifestPlaintext = ObjectManifestPlaintext(
            objectProtocolVersion: ObjectManifestPlaintext.protocolVersion,
            senderId: localIdentity.peerId,
            recipientIds: [peer.id],
            objectType: Self.objectType(for: attachment.contentType),
            fileName: attachment.fileName,
            mimeType: attachment.contentType,
            plainSize: attachment.data.count,
            encryptedSize: encryptedDataPieces.reduce(0) { $0 + $1.ciphertext.count + $1.tag.count },
            pieceSize: pieceSize,
            pieceCount: dataChunks.count,
            recoveryPieceCount: recoveryChunks.count,
            dataPieceHashes: dataPieceHashes,
            plainPieceHashes: plainPieceHashes,
            merkleRoot: merkleRoot,
            plainSHA256: Data(SHA256.hash(data: attachment.data)),
            createdAtEpochMillis: createdAt,
            expiresAtEpochMillis: expiresAt,
            relayPolicy: .trustedPeersOnly,
            previewPolicy: "recipient-only"
        )

        let manifestData = try ObjectTransferCodec.encode(manifestPlaintext)
        let manifestNonce = AES.GCM.Nonce()
        let manifestSealed = try AES.GCM.seal(manifestData, using: objectKey, nonce: manifestNonce, authenticating: Data("LocalWave.Manifest.v1".utf8))
        var manifestCommitment = manifestNonce.withUnsafeBytes { Data($0) }
        manifestCommitment.append(manifestSealed.ciphertext)
        manifestCommitment.append(manifestSealed.tag)
        let objectId = Data(SHA256.hash(data: manifestCommitment)).hexString

        let wrappingKey = try await sessionCrypto.objectWrappingKey(to: peer, channel: channel)
        let wrapNonce = AES.GCM.Nonce()
        let wrapAAD = Data("LocalWave.ObjectKey.v1|\(objectId)|\(peer.id)".utf8)
        let wrappedKey = try AES.GCM.seal(objectKeyBytes, using: wrappingKey, nonce: wrapNonce, authenticating: wrapAAD)
        let manifest = EncryptedObjectManifest(
            objectProtocolVersion: ObjectManifestPlaintext.protocolVersion,
            objectId: objectId,
            senderId: localIdentity.peerId,
            recipientId: peer.id,
            createdAtEpochMillis: createdAt,
            expiresAtEpochMillis: expiresAt,
            nonce: manifestNonce.withUnsafeBytes { Data($0) },
            ciphertext: manifestSealed.ciphertext,
            tag: manifestSealed.tag,
            wrappedObjectKeyNonce: wrapNonce.withUnsafeBytes { Data($0) },
            wrappedObjectKeyCiphertext: wrappedKey.ciphertext,
            wrappedObjectKeyTag: wrappedKey.tag,
            senderFingerprint: localIdentity.fingerprint,
            senderAgreementPublicKey: localIdentity.agreementPublicKey
        )
        let pieces = (encryptedDataPieces + encryptedRecoveryPieces).map { piece in
            ObjectPiece(
                objectProtocolVersion: piece.objectProtocolVersion,
                objectId: objectId,
                pieceIndex: piece.pieceIndex,
                pieceKind: piece.pieceKind,
                nonce: piece.nonce,
                ciphertext: piece.ciphertext,
                tag: piece.tag,
                pieceHash: piece.pieceHash,
                merkleProof: []
            )
        }
        return LocalWaveObjectPackage(manifest: manifest, pieces: pieces)
    }

    public func decryptPackage(
        _ package: LocalWaveObjectPackage,
        from peer: PeerProfile,
        channel: ChannelCode,
        now: Date = Date()
    ) async throws -> ObjectTransferResult {
        let objectKey = try await unwrapObjectKey(package.manifest, from: peer, channel: channel)
        let manifest = try decryptManifest(package.manifest, objectKey: objectKey, now: now)
        let dataPieces = package.pieces.filter { $0.pieceKind == .data }
        let recoveryPieces = package.pieces.filter { $0.pieceKind == .recovery }
        var plaintextChunks: [Int: Data] = [:]
        for piece in dataPieces {
            guard piece.pieceIndex < manifest.pieceCount else { continue }
            guard piece.pieceHash == manifest.dataPieceHashes[piece.pieceIndex] else { throw LocalWaveError.decryptionFailed }
            plaintextChunks[piece.pieceIndex] = try Self.decryptPiece(piece, objectKey: objectKey)
        }
        try recoverMissingChunks(plaintextChunks: &plaintextChunks, manifest: manifest, recoveryPieces: recoveryPieces, objectKey: objectKey)
        let ordered = try (0..<manifest.pieceCount).map { index -> Data in
            guard let chunk = plaintextChunks[index] else { throw LocalWaveError.transferUnavailable("Object is missing verified pieces.") }
            return chunk
        }
        let payload = ordered.reduce(Data(), +)
        guard payload.count == manifest.plainSize else { throw LocalWaveError.decryptionFailed }
        guard Data(SHA256.hash(data: payload)) == manifest.plainSHA256 else { throw LocalWaveError.decryptionFailed }
        let attachment = try OutboundAttachment(fileName: manifest.fileName, contentType: manifest.mimeType, data: payload)
        let receipt = TransferReceipt(
            objectProtocolVersion: manifest.objectProtocolVersion,
            transferId: UUID(uuidString: String(package.manifest.objectId.prefix(32)).uuidFormattedFromHex) ?? UUID(),
            objectId: package.manifest.objectId,
            senderId: package.manifest.senderId,
            recipientId: package.manifest.recipientId,
            completedAtEpochMillis: Int64(now.timeIntervalSince1970 * 1_000),
            verifiedPlainSHA256: manifest.plainSHA256
        )
        return ObjectTransferResult(transferId: receipt.transferId, objectId: package.manifest.objectId, attachment: attachment, receipt: receipt)
    }

    public func decryptManifest(_ manifest: EncryptedObjectManifest, objectKey: SymmetricKey, now: Date = Date()) throws -> ObjectManifestPlaintext {
        guard manifest.objectProtocolVersion == ObjectManifestPlaintext.protocolVersion else { throw LocalWaveError.unsupportedPackage }
        guard manifest.expiresAtEpochMillis > Int64(now.timeIntervalSince1970 * 1_000) else { throw LocalWaveError.transferUnavailable("Object transfer expired.") }
        var commitment = manifest.nonce
        commitment.append(manifest.ciphertext)
        commitment.append(manifest.tag)
        guard Data(SHA256.hash(data: commitment)).hexString == manifest.objectId else { throw LocalWaveError.decryptionFailed }
        let box = try AES.GCM.SealedBox(nonce: AES.GCM.Nonce(data: manifest.nonce), ciphertext: manifest.ciphertext, tag: manifest.tag)
        let data = try AES.GCM.open(box, using: objectKey, authenticating: Data("LocalWave.Manifest.v1".utf8))
        let plaintext = try ObjectTransferCodec.decode(ObjectManifestPlaintext.self, from: data)
        guard plaintext.merkleRoot == Self.merkleRoot(for: plaintext.dataPieceHashes) else { throw LocalWaveError.decryptionFailed }
        return plaintext
    }

    private func unwrapObjectKey(_ manifest: EncryptedObjectManifest, from peer: PeerProfile, channel: ChannelCode) async throws -> SymmetricKey {
        let wrappingKey = try await sessionCrypto.objectWrappingKey(to: peer, channel: channel)
        let box = try AES.GCM.SealedBox(
            nonce: AES.GCM.Nonce(data: manifest.wrappedObjectKeyNonce),
            ciphertext: manifest.wrappedObjectKeyCiphertext,
            tag: manifest.wrappedObjectKeyTag
        )
        let aad = Data("LocalWave.ObjectKey.v1|\(manifest.objectId)|\(manifest.recipientId)".utf8)
        let objectKeyBytes = try AES.GCM.open(box, using: wrappingKey, authenticating: aad)
        return SymmetricKey(data: objectKeyBytes)
    }

    private func recoverMissingChunks(plaintextChunks: inout [Int: Data], manifest: ObjectManifestPlaintext, recoveryPieces: [ObjectPiece], objectKey: SymmetricKey) throws {
        for recovery in recoveryPieces {
            let stripe = recovery.pieceIndex - manifest.pieceCount
            let start = stripe * Self.recoveryStripeSize
            let end = min(start + Self.recoveryStripeSize, manifest.pieceCount)
            let missing = (start..<end).filter { plaintextChunks[$0] == nil }
            guard missing.count == 1 else { continue }
            var recovered = try Self.decryptPiece(recovery, objectKey: objectKey)
            for index in start..<end where index != missing[0] {
                guard let chunk = plaintextChunks[index] else { recovered = Data(); break }
                recovered = Self.xor(recovered, chunk, size: manifest.pieceSize)
            }
            let expectedSize = missing[0] == manifest.pieceCount - 1 ? manifest.plainSize - (missing[0] * manifest.pieceSize) : manifest.pieceSize
            let trimmed = recovered.prefix(expectedSize)
            guard Data(SHA256.hash(data: trimmed)) == manifest.plainPieceHashes[missing[0]] else { continue }
            plaintextChunks[missing[0]] = Data(trimmed)
        }
    }

    private static func encryptPiece(_ plaintext: Data, index: Int, kind: ObjectPieceKind, objectId: String, objectKey: SymmetricKey) throws -> ObjectPiece {
        let nonce = AES.GCM.Nonce()
        let aad = Data("LocalWave.ObjectPiece.v1|\(index)|\(kind.rawValue)".utf8)
        let sealed = try AES.GCM.seal(plaintext, using: objectKey, nonce: nonce, authenticating: aad)
        var hashInput = nonce.withUnsafeBytes { Data($0) }
        hashInput.append(sealed.ciphertext)
        hashInput.append(sealed.tag)
        return ObjectPiece(
            objectProtocolVersion: ObjectManifestPlaintext.protocolVersion,
            objectId: objectId,
            pieceIndex: index,
            pieceKind: kind,
            nonce: nonce.withUnsafeBytes { Data($0) },
            ciphertext: sealed.ciphertext,
            tag: sealed.tag,
            pieceHash: Data(SHA256.hash(data: hashInput)),
            merkleProof: []
        )
    }

    private static func decryptPiece(_ piece: ObjectPiece, objectKey: SymmetricKey) throws -> Data {
        var hashInput = piece.nonce
        hashInput.append(piece.ciphertext)
        hashInput.append(piece.tag)
        guard Data(SHA256.hash(data: hashInput)) == piece.pieceHash else { throw LocalWaveError.decryptionFailed }
        let aad = Data("LocalWave.ObjectPiece.v1|\(piece.pieceIndex)|\(piece.pieceKind.rawValue)".utf8)
        let box = try AES.GCM.SealedBox(nonce: AES.GCM.Nonce(data: piece.nonce), ciphertext: piece.ciphertext, tag: piece.tag)
        return try AES.GCM.open(box, using: objectKey, authenticating: aad)
    }

    public static func merkleRoot(for hashes: [Data]) -> Data {
        guard !hashes.isEmpty else { return Data(SHA256.hash(data: Data())) }
        var level = hashes
        while level.count > 1 {
            var next: [Data] = []
            var index = 0
            while index < level.count {
                let left = level[index]
                let right = index + 1 < level.count ? level[index + 1] : left
                var combined = left
                combined.append(right)
                next.append(Data(SHA256.hash(data: combined)))
                index += 2
            }
            level = next
        }
        return level[0]
    }

    private static func recoveryChunks(from chunks: [Data], stripeSize: Int) -> [Data] {
        guard chunks.reduce(0, { $0 + $1.count }) >= 1_024 * 1_024 else { return [] }
        var result: [Data] = []
        var index = 0
        while index < chunks.count {
            let stripe = Array(chunks[index..<min(index + stripeSize, chunks.count)])
            var parity = Data(repeating: 0, count: stripe.map(\.count).max() ?? 0)
            for chunk in stripe {
                parity = xor(parity, chunk, size: parity.count)
            }
            result.append(parity)
            index += stripeSize
        }
        return result
    }

    private static func xor(_ lhs: Data, _ rhs: Data, size: Int) -> Data {
        var output = [UInt8](repeating: 0, count: size)
        let left = [UInt8](lhs)
        let right = [UInt8](rhs)
        for index in 0..<size {
            output[index] = (index < left.count ? left[index] : 0) ^ (index < right.count ? right[index] : 0)
        }
        return Data(output)
    }

    private static func objectType(for mimeType: String) -> String {
        if mimeType.hasPrefix("image/") { return "image" }
        if mimeType.hasPrefix("video/") { return "video" }
        if mimeType.hasPrefix("audio/") { return "voice" }
        if mimeType == "application/pdf" { return "document" }
        return "file"
    }
}

private extension Data {
    func chunked(size: Int) -> [Data] {
        guard !isEmpty else { return [Data()] }
        var chunks: [Data] = []
        var offset = 0
        while offset < count {
            chunks.append(subdata(in: offset..<Swift.min(offset + size, count)))
            offset += size
        }
        return chunks
    }

    var hexString: String {
        map { String(format: "%02x", $0) }.joined()
    }
}

private extension String {
    var uuidFormattedFromHex: String {
        let padded = padding(toLength: 32, withPad: "0", startingAt: 0)
        return "\(padded.prefix(8))-\(padded.dropFirst(8).prefix(4))-\(padded.dropFirst(12).prefix(4))-\(padded.dropFirst(16).prefix(4))-\(padded.dropFirst(20).prefix(12))"
    }
}
