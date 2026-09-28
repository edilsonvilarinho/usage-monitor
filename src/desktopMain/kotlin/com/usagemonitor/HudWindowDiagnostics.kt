package com.usagemonitor

import com.usagemonitor.data.datasource.restrictToOwnerReadWrite
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * O que a HUD fez com o recorte de clique da janela (issue #342).
 *
 * A #340 chegou como uma captura de tela e nada mais: sem saber se o recorte foi
 * aplicado, pulado ou recusado pelo sistema, a causa teve de ser deduzida pelo
 * formato do que sobrou na tela. O Codenotch pede o log unificado no relato de
 * bug pelo mesmo motivo.
 */
internal enum class HudHitRegionEvent {
    /** A plataforma usa o recorte (`hudUsesHitRegion`). */
    ENABLED,

    /** A plataforma não usa o recorte: a janela fica inteira clicável. */
    SKIPPED,

    /** `Window.setShape` lançou — sem `PERPIXEL_TRANSPARENT`, por exemplo. */
    SET_FAILED,

    /**
     * O `shape = null` voltou sem efeito **do lado do Java** (`getShape()` ainda
     * não nulo). Não pega o caso da #340, em que o Java diz nulo e o X11 mantém
     * o recorte: esse só se vê na tela.
     */
    CLEAR_NOT_EFFECTIVE;

    val wireValue: String
        get() = when (this) {
            ENABLED -> "enabled"
            SKIPPED -> "skipped"
            SET_FAILED -> "set-failed"
            CLEAR_NOT_EFFECTIVE -> "clear-not-effective"
        }
}

/**
 * Uma linha por evento em `~/.usage-monitor/diagnostics/hud-window.jsonl`, e
 * **cada evento uma vez por processo**: o recorte entra e sai a cada hover, e
 * gravar todos expulsaria do arquivo o que explica o defeito. Sistema e
 * sessão gráfica ficam no `startup.jsonl` do mesmo `pid`.
 *
 * Sempre ligado, como o registro de arranque; só metadados, nada de uso.
 */
internal class HudWindowDiagnostics(
    private val diagnosticsFile: File = defaultDiagnosticsFile(),
    private val json: Json = Json { encodeDefaults = true }
) {
    private val lock = Any()
    private val recorded = mutableSetOf<HudHitRegionEvent>()

    fun record(
        event: HudHitRegionEvent,
        detail: String? = null,
        version: String = CURRENT_APP_VERSION,
        pid: Long = ProcessHandle.current().pid(),
        nowMillis: Long = Clock.System.now().toEpochMilliseconds(),
        platform: String = AutoStartManager.currentPlatform().name.lowercase()
    ) {
        val entry = HudWindowDiagnosticsEntry(
            ts = kotlinx.datetime.Instant.fromEpochMilliseconds(nowMillis).toString(),
            pid = pid,
            version = version,
            platform = platform,
            event = event.wireValue,
            detail = detail
        )
        synchronized(lock) {
            if (!recorded.add(event)) {
                return
            }
            // Falha aqui não pode derrubar a HUD: o registro explica a janela.
            runCatching { appendLine(json.encodeToString(entry)) }
        }
    }

    private fun appendLine(line: String) {
        diagnosticsFile.parentFile?.mkdirs()
        trimIfNeeded()
        diagnosticsFile.appendText("$line\n")
        restrictToOwnerReadWrite(diagnosticsFile.toPath())
    }

    // Os mesmos limites do registro de arranque e da trilha de passos.
    private fun trimIfNeeded() {
        if (!diagnosticsFile.exists()) {
            return
        }
        val lines = diagnosticsFile.readLines()
        if (lines.size <= StartupDiagnostics.MAX_LINES) {
            return
        }
        val kept = lines.takeLast(StartupDiagnostics.KEPT_LINES)
        diagnosticsFile.writeText(kept.joinToString(separator = "\n", postfix = "\n"))
    }

    internal companion object {
        fun defaultDiagnosticsFile(): File {
            val homeDir = System.getProperty("user.home")
                ?: throw IllegalStateException("Propriedade 'user.home' não disponível")
            return File(homeDir, ".usage-monitor/diagnostics/hud-window.jsonl")
        }
    }
}

@Serializable
private data class HudWindowDiagnosticsEntry(
    val ts: String,
    val pid: Long,
    val version: String,
    val platform: String,
    val event: String,
    val detail: String?
)
