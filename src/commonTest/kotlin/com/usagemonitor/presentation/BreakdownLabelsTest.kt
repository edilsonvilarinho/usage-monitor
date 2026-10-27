package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.CliUsageBucket
import com.usagemonitor.presentation.ui.BreakdownAxis
import com.usagemonitor.presentation.ui.BreakdownLabels
import com.usagemonitor.presentation.ui.BreakdownPage
import com.usagemonitor.presentation.ui.BreakdownSort
import com.usagemonitor.presentation.ui.formatActiveTime
import com.usagemonitor.presentation.ui.formatMicrosUsdShort
import com.usagemonitor.presentation.ui.formatQuantity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val PT = AppLanguage.PT
private val EN = AppLanguage.EN

class BreakdownLabelsTest {

    @Test
    fun `every axis has a tab, a column and an unknown label in both languages`() {
        val ptTabs = BreakdownAxis.entries.map { BreakdownLabels.axisTab(it, PT) }
        val enTabs = BreakdownAxis.entries.map { BreakdownLabels.axisTab(it, EN) }
        val ptColumns = BreakdownAxis.entries.map { BreakdownLabels.columnAxis(it, PT) }
        val enColumns = BreakdownAxis.entries.map { BreakdownLabels.columnAxis(it, EN) }

        // Um rótulo por eixo, sem repetição: duas abas iguais seriam indistinguíveis.
        assertEquals(BreakdownAxis.entries.size, ptTabs.toSet().size)
        assertEquals(BreakdownAxis.entries.size, enTabs.toSet().size)
        assertEquals(listOf("Integrante", "Projeto", "Modelo", "Branch", "Ferramenta", "Hora"), ptColumns)
        assertEquals(listOf("Member", "Project", "Model", "Branch", "Tool", "Hour"), enColumns)
        assertEquals("Ferramentas", BreakdownLabels.axisTab(BreakdownAxis.TOOL, PT))
        assertEquals("Tools", BreakdownLabels.axisTab(BreakdownAxis.TOOL, EN))

        // Ferramenta e hora não têm "desconhecido": a linha sempre tem nome.
        assertEquals("", BreakdownLabels.unknownLabel(BreakdownAxis.TOOL, PT))
        assertEquals("", BreakdownLabels.unknownLabel(BreakdownAxis.ACTIVITY, EN))
        for (axis in listOf(BreakdownAxis.MEMBER, BreakdownAxis.PROJECT, BreakdownAxis.MODEL, BreakdownAxis.BRANCH)) {
            assertTrue(BreakdownLabels.unknownLabel(axis, PT).isNotBlank(), "PT $axis")
            assertTrue(BreakdownLabels.unknownLabel(axis, EN).isNotBlank(), "EN $axis")
        }
    }

    @Test
    fun `section titles in english`() {
        assertEquals("By member", BreakdownLabels.byMember(EN))
        assertEquals("By model", BreakdownLabels.byModel(EN))
        assertEquals("By branch", BreakdownLabels.byBranch(EN))
        assertEquals("Por branch", BreakdownLabels.byBranch(PT))
        assertEquals("Calls", BreakdownLabels.columnCalls(EN))
        assertEquals("Chamadas", BreakdownLabels.columnCalls(PT))
        assertEquals("Most used tools", BreakdownLabels.byTool(EN))
        assertEquals("Activity by hour", BreakdownLabels.activityTitle(EN))
        assertEquals("Burn rate", BreakdownLabels.burnRateTitle(EN))
        assertEquals("Monthly budget", BreakdownLabels.budgetTitle(EN))
    }

    @Test
    fun `sort options never call the tool count a cost`() {
        assertEquals("Chamadas", BreakdownLabels.sortOption(BreakdownSort.SHARE, BreakdownAxis.TOOL, PT))
        assertEquals("Calls", BreakdownLabels.sortOption(BreakdownSort.SHARE, BreakdownAxis.TOOL, EN))
        assertEquals("Custo", BreakdownLabels.sortOption(BreakdownSort.SHARE, BreakdownAxis.MODEL, PT))
        assertEquals("Cost", BreakdownLabels.sortOption(BreakdownSort.SHARE, BreakdownAxis.MODEL, EN))
        assertEquals("Turnos", BreakdownLabels.sortOption(BreakdownSort.VOLUME, BreakdownAxis.TOOL, PT))
        assertEquals("Turns", BreakdownLabels.sortOption(BreakdownSort.VOLUME, BreakdownAxis.TOOL, EN))
        assertEquals("Tokens", BreakdownLabels.sortOption(BreakdownSort.VOLUME, BreakdownAxis.PROJECT, PT))
        assertEquals("Tokens", BreakdownLabels.sortOption(BreakdownSort.VOLUME, BreakdownAxis.PROJECT, EN))
        assertEquals("Nome", BreakdownLabels.sortOption(BreakdownSort.NAME, BreakdownAxis.PROJECT, PT))
        assertEquals("Name", BreakdownLabels.sortOption(BreakdownSort.NAME, BreakdownAxis.PROJECT, EN))
        assertEquals("Maior primeiro", BreakdownLabels.sortDirection(true, PT))
        assertEquals("Highest first", BreakdownLabels.sortDirection(true, EN))
        assertEquals("Menor primeiro", BreakdownLabels.sortDirection(false, PT))
        assertEquals("Lowest first", BreakdownLabels.sortDirection(false, EN))
    }

