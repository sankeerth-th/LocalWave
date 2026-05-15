package com.localwave.core.protocol

import com.localwave.core.model.ChannelCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class FrequencyCodeNormalizerTest {
    @Test
    fun normalizesLikeIos() {
        assertEquals("DOCK-A-17", ChannelCode(" dock-a-17 ").normalized)
        assertEquals("NIGHT SHIFT", ChannelCode("night   shift").normalized)
        assertEquals("462.625", ChannelCode(" 462.625 ").normalized)
    }

    @Test
    fun rejectsTooShortAndTooLongCodes() {
        assertThrows(IllegalArgumentException::class.java) { ChannelCode("ab") }
        assertThrows(IllegalArgumentException::class.java) { ChannelCode("A".repeat(33)) }
    }

    @Test
    fun channelCodeEqualityUsesNormalizedValue() {
        assertEquals(ChannelCode(" dock-a-17 "), ChannelCode("DOCK-A-17"))
        assertNotEquals(ChannelCode("DOCK-A-17"), ChannelCode("DOCK-B-17"))
    }
}
