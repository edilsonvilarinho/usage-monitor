package com.usagemonitor.data

import com.usagemonitor.data.mapper.CursorUsageMapper
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.UsageUnit
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class CursorUsageMapperTest {
    private val capturedAt = Instant.parse("2026-09-23T12:00:00Z")

    @Test
    fun `maps only explicit personal percentage fields and billing cycle reset`() {
        val payload = Json.parseToJsonElement(
            """{"membershipType":"pro","billingCycleEnd":"2026-10-01T00:00:00Z","individualUsage":{"plan":{"used":0,"limit":0,"remaining":0,"autoPercentUsed":10.4,"apiPercentUsed":20,"totalPercentUsed":15.5,"onDemand":{"used":500,"limit":10000}}}}"""
        )

        val stats = CursorUsageMapper.toDomain(payload, capturedAt)
        val quotas = stats.quotas.associateBy { quota -> quota.label }

        assertEquals(ApiSource.CURSOR, stats.source)
        assertEquals(10L, quotas.getValue("Cursor Auto").used)
        assertEquals(20L, quotas.getValue("Cursor API").used)
        assertEquals(16L, quotas.getValue("Cursor Included total").used)
        assertEquals(100L, quotas.getValue("Cursor Included total").total)
        assertEquals(UsageUnit.PERCENTAGE, quotas.getValue("Cursor Included total").unit)
        assertEquals(PeriodType.MONTHLY, quotas.getValue("Cursor Included total").periodType)
        assertEquals(Instant.parse("2026-10-01T00:00:00Z"), quotas.getValue("Cursor Included total").periodEndAt)
        assertFalse(stats.quotas.any { quota -> quota.unit == UsageUnit.CURRENCY_USD })
    }

    @Test
    fun `explicit zero is valid but spend fields never become a quota`() {
        val payload = Json.parseToJsonElement(
            """{"individualUsage":{"plan":{"used":50,"limit":100,"remaining":50,"totalPercentUsed":0}}}"""
        )

        val stats = CursorUsageMapper.toDomain(payload, capturedAt)

        assertEquals(0L, stats.quotas.single().used)
        assertEquals(100L, stats.quotas.single().total)
        assertFalse(stats.quotas.single().hasKnownResetAt)
        assertEquals(capturedAt, stats.quotas.single().periodEndAt)
    }

    @Test
    fun `missing or invalid percentage means unavailable rather than a derived value`() {
        val missingPercentages = Json.parseToJsonElement(
            """{"individualUsage":{"plan":{"used":0,"limit":100,"remaining":100}}}"""
        )
        val outOfRange = Json.parseToJsonElement(
            """{"individualUsage":{"plan":{"totalPercentUsed":101}}}"""
        )
        val malformedValue = Json.parseToJsonElement(
            """{"individualUsage":{"plan":{"totalPercentUsed":"unknown"}}}"""
        )

        assertFailsWith<IllegalStateException> { CursorUsageMapper.toDomain(missingPercentages, capturedAt) }
        assertFailsWith<IllegalStateException> { CursorUsageMapper.toDomain(outOfRange, capturedAt) }
        assertFailsWith<IllegalStateException> { CursorUsageMapper.toDomain(malformedValue, capturedAt) }
    }
}
