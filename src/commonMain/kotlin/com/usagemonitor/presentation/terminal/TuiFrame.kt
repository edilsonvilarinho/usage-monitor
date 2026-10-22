package com.usagemonitor.presentation.terminal

/**
 * A moldura única do modo interativo (`cli/readme.md`, "Moldura do modo interativo", tela 3.1
 * do protótipo): o título e o status na borda de cima, a linha de abas, o corpo e o rodapé de
 * atalhos. Muda a aba, a moldura fica. Funções puras de dados para linhas.
 */
internal object TuiFrame {

    /**
     * A tela inteira, com exatamente [TuiSize.rows] linhas de [TuiSize.frameWidth] células. O
     * corpo é cortado na altura e na largura; quem rola a lista é a aba, que sabe onde está a
     * seleção.
     */
    fun render(
        size: TuiSize,
        tabs: List<TuiTab>,
        active: TuiTab,
        status: TerminalSpan,
        body: List<TerminalLine>,
        shortcuts: List<TuiShortcut>,
        labels: TuiLabels,
        profile: TerminalProfile
    ): List<TerminalLine> {
        val box = profile.glyphs.box
        val inner = size.frameWidth - 2
        val lines = mutableListOf<TerminalLine>()
        lines += topBorder(inner, status, labels, box)
        lines += bordered(tabLine(tabs, active, labels, profile).fit(inner), box)
        lines += separator(inner, box)
        val bodyLines = body.take(size.bodyHeight.coerceAtLeast(0))
        for (line in bodyLines) {
            lines += bordered(terminalLine(TerminalSpan(" ")) + line.fit(inner - 1), box)
        }
        repeat(size.bodyHeight - bodyLines.size) {
            lines += bordered(TerminalLine.EMPTY.fit(inner), box)
        }
        lines += separator(inner, box)
        lines += bordered(terminalLine(TerminalSpan(" ")) + shortcutLine(shortcuts).fit(inner - 1), box)
        lines += terminalLine(TerminalSpan(box.bottomLeft + box.horizontal.toString().repeat(inner) + box.bottomRight, TerminalColor.Border))
        return lines
    }

    /** Terminal menor que o mínimo: só a frase, sem moldura, para nada sair embaralhado. */
    fun tooSmall(size: TuiSize, labels: TuiLabels): List<TerminalLine> =
        listOf(terminalLine(TerminalSpan(labels.tooSmall(size.columns, size.rows), TerminalColor.Warn)).clip(size.columns))

    /** `╭─ usage monitor ──── 14:23 BRT · ao vivo ─╮`: título em ouro à esquerda, status à direita. */
    private fun topBorder(inner: Int, status: TerminalSpan, labels: TuiLabels, box: TerminalBoxGlyphs): TerminalLine {
        val title = TerminalSpan(" ${labels.title} ", TerminalColor.Gold, bold = true)
        var statusText = " ${status.text} "
        val room = inner - 2 - title.text.length - 1
        if (statusText.length > room) {
            statusText = if (room > 2) statusText.take(room - 1) + " " else ""
        }
        val fill = (inner - 2 - title.text.length - statusText.length).coerceAtLeast(0)
        val dash = box.horizontal.toString()
        return terminalLine(
            TerminalSpan(box.topLeft + dash, TerminalColor.Border),
            title,
            TerminalSpan(dash.repeat(fill), TerminalColor.Border),
            status.copy(text = statusText),
            TerminalSpan(dash + box.topRight, TerminalColor.Border)
        ).clip(inner + 2)
    }

    private fun separator(inner: Int, box: TerminalBoxGlyphs): TerminalLine =
        terminalLine(TerminalSpan(box.teeLeft + box.horizontal.toString().repeat(inner) + box.teeRight, TerminalColor.Border))

    private fun bordered(line: TerminalLine, box: TerminalBoxGlyphs): TerminalLine {
        val edge = TerminalSpan(box.vertical.toString(), TerminalColor.Border)
        return terminalLine(edge) + line + terminalLine(edge)
    }

