package com.localwave.core.protocol

object ProtocolConstants {
    const val CHANNEL_NAMESPACE = "LocalWave.Channel.v1"
    const val SESSION_INFO_PREFIX = "LocalWave.Session.v1"
    const val AAD_PREFIX = "LocalWave.AAD.v1"
    const val IDENTITY_PREFIX = "LocalWave.Identity.v1"
    const val PACKET_VERSION: UByte = 1u
    const val PACKET_HEADER_BYTES = 46
    const val DEFAULT_MAX_FRAME_BYTES = 182
    const val DEFAULT_MAX_PAYLOAD_BYTES = 16 * 1024
}

fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }

fun String.hexToBytes(): ByteArray {
    require(length % 2 == 0) { "Hex string length must be even." }
    return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
