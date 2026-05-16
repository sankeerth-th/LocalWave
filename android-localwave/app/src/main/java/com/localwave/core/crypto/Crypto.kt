package com.localwave.core.crypto

import com.localwave.core.model.ChannelCode
import com.localwave.core.model.AttachmentEnvelope
import com.localwave.core.model.AttachmentPlaintext
import com.localwave.core.model.LocalIdentity
import com.localwave.core.model.MessageEnvelope
import com.localwave.core.model.OutboundAttachment
import com.localwave.core.model.PeerId
import com.localwave.core.model.PeerProfile
import com.localwave.core.model.WakeEnvelope
import com.localwave.core.protocol.ProtocolJson
import com.localwave.core.protocol.CryptoService
import com.localwave.core.protocol.ProtocolConstants
import com.localwave.core.protocol.UuidDerivation
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters

sealed class CryptoException(message: String) : RuntimeException(message) {
    data object MissingPeerPublicKey : CryptoException("Peer public key is missing.")
    data object DecryptionFailed : CryptoException("Envelope could not be decrypted.")
    data object ReplayDetected : CryptoException("Duplicate replay counter rejected.")
    data object IdentityUnavailable : CryptoException("Local identity is unavailable.")
}

data class LocalIdentityKeyPair(
    val identity: LocalIdentity,
    val agreementPrivateKeyData: ByteArray,
    val signingPrivateKeyData: ByteArray
) {
    companion object {
        fun generate(peerId: String? = null, displayName: String): LocalIdentityKeyPair {
            val secureRandom = SecureRandom()
            val agreement = X25519PrivateKeyParameters(secureRandom)
            val signing = Ed25519PrivateKeyParameters(secureRandom)
            val agreementPrivate = ByteArray(X25519PrivateKeyParameters.KEY_SIZE)
            val agreementPublic = ByteArray(X25519PublicKeyParameters.KEY_SIZE)
            val signingPrivate = ByteArray(Ed25519PrivateKeyParameters.KEY_SIZE)
            val signingPublic = ByteArray(32)
            agreement.encode(agreementPrivate, 0)
            agreement.generatePublicKey().encode(agreementPublic, 0)
            signing.encode(signingPrivate, 0)
            signing.generatePublicKey().encode(signingPublic, 0)
            val fingerprint = IdentityFingerprint.make(agreementPublic, signingPublic)
            return LocalIdentityKeyPair(
                identity = LocalIdentity(
                    peerId = PeerId(peerId ?: fingerprint.take(16)),
                    displayName = displayName,
                    agreementPublicKey = agreementPublic,
                    signingPublicKey = signingPublic,
                    fingerprint = fingerprint
                ),
                agreementPrivateKeyData = agreementPrivate,
                signingPrivateKeyData = signingPrivate
            )
        }
    }
}

interface IdentityKeyStore {
    suspend fun loadOrCreateIdentity(displayName: String): LocalIdentity
    suspend fun currentIdentity(): LocalIdentity
    suspend fun keyPair(): LocalIdentityKeyPair
    suspend fun updateDisplayName(displayName: String)
    suspend fun reset()
}

class InMemoryIdentityKeyStore(private var material: LocalIdentityKeyPair? = null) : IdentityKeyStore {
    override suspend fun loadOrCreateIdentity(displayName: String): LocalIdentity {
        val existing = material
        if (existing != null) {
            val updated = existing.copy(identity = existing.identity.copy(displayName = displayName))
            material = updated
            return updated.identity
        }
        val generated = LocalIdentityKeyPair.generate(displayName = displayName)
        material = generated
        return generated.identity
    }

    override suspend fun currentIdentity(): LocalIdentity = material?.identity ?: throw CryptoException.IdentityUnavailable
    override suspend fun keyPair(): LocalIdentityKeyPair = material ?: throw CryptoException.IdentityUnavailable
    override suspend fun updateDisplayName(displayName: String) {
        val existing = material ?: throw CryptoException.IdentityUnavailable
        material = existing.copy(identity = existing.identity.copy(displayName = displayName))
    }
    override suspend fun reset() {
        material = null
    }
}

object IdentityFingerprint {
    fun make(agreementPublicKey: ByteArray, signingPublicKey: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(ProtocolConstants.IDENTITY_PREFIX.toByteArray(StandardCharsets.UTF_8))
        digest.update(agreementPublicKey)
        digest.update(signingPublicKey)
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}

class ReplayProtector {
    private val highestCounters = mutableMapOf<String, ULong>()
    private val messageIds = mutableSetOf<UUID>()

