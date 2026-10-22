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
 * Uma conta no `accounts list`. [ready] nulo: não dá para saber pelo disco (a conta padrão
 * no macOS guarda o login no Keychain). [bySource]: a conta Codex padrão, que segue a fonte.
 */
internal data class TerminalAccountRow(
    val source: ApiSource,
    val label: String,
    val id: String,
    val origin: String,
    val monitored: Boolean,
    val ready: Boolean?,
    val bySource: Boolean = false
)

/**
 * Um ajuste no `config list`: [value] nulo é desligado. Ajuste [masked] chega aqui já sem
 * o segredo — só saber se há um — e sai como máscara.
 */
internal data class TerminalSettingRow(val key: String, val value: String?, val masked: Boolean)

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

    /** Uma linha de complemento, indentada e em muted, abaixo de um resultado. */
    fun note(text: String): TerminalLine = terminalLine(TerminalSpan("  $text", TerminalColor.Muted))

    /** `▲ aviso` e, se houver o que rodar, o comando na linha de baixo, indentado. */
    fun warning(text: String, command: String?, glyphs: TerminalGlyphs): List<TerminalLine> {
        val lines = mutableListOf(terminalLine(TerminalSpan(glyphs.warning + " ", TerminalColor.Warn), TerminalSpan(text)))
        if (command != null) {
            lines += commandLine(command)
        }
        return lines
    }

    /** Um comando para rodar, indentado e em negrito, abaixo de quem o pede. */
    fun commandLine(command: String): TerminalLine = terminalLine(TerminalSpan("    $command", bold = true))

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

    /** `config list`: o nome em muted e o valor; desligado em muted; segredo como máscara. */
    fun settings(rows: List<TerminalSettingRow>, labels: TerminalConfigLabels, glyphs: TerminalGlyphs): TerminalOutput {
        val keyWidth = rows.maxOfOrNull { row -> row.key.length } ?: 0
        val lines = rows.map { row ->
            val value = when {
                row.value == null -> TerminalSpan(if (row.masked) labels.empty else labels.off, TerminalColor.Muted)
                row.masked -> TerminalSpan(glyphs.secretMask, TerminalColor.Muted)
                else -> TerminalSpan(row.value)
            }
            terminalLine(TerminalSpan(row.key.padEnd(keyWidth) + GAP, TerminalColor.Muted), value)
        }
        return TerminalOutput(lines, EXIT_OK)
    }

    /** `accounts list`: fonte na cor dela, apelido, id curto, origem e estado. */
    fun accounts(rows: List<TerminalAccountRow>, labels: TerminalConfigLabels, profile: TerminalProfile): TerminalOutput {
        val glyphs = profile.glyphs
        val sourceWidth = maxOf(labels.headerSource.length, rows.maxOfOrNull { row -> row.source.displayName().length + 2 } ?: 0)
        val labelWidth = maxOf(labels.headerLabel.length, rows.maxOfOrNull { row -> row.label.length } ?: 0)
        val idWidth = maxOf(labels.headerId.length, rows.maxOfOrNull { row -> row.id.length } ?: 0)
        val originWidth = maxOf(labels.headerOrigin.length, rows.maxOfOrNull { row -> row.origin.length } ?: 0)
        val header = " " + listOf(
            labels.headerSource.padEnd(sourceWidth), labels.headerLabel.padEnd(labelWidth), labels.headerId.padEnd(idWidth),
            labels.headerOrigin.padEnd(originWidth), labels.headerState
        ).joinToString(GAP)
        val lines = mutableListOf(terminalLine(TerminalSpan(header, TerminalColor.Muted, bold = true)))
        for (row in rows) {
            val spans = mutableListOf(
                TerminalSpan(" "),
                TerminalSpan(glyphs.sourceMark + " ", TerminalColor.Source(row.source)),
                TerminalSpan(row.source.displayName().padEnd(sourceWidth - 2) + GAP),
                TerminalSpan(row.label.padEnd(labelWidth) + GAP),
                TerminalSpan(row.id.padEnd(idWidth) + GAP, TerminalColor.Muted),
                TerminalSpan(row.origin.padEnd(originWidth) + GAP, TerminalColor.Muted)
            )
            spans += when {
                row.bySource -> TerminalSpan(labels.bySource, TerminalColor.Muted)
                row.monitored -> TerminalSpan(labels.monitored, TerminalColor.Ok)
                else -> TerminalSpan(labels.notMonitored, TerminalColor.Muted)
            }
            when (row.ready) {
                true -> spans += TerminalSpan(" ${glyphs.separator} ${labels.ready}", TerminalColor.Muted)
                false -> spans += TerminalSpan(" ${glyphs.separator} ${labels.noLogin}", TerminalColor.Warn)
                null -> Unit
            }
            lines += TerminalLine(spans)
        }
        return TerminalOutput(lines, EXIT_OK)
    }
}