    @Test
    fun `page summary says where the page starts, ends and what the filter hid`() {
        val empty = BreakdownPage<String>(totalCount = 4, filteredCount = 0)
        assertEquals("nenhum resultado", BreakdownLabels.pageSummary(empty, PT))
        assertEquals("no results", BreakdownLabels.pageSummary(empty, EN))

        val unfiltered = BreakdownPage(items = listOf("a", "b"), fromIndex = 10, totalCount = 12, filteredCount = 12)
        assertEquals("11–12 de 12", BreakdownLabels.pageSummary(unfiltered, PT))
        assertEquals("11–12 of 12", BreakdownLabels.pageSummary(unfiltered, EN))

        val filtered = BreakdownPage(items = listOf("a"), totalCount = 12, filteredCount = 3)
        assertEquals("1–1 de 3 (de 12 sem filtro)", BreakdownLabels.pageSummary(filtered, PT))
        assertEquals("1–1 of 3 (of 12 unfiltered)", BreakdownLabels.pageSummary(filtered, EN))
    }

    @Test
    fun `filter and empty states quote the query`() {
        assertEquals("Nada com \"api\" neste eixo.", BreakdownLabels.noMatches("api", PT))
        assertEquals("Nothing matching \"api\" on this axis.", BreakdownLabels.noMatches("api", EN))
        assertEquals("Nenhum turno nesta janela.", BreakdownLabels.empty(PT))
        assertEquals("No turns in this window.", BreakdownLabels.empty(EN))
        assertTrue(BreakdownLabels.refreshing(EN).startsWith("Refreshing"))
    }

    @Test
    fun `incomplete cost carries the plus sign`() {
        val complete = CliUsageBucket(costMicros = 1_500_000L)
        val floor = complete.copy(unpricedTurnCount = 2)

        assertEquals(formatMicrosUsdShort(1_500_000L), BreakdownLabels.bucketCost(complete))
        assertEquals(formatMicrosUsdShort(1_500_000L) + "+", BreakdownLabels.bucketCost(floor))
        assertEquals("Estimated cost: ${BreakdownLabels.bucketCost(floor)}", BreakdownLabels.totalCost(floor, EN))
        assertEquals("Custo estimado: ${BreakdownLabels.bucketCost(complete)}", BreakdownLabels.totalCost(complete, PT))
    }

    @Test
    fun `active time joins the subtitles only when measured and positive`() {
        val totals = CliUsageBucket(sessionCount = 2, turnCount = 5, inputTokens = 1_000L)
        val tokens = formatQuantity(1_000L)

        assertEquals("2 sessions · 5 turns · $tokens tokens", BreakdownLabels.totalSubtitle(totals, EN))
        assertEquals("2 sessões · 5 turnos · $tokens tokens", BreakdownLabels.totalSubtitle(totals.copy(activeMillis = 0L), PT))
        assertEquals(
            "2 sessions · 5 turns · $tokens tokens · ${formatActiveTime(3_600_000L)} active time",
            BreakdownLabels.totalSubtitle(totals.copy(activeMillis = 3_600_000L), EN)
        )
        assertTrue(BreakdownLabels.totalSubtitle(totals.copy(activeMillis = 3_600_000L), PT).endsWith(" tempo ativo"))

        val bucketBase = BreakdownLabels.bucketSubtitle(totals, EN)
        assertTrue(bucketBase.startsWith("2 sessions · $tokens tokens · cache "))
        assertEquals(bucketBase, BreakdownLabels.bucketSubtitle(totals.copy(activeMillis = 0L), EN))
        assertEquals(
            "$bucketBase · ${formatActiveTime(90_000L)}",
            BreakdownLabels.bucketSubtitle(totals.copy(activeMillis = 90_000L), EN)
        )
        assertTrue(BreakdownLabels.bucketSubtitle(totals, PT).startsWith("2 sessões · "))
    }

