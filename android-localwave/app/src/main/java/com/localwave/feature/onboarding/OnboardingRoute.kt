package com.localwave.feature.onboarding

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.localwave.app.AppEnvironment
import com.localwave.app.AppStateHolder
import com.localwave.core.model.ChannelCode
import kotlinx.coroutines.launch

@Composable
fun OnboardingRoute(
    environment: AppEnvironment,
    appStateHolder: AppStateHolder,
    activity: Activity,
    onFinished: () -> Unit
) {
    val viewModel = remember { OnboardingViewModel(environment.engine) }
    val scope = rememberCoroutineScope()
    OnboardingScreen(
        viewModel = viewModel,
        onRequestBluetooth = { environment.permissionController.requestBluetooth(activity) },
        onRequestNotifications = { environment.permissionController.requestNotifications(activity) },
        onFinish = {
            scope.launch {
                val state = viewModel.state.value
                val channel = ChannelCode(state.channelText)
                appStateHolder.completeOnboarding(state.displayName, channel)
                viewModel.startEngine()
                onFinished()
            }
        }
    )
}
