package com.usagemonitor.data

import com.usagemonitor.data.dto.OpenCodeGoUsageDto
import com.usagemonitor.data.dto.OpenCodeGoUsageResponse
import com.usagemonitor.data.dto.OpenCodeGoWindowDto
import com.usagemonitor.data.mapper.OpenCodeGoMapper
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.OpenCodeGoQuotaLabels
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.UsageUnit
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OpenCodeGoMapperTest {

    /** O corpo é o da chamada real registrada na issue #124. */
    private val realResponse = OpenCodeGoUsageResponse(
        usage = OpenCodeGoUsageDto(
            rolling = OpenCodeGoWindowDto("ok", 0.0, "2026-08-29T10:28:33.651Z"),
            weekly = OpenCodeGoWindowDto("ok", 51.0, "2026-08-31T00:00:00.651Z"),
            monthly = OpenCodeGoWindowDto("ok", 47.0, "2026-09-05T18:47:55.651Z")
        )
    )

    @Test
    fun `maps the three windows to percentage quotas`() {
        val stats = OpenCodeGoMapper.toUsageStats(realResponse)

        assertEquals(ApiSource.OPENCODE_GO, stats.source)
        assertEquals(
            listOf(
                OpenCodeGoQuotaLabels.ROLLING,
                OpenCodeGoQuotaLabels.WEEKLY,
                OpenCodeGoQuotaLabels.MONTHLY
            ),
            stats.quotas.map { quota -> quota.label }
        )
        assertEquals(
            listOf(PeriodType.INTERVAL, PeriodType.WEEKLY, PeriodType.MONTHLY),
            stats.quotas.map { quota -> quota.periodType }
        )
        assertTrue(stats.quotas.all { quota -> quota.unit == UsageUnit.PERCENTAGE })
        assertTrue(stats.quotas.all { quota -> quota.total == 100L })
        assertEquals(listOf(0L, 51L, 47L), stats.quotas.map { quota -> quota.used })
        assertEquals(
            Instant.parse("2026-08-31T00:00:00.651Z"),
            stats.quotas[1].periodEndAt
        )
        assertTrue(stats.quotas.all { quota -> quota.hasKnownResetAt })
    }

    /**
     * Sem grandeza subjacente conhecida não há tokens estimados: a Anthropic
     * converte percentual numa capacidade porque conhece o teto, e aqui a API
     * nunca informa valor gasto nem limite.
     */
    @Test
    fun `does not invent raw token capacity`() {
        val stats = OpenCodeGoMapper.toUsageStats(realResponse)

        assertTrue(stats.quotas.all { quota -> quota.rawUsed == 0L && quota.rawTotal == 0L })
    }

    @Test
    fun `keeps the remaining windows when one is absent`() {
        val stats = OpenCodeGoMapper.toUsageStats(
            OpenCodeGoUsageResponse(
                usage = OpenCodeGoUsageDto(
                    rolling = OpenCodeGoWindowDto("ok", 12.0, "2026-08-29T10:28:33.651Z"),
                    weekly = null,
                    monthly = OpenCodeGoWindowDto("ok", 47.0, "2026-09-05T18:47:55.651Z")
                )
            )
        )

        assertEquals(
            listOf(OpenCodeGoQuotaLabels.ROLLING, OpenCodeGoQuotaLabels.MONTHLY),
            stats.quotas.map { quota -> quota.label }
        )
    }

    /**
     * Janela sem `resetsAt` continua valendo e só perde a projeção. Marcá-la como
     * reinício conhecido faria a régua de risco prometer uma data inventada.
     */
    @Test
    fun `window without resetsAt has no known reset`() {
        val stats = OpenCodeGoMapper.toUsageStats(
            OpenCodeGoUsageResponse(
                usage = OpenCodeGoUsageDto(
                    rolling = OpenCodeGoWindowDto("ok", 30.0, null)
                )
            )
        )

        val quota = stats.quotas.single()
        assertEquals(30L, quota.used)
        assertFalse(quota.hasKnownResetAt)
    }

    @Test
    fun `unparseable resetsAt degrades to unknown reset instead of failing`() {
        val stats = OpenCodeGoMapper.toUsageStats(
            OpenCodeGoUsageResponse(
                usage = OpenCodeGoUsageDto(
                    rolling = OpenCodeGoWindowDto("ok", 30.0, "amanhã")
                )
            )
        )

        assertFalse(stats.quotas.single().hasKnownResetAt)
    }

    /**
     * Resposta sem nenhuma janela é contrato mudado, não conta zerada: falhar
     * preserva o último valor em cache em vez de apagá-lo.
     */
    @Test
    fun `fails when the response carries no window at all`() {
        val error = assertFailsWith<IllegalStateException> {
            OpenCodeGoMapper.toUsageStats(OpenCodeGoUsageResponse())
        }

        assertTrue(error.message!!.contains("sem nenhuma janela de uso"))
    }

    @Test
    fun `clamps percent outside the zero to one hundred range`() {
        val stats = OpenCodeGoMapper.toUsageStats(
            OpenCodeGoUsageResponse(
                usage = OpenCodeGoUsageDto(
                    rolling = OpenCodeGoWindowDto("rate-limited", 137.0, null),
                    weekly = OpenCodeGoWindowDto("ok", -3.0, null)
                )
            )
        )

        assertEquals(listOf(100L, 0L), stats.quotas.map { quota -> quota.used })
    }
}
