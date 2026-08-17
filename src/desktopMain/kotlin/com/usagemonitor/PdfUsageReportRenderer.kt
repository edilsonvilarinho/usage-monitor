package com.usagemonitor

import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.ui.report.ReportLabels
import com.usagemonitor.presentation.ui.report.UsageReportColumn
import com.usagemonitor.presentation.ui.report.UsageReportDocument
import com.usagemonitor.presentation.ui.report.UsageReportSection
import kotlinx.datetime.DayOfWeek
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDFont
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import java.io.ByteArrayOutputStream

/**
 * Desenha um [UsageReportDocument] em PDF.
 *
 * Vive em `desktopMain` porque o PDFBox é JVM-only. Recebe o documento já
 * pronto e **não decide conteúdo**: nenhuma soma, nenhuma formatação de número.
 * A divisão é essa de propósito — o que vai no relatório é testável sem gerar
 * PDF, e o que este arquivo pode errar é só desenho.
 *
 * Paisagem porque as tabelas têm até nove colunas; em retrato o nome do modelo
 * sozinho já comeria metade da linha.
 */
class PdfUsageReportRenderer(private val language: AppLanguage) {

    fun render(document: UsageReportDocument): ByteArray {
        PDDocument().use { pdf ->
            val writer = PageWriter(pdf, language)

            writer.title(document.title)
            writer.subtitle(document.subtitle)

            for (section in document.sections) {
                when (section) {
                    is UsageReportSection.KeyValues -> writer.keyValues(section)
                    is UsageReportSection.Table -> writer.table(section)
                    is UsageReportSection.Grid -> writer.grid(section)
                }
            }

            if (document.footnotes.isNotEmpty()) {
                writer.footnotes(document.footnotes)
            }

            writer.finish()

            val output = ByteArrayOutputStream()
            pdf.save(output)
            return output.toByteArray()
        }
    }
}

private val PAGE_SIZE = PDRectangle(PDRectangle.A4.height, PDRectangle.A4.width)

private const val MARGIN = 36f
private const val TITLE_SIZE = 16f
private const val SUBTITLE_SIZE = 9f
private const val HEADING_SIZE = 11f
private const val BODY_SIZE = 8f
private const val NOTE_SIZE = 7f
private const val LINE_GAP = 3f
private const val SECTION_GAP = 14f
private const val CELL_PADDING = 3f

/** Altura de uma célula da grade; 24 colunas de hora têm de caber na largura. */
private const val GRID_CELL = 13f
private const val GRID_LABEL_WIDTH = 34f

/**
 * Escreve de cima para baixo, abrindo página nova quando o espaço acaba.
 *
 * O cursor é um campo e não um retorno de função porque cada bloco precisa saber
 * onde o anterior parou; devolver a posição em toda chamada só espalharia a
 * mesma variável pelos parâmetros.
 */
private class PageWriter(private val pdf: PDDocument, private val language: AppLanguage) {

