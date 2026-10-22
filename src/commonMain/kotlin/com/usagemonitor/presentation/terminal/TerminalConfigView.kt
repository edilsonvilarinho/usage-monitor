package com.usagemonitor.presentation.terminal

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.displayName

/** Uma fonte na lista do `sources list`. [needsKey] falso: a fonte não usa chave. */
internal data class TerminalSourceRow(
    val source: ApiSource,
    val id: String,
    val enabled: Boolean,
    val needsKey: Boolean,
    val hasKey: Boolean
)

/**
 * As telas de configurar pelo terminal (#440, 4.3 do protótipo). Funções puras de dados
 * para linhas; a primitiva é o "Resultado de comando" do design system.
 */
internal object TerminalConfigView {
    const val EXIT_OK = 0
    const val EXIT_REFUSED = 1

    private const val GAP = "  "

    /** `✓ ação · consequência`, a consequência em muted. */
    fun done(text: String, consequence: String?, glyphs: TerminalGlyphs): TerminalLine {
        val spans = mutableListOf(TerminalSpan(glyphs.done + " ", TerminalColor.Ok), TerminalSpan(text))
        if (consequence != null) {
            spans += TerminalSpan(" ${glyphs.separator} $consequence", TerminalColor.Muted)
        }
        return TerminalLine(spans)
    }

    /** `▲ aviso` e, se houver o que rodar, o comando na linha de baixo, indentado. */
    fun warning(text: String, command: String?, glyphs: TerminalGlyphs): List<TerminalLine> {
        val lines = mutableListOf(terminalLine(TerminalSpan(glyphs.warning + " ", TerminalColor.Warn), TerminalSpan(text)))
        if (command != null) {
            lines += terminalLine(TerminalSpan("    $command", bold = true))
        }
        return lines
    }

    /** `sources list`: fonte (na cor dela), id, estado e chave, na ordem do app. */
    fun sources(rows: List<TerminalSourceRow>, labels: TerminalConfigLabels, profile: TerminalProfile): TerminalOutput {
        val nameWidth = maxOf(labels.headerSource.length, rows.maxOfOrNull { row -> row.source.displayName().length + 2 } ?: 0)
        val idWidth = maxOf(labels.headerId.length, rows.maxOfOrNull { row -> row.id.length } ?: 0)
        val stateWidth = maxOf(labels.headerState.length, labels.enabled.length, labels.disabled.length)
        val header = " " + listOf(labels.headerSource.padEnd(nameWidth), labels.headerId.padEnd(idWidth), labels.headerState.padEnd(stateWidth), labels.headerKey)
            .joinToString(GAP)
        val lines = mutableListOf(terminalLine(TerminalSpan(header, TerminalColor.Muted, bold = true)))
        for (row in rows) {
            val key = when {
                !row.needsKey -> TerminalSpan(profile.glyphs.skipped, TerminalColor.Muted)
                row.hasKey -> TerminalSpan(labels.keySaved, TerminalColor.Ok)
                else -> TerminalSpan(labels.keyMissing, TerminalColor.Warn)
            }
            lines += terminalLine(
                TerminalSpan(" "),
                TerminalSpan(profile.glyphs.sourceMark + " ", TerminalColor.Source(row.source)),
                TerminalSpan(row.source.displayName().padEnd(nameWidth - 2) + GAP),
                TerminalSpan(row.id.padEnd(idWidth) + GAP, TerminalColor.Muted),
                TerminalSpan((if (row.enabled) labels.enabled else labels.disabled).padEnd(stateWidth) + GAP, if (row.enabled) TerminalColor.Ok else TerminalColor.Muted),
                key
            )
        }
        return TerminalOutput(lines, EXIT_OK)
    }
}
