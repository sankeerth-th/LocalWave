package com.localwave.core.protocol

import com.localwave.core.model.ChannelCode
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID

data class GattProfile(
    val serviceUuid: UUID,
    val packetCharacteristicUuid: UUID,
    val presenceCharacteristicUuid: UUID,
    val wakeCharacteristicUuid: UUID
)

data class ChannelIdentity(
    val channelId: UUID,
    val discoveryTag: ByteArray,
    val hkdfSalt: ByteArray,
    val gatt: GattProfile
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ChannelIdentity) return false
        return channelId == other.channelId &&
            discoveryTag.contentEquals(other.discoveryTag) &&
            hkdfSalt.contentEquals(other.hkdfSalt) &&
            gatt == other.gatt
    }

    override fun hashCode(): Int {
        var result = channelId.hashCode()
        result = 31 * result + discoveryTag.contentHashCode()
        result = 31 * result + hkdfSalt.contentHashCode()
        result = 31 * result + gatt.hashCode()
        return result
    }
}

object FrequencyCodeNormalizer {
    fun normalize(value: String): String = ChannelCode.normalize(value)
}

object UuidDerivation {
    fun derive(channel: ChannelCode): ChannelIdentity {
        val normalized = channel.normalized
        return ChannelIdentity(
            channelId = uuid("channel-id", normalized),
            discoveryTag = sha256(data("discovery-tag", normalized)).copyOfRange(0, 8),
            hkdfSalt = sha256(data("hkdf-salt", normalized)),
            gatt = GattProfile(
                serviceUuid = uuid("gatt-service", normalized),
                packetCharacteristicUuid = uuid("gatt-packet", normalized),
                presenceCharacteristicUuid = uuid("gatt-presence", normalized),
                wakeCharacteristicUuid = uuid("gatt-wake", normalized)
            )
        )
    }

    private fun uuid(label: String, normalized: String): UUID {
        val bytes = sha256(data(label, normalized)).copyOfRange(0, 16)
        bytes[6] = ((bytes[6].toInt() and 0x0f) or 0x50).toByte()
        bytes[8] = ((bytes[8].toInt() and 0x3f) or 0x80).toByte()
        val msb = bytes.take(8).fold(0L) { acc, byte -> (acc shl 8) or (byte.toLong() and 0xffL) }
        val lsb = bytes.drop(8).take(8).fold(0L) { acc, byte -> (acc shl 8) or (byte.toLong() and 0xffL) }
        return UUID(msb, lsb)
    }

    private fun data(label: String, normalized: String): ByteArray =
        "${ProtocolConstants.CHANNEL_NAMESPACE}.$label.$normalized".toByteArray(StandardCharsets.UTF_8)

    private fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)
}
