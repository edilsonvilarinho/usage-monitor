package com.usagemonitor.domain

import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.Breadcrumb
import com.usagemonitor.domain.entity.BreadcrumbCategory
import com.usagemonitor.domain.entity.BugReportEnvelope
import com.usagemonitor.domain.entity.BugReportMachineInfo
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BugReportIssueBodyTest {

    @Test
    fun `the body carries the description, the environment and the trail`() {
        val body = envelope(
            description = "o card do Codex ficou em branco",
            breadcrumbs = trail(3)
        ).toGithubIssueBody()

        assertTrue(body.contains("## O que aconteceu"), body)
        assertTrue(body.contains("o card do Codex ficou em branco"), body)
        assertTrue(body.contains("- App: 38.0.2"), body)
        assertTrue(body.contains("## Trilha de eventos"), body)
        assertTrue(body.contains("passo 2"), body)
        // O pacote completo é o arquivo; o corpo pede o anexo, senão o relatório
        // chega pela metade e ninguém sabe que falta a outra.
        assertTrue(body.contains("Anexe aqui o arquivo"), body)
    }

    @Test
    fun `an empty description says so instead of leaving a blank section`() {
        val body = envelope(description = "   ").toGithubIssueBody()

        assertTrue(body.contains("(sem descrição)"), body)
    }

    @Test
    fun `an empty trail says so instead of an empty code block`() {
        val body = envelope(breadcrumbs = emptyList()).toGithubIssueBody()

        assertTrue(body.contains("(trilha vazia)"), body)
        assertFalse(body.contains("```"), body)
    }

    /**
     * Primeiro corte: o que explica a falha é o que aconteceu perto dela, então
     * os últimos trinta ficam e os anteriores saem.
     */
    @Test
    fun `only the last thirty steps enter the body, and the header says so`() {
        val body = envelope(breadcrumbs = trail(45)).toGithubIssueBody()

        assertTrue(body.contains("## Trilha de eventos (últimos 30 de 45)"), body)
        assertFalse(body.contains("passo 14 "), body)
        assertTrue(body.contains("passo 15 "), body)
        assertTrue(body.contains("passo 44 "), body)
    }

    @Test
    fun `a trail at the limit keeps the plain header`() {
        val body = envelope(
            breadcrumbs = trail(BugReportEnvelope.MAX_ISSUE_BODY_BREADCRUMBS)
        ).toGithubIssueBody()

        assertTrue(body.contains("## Trilha de eventos\n"), body)
        assertFalse(body.contains("últimos"), body)
    }

    /**
     * Segundo corte: passos longos podem estourar o teto mesmo dentro dos trinta.
     * Eles saem do mais antigo para o mais novo, e o texto continua íntegro.
     */
    @Test
    fun `long steps are dropped oldest-first until the body fits`() {
        val longMessage = "z".repeat(Breadcrumb.MAX_MESSAGE_LENGTH)
        val breadcrumbs = List(BugReportEnvelope.MAX_ISSUE_BODY_BREADCRUMBS) { index ->
            Breadcrumb(
                at = Instant.fromEpochMilliseconds(1_700_000_000_000L + index),
                category = BreadcrumbCategory.USE_CASE,
                message = "$index-$longMessage"
            )
        }

        val body = envelope(breadcrumbs = breadcrumbs).toGithubIssueBody()

        assertTrue(body.length <= BugReportEnvelope.MAX_ISSUE_BODY_LENGTH, body.length.toString())
        // O passo mais recente sobrevive ao corte; o mais antigo não — e o
        // cabeçalho denuncia quantos ficaram.
        assertTrue(body.contains("29-$longMessage"), body.take(200))
        // Os dois espaços vêm do separador de coluna: sem eles, "0-zzz" casaria
        // dentro de "10-zzz" e o assert passaria por acidente.
        assertFalse(body.contains("  0-$longMessage"), body.take(200))
        val shown = Regex("\\(últimos (\\d+) de 30\\)").find(body)?.groupValues?.get(1)?.toInt()
        assertTrue(shown != null && shown < BugReportEnvelope.MAX_ISSUE_BODY_BREADCRUMBS, body.take(400))
        // O corpo continua terminando no rodapé, não no meio de uma linha.
        assertTrue(body.trimEnd().endsWith("antes de publicar."), body.takeLast(200))
    }

    /**
     * Último caso: descrição sozinha maior que o teto. Cortar em silêncio faria
     * o leitor tomar meia frase por frase inteira.
     */
    @Test
    fun `an oversized description is cut with a visible notice`() {
        val body = envelope(
            description = "a".repeat(BugReportEnvelope.MAX_ISSUE_BODY_LENGTH * 2),
            breadcrumbs = trail(5)
        ).toGithubIssueBody()

        assertEquals(BugReportEnvelope.MAX_ISSUE_BODY_LENGTH, body.length)
        assertTrue(body.endsWith("_(corpo truncado; o pacote completo está no arquivo anexo)_"), body.takeLast(100))
    }

    private fun trail(size: Int) = List(size) { index ->
        Breadcrumb(
            at = Instant.fromEpochMilliseconds(1_700_000_000_000L + index * 1_000L),
            category = BreadcrumbCategory.NAVIGATION,
            message = "passo $index "
        )
    }

    private fun envelope(
        description: String = "descrição",
        breadcrumbs: List<Breadcrumb> = emptyList()
    ) = BugReportEnvelope(
        description = description,
        machineInfo = BugReportMachineInfo(
            osName = "Windows 11",
            osVersion = "10.0.26200",
            osArch = "amd64",
            javaVersion = "21.0.4",
            appVersion = "38.0.2",
            language = AppLanguage.PT,
            uiScalePercent = 115,
            screenResolution = "1920x1080",
            timeZoneId = "America/Sao_Paulo"
        ),
        capturedAt = Instant.fromEpochMilliseconds(1_700_000_005_000L),
        breadcrumbs = breadcrumbs
    )
}
