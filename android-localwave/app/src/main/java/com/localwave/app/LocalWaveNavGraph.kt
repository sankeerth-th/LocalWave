package com.localwave.app

import android.app.Activity
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.localwave.feature.chat.ChatRoute
import com.localwave.feature.onboarding.OnboardingRoute
import com.localwave.feature.people.PeopleRoute
import com.localwave.feature.settings.DiagnosticsScreen
import com.localwave.feature.settings.IdentityFingerprintScreen
import com.localwave.feature.settings.PermissionStatusScreen
import com.localwave.feature.settings.PrivacyExplanationScreen
import com.localwave.feature.settings.SettingsRoute

@Composable
fun LocalWaveNavGraph(
    environment: AppEnvironment,
    appStateHolder: AppStateHolder,
    activity: Activity,
    persistedState: PersistedAppState
) {
    val navController = rememberNavController()
    val start = if (persistedState.onboardingComplete) "people" else "onboarding/welcome"
    Scaffold(
        bottomBar = {
            if (navController.currentBackStackEntryAsState().value?.destination?.route in setOf("people", "settings")) {
                LocalWaveBottomBar(navController)
            }
        }
    ) { padding ->
        NavHost(navController = navController, startDestination = start, modifier = Modifier.padding(padding)) {
            composable("onboarding/welcome") {
                OnboardingRoute(environment, appStateHolder, activity) {
                    navController.navigate("people") { popUpTo("onboarding/welcome") { inclusive = true } }
                }
            }
            composable("people") {
                PeopleRoute(environment, persistedState, openChat = { navController.navigate("chat/${it.value}") }, openSettings = { navController.navigate("settings") })
            }
            composable("chat/{peerId}") { entry ->
                ChatRoute(environment, entry.arguments?.getString("peerId").orEmpty(), onBack = { navController.popBackStack() })
            }
            composable("settings") { SettingsRoute(environment, appStateHolder, persistedState, navController) }
            composable("settings/privacy") { PrivacyExplanationScreen(onBack = { navController.popBackStack() }) }
            composable("settings/identity") { IdentityFingerprintScreen(environment, onBack = { navController.popBackStack() }) }
            composable("settings/diagnostics") { DiagnosticsScreen(environment, onBack = { navController.popBackStack() }) }
            composable("settings/permissions") { PermissionStatusScreen(environment, onBack = { navController.popBackStack() }) }
        }
    }
}

@Composable
private fun LocalWaveBottomBar(navController: NavHostController) {
    val route = navController.currentBackStackEntryAsState().value?.destination?.route
    NavigationBar {
        NavigationBarItem(
            selected = route == "people",
            onClick = { navController.navigate("people") { launchSingleTop = true } },
            icon = { Icon(com.localwave.design.LWIcons.People, contentDescription = null) },
            label = { Text("People") },
            colors = NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent)
        )
        NavigationBarItem(
            selected = route == "settings",
            onClick = { navController.navigate("settings") { launchSingleTop = true } },
            icon = { Icon(com.localwave.design.LWIcons.Settings, contentDescription = null) },
            label = { Text("Settings") },
            colors = NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent)
        )
    }
}
