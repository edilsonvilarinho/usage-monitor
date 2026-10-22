package com.usagemonitor.domain

import com.usagemonitor.domain.entity.AlertDestination
import com.usagemonitor.domain.entity.AlertLogKind
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.CollectorKind
import com.usagemonitor.domain.entity.TelegramBotSettings
import com.usagemonitor.domain.entity.TelegramChat
import com.usagemonitor.domain.entity.UsageAlert
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.domain.entity.alertDestinationsFor
import com.usagemonitor.domain.entity.alertLogKindOf
import com.usagemonitor.domain.entity.alertLogPercentOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/** O registro dos avisos (#440, 5.2): o tipo, o percentual e para onde o aviso foi. */
class AlertLogEntryTest {

    private val target = UsageTargetKey(ApiSource.ANTHROPIC, "default")
    private val at = Instant.parse("2026-10-22T16:51:00Z")

    private fun threshold(percent: Int) = UsageAlert.QuotaThreshold(target, "Claude Team", "Claude 5h", 90, percent, at, true)

    @Test
    fun `the kind follows the single critical rule, and only quota alerts carry a percent`() {
        assertEquals(AlertLogKind.CRITICAL, alertLogKindOf(threshold(91)))
        assertEquals(AlertLogKind.WARNING, alertLogKindOf(threshold(75)))
        assertEquals(AlertLogKind.RESET, alertLogKindOf(UsageAlert.QuotaReset(target, "Claude Team", "Claude 7d", at)))
        assertEquals(AlertLogKind.SESSION, alertLogKindOf(UsageAlert.SessionSaturated("s1", "app")))
        assertEquals(91, alertLogPercentOf(threshold(91)))
        assertEquals(null, alertLogPercentOf(UsageAlert.SessionSaturated("s1", "app")))
    }

    @Test
    fun `the tray is only in the app, and Telegram only with a paired chat`() {
        val paired = TelegramBotSettings(enabled = true, botToken = "t", authorizedChats = listOf(TelegramChat(1L, "@ed")))
        assertEquals(listOf(AlertDestination.TRAY, AlertDestination.TELEGRAM), alertDestinationsFor(CollectorKind.APP, paired))
        assertEquals(listOf(AlertDestination.TELEGRAM), alertDestinationsFor(CollectorKind.SERVICE, paired))
        assertEquals(emptyList(), alertDestinationsFor(CollectorKind.SERVICE, TelegramBotSettings(enabled = true, botToken = "t")))
        assertEquals(emptyList(), alertDestinationsFor(CollectorKind.SERVICE, TelegramBotSettings(enabled = false, botToken = "t", authorizedChats = paired.authorizedChats)))
    }
}
