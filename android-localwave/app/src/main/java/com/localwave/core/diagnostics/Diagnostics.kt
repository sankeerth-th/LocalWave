package com.localwave.core.diagnostics

import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

data class DiagnosticEvent(
    val id: UUID = UUID.randomUUID(),
    val timestampEpochMillis: Long = System.currentTimeMillis(),
    val category: String,
    val message: String
)

class RedactedLogger {
    private val _events = MutableStateFlow<List<DiagnosticEvent>>(emptyList())
    val events: StateFlow<List<DiagnosticEvent>> = _events

    fun record(category: String, message: String) {
        val redacted = redactValue(message)
        _events.update { current -> (current + DiagnosticEvent(category = category, message = redacted)).takeLast(100) }
    }

    private fun redactValue(value: String): String {
        var redacted = value
        val patterns = listOf(
            Regex("private\\s+key\\s+\\S+", RegexOption.IGNORE_CASE),
            Regex("shared\\s+secret\\s+\\S+", RegexOption.IGNORE_CASE),
            Regex("message=\\S+", RegexOption.IGNORE_CASE),
            Regex("frequency\\s+\\S+", RegexOption.IGNORE_CASE),
            Regex("DOCK-[A-Z]-\\d+", RegexOption.IGNORE_CASE)
        )
        patterns.forEach { pattern -> redacted = pattern.replace(redacted, "[redacted]") }
        return redacted
    }

    companion object {
        fun redact(value: String): String = RedactedLogger().redactValue(value)
    }
}

data class TransportDiagnostics(
    val scanRestarts: Int = 0,
    val connectAttempts: Int = 0,
    val writeFailures: Int = 0
)

data class ProtocolDiagnostics(
    val packetsRejected: Int = 0,
    val replayRejections: Int = 0,
    val malformedPackets: Int = 0
)
