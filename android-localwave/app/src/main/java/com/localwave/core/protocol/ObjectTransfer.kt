package com.localwave.core.protocol

import com.localwave.core.crypto.SessionCrypto
import com.localwave.core.crypto.aesGcmDecrypt
import com.localwave.core.crypto.aesGcmEncrypt
import com.localwave.core.model.ChannelCode
import com.localwave.core.model.EncryptedObjectManifest
import com.localwave.core.model.LocalIdentity
import com.localwave.core.model.LocalWaveObjectPackage
import com.localwave.core.model.ObjectManifestPlaintext
import com.localwave.core.model.ObjectPiece
import com.localwave.core.model.ObjectPieceBatch
import com.localwave.core.model.ObjectPieceKind
import com.localwave.core.model.ObjectRelayPolicy
import com.localwave.core.model.ObjectTransferResult
import com.localwave.core.model.OutboundAttachment
import com.localwave.core.model.PeerId
import com.localwave.core.model.PeerProfile
import com.localwave.core.model.PieceInventory
import com.localwave.core.model.RelayChunk
import com.localwave.core.model.ResumeToken
import com.localwave.core.model.TransferReceipt
import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

object ObjectTransferJson {
    private val b64 = Base64.getEncoder()
    private val b64d = Base64.getDecoder()

    fun encodeManifestPlaintext(manifest: ObjectManifestPlaintext): ByteArray =
        JSONObject()
            .put("objectProtocolVersion", manifest.objectProtocolVersion.toInt())
            .put("senderId", manifest.senderId.value)
            .put("recipientIds", JSONArray(manifest.recipientIds.map { it.value }))
            .put("objectType", manifest.objectType)
            .put("fileName", manifest.fileName)
            .put("mimeType", manifest.mimeType)
            .put("plainSize", manifest.plainSize)
            .put("encryptedSize", manifest.encryptedSize)
            .put("pieceSize", manifest.pieceSize)
            .put("pieceCount", manifest.pieceCount)
            .put("recoveryPieceCount", manifest.recoveryPieceCount)
            .put("dataPieceHashes", bytesArray(manifest.dataPieceHashes))
            .put("plainPieceHashes", bytesArray(manifest.plainPieceHashes))
            .put("merkleRoot", b64.encodeToString(manifest.merkleRoot))
            .put("plainSha256", b64.encodeToString(manifest.plainSha256))
            .put("createdAtEpochMillis", manifest.createdAtEpochMillis)
            .put("expiresAtEpochMillis", manifest.expiresAtEpochMillis)
            .put("relayPolicy", manifest.relayPolicy.wireName())
            .put("previewPolicy", manifest.previewPolicy)
            .toString()
            .encodeToByteArray()

    fun decodeManifestPlaintext(bytes: ByteArray): ObjectManifestPlaintext =
        JSONObject(bytes.decodeToString()).let { json ->
            ObjectManifestPlaintext(
                objectProtocolVersion = json.getInt("objectProtocolVersion").toUByte(),
                senderId = PeerId(json.getString("senderId")),
                recipientIds = json.getJSONArray("recipientIds").toStringList().map(::PeerId),
                objectType = json.getString("objectType"),
                fileName = json.getString("fileName"),
                mimeType = json.getString("mimeType"),
                plainSize = json.getInt("plainSize"),
                encryptedSize = json.getInt("encryptedSize"),
                pieceSize = json.getInt("pieceSize"),
                pieceCount = json.getInt("pieceCount"),
                recoveryPieceCount = json.getInt("recoveryPieceCount"),
                dataPieceHashes = json.getJSONArray("dataPieceHashes").toBytesList(),
                plainPieceHashes = json.getJSONArray("plainPieceHashes").toBytesList(),
                merkleRoot = b64d.decode(json.getString("merkleRoot")),
                plainSha256 = b64d.decode(json.getString("plainSha256")),
                createdAtEpochMillis = json.getLong("createdAtEpochMillis"),
                expiresAtEpochMillis = json.getLong("expiresAtEpochMillis"),
                relayPolicy = relayPolicyFromWire(json.getString("relayPolicy")),
                previewPolicy = json.getString("previewPolicy")
            )
        }

