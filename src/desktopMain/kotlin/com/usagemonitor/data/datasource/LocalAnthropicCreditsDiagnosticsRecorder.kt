package com.usagemonitor.data.datasource

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

private const val ANTHROPIC_CREDITS_DIAGNOSTICS_ENV_VAR = "USAGE_MONITOR_DEBUG_ANTHROPIC_CREDITS"
private val ENABLED_FLAG_VALUES = setOf("1", "true", "yes", "on")

/**
 * Grava o diagnóstico dos créditos em JSONL, no mesmo desenho do recorder do
 * Codex: desligado por padrão, uma linha por coleta, arquivo restrito ao dono.
 */
class LocalAnthropicCreditsDiagnosticsRecorder(
    override val isEnabled: Boolean = isAnthropicCreditsDiagnosticsEnabled(),
    private val diagnosticsFile: File = defaultDiagnosticsFile(),
    private val json: Json = Json { encodeDefaults = true }
) : AnthropicCreditsDiagnosticsRecorder {

    private val lock = Any()

    override fun record(event: AnthropicCreditsDiagnosticsEvent) {
        if (!isEnabled) {
            return
        }

        appendLine(json.encodeToString(event))
    }

    private fun appendLine(line: String) {
        synchronized(lock) {
            diagnosticsFile.parentFile?.mkdirs()
            diagnosticsFile.appendText("$line\n")
            restrictToOwnerReadWrite(diagnosticsFile.toPath())
        }
    }

    private companion object {
        fun isAnthropicCreditsDiagnosticsEnabled(): Boolean {
            val value = System.getenv(ANTHROPIC_CREDITS_DIAGNOSTICS_ENV_VAR)
                ?.trim()
                ?.lowercase()
                ?: return false

            return value in ENABLED_FLAG_VALUES
        }

        fun defaultDiagnosticsFile(): File {
            val homeDir = System.getProperty("user.home")
                ?: throw IllegalStateException("Propriedade 'user.home' não disponível")

            return File(homeDir, ".usage-monitor/diagnostics/anthropic-credits.jsonl")
        }
    }
}
