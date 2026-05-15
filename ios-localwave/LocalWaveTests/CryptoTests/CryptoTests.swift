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