    @Synchronized
    fun validate(senderId: PeerId, channel: ChannelCode, counter: ULong, messageId: UUID? = null) {
        val key = "${senderId.value}|${channel.normalized}"
        val highest = highestCounters[key]
        if (highest != null && counter <= highest) throw CryptoException.ReplayDetected
        if (messageId != null && !messageIds.add(messageId)) throw CryptoException.ReplayDetected
        highestCounters[key] = counter
    }
}

class SessionCrypto(
    private val identityStore: IdentityKeyStore,
    private val replayProtector: ReplayProtector = ReplayProtector()
) : CryptoService {
    override suspend fun encryptMessage(
        text: String,
        peer: PeerProfile,
        counter: Long,
        channel: ChannelCode
    ): MessageEnvelope {
        val messageId = UUID.randomUUID()
        val secure = encryptSecureEnvelope(
            plaintext = text.toByteArray(StandardCharsets.UTF_8),
            kind = "message",
            messageId = messageId,
            peer = peer,
            counter = counter.toULong(),
            channel = channel
        )
        return MessageEnvelope(
            version = secure.version,
            senderId = secure.senderId,
            recipientId = secure.recipientId,
            timestampEpochMillis = secure.timestampEpochMillis,
            messageId = messageId,
            replayCounter = secure.replayCounter,
            nonce = secure.nonce,
            ciphertext = secure.ciphertext,
            tag = secure.tag
        )
    }

    override suspend fun decryptMessage(envelope: MessageEnvelope, peer: PeerProfile, channel: ChannelCode): String {
        val secure = SecureEnvelope(
            kind = "message",
            senderId = envelope.senderId,
            recipientId = envelope.recipientId,
            timestampEpochMillis = envelope.timestampEpochMillis,
            messageId = envelope.messageId,
            replayCounter = envelope.replayCounter,
            nonce = envelope.nonce,
            ciphertext = envelope.ciphertext,
            tag = envelope.tag
        )
        val plaintext = decryptSecureEnvelope(secure, peer, channel)
        replayProtector.validate(envelope.senderId, channel, envelope.replayCounter, envelope.messageId)
        return plaintext.toString(StandardCharsets.UTF_8)
    }

    suspend fun encryptWake(peer: PeerProfile, counter: Long, channel: ChannelCode): WakeEnvelope {
        val secure = encryptSecureEnvelope(
            plaintext = "wake".toByteArray(StandardCharsets.UTF_8),
            kind = "wake",
            messageId = null,
            peer = peer,
            counter = counter.toULong(),
            channel = channel
        )
        return WakeEnvelope(
            version = secure.version,
            senderId = secure.senderId,
            recipientId = secure.recipientId,
            timestampEpochMillis = secure.timestampEpochMillis,
            replayCounter = secure.replayCounter,
            nonce = secure.nonce,
            ciphertext = secure.ciphertext,
            tag = secure.tag
        )
    }

    suspend fun decryptWake(envelope: WakeEnvelope, peer: PeerProfile, channel: ChannelCode) {
        val secure = SecureEnvelope(
            kind = "wake",
            senderId = envelope.senderId,
            recipientId = envelope.recipientId,
            timestampEpochMillis = envelope.timestampEpochMillis,
            messageId = null,
            replayCounter = envelope.replayCounter,
            nonce = envelope.nonce,
            ciphertext = envelope.ciphertext,
            tag = envelope.tag
        )
        decryptSecureEnvelope(secure, peer, channel)
        replayProtector.validate(envelope.senderId, channel, envelope.replayCounter)
    }

    override suspend fun encryptAttachment(
        attachment: OutboundAttachment,
        peer: PeerProfile,
        counter: Long,
        channel: ChannelCode
    ): AttachmentEnvelope {
        val transferId = UUID.randomUUID()
        val plaintext = AttachmentPlaintext(
            fileName = attachment.fileName,
            contentType = attachment.contentType.ifBlank { "application/octet-stream" },
            byteCount = attachment.data.size,
            sha256 = MessageDigest.getInstance("SHA-256").digest(attachment.data),
            payload = attachment.data
        )
        val secure = encryptSecureEnvelope(
            plaintext = ProtocolJson.encodeAttachmentPlaintext(plaintext),
            kind = "attachment",
            messageId = transferId,
            peer = peer,
            counter = counter.toULong(),
            channel = channel
        )
        return AttachmentEnvelope(
            version = secure.version,
            senderId = secure.senderId,
            recipientId = secure.recipientId,
            timestampEpochMillis = secure.timestampEpochMillis,
            transferId = transferId,
            replayCounter = secure.replayCounter,
            nonce = secure.nonce,
            ciphertext = secure.ciphertext,
            tag = secure.tag
        )
    }

    override suspend fun decryptAttachment(envelope: AttachmentEnvelope, peer: PeerProfile, channel: ChannelCode): OutboundAttachment {
        val secure = SecureEnvelope(
            kind = "attachment",
            senderId = envelope.senderId,
            recipientId = envelope.recipientId,
            timestampEpochMillis = envelope.timestampEpochMillis,
            messageId = envelope.transferId,
            replayCounter = envelope.replayCounter,
            nonce = envelope.nonce,
            ciphertext = envelope.ciphertext,
            tag = envelope.tag
        )
        val plaintextBytes = decryptSecureEnvelope(secure, peer, channel)
        replayProtector.validate(envelope.senderId, channel, envelope.replayCounter, envelope.transferId)
        val plaintext = ProtocolJson.decodeAttachmentPlaintext(plaintextBytes)
        if (plaintext.byteCount != plaintext.payload.size) throw CryptoException.DecryptionFailed
        val computedHash = MessageDigest.getInstance("SHA-256").digest(plaintext.payload)
        if (!computedHash.contentEquals(plaintext.sha256)) throw CryptoException.DecryptionFailed
        return OutboundAttachment(
            fileName = plaintext.fileName,
            contentType = plaintext.contentType.ifBlank { "application/octet-stream" },
            data = plaintext.payload
        )
    }

    private suspend fun encryptSecureEnvelope(
        plaintext: ByteArray,
        kind: String,
        messageId: UUID?,
        peer: PeerProfile,
        counter: ULong,
        channel: ChannelCode
    ): SecureEnvelope {
        val peerPublic = peer.publicKeyData ?: throw CryptoException.MissingPeerPublicKey
        val keyPair = identityStore.keyPair()
        val envelope = SecureEnvelope(
            kind = kind,
            senderId = keyPair.identity.peerId,
            recipientId = peer.id,
            timestampEpochMillis = System.currentTimeMillis(),
            messageId = messageId,
            replayCounter = counter,
            nonce = ByteArray(12),
            ciphertext = ByteArray(0),
            tag = ByteArray(0)
        )
        val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(keyPair.agreementPrivateKeyData, peerPublic, keyPair.identity.peerId, peer.id, channel)
        val sealed = aesGcmEncrypt(plaintext, key, nonce, associatedData(envelope.copy(nonce = nonce), channel))
        return envelope.copy(nonce = nonce, ciphertext = sealed.first, tag = sealed.second)
    }

    private suspend fun decryptSecureEnvelope(envelope: SecureEnvelope, peer: PeerProfile, channel: ChannelCode): ByteArray {
        if (envelope.senderId != peer.id) throw CryptoException.DecryptionFailed
        val peerPublic = peer.publicKeyData ?: throw CryptoException.MissingPeerPublicKey
        val keyPair = identityStore.keyPair()
        if (envelope.recipientId != keyPair.identity.peerId) throw CryptoException.DecryptionFailed
        return try {
            val key = deriveKey(keyPair.agreementPrivateKeyData, peerPublic, keyPair.identity.peerId, peer.id, channel)
            aesGcmDecrypt(envelope.ciphertext, envelope.tag, key, envelope.nonce, associatedData(envelope, channel))
        } catch (error: CryptoException) {
            throw error
        } catch (_: Exception) {
            throw CryptoException.DecryptionFailed
        }
    }

    private fun deriveKey(
        localPrivateKeyData: ByteArray,
        remotePublicKeyData: ByteArray,
        localPeerId: PeerId,
        remotePeerId: PeerId,
        channel: ChannelCode
    ): ByteArray {
        val localPrivate = X25519PrivateKeyParameters(localPrivateKeyData, 0)
        val remotePublic = X25519PublicKeyParameters(remotePublicKeyData, 0)
        val sharedSecret = ByteArray(32)
        localPrivate.generateSecret(remotePublic, sharedSecret, 0)
        val salt = UuidDerivation.derive(channel).hkdfSalt
        val peers = listOf(localPeerId.value, remotePeerId.value).sorted().joinToString("|")
        val info = "${ProtocolConstants.SESSION_INFO_PREFIX}|$peers".toByteArray(StandardCharsets.UTF_8)
        return hkdfSha256(sharedSecret, salt, info, 32)
    }

    private fun associatedData(envelope: SecureEnvelope, channel: ChannelCode): ByteArray {
        val out = mutableListOf<Byte>()
        out.addAll(ProtocolConstants.AAD_PREFIX.toByteArray(StandardCharsets.UTF_8).toList())
        out.add(envelope.version.toByte())
        out.addUtf8Field(envelope.kind)
        out.addUtf8Field(envelope.senderId.value)
        out.addUtf8Field(envelope.recipientId.value)
        out.addUtf8Field(envelope.messageId?.toString()?.uppercase() ?: "")
        out.addAll(ByteBuffer.allocate(8).putLong(envelope.timestampEpochMillis).array().toList())
        out.addAll(ByteBuffer.allocate(8).putLong(envelope.replayCounter.toLong()).array().toList())
        out.addUtf8Field(channel.normalized)
        return out.toByteArray()
    }

    private data class SecureEnvelope(
        val version: UByte = 1u,
        val kind: String,
        val senderId: PeerId,
        val recipientId: PeerId,
        val timestampEpochMillis: Long,
        val messageId: UUID?,
        val replayCounter: ULong,
        val nonce: ByteArray,
        val ciphertext: ByteArray,
        val tag: ByteArray
    )
}

object InviteAuthenticator {
    fun makeProof(
        local: LocalIdentity,
        remote: PeerProfile,
        channel: ChannelCode,
        invitePhrase: String,
        nonce: ByteArray = ByteArray(32).also { SecureRandom().nextBytes(it) }
    ): InviteAuthProof {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(inviteKey(channel, invitePhrase), "HmacSHA256"))
        val proof = mac.doFinal(transcript(local.peerId, remote.id, channel, local.fingerprint, remote.fingerprint, nonce))
        return InviteAuthProof(local.peerId, remote.id, nonce, proof)
    }

