package com.localwave.app

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.localwave.core.model.ChannelCode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.localWaveDataStore by preferencesDataStore("localwave_app_state")

data class PersistedAppState(
    val onboardingComplete: Boolean = false,
    val displayName: String = "",
    val channelText: String = "",
    val bluetoothEducationShown: Boolean = false,
    val notificationEducationShown: Boolean = false
)

class AppStateHolder(private val context: Context) {
    val state: Flow<PersistedAppState> = context.localWaveDataStore.data.map { prefs ->
        PersistedAppState(
            onboardingComplete = prefs[ONBOARDING_COMPLETE] ?: false,
            displayName = prefs[DISPLAY_NAME].orEmpty(),
            channelText = prefs[CHANNEL_TEXT].orEmpty(),
            bluetoothEducationShown = prefs[BLUETOOTH_EDUCATION] ?: false,
            notificationEducationShown = prefs[NOTIFICATION_EDUCATION] ?: false
        )
    }

    suspend fun completeOnboarding(displayName: String, channel: ChannelCode) {
        context.localWaveDataStore.edit { prefs ->
            prefs[DISPLAY_NAME] = displayName.trim()
            prefs[CHANNEL_TEXT] = channel.normalized
            prefs[ONBOARDING_COMPLETE] = true
            prefs[BLUETOOTH_EDUCATION] = true
            prefs[NOTIFICATION_EDUCATION] = true
        }
    }

    suspend fun updateDisplayName(displayName: String) {
        context.localWaveDataStore.edit { it[DISPLAY_NAME] = displayName.trim() }
    }

    suspend fun updateChannel(channel: ChannelCode) {
        context.localWaveDataStore.edit { it[CHANNEL_TEXT] = channel.normalized }
    }

    companion object {
        private val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        private val DISPLAY_NAME = stringPreferencesKey("display_name")
        private val CHANNEL_TEXT = stringPreferencesKey("channel_text")
        private val BLUETOOTH_EDUCATION = booleanPreferencesKey("bluetooth_education")
        private val NOTIFICATION_EDUCATION = booleanPreferencesKey("notification_education")
    }
}