    fun encodeManifest(manifest: EncryptedObjectManifest): JSONObject =
        JSONObject()
            .put("objectProtocolVersion", manifest.objectProtocolVersion.toInt())
            .put("objectId", manifest.objectId)
            .put("senderId", manifest.senderId.value)
            .put("recipientId", manifest.recipientId.value)
            .put("createdAtEpochMillis", manifest.createdAtEpochMillis)
            .put("expiresAtEpochMillis", manifest.expiresAtEpochMillis)
            .put("nonce", b64.encodeToString(manifest.nonce))
            .put("ciphertext", b64.encodeToString(manifest.ciphertext))
            .put("tag", b64.encodeToString(manifest.tag))
            .put("wrappedObjectKeyNonce", b64.encodeToString(manifest.wrappedObjectKeyNonce))
            .put("wrappedObjectKeyCiphertext", b64.encodeToString(manifest.wrappedObjectKeyCiphertext))
            .put("wrappedObjectKeyTag", b64.encodeToString(manifest.wrappedObjectKeyTag))
            .put("senderFingerprint", manifest.senderFingerprint)
            .put("senderAgreementPublicKey", b64.encodeToString(manifest.senderAgreementPublicKey))

    fun decodeManifest(json: JSONObject): EncryptedObjectManifest =
        EncryptedObjectManifest(
            objectProtocolVersion = json.getInt("objectProtocolVersion").toUByte(),
            objectId = json.getString("objectId"),
            senderId = PeerId(json.getString("senderId")),
            recipientId = PeerId(json.getString("recipientId")),
            createdAtEpochMillis = json.getLong("createdAtEpochMillis"),
            expiresAtEpochMillis = json.getLong("expiresAtEpochMillis"),
            nonce = b64d.decode(json.getString("nonce")),
            ciphertext = b64d.decode(json.getString("ciphertext")),
            tag = b64d.decode(json.getString("tag")),
            wrappedObjectKeyNonce = b64d.decode(json.getString("wrappedObjectKeyNonce")),
            wrappedObjectKeyCiphertext = b64d.decode(json.getString("wrappedObjectKeyCiphertext")),
            wrappedObjectKeyTag = b64d.decode(json.getString("wrappedObjectKeyTag")),
            senderFingerprint = json.getString("senderFingerprint"),
            senderAgreementPublicKey = b64d.decode(json.getString("senderAgreementPublicKey"))
        )

    fun encodePiece(piece: ObjectPiece): JSONObject =
        JSONObject()
            .put("objectProtocolVersion", piece.objectProtocolVersion.toInt())
            .put("objectId", piece.objectId)
            .put("pieceIndex", piece.pieceIndex)
            .put("pieceKind", piece.pieceKind.name.lowercase())
            .put("nonce", b64.encodeToString(piece.nonce))
            .put("ciphertext", b64.encodeToString(piece.ciphertext))
            .put("tag", b64.encodeToString(piece.tag))
            .put("pieceHash", b64.encodeToString(piece.pieceHash))
            .put("merkleProof", bytesArray(piece.merkleProof))

    fun decodePiece(json: JSONObject): ObjectPiece =
        ObjectPiece(
            objectProtocolVersion = json.getInt("objectProtocolVersion").toUByte(),
            objectId = json.getString("objectId"),
            pieceIndex = json.getInt("pieceIndex"),
            pieceKind = ObjectPieceKind.valueOf(json.getString("pieceKind").uppercase()),
            nonce = b64d.decode(json.getString("nonce")),
            ciphertext = b64d.decode(json.getString("ciphertext")),
            tag = b64d.decode(json.getString("tag")),
            pieceHash = b64d.decode(json.getString("pieceHash")),
            merkleProof = json.getJSONArray("merkleProof").toBytesList()
        )

    fun encodePieceBatch(batch: ObjectPieceBatch): ByteArray =
        JSONObject()
            .put("objectProtocolVersion", batch.objectProtocolVersion.toInt())
            .put("objectId", batch.objectId)
            .put("pieces", JSONArray(batch.pieces.map(::encodePiece)))
            .toString()
            .encodeToByteArray()

    fun decodePieceBatch(bytes: ByteArray): ObjectPieceBatch =
        JSONObject(bytes.decodeToString()).let { json ->
            ObjectPieceBatch(
                objectProtocolVersion = json.getInt("objectProtocolVersion").toUByte(),
                objectId = json.getString("objectId"),
                pieces = json.getJSONArray("pieces").objects().map(::decodePiece)
            )
        }

    fun encodePackage(packageFile: LocalWaveObjectPackage): ByteArray =
        JSONObject()
            .put("manifest", encodeManifest(packageFile.manifest))
            .put("pieces", JSONArray(packageFile.pieces.map(::encodePiece)))
            .toString()
            .encodeToByteArray()

