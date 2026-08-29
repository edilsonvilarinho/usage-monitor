package com.usagemonitor.domain

import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.BugReportMachineInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BugReportMachineInfoTest {

    @Test
    fun `the summary lists what identifies the build, not who is running it`() {
        val lines = fullInfo().summaryLines()

        assertEquals(
            listOf(
                "App: 38.0.2",
                "OS: Windows 11 10.0.26200 (amd64)",
                "JVM: 21.0.4",
                "Idioma: PT",
                "Escala da interface: 115%",
                "Resolução: 1920x1080",
                "Fuso: America/Sao_Paulo"
            ),
            lines
        )
    }

    /**
     * O pacote vira o corpo de uma issue pública: hostname e usuário do sistema
     * identificam a pessoa sem ajudar a diagnosticar o app, e a única forma de
     * eles entrarem seria alguém acrescentá-los aqui.
     */
    @Test
    fun `no line carries a hostname or a system user`() {
        val joined = fullInfo().summaryLines().joinToString(separator = "\n").lowercase()

        assertFalse(joined.contains("host"), joined)
        assertFalse(joined.contains("usuário"), joined)
        assertFalse(joined.contains("user"), joined)
    }

    /**
     * Nulo é "não medido". A linha some em vez de afirmar "desconhecida", que
     * ocuparia espaço sem informar.
     */
    @Test
    fun `unmeasured fields drop their line instead of printing null`() {
        val lines = fullInfo().copy(screenResolution = null, timeZoneId = null).summaryLines()

        assertEquals(5, lines.size)
        assertTrue(lines.none { line -> line.contains("null") }, lines.toString())
        assertTrue(lines.none { line -> line.startsWith("Resolução") }, lines.toString())
        assertTrue(lines.none { line -> line.startsWith("Fuso") }, lines.toString())
    }

    private fun fullInfo() = BugReportMachineInfo(
        osName = "Windows 11",
        osVersion = "10.0.26200",
        osArch = "amd64",
        javaVersion = "21.0.4",
        appVersion = "38.0.2",
        language = AppLanguage.PT,
        uiScalePercent = 115,
        screenResolution = "1920x1080",
        timeZoneId = "America/Sao_Paulo"
    )
}
