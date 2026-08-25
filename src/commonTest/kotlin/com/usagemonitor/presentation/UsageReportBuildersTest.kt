package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.CliActivityCell
import com.usagemonitor.domain.entity.CliActivityHeatmap
import com.usagemonitor.domain.entity.CliSessionRange
import com.usagemonitor.domain.entity.CliSessionSummary
import com.usagemonitor.domain.entity.CliUsageBreakdown
import com.usagemonitor.domain.entity.CliUsageBucket
import com.usagemonitor.domain.entity.TeamMemberUsage
import com.usagemonitor.domain.entity.TeamAccountEmailSource
import com.usagemonitor.presentation.ui.report.UsageReportSection
import com.usagemonitor.presentation.ui.report.reportForCliSessions
import com.usagemonitor.presentation.ui.report.reportForTeam
import com.usagemonitor.presentation.ui.report.toReportText
import com.usagemonitor.presentation.viewmodel.CliSessionsUiState
import com.usagemonitor.presentation.viewmodel.TeamUsageUiState
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private val NOW = Instant.parse("2026-08-17T18:30:00Z")

class UsageReportBuildersTest {

    @Test
    fun `os totais do relatorio sao os do estado`() {
        val document = reportForCliSessions(state = successState(), language = AppLanguage.PT, now = NOW)

        val totals = assertIs<UsageReportSection.KeyValues>(document.sections.first())
        val values = totals.entries.associate { entry -> entry.label to entry.value }

        assertEquals("2", values["Sessões"])
        // 2 sessões × 3 turnos, a mesma soma que o cabeçalho da tela mostra.
        assertEquals("6", values["Turnos"])
        assertEquals("1h40", values["Tempo ativo"])
    }

    /**
     * O relatório é o recorte da tela. Se a janela não entrasse no subtítulo, dois
     * PDFs do mesmo dia com filtros diferentes seriam indistinguíveis.
     */
    @Test
    fun `o subtitulo carrega conta janela e carimbo`() {
        val document = reportForCliSessions(
            state = successState(profileLabel = "Pessoal").copy(
                rangeStartsAt = Instant.parse("2026-08-17T13:30:00Z")
            ),
            language = AppLanguage.PT,
            now = NOW
        )

        assertTrue(document.subtitle.startsWith("Pessoal · "))
        // 18:30 UTC é 15:30 em BRT: o carimbo é do fuso da apresentação.
        assertTrue(document.subtitle.endsWith("2026-08-17 15:30 BRT"), document.subtitle)
        assertEquals(
            "Período considerado: 2026-08-17 10:30 a 2026-08-17 15:30 BRT",
            document.period
        )
    }

    /** O PDF deve usar o corte resolvido, inclusive quando ele não é `now - 5h`. */
    @Test
    fun `o periodo usa o inicio efetivamente aplicado`() {
        val anchoredStart = Instant.parse("2026-08-17T17:45:00Z")
        val document = reportForCliSessions(
            state = successState().copy(rangeStartsAt = anchoredStart, rangeAnchored = true),
            language = AppLanguage.PT,
            now = NOW
        )

        assertEquals(
            "Período considerado: 2026-08-17 14:45 a 2026-08-17 15:30 BRT",
            document.period
        )
    }

    @Test
    fun `o total com dados começa na atividade mais antiga`() {
        val state = successState().copy(
            range = CliSessionRange.ALL,
            rangeStartsAt = null,
            sessions = listOf(
                session("recente", "p", activeMillis = 1L),
                session("antiga", "p", activeMillis = 1L).copy(
                    firstTs = Instant.parse("2026-07-01T09:15:00Z")
                )
            )
        )

        val document = reportForCliSessions(state = state, language = AppLanguage.PT, now = NOW)

        assertEquals(
            "Período considerado: 2026-07-01 06:15 a 2026-08-17 15:30 BRT",
            document.period
        )
    }

    @Test
    fun `o total vazio nao inventa uma data inicial`() {
        val state = successState().copy(
            range = CliSessionRange.ALL,
            rangeStartsAt = null,
            sessions = emptyList()
        )

        val document = reportForCliSessions(state = state, language = AppLanguage.PT, now = NOW)

        assertEquals(
            "Período considerado: todo o histórico disponível até 2026-08-17 15:30 BRT",
            document.period
        )
    }

    @Test
    fun `o relatorio do time traduz o periodo para ingles`() {
        val state = TeamUsageUiState.Success(
            members = listOf(teamMember("membro", session("a", "p", activeMillis = 1L))),
            range = CliSessionRange.LAST_7D,
            rangeStartsAt = Instant.parse("2026-08-10T18:30:00Z")
        )

        val document = reportForTeam(state = state, language = AppLanguage.EN, now = NOW)

        assertEquals(
            "Period considered: 2026-08-10 15:30 to 2026-08-17 15:30 BRT",
            document.period
        )
    }