    fun decodePackage(bytes: ByteArray): LocalWaveObjectPackage =
        JSONObject(bytes.decodeToString()).let { json ->
            LocalWaveObjectPackage(
                manifest = decodeManifest(json.getJSONObject("manifest")),
                pieces = json.getJSONArray("pieces").objects().map(::decodePiece)
            )
        }

    fun encodeReceipt(receipt: TransferReceipt): ByteArray =
        JSONObject()
            .put("objectProtocolVersion", receipt.objectProtocolVersion.toInt())
            .put("transferId", receipt.transferId.toString().uppercase())
            .put("objectId", receipt.objectId)
            .put("senderId", receipt.senderId.value)
            .put("recipientId", receipt.recipientId.value)
            .put("completedAtEpochMillis", receipt.completedAtEpochMillis)
            .put("verifiedPlainSha256", b64.encodeToString(receipt.verifiedPlainSha256))
            .toString()
            .encodeToByteArray()

    fun decodeReceipt(bytes: ByteArray): TransferReceipt =
        JSONObject(bytes.decodeToString()).let { json ->
            TransferReceipt(
                objectProtocolVersion = json.getInt("objectProtocolVersion").toUByte(),
                transferId = UUID.fromString(json.getString("transferId")),
                objectId = json.getString("objectId"),
                senderId = PeerId(json.getString("senderId")),
                recipientId = PeerId(json.getString("recipientId")),
                completedAtEpochMillis = json.getLong("completedAtEpochMillis"),
                verifiedPlainSha256 = b64d.decode(json.getString("verifiedPlainSha256"))
            )
        }

    private fun bytesArray(values: List<ByteArray>): JSONArray = JSONArray(values.map { b64.encodeToString(it) })
    private fun JSONArray.toStringList(): List<String> = (0 until length()).map { getString(it) }
    private fun JSONArray.toBytesList(): List<ByteArray> = (0 until length()).map { b64d.decode(getString(it)) }
    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
    private fun ObjectRelayPolicy.wireName(): String = when (this) {
        ObjectRelayPolicy.DIRECT_ONLY -> "directOnly"
        ObjectRelayPolicy.TRUSTED_PEERS_ONLY -> "trustedPeersOnly"
    }
    private fun relayPolicyFromWire(value: String): ObjectRelayPolicy = when (value) {
        "directOnly", "DIRECT_ONLY" -> ObjectRelayPolicy.DIRECT_ONLY
        "trustedPeersOnly", "TRUSTED_PEERS_ONLY" -> ObjectRelayPolicy.TRUSTED_PEERS_ONLY
        else -> throw IllegalArgumentException("Unknown relay policy.")
    }
}

class ObjectTransferCrypto(private val sessionCrypto: SessionCrypto) {
    companion object {
        const val DEFAULT_PIECE_SIZE = 32 * 1024
        private const val RECOVERY_STRIPE_SIZE = 10
    }

