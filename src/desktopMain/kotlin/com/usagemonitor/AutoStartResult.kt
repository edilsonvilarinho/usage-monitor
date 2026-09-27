package com.usagemonitor

sealed interface AutoStartResult {
    data object Success : AutoStartResult

    data class Failure(
        val reason: String,
        val exitCode: Int? = null
    ) : AutoStartResult

    val isSuccess: Boolean
        get() = this is Success
}

internal data class AutoStartCommandResult(
    val exitCode: Int,
    val output: String
)

internal fun AutoStartCommandResult.toAutoStartResult(operation: String): AutoStartResult {
    if (exitCode == 0) {
        return AutoStartResult.Success
    }
    return AutoStartResult.Failure(
        reason = buildString {
            append(operation)
            append(" retornou código ")
            append(exitCode)
            val outputSummary = output.lineSequence()
                .map(String::trim)
                .firstOrNull(String::isNotBlank)
            if (outputSummary != null) {
                append(": ")
                append(com.usagemonitor.domain.entity.sanitizeBreadcrumbErrorMessage(outputSummary))
            }
        },
        exitCode = exitCode
    )
}

internal fun Throwable.toAutoStartFailure(operation: String): AutoStartResult.Failure {
    return AutoStartResult.Failure(
        reason = "$operation: ${com.usagemonitor.domain.entity.breadcrumbFailureReasonOf(this)}"
    )
}
