package com.usagemonitor.data

import com.usagemonitor.PdfUsageReportRenderer
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.CliActivityCell
import com.usagemonitor.domain.entity.CliActivityHeatmap
import com.usagemonitor.presentation.ui.report.UsageReportColumn
import com.usagemonitor.presentation.ui.report.UsageReportDocument
import com.usagemonitor.presentation.ui.report.UsageReportEntry
import com.usagemonitor.presentation.ui.report.UsageReportSection
import kotlinx.datetime.DayOfWeek
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PdfUsageReportRendererTest {

    /**
     * O PDF gerado é relido com o próprio PDFBox: afirmar que os bytes existem não
     * diria nada sobre o arquivo abrir num leitor.
     */
    @Test
    fun `o pdf gerado e legivel e contem o conteudo do documento`() {
        val bytes = PdfUsageReportRenderer(AppLanguage.PT).render(sampleDocument())

        Loader.loadPDF(bytes).use { pdf ->
            val text = PDFTextStripper().getText(pdf)

            assertTrue(text.contains("Relatório de uso"), text)
            assertTrue(text.contains("Pessoal"), text)
            assertTrue(text.contains("usage-monitor"), text)
            assertTrue(text.contains("US$ 12.40"), text)
            assertTrue(text.contains("Página 1 de"), text)
        }
    }

    /**
     * Tabela longa tem de virar páginas com o cabeçalho repetido: sem ele, da
     * segunda página em diante o relatório é uma grade de números sem nome.
     */
    @Test
    fun `tabela longa quebra em paginas repetindo o cabecalho`() {
        val rows = (1..200).map { index ->
            listOf("projeto-$index", index.toString(), "US$ 1.00")
        }
        val document = UsageReportDocument(
            title = "Relatório",
            subtitle = "teste",
            sections = listOf(
                UsageReportSection.Table(
                    heading = "Projetos",
                    columns = listOf(
                        UsageReportColumn("Projeto"),
                        UsageReportColumn("Turnos", alignEnd = true),
                        UsageReportColumn("Custo", alignEnd = true)
                    ),
                    rows = rows
                )
            )
        )

        val bytes = PdfUsageReportRenderer(AppLanguage.PT).render(document)

        Loader.loadPDF(bytes).use { pdf ->
            assertTrue(pdf.numberOfPages > 1, "esperava mais de uma página")

            val stripper = PDFTextStripper()
            stripper.startPage = 2
            stripper.endPage = 2
            assertTrue(stripper.getText(pdf).contains("Projeto"), "cabeçalho não voltou na página 2")

            // A última página numera o total, que só existe depois de todas escritas.
            val last = PDFTextStripper()
            last.startPage = pdf.numberOfPages
            last.endPage = pdf.numberOfPages
            assertTrue(last.getText(pdf).contains("de ${pdf.numberOfPages}"))
        }
    }

    /** Documento sem seção nenhuma ainda tem de produzir um PDF de uma página. */
    @Test
    fun `documento minimo gera uma pagina`() {
        val bytes = PdfUsageReportRenderer(AppLanguage.EN)
            .render(UsageReportDocument(title = "Report", subtitle = "-", sections = emptyList()))

        Loader.loadPDF(bytes).use { pdf ->
            assertEquals(1, pdf.numberOfPages)
        }
    }
}

private fun sampleDocument(): UsageReportDocument {
    return UsageReportDocument(
        title = "Relatório de uso — Sessões CLI",
        subtitle = "Pessoal · Últimas 5h · gerado em 2026-08-17 15:30 BRT",
        sections = listOf(
            UsageReportSection.KeyValues(
                heading = "Totais",
                entries = listOf(
                    UsageReportEntry("Sessões", "2"),
                    UsageReportEntry("Custo estimado", "US$ 12.40")
                )
            ),
            UsageReportSection.Table(
                heading = "Projeto",
                columns = listOf(
                    UsageReportColumn("Projeto", weight = 2f),
                    UsageReportColumn("Tempo ativo", alignEnd = true)
                ),
                rows = listOf(listOf("usage-monitor", "4h35")),
                note = "Os eixos descrevem os mesmos turnos."
            ),
            UsageReportSection.Grid(
                heading = "Atividade",
                heatmap = CliActivityHeatmap(
                    cells = listOf(CliActivityCell(DayOfWeek.MONDAY, hour = 14, turnCount = 3, costMicros = 900L))
                )
            )
        ),
        footnotes = listOf("Tempo ativo não é duração.")
    ).sanitized()
}
