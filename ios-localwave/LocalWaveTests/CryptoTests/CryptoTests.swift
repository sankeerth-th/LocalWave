import CryptoKit
import XCTest
@testable import LocalWave

final class CryptoTests: XCTestCase {
    func testMessageEnvelopeDecryptsWithMatchingPeerKey() async throws {
        let aliceStore = InMemoryIdentityStore(keyPair: TestIdentities.alice)
        let bobStore = InMemoryIdentityStore(keyPair: TestIdentities.bob)
        let alice = SessionCrypto(identityStore: aliceStore)
        let bob = SessionCrypto(identityStore: bobStore)
        let channel = try ChannelCode("DOCK-A-17")

        let envelope = try await alice.encryptMessage("Shift change at 06:30", to: TestIdentities.bobPeer, counter: 7, channel: channel)
        let plaintext = try await bob.decryptMessage(envelope, from: TestIdentities.alicePeer, channel: channel)

        XCTAssertEqual(plaintext, "Shift change at 06:30")
        XCTAssertEqual(envelope.senderId, TestIdentities.alice.identity.peerId)
        XCTAssertEqual(envelope.recipientId, TestIdentities.bob.identity.peerId)
    }

    func testEncodedMessageEnvelopeDecryptsAfterJsonRoundTrip() async throws {
        let aliceStore = InMemoryIdentityStore(keyPair: TestIdentities.alice)
        let bobStore = InMemoryIdentityStore(keyPair: TestIdentities.bob)
        let alice = SessionCrypto(identityStore: aliceStore)
        let bob = SessionCrypto(identityStore: bobStore)
        let channel = try ChannelCode("DOCK-A-17")

        let envelope = try await alice.encryptMessage("Inbound trailer cleared", to: TestIdentities.bobPeer, counter: 10, channel: channel)
        let encoded = try SecureEnvelopeCodec.encode(envelope)
        let decoded = try SecureEnvelopeCodec.decode(MessageEnvelope.self, from: encoded)
        let plaintext = try await bob.decryptMessage(decoded, from: TestIdentities.alicePeer, channel: channel)

        XCTAssertEqual(plaintext, "Inbound trailer cleared")
        XCTAssertEqual(decoded.timestamp.timeIntervalSince1970, envelope.timestamp.timeIntervalSince1970, accuracy: 0.001)
    }

    func testEncodedWakeEnvelopeDecryptsAfterJsonRoundTrip() async throws {
        let aliceStore = InMemoryIdentityStore(keyPair: TestIdentities.alice)
        let bobStore = InMemoryIdentityStore(keyPair: TestIdentities.bob)
        let alice = SessionCrypto(identityStore: aliceStore)
        let bob = SessionCrypto(identityStore: bobStore)
        let channel = try ChannelCode("DOCK-A-17")

        let envelope = try await alice.encryptWake(to: TestIdentities.bobPeer, counter: 11, channel: channel)
        let encoded = try SecureEnvelopeCodec.encode(envelope)
        let decoded = try SecureEnvelopeCodec.decode(WakeEnvelope.self, from: encoded)

        try await bob.decryptWake(decoded, from: TestIdentities.alicePeer, channel: channel)
        XCTAssertEqual(decoded.timestamp.timeIntervalSince1970, envelope.timestamp.timeIntervalSince1970, accuracy: 0.001)
    }

    func testDeliveryReceiptRoundTripsForMessageStatusAck() throws {
        let receipt = DeliveryReceipt(
            messageId: UUID(),
            senderId: TestIdentities.alice.identity.peerId,
            recipientId: TestIdentities.bob.identity.peerId,
            deliveredAt: Date()
        )

        let decoded = try SecureEnvelopeCodec.decode(DeliveryReceipt.self, from: SecureEnvelopeCodec.encode(receipt))

        XCTAssertEqual(decoded.messageId, receipt.messageId)
        XCTAssertEqual(decoded.senderId, receipt.senderId)
        XCTAssertEqual(decoded.recipientId, receipt.recipientId)
        XCTAssertEqual(decoded.deliveredAt.timeIntervalSince1970, receipt.deliveredAt.timeIntervalSince1970, accuracy: 0.001)
    }

    func testPeerIntroRoundTripsWithIdentityPublicKey() throws {
        let intro = PeerIntroEnvelope(
            peerId: TestIdentities.alice.identity.peerId,
            displayName: "Alice",
            fingerprint: TestIdentities.alice.identity.fingerprint,
            agreementPublicKey: TestIdentities.alice.identity.agreementPublicKey
        )

        let decoded = try SecureEnvelopeCodec.decode(PeerIntroEnvelope.self, from: SecureEnvelopeCodec.encode(intro))

        XCTAssertEqual(decoded.peerId, intro.peerId)
        XCTAssertEqual(decoded.fingerprint, intro.fingerprint)
        XCTAssertEqual(decoded.agreementPublicKey, intro.agreementPublicKey)
    }

