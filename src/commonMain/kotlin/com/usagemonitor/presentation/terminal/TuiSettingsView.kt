package com.usagemonitor.presentation.terminal

import com.usagemonitor.domain.entity.CollectorKind

/**
 * Aba 5, Configurações (4.2): as seções à esquerda e os campos da escolhida à direita
 * (`cli/readme.md`, "Campo de configuração"). No alto da seção, quando a mudança passa a
 * valer: na hora, com o coletor vivo, ou depois de reiniciar (proxy). Empilhado, a lista ou
 * os campos, como a aba 1.
 */
internal object TuiSettingsView {
    /** `[x] ` ou quatro espaços: os rótulos alinham entre os tipos de campo. */
    private const val MARK_WIDTH = 4

    fun render(
        settings: TuiSettings?,
        state: TuiSettingsState,
        collector: CollectorKind?,
        size: TuiSize,
        labels: TuiSettingsLabels,
        profile: TerminalProfile
    ): TuiTabView {
        settings ?: return TuiTabView(emptyList())
        if (settings.sections.isEmpty()) {
            return TuiTabView(emptyList())
        }
        val glyphs = profile.glyphs
        val sectionIndex = state.section.coerceIn(0, settings.sections.lastIndex)
        val section = settings.sections[sectionIndex]
        val left = listOf(TuiFrame.header(labels.headerSections), TerminalLine.EMPTY) +
            settings.sections.mapIndexed { index, item ->
                TuiFrame.listItem(terminalLine(TerminalSpan(item.title)), selected = index == sectionIndex, width = size.listWidth, glyphs = glyphs)
            }
        val right = fields(section, state, collector, size, labels, glyphs)
        return TuiTabView(TuiFrame.listDetail(left, right, size, detailOpen = state.inFields, box = glyphs.box), shortcuts(section, state, labels))
    }

    private fun fields(
        section: TuiSettingsSection,
        state: TuiSettingsState,
        collector: CollectorKind?,
        size: TuiSize,
        labels: TuiSettingsLabels,
        glyphs: TerminalGlyphs
    ): List<TerminalLine> {
        val width = size.detailWidth
        val configLabels = TerminalConfigLabels(labels.language)
        val applies = if (section.restartNeeded) configLabels.restartNeeded(collector) else configLabels.appliedBy(collector)
        val title = terminalLine(TerminalSpan(section.title, bold = true))
        val gap = width - 1 - title.width - applies.length
        val header = if (gap >= 2) title + terminalLine(TerminalSpan(" ".repeat(gap) + applies, if (section.restartNeeded) TerminalColor.Warn else TerminalColor.Muted)) else title
        val lines = mutableListOf(header, TerminalLine.EMPTY)
        // A dica quebra por palavra: em 80 colunas a coluna dos campos tem 54 células.
        val hint = section.hint?.let { text -> TuiScreen.wrapped(text, width - 1) }.orEmpty()
        // O cabeçalho e a linha em branco já estão; a dica, quando há, leva a linha em branco e as dela.
        val reserved = lines.size + if (hint.isEmpty()) 0 else hint.size + 1
        val visible = (size.bodyHeight - reserved).coerceAtLeast(1)
        val selected = state.field.coerceIn(0, (section.fields.size - 1).coerceAtLeast(0))
        val start = TuiFrame.scrollStart(selected, section.fields.size, visible)
        section.fields.subList(start, (start + visible).coerceAtMost(section.fields.size)).forEachIndexed { offset, field ->
            val index = start + offset
            val focused = state.inFields && index == selected
            val editing = state.editing?.takeIf { focused }
            val confirming = focused && state.confirming != null
            val line = row(field, editing, confirming, width - 1, labels, glyphs)
            lines += if (focused) line.fit(width - 1).selected() else line
        }
        if (hint.isNotEmpty()) {
            lines += TerminalLine.EMPTY
            hint.forEach { part -> lines += terminalLine(TerminalSpan(part, TerminalColor.Muted)) }
        }
        return lines
    }