    suspend fun createPackage(
        attachment: OutboundAttachment,
        peer: PeerProfile,
        localIdentity: LocalIdentity,
        channel: ChannelCode,
        now: Long = System.currentTimeMillis(),
        expiresInMillis: Long = 24 * 60 * 60 * 1000L
    ): LocalWaveObjectPackage {
        val objectKey = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val dataChunks = attachment.data.chunked(DEFAULT_PIECE_SIZE)
        val recoveryChunks = recoveryChunks(dataChunks, RECOVERY_STRIPE_SIZE)
        val dataPieces = dataChunks.mapIndexed { index, chunk -> encryptPiece(chunk, index, ObjectPieceKind.DATA, "", objectKey) }
        val recoveryPieces = recoveryChunks.mapIndexed { offset, chunk -> encryptPiece(chunk, dataChunks.size + offset, ObjectPieceKind.RECOVERY, "", objectKey) }
        val dataPieceHashes = dataPieces.map { it.pieceHash }
        val plainPieceHashes = dataChunks.map { sha256(it) }
        val merkleRoot = merkleRoot(dataPieceHashes)
        val manifestPlaintext = ObjectManifestPlaintext(
            senderId = localIdentity.peerId,
            recipientIds = listOf(peer.id),
            objectType = objectType(attachment.contentType),
            fileName = attachment.fileName,
            mimeType = attachment.contentType.ifBlank { "application/octet-stream" },
            plainSize = attachment.data.size,
            encryptedSize = dataPieces.sumOf { it.ciphertext.size + it.tag.size },
            pieceSize = DEFAULT_PIECE_SIZE,
            pieceCount = dataChunks.size,
            recoveryPieceCount = recoveryChunks.size,
            dataPieceHashes = dataPieceHashes,
            plainPieceHashes = plainPieceHashes,
            merkleRoot = merkleRoot,
            plainSha256 = sha256(attachment.data),
            createdAtEpochMillis = now,
            expiresAtEpochMillis = now + expiresInMillis,
            relayPolicy = ObjectRelayPolicy.TRUSTED_PEERS_ONLY,
            previewPolicy = "recipient-only"
        )
        val manifestNonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val sealedManifest = aesGcmEncrypt(
            ObjectTransferJson.encodeManifestPlaintext(manifestPlaintext),
            objectKey,
            manifestNonce,
            "LocalWave.Manifest.v1".encodeToByteArray()
        )
        val objectId = sha256(manifestNonce + sealedManifest.first + sealedManifest.second).hex()
        val wrappingKey = sessionCrypto.objectWrappingKey(peer, channel)
        val wrapNonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val wrapped = aesGcmEncrypt(objectKey, wrappingKey, wrapNonce, "LocalWave.ObjectKey.v1|$objectId|${peer.id.value}".encodeToByteArray())
        val manifest = EncryptedObjectManifest(
            objectId = objectId,
            senderId = localIdentity.peerId,
            recipientId = peer.id,
            createdAtEpochMillis = now,
            expiresAtEpochMillis = now + expiresInMillis,
            nonce = manifestNonce,
            ciphertext = sealedManifest.first,
            tag = sealedManifest.second,
            wrappedObjectKeyNonce = wrapNonce,
            wrappedObjectKeyCiphertext = wrapped.first,
            wrappedObjectKeyTag = wrapped.second,
            senderFingerprint = localIdentity.fingerprint,
            senderAgreementPublicKey = localIdentity.agreementPublicKey
        )
        val pieces = (dataPieces + recoveryPieces).map { it.copy(objectId = objectId) }
        return LocalWaveObjectPackage(manifest, pieces)
    }

    suspend fun decryptPackage(packageFile: LocalWaveObjectPackage, peer: PeerProfile, channel: ChannelCode, now: Long = System.currentTimeMillis()): ObjectTransferResult {
        val objectKey = unwrapObjectKey(packageFile.manifest, peer, channel)
        val manifest = decryptManifest(packageFile.manifest, objectKey, now)
        val plaintextChunks = mutableMapOf<Int, ByteArray>()
        packageFile.pieces.filter { it.pieceKind == ObjectPieceKind.DATA }.forEach { piece ->
            if (piece.pieceIndex < manifest.pieceCount && MessageDigest.isEqual(piece.pieceHash, manifest.dataPieceHashes[piece.pieceIndex])) {
                plaintextChunks[piece.pieceIndex] = decryptPiece(piece, objectKey)
            }
        }
        recoverMissingChunks(plaintextChunks, manifest, packageFile.pieces.filter { it.pieceKind == ObjectPieceKind.RECOVERY }, objectKey)
        val payload = (0 until manifest.pieceCount).fold(ByteArray(0)) { acc, index ->
            acc + (plaintextChunks[index] ?: throw IllegalArgumentException("Object is missing verified pieces."))
        }
        if (payload.size != manifest.plainSize || !MessageDigest.isEqual(sha256(payload), manifest.plainSha256)) throw IllegalArgumentException("Object verification failed.")
        val attachment = OutboundAttachment(manifest.fileName, manifest.mimeType, payload)
        val receipt = TransferReceipt(
            transferId = UUID.fromString(packageFile.manifest.objectId.take(32).padEnd(32, '0').uuidFormat()),
            objectId = packageFile.manifest.objectId,
            senderId = packageFile.manifest.senderId,
            recipientId = packageFile.manifest.recipientId,
            completedAtEpochMillis = now,
            verifiedPlainSha256 = manifest.plainSha256
        )
        return ObjectTransferResult(receipt.transferId, packageFile.manifest.objectId, attachment, receipt)
    }

    fun decryptManifest(manifest: EncryptedObjectManifest, objectKey: ByteArray, now: Long = System.currentTimeMillis()): ObjectManifestPlaintext {
        if (manifest.expiresAtEpochMillis <= now) throw IllegalArgumentException("Object transfer expired.")
        if (sha256(manifest.nonce + manifest.ciphertext + manifest.tag).hex() != manifest.objectId) throw IllegalArgumentException("Manifest commitment mismatch.")
        val plaintext = aesGcmDecrypt(manifest.ciphertext, manifest.tag, objectKey, manifest.nonce, "LocalWave.Manifest.v1".encodeToByteArray())
        val decoded = ObjectTransferJson.decodeManifestPlaintext(plaintext)
        if (!MessageDigest.isEqual(decoded.merkleRoot, merkleRoot(decoded.dataPieceHashes))) throw IllegalArgumentException("Merkle root mismatch.")
        return decoded
    }