    @Test
    fun `notices in both languages`() {
        assertEquals(
            "3 turnos sem preço conhecido: o custo exibido é o piso.",
            BreakdownLabels.unpricedNotice(3, PT)
        )
        assertEquals("3 turns with no known price: the cost shown is a floor.", BreakdownLabels.unpricedNotice(3, EN))
        assertTrue(BreakdownLabels.axisNotice(EN).endsWith("they do not add up."))
        assertTrue(BreakdownLabels.cacheSavings(1_000_000L, 0.5, 0.9, EN).startsWith("Cache saved "))
        assertTrue(BreakdownLabels.cacheSavings(1_000_000L, 0.5, 0.9, PT).startsWith("Cache economizou "))
        assertEquals("4 calls across 2 turns", BreakdownLabels.toolSubtitle(4, 2, EN))
        assertEquals("4 chamadas em 2 turnos", BreakdownLabels.toolSubtitle(4, 2, PT))
        assertTrue(BreakdownLabels.toolNotice(EN).startsWith("Call counts, not cost"))
        assertTrue(BreakdownLabels.toolNotice(PT).startsWith("Contagem de chamadas"))
        assertTrue(BreakdownLabels.activityNotice(EN).endsWith("BRT time."))
        assertTrue(BreakdownLabels.activityNotice(PT).endsWith("Horário BRT."))
        assertEquals("Last read failed; the numbers are from the previous one. x", BreakdownLabels.staleNotice("x", EN))
        assertEquals("Última leitura falhou; os números são da anterior. x", BreakdownLabels.staleNotice("x", PT))
        assertTrue(BreakdownLabels.burnRateFooter(EN).isNotBlank())
    }

    @Test
    fun `budget value and projection mark the floor and the cap`() {
        val spent = formatMicrosUsdShort(2_000_000L)
        val limit = formatMicrosUsdShort(10_000_000L)
        val projected = formatMicrosUsdShort(12_000_000L)

        assertEquals("$spent de $limit", BreakdownLabels.budgetValue(2_000_000L, 10_000_000L, true, PT))
        assertEquals("$spent+ of $limit", BreakdownLabels.budgetValue(2_000_000L, 10_000_000L, false, EN))
        assertEquals(
            "No ritmo atual o mês fecha em $projected — acima do teto.",
            BreakdownLabels.budgetProjection(12_000_000L, true, PT)
        )
        assertEquals("No ritmo atual o mês fecha em $projected.", BreakdownLabels.budgetProjection(12_000_000L, false, PT))
        assertEquals(
            "At this pace the month closes at $projected — over the cap.",
            BreakdownLabels.budgetProjection(12_000_000L, true, EN)
        )
        assertEquals("At this pace the month closes at $projected.", BreakdownLabels.budgetProjection(12_000_000L, false, EN))
        assertTrue(BreakdownLabels.budgetScopeNotice(EN).startsWith("Estimated CLI session cost"))
    }

    @Test
    fun `account credits stay in the account currency with two decimals`() {
        assertEquals(
            "Créditos de uso da conta: BRL 327.84 de BRL 550.05 (moeda da conta, não convertida)",
            BreakdownLabels.accountCredits(32_784L, 55_005L, "BRL", PT)
        )
        assertEquals(
            "Account usage credits: USD 0.07 of USD 10.00 (account currency, not converted)",
            BreakdownLabels.accountCredits(7L, 1_000L, "USD", EN)
        )
    }

    @Test
    fun `burn rate texts`() {
        assertEquals("${formatMicrosUsdShort(3_000_000L)}/h", BreakdownLabels.burnRateCostPerHour(3_000_000L))
        assertEquals("${formatQuantity(1_234L)} tokens por hora.", BreakdownLabels.burnRateTokensPerHour(1_234.9, PT))
        assertEquals("${formatQuantity(1_234L)} tokens per hour.", BreakdownLabels.burnRateTokensPerHour(1_234.9, EN))
        // Abaixo de uma hora o tempo sai em minutos; acima, horas com minutos em dois dígitos.
        assertEquals(
            "Medido sobre 45min decorridos da janela, não sobre a duração nominal dela.",
            BreakdownLabels.burnRateElapsed(45L * 60_000L, PT)
        )
        assertEquals(
            "Measured over 2h05 elapsed in the window, not over its nominal length.",
            BreakdownLabels.burnRateElapsed(125L * 60_000L, EN)
        )
        assertEquals(
            "Mantido o ritmo, a janela fecha em ${formatMicrosUsdShort(9_000_000L)}.",
            BreakdownLabels.burnRateProjection(9_000_000L, PT)
        )
        assertEquals(
            "At this pace, the window closes at ${formatMicrosUsdShort(9_000_000L)}.",
            BreakdownLabels.burnRateProjection(9_000_000L, EN)
        )
        assertEquals(
            "Burn rate unavailable: the window is open-ended or just started.",
            BreakdownLabels.burnRateUnavailable(EN)
        )
    }
}
