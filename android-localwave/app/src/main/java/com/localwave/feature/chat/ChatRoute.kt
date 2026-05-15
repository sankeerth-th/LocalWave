package com.localwave.feature.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.localwave.app.AppEnvironment
import com.localwave.core.model.PeerId

@Composable
fun ChatRoute(environment: AppEnvironment, peerId: String, onBack: () -> Unit) {
    val viewModel = remember(peerId) { ChatViewModel(environment.engine, PeerId(peerId)) }
    LaunchedEffect(peerId) { viewModel.start() }
    ChatScreen(viewModel, environment.mode.name, onBack)
}
