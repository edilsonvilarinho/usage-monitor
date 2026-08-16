package com.usagemonitor.domain

import com.usagemonitor.domain.entity.CliUsageGroupRow
import com.usagemonitor.domain.entity.ModelPricingTable
import com.usagemonitor.domain.entity.toUsageBreakdown
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val OPUS = "claude-opus-5"
private const val HAIKU = "claude-haiku-4-5"

class CliUsageBreakdownTest {

    @Test
    fun `rows are folded along the three axes`() {
        val breakdown = listOf(
            row(sessionId = "s1", cwd = "/home/dev/alpha", gitBranch = "main", model = OPUS, inputTokens = 1_000),
            row(sessionId = "s2", cwd = "/home/dev/beta", gitBranch = "main", model = HAIKU, inputTokens = 2_000)
        ).toUsageBreakdown()

        assertEquals(listOf("alpha", "beta"), breakdown.byProject.mapNotNull { bucket -> bucket.label }.sorted())
        assertEquals(listOf("main"), breakdown.byBranch.map { bucket -> bucket.label })
        assertEquals(listOf(HAIKU, OPUS), breakdown.byModel.mapNotNull { bucket -> bucket.label }.sorted())
        assertEquals(3_000L, breakdown.totals.inputTokens)
    }

    /** Uma sessão vira uma linha por modelo; somá-las inflaria a contagem. */
    @Test
    fun `a session split across models counts once`() {
        val breakdown = listOf(
            row(sessionId = "s1", cwd = "/home/dev/alpha", model = OPUS, turnCount = 3),
            row(sessionId = "s1", cwd = "/home/dev/alpha", model = HAIKU, turnCount = 2)
        ).toUsageBreakdown()

        val project = breakdown.byProject.single()
        assertEquals(1, project.sessionCount)
        assertEquals(5, project.turnCount)
        assertEquals(1, breakdown.totals.sessionCount)
    }

    /**
     * O custo tem de sair da tabela de preços por modelo, não de um rateio: é a
     * mesma conta que a lista de sessões faz, e as duas telas não podem divergir.
     */
    @Test
    fun `cost is priced per model`() {
        val opusPricing = ModelPricingTable.forModel(OPUS)!!
        val haikuPricing = ModelPricingTable.forModel(HAIKU)!!

        val breakdown = listOf(
            row(sessionId = "s1", model = OPUS, inputTokens = 1_000_000, outputTokens = 500_000),
            row(sessionId = "s2", model = HAIKU, inputTokens = 1_000_000, outputTokens = 500_000)
        ).toUsageBreakdown()

        val expectedOpus = opusPricing.costMicros(inputTokens = 1_000_000, outputTokens = 500_000)
        val expectedHaiku = haikuPricing.costMicros(inputTokens = 1_000_000, outputTokens = 500_000)

        val byModel = breakdown.byModel.associateBy { bucket -> bucket.label }
        assertEquals(expectedOpus, byModel.getValue(OPUS).costMicros)
        assertEquals(expectedHaiku, byModel.getValue(HAIKU).costMicros)
        assertEquals(expectedOpus + expectedHaiku, breakdown.totals.costMicros)
    }

    /** Sem tarifa não se inventa custo: o balde declara a lacuna. */
    @Test
    fun `an unknown model adds no cost and is declared`() {
        val breakdown = listOf(
            row(sessionId = "s1", model = "modelo-inexistente", turnCount = 4, inputTokens = 1_000_000)
        ).toUsageBreakdown()

        val bucket = breakdown.byModel.single()
        assertEquals(0L, bucket.costMicros)
        assertEquals(4, bucket.unpricedTurnCount)
        assertFalse(bucket.isCostComplete)
        assertFalse(breakdown.totals.isCostComplete)
    }

    @Test
    fun `buckets are ranked by cost`() {
        val breakdown = listOf(
            row(sessionId = "s1", cwd = "/home/dev/cheap", model = HAIKU, inputTokens = 1_000_000),
            row(sessionId = "s2", cwd = "/home/dev/pricey", model = OPUS, inputTokens = 1_000_000)
        ).toUsageBreakdown()

        assertEquals(listOf("pricey", "cheap"), breakdown.byProject.map { bucket -> bucket.label })
    }

    /**
     * Duas leituras iguais têm de produzir listas iguais, ou o `StateFlow`
     * reemite e a tela recompõe a cada tique do laço ao vivo.
     */
    @Test
    fun `equal readings produce equal lists`() {
        val rows = listOf(
            row(sessionId = "s1", cwd = "/home/dev/alpha", model = OPUS, inputTokens = 1_000),
            row(sessionId = "s2", cwd = "/home/dev/beta", model = OPUS, inputTokens = 1_000),
            row(sessionId = "s3", cwd = "/home/dev/gamma", model = OPUS, inputTokens = 1_000)
        )

        assertEquals(rows.toUsageBreakdown(), rows.reversed().toUsageBreakdown())
    }

    @Test
    fun `an absent project falls into its own bucket`() {
        val breakdown = listOf(
            row(sessionId = "s1", cwd = null, gitBranch = null, model = OPUS)
        ).toUsageBreakdown()

        assertEquals(null, breakdown.byProject.single().label)
        assertEquals(null, breakdown.byBranch.single().label)
    }

    @Test
    fun `cache savings measure what was avoided`() {
        val pricing = ModelPricingTable.forModel(OPUS)!!
        val breakdown = listOf(
            row(sessionId = "s1", model = OPUS, cacheReadTokens = 10_000_000)
        ).toUsageBreakdown()

        assertEquals(pricing.cacheSavingsMicros(10_000_000), breakdown.totals.cacheSavingsMicros)
        // Cache read custa 0,1x o input, então a economia é 90% do que seria gasto.
        assertTrue(breakdown.cacheSavingsShare > 0.89 && breakdown.cacheSavingsShare < 0.91)
    }

    @Test
    fun `an empty reading is empty`() {
        val breakdown = emptyList<CliUsageGroupRow>().toUsageBreakdown()

        assertTrue(breakdown.isEmpty)
        assertEquals(0.0, breakdown.cacheSavingsShare)
        assertTrue(breakdown.byProject.isEmpty())
    }

    @Test
    fun `the share of a bucket is measured against the total cost`() {
        val breakdown = listOf(
            row(sessionId = "s1", cwd = "/home/dev/alpha", model = OPUS, inputTokens = 3_000_000),
            row(sessionId = "s2", cwd = "/home/dev/beta", model = OPUS, inputTokens = 1_000_000)
        ).toUsageBreakdown()

        val alpha = breakdown.byProject.first { bucket -> bucket.label == "alpha" }
        assertEquals(0.75, alpha.costShareOf(breakdown.totals))
    }
}

private fun row(
    sessionId: String,
    cwd: String? = "/home/dev/alpha",
    gitBranch: String? = "main",
    model: String? = OPUS,
    turnCount: Int = 1,
    inputTokens: Long = 0L,
    outputTokens: Long = 0L,
    cacheReadTokens: Long = 0L
): CliUsageGroupRow {
    return CliUsageGroupRow(
        sessionId = sessionId,
        cwd = cwd,
        gitBranch = gitBranch,
        model = model,
        turnCount = turnCount,
        inputTokens = inputTokens,
        outputTokens = outputTokens,
        cacheReadTokens = cacheReadTokens
    )
}
