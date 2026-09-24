package com.usagemonitor.data

import com.usagemonitor.data.mapper.CursorUsageMapper
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.CursorQuotaLabels
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.UsageUnit
import com.usagemonitor.domain.repository.CursorUsageException
import com.usagemonitor.domain.repository.CursorUsageFailureKind
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Formas registradas pelo Codenotch (`CursorUsage.swift`) contra contas reais. */
class CursorUsageMapperTest {

    private fun map(json: String) = CursorUsageMapper.toDomain(Json.parseToJsonElement(json))

    @Test
    fun `free plan reads the Auto and API percentages, not the blended total`() {
        val stats = map(
            """{"billingCycleStart":"2026-08-24T03:32:15.933Z","billingCycleEnd":"2026-09-24T03:32:15.933Z",
               "membershipType":"free","isUnlimited":false,
               "individualUsage":{
                 "plan":{"enabled":true,"used":0,"limit":0,"remaining":0,
                         "breakdown":{"included":0,"bonus":19,"total":19},
                         "autoPercentUsed":10.9,"apiPercentUsed":19,"totalPercentUsed":9.5},
                 "onDemand":{"enabled":false,"used":0,"limit":null}}}"""
        )

        assertEquals(ApiSource.CURSOR, stats.source)
        assertEquals(listOf(CursorQuotaLabels.AUTO, CursorQuotaLabels.API), stats.quotas.map { it.label })
        // Truncado, não arredondado: 10,9% não é 11%.
        assertEquals(listOf(10L, 19L), stats.quotas.map { it.used })
        stats.quotas.forEach { quota ->
            assertEquals(100L, quota.total)
            assertEquals(UsageUnit.PERCENTAGE, quota.unit)
            assertEquals(PeriodType.MONTHLY, quota.periodType)
            assertTrue(quota.hasKnownResetAt)
            assertEquals(Instant.parse("2026-09-24T03:32:15.933Z"), quota.periodEndAt)
        }
    }

    @Test
    fun `API at zero stays out and Auto at zero is a reading`() {
        val stats = map("""{"membershipType":"pro","individualUsage":{"plan":{"autoPercentUsed":0,"apiPercentUsed":0}}}""")

        assertEquals(CursorQuotaLabels.AUTO, stats.quotas.single().label)
        assertEquals(0L, stats.quotas.single().used)
    }

    /** A primeira versão exigia `plan` e falhava sempre nessas contas. */
    @Test
    fun `enterprise overall ceiling and team on-demand spend`() {
        val stats = map(
            """{"membershipType":"enterprise","limitType":"team","billingCycleEnd":"2026-10-01T00:00:00Z",
               "individualUsage":{"overall":{"enabled":true,"used":6907,"limit":45000,"remaining":38093}},
               "teamUsage":{"onDemand":{"enabled":true,"used":250000,"limit":1000000}}}"""
        )

        assertEquals(listOf(CursorQuotaLabels.INCLUDED, CursorQuotaLabels.TEAM_ON_DEMAND), stats.quotas.map { it.label })
        assertEquals(listOf(15L, 25L), stats.quotas.map { it.used })
    }

    @Test
    fun `personal on-demand counts only when enabled with a ceiling`() {
        val withCeiling = map(
            """{"membershipType":"pro","individualUsage":{"plan":{"autoPercentUsed":5},
               "onDemand":{"enabled":true,"used":500,"limit":2000}}}"""
        )
        assertEquals(25L, withCeiling.quotas.single { it.label == CursorQuotaLabels.ON_DEMAND }.used)

        val disabled = map(
            """{"membershipType":"pro","individualUsage":{"plan":{"autoPercentUsed":5},
               "onDemand":{"enabled":false,"used":500,"limit":2000}}}"""
        )
        assertFalse(disabled.quotas.any { it.label == CursorQuotaLabels.ON_DEMAND })
    }

    @Test
    fun `over the allowance saturates instead of failing the card`() {
        val stats = map("""{"membershipType":"pro","individualUsage":{"plan":{"autoPercentUsed":137.2}}}""")

        assertEquals(100L, stats.quotas.single().used)
    }

    /**
     * Sem fim de ciclo, gravar o instante da coleta como reset faria cada poll de 10
     * minutos parecer um período novo — e rearmaria o alerta de limiar a cada um.
     */
    @Test
    fun `missing billing cycle end is an unknown reset, not the capture instant`() {
        val quota = map("""{"membershipType":"pro","individualUsage":{"plan":{"autoPercentUsed":40}}}""").quotas.single()

        assertFalse(quota.hasKnownResetAt)
        assertEquals(Instant.parse("2100-01-01T00:00:00Z"), quota.periodEndAt)
    }

    @Test
    fun `a plan with nothing metered is its own state, not zero usage`() {
        listOf(
            """{"membershipType":"free","isUnlimited":false,"individualUsage":{"plan":{"used":0,"limit":0}}}""",
            """{"isUnlimited":true}"""
        ).forEach { json ->
            val error = assertFailsWith<CursorUsageException> { map(json) }
            assertEquals(CursorUsageFailureKind.NOTHING_METERED, error.kind)
        }
    }

    @Test
    fun `an unknown shape is unrecognized rather than a derived value`() {
        listOf(
            """{"individualUsage":{"plan":{"used":0,"limit":100,"remaining":100}}}""",
            """{"individualUsage":{"plan":{"autoPercentUsed":"unknown"}}}""",
            """[]"""
        ).forEach { json ->
            val error = assertFailsWith<IllegalStateException> { map(json) }
            assertFalse(error is CursorUsageException)
            assertEquals("Cursor usage response format is unrecognized", error.message)
        }
    }
}
