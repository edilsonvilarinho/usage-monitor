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

class BugReportEnvelopeJsonTest {

    @Test
    fun `the package carries the description, the machine and every step`() {
        val json = envelope(
            description = "o card do Codex ficou em branco",
            breadcrumbs = listOf(
                Breadcrumb(
                    at = Instant.fromEpochMilliseconds(1_700_000_000_000L),
                    category = BreadcrumbCategory.NAVIGATION,
                    message = "abriu as Configurações"
                ),
                Breadcrumb(
                    at = Instant.fromEpochMilliseconds(1_700_000_001_000L),
                    category = BreadcrumbCategory.API_CALL,
                    message = "Codex: falhou"
                )
            )
        ).toJson()

        assertTrue(json.contains("\"description\": \"o card do Codex ficou em branco\""), json)
        assertTrue(json.contains("\"app\": \"38.0.2\""), json)
        assertTrue(json.contains("\"uiScalePercent\": 115"), json)
        assertTrue(json.contains("\"capturedAt\": \"2023-11-14T22:13:25Z\""), json)
        assertTrue(json.contains("\"category\": \"navigation\""), json)
        assertTrue(json.contains("\"category\": \"api-call\""), json)
        assertTrue(json.contains("\"message\": \"Codex: falhou\""), json)
    }

    /**
     * O campo não medido vira `null` de JSON, não a string "null": quem lê o
     * arquivo do outro lado precisa distinguir os dois.
     */
    @Test
    fun `an unmeasured machine field is a json null, not the text null`() {
        val json = envelope(screenResolution = null, timeZoneId = null).toJson()

        assertTrue(json.contains("\"screenResolution\": null"), json)
        assertTrue(json.contains("\"timeZone\": null"), json)
        assertFalse(json.contains("\"screenResolution\": \"null\""), json)
    }

    /**
     * A descrição é texto colado pelo usuário. Um caractere de controle ou uma
     * aspa no meio dela produziria um arquivo que nenhum parser abre — a pior
     * forma de perder um relatório, porque a falha só aparece do outro lado.
     */
    @Test
    fun `quotes, backslashes and control characters are escaped`() {
        val json = envelope(description = "a \"barra\" C:\\temp\nlinha\u0001").toJson()

        assertTrue(json.contains("\\\"barra\\\""), json)
        assertTrue(json.contains("""C:\\temp"""), json)
        assertTrue(json.contains("""linha\u0001"""), json)
        assertTrue(json.contains("""\n"""), json)
    }

    @Test
    fun `an empty trail still produces a valid array`() {
        val json = envelope(breadcrumbs = emptyList()).toJson()

        assertTrue(json.contains("\"breadcrumbs\": []"), json)
        assertEquals(1, json.count { character -> character == '[' })
    }

    /**
     * Balanceamento de chaves: o documento é montado à mão, e um `append`
     * esquecido produz um arquivo que só falha quando alguém tenta abri-lo.
     */
    @Test
    fun `braces and brackets are balanced`() {
        val json = envelope(
            breadcrumbs = List(3) { index ->
                Breadcrumb(
                    at = Instant.fromEpochMilliseconds(1_700_000_000_000L + index),
                    category = BreadcrumbCategory.USE_CASE,
                    message = "passo $index"
                )
            }
        ).toJson()

        assertEquals(json.count { it == '{' }, json.count { it == '}' })
        assertEquals(json.count { it == '[' }, json.count { it == ']' })
        // Vírgula sobrando antes do fecho é o defeito clássico de serializador
        // escrito à mão, e nenhum parser tolera.
        assertFalse(json.contains(",\n  ]"), json)
        assertFalse(json.contains(",\n  }"), json)
    }

    private fun envelope(
        description: String = "descrição",
        breadcrumbs: List<Breadcrumb> = emptyList(),
        screenResolution: String? = "1920x1080",
        timeZoneId: String? = "America/Sao_Paulo"
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
            screenResolution = screenResolution,
            timeZoneId = timeZoneId
        ),
        capturedAt = Instant.fromEpochMilliseconds(1_700_000_005_000L),
        breadcrumbs = breadcrumbs
    )
}
