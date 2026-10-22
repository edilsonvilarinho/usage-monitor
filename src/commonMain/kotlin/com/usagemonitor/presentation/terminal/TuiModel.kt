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
 * encolhe, e abaixo de 80 lista e detalhe se empilham.
 */
data class TuiSize(val columns: Int, val rows: Int) {
    /** A moldura nunca passa de 96 colunas: mais largo, sobra terminal à direita. */
    val frameWidth: Int
        get() = columns.coerceAtMost(DESIGN_WIDTH)

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
        const val DESIGN_WIDTH = 96
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
}

/** Uma frase de resultado no pé do corpo, até a próxima tecla ("CSV salvo em …"). */
data class TuiNotice(val text: String, val ok: Boolean)