    private suspend fun unwrapObjectKey(manifest: EncryptedObjectManifest, peer: PeerProfile, channel: ChannelCode): ByteArray {
        val wrappingKey = sessionCrypto.objectWrappingKey(peer, channel)
        return aesGcmDecrypt(
            manifest.wrappedObjectKeyCiphertext,
            manifest.wrappedObjectKeyTag,
            wrappingKey,
            manifest.wrappedObjectKeyNonce,
            "LocalWave.ObjectKey.v1|${manifest.objectId}|${manifest.recipientId.value}".encodeToByteArray()
        )
    }

    private fun encryptPiece(plaintext: ByteArray, index: Int, kind: ObjectPieceKind, objectId: String, objectKey: ByteArray): ObjectPiece {
        val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val sealed = aesGcmEncrypt(plaintext, objectKey, nonce, "LocalWave.ObjectPiece.v1|$index|${kind.name.lowercase()}".encodeToByteArray())
        return ObjectPiece(
            objectId = objectId,
            pieceIndex = index,
            pieceKind = kind,
            nonce = nonce,
            ciphertext = sealed.first,
            tag = sealed.second,
            pieceHash = sha256(nonce + sealed.first + sealed.second)
        )
    }

    private fun decryptPiece(piece: ObjectPiece, objectKey: ByteArray): ByteArray {
        if (!MessageDigest.isEqual(sha256(piece.nonce + piece.ciphertext + piece.tag), piece.pieceHash)) throw IllegalArgumentException("Piece hash mismatch.")
        return aesGcmDecrypt(piece.ciphertext, piece.tag, objectKey, piece.nonce, "LocalWave.ObjectPiece.v1|${piece.pieceIndex}|${piece.pieceKind.name.lowercase()}".encodeToByteArray())
    }

    private fun recoverMissingChunks(chunks: MutableMap<Int, ByteArray>, manifest: ObjectManifestPlaintext, recoveryPieces: List<ObjectPiece>, objectKey: ByteArray) {
        recoveryPieces.forEach { recovery ->
            val stripe = recovery.pieceIndex - manifest.pieceCount
            val start = stripe * RECOVERY_STRIPE_SIZE
            val end = minOf(start + RECOVERY_STRIPE_SIZE, manifest.pieceCount)
            val missing = (start until end).filter { chunks[it] == null }
            if (missing.size == 1) {
                var recovered = decryptPiece(recovery, objectKey)
                for (index in start until end) if (index != missing.first()) recovered = xor(recovered, chunks[index] ?: return@forEach, manifest.pieceSize)
                val expectedSize = if (missing.first() == manifest.pieceCount - 1) manifest.plainSize - (missing.first() * manifest.pieceSize) else manifest.pieceSize
                val trimmed = recovered.copyOf(expectedSize)
                if (MessageDigest.isEqual(sha256(trimmed), manifest.plainPieceHashes[missing.first()])) chunks[missing.first()] = trimmed
            }
        }
    }

    private fun recoveryChunks(chunks: List<ByteArray>, stripeSize: Int): List<ByteArray> {
        if (chunks.sumOf { it.size } < 1024 * 1024) return emptyList()
        return chunks.chunked(stripeSize).map { stripe ->
            val size = stripe.maxOf { it.size }
            stripe.fold(ByteArray(size)) { acc, chunk -> xor(acc, chunk, size) }
        }
    }

    private fun objectType(mimeType: String): String = when {
        mimeType.startsWith("image/") -> "image"
        mimeType.startsWith("video/") -> "video"
        mimeType.startsWith("audio/") -> "voice"
        mimeType == "application/pdf" -> "document"
        else -> "file"
    }
}

class PieceScheduler {
    fun orderedMissingPieces(pieceCount: Int, received: Set<Int>, inventories: List<PieceInventory>): List<Int> {
        val rarity = inventories.flatMap { it.dataPieceIndexes }.groupingBy { it }.eachCount()
        return (0 until pieceCount)
            .filterNot(received::contains)
            .sortedWith(compareBy<Int> { rarity[it] ?: Int.MAX_VALUE }.thenBy { it })
    }
}

