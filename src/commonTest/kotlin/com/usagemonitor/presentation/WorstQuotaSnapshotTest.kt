package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.DEFAULT_ANTHROPIC_PROFILE_ID
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.QuotaInfo
import com.usagemonitor.domain.entity.QuotaRiskSummary
import com.usagemonitor.domain.entity.QuotaSeriesKey
import com.usagemonitor.domain.entity.UsageRiskLevel
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.domain.entity.UsageUnit
import com.usagemonitor.presentation.viewmodel.allQuotaRisks
import com.usagemonitor.presentation.viewmodel.allSourceRisks
import com.usagemonitor.presentation.viewmodel.worstQuotaSnapshot
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

private val NOW = Instant.parse("2026-08-13T12:00:00Z")
private val ANTHROPIC_TARGET = UsageTargetKey(ApiSource.ANTHROPIC, DEFAULT_ANTHROPIC_PROFILE_ID)
private val CODEX_TARGET = UsageTargetKey.forSource(ApiSource.CODEX)
private val ANTHROPIC_QUOTA_KEY = QuotaSeriesKey("Sessão 5h", PeriodType.INTERVAL)
private val CODEX_QUOTA_KEY = QuotaSeriesKey("Codex 5h", PeriodType.INTERVAL)

class WorstQuotaSnapshotTest {

    @Test
    fun `allSourceRisks orders by level descending, worst first`() {
        val stats = listOf(
            stats(ApiSource.ANTHROPIC, ANTHROPIC_TARGET, "Sessão 5h"),
            stats(ApiSource.CODEX, CODEX_TARGET, "Codex 5h")
        )
        val riskSummaries = mapOf(
            ANTHROPIC_TARGET to mapOf(ANTHROPIC_QUOTA_KEY to QuotaRiskSummary(UsageRiskLevel.AT_RISK, NOW + 1.hours)),
            CODEX_TARGET to mapOf(CODEX_QUOTA_KEY to QuotaRiskSummary(UsageRiskLevel.WILL_EXCEED, NOW + 30.minutes))
        )

        val ordered = allSourceRisks(stats, riskSummaries, NOW)

        assertEquals(listOf(ApiSource.CODEX, ApiSource.ANTHROPIC), ordered.map { it.stats.source })
    }

    /** Empate no nível é desempatado pelo nome da fonte — ordem tem de ser total. */
    @Test
    fun `allSourceRisks breaks ties by source name`() {
        val stats = listOf(
            stats(ApiSource.CODEX, CODEX_TARGET, "Codex 5h"),
            stats(ApiSource.ANTHROPIC, ANTHROPIC_TARGET, "Sessão 5h")
        )
        val riskSummaries = mapOf(
            ANTHROPIC_TARGET to mapOf(ANTHROPIC_QUOTA_KEY to QuotaRiskSummary(UsageRiskLevel.AT_RISK, NOW + 1.hours)),
            CODEX_TARGET to mapOf(CODEX_QUOTA_KEY to QuotaRiskSummary(UsageRiskLevel.AT_RISK, NOW + 1.hours))
        )

        val ordered = allSourceRisks(stats, riskSummaries, NOW)

        assertEquals(listOf("ANTHROPIC", "CODEX"), ordered.map { it.stats.apiName })
    }

    @Test
    fun `worstQuotaSnapshot is the first entry of allSourceRisks`() {
        val stats = listOf(
            stats(ApiSource.ANTHROPIC, ANTHROPIC_TARGET, "Sessão 5h"),
            stats(ApiSource.CODEX, CODEX_TARGET, "Codex 5h")
        )
        val riskSummaries = mapOf(
            ANTHROPIC_TARGET to mapOf(ANTHROPIC_QUOTA_KEY to QuotaRiskSummary(UsageRiskLevel.AT_RISK, NOW + 1.hours)),
            CODEX_TARGET to mapOf(CODEX_QUOTA_KEY to QuotaRiskSummary(UsageRiskLevel.WILL_EXCEED, NOW + 30.minutes))
        )

        val worst = worstQuotaSnapshot(stats, riskSummaries, NOW)

        assertEquals(ApiSource.CODEX, worst?.stats?.source)
    }

    @Test
    fun `allSourceRisks is empty and worstQuotaSnapshot is null with no data`() {
        assertEquals(emptyList(), allSourceRisks(emptyList(), emptyMap(), NOW))
        assertNull(worstQuotaSnapshot(emptyList(), emptyMap(), NOW))
    }

    private fun stats(source: ApiSource, target: UsageTargetKey, label: String): ApiUsageStats {
        return statsWithQuotas(source, target, listOf(label))
    }

