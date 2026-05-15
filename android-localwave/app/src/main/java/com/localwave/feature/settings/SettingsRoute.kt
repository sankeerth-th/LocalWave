package com.localwave.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import com.localwave.app.AppEnvironment
import com.localwave.app.AppStateHolder
import com.localwave.app.PersistedAppState

@Composable
fun SettingsRoute(environment: AppEnvironment, appStateHolder: AppStateHolder, persisted: PersistedAppState, navController: NavHostController) {
    val viewModel = remember { SettingsViewModel(environment, appStateHolder, persisted) }
    LaunchedEffect(Unit) { viewModel.start() }
    SettingsScreen(
        viewModel = viewModel,
        engineMode = environment.mode.name.lowercase().replace("_", " "),
        openPrivacy = { navController.navigate("settings/privacy") },
        openIdentity = { navController.navigate("settings/identity") },
        openDiagnostics = { navController.navigate("settings/diagnostics") },
        openPermissions = { navController.navigate("settings/permissions") }
    )
}
