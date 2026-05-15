package com.localwave.feature.onboarding

import com.localwave.core.model.ChannelCode
import com.localwave.core.protocol.LocalWaveEngine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

enum class OnboardingStep { WELCOME, DISPLAY_NAME, FREQUENCY, BLUETOOTH, NOTIFICATIONS }

data class ValidationResult(val isValid: Boolean, val error: String? = null) {
    val reason: String? get() = error
}

object DisplayNameInput {
    fun validate(value: String): ValidationResult {
        val trimmed = value.trim()
        return when {
            trimmed.isEmpty() -> ValidationResult(false, "Display name is required.")
            trimmed.length > 32 -> ValidationResult(false, "Display name is limited to 32 characters.")
            else -> ValidationResult(true)
        }
    }
}

data class OnboardingState(
    val step: OnboardingStep = OnboardingStep.WELCOME,
    val displayName: String = "",
    val channelText: String = "",
    val channelCode: ChannelCode? = null,
    val canContinue: Boolean = true,
    val error: String? = null
)

class OnboardingViewModel(
    private val engine: LocalWaveEngine,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main
) {
    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state

    fun updateDisplayName(value: String) {
        val validation = DisplayNameInput.validate(value)
        _state.update {
            if (!validation.isValid) {
                it.copy(displayName = value, canContinue = false, error = validation.error)
            } else {
                it.copy(displayName = value, error = null).validated()
            }
        }
    }

    fun updateChannelCode(value: String) = updateChannel(value)

    fun updateChannel(value: String) {
        val channel = runCatching { ChannelCode.parse(value) }.getOrNull()
        _state.update { it.copy(channelText = value, channelCode = channel, error = null).validated() }
    }

    fun next() {
        val current = _state.value.validated()
        if (!current.canContinue) {
            _state.value = current.copy(error = current.error ?: "Check this step before continuing.")
            return
        }
        _state.value = current.copy(step = current.step.next(), error = null).validated()
    }

    fun back() {
        _state.update { it.copy(step = it.step.previous(), error = null).validated() }
    }

    suspend fun startEngine() {
        val current = _state.value
        engine.start(ChannelCode(current.channelText), current.displayName.trim())
    }

    private fun OnboardingState.validated(): OnboardingState {
        val result = when (step) {
            OnboardingStep.DISPLAY_NAME -> DisplayNameInput.validate(displayName)
            OnboardingStep.FREQUENCY -> runCatching { ChannelCode(channelText) }
                .fold(onSuccess = { ValidationResult(true) }, onFailure = { ValidationResult(false, it.message) })
            else -> ValidationResult(true)
        }
        return copy(canContinue = result.isValid, error = if (result.isValid) null else result.error)
    }

    private fun OnboardingStep.next(): OnboardingStep = enumValues<OnboardingStep>().getOrElse(ordinal + 1) { this }
    private fun OnboardingStep.previous(): OnboardingStep = enumValues<OnboardingStep>().getOrElse(ordinal - 1) { this }
}
