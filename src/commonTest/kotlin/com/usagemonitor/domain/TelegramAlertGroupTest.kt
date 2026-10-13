package com.usagemonitor.domain

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.UsageAlert
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.domain.entity.alertGroupKey
import com.usagemonitor.domain.entity.batchAlertItems
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.time.Instant

/** #408, AA1: o que conta como "o mesmo alerta" para o bot agrupar. */
class TelegramAlertGroupTest {

    private fun quota(threshold: Int, actual: Int = threshold) = UsageAlert.QuotaThreshold(
        target = UsageTargetKey(ApiSource.CODEX),
        targetLabel = "Codex",
        quotaLabel = "Semanal",
        thresholdPercent = threshold,
        actualPercent = actual,
        periodEndAt = Instant.parse("2026-10-09T15:00:00Z"),
        hasKnownResetAt = true
    )

    @Test
    fun `sessions of the same project share the key and other projects do not`() {
        val first = alertGroupKey(UsageAlert.SessionSaturated("s1", "alem-das-sombras"))
        assertEquals(first, alertGroupKey(UsageAlert.SessionSaturated("s2", "alem-das-sombras")))
        assertNotEquals(first, alertGroupKey(UsageAlert.SessionSaturated("s3", "usage-monitor")))
        assertNotEquals(alertGroupKey(UsageAlert.SessionSaturated("s1", null)), alertGroupKey(UsageAlert.SessionSaturated("s2", null)))
    }

    @Test
    fun `another threshold is another alert and critical never groups`() {
        assertNotEquals(alertGroupKey(quota(50)), alertGroupKey(quota(75)))
        assertNull(alertGroupKey(quota(75, actual = 91)))
    }

    /** #408, AA25: um item por grupo, na ordem da primeira chegada, com o alerta mais recente. */
    @Test
    fun `batch keeps one item per group with the latest alert`() {
        val items = batchAlertItems(
            listOf(
                UsageAlert.SessionSaturated("s1", "alem-das-sombras"),
                quota(75, actual = 76),
                UsageAlert.SessionSaturated("s2", "alem-das-sombras"),
                quota(75, actual = 78)
            )
        )
        assertEquals(listOf(2, 2), items.map { item -> item.count })
        assertEquals("s2", (items[0].alert as UsageAlert.SessionSaturated).sessionId)
        assertEquals(78, (items[1].alert as UsageAlert.QuotaThreshold).actualPercent)
    }
}
