package com.localwave.core.crypto

import com.localwave.core.model.ChannelCode
import com.localwave.core.model.PeerProfile
import com.localwave.core.model.PresenceState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.fail
import org.junit.Assert.assertEquals
import org.junit.Test

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