    /**
     * As abas numeradas: a ativa em ouro e negrito sobre o fundo de seleção, as outras em
     * muted. Sem cor não há fundo, e a ativa ganha colchetes no lugar dos espaços que a
     * cercam, com a mesma largura: a linha não anda entre os modos.
     */
    fun tabLine(tabs: List<TuiTab>, active: TuiTab, labels: TuiLabels, profile: TerminalProfile): TerminalLine {
        val spans = mutableListOf<TerminalSpan>()
        for (tab in tabs) {
            val name = "${tab.number} ${labels.tab(tab)}"
            spans += if (tab == active) {
                val text = if (profile.palette == TerminalPalette.NONE) "[$name]" else " $name "
                TerminalSpan(text, TerminalColor.Gold, bold = true, selected = true)
            } else {
                TerminalSpan(" $name ", TerminalColor.Muted)
            }
            spans += TerminalSpan(" ")
        }
        return TerminalLine(spans)
    }

    /** O rodapé de atalhos: a tecla em negrito, o rótulo em muted, dois espaços entre pares. */
    fun shortcutLine(shortcuts: List<TuiShortcut>): TerminalLine =
        TerminalLine(
            shortcuts.flatMap { shortcut ->
                listOf(TerminalSpan(shortcut.key, bold = true), TerminalSpan(" ${shortcut.label}  ", TerminalColor.Muted))
            }
        )

    /**
     * Caixa (tela 3.5): borda arredondada e título opcional na cor de acento. [inner] é a
     * largura entre as bordas; cada linha vai com um espaço à esquerda. Nunca há caixa
     * dentro de caixa.
     */
    fun box(title: TerminalSpan?, lines: List<TerminalLine>, inner: Int, box: TerminalBoxGlyphs): List<TerminalLine> {
        val dash = box.horizontal.toString()
        val titleText = title?.let { span -> " ${span.text} " }.orEmpty()
        val top = mutableListOf(TerminalSpan(box.topLeft + dash, TerminalColor.Border))
        if (title != null) {
            top += title.copy(text = titleText)
        }
        top += TerminalSpan(dash.repeat((inner - titleText.length - 1).coerceAtLeast(0)) + box.topRight, TerminalColor.Border)
        val edge = TerminalSpan(box.vertical.toString(), TerminalColor.Border)
        val out = mutableListOf(TerminalLine(top).clip(inner + 2))
        for (line in lines) {
            out += terminalLine(edge, TerminalSpan(" ")) + line.fit(inner - 1) + terminalLine(edge)
        }
        out += terminalLine(TerminalSpan(box.bottomLeft + dash.repeat(inner) + box.bottomRight, TerminalColor.Border))
        return out
    }

    /**
     * Lista e detalhe (3.1, 4.2): a lista em [TuiSize.listWidth] células, o divisor `│` e o
     * detalhe à direita. Empilhado (abaixo de 80 colunas), uma coisa de cada vez: a lista, ou
     * o detalhe quando [detailOpen].
     */
    fun listDetail(left: List<TerminalLine>, right: List<TerminalLine>, size: TuiSize, detailOpen: Boolean, box: TerminalBoxGlyphs): List<TerminalLine> {
        if (size.stacked) {
            return if (detailOpen) right else left
        }
        val divider = TerminalSpan(box.vertical.toString(), TerminalColor.Border)
        val rows = maxOf(left.size, right.size)
        return (0 until rows).map { row ->
            val listCell = (left.getOrNull(row) ?: TerminalLine.EMPTY).fit(size.listWidth)
            listCell + terminalLine(divider, TerminalSpan(" ")) + (right.getOrNull(row) ?: TerminalLine.EMPTY)
        }
    }

    /**
     * Um item de lista: `▸` em ouro e o fundo de seleção na linha selecionada, dois espaços
     * nas outras. [width] é a largura da coluna; a seleção pinta uma célula a menos, para o
     * fundo não encostar no divisor.
     */
    fun listItem(content: TerminalLine, selected: Boolean, width: Int, glyphs: TerminalGlyphs): TerminalLine {
        if (!selected) {
            return terminalLine(TerminalSpan("  ")) + content
        }
        val marker = TerminalSpan(glyphs.selection + " ", TerminalColor.Gold)
        return (terminalLine(marker) + content).fit(width - 1).selected()
    }

    /** Cabeçalho de coluna: maiúsculas, muted e negrito. */
    fun header(text: String): TerminalLine = terminalLine(TerminalSpan(text, TerminalColor.Muted, bold = true))

    /**
     * A janela de [visible] linhas que mantém [selected] à vista, para listas maiores que a
     * tela. Devolve o primeiro índice mostrado.
     */
    fun scrollStart(selected: Int, total: Int, visible: Int): Int {
        if (visible <= 0 || total <= visible) {
            return 0
        }
        return (selected - visible + 1).coerceAtLeast(0).coerceAtMost(total - visible)
    }
}