    private fun statsWithQuotas(
        source: ApiSource,
        target: UsageTargetKey,
        labels: List<String>
    ): ApiUsageStats {
        return ApiUsageStats(
            source = source,
            targetKey = target,
            apiName = source.name,
            quotas = labels.map { label ->
                QuotaInfo(
                    label = label,
                    used = 10L,
                    total = 100L,
                    periodEndAt = NOW + 2.hours,
                    periodType = PeriodType.INTERVAL,
                    unit = UsageUnit.PERCENTAGE
                )
            }
        )
    }

    // ------------------------------------------------------------ allQuotaRisks

    /**
     * O caso que abriu esta correção: uma conta com janela de 5h e de 7d
     * aparecia no HUD com **uma** linha, e o outro limite não existia na tela.
     */
    @Test
    fun `allQuotaRisks emits one entry per quota, not per source`() {
        val stats = listOf(
            statsWithQuotas(ApiSource.ANTHROPIC, ANTHROPIC_TARGET, listOf("Sessão 5h", "Sessão 7d"))
        )
        val riskSummaries = mapOf(
            ANTHROPIC_TARGET to mapOf(
                ANTHROPIC_QUOTA_KEY to QuotaRiskSummary(UsageRiskLevel.AT_RISK, NOW + 1.hours)
            )
        )

        val entries = allQuotaRisks(stats, riskSummaries, NOW)

        assertEquals(listOf("Sessão 5h", "Sessão 7d"), entries.map { it.quota.label })
        assertEquals(1, allSourceRisks(stats, riskSummaries, NOW).size)
    }

    /**
     * Sem projeção a linha continua saindo: o percentual é fato medido e não
     * depende de previsão. Kilo e OpenCode nunca têm projeção, e com a regra do
     * badge do card eles sumiriam do HUD inteiro.
     */
    @Test
    fun `allQuotaRisks keeps quotas without a forecast`() {
        val stats = listOf(stats(ApiSource.CODEX, CODEX_TARGET, "Codex 5h"))

        val entries = allQuotaRisks(stats, emptyMap(), NOW)

        assertEquals(1, entries.size)
        assertNull(entries.single().risk)
    }

    /**
     * **Não ordena por risco.** Quem ordena a barra HUD é a ordem de cards que o
     * usuário arrastou; ordenar por risco aqui e por card ali daria duas ordens
     * brigando, e a linha parada trocaria de conta sozinha conforme o risco
     * mudasse.
     */
    @Test
    fun `allQuotaRisks keeps the incoming order`() {
        val stats = listOf(
            stats(ApiSource.CODEX, CODEX_TARGET, "Codex 5h"),
            stats(ApiSource.ANTHROPIC, ANTHROPIC_TARGET, "Sessão 5h")
        )
        val riskSummaries = mapOf(
            ANTHROPIC_TARGET to mapOf(
                ANTHROPIC_QUOTA_KEY to QuotaRiskSummary(UsageRiskLevel.WILL_EXCEED, NOW + 30.minutes)
            )
        )

        val entries = allQuotaRisks(stats, riskSummaries, NOW)

        // A Anthropic está crítica e mesmo assim continua depois do Codex.
        assertEquals(listOf("Codex 5h", "Sessão 5h"), entries.map { it.quota.label })
    }

    /** Cota vencida continua fora: o número seria o da janela anterior. */
    @Test
    fun `allQuotaRisks drops expired quotas`() {
        val expired = ApiUsageStats(
            source = ApiSource.ANTHROPIC,
            targetKey = ANTHROPIC_TARGET,
            apiName = ApiSource.ANTHROPIC.name,
            quotas = listOf(
                QuotaInfo(
                    label = "Sessão 5h",
                    used = 10L,
                    total = 100L,
                    periodEndAt = NOW - 1.hours,
                    periodType = PeriodType.INTERVAL,
                    unit = UsageUnit.PERCENTAGE
                )
            )
        )

        assertEquals(emptyList(), allQuotaRisks(listOf(expired), emptyMap(), NOW))
    }

    /**
     * Dentro da fonte, a ordem é a de declaração das cotas — a da resposta da
     * API, 5h antes de 7d. É ela que o resumo da linha parada imprime, e trocá-la
     * faria "5h 88% · 7d 9%" sair invertido sem nada ter mudado.
     */
    @Test
    fun `allQuotaRisks keeps the declared quota order inside a source`() {
        val stats = listOf(
            statsWithQuotas(ApiSource.ANTHROPIC, ANTHROPIC_TARGET, listOf("Sessão 7d", "Sessão 5h"))
        )
        val riskSummaries = mapOf(
            ANTHROPIC_TARGET to mapOf(
                ANTHROPIC_QUOTA_KEY to QuotaRiskSummary(UsageRiskLevel.WILL_EXCEED, NOW + 30.minutes)
            )
        )

        val entries = allQuotaRisks(stats, riskSummaries, NOW)

        assertEquals(listOf("Sessão 7d", "Sessão 5h"), entries.map { it.quota.label })
    }
}
