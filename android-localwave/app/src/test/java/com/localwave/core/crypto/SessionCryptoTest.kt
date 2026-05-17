package com.localwave.core.crypto

import com.localwave.core.model.ChannelCode
import com.localwave.core.model.DeliveryReceipt
import com.localwave.core.model.DeliveryRoute
import com.localwave.core.model.EncryptedSharePackage
import com.localwave.core.model.OutboundAttachment
import com.localwave.core.model.PeerIntroEnvelope
import com.localwave.core.model.PeerProfile
import com.localwave.core.model.PresenceState
import com.localwave.core.model.RelayChunk
import com.localwave.core.model.RelayChunkStore
import com.localwave.core.model.LocalWaveObjectPackage
import com.localwave.core.model.ObjectPieceKind
import com.localwave.core.model.ObjectControlEnvelope
import com.localwave.core.model.ObjectControlKind
import com.localwave.core.protocol.ObjectTransferCrypto
import com.localwave.core.protocol.ObjectTransferJson
import com.localwave.core.protocol.ProtocolJson
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Assert.fail
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SessionCryptoTest {
    @Test
    fun messageEnvelopeDecryptsWithMatchingPeerKey() = runTest {
        val aliceStore = InMemoryIdentityKeyStore(TestIdentities.alice)
        val bobStore = InMemoryIdentityKeyStore(TestIdentities.bob)
        val aliceCrypto = SessionCrypto(aliceStore)
        val bobCrypto = SessionCrypto(bobStore)
        val channel = ChannelCode("DOCK-A-17")

        val envelope = aliceCrypto.encryptMessage("Shift change at 06:30", TestIdentities.bobPeer, 7, channel)
        val plaintext = bobCrypto.decryptMessage(envelope, TestIdentities.alicePeer, channel)

        assertEquals("Shift change at 06:30", plaintext)
        assertEquals("alice", envelope.senderId.value)
        assertEquals("bob", envelope.recipientId.value)
    }

    @Test
    fun encodedMessageEnvelopeDecryptsAfterJsonRoundTrip() = runTest {
        val aliceCrypto = SessionCrypto(InMemoryIdentityKeyStore(TestIdentities.alice))
        val bobCrypto = SessionCrypto(InMemoryIdentityKeyStore(TestIdentities.bob))
        val channel = ChannelCode("DOCK-A-17")

        val envelope = aliceCrypto.encryptMessage("Inbound trailer cleared", TestIdentities.bobPeer, 10, channel)
        val encoded = ProtocolJson.encodeMessageEnvelope(envelope)
        val decoded = ProtocolJson.decodeMessageEnvelope(encoded)
        val plaintext = bobCrypto.decryptMessage(decoded, TestIdentities.alicePeer, channel)

        assertEquals("Inbound trailer cleared", plaintext)
        assertEquals(envelope.timestampEpochMillis, decoded.timestampEpochMillis)
    }

    @Test
    fun encodedWakeEnvelopeDecryptsAfterJsonRoundTrip() = runTest {
        val aliceCrypto = SessionCrypto(InMemoryIdentityKeyStore(TestIdentities.alice))
        val bobCrypto = SessionCrypto(InMemoryIdentityKeyStore(TestIdentities.bob))
        val channel = ChannelCode("DOCK-A-17")

        val envelope = aliceCrypto.encryptWake(TestIdentities.bobPeer, 11, channel)
        val encoded = ProtocolJson.encodeWakeEnvelope(envelope)
        val decoded = ProtocolJson.decodeWakeEnvelope(encoded)

        bobCrypto.decryptWake(decoded, TestIdentities.alicePeer, channel)
        assertEquals(envelope.timestampEpochMillis, decoded.timestampEpochMillis)
    }

    @Test
    fun deliveryReceiptRoundTripsForMessageStatusAck() {
        val receipt = DeliveryReceipt(
            messageId = UUID.randomUUID(),
            senderId = TestIdentities.alice.identity.peerId,
            recipientId = TestIdentities.bob.identity.peerId,
            deliveredAtEpochMillis = 123456789L
        )

        val decoded = ProtocolJson.decodeDeliveryReceipt(ProtocolJson.encodeDeliveryReceipt(receipt))

        assertEquals(receipt.messageId, decoded.messageId)
        assertEquals(receipt.senderId, decoded.senderId)
        assertEquals(receipt.recipientId, decoded.recipientId)
        assertEquals(receipt.deliveredAtEpochMillis, decoded.deliveredAtEpochMillis)
    }

    @Test
    fun peerIntroRoundTripsWithIdentityPublicKey() {
        val intro = PeerIntroEnvelope(
            peerId = TestIdentities.alice.identity.peerId,
            displayName = "Alice",
            fingerprint = TestIdentities.alice.identity.fingerprint,
            agreementPublicKey = TestIdentities.alice.identity.agreementPublicKey,
            sentAtEpochMillis = 123456789L
        )

        val decoded = ProtocolJson.decodePeerIntro(ProtocolJson.encodePeerIntro(intro))

        assertEquals(intro.peerId, decoded.peerId)
        assertEquals(intro.displayName, decoded.displayName)
        assertEquals(intro.fingerprint, decoded.fingerprint)
        assertEquals(intro.agreementPublicKey.toList(), decoded.agreementPublicKey.toList())
        assertEquals(intro.sentAtEpochMillis, decoded.sentAtEpochMillis)
    }

    @Test
    fun objectControlEnvelopeRoundTripsForPieceAck() {
        val transferId = UUID.randomUUID()
        val control = ObjectControlEnvelope(
            kind = ObjectControlKind.PIECE_ACK,
            objectId = "a".repeat(64),
            senderId = TestIdentities.bob.identity.peerId,
            recipientId = TestIdentities.alice.identity.peerId,
            transferId = transferId,
            pieceIndexes = listOf(0, 1, 2, 3),
            updatedAtEpochMillis = 1_778_900_000_000
        )

        val decoded = ObjectTransferJson.decodeObjectControl(ObjectTransferJson.encodeObjectControl(control))

        assertEquals(1, decoded.objectProtocolVersion.toInt())
        assertEquals(ObjectControlKind.PIECE_ACK, decoded.kind)
        assertEquals(control.objectId, decoded.objectId)
        assertEquals(transferId, decoded.transferId)
        assertEquals(listOf(0, 1, 2, 3), decoded.pieceIndexes)
        assertEquals(null, decoded.resumeToken)
        assertEquals(null, decoded.failureReason)
    }

    @Test
    fun encodedAttachmentEnvelopeDecryptsAfterJsonRoundTrip() = runTest {
        val aliceCrypto = SessionCrypto(InMemoryIdentityKeyStore(TestIdentities.alice))
        val bobCrypto = SessionCrypto(InMemoryIdentityKeyStore(TestIdentities.bob))
        val channel = ChannelCode("DOCK-A-17")
        val attachment = OutboundAttachment(
            fileName = "dock-photo.jpg",
            contentType = "image/jpeg",
            data = "encrypted-image-bytes".encodeToByteArray()
        )

        val envelope = aliceCrypto.encryptAttachment(attachment, TestIdentities.bobPeer, 12, channel)
        val encoded = ProtocolJson.encodeAttachmentEnvelope(envelope)
        val decoded = ProtocolJson.decodeAttachmentEnvelope(encoded)
        val decrypted = bobCrypto.decryptAttachment(decoded, TestIdentities.alicePeer, channel)

        assertEquals(attachment.fileName, decrypted.fileName)
        assertEquals(attachment.contentType, decrypted.contentType)
        assertEquals(attachment.data.decodeToString(), decrypted.data.decodeToString())
        assertEquals(envelope.transferId, decoded.transferId)
    }

    @Test
    fun encryptedSharePackageRoundTripsThroughProtocolJson() {
        val envelope = com.localwave.core.model.AttachmentEnvelope(
            senderId = TestIdentities.alice.identity.peerId,
            recipientId = TestIdentities.bob.identity.peerId,
            timestampEpochMillis = 1234,
            transferId = UUID.randomUUID(),
            replayCounter = 1uL,
            nonce = byteArrayOf(1, 2, 3),
            ciphertext = byteArrayOf(4, 5, 6),
            tag = byteArrayOf(7, 8, 9)
        )
        val packageFile = EncryptedSharePackage(
            packageId = envelope.transferId,
            createdAtEpochMillis = envelope.timestampEpochMillis,
            route = DeliveryRoute.NATIVE_SHARE,
            senderId = envelope.senderId,
            recipientId = envelope.recipientId,
            envelope = envelope
        )

        val decoded = ProtocolJson.decodeEncryptedSharePackage(ProtocolJson.encodeEncryptedSharePackage(packageFile))

        assertEquals(packageFile.packageId, decoded.packageId)
        assertEquals(packageFile.route, decoded.route)
        assertEquals(packageFile.envelope.transferId, decoded.envelope.transferId)
    }

    @Test
    fun objectPackageDecryptsAfterJsonRoundTrip() = runTest {
        val aliceCrypto = SessionCrypto(InMemoryIdentityKeyStore(TestIdentities.alice))
        val bobCrypto = SessionCrypto(InMemoryIdentityKeyStore(TestIdentities.bob))
        val senderObjectCrypto = ObjectTransferCrypto(aliceCrypto)
        val receiverObjectCrypto = ObjectTransferCrypto(bobCrypto)
        val channel = ChannelCode("DOCK-A-17")
        val attachment = OutboundAttachment("inventory.pdf", "application/pdf", ByteArray(128 * 1024) { 0x42 })

        val packageFile = senderObjectCrypto.createPackage(attachment, TestIdentities.bobPeer, TestIdentities.alice.identity, channel)
        val decoded = ObjectTransferJson.decodePackage(ObjectTransferJson.encodePackage(packageFile))
        val result = receiverObjectCrypto.decryptPackage(decoded, TestIdentities.alicePeer, channel)

        assertEquals(attachment.fileName, result.attachment.fileName)
        assertEquals(attachment.data.size, result.attachment.data.size)
        assertEquals(packageFile.manifest.objectId, result.receipt.objectId)
    }

    @Test
    fun objectPackageRecoversOneMissingPieceWithParity() = runTest {
        val aliceCrypto = SessionCrypto(InMemoryIdentityKeyStore(TestIdentities.alice))
        val bobCrypto = SessionCrypto(InMemoryIdentityKeyStore(TestIdentities.bob))
        val senderObjectCrypto = ObjectTransferCrypto(aliceCrypto)
        val receiverObjectCrypto = ObjectTransferCrypto(bobCrypto)
        val channel = ChannelCode("DOCK-A-17")
        val attachment = OutboundAttachment("short-video.mp4", "video/mp4", ByteArray(1_200_000) { 0x7A })
        val packageFile = senderObjectCrypto.createPackage(attachment, TestIdentities.bobPeer, TestIdentities.alice.identity, channel)

        assertEquals(true, packageFile.pieces.any { it.pieceKind == ObjectPieceKind.RECOVERY })
        val missingOne = packageFile.pieces.filterNot { it.pieceKind == ObjectPieceKind.DATA && it.pieceIndex == 3 }
        val result = receiverObjectCrypto.decryptPackage(LocalWaveObjectPackage(packageFile.manifest, missingOne), TestIdentities.alicePeer, channel)

        assertEquals(attachment.data.size, result.attachment.data.size)
        assertEquals(attachment.data.last(), result.attachment.data.last())
    }

    @Test
    fun inviteAuthenticationRejectsWrongPhrase() {
        val channel = ChannelCode("DOCK-A-17")
        val nonce = "first-contact-nonce".encodeToByteArray()
        val proof = InviteAuthenticator.makeProof(
            local = TestIdentities.alice.identity,
            remote = TestIdentities.bobPeer,
            channel = channel,
            invitePhrase = "correct horse battery",
            nonce = nonce
        )

        assertEquals(true, InviteAuthenticator.verify(proof, TestIdentities.bob.identity, TestIdentities.alicePeer, channel, "correct horse battery"))
        assertEquals(false, InviteAuthenticator.verify(proof, TestIdentities.bob.identity, TestIdentities.alicePeer, channel, "wrong invite phrase"))
    }

    @Test
    fun relayStoreKeepsOnlyOpaqueEncryptedChunks() {
        val chunk = RelayChunk(
            id = UUID.randomUUID(),
            sourcePeerId = TestIdentities.alice.identity.peerId,
            destinationPeerId = TestIdentities.bob.identity.peerId,
            route = DeliveryRoute.FIXED_RELAY,
            expiresAtEpochMillis = System.currentTimeMillis() + 60_000,
            payload = "ciphertext-only".encodeToByteArray()
        )
        val store = RelayChunkStore(maxChunks = 4)

        store.insert(chunk)
        val available = store.chunksFor(TestIdentities.bob.identity.peerId)

        assertEquals(1, available.size)
        assertEquals("ciphertext-only", available.single().payload.decodeToString())
    }

    @Test
    fun decryptFailsWithWrongPeerKey() = runTest {
        val aliceCrypto = SessionCrypto(InMemoryIdentityKeyStore(TestIdentities.alice))
        val bobCrypto = SessionCrypto(InMemoryIdentityKeyStore(TestIdentities.bob))
        val channel = ChannelCode("DOCK-A-17")
        val envelope = aliceCrypto.encryptMessage("Gate changed", TestIdentities.bobPeer, 8, channel)

        try {
            bobCrypto.decryptMessage(envelope, TestIdentities.malloryPeer, channel)
            fail("Wrong peer key should not decrypt")
        } catch (_: CryptoException.DecryptionFailed) {
            // Expected.
        }
    }

    @Test
    fun replayCounterIsRejectedAfterFirstDecrypt() = runTest {
        val aliceCrypto = SessionCrypto(InMemoryIdentityKeyStore(TestIdentities.alice))
        val bobCrypto = SessionCrypto(InMemoryIdentityKeyStore(TestIdentities.bob))
        val channel = ChannelCode("DOCK-A-17")
        val envelope = aliceCrypto.encryptMessage("Wake up desk three", TestIdentities.bobPeer, 9, channel)

        bobCrypto.decryptMessage(envelope, TestIdentities.alicePeer, channel)

        try {
            bobCrypto.decryptMessage(envelope, TestIdentities.alicePeer, channel)
            fail("Duplicate counter should be rejected")
        } catch (_: CryptoException.ReplayDetected) {
            // Expected.
        }
    }
}

