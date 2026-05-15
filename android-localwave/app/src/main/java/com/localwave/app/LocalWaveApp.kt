package com.localwave.app

import android.app.Activity
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.localwave.core.model.ChannelCode

@Composable
fun LocalWaveApp(environment: AppEnvironment, appStateHolder: AppStateHolder, activity: Activity) {
    val state by appStateHolder.state.collectAsStateWithLifecycle(initialValue = null)
    Surface(modifier = Modifier.fillMaxSize()) {
        val current = state
        if (current == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            LaunchedEffect(current.onboardingComplete, current.channelText, current.displayName) {
                if (current.onboardingComplete && current.channelText.isNotBlank()) {
                    environment.engine.start(ChannelCode(current.channelText), current.displayName)
                }
            }
            LocalWaveNavGraph(environment, appStateHolder, activity, current)
        }
    }
}
