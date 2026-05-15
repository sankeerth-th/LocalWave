package com.localwave.mock

import com.localwave.core.model.ChannelCode
import com.localwave.core.model.MessageStatus
import com.localwave.core.model.PeerId
import com.localwave.core.model.TransportPermissionState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MockLocalWaveEngineTest {
    @Test
    fun mockEngineSimulatesPeersMessagesAndChannelSwitching() = runTest {
        val engine = MockLocalWaveEngine()

        engine.start(ChannelCode("DOCK-A-17"), "Android")
        assertEquals(TransportPermissionState.ALLOWED, engine.observeTransportState().first().permission)
        assertTrue(engine.observePeers().first().isNotEmpty())

        val peerId = engine.observePeers().first().first().id
        val messageId = engine.sendMessage("hello", peerId)
        assertEquals(MessageStatus.DELIVERED, engine.observeMessages(peerId).first().first { it.id == messageId }.status)

        engine.switchChannel(ChannelCode("DOCK-B-17"))
        assertTrue(engine.observePeers().first().all { it.id != PeerId("mock-alex") })
    }

    @Test
    fun observeMessagesEmitsMessagesSentAfterSubscription() = runTest {
        val engine = MockLocalWaveEngine()
        engine.start(ChannelCode("DOCK-A-17"), "Android")
        val peerId = engine.observePeers().first().first().id
        val observed = async { engine.observeMessages(peerId).first { it.isNotEmpty() } }

        engine.sendMessage("hello after subscription", peerId)

        assertEquals(1, observed.await().size)
    }
}