    private fun row(field: TuiSettingField, editing: TuiTextEdit?, confirming: Boolean, width: Int, labels: TuiSettingsLabels, glyphs: TerminalGlyphs): TerminalLine {
        val indent = TerminalSpan(" ".repeat(MARK_WIDTH))
        val content = when (field) {
            is TuiSettingField.Toggle -> {
                val mark = if (field.on) TerminalSpan("[x] ", TerminalColor.Ok) else TerminalSpan("[ ] ", TerminalColor.Muted)
                if (editing != null) terminalLine(mark) + edited(field.label, editing, glyphs) else terminalLine(mark, TerminalSpan(field.label))
            }
            is TuiSettingField.Stepper -> terminalLine(
                indent,
                TerminalSpan(field.label + "  "),
                TerminalSpan(glyphs.adjustLeft, if (field.previous != null) TerminalColor.Gold else TerminalColor.Muted),
                TerminalSpan(" ${field.shown} ", bold = true),
                TerminalSpan(glyphs.adjustRight, if (field.next != null) TerminalColor.Gold else TerminalColor.Muted)
            )
            is TuiSettingField.Text ->
                if (editing != null) {
                    terminalLine(indent) + edited(field.label, editing, glyphs)
                } else {
                    val value = field.shown?.let { text -> TerminalSpan(" $text ", bold = true) } ?: TerminalSpan(" ${labels.emptyValue} ", TerminalColor.Muted)
                    terminalLine(indent, TerminalSpan(field.label + "  "), TerminalSpan("[", TerminalColor.Border), value, TerminalSpan("]", TerminalColor.Border))
                }
            is TuiSettingField.Action -> terminalLine(indent, TerminalSpan(field.label, TerminalColor.Gold))
            is TuiSettingField.Info -> terminalLine(indent, TerminalSpan(field.label + "  ", TerminalColor.Muted), TerminalSpan(field.value))
        }
        val note = when {
            confirming -> TerminalSpan("   ${labels.confirm}", TerminalColor.Warn)
            editing != null -> if (editing.edit.secret) TerminalSpan("   ${labels.editingSecret}", TerminalColor.Muted) else null
            field.note != null -> TerminalSpan("   ${field.note}", TerminalColor.Muted)
            else -> null
        }
        // A nota que não cabe inteira sai: cortada no meio da palavra ela confunde mais do que ajuda.
        return if (note != null && content.width + note.text.length <= width) content + terminalLine(note) else content
    }

    /** O campo aberto: `[ texto▏ ]`. Segredo não ecoa nada, nem o tamanho. */
    private fun edited(label: String, editing: TuiTextEdit, glyphs: TerminalGlyphs): TerminalLine {
        val shown = if (editing.edit.secret) "" else editing.text
        return terminalLine(
            TerminalSpan("$label  "),
            TerminalSpan("[", TerminalColor.Border),
            TerminalSpan(" $shown", bold = true),
            TerminalSpan(glyphs.cursor, TerminalColor.Gold),
            TerminalSpan(" "),
            TerminalSpan("]", TerminalColor.Border)
        )
    }

    private fun shortcuts(section: TuiSettingsSection, state: TuiSettingsState, labels: TuiSettingsLabels): List<TuiShortcut> {
        if (state.editing != null) {
            return listOf(TuiShortcut("enter", labels.keySave), TuiShortcut("esc", labels.keyGiveUp))
        }
        if (!state.inFields) {
            return listOf(TuiShortcut("↑↓", labels.keySection), TuiShortcut("enter", labels.keyOpen))
        }
        val field = section.fields.getOrNull(state.field)
        return buildList {
            add(TuiShortcut("↑↓", labels.keyField))
            when (field) {
                is TuiSettingField.Toggle -> {
                    add(TuiShortcut(labels.spaceKey, labels.keyToggle))
                    if (field.rename != null) add(TuiShortcut("enter", labels.keyEdit))
                }
                is TuiSettingField.Stepper -> add(TuiShortcut("←→", labels.keyValue))
                is TuiSettingField.Text -> add(TuiShortcut("enter", labels.keyEdit))
                is TuiSettingField.Action -> add(TuiShortcut("enter", labels.keyRun))
                is TuiSettingField.Info, null -> Unit
            }
            add(TuiShortcut("tab", labels.keySection))
            add(TuiShortcut("esc", labels.keyBack))
        }
    }
}
