package com.localwave.core.protocol

import com.localwave.core.model.ChannelCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class UuidDerivationTest {
    @Test
    fun dockA17MatchesIosVectors() {
        val identity = UuidDerivation.derive(ChannelCode("DOCK-A-17"))

        assertEquals("f1ef928c-55d0-5d6b-bacb-9ac446ff8e53", identity.channelId.toString())
        assertEquals("b7f9b4ff-4cb7-534a-a4bd-b6be5c60f07d", identity.gatt.serviceUuid.toString())
        assertEquals("e51f10cf-b289-5d7a-bcb2-442a0eb64e70", identity.gatt.packetCharacteristicUuid.toString())
        assertEquals("888d6630-a75a-53cf-bb58-bc42d9f8d876", identity.gatt.presenceCharacteristicUuid.toString())
        assertEquals("0617799b-472e-5ac4-ad84-718391d515bd", identity.gatt.wakeCharacteristicUuid.toString())
        assertEquals("8398840ba684a568", identity.discoveryTag.toHex())
        assertEquals("7bab0f51b0c19a22c37e0bdd62da49187b2920ae89a39d482046fa7e373f9cc4", identity.hkdfSalt.toHex())
    }

    @Test
    fun equivalentCodesDeriveSameUuidsAndDifferentCodesDoNot() {
        val first = UuidDerivation.derive(ChannelCode(" dock-a-17 "))
        val second = UuidDerivation.derive(ChannelCode("DOCK-A-17"))
        val third = UuidDerivation.derive(ChannelCode("DOCK-B-17"))

        assertEquals(first, second)
        assertNotEquals(first.gatt.serviceUuid, third.gatt.serviceUuid)
    }
}