    /** Custo incompleto é piso, e o `+` da tela tem de existir no PDF também. */
    @Test
    fun `custo parcial sai marcado e explicado no rodape`() {
        val state = successState(unpricedTurnCount = 2)

        val document = reportForCliSessions(state = state, language = AppLanguage.PT, now = NOW)
        val totals = assertIs<UsageReportSection.KeyValues>(document.sections.first())

        assertTrue(totals.entries.any { entry -> entry.value.endsWith("+") })
        assertTrue(document.footnotes.isNotEmpty())
    }

    /**
     * O intervalo entre turnos não pertence a um modelo, então a tabela de modelos
     * não pode ganhar uma coluna de hora — nem cheia de traços.
     */
    @Test
    fun `a tabela de modelos nao tem coluna de tempo`() {
        val document = reportForCliSessions(state = successState(), language = AppLanguage.PT, now = NOW)

        val tables = document.sections.filterIsInstance<UsageReportSection.Table>()
        val projects = tables.first { table -> table.heading == "Projeto" }
        val models = tables.first { table -> table.heading == "Modelo" }

        assertTrue(projects.columns.any { column -> column.title == "Tempo ativo" })
        assertTrue(models.columns.none { column -> column.title == "Tempo ativo" })
        // Toda linha tem exatamente uma célula por coluna, ou o desenho sai torto.
        assertTrue(projects.rows.all { row -> row.size == projects.columns.size })
        assertTrue(models.rows.all { row -> row.size == models.columns.size })
    }

    @Test
    fun `a grade entra como secao propria quando ha atividade`() {
        val document = reportForCliSessions(state = successState(), language = AppLanguage.PT, now = NOW)

        assertTrue(document.sections.any { section -> section is UsageReportSection.Grid })
    }

    /**
     * Nome de projeto é texto livre. Um caractere fora do conjunto da fonte faz o
     * PDFBox lançar no meio da escrita — o relatório inteiro morreria por causa de
     * um emoji num nome de pasta.
     */
    @Test
    fun `caractere fora da fonte vira interrogacao e o acento sobrevive`() {
        val document = reportForCliSessions(
            state = successState(projectLabel = "café 🚀 项目"),
            language = AppLanguage.PT,
            now = NOW
        )

        val projects = document.sections
            .filterIsInstance<UsageReportSection.Table>()
            .first { table -> table.heading == "Projeto" }

        // O emoji é um par substituto e sai como dois `?`: o que importa é que a
        // linha sobreviva, não a contagem de marcas no lugar dele.
        assertEquals("café ?? ??", projects.rows.first().first())
    }

    @Test
    fun `o saneamento preserva ascii e latin-1`() {
        assertEquals("Sessões · US$ 1,00 — fim", "Sessões · US$ 1,00 — fim".toReportText())
        assertEquals("?", "→".toReportText())
    }

    @Test
    fun `sessoes cli saem por tempo ativo e preservam empates`() {
        val state = successState().copy(
            sessions = listOf(
                session("zero", "p", activeMillis = 0L),
                session("ausente", "p", activeMillis = null),
                session("maior", "p", activeMillis = 7_200_000L),
                session("empate-a", "p", activeMillis = 3_600_000L),
                session("empate-b", "p", activeMillis = 3_600_000L)
            )
        )

        val document = reportForCliSessions(state = state, language = AppLanguage.PT, now = NOW)
        val sessions = document.sections.filterIsInstance<UsageReportSection.Table>().last()

        assertEquals(
            listOf("maior", "empate-a", "empate-b", "zero", "ausente"),
            sessions.rows.map { row -> row.first() }
        )
    }

    @Test
    fun `eixos com tempo sao reordenados e modelo preserva o ranking`() {
        val state = successState()
        val original = requireNotNull(state.breakdown)
        val breakdown = original.copy(
            byProject = listOf(
                CliUsageBucket(label = "zero", activeMillis = 0L),
                CliUsageBucket(label = "ausente", activeMillis = null),
                CliUsageBucket(label = "maior", activeMillis = 7_200_000L),
                CliUsageBucket(label = "empate-a", activeMillis = 3_600_000L),
                CliUsageBucket(label = "empate-b", activeMillis = 3_600_000L)
            ),
            byModel = listOf(
                CliUsageBucket(label = "modelo-b", costMicros = 2L),
                CliUsageBucket(label = "modelo-a", costMicros = 1L)
            )
        )

        val document = reportForCliSessions(
            state = state.copy(breakdown = breakdown),
            language = AppLanguage.PT,
            now = NOW
        )
        val tables = document.sections.filterIsInstance<UsageReportSection.Table>()
        val projects = tables.first { table -> table.heading == "Projeto" }
        val models = tables.first { table -> table.heading == "Modelo" }

        assertEquals(
            listOf("maior", "empate-a", "empate-b", "zero", "ausente"),
            projects.rows.map { row -> row.first() }
        )
        assertEquals(listOf("modelo-b", "modelo-a"), models.rows.map { row -> row.first() })
    }