class ObjectRelayCache(private val maxBytes: Int) {
    private val chunks = linkedMapOf<String, RelayChunk>()

    @Synchronized
    fun insert(chunk: RelayChunk, now: Long = System.currentTimeMillis()) {
        prune(now)
        require(chunk.expiresAtEpochMillis > now) { "Relay chunk is expired." }
        chunks["${chunk.id}|${chunk.destinationPeerId.value}"] = chunk
        while (chunks.values.sumOf { it.payload.size } > maxOf(1, maxBytes)) {
            val oldest = chunks.values.minBy { it.expiresAtEpochMillis }
            chunks.remove("${oldest.id}|${oldest.destinationPeerId.value}")
        }
    }

    @Synchronized
    fun chunksFor(destinationPeerId: PeerId, now: Long = System.currentTimeMillis()): List<RelayChunk> {
        prune(now)
        return chunks.values.filter { it.destinationPeerId == destinationPeerId }.sortedBy { it.expiresAtEpochMillis }
    }

    private fun prune(now: Long) {
        chunks.entries.removeAll { it.value.expiresAtEpochMillis <= now }
    }
}

class LocalWaveObjectFileStore(private val root: File) {
    @Synchronized
    fun store(packageFile: LocalWaveObjectPackage) {
        store(packageFile.manifest)
        storePieces(packageFile.pieces)
    }

    @Synchronized
    fun store(manifest: EncryptedObjectManifest) {
        val directory = objectDirectory(manifest.objectId)
        File(directory, "pieces").mkdirs()
        File(directory, "manifest.json").writeBytes(ObjectTransferJson.encodeManifest(manifest).toString().encodeToByteArray())
    }

    @Synchronized
    fun storePieces(pieces: List<ObjectPiece>) {
        pieces.forEach { piece ->
            val piecesDirectory = File(objectDirectory(piece.objectId), "pieces").also { it.mkdirs() }
            val suffix = if (piece.pieceKind == ObjectPieceKind.DATA) "data" else "recovery"
            File(piecesDirectory, "${piece.pieceIndex}-$suffix.json").writeBytes(ObjectTransferJson.encodePiece(piece).toString().encodeToByteArray())
        }
    }

    @Synchronized
    fun packageFor(objectId: String): LocalWaveObjectPackage? {
        val directory = objectDirectory(objectId)
        val manifestFile = File(directory, "manifest.json")
        if (!manifestFile.exists()) return null
        val manifest = ObjectTransferJson.decodeManifest(JSONObject(manifestFile.readText()))
        val pieces = File(directory, "pieces")
            .listFiles { file -> file.isFile && file.extension == "json" }
            ?.map { ObjectTransferJson.decodePiece(JSONObject(it.readText())) }
            ?.sortedBy { it.pieceIndex }
            .orEmpty()
        return LocalWaveObjectPackage(manifest, pieces)
    }

    @Synchronized
    fun pruneExpired(now: Long = System.currentTimeMillis()) {
        root.listFiles()?.forEach { objectDirectory ->
            val manifestFile = File(objectDirectory, "manifest.json")
            if (manifestFile.exists()) {
                val manifest = ObjectTransferJson.decodeManifest(JSONObject(manifestFile.readText()))
                if (manifest.expiresAtEpochMillis <= now) objectDirectory.deleteRecursively()
            }
        }
    }

    private fun objectDirectory(objectId: String): File = File(root, objectId)
}

private fun ByteArray.chunked(size: Int): List<ByteArray> =
    if (isEmpty()) listOf(ByteArray(0)) else indices.step(size).map { copyOfRange(it, minOf(it + size, this.size)) }

private fun xor(left: ByteArray, right: ByteArray, size: Int): ByteArray =
    ByteArray(size) { index -> ((left.getOrNull(index) ?: 0).toInt() xor (right.getOrNull(index) ?: 0).toInt()).toByte() }

    private fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)

    private fun merkleRoot(hashes: List<ByteArray>): ByteArray {
        if (hashes.isEmpty()) return sha256(ByteArray(0))
        var level = hashes
        while (level.size > 1) {
            level = level.chunked(2).map { pair ->
                val right = pair.getOrElse(1) { pair[0] }
                sha256(pair[0] + right)
            }
        }
        return level[0]
    }

private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }

private fun String.uuidFormat(): String = "${substring(0, 8)}-${substring(8, 12)}-${substring(12, 16)}-${substring(16, 20)}-${substring(20, 32)}"
