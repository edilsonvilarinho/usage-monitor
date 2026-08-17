package com.usagemonitor.presentation.ui.report

import com.usagemonitor.domain.entity.CliActivityHeatmap

/**
 * O relatório já resolvido em texto, antes de virar PDF.
 *
 * Mora em `presentation` e não em `data` pelo mesmo motivo de
 * `UsageExportRequests`: os números e rótulos aqui são **os da tela**, montados
 * com a mesma formatação que ela usa. Um relatório que formata por conta própria
 * diverge da tela no primeiro ajuste e ninguém percebe.
 *
 * É puro e sem dependência de PDF: quem desenha é o renderizador do desktop, que
 * recebe isto pronto. É o que permite testar o conteúdo do relatório sem gerar
 * um único byte de PDF.
 */
data class UsageReportDocument(
    val title: String,
    /** Conta, janela e carimbo de geração — o que responde "de quando é isto?". */
    val subtitle: String,
    val sections: List<UsageReportSection>,
    /**
     * Ressalvas do rodapé: custo parcial, hora não medida, janela deslizante.
     *
     * Ficam no fim e não coladas em cada número porque descrevem o documento
     * inteiro; repeti-las por linha tornaria a tabela ilegível.
     */
    val footnotes: List<String> = emptyList()
) {
    /**
     * Cópia com todo o texto reduzido ao que a fonte do PDF sabe escrever.
     *
     * Ponto único de saneamento, e não uma chamada espalhada por cada rótulo:
     * nome de projeto e branch são texto livre do usuário, e um caractere fora
     * do conjunto faz o PDFBox lançar exceção no meio da escrita — o relatório
     * inteiro morreria por causa de um emoji num nome de pasta.
     */
    fun sanitized(): UsageReportDocument {
        return UsageReportDocument(
            title = title.toReportText(),
            subtitle = subtitle.toReportText(),
            sections = sections.map { section -> section.sanitized() },
            footnotes = footnotes.map { note -> note.toReportText() }
        )
    }
}

sealed interface UsageReportSection {

    val heading: String

    fun sanitized(): UsageReportSection

    /** Bloco rótulo/valor: os totais da janela. */
    data class KeyValues(
        override val heading: String,
        val entries: List<UsageReportEntry>
    ) : UsageReportSection {
        override fun sanitized(): UsageReportSection {
            return KeyValues(
                heading = heading.toReportText(),
                entries = entries.map { entry ->
                    UsageReportEntry(entry.label.toReportText(), entry.value.toReportText())
                }
            )
        }
    }

    /** Tabela com cabeçalho fixo; o renderizador o repete a cada página. */
    data class Table(
        override val heading: String,
        val columns: List<UsageReportColumn>,
        val rows: List<List<String>>,
        val note: String? = null
    ) : UsageReportSection {
        override fun sanitized(): UsageReportSection {
            return Table(
                heading = heading.toReportText(),
                columns = columns.map { column -> column.copy(title = column.title.toReportText()) },
                rows = rows.map { row -> row.map { cell -> cell.toReportText() } },
                note = note?.toReportText()
            )
        }
    }

    /**
     * Grade dia da semana × hora local.
     *
     * Carrega a entidade do domain em vez de uma matriz de strings porque a
     * intensidade de cada célula sai de `intensityAt` — inventar uma segunda
     * escala aqui daria uma grade que não corresponde à da tela.
     */
    data class Grid(
        override val heading: String,
        val heatmap: CliActivityHeatmap,
        val note: String? = null
    ) : UsageReportSection {
        override fun sanitized(): UsageReportSection {
            return Grid(heading = heading.toReportText(), heatmap = heatmap, note = note?.toReportText())
        }
    }
}

data class UsageReportEntry(val label: String, val value: String)

/**
 * Uma coluna da tabela.
 *
 * [weight] reparte a largura disponível; [alignEnd] alinha número à direita,
 * que é o que permite comparar valores lendo a coluna de cima a baixo.
 */
data class UsageReportColumn(
    val title: String,
    val weight: Float = 1f,
    val alignEnd: Boolean = false
)

/** Substitui o que a fonte não escreve; ver [UsageReportDocument.sanitized]. */
private const val REPLACEMENT = '?'

/**
 * Caracteres fora de ASCII e de Latin-1 que a fonte ainda escreve.
 *
 * São os acréscimos do WinAnsi na faixa 128–159 — travessão, aspas curvas,
 * reticências e companhia. Aparecem em texto colado de editor e some-los seria
 * degradar o relatório sem necessidade.
 */
private val EXTRA_ENCODABLE = setOf(
    '€', '‚', 'ƒ', '„', '…', '†', '‡',
    'ˆ', '‰', 'Š', '‹', 'Œ', 'Ž', '‘',
    '’', '“', '”', '•', '–', '—', '˜',
    '™', 'š', '›', 'œ', 'ž', 'Ÿ'
)

/**
 * Texto reduzido ao que a fonte base do PDF (Helvetica, WinAnsi) escreve.
 *
 * A faixa 32–126 é ASCII imprimível e a 160–255 é a metade alta do Latin-1 —
 * as duas estão inteiras no WinAnsi, então o português acentuado passa direto.
 * O resto vira `?`: perder um emoji do nome de uma pasta é melhor que perder o
 * relatório.
 */
internal fun String.toReportText(): String {
    val builder = StringBuilder(length)
    for (char in this) {
        val code = char.code
        val encodable = code in 32..126 || code in 160..255 || char in EXTRA_ENCODABLE
        builder.append(if (encodable) char else REPLACEMENT)
    }
    return builder.toString()
}