private object TestIdentities {
    val alice = LocalIdentityKeyPair.generate(peerId = "alice", displayName = "Alice")
    val bob = LocalIdentityKeyPair.generate(peerId = "bob", displayName = "Bob")
    val mallory = LocalIdentityKeyPair.generate(peerId = "alice", displayName = "Mallory")

    val alicePeer = PeerProfile(
        id = alice.identity.peerId,
        displayName = "Alice",
        fingerprint = alice.identity.fingerprint,
        rssi = -44,
        lastSeenEpochMillis = 1,
        state = PresenceState.AVAILABLE,
        publicKeyData = alice.identity.agreementPublicKey
    )
    val bobPeer = PeerProfile(
        id = bob.identity.peerId,
        displayName = "Bob",
        fingerprint = bob.identity.fingerprint,
        rssi = -45,
        lastSeenEpochMillis = 1,
        state = PresenceState.AVAILABLE,
        publicKeyData = bob.identity.agreementPublicKey
    )
    val malloryPeer = PeerProfile(
        id = alice.identity.peerId,
        displayName = "Alice",
        fingerprint = mallory.identity.fingerprint,
        rssi = -46,
        lastSeenEpochMillis = 1,
        state = PresenceState.AVAILABLE,
        publicKeyData = mallory.identity.agreementPublicKey
    )
}
