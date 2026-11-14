package com.usagemonitor.data.datasource

import kotlinx.serialization.SerialName
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
    @SerialName("timestamp") val timestamp: String,
    @SerialName("event") val event: String = "success",
    @SerialName("sourceKind") val sourceKind: String = "legacy_five_hour",
    @SerialName("planType") val planType: String,
    @SerialName("allowed") val allowed: Boolean,
    @SerialName("limitReached") val limitReached: Boolean,
    @SerialName("primaryUsedPercent") val primaryUsedPercent: Long? = null,
    @SerialName("primaryResetAt") val primaryResetAt: Long? = null,
    @SerialName("primaryResetAfterSeconds") val primaryResetAfterSeconds: Long? = null,
    @SerialName("primaryLimitWindowSeconds") val primaryLimitWindowSeconds: Long? = null
)

@Serializable
data class CodexDiagnosticsFailureEvent(
    @SerialName("timestamp") val timestamp: String,
    @SerialName("event") val event: String = "failure",
    @SerialName("statusCode") val statusCode: Int? = null,
    @SerialName("failureKind") val failureKind: String,
    @SerialName("message") val message: String
)
