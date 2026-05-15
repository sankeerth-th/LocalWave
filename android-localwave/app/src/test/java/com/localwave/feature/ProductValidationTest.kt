package com.localwave.feature

import com.localwave.core.model.ChannelCode
import com.localwave.feature.onboarding.DisplayNameInput
import com.localwave.feature.onboarding.OnboardingViewModel
import com.localwave.mock.MockLocalWaveEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProductValidationTest {
    @Test
    fun channelCodeNormalizesWhitespaceAndCase() {
        assertEquals("DOCK A", ChannelCode.parse(" dock   a ").normalized)
    }

    @Test(expected = IllegalArgumentException::class)
    fun channelCodeRejectsShortCodes() {
        ChannelCode.parse("ab")
    }

    @Test
    fun displayNameRejectsEmptyAndLongValues() {
        assertFalse(DisplayNameInput.validate("   ").isValid)
        assertFalse(DisplayNameInput.validate("a".repeat(33)).isValid)
        assertTrue(DisplayNameInput.validate("Maya").isValid)
    }

    @Test
    fun onboardingCannotContinueWithInvalidName() {
        val viewModel = OnboardingViewModel(MockLocalWaveEngine(), StandardTestDispatcher())
        viewModel.updateDisplayName(" ")
        assertFalse(viewModel.state.value.canContinue)
    }

    @Test
    fun mockEngineSendMessageAddsPendingOutgoingMessage() = runTest {
        val engine = MockLocalWaveEngine(testScheduler = testScheduler)
        engine.start(ChannelCode.parse("DOCK-A"), "Maya")
        val peer = engine.currentPeers().first()

        engine.sendMessage("Need a hand at dock seven", peer.id)

        val message = engine.currentMessages(peer.id).last()
        assertEquals("Need a hand at dock seven", message.text)
        assertEquals("pending", message.status.name.lowercase())
    }
}