    fun verify(proof: InviteAuthProof, local: LocalIdentity, remote: PeerProfile, channel: ChannelCode, invitePhrase: String): Boolean {
        if (proof.senderId != remote.id || proof.recipientId != local.peerId) return false
        val expected = makeProof(
            local = LocalIdentity(remote.id, remote.displayName, remote.publicKeyData ?: byteArrayOf(), byteArrayOf(), remote.fingerprint),
            remote = PeerProfile(local.peerId, local.displayName, local.fingerprint, 0, 0, com.localwave.core.model.PresenceState.AVAILABLE, publicKeyData = local.agreementPublicKey),
            channel = channel,
            invitePhrase = invitePhrase,
            nonce = proof.nonce
        )
        return MessageDigest.isEqual(proof.proof, expected.proof)
    }

    private fun inviteKey(channel: ChannelCode, invitePhrase: String): ByteArray {
        val normalized = invitePhrase.trim()
        require(normalized.encodeToByteArray().size >= 12) { "Private channels require an invite phrase of at least 12 characters." }
        val phraseDigest = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray(StandardCharsets.UTF_8))
        return hkdfSha256(
            ikm = phraseDigest,
            salt = UuidDerivation.derive(channel).hkdfSalt,
            info = "LocalWave.InviteAuth.v1".toByteArray(StandardCharsets.UTF_8),
            length = 32
        )
    }

    private fun transcript(
        localId: PeerId,
        remoteId: PeerId,
        channel: ChannelCode,
        localFingerprint: String,
        remoteFingerprint: String,
        nonce: ByteArray
    ): ByteArray {
        val orderedPeers = listOf(localId.value, remoteId.value).sorted().joinToString("|")
        val orderedFingerprints = listOf(localFingerprint, remoteFingerprint).sorted().joinToString("|")
        val out = mutableListOf<Byte>()
        out.addAll("LocalWave.FirstContact.v1".toByteArray(StandardCharsets.UTF_8).toList())
        out.addUtf8Field(channel.normalized)
        out.addUtf8Field(orderedPeers)
        out.addUtf8Field(orderedFingerprints)
        out.addAll(nonce.toList())
        return out.toByteArray()
    }
}

