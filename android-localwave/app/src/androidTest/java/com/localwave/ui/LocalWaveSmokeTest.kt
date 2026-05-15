package com.localwave.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.localwave.app.MainActivity
import org.junit.Rule
import org.junit.Test

class LocalWaveSmokeTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun appLaunchesIntoLocalWaveShell() {
        compose.onNodeWithText("LocalWave").assertIsDisplayed()
    }
}
