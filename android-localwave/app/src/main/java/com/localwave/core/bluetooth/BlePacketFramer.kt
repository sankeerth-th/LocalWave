package com.localwave.core.bluetooth

import com.localwave.core.model.ConversationId
import com.localwave.core.model.PacketId
import com.localwave.core.model.TransportPacketKind
import com.localwave.core.protocol.ProtocolConstants
import java.nio.ByteBuffer
import java.util.UUID
import java.util.zip.CRC32

data class TransportPacket(
    val id: PacketId,
    val conversationId: ConversationId,
    val kind: TransportPacketKind,
    val chunkIndex: Int,
    val chunkCount: Int,
    val bodyLength: Long,
    val checksum: UInt,
    val body: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TransportPacket) return false
        return id == other.id &&
            conversationId == other.conversationId &&
            kind == other.kind &&
            chunkIndex == other.chunkIndex &&
            chunkCount == other.chunkCount &&
            bodyLength == other.bodyLength &&
            checksum == other.checksum &&
            body.contentEquals(other.body)
    }

    override fun hashCode(): Int = id.hashCode()
}

data class ReassembledPacket(
    val packetId: PacketId,
    val conversationId: ConversationId,
    val kind: TransportPacketKind,
    val body: ByteArray
)

class BlePacketFramer(
    private val maxFrameBytes: Int = ProtocolConstants.DEFAULT_MAX_FRAME_BYTES,
    private val maxPayloadBytes: Int = ProtocolConstants.DEFAULT_MAX_PAYLOAD_BYTES
) {
    fun frame(
        body: ByteArray,
        kind: TransportPacketKind,
        packetId: UUID = UUID.randomUUID(),
        conversationId: UUID = UUID.randomUUID()
    ): List<TransportPacket> {
        require(body.size <= maxPayloadBytes) { "Messages are limited to $maxPayloadBytes bytes." }
        val maxChunkBytes = maxFrameBytes - ProtocolConstants.PACKET_HEADER_BYTES
        require(maxChunkBytes > 0) { "Max frame bytes must exceed header bytes." }
        val chunkCount = maxOf(1, (body.size + maxChunkBytes - 1) / maxChunkBytes)
        require(chunkCount <= UShort.MAX_VALUE.toInt()) { "Too many BLE chunks." }
        val checksum = crc32(body)
        return (0 until chunkCount).map { index ->
            val start = index * maxChunkBytes
            val end = minOf(start + maxChunkBytes, body.size)
            TransportPacket(
                id = packetId,
                conversationId = conversationId,
                kind = kind,
                chunkIndex = index,
                chunkCount = chunkCount,
                bodyLength = body.size.toLong(),
                checksum = checksum,
                body = if (start < end) body.copyOfRange(start, end) else ByteArray(0)
            )
        }
    }

    fun encode(packet: TransportPacket): ByteArray {
        val buffer = ByteBuffer.allocate(ProtocolConstants.PACKET_HEADER_BYTES + packet.body.size)
        buffer.put(ProtocolConstants.PACKET_VERSION.toByte())
        buffer.putUuid(packet.id)
        buffer.putUuid(packet.conversationId)
        buffer.put(packet.kind.wireValue.toByte())
        buffer.putShort(packet.chunkIndex.toShort())
        buffer.putShort(packet.chunkCount.toShort())
        buffer.putInt(packet.bodyLength.toInt())
        buffer.putInt(packet.checksum.toInt())
        buffer.put(packet.body)
        return buffer.array()
    }

    fun decode(data: ByteArray): TransportPacket {
        require(data.size >= ProtocolConstants.PACKET_HEADER_BYTES) { "Malformed packet." }
        val buffer = ByteBuffer.wrap(data)
        val version = buffer.get().toUByte()
        require(version == ProtocolConstants.PACKET_VERSION) { "Unsupported packet version." }
        val packetId = buffer.readUuid()
        val conversationId = buffer.readUuid()
        val kind = TransportPacketKind.fromWire(buffer.get().toUByte())
        val chunkIndex = buffer.short.toInt() and 0xffff
        val chunkCount = buffer.short.toInt() and 0xffff
        val bodyLength = buffer.int.toLong() and 0xffff_ffffL
        val checksum = buffer.int.toUInt()
        val body = ByteArray(buffer.remaining())
        buffer.get(body)
        require(chunkCount > 0 && chunkIndex < chunkCount) { "Malformed chunk indexes." }
        return TransportPacket(packetId, conversationId, kind, chunkIndex, chunkCount, bodyLength, checksum, body)
    }

    companion object {
        fun crc32(body: ByteArray): UInt {
            val crc = CRC32()
            crc.update(body)
            return crc.value.toUInt()
        }
    }
}

class PacketReassembler {
    private data class Accumulator(
        val conversationId: UUID,
        val kind: TransportPacketKind,
        val chunkCount: Int,
        val bodyLength: Long,
        val checksum: UInt,
        val chunks: MutableMap<Int, ByteArray> = mutableMapOf()
    )

    private val accumulators = mutableMapOf<UUID, Accumulator>()

    fun receive(packet: TransportPacket): ReassembledPacket? {
        require(packet.chunkCount > 0 && packet.chunkIndex < packet.chunkCount) { "Malformed packet." }
        val accumulator = accumulators.getOrPut(packet.id) {
            Accumulator(packet.conversationId, packet.kind, packet.chunkCount, packet.bodyLength, packet.checksum)
        }
        require(accumulator.conversationId == packet.conversationId)
        require(accumulator.kind == packet.kind)
        require(accumulator.chunkCount == packet.chunkCount)
        require(accumulator.bodyLength == packet.bodyLength)
        require(accumulator.checksum == packet.checksum)
        accumulator.chunks[packet.chunkIndex] = packet.body
        if (accumulator.chunks.size != accumulator.chunkCount) return null
        val body = ByteArray(accumulator.bodyLength.toInt())
        var offset = 0
        for (index in 0 until accumulator.chunkCount) {
            val chunk = accumulator.chunks[index] ?: return null
            chunk.copyInto(body, offset)
            offset += chunk.size
        }
        accumulators.remove(packet.id)
        require(offset == body.size && BlePacketFramer.crc32(body) == accumulator.checksum) { "Malformed packet." }
        return ReassembledPacket(packet.id, accumulator.conversationId, accumulator.kind, body)
    }
}

private fun ByteBuffer.putUuid(uuid: UUID) {
    putLong(uuid.mostSignificantBits)
    putLong(uuid.leastSignificantBits)
}

private fun ByteBuffer.readUuid(): UUID = UUID(long, long)

fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }
