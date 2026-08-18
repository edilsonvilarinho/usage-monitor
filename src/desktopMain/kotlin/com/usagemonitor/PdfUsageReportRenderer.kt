package com.usagemonitor

import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.ui.CliSessionsLabels
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
 * pronto e não decide somas nem formatação de números; aqui ficam apenas layout,
 * paginação e a paleta fixa do relatório.
 *
 * Paisagem porque as tabelas têm até nove colunas; em retrato o nome do modelo
 * sozinho já consumiria espaço demais.
 */
class PdfUsageReportRenderer(private val language: AppLanguage) {

    fun render(document: UsageReportDocument): ByteArray {
        PDDocument().use { pdf ->
            val writer = PageWriter(pdf, language)

            writer.title(document.title)
            writer.subtitle(document.subtitle)

            for ((index, section) in document.sections.withIndex()) {
                when (section) {
                    is UsageReportSection.KeyValues -> writer.keyValues(section)
                    is UsageReportSection.Table -> writer.table(section)
                    is UsageReportSection.Grid -> writer.grid(
                        section,
                        followingFootnotes = document.footnotes.takeIf { index == document.sections.lastIndex }.orEmpty()
                    )
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

private const val MARGIN = 32f
private const val CONTENT_BOTTOM = 36f
private const val HEADER_CARD_HEIGHT = 58f
private const val SECTION_HEADER_HEIGHT = 25f
private const val TABLE_HEADER_HEIGHT = 21f
private const val TABLE_ROW_HEIGHT = 16f
private const val METRIC_CARD_HEIGHT = 42f
private const val METRIC_COLUMNS = 4
private const val CARD_GAP = 6f
private const val TITLE_SIZE = 17f
private const val SUBTITLE_SIZE = 8.5f
private const val HEADING_SIZE = 11f
private const val METRIC_VALUE_SIZE = 12.5f
private const val BODY_SIZE = 8f
private const val NOTE_SIZE = 7f
private const val NOTE_LINE_HEIGHT = 10f
private const val SECTION_INNER_GAP = 6f
private const val SECTION_GAP = 13f
private const val CELL_PADDING = 4f

/** Altura da célula; a largura é calculada para ocupar toda a área útil. */
private const val GRID_CELL_HEIGHT = 13f
private const val GRID_LABEL_WIDTH = 34f
private const val GRID_CARD_PADDING = 12f

private data class ReportColor(val red: Float, val green: Float, val blue: Float)

private fun rgb(red: Int, green: Int, blue: Int): ReportColor {
    return ReportColor(red / 255f, green / 255f, blue / 255f)
}

private val BACKGROUND = rgb(0x18, 0x18, 0x18)
private val SURFACE = rgb(0x24, 0x24, 0x24)
private val SURFACE_ALTERNATE = rgb(0x20, 0x20, 0x20)
private val SURFACE_VARIANT = rgb(0x2C, 0x2C, 0x2C)
private val ON_SURFACE = rgb(0xE0, 0xE0, 0xE0)
private val ON_SURFACE_VARIANT = rgb(0xBD, 0xBD, 0xBD)
private val PRIMARY = rgb(0x82, 0xB1, 0xFF)
private val SUCCESS = rgb(0x4C, 0xAF, 0x50)
private val OUTLINE = rgb(0x3A, 0x3A, 0x3A)

/** Escreve de cima para baixo, abrindo página nova quando o espaço acaba. */
private class PageWriter(private val pdf: PDDocument, private val language: AppLanguage) {

    private val regular: PDFont = PDType1Font(Standard14Fonts.FontName.HELVETICA)
    private val bold: PDFont = PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD)
    private val contentWidth = PAGE_SIZE.width - 2 * MARGIN

    private var stream: PDPageContentStream = newPage()
    private var cursorY = PAGE_SIZE.height - MARGIN
    private var headerTop: Float? = null

    private fun newPage(): PDPageContentStream {
        val page = PDPage(PAGE_SIZE)
        pdf.addPage(page)
        val pageStream = PDPageContentStream(pdf, page)
        fillRect(pageStream, 0f, 0f, PAGE_SIZE.width, PAGE_SIZE.height, BACKGROUND)
        return pageStream
    }

    private fun breakPage() {
        stream.close()
        stream = newPage()
        cursorY = PAGE_SIZE.height - MARGIN
        headerTop = null
    }

    private fun ensureSpace(height: Float) {
        if (cursorY - height >= CONTENT_BOTTOM) {
            return
        }
        breakPage()
    }

    private fun fillRect(
        target: PDPageContentStream,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        color: ReportColor
    ) {
        target.setNonStrokingColor(color.red, color.green, color.blue)
        target.addRect(x, y, width, height)
        target.fill()
    }

    private fun drawLine(
        target: PDPageContentStream,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        color: ReportColor,
        width: Float = 0.5f
    ) {
        target.setStrokingColor(color.red, color.green, color.blue)
        target.setLineWidth(width)
        target.moveTo(x1, y1)
        target.lineTo(x2, y2)
        target.stroke()
    }

    private fun drawText(
        text: String,
        x: Float,
        y: Float,
        font: PDFont,
        size: Float,
        color: ReportColor = ON_SURFACE
    ) {
        stream.beginText()
        stream.setNonStrokingColor(color.red, color.green, color.blue)
        stream.setFont(font, size)
        stream.newLineAtOffset(x, y)
        stream.showText(text)
        stream.endText()
    }

    private fun widthOf(text: String, font: PDFont, size: Float): Float {
        return font.getStringWidth(text) / 1000f * size
    }

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
        ensureSpace(HEADER_CARD_HEIGHT)
        val top = cursorY
        val bottom = top - HEADER_CARD_HEIGHT
        fillRect(stream, MARGIN, bottom, contentWidth, HEADER_CARD_HEIGHT, SURFACE)
        fillRect(stream, MARGIN, bottom, 4f, HEADER_CARD_HEIGHT, PRIMARY)
        drawText(truncate(text, contentWidth - 32f, bold, TITLE_SIZE), MARGIN + 16f, top - 25f, bold, TITLE_SIZE)
        headerTop = top
    }

    fun subtitle(text: String) {
        val top = headerTop ?: cursorY
        drawText(
            truncate(text, contentWidth - 32f, regular, SUBTITLE_SIZE),
            MARGIN + 16f,
            top - 43f,
            regular,
            SUBTITLE_SIZE,
            ON_SURFACE_VARIANT
        )
        cursorY = top - HEADER_CARD_HEIGHT - SECTION_GAP
        headerTop = null
    }

    private fun heading(text: String, continuation: Boolean = false) {
        ensureSpace(SECTION_HEADER_HEIGHT + TABLE_HEADER_HEIGHT + TABLE_ROW_HEIGHT)
        val label = if (continuation) {
            "$text - ${ReportLabels.continued(language)}"
        } else {
            text
        }
        val bottom = cursorY - SECTION_HEADER_HEIGHT
        fillRect(stream, MARGIN, bottom, contentWidth, SECTION_HEADER_HEIGHT, SURFACE_VARIANT)
        fillRect(stream, MARGIN, bottom, 4f, SECTION_HEADER_HEIGHT, PRIMARY)
        drawText(
            truncate(label, contentWidth - 24f, bold, HEADING_SIZE),
            MARGIN + 12f,
            bottom + 8f,
            bold,
            HEADING_SIZE
        )
        cursorY = bottom - SECTION_INNER_GAP
    }

    private fun note(text: String) {
        val lines = wrap(text, contentWidth, regular, NOTE_SIZE)
        ensureSpace(lines.size * NOTE_LINE_HEIGHT)
        for (line in lines) {
            cursorY -= NOTE_SIZE
            drawText(line, MARGIN, cursorY, regular, NOTE_SIZE, ON_SURFACE_VARIANT)
            cursorY -= NOTE_LINE_HEIGHT - NOTE_SIZE
        }
    }

    fun keyValues(section: UsageReportSection.KeyValues) {
        val rows = (section.entries.size + METRIC_COLUMNS - 1) / METRIC_COLUMNS
        ensureSpace(
            SECTION_HEADER_HEIGHT + SECTION_INNER_GAP +
                rows * METRIC_CARD_HEIGHT + (rows - 1).coerceAtLeast(0) * CARD_GAP + SECTION_GAP
        )
        heading(section.heading)

        val cardWidth = (contentWidth - (METRIC_COLUMNS - 1) * CARD_GAP) / METRIC_COLUMNS
        for (row in 0 until rows) {
            val bottom = cursorY - METRIC_CARD_HEIGHT
            for (column in 0 until METRIC_COLUMNS) {
                val entry = section.entries.getOrNull(row * METRIC_COLUMNS + column) ?: continue
                val x = MARGIN + column * (cardWidth + CARD_GAP)
                fillRect(stream, x, bottom, cardWidth, METRIC_CARD_HEIGHT, SURFACE)
                drawText(
                    truncate(entry.label, cardWidth - 20f, regular, NOTE_SIZE),
                    x + 10f,
                    bottom + 26f,
                    regular,
                    NOTE_SIZE,
                    ON_SURFACE_VARIANT
                )
                drawText(
                    truncate(entry.value, cardWidth - 20f, bold, METRIC_VALUE_SIZE),
                    x + 10f,
                    bottom + 10f,
                    bold,
                    METRIC_VALUE_SIZE,
                    metricColor(entry.label)
                )
            }
            cursorY = bottom - CARD_GAP
        }
        cursorY -= SECTION_GAP - CARD_GAP
    }

    fun table(section: UsageReportSection.Table) {
        val noteHeight = section.note?.let { text -> wrap(text, contentWidth, regular, NOTE_SIZE).size * NOTE_LINE_HEIGHT }
            ?: 0f
        ensureSpace(
            SECTION_HEADER_HEIGHT + SECTION_INNER_GAP + noteHeight +
                TABLE_HEADER_HEIGHT + TABLE_ROW_HEIGHT
        )
        heading(section.heading)
        if (section.note != null) {
            note(section.note)
        }

        val widths = columnWidths(section.columns)
        if (section.rows.isEmpty()) {
            val bottom = cursorY - TABLE_ROW_HEIGHT
            fillRect(stream, MARGIN, bottom, contentWidth, TABLE_ROW_HEIGHT, SURFACE)
            drawText(EMPTY_TABLE_MARK, MARGIN + CELL_PADDING, bottom + 5f, regular, BODY_SIZE, ON_SURFACE_VARIANT)
            cursorY = bottom - SECTION_GAP
            return
        }

        drawTableHeader(section.columns, widths)
        for ((index, row) in section.rows.withIndex()) {
            if (cursorY - TABLE_ROW_HEIGHT < CONTENT_BOTTOM) {
                breakPage()
                heading(section.heading, continuation = true)
                drawTableHeader(section.columns, widths)
            }
            drawTableRow(row, section.columns, widths, index)
        }
        cursorY -= SECTION_GAP
    }

    private fun drawTableHeader(columns: List<UsageReportColumn>, widths: List<Float>) {
        val bottom = cursorY - TABLE_HEADER_HEIGHT
        fillRect(stream, MARGIN, bottom, contentWidth, TABLE_HEADER_HEIGHT, SURFACE_VARIANT)
        drawRow(
            cells = columns.map { column -> column.title },
            columns = columns,
            widths = widths,
            y = bottom + 7f,
            font = bold,
            size = BODY_SIZE,
            color = ON_SURFACE,
            accentMetrics = false
        )
        drawLine(stream, MARGIN, bottom, MARGIN + contentWidth, bottom, OUTLINE)
        cursorY = bottom
    }

    private fun drawTableRow(
        cells: List<String>,
        columns: List<UsageReportColumn>,
        widths: List<Float>,
        index: Int
    ) {
        val bottom = cursorY - TABLE_ROW_HEIGHT
        val background = if (index % 2 == 0) SURFACE else SURFACE_ALTERNATE
        fillRect(stream, MARGIN, bottom, contentWidth, TABLE_ROW_HEIGHT, background)
        drawRow(
            cells = cells,
            columns = columns,
            widths = widths,
            y = bottom + 5f,
            font = regular,
            size = BODY_SIZE,
            color = ON_SURFACE,
            accentMetrics = true
        )
        drawLine(stream, MARGIN, bottom, MARGIN + contentWidth, bottom, OUTLINE, width = 0.25f)
        cursorY = bottom
    }

    private fun drawRow(
        cells: List<String>,
        columns: List<UsageReportColumn>,
        widths: List<Float>,
        y: Float,
        font: PDFont,
        size: Float,
        color: ReportColor,
        accentMetrics: Boolean
    ) {
        var x = MARGIN
        for (index in columns.indices) {
            val width = widths[index]
            val available = width - 2 * CELL_PADDING
            val text = truncate(cells.getOrElse(index) { "" }, available, font, size)
            val offset = if (columns[index].alignEnd) {
                width - CELL_PADDING - widthOf(text, font, size)
            } else {
                CELL_PADDING
            }
            val cellColor = if (accentMetrics) metricColor(columns[index].title) else color
            drawText(text, x + offset, y, font, size, cellColor)
            x += width
        }
    }

    private fun metricColor(label: String): ReportColor {
        return when (label) {
            CliSessionsLabels.activeTime(language) -> SUCCESS
            CliSessionsLabels.columnCost(language) -> PRIMARY
            else -> ON_SURFACE
        }
    }

    private fun columnWidths(columns: List<UsageReportColumn>): List<Float> {
        val totalWeight = columns.sumOf { column -> column.weight.toDouble() }.toFloat()
        return columns.map { column -> contentWidth * column.weight / totalWeight }
    }

    fun grid(section: UsageReportSection.Grid, followingFootnotes: List<String> = emptyList()) {
        val days = DayOfWeek.entries
        val noteHeight = section.note?.let { text -> wrap(text, contentWidth, regular, NOTE_SIZE).size * NOTE_LINE_HEIGHT }
            ?: 0f
        val gridCardHeight = 35f + GRID_CELL_HEIGHT * days.size
        ensureSpace(
            SECTION_HEADER_HEIGHT + SECTION_INNER_GAP + noteHeight + gridCardHeight + SECTION_GAP +
                footnotesHeight(followingFootnotes)
        )
        heading(section.heading)
        if (section.note != null) {
            note(section.note)
        }

        val cardTop = cursorY
        val cardBottom = cardTop - gridCardHeight
        fillRect(stream, MARGIN, cardBottom, contentWidth, gridCardHeight, SURFACE)

        val gridOriginX = MARGIN + GRID_CARD_PADDING + GRID_LABEL_WIDTH
        val gridCellWidth =
            (contentWidth - 2 * GRID_CARD_PADDING - GRID_LABEL_WIDTH) / HOURS_IN_DAY
        val hourBaseline = cardTop - 16f
        for (hour in 0 until HOURS_IN_DAY step 2) {
            drawText(
                hour.toString(),
                gridOriginX + hour * gridCellWidth,
                hourBaseline,
                regular,
                NOTE_SIZE,
                ON_SURFACE_VARIANT
            )
        }

        val gridTop = cardTop - 27f
        for ((dayIndex, day) in days.withIndex()) {
            val cellY = gridTop - (dayIndex + 1) * GRID_CELL_HEIGHT
            drawText(
                dayLabel(day, language),
                MARGIN + GRID_CARD_PADDING,
                cellY + 3f,
                regular,
                NOTE_SIZE,
                ON_SURFACE_VARIANT
            )
            for (hour in 0 until HOURS_IN_DAY) {
                val intensity = section.heatmap.intensityAt(day, hour)
                fillRect(
                    stream,
                    gridOriginX + hour * gridCellWidth,
                    cellY,
                    gridCellWidth - 1f,
                    GRID_CELL_HEIGHT - 1f,
                    mix(SURFACE_VARIANT, PRIMARY, intensity)
                )
            }
        }
        cursorY = cardBottom - SECTION_GAP
    }

    fun footnotes(notes: List<String>) {
        for (text in notes) {
            val lines = wrap(text, contentWidth - 24f, regular, NOTE_SIZE)
            val cardHeight = 10f + lines.size * NOTE_LINE_HEIGHT
            ensureSpace(cardHeight)
            val bottom = cursorY - cardHeight
            fillRect(stream, MARGIN, bottom, contentWidth, cardHeight, SURFACE)
            fillRect(stream, MARGIN, bottom, 3f, cardHeight, OUTLINE)
            var textY = cursorY - 10f
            for (line in lines) {
                drawText(line, MARGIN + 12f, textY, regular, NOTE_SIZE, ON_SURFACE_VARIANT)
                textY -= NOTE_LINE_HEIGHT
            }
            cursorY = bottom - 4f
        }
    }

    private fun footnotesHeight(notes: List<String>): Float {
        return notes.sumOf { text ->
            val lineCount = wrap(text, contentWidth - 24f, regular, NOTE_SIZE).size
            (10f + lineCount * NOTE_LINE_HEIGHT + 4f).toDouble()
        }.toFloat()
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

    /** Carimba o rodapé em todas as folhas e fecha o fluxo principal. */
    fun finish() {
        stream.close()

        for (index in 0 until pdf.numberOfPages) {
            val page = pdf.getPage(index)
            PDPageContentStream(pdf, page, PDPageContentStream.AppendMode.APPEND, true).use { footer ->
                val text = ReportLabels.page(index + 1, pdf.numberOfPages, language)
                drawLine(footer, MARGIN, 27f, PAGE_SIZE.width - MARGIN, 27f, OUTLINE)
                footer.beginText()
                footer.setNonStrokingColor(
                    ON_SURFACE_VARIANT.red,
                    ON_SURFACE_VARIANT.green,
                    ON_SURFACE_VARIANT.blue
                )
                footer.setFont(regular, NOTE_SIZE)
                footer.newLineAtOffset(PAGE_SIZE.width - MARGIN - widthOf(text, regular, NOTE_SIZE), 15f)
                footer.showText(text)
                footer.endText()
            }
        }
    }
}

private const val HOURS_IN_DAY = 24
private const val EMPTY_TABLE_MARK = "-"

private fun mix(start: ReportColor, end: ReportColor, ratio: Float): ReportColor {
    val bounded = ratio.coerceIn(0f, 1f)
    return ReportColor(
        red = start.red + (end.red - start.red) * bounded,
        green = start.green + (end.green - start.green) * bounded,
        blue = start.blue + (end.blue - start.blue) * bounded
    )
}

/** Abreviação do dia no idioma do relatório. */
private fun dayLabel(day: DayOfWeek, language: AppLanguage): String {
    val labels = if (language == AppLanguage.PT) {
        listOf("Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom")
    } else {
        listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    }
    return labels[day.ordinal]
}
