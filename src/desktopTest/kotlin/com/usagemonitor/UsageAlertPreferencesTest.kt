package com.usagemonitor

import com.usagemonitor.domain.entity.DEFAULT_QUOTA_ALERT_PERCENTS
import com.usagemonitor.domain.entity.DEFAULT_STALL_THRESHOLD_MILLIS
import com.usagemonitor.domain.entity.QuietHours
import com.usagemonitor.presentation.ui.components.wrapHour
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UsageAlertPreferencesTest {

    @Test
    fun `absent value falls back to the defaults`() {
        assertEquals(DEFAULT_QUOTA_ALERT_PERCENTS, decodeQuotaPercents(null))
    }

    /** Lista vazia é escolha válida e não pode virar o default de volta. */
    @Test
    fun `an empty stored list stays empty`() {
        assertEquals(emptyList(), decodeQuotaPercents(""))
    }

    @Test
    fun `out of range and repeated thresholds are dropped`() {
        assertEquals(listOf(50, 90), decodeQuotaPercents("90, 0, 50, 90, 150, -3"))
    }

    @Test
    fun `thresholds survive a round trip`() {
        val encoded = encodeQuotaPercents(listOf(100, 60, 60))

        assertEquals(listOf(60, 100), decodeQuotaPercents(encoded))
    }

    @Test
    fun `quiet hours survive a round trip`() {
        val encoded = encodeQuietHours(QuietHours(22, 8))

        assertEquals(QuietHours(22, 8), decodeQuietHours(encoded))
    }

    @Test
    fun `an unreadable quiet range means no silence`() {
        assertNull(decodeQuietHours("22"))
        assertNull(decodeQuietHours("22-99"))
        assertNull(decodeQuietHours("noite-manha"))
        assertNull(decodeQuietHours(""))
    }

    @Test
    fun `an absent stall threshold falls back to the default`() {
        assertEquals(DEFAULT_STALL_THRESHOLD_MILLIS, decodeStallThresholdMillis(null))
    }

    @Test
    fun `a stall threshold survives a round trip in minutes`() {
        assertEquals(60L * 60 * 1_000, decodeStallThresholdMillis(60))
        assertEquals(4L * 60 * 60 * 1_000, decodeStallThresholdMillis(240))
    }

    /** Registro editado à mão não é fonte confiável de faixa válida. */
    @Test
    fun `a stall threshold below the floor falls back to the default`() {
        assertEquals(DEFAULT_STALL_THRESHOLD_MILLIS, decodeStallThresholdMillis(5))
        assertEquals(DEFAULT_STALL_THRESHOLD_MILLIS, decodeStallThresholdMillis(-30))
    }

    @Test
    fun `the hour stepper wraps like a clock`() {
        assertEquals(23, wrapHour(-1))
        assertEquals(0, wrapHour(24))
        assertEquals(13, wrapHour(13))
    }
}