    func testAttachmentEnvelopeDecryptsAfterJsonRoundTrip() async throws {
        let alice = SessionCrypto(identityStore: InMemoryIdentityStore(keyPair: TestIdentities.alice))
        let bob = SessionCrypto(identityStore: InMemoryIdentityStore(keyPair: TestIdentities.bob))
        let channel = try ChannelCode("DOCK-A-17")
        let attachment = try OutboundAttachment(
            fileName: "dock-photo.jpg",
            contentType: "image/jpeg",
            data: Data("encrypted-image-bytes".utf8)
        )

        let envelope = try await alice.encryptAttachment(attachment, to: TestIdentities.bobPeer, counter: 13, channel: channel)
        let encoded = try SecureEnvelopeCodec.encode(envelope)
        let decoded = try SecureEnvelopeCodec.decode(AttachmentEnvelope.self, from: encoded)
        let decrypted = try await bob.decryptAttachment(decoded, from: TestIdentities.alicePeer, channel: channel)

        XCTAssertEqual(decrypted.fileName, attachment.fileName)
        XCTAssertEqual(decrypted.contentType, attachment.contentType)
        XCTAssertEqual(decrypted.data, attachment.data)
        XCTAssertEqual(decoded.transferId, envelope.transferId)
    }

    func testObjectPackageDecryptsAfterJsonRoundTrip() async throws {
        let aliceCrypto = SessionCrypto(identityStore: InMemoryIdentityStore(keyPair: TestIdentities.alice))
        let bobCrypto = SessionCrypto(identityStore: InMemoryIdentityStore(keyPair: TestIdentities.bob))
        let objectCrypto = ObjectTransferCrypto(sessionCrypto: aliceCrypto)
        let receiverObjectCrypto = ObjectTransferCrypto(sessionCrypto: bobCrypto)
        let channel = try ChannelCode("DOCK-A-17")
        let attachment = try OutboundAttachment(fileName: "inventory.pdf", contentType: "application/pdf", data: Data(repeating: 0x42, count: 128 * 1024))

        let package = try await objectCrypto.createPackage(attachment: attachment, to: TestIdentities.bobPeer, localIdentity: TestIdentities.alice.identity, channel: channel)
        let encoded = try ObjectTransferCodec.encode(package)
        let decoded = try ObjectTransferCodec.decode(LocalWaveObjectPackage.self, from: encoded)
        let result = try await receiverObjectCrypto.decryptPackage(decoded, from: TestIdentities.alicePeer, channel: channel)

        XCTAssertEqual(result.attachment.fileName, attachment.fileName)
        XCTAssertEqual(result.attachment.data, attachment.data)
        XCTAssertEqual(result.receipt.objectId, package.manifest.objectId)
    }

    func testObjectPackageRecoversOneMissingPieceWithParity() async throws {
        let aliceCrypto = SessionCrypto(identityStore: InMemoryIdentityStore(keyPair: TestIdentities.alice))
        let bobCrypto = SessionCrypto(identityStore: InMemoryIdentityStore(keyPair: TestIdentities.bob))
        let objectCrypto = ObjectTransferCrypto(sessionCrypto: aliceCrypto)
        let receiverObjectCrypto = ObjectTransferCrypto(sessionCrypto: bobCrypto)
        let channel = try ChannelCode("DOCK-A-17")
        let attachment = try OutboundAttachment(fileName: "short-video.mp4", contentType: "video/mp4", data: Data(repeating: 0x7A, count: 1_200_000))
        let package = try await objectCrypto.createPackage(attachment: attachment, to: TestIdentities.bobPeer, localIdentity: TestIdentities.alice.identity, channel: channel)

        XCTAssertTrue(package.pieces.contains { $0.pieceKind == .recovery })
        let piecesWithOneMissing = package.pieces.filter { !($0.pieceKind == .data && $0.pieceIndex == 3) }
        let recovered = try await receiverObjectCrypto.decryptPackage(LocalWaveObjectPackage(manifest: package.manifest, pieces: piecesWithOneMissing), from: TestIdentities.alicePeer, channel: channel)

        XCTAssertEqual(recovered.attachment.data, attachment.data)
    }

    func testInviteAuthenticationRejectsWrongPhrase() throws {
        let channel = try ChannelCode("DOCK-A-17")
        let nonce = Data("first-contact-nonce".utf8)
        let proof = try InviteAuthenticator.makeProof(
            local: TestIdentities.alice.identity,
            remote: TestIdentities.bobPeer,
            channel: channel,
            invitePhrase: "correct horse battery",
            nonce: nonce
        )

        XCTAssertTrue(try InviteAuthenticator.verify(
            proof,
            local: TestIdentities.bob.identity,
            remote: TestIdentities.alicePeer,
            channel: channel,
            invitePhrase: "correct horse battery"
        ))
        XCTAssertFalse(try InviteAuthenticator.verify(
            proof,
            local: TestIdentities.bob.identity,
            remote: TestIdentities.alicePeer,
            channel: channel,
            invitePhrase: "wrong invite phrase"
        ))
    }

