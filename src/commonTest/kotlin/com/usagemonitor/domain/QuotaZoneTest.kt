package com.usagemonitor.domain

import com.usagemonitor.domain.entity.DEFAULT_QUOTA_ALERT_PERCENTS
import com.usagemonitor.domain.entity.QuotaZone
import com.usagemonitor.domain.entity.quotaZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class QuotaZoneTest {

    @Test
    fun `com os limiares padrao 75, 90 e 100, a zona conta quantos o percentual alcancou`() {
        assertEquals(QuotaZone.ON_TRACK, quotaZone(74, DEFAULT_QUOTA_ALERT_PERCENTS))
        assertEquals(QuotaZone.ATTENTION, quotaZone(75, DEFAULT_QUOTA_ALERT_PERCENTS))
        assertEquals(QuotaZone.ATTENTION, quotaZone(89, DEFAULT_QUOTA_ALERT_PERCENTS))
        assertEquals(QuotaZone.CRITICAL, quotaZone(90, DEFAULT_QUOTA_ALERT_PERCENTS))
        assertEquals(QuotaZone.CRITICAL, quotaZone(100, DEFAULT_QUOTA_ALERT_PERCENTS))
    }

    @Test
    fun `limiar proprio da conta muda a zona`() {
        assertEquals(QuotaZone.ATTENTION, quotaZone(73, listOf(70, 90)))
        assertEquals(QuotaZone.ON_TRACK, quotaZone(73, listOf(80)))
    }

    @Test
    fun `um limiar so nunca passa de atencao`() {
        assertEquals(QuotaZone.ATTENTION, quotaZone(100, listOf(50)))
    }

    @Test
    fun `limiares fora de ordem, repetidos ou invalidos sao normalizados`() {
        assertEquals(QuotaZone.CRITICAL, quotaZone(92, listOf(90, 0, 70, 90, 150)))
    }

    @Test
    fun `sem limiar ou sem percentual nao ha zona`() {
        assertNull(quotaZone(95, emptyList()))
        assertNull(quotaZone(null, DEFAULT_QUOTA_ALERT_PERCENTS))
    }
}
