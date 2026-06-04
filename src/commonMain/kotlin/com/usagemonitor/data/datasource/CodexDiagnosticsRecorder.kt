package com.usagemonitor.data.datasource

import kotlinx.serialization.Serializable

interface CodexDiagnosticsRecorder {
    fun recordSuccess(event: CodexDiagnosticsSuccessEvent)

    fun recordFailure(event: CodexDiagnosticsFailureEvent)
}

object NoOpCodexDiagnosticsRecorder : CodexDiagnosticsRecorder {
    override fun recordSuccess(event: CodexDiagnosticsSuccessEvent) = Unit

    override fun recordFailure(event: CodexDiagnosticsFailureEvent) = Unit
}

@Serializable
data class CodexDiagnosticsSuccessEvent(
    val timestamp: String,
    val event: String = "success",
    val planType: String,
    val allowed: Boolean,
    val limitReached: Boolean,
    val primaryUsedPercent: Long,
    val primaryResetAt: Long,
    val primaryResetAfterSeconds: Long,
    val primaryLimitWindowSeconds: Long,
    val secondaryUsedPercent: Long,
    val secondaryResetAt: Long,
    val secondaryResetAfterSeconds: Long,
    val secondaryLimitWindowSeconds: Long
)

@Serializable
data class CodexDiagnosticsFailureEvent(
    val timestamp: String,
    val event: String = "failure",
    val statusCode: Int? = null,
    val failureKind: String,
    val message: String
)
