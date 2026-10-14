package com.usagemonitor.domain

import com.usagemonitor.domain.entity.StartupGuideDecision
import com.usagemonitor.domain.entity.nextStartupTipIndex
import com.usagemonitor.domain.entity.startupGuideDecision
import com.usagemonitor.domain.entity.startupTipIndex
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class StartupGuideTest {

    private val today = LocalDate(2026, 10, 13)
    private val yesterday = LocalDate(2026, 10, 12)

    private fun decide(
        seen: String? = "41.10.0",
        onboarding: String? = "41.10.0",
        receipt: Boolean = false,
        tipsEnabled: Boolean = true,
        lastTip: LocalDate? = yesterday,
        current: String = "41.10.0"
    ) = startupGuideDecision(
        currentVersion = current,
        releaseNotesSeenVersion = seen,
        onboardingCompletedVersion = onboarding,
        hasUpdateReceipt = receipt,
        tipsEnabled = tipsEnabled,
        lastTipDate = lastTip,
        today = today
    )

    @Test
    fun `fresh install without receipt opens the guide`() {
        // macOS e instalação manual: nunca há recibo, e a primeira abertura é esta.
        assertEquals(StartupGuideDecision.GUIDE, decide(seen = null, onboarding = null, lastTip = null))
    }

    @Test
    fun `fresh install opens the guide even with tips off`() {
        assertEquals(StartupGuideDecision.GUIDE, decide(seen = null, onboarding = null, tipsEnabled = false))
    }

    @Test
    fun `update with receipt and no marks shows the release notes, not the guide`() {
        // Windows pelo instalador e Linux pelo atualizador: o recibo prova a
        // atualização, e as Novidades ocupam a abertura.
        assertEquals(StartupGuideDecision.NOTHING, decide(seen = null, onboarding = null, receipt = true))
    }

    @Test
    fun `newer version than the seen one leaves the opening to the release notes`() {
        assertEquals(StartupGuideDecision.NOTHING, decide(seen = "41.9.0", current = "41.10.0"))
    }

    @Test
    fun `guide already completed falls back to the tip`() {
        assertEquals(StartupGuideDecision.TIP, decide(seen = null, onboarding = "41.10.0"))
    }

    @Test
    fun `same version opening shows the tip once a day`() {
        assertEquals(StartupGuideDecision.TIP, decide(lastTip = yesterday))
        assertEquals(StartupGuideDecision.NOTHING, decide(lastTip = today))
    }

    @Test
    fun `tip never shown before shows today`() {
        assertEquals(StartupGuideDecision.TIP, decide(lastTip = null))
    }

    @Test
    fun `tips off show nothing on a normal opening`() {
        assertEquals(StartupGuideDecision.NOTHING, decide(tipsEnabled = false))
    }

    @Test
    fun `a date in the future still differs from today and shows the tip`() {
        // Relógio que voltou: a dica não fica presa até a data gravada chegar.
        assertEquals(StartupGuideDecision.TIP, decide(lastTip = LocalDate(2026, 10, 20)))
    }

    @Test
    fun `stored tip index outside the list restarts at zero`() {
        assertEquals(0, startupTipIndex(storedIndex = null, tipCount = 5))
        assertEquals(0, startupTipIndex(storedIndex = 7, tipCount = 5))
        assertEquals(0, startupTipIndex(storedIndex = -1, tipCount = 5))
        assertEquals(3, startupTipIndex(storedIndex = 3, tipCount = 5))
    }

    @Test
    fun `next tip wraps after the last one`() {
        assertEquals(1, nextStartupTipIndex(currentIndex = 0, tipCount = 5))
        assertEquals(0, nextStartupTipIndex(currentIndex = 4, tipCount = 5))
        assertEquals(0, nextStartupTipIndex(currentIndex = 0, tipCount = 0))
    }
}