data class InviteAuthProof(
    val senderId: PeerId,
    val recipientId: PeerId,
    val nonce: ByteArray,
    val proof: ByteArray,
    val version: UByte = 1u
)

private fun MutableList<Byte>.addUtf8Field(value: String) {
    val bytes = value.toByteArray(StandardCharsets.UTF_8)
    addAll(ByteBuffer.allocate(4).putInt(bytes.size).array().toList())
    addAll(bytes.toList())
}

private fun hkdfSha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(salt, "HmacSHA256"))
    val prk = mac.doFinal(ikm)
    val result = ByteArray(length)
    var previous = ByteArray(0)
    var offset = 0
    var counter = 1
    while (offset < length) {
        mac.init(SecretKeySpec(prk, "HmacSHA256"))
        mac.update(previous)
        mac.update(info)
        mac.update(counter.toByte())
        previous = mac.doFinal()
        val toCopy = minOf(previous.size, length - offset)
        previous.copyInto(result, offset, 0, toCopy)
        offset += toCopy
        counter += 1
    }
    return result
}

private fun aesGcmEncrypt(plaintext: ByteArray, key: ByteArray, nonce: ByteArray, aad: ByteArray): Pair<ByteArray, ByteArray> {
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
    cipher.updateAAD(aad)
    val combined = cipher.doFinal(plaintext)
    return combined.copyOfRange(0, combined.size - 16) to combined.copyOfRange(combined.size - 16, combined.size)
}

private fun aesGcmDecrypt(ciphertext: ByteArray, tag: ByteArray, key: ByteArray, nonce: ByteArray, aad: ByteArray): ByteArray {
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
    cipher.updateAAD(aad)
    return cipher.doFinal(ciphertext + tag)
}
