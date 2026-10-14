package com.usagemonitor.onboarding

import com.russhwolf.settings.PreferencesSettings
import com.usagemonitor.domain.entity.StartupGuideDecision
import com.usagemonitor.readOnboardingCompletedVersion
import com.usagemonitor.readStartupTipLastShownDate
import com.usagemonitor.readStartupTipNextIndex
import com.usagemonitor.readStartupTipsEnabled
import kotlinx.datetime.LocalDate
import java.util.UUID
import java.util.prefs.Preferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StartupGuideControllerTest {

    private val today = LocalDate(2026, 10, 13)

    @Test
    fun `opening the guide marks the version at once`() {
        withController { controller, settings ->
            controller.apply(StartupGuideDecision.GUIDE)

            assertTrue(controller.isGuideOpen)
            assertEquals(1, controller.guideOpenGeneration)
            assertEquals("41.11.0", readOnboardingCompletedVersion(settings))
            assertNull(controller.tipIndex)
        }
    }

    @Test
    fun `a tip marks the day and keeps the next one for tomorrow`() {
        withController { controller, settings ->
            settings.putInt("startupTipNextIndex", 2)

            controller.apply(StartupGuideDecision.TIP)

            assertEquals(2, controller.tipIndex)
            assertEquals(today, readStartupTipLastShownDate(settings))
            assertEquals(3, readStartupTipNextIndex(settings))
        }
    }

    @Test
    fun `next tip walks the ring and closing keeps the marks`() {
        withController(tipCount = 3) { controller, settings ->
            settings.putInt("startupTipNextIndex", 2)
            controller.apply(StartupGuideDecision.TIP)

            controller.nextTip()

            assertEquals(0, controller.tipIndex)
            assertEquals(1, readStartupTipNextIndex(settings))
            controller.closeTip()
            assertNull(controller.tipIndex)
            assertEquals(today, readStartupTipLastShownDate(settings))
        }
    }

    @Test
    fun `nothing opens nothing`() {
        withController { controller, settings ->
            controller.apply(StartupGuideDecision.NOTHING)

            assertFalse(controller.isGuideOpen)
            assertNull(controller.tipIndex)
            assertNull(readStartupTipLastShownDate(settings))
        }
    }

    @Test
    fun `turning tips off persists and shows in the state`() {
        withController { controller, settings ->
            controller.updateTipsEnabled(false)

            assertFalse(controller.tipsEnabled)
            assertFalse(readStartupTipsEnabled(settings))
        }
    }

    private fun withController(
        tipCount: Int = 5,
        block: (StartupGuideController, PreferencesSettings) -> Unit
    ) {
        val nodeName = "com.usagemonitor.tests.${UUID.randomUUID()}"
        val preferencesNode = Preferences.userRoot().node(nodeName)
        try {
            val settings = PreferencesSettings(preferencesNode)
            block(StartupGuideController(settings, "41.11.0", tipCount) { today }, settings)
        } finally {
            preferencesNode.removeNode()
        }
    }
}
