package com.localwave.feature.people

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.localwave.app.AppEnvironment
import com.localwave.app.PersistedAppState
import com.localwave.core.model.PeerId

@Composable
fun PeopleRoute(environment: AppEnvironment, persistedState: PersistedAppState, openChat: (PeerId) -> Unit, openSettings: () -> Unit) {
    val viewModel = remember { PeopleViewModel(environment.engine) }
    LaunchedEffect(Unit) { viewModel.start() }
    PeopleScreen(viewModel, persistedState.channelText.ifBlank { "DOCK-A" }, openChat, openSettings)
}
