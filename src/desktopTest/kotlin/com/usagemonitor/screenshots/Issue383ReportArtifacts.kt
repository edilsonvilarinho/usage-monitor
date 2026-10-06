package com.usagemonitor.screenshots

import com.usagemonitor.PdfUsageReportRenderer
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.ui.report.reportForHistory
import com.usagemonitor.presentation.viewmodel.HistoryUiState
import org.apache.pdfbox.Loader
import org.apache.pdfbox.rendering.PDFRenderer
import org.apache.pdfbox.text.PDFTextStripper
import java.io.File
import javax.imageio.ImageIO

/** Relatórios sintéticos e páginas reais para inspeção; nenhuma leitura de dados do usuário. */
internal fun generateIssue383Reports(outputDir: File) {
    for (report in issue383HistoryReports()) {
        val snapshot = report.copy(rangeStartsAt = report.range.windowStart(ScreenshotFixtures.NOW), rangeEndsAt = ScreenshotFixtures.NOW)
        val state = HistoryUiState.Success(listOf(report.source), report.source, report.range, snapshot, selectedAccount = report.accountContext)
        for (language in listOf(AppLanguage.PT, AppLanguage.EN)) {
            val name = "history-${report.source.name.lowercase()}-${language.name.lowercase()}"
            val bytes = PdfUsageReportRenderer(language).render(reportForHistory(state, language, ScreenshotFixtures.NOW))
            File(outputDir, "$name.pdf").writeBytes(bytes)
            Loader.loadPDF(bytes).use { pdf ->
                File(outputDir, "$name.txt").writeText(PDFTextStripper().getText(pdf))
                val renderer = PDFRenderer(pdf)
                for (page in 0 until pdf.numberOfPages) {
                    ImageIO.write(renderer.renderImageWithDPI(page, 96f), "png", File(outputDir, "$name-page${page + 1}.png"))
                }
                println("  $name.pdf (${pdf.numberOfPages} páginas)")
            }
        }
    }
}
