package com.usagemonitor

import com.usagemonitor.domain.entity.AnalysisView
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.SessionAnalysis
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import java.io.ByteArrayOutputStream

/**
 * AB15 · relatório da análise em PDF de uma página (A4 paisagem, fundo escuro do relatório do
 * Histórico): o cartão de resultado, a curva da janela, as horas e os dias, nas mesmas imagens
 * que o bot manda. Todo texto está dentro das imagens (desenhadas pelo Skia com a Plex Mono),
 * então o PDF não tem contrato de caracteres próprio — o saneamento WinAnsi do relatório do
 * Histórico não se aplica aqui.
 */
internal object TelegramAnalysisPdf {

    fun render(analysis: SessionAnalysis, language: AppLanguage): ByteArray {
        val page = PDRectangle(PDRectangle.A4.height, PDRectangle.A4.width)
        PDDocument().use { pdf ->
            val sheet = PDPage(page)
            pdf.addPage(sheet)
            PDPageContentStream(pdf, sheet).use { content ->
                content.setNonStrokingColor(BACKGROUND_RED, BACKGROUND_GREEN, BACKGROUND_BLUE)
                content.addRect(0f, 0f, page.width, page.height)
                content.fill()
                val column = (page.width - MARGIN * 3) / 2f
                val slots = listOf(
                    AnalysisView.CARD to (MARGIN to page.height - MARGIN),
                    AnalysisView.CURVE to (MARGIN * 2 + column to page.height - MARGIN),
                    AnalysisView.HOURS to (MARGIN to page.height / 2f - 4f),
                    AnalysisView.DAYS to (MARGIN * 2 + column to page.height / 2f - 4f)
                )
                for ((view, corner) in slots) {
                    val png = TelegramAnalysisRenderer.render(view, analysis, language) ?: continue
                    val image = PDImageXObject.createFromByteArray(pdf, png, view.name)
                    val height = column * image.height / image.width
                    content.drawImage(image, corner.first, corner.second - height, column, height)
                }
            }
            val output = ByteArrayOutputStream()
            pdf.save(output)
            return output.toByteArray()
        }
    }

    private const val MARGIN = 24f

    // O fundo do relatório do Histórico (#131313 do app), em 0–1 para o PDFBox.
    private const val BACKGROUND_RED = 0x13 / 255f
    private const val BACKGROUND_GREEN = 0x10 / 255f
    private const val BACKGROUND_BLUE = 0x10 / 255f
}
