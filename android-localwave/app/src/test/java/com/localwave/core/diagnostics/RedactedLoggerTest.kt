package com.localwave.core.diagnostics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RedactedLoggerTest {
    @Test
    fun redactsSecretsBeforeRecordingDiagnosticEvent() {
        val logger = RedactedLogger()
        logger.record(
            category = "crypto",
            message = "private key abc shared secret def message=hello frequency DOCK-A-17"
        )

        val event = logger.events.value.single()
        assertFalse(event.message.contains("private key", ignoreCase = true))
        assertFalse(event.message.contains("shared secret", ignoreCase = true))
        assertFalse(event.message.contains("hello", ignoreCase = true))
        assertTrue(event.message.contains("[redacted]", ignoreCase = true))
    }
}
