package com.usagemonitor.presentation.terminal

/**
 * As abas do modo interativo (`usage-monitor tui`, #441), numeradas e fixas: sem time
 * configurado, a aba 4 some e as outras **mantêm o número**, para o atalho não mudar de
 * tecla (`cli/readme.md`, "Abas").
 */
enum class TuiTab(val number: Int) {
    OVERVIEW(1),
    SESSIONS(2),
    HISTORY(3),
    TEAM(4),
    SETTINGS(5);

    companion object {
        fun ofNumber(number: Int): TuiTab? = entries.firstOrNull { tab -> tab.number == number }
    }
}

/** Uma tecla já decodificada. Caractere comum chega como [Text]. */
sealed interface TuiKey {
    data class Text(val char: Char) : TuiKey
    data object Up : TuiKey
    data object Down : TuiKey
    data object Left : TuiKey
    data object Right : TuiKey
    data object Enter : TuiKey
    data object Escape : TuiKey
    data object Tab : TuiKey
    data object Space : TuiKey
    data object Backspace : TuiKey

    /** Ctrl+C: sai de qualquer lugar, mesmo de um campo em edição. */
    data object Interrupt : TuiKey
}

/** Um par do rodapé de atalhos: a tecla em negrito e o rótulo em muted. */
data class TuiShortcut(val key: String, val label: String)

/**
 * O tamanho do terminal e as larguras que saem dele (`cli/readme.md`, "Largura"): o modo
 * interativo foi desenhado para 96 colunas (94 internas); abaixo disso a coluna de detalhe
 * encolhe, e abaixo de 80 lista e detalhe se empilham. Mais largo, a moldura acompanha o terminal.
 */
data class TuiSize(val columns: Int, val rows: Int) {
    /**
     * A moldura ocupa a largura toda, como já ocupa a altura: presa em 96 colunas, num monitor
     * largo ela sobrava num canto e o resto do terminal ficava vazio.
     */
    val frameWidth: Int
        get() = columns

    /** O que cabe entre `│ ` e `│`. */
    val bodyWidth: Int
        get() = frameWidth - 3

    val bodyHeight: Int
        get() = rows - FRAME_CHROME_ROWS

    val stacked: Boolean
        get() = columns < STACK_WIDTH

    val tooSmall: Boolean
        get() = columns < MIN_COLUMNS || rows < MIN_ROWS

    /** A coluna de lista: 21 células ao lado do detalhe, a largura toda quando empilha. */
    val listWidth: Int
        get() = if (stacked) bodyWidth else LIST_WIDTH

    /** A coluna de detalhe, depois da lista e do divisor `│ `. */
    val detailWidth: Int
        get() = if (stacked) bodyWidth else bodyWidth - LIST_WIDTH - 2

    companion object {
        const val STACK_WIDTH = 80
        const val LIST_WIDTH = 21
        const val MIN_COLUMNS = 40
        const val MIN_ROWS = 12

        /** Borda de cima, abas, dois separadores, atalhos e borda de baixo. */
        const val FRAME_CHROME_ROWS = 6
    }
}

/** O que o laço do terminal faz depois de uma tecla, além de redesenhar. */
sealed interface TuiEffect {
    data object Quit : TuiEffect

    /** `r`, ou um corte novo (período, janela, time): lê de novo agora. */
    data object Refresh : TuiEffect

    /** `e` na aba 2: as sessões do Claude Code do corte num CSV, na pasta de onde a TUI foi aberta. */
    data object ExportSessions : TuiEffect

    /** Uma mudança da aba 5, pelo mesmo comando da 4.3 que a faria (#441, A08). */
    data class ApplySetting(val action: TuiSettingAction) : TuiEffect
}

/** Uma frase de resultado no pé do corpo, até a próxima tecla ("CSV salvo em …"). */
data class TuiNotice(
    val text: String,
    val ok: Boolean,
    val detail: List<String> = emptyList(),
    /** A mudança ainda está sendo aplicada: sem `✓` nem `▲`, em muted. */
    val pending: Boolean = false
) {
    companion object {
        /**
         * A saída de um comando de configurar como aviso: a primeira linha sem o `✓`/`▲` (a
         * tela põe o dela), as outras como detalhe. Mesmo texto que o comando imprimiria.
         */
        internal fun of(output: TerminalOutput, glyphs: TerminalGlyphs): TuiNotice? {
            val first = output.lines.firstOrNull() ?: return null
            val marks = setOf(glyphs.done, glyphs.warning)
            val spans = first.spans.dropWhile { span -> span.text.trim() in marks }
            val detail = output.lines.drop(1).map { line -> line.plainText.trim() }.filter { text -> text.isNotEmpty() }
            return TuiNotice(TerminalLine(spans).plainText.trim(), output.exitCode == 0, detail)
        }
    }
}
