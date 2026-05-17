package com.localwave.app

import android.app.Activity
import android.net.Uri
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.localwave.core.model.ChannelCode
import com.localwave.feature.chat.LocalWaveShareBridge
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

@Composable
fun LocalWaveApp(
    environment: AppEnvironment,
    appStateHolder: AppStateHolder,
    activity: Activity,
    packageImportUris: Flow<Uri> = emptyFlow()
) {
    val state by appStateHolder.state.collectAsStateWithLifecycle(initialValue = null)
    val context = LocalContext.current
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
            LaunchedEffect(current.onboardingComplete, current.channelText) {
                if (current.onboardingComplete && current.channelText.isNotBlank()) {
                    packageImportUris.collect { uri ->
                        runCatching {
                            LocalWaveShareBridge.importSharePackage(context, environment.engine, uri)
                        }
                    }
                }
            }
            LocalWaveNavGraph(environment, appStateHolder, activity, current)
        }
    }
}
