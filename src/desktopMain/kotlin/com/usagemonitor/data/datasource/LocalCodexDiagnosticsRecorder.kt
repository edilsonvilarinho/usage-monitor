package com.usagemonitor.data.datasource

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

private const val CODEX_DIAGNOSTICS_ENV_VAR = "USAGE_MONITOR_DEBUG_CODEX"
private val ENABLED_FLAG_VALUES = setOf("1", "true", "yes", "on")

class LocalCodexDiagnosticsRecorder(
    private val enabled: Boolean = isCodexDiagnosticsEnabled(),
    private val diagnosticsFile: File = defaultDiagnosticsFile(),
    private val json: Json = Json { encodeDefaults = true }
) : CodexDiagnosticsRecorder {

    private val lock = Any()

    override fun recordSuccess(event: CodexDiagnosticsSuccessEvent) {
        if (!enabled) {
            return
        }

        appendLine(json.encodeToString(event))
    }

    override fun recordFailure(event: CodexDiagnosticsFailureEvent) {
        if (!enabled) {
            return
        }

        appendLine(json.encodeToString(event))
    }

    private fun appendLine(line: String) {
        synchronized(lock) {
            diagnosticsFile.parentFile?.mkdirs()
            diagnosticsFile.appendText("$line\n")
        }
    }

    private companion object {
        fun isCodexDiagnosticsEnabled(): Boolean {
            val value = System.getenv(CODEX_DIAGNOSTICS_ENV_VAR)
                ?.trim()
                ?.lowercase()
                ?: return false

            return value in ENABLED_FLAG_VALUES
        }

        fun defaultDiagnosticsFile(): File {
            val homeDir = System.getProperty("user.home")
                ?: throw IllegalStateException("Propriedade 'user.home' não disponível")

            return File(homeDir, ".usage-monitor/diagnostics/codex-usage.jsonl")
        }
    }
}
