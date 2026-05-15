package com.localwave.core.bluetooth

import com.localwave.core.model.TransportPacketKind
import java.util.UUID
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BlePacketFramerTest {
    @Test
    fun encodesIosPacketLayout() {
        val framer = BlePacketFramer()
        val packet = TransportPacket(
            id = UUID.fromString("00112233-4455-6677-8899-aabbccddeeff"),
            conversationId = UUID.fromString("10213243-5465-7687-98a9-bacbdcedfe0f"),
            kind = TransportPacketKind.MESSAGE,
            chunkIndex = 0,
            chunkCount = 1,
            bodyLength = 5,
            checksum = 0x3610a686u,
            body = "hello".encodeToByteArray()
        )

        assertEquals(
            "0100112233445566778899aabbccddeeff102132435465768798a9bacbdcedfe0f0200000001000000053610a68668656c6c6f",
            framer.encode(packet).toHex()
        )
    }

    @Test
    fun chunksAndReassemblesOutOfOrderPayload() {
        val framer = BlePacketFramer(maxFrameBytes = 96, maxPayloadBytes = 4096)
        val reassembler = PacketReassembler()
        val body = ByteArray(512) { (it % 251).toByte() }
        val chunks = framer.frame(
            body = body,
            kind = TransportPacketKind.MESSAGE,
            packetId = UUID.fromString("00112233-4455-6677-8899-aabbccddeeff"),
            conversationId = UUID.fromString("10213243-5465-7687-98a9-bacbdcedfe0f")
        )

        assertTrue(chunks.size > 1)
        chunks.asReversed().forEachIndexed { index, packet ->
            val result = reassembler.receive(packet)
            if (index == chunks.lastIndex) {
                assertArrayEquals(body, result?.body)
                assertEquals(TransportPacketKind.MESSAGE, result?.kind)
            } else {
                assertNull(result)
            }
        }
    }

    @Test
    fun rejectsOversizedPayload() {
        val framer = BlePacketFramer(maxFrameBytes = 96, maxPayloadBytes = 128)
        assertThrows(IllegalArgumentException::class.java) {
            framer.frame(ByteArray(129), TransportPacketKind.MESSAGE)
        }
    }
}