    @Test
    fun `relatorio do time ordena integrantes e sessoes por tempo ativo`() {
        val members = listOf(
            teamMember("zero", session("zero", "p", activeMillis = 0L)),
            teamMember("ausente", session("ausente", "p", activeMillis = null)),
            teamMember("maior", session("maior", "p", activeMillis = 7_200_000L)),
            teamMember("empate-a", session("empate-a", "p", activeMillis = 3_600_000L)),
            teamMember("empate-b", session("empate-b", "p", activeMillis = 3_600_000L))
        )

        val document = reportForTeam(
            state = TeamUsageUiState.Success(members = members),
            language = AppLanguage.PT,
            now = NOW
        )
        val tables = document.sections.filterIsInstance<UsageReportSection.Table>()

        assertEquals(
            listOf("maior", "empate-a", "empate-b", "zero", "ausente"),
            tables.first().rows.map { row -> row.first() }
        )
        assertEquals(
            listOf("maior", "empate-a", "empate-b", "zero", "ausente"),
            tables.last().rows.map { row -> row.first() }
        )
    }

    @Test
    fun `relatorio global resume por e-mail e ordena por e-mail e UUID`() {
        val members = listOf(
            teamMember("zeca", session("z", "p", activeMillis = 9_000L)).copy(
                accountKey = "uuid-b",
                accountEmail = "b@empresa.com",
                accountEmailSource = TeamAccountEmailSource.REPORTED
            ),
            teamMember("ana", session("a", "p", activeMillis = 1_000L)).copy(
                accountKey = "uuid-a2",
                accountEmail = "a@empresa.com",
                accountEmailSource = TeamAccountEmailSource.LABEL
            ),
            teamMember("bia", session("b", "p", activeMillis = 2_000L)).copy(
                accountKey = "uuid-a1",
                accountEmail = "a@empresa.com",
                accountEmailSource = TeamAccountEmailSource.REPORTED
            )
        )

        val document = reportForTeam(
            state = TeamUsageUiState.Success(members = members, isAdminOverview = true),
            language = AppLanguage.PT,
            now = NOW
        )
        val tables = document.sections.filterIsInstance<UsageReportSection.Table>()
        val summary = tables.first { table -> table.heading == "Resumo por e-mail" }
        val memberTable = tables.first { table -> table.heading == "Integrante" }
        val sessionsTable = tables.last { table -> table.heading == "Sessões" }

        assertEquals(listOf("a@empresa.com", "b@empresa.com"), summary.rows.map { row -> row[0] })
        assertEquals(listOf("uuid-a1", "uuid-a2", "uuid-b"), memberTable.rows.map { row -> row[1] })
        assertEquals(listOf("uuid-a1", "uuid-a2", "uuid-b"), sessionsTable.rows.map { row -> row[1] })
        assertTrue(document.footnotes.any { note -> note.contains("rótulo administrativo") })
    }
}

private fun successState(
    profileLabel: String? = null,
    projectLabel: String = "usage-monitor",
    unpricedTurnCount: Int = 0
): CliSessionsUiState.Success {
    val sessions = listOf(
        session("a", projectLabel, activeMillis = 3_600_000L, unpricedTurnCount = unpricedTurnCount),
        session("b", projectLabel, activeMillis = 2_400_000L)
    )

    return CliSessionsUiState.Success(
        sessions = sessions,
        range = CliSessionRange.LAST_5H,
        profileLabel = profileLabel,
        breakdown = CliUsageBreakdown(
            byProject = listOf(
                CliUsageBucket(
                    label = projectLabel,
                    turnCount = 6,
                    sessionCount = 2,
                    inputTokens = 1_000L,
                    costMicros = 500_000L,
                    activeMillis = 6_000_000L
                )
            ),
            byBranch = listOf(
                CliUsageBucket(label = "main", turnCount = 6, sessionCount = 2, activeMillis = 6_000_000L)
            ),
            byModel = listOf(
                CliUsageBucket(label = "claude-opus-5", turnCount = 6, sessionCount = 2)
            ),
            totals = CliUsageBucket(turnCount = 6, sessionCount = 2, activeMillis = 6_000_000L),
            heatmap = CliActivityHeatmap(
                cells = listOf(CliActivityCell(DayOfWeek.MONDAY, hour = 14, turnCount = 6, costMicros = 500_000L))
            )
        )
    )
}

private fun session(
    sessionId: String,
    projectLabel: String,
    activeMillis: Long?,
    unpricedTurnCount: Int = 0
): CliSessionSummary {
    return CliSessionSummary(
        sessionId = sessionId,
        filePath = "/tmp/$sessionId.jsonl",
        cwd = "/home/dev/$projectLabel",
        gitBranch = "main",
        firstTs = Instant.parse("2026-08-17T10:00:00Z"),
        lastTs = Instant.parse("2026-08-17T12:00:00Z"),
        primaryModel = "claude-opus-5",
        turnCount = 3,
        inputTokens = 1_000L,
        costMicros = 250_000L,
        unpricedTurnCount = unpricedTurnCount,
        activeMillis = activeMillis
    )
}

private fun teamMember(alias: String, session: CliSessionSummary): TeamMemberUsage {
    return TeamMemberUsage(
        deviceId = "device-$alias",
        alias = alias,
        hostName = "host-$alias",
        sessions = listOf(session)
    )
}
