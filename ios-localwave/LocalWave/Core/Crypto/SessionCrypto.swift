import CryptoKit
import Foundation

public actor ReplayProtector {
    private var highestCounters: [String: UInt64] = [:]

    public init() {}

    public func validate(senderId: PeerID, channel: ChannelCode, counter: UInt64) throws {
        let key = "\(senderId)|\(channel.normalized)"
        if let highest = highestCounters[key], counter <= highest {
            throw LocalWaveError.replayDetected
        }
        highestCounters[key] = counter
    }
}

public actor SessionCrypto: CryptoServiceProtocol {
    private let identityStore: IdentityStoreProtocol
    private let replayProtector: ReplayProtector

    public init(identityStore: IdentityStoreProtocol, replayProtector: ReplayProtector = ReplayProtector()) {
        self.identityStore = identityStore
        self.replayProtector = replayProtector
    }

    public func encryptMessage(
        _ text: String,
        to peer: PeerProfile,
        counter: UInt64,
        channel: ChannelCode
    ) async throws -> MessageEnvelope {
        let envelope = try await encryptSecureEnvelope(
            Data(text.utf8),
            kind: .message,
            messageId: UUID(),
            to: peer,
            counter: counter,
            channel: channel
        )

        guard let messageId = envelope.messageId else { throw LocalWaveError.encryptionFailed }
        return MessageEnvelope(
            version: envelope.version,
            senderId: envelope.senderId,
            recipientId: envelope.recipientId,
            timestamp: envelope.timestamp,
            messageId: messageId,
            replayCounter: envelope.replayCounter,
            nonce: envelope.nonce,
            ciphertext: envelope.ciphertext,
            tag: envelope.tag
        )
    }

    public func decryptMessage(
        _ envelope: MessageEnvelope,
        from peer: PeerProfile,
        channel: ChannelCode
    ) async throws -> String {
        let secure = SecureEnvelope(
            version: envelope.version,
            kind: .message,
            senderId: envelope.senderId,
            recipientId: envelope.recipientId,
            timestamp: envelope.timestamp,
            messageId: envelope.messageId,
            replayCounter: envelope.replayCounter,
            nonce: envelope.nonce,
            ciphertext: envelope.ciphertext,
            tag: envelope.tag
        )
        let plaintext = try await decryptSecureEnvelope(secure, from: peer, channel: channel)
        guard let text = String(data: plaintext, encoding: .utf8) else {
            throw LocalWaveError.decryptionFailed
        }
        return text
    }

    public func encryptWake(to peer: PeerProfile, counter: UInt64, channel: ChannelCode) async throws -> WakeEnvelope {
        let envelope = try await encryptSecureEnvelope(
            Data("wake".utf8),
            kind: .wake,
            messageId: nil,
            to: peer,
            counter: counter,
            channel: channel
        )
        return WakeEnvelope(
            version: envelope.version,
            senderId: envelope.senderId,
            recipientId: envelope.recipientId,
            timestamp: envelope.timestamp,
            replayCounter: envelope.replayCounter,
            nonce: envelope.nonce,
            ciphertext: envelope.ciphertext,
            tag: envelope.tag
        )
    }

    public func decryptWake(_ envelope: WakeEnvelope, from peer: PeerProfile, channel: ChannelCode) async throws {
        let secure = SecureEnvelope(
            version: envelope.version,
            kind: .wake,
            senderId: envelope.senderId,
            recipientId: envelope.recipientId,
            timestamp: envelope.timestamp,
            messageId: nil,
            replayCounter: envelope.replayCounter,
            nonce: envelope.nonce,
            ciphertext: envelope.ciphertext,
            tag: envelope.tag
        )
        _ = try await decryptSecureEnvelope(secure, from: peer, channel: channel)
    }

    public func encryptSecureEnvelope(
        _ plaintext: Data,
        kind: SecureEnvelopeKind,
        messageId: MessageID?,
        to peer: PeerProfile,
        counter: UInt64,
        channel: ChannelCode
    ) async throws -> SecureEnvelope {
        guard let peerPublicKeyData = peer.publicKeyData else { throw LocalWaveError.peerUnavailable }
        let keyPair = try await identityStore.keyPair()
        let timestamp = Date()
        let envelopeSkeleton = SecureEnvelope(
            kind: kind,
            senderId: keyPair.identity.peerId,
            recipientId: peer.id,
            timestamp: timestamp,
            messageId: messageId,
            replayCounter: counter,
            nonce: Data(),
            ciphertext: Data(),
            tag: Data()
        )

        do {
            let symmetricKey = try deriveKey(
                localPrivateKeyData: keyPair.agreementPrivateKeyData,
                remotePublicKeyData: peerPublicKeyData,
                localPeerId: keyPair.identity.peerId,
                remotePeerId: peer.id,
                channel: channel
            )
            let nonce = AES.GCM.Nonce()
            let nonceData = nonce.withUnsafeBytes { Data($0) }
            let associatedData = Self.associatedData(for: envelopeSkeleton, channel: channel)
            let sealed = try AES.GCM.seal(plaintext, using: symmetricKey, nonce: nonce, authenticating: associatedData)
            return SecureEnvelope(
                version: envelopeSkeleton.version,
                kind: kind,
                senderId: envelopeSkeleton.senderId,
                recipientId: envelopeSkeleton.recipientId,
                timestamp: timestamp,
                messageId: messageId,
                replayCounter: counter,
                nonce: nonceData,
                ciphertext: sealed.ciphertext,
                tag: sealed.tag
            )
        } catch let error as LocalWaveError {
            throw error
        } catch {
            throw LocalWaveError.encryptionFailed
        }
    }

    public func decryptSecureEnvelope(_ envelope: SecureEnvelope, from peer: PeerProfile, channel: ChannelCode) async throws -> Data {
        guard envelope.senderId == peer.id else { throw LocalWaveError.decryptionFailed }
        guard let peerPublicKeyData = peer.publicKeyData else { throw LocalWaveError.peerUnavailable }
        let keyPair = try await identityStore.keyPair()
        guard envelope.recipientId == keyPair.identity.peerId else { throw LocalWaveError.decryptionFailed }

        do {
            let symmetricKey = try deriveKey(
                localPrivateKeyData: keyPair.agreementPrivateKeyData,
                remotePublicKeyData: peerPublicKeyData,
                localPeerId: keyPair.identity.peerId,
                remotePeerId: peer.id,
                channel: channel
            )
            let nonce = try AES.GCM.Nonce(data: envelope.nonce)
            let sealedBox = try AES.GCM.SealedBox(nonce: nonce, ciphertext: envelope.ciphertext, tag: envelope.tag)
            let associatedData = Self.associatedData(for: envelope, channel: channel)
            let plaintext = try AES.GCM.open(sealedBox, using: symmetricKey, authenticating: associatedData)
            try await replayProtector.validate(senderId: envelope.senderId, channel: channel, counter: envelope.replayCounter)
            return plaintext
        } catch let error as LocalWaveError {
            throw error
        } catch {
            throw LocalWaveError.decryptionFailed
        }
    }

    private func deriveKey(
        localPrivateKeyData: Data,
        remotePublicKeyData: Data,
        localPeerId: PeerID,
        remotePeerId: PeerID,
        channel: ChannelCode
    ) throws -> SymmetricKey {
        let localPrivateKey = try Curve25519.KeyAgreement.PrivateKey(rawRepresentation: localPrivateKeyData)
        let remotePublicKey = try Curve25519.KeyAgreement.PublicKey(rawRepresentation: remotePublicKeyData)
        let sharedSecret = try localPrivateKey.sharedSecretFromKeyAgreement(with: remotePublicKey)
        let channelIdentity = ChannelKeyDerivation.derive(from: channel)
        let peerInfo = [localPeerId, remotePeerId].sorted().joined(separator: "|")
        return sharedSecret.hkdfDerivedSymmetricKey(
            using: SHA256.self,
            salt: channelIdentity.hkdfSalt,
            sharedInfo: Data("LocalWave.Session.v1|\(peerInfo)".utf8),
            outputByteCount: 32
        )
    }

    private static func associatedData(for envelope: SecureEnvelope, channel: ChannelCode) -> Data {
        var data = Data("LocalWave.AAD.v1".utf8)
        data.append(envelope.version)
        data.append(utf8Field(envelope.kind.rawValue))
        data.append(utf8Field(envelope.senderId))
        data.append(utf8Field(envelope.recipientId))
        data.append(utf8Field(envelope.messageId?.uuidString ?? ""))
        data.append(UInt64(envelope.timestamp.timeIntervalSince1970 * 1_000).bigEndianData)
        data.append(envelope.replayCounter.bigEndianData)
        data.append(utf8Field(channel.normalized))
        return data
    }

    private static func utf8Field(_ value: String) -> Data {
        var data = UInt32(value.utf8.count).bigEndianData
        data.append(contentsOf: value.utf8)
        return data
    }
}

private extension FixedWidthInteger {
    var bigEndianData: Data {
        var value = self.bigEndian
        return withUnsafeBytes(of: &value) { Data($0) }
    }
}
