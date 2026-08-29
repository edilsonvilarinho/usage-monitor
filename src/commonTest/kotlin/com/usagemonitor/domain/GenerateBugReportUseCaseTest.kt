package com.usagemonitor.domain

import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.Breadcrumb
import com.usagemonitor.domain.entity.BreadcrumbCategory
import com.usagemonitor.domain.entity.BugReportMachineInfo
import com.usagemonitor.domain.repository.BreadcrumbRecorder
import com.usagemonitor.domain.usecase.GenerateBugReportUseCase
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

private class StubRecorder(private val trail: List<Breadcrumb>) : BreadcrumbRecorder {
    var requestedLimit: Int? = null

    override fun record(category: BreadcrumbCategory, message: String) = Unit

    override fun read(limit: Int): List<Breadcrumb> {
        requestedLimit = limit
        return trail
    }
}

private class FixedClock(private val instant: Instant) : Clock {
    override fun now(): Instant = instant
}

class GenerateBugReportUseCaseTest {

    private val trail = listOf(
        Breadcrumb(
            at = Instant.fromEpochMilliseconds(1_700_000_000_000L),
            category = BreadcrumbCategory.NAVIGATION,
            message = "abriu as Configurações"
        )
    )

    @Test
    fun `the envelope carries the description, the machine, the clock and the trail`() {
        val recorder = StubRecorder(trail)
        val useCase = GenerateBugReportUseCase(
            breadcrumbs = recorder,
            machineInfo = { machineInfo() },
            breadcrumbLimit = 200,
            clock = FixedClock(Instant.fromEpochMilliseconds(1_700_000_060_000L))
        )

        val envelope = useCase("  o card do Codex ficou em branco  ")

        assertEquals("o card do Codex ficou em branco", envelope.description)
        assertEquals("38.0.2", envelope.machineInfo.appVersion)
        assertEquals(Instant.fromEpochMilliseconds(1_700_000_060_000L), envelope.capturedAt)
        assertEquals(trail, envelope.breadcrumbs)
        assertEquals(200, recorder.requestedLimit)
    }

    /**
     * Idioma e escala mudam nas Configurações enquanto o app roda: capturá-los na
     * construção faria o relatório descrever um estado que já não é o do momento
     * da falha.
     */
    @Test
    fun `the machine info is read at report time, not at construction`() {
        var scale = 100
        val useCase = GenerateBugReportUseCase(
            breadcrumbs = StubRecorder(emptyList()),
            machineInfo = { machineInfo(uiScalePercent = scale) },
            breadcrumbLimit = 30
        )

        assertEquals(100, useCase("antes").machineInfo.uiScalePercent)
        scale = 150
        assertEquals(150, useCase("depois").machineInfo.uiScalePercent)
    }

    private fun machineInfo(uiScalePercent: Int = 115) = BugReportMachineInfo(
        osName = "Windows 11",
        osVersion = "10.0.26200",
        osArch = "amd64",
        javaVersion = "21.0.4",
        appVersion = "38.0.2",
        language = AppLanguage.PT,
        uiScalePercent = uiScalePercent
    )
}