    func testRelayStoreKeepsOnlyOpaqueEncryptedChunks() throws {
        let chunk = RelayChunk(
            id: UUID(),
            sourcePeerId: "alice",
            destinationPeerId: "bob",
            route: .fixedRelay,
            expiresAt: Date().addingTimeInterval(60),
            payload: Data("ciphertext-only".utf8)
        )
        var store = RelayChunkStore(maxChunks: 4)

        try store.insert(chunk)
        let available = store.chunks(for: "bob", now: Date())

        XCTAssertEqual(available, [chunk])
        XCTAssertEqual(available.first?.payload, Data("ciphertext-only".utf8))
    }

    func testWakeEnvelopeDecryptsWithMatchingPeerKey() async throws {
        let alice = SessionCrypto(identityStore: InMemoryIdentityStore(keyPair: TestIdentities.alice))
        let bob = SessionCrypto(identityStore: InMemoryIdentityStore(keyPair: TestIdentities.bob))
        let channel = try ChannelCode("DOCK-A-17")

        let envelope = try await alice.encryptWake(to: TestIdentities.bobPeer, counter: 12, channel: channel)

        try await bob.decryptWake(envelope, from: TestIdentities.alicePeer, channel: channel)
    }

    func testDecryptFailsWithWrongPeerKey() async throws {
        let alice = SessionCrypto(identityStore: InMemoryIdentityStore(keyPair: TestIdentities.alice))
        let bob = SessionCrypto(identityStore: InMemoryIdentityStore(keyPair: TestIdentities.bob))
        let channel = try ChannelCode("DOCK-A-17")

        let envelope = try await alice.encryptMessage("Gate changed", to: TestIdentities.bobPeer, counter: 8, channel: channel)

        do {
            _ = try await bob.decryptMessage(envelope, from: TestIdentities.malloryPeer, channel: channel)
            XCTFail("Wrong peer key should not decrypt")
        } catch LocalWaveError.decryptionFailed {
            // Expected.
        }
    }

    func testReplayCounterIsRejectedAfterFirstDecrypt() async throws {
        let alice = SessionCrypto(identityStore: InMemoryIdentityStore(keyPair: TestIdentities.alice))
        let bob = SessionCrypto(identityStore: InMemoryIdentityStore(keyPair: TestIdentities.bob))
        let channel = try ChannelCode("DOCK-A-17")
        let envelope = try await alice.encryptMessage("Wake up desk three", to: TestIdentities.bobPeer, counter: 9, channel: channel)

        _ = try await bob.decryptMessage(envelope, from: TestIdentities.alicePeer, channel: channel)

        do {
            _ = try await bob.decryptMessage(envelope, from: TestIdentities.alicePeer, channel: channel)
            XCTFail("Duplicate counter should be rejected")
        } catch LocalWaveError.replayDetected {
            // Expected.
        }
    }
}

private enum TestIdentities {
    static let aliceKeys = Curve25519.KeyAgreement.PrivateKey()
    static let bobKeys = Curve25519.KeyAgreement.PrivateKey()
    static let malloryKeys = Curve25519.KeyAgreement.PrivateKey()
    static let aliceSigning = Curve25519.Signing.PrivateKey()
    static let bobSigning = Curve25519.Signing.PrivateKey()
    static let mallorySigning = Curve25519.Signing.PrivateKey()

    static let alice = LocalIdentityKeyPair(
        identity: LocalIdentity(
            peerId: "alice",
            displayName: "Alice",
            agreementPublicKey: aliceKeys.publicKey.rawRepresentation,
            signingPublicKey: aliceSigning.publicKey.rawRepresentation,
            fingerprint: "alice-fingerprint"
        ),
        agreementPrivateKeyData: aliceKeys.rawRepresentation,
        signingPrivateKeyData: aliceSigning.rawRepresentation
    )

    static let bob = LocalIdentityKeyPair(
        identity: LocalIdentity(
            peerId: "bob",
            displayName: "Bob",
            agreementPublicKey: bobKeys.publicKey.rawRepresentation,
            signingPublicKey: bobSigning.publicKey.rawRepresentation,
            fingerprint: "bob-fingerprint"
        ),
        agreementPrivateKeyData: bobKeys.rawRepresentation,
        signingPrivateKeyData: bobSigning.rawRepresentation
    )

    static let alicePeer = PeerProfile(id: "alice", displayName: "Alice", fingerprint: "alice-fingerprint", rssi: -44, lastSeen: Date(), state: .available, publicKeyData: aliceKeys.publicKey.rawRepresentation)
    static let bobPeer = PeerProfile(id: "bob", displayName: "Bob", fingerprint: "bob-fingerprint", rssi: -45, lastSeen: Date(), state: .available, publicKeyData: bobKeys.publicKey.rawRepresentation)
    static let malloryPeer = PeerProfile(id: "alice", displayName: "Alice", fingerprint: "wrong-fingerprint", rssi: -46, lastSeen: Date(), state: .available, publicKeyData: malloryKeys.publicKey.rawRepresentation)
}
