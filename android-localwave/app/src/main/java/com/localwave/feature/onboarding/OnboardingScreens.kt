package com.localwave.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.localwave.design.LWSpacing
import com.localwave.design.LWTheme
import com.localwave.design.components.GlassCard
import com.localwave.design.components.PermissionBanner
import com.localwave.mock.MockLocalWaveEngine

@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onRequestBluetooth: () -> Unit,
    onRequestNotifications: () -> Unit,
    onFinish: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        LinearProgressIndicator(
            progress = { (state.step.ordinal + 1) / OnboardingStep.entries.size.toFloat() },
            modifier = Modifier.fillMaxWidth()
        )
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(LWSpacing.screen),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text("LocalWave", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
            Text(stepSubtitle(state.step), color = MaterialTheme.colorScheme.onSurfaceVariant)
            when (state.step) {
                OnboardingStep.WELCOME -> WelcomeScreen()
                OnboardingStep.DISPLAY_NAME -> DisplayNameScreen(state, viewModel::updateDisplayName)
                OnboardingStep.FREQUENCY -> FrequencyCodeScreen(state, viewModel::updateChannel)
                OnboardingStep.BLUETOOTH -> BluetoothEducationScreen(onRequestBluetooth)
                OnboardingStep.NOTIFICATIONS -> NotificationEducationScreen(onRequestNotifications)
            }
            state.error?.let { PermissionBanner(it) }
        }
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            OutlinedButton(onClick = viewModel::back, enabled = state.step != OnboardingStep.WELCOME) { Text("Back") }
            Button(
                onClick = { if (state.step == OnboardingStep.NOTIFICATIONS) onFinish() else viewModel.next() },
                enabled = state.canContinue
            ) { Text(if (state.step == OnboardingStep.NOTIFICATIONS) "Start" else "Continue") }
        }
    }
}

@Composable
fun WelcomeScreen() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Private local chat.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("No internet. No account. Nearby only.")
        GlassCard { Text("LocalWave uses Bluetooth with people nearby on the same Frequency Code. It does not use a backend relay.") }
    }
}

@Composable
fun DisplayNameScreen(state: OnboardingState, onChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(value = state.displayName, onValueChange = onChange, label = { Text("Display name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Text("This name is visible to people on the same Frequency Code nearby.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun FrequencyCodeScreen(state: OnboardingState, onChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = state.channelText,
            onValueChange = onChange,
            label = { Text("Frequency Code") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            modifier = Modifier.fillMaxWidth()
        )
        Text("Examples: DOCK-A, WAREHOUSE-7, 462.625")
        Text("This is a private local Bluetooth channel code. It is not a real radio tuner.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun BluetoothEducationScreen(onRequestBluetooth: () -> Unit) {
    GlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Bluetooth access", fontWeight = FontWeight.Bold)
            Text("Bluetooth lets LocalWave find nearby teammates using the same Frequency Code. No internet or account is used.")
            Button(onClick = onRequestBluetooth) { Text("Check Bluetooth") }
        }
    }
}

@Composable
fun NotificationEducationScreen(onRequestNotifications: () -> Unit) {
    GlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Wake alerts", fontWeight = FontWeight.Bold)
            Text("Notifications let Wake alerts show when someone nearby is trying to reach you.")
            Text("Wake works best when both phones are nearby, Bluetooth is on, and the app has permission.", style = MaterialTheme.typography.bodySmall)
            Button(onClick = onRequestNotifications) { Text("Check Notifications") }
        }
    }
}

private fun stepSubtitle(step: OnboardingStep): String = when (step) {
    OnboardingStep.WELCOME -> "No internet. No signup. Nearby only."
    OnboardingStep.DISPLAY_NAME -> "Choose the name teammates will see nearby."
    OnboardingStep.FREQUENCY -> "Join a private local Bluetooth channel."
    OnboardingStep.BLUETOOTH -> "Enable nearby discovery."
    OnboardingStep.NOTIFICATIONS -> "Allow best-effort Wake alerts."
}

@Preview
@Composable
private fun WelcomePreview() {
    LWTheme { OnboardingScreen(OnboardingViewModel(MockLocalWaveEngine()), {}, {}, {}) }
}