    private val regular: PDFont = PDType1Font(Standard14Fonts.FontName.HELVETICA)
    private val bold: PDFont = PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD)

    private val contentWidth = PAGE_SIZE.width - 2 * MARGIN

    private var stream: PDPageContentStream = newPage()
    private var cursorY = PAGE_SIZE.height - MARGIN
    private var pageCount = 1

    private fun newPage(): PDPageContentStream {
        val page = PDPage(PAGE_SIZE)
        pdf.addPage(page)
        return PDPageContentStream(pdf, page)
    }

    private fun breakPage() {
        stream.close()
        stream = newPage()
        cursorY = PAGE_SIZE.height - MARGIN
        pageCount += 1
    }

    /** Abre página nova quando o que vem não cabe no que sobrou. */
    private fun ensureSpace(height: Float) {
        if (cursorY - height >= MARGIN) {
            return
        }
        breakPage()
    }

    private fun drawText(text: String, x: Float, y: Float, font: PDFont, size: Float, gray: Float = 0f) {
        stream.beginText()
        stream.setNonStrokingColor(gray, gray, gray)
        stream.setFont(font, size)
        stream.newLineAtOffset(x, y)
        stream.showText(text)
        stream.endText()
    }

    private fun widthOf(text: String, font: PDFont, size: Float): Float {
        return font.getStringWidth(text) / 1000f * size
    }

    /**
     * Corta o texto que não cabe, com reticências.
     *
     * Sem isso a célula longa invadiria a coluna seguinte — e como cada linha é
     * desenhada em posição absoluta, a invasão sairia como texto sobreposto e
     * ilegível, não como quebra.
     */
    private fun truncate(text: String, maxWidth: Float, font: PDFont, size: Float): String {
        if (widthOf(text, font, size) <= maxWidth) {
            return text
        }
        val ellipsis = "..."
        val available = maxWidth - widthOf(ellipsis, font, size)
        if (available <= 0f) {
            return ""
        }
        var end = text.length
        while (end > 0 && widthOf(text.take(end), font, size) > available) {
            end -= 1
        }
        return text.take(end) + ellipsis
    }

    fun title(text: String) {
        ensureSpace(TITLE_SIZE + LINE_GAP)
        cursorY -= TITLE_SIZE
        drawText(text, MARGIN, cursorY, bold, TITLE_SIZE)
        cursorY -= LINE_GAP
    }

    fun subtitle(text: String) {
        ensureSpace(SUBTITLE_SIZE + SECTION_GAP)
        cursorY -= SUBTITLE_SIZE + LINE_GAP
        drawText(text, MARGIN, cursorY, regular, SUBTITLE_SIZE, gray = 0.35f)
        cursorY -= SECTION_GAP
    }

    private fun heading(text: String) {
        // Junto com uma linha de conteúdo: título sozinho no pé da página é o
        // cabeçalho órfão que faz o leitor virar a folha para descobrir do que ele
        // era título.
        ensureSpace(HEADING_SIZE + BODY_SIZE + 2 * LINE_GAP)
        cursorY -= HEADING_SIZE
        drawText(text, MARGIN, cursorY, bold, HEADING_SIZE)
        cursorY -= LINE_GAP * 2
    }

    private fun note(text: String) {
        ensureSpace(NOTE_SIZE + LINE_GAP)
        cursorY -= NOTE_SIZE
        drawText(truncate(text, contentWidth, regular, NOTE_SIZE), MARGIN, cursorY, regular, NOTE_SIZE, gray = 0.45f)
        cursorY -= LINE_GAP
    }

    fun keyValues(section: UsageReportSection.KeyValues) {
        heading(section.heading)

        // Duas colunas: os totais são poucos e uma coluna só deixaria dois terços
        // da folha em branco ao lado deles.
        val columnWidth = contentWidth / 2f
        val rows = (section.entries.size + 1) / 2

        for (index in 0 until rows) {
            ensureSpace(BODY_SIZE + LINE_GAP)
            cursorY -= BODY_SIZE
            for (column in 0 until 2) {
                val entry = section.entries.getOrNull(index * 2 + column) ?: continue
                val x = MARGIN + column * columnWidth
                drawText("${entry.label}: ", x, cursorY, regular, BODY_SIZE, gray = 0.35f)
                val labelWidth = widthOf("${entry.label}: ", regular, BODY_SIZE)
                drawText(entry.value, x + labelWidth, cursorY, bold, BODY_SIZE)
            }
            cursorY -= LINE_GAP
        }
        cursorY -= SECTION_GAP
    }

    fun table(section: UsageReportSection.Table) {
        heading(section.heading)
        if (section.note != null) {
            note(section.note)
        }

        val widths = columnWidths(section.columns)

        if (section.rows.isEmpty()) {
            note(EMPTY_TABLE_MARK)
            cursorY -= SECTION_GAP
            return
        }

        drawTableHeader(section.columns, widths)

        for (row in section.rows) {
            val rowHeight = BODY_SIZE + LINE_GAP
            if (cursorY - rowHeight < MARGIN) {
                breakPage()
                // O cabeçalho volta em toda página: sem ele as páginas seguintes
                // são uma grade de números sem nome de coluna.
                drawTableHeader(section.columns, widths)
            }
            cursorY -= BODY_SIZE
            drawRow(row, section.columns, widths, regular, gray = 0.1f)
            cursorY -= LINE_GAP
        }

        cursorY -= SECTION_GAP
    }

    private fun drawTableHeader(columns: List<UsageReportColumn>, widths: List<Float>) {
        cursorY -= BODY_SIZE
        drawRow(columns.map { column -> column.title }, columns, widths, bold, gray = 0.3f)
        cursorY -= 2f
        stream.setStrokingColor(0.75f, 0.75f, 0.75f)
        stream.setLineWidth(0.5f)
        stream.moveTo(MARGIN, cursorY)
        stream.lineTo(MARGIN + contentWidth, cursorY)
        stream.stroke()
        cursorY -= LINE_GAP
    }

    private fun drawRow(
        cells: List<String>,
        columns: List<UsageReportColumn>,
        widths: List<Float>,
        font: PDFont,
        gray: Float
    ) {
        var x = MARGIN
        for (index in columns.indices) {
            val width = widths[index]
            val available = width - 2 * CELL_PADDING
            val text = truncate(cells.getOrElse(index) { "" }, available, font, BODY_SIZE)
            val offset = if (columns[index].alignEnd) {
                width - CELL_PADDING - widthOf(text, font, BODY_SIZE)
            } else {
                CELL_PADDING
            }
            drawText(text, x + offset, cursorY, font, BODY_SIZE, gray)
            x += width
        }
    }

    private fun columnWidths(columns: List<UsageReportColumn>): List<Float> {
        val totalWeight = columns.sumOf { column -> column.weight.toDouble() }.toFloat()
        return columns.map { column -> contentWidth * column.weight / totalWeight }
    }

    fun grid(section: UsageReportSection.Grid) {
        val heatmap = section.heatmap
        val days = DayOfWeek.entries

        // O bloco inteiro é reservado antes do título, e não a grade depois dele:
        // reservar só a grade deixaria o título e a legenda no pé de uma página e
        // os quadrados na seguinte, órfãos um do outro.
        ensureSpace(
            HEADING_SIZE + 2 * LINE_GAP +
                (if (section.note != null) NOTE_SIZE + LINE_GAP else 0f) +
                NOTE_SIZE + 2f + GRID_CELL * days.size + SECTION_GAP
        )

        heading(section.heading)
        if (section.note != null) {
            note(section.note)
        }

        // Régua de horas por cima da grade: sem ela as colunas são 24 quadrados
        // anônimos e a resposta ("a que horas eu queimo quota?") não sai.
        cursorY -= NOTE_SIZE
        for (hour in 0 until HOURS_IN_DAY step 2) {
            val x = MARGIN + GRID_LABEL_WIDTH + hour * GRID_CELL
            drawText(hour.toString(), x, cursorY, regular, NOTE_SIZE, gray = 0.45f)
        }
        cursorY -= 2f

        for (day in days) {
            cursorY -= GRID_CELL
            drawText(dayLabel(day, language), MARGIN, cursorY + 3f, regular, NOTE_SIZE, gray = 0.35f)
            for (hour in 0 until HOURS_IN_DAY) {
                val intensity = heatmap.intensityAt(day, hour)
                // Célula sem atividade fica cinza-claro e não branca: é o contorno
                // da grade que faz as horas vazias serem lidas como vazias, e não
                // como área fora do gráfico.
                val level = 0.93f - 0.78f * intensity
                stream.setNonStrokingColor(level, level, level)
                stream.addRect(
                    MARGIN + GRID_LABEL_WIDTH + hour * GRID_CELL,
                    cursorY,
                    GRID_CELL - 1f,
                    GRID_CELL - 1f
                )
                stream.fill()
            }
        }

        cursorY -= SECTION_GAP
    }

    fun footnotes(notes: List<String>) {
        ensureSpace(NOTE_SIZE * notes.size + SECTION_GAP)
        for (text in notes) {
            // Nota longa quebra em várias linhas: cortá-la com reticências
            // esconderia justamente a ressalva que ela existe para dar.
            for (line in wrap(text, contentWidth, regular, NOTE_SIZE)) {
                ensureSpace(NOTE_SIZE + LINE_GAP)
                cursorY -= NOTE_SIZE
                drawText(line, MARGIN, cursorY, regular, NOTE_SIZE, gray = 0.45f)
                cursorY -= LINE_GAP
            }
        }
    }

    private fun wrap(text: String, maxWidth: Float, font: PDFont, size: Float): List<String> {
        val words = text.split(' ')
        val lines = mutableListOf<String>()
        var current = StringBuilder()

        for (word in words) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (widthOf(candidate, font, size) <= maxWidth) {
                current = StringBuilder(candidate)
                continue
            }
            if (current.isNotEmpty()) {
                lines += current.toString()
            }
            current = StringBuilder(word)
        }
        if (current.isNotEmpty()) {
            lines += current.toString()
        }
        return lines
    }

    /**
     * Carimba o rodapé de página em todas as folhas e fecha o fluxo.
     *
     * Só aqui porque "página 3 de 7" exige o total, que só existe depois que a
     * última página foi escrita.
     */
    fun finish() {
        stream.close()

        for (index in 0 until pdf.numberOfPages) {
            val page = pdf.getPage(index)
            PDPageContentStream(pdf, page, PDPageContentStream.AppendMode.APPEND, true).use { footer ->
                val text = ReportLabels.page(index + 1, pdf.numberOfPages, language)
                footer.beginText()
                footer.setNonStrokingColor(0.5f, 0.5f, 0.5f)
                footer.setFont(regular, NOTE_SIZE)
                footer.newLineAtOffset(PAGE_SIZE.width - MARGIN - widthOf(text, regular, NOTE_SIZE), MARGIN / 2f)
                footer.showText(text)
                footer.endText()
            }
        }
    }
}

private const val HOURS_IN_DAY = 24
private const val EMPTY_TABLE_MARK = "-"

/**
 * Abreviacao do dia no idioma do relatorio.
 *
 * Tabela explicita, e nao o nome do enum: `MONDAY.take(3)` daria "Mon" tambem em
 * portugues, e o relatorio ficaria meio traduzido.
 */
private fun dayLabel(day: DayOfWeek, language: AppLanguage): String {
    val labels = if (language == AppLanguage.PT) {
        listOf("Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom")
    } else {
        listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    }
    return labels[day.ordinal]
}
