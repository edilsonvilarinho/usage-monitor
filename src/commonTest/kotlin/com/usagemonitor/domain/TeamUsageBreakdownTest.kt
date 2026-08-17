package com.usagemonitor.domain

import com.usagemonitor.data.dto.TeamMemberRowDto
import com.usagemonitor.data.dto.TeamSessionActivityDto
import com.usagemonitor.data.dto.TeamSnapshotDto
import com.usagemonitor.data.dto.TeamUsageRowDto
import com.usagemonitor.data.mapper.toDomain
import com.usagemonitor.domain.entity.CliRangeWindow
import com.usagemonitor.domain.entity.TeamUsageSnapshot
import com.usagemonitor.domain.entity.toTeamBreakdown
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val OPUS = "claude-opus-5"
private const val HAIKU = "claude-haiku-4-5"
private const val MILLION = 1_000_000L

private val NOW = Instant.parse("2026-08-16T12:00:00Z")

/** Janela de 5h já ancorada e com tempo decorrido suficiente para haver ritmo. */
private val ANCHORED_WINDOW = CliRangeWindow(
    cutoffMillis = NOW.toEpochMilliseconds() - 2 * 60 * 60 * 1_000L,
    endsAt = Instant.parse("2026-08-16T15:00:00Z"),
    isAnchored = true
)

private fun row(
    deviceId: String,
    sessionId: String,
    model: String? = OPUS,
    cwd: String? = "/home/dev/alpha",
    gitBranch: String? = "main",
    turnCount: Int = 1,
    inputTokens: Long = MILLION
) = TeamUsageRowDto(
    deviceId = deviceId,
    sessionId = sessionId,
    cwd = cwd,
    gitBranch = gitBranch,
    model = model,
    turnCount = turnCount,
    firstTs = 1_000L,
    lastTs = 2_000L,
    inputTokens = inputTokens
)

private fun snapshotOf(
    members: List<Pair<String, String>>,
    rows: List<TeamUsageRowDto>,
    activity: List<TeamSessionActivityDto> = emptyList()
): TeamUsageSnapshot {
    return TeamSnapshotDto(
        members = members.map { (deviceId, alias) ->
            TeamMemberRowDto(deviceId = deviceId, alias = alias)
        },
        rows = rows,
        activity = activity
    ).toDomain()
}

class TeamUsageBreakdownTest {

    @Test
    fun `dobra as linhas do time nos mesmos eixos do resumo local`() {
        val snapshot = snapshotOf(
            members = listOf("device-1" to "edilson", "device-2" to "maria"),
            rows = listOf(
                row("device-1", "s1", model = OPUS, cwd = "/home/dev/alpha", gitBranch = "main"),
                row("device-2", "s2", model = HAIKU, cwd = "/home/dev/beta", gitBranch = "fix/x")
            )
        )

        val breakdown = snapshot.toTeamBreakdown(window = ANCHORED_WINDOW, now = NOW)

        assertEquals(listOf("alpha", "beta"), breakdown.byProject.mapNotNull { it.label }.sorted())
        assertEquals(listOf("fix/x", "main"), breakdown.byBranch.mapNotNull { it.label }.sorted())
        assertEquals(listOf(HAIKU, OPUS), breakdown.byModel.mapNotNull { it.label }.sorted())
        assertEquals(2 * MILLION, breakdown.totals.inputTokens)
    }

    @Test
    fun `o tempo medido pelo servidor chega aos eixos e ao integrante`() {
        val snapshot = snapshotOf(
            members = listOf("device-1" to "edilson", "device-2" to "maria"),
            rows = listOf(
                // A mesma sessão em dois modelos: a hora dela entra uma vez só.
                row("device-1", "s1", model = OPUS, cwd = "/home/dev/alpha"),
                row("device-1", "s1", model = HAIKU, cwd = "/home/dev/alpha"),
                row("device-2", "s2", model = OPUS, cwd = "/home/dev/alpha")
            ),
            activity = listOf(
                TeamSessionActivityDto("device-1", "s1", 600_000L),
                TeamSessionActivityDto("device-2", "s2", 300_000L)
            )
        )

        val breakdown = snapshot.toTeamBreakdown(window = ANCHORED_WINDOW, now = NOW)

        assertEquals(900_000L, breakdown.byProject.single().activeMillis)
        assertEquals(900_000L, breakdown.totals.activeMillis)
        assertEquals(
            listOf(300_000L, 600_000L),
            breakdown.byMember.map { bucket -> bucket.activeMillis }.sortedBy { millis -> millis }
        )
        assertEquals(600_000L, snapshot.members.first { it.deviceId == "device-1" }.totalActiveMillis)
    }

    /** Servidor anterior à 0.7.0 não manda o campo: hora nula, resto intacto. */
    @Test
    fun `sem medida do servidor as horas ficam nulas`() {
        val snapshot = snapshotOf(
            members = listOf("device-1" to "edilson"),
            rows = listOf(row("device-1", "s1", cwd = "/home/dev/alpha"))
        )

        val breakdown = snapshot.toTeamBreakdown(window = ANCHORED_WINDOW, now = NOW)

        assertNull(breakdown.totals.activeMillis)
        assertNull(breakdown.byProject.single().activeMillis)
        assertNull(snapshot.members.single().totalActiveMillis)
        assertEquals(MILLION, breakdown.totals.inputTokens)
    }

    /**
     * A invariante que o resumo existe para não quebrar: divergência entre o total
     * do resumo e o do cabeçalho da lista significa dois caminhos de precificação.
     */
    @Test
    fun `o custo do resumo bate com o total do snapshot`() {
        val snapshot = snapshotOf(
            members = listOf("device-1" to "edilson", "device-2" to "maria"),
            rows = listOf(
                row("device-1", "s1", model = OPUS, turnCount = 3),
                row("device-1", "s1", model = HAIKU, turnCount = 2),
                row("device-2", "s2", model = OPUS, turnCount = 1)
            )
        )

        val breakdown = snapshot.toTeamBreakdown(window = ANCHORED_WINDOW, now = NOW)

        assertEquals(snapshot.totalCostMicros, breakdown.totals.costMicros)
        assertEquals(snapshot.totalTokens, breakdown.totals.totalTokens)
        assertEquals(snapshot.sessionCount, breakdown.totals.sessionCount)
    }

    @Test
    fun `o eixo por integrante rotula pelo apelido e ordena por custo`() {
        val snapshot = snapshotOf(
            members = listOf("device-1" to "edilson", "device-2" to "maria"),
            rows = listOf(
                row("device-1", "s1", inputTokens = MILLION),
                row("device-2", "s2", inputTokens = 9 * MILLION)
            )
        )

        val breakdown = snapshot.toTeamBreakdown(window = ANCHORED_WINDOW, now = NOW)

        assertEquals(listOf("maria", "edilson"), breakdown.byMember.map { bucket -> bucket.label })
        assertEquals(0.9, breakdown.byMember.first().costShareOf(breakdown.totals), 1e-9)
    }

    /** Uma sessão vira uma linha por modelo; somá-las inflaria a contagem. */
    @Test
    fun `o balde do integrante conta a sessao uma vez so`() {
        val snapshot = snapshotOf(
            members = listOf("device-1" to "edilson"),
            rows = listOf(
                row("device-1", "s1", model = OPUS, turnCount = 3),
                row("device-1", "s1", model = HAIKU, turnCount = 2)
            )
        )

        val bucket = snapshot.toTeamBreakdown(window = ANCHORED_WINDOW, now = NOW).byMember.single()

        assertEquals(1, bucket.sessionCount)
        assertEquals(5, bucket.turnCount)
    }

    /**
     * A mesma máquina que perdeu o `team.json` volta com outro `deviceId` e o
     * mesmo apelido. Dois baldes com o rótulo "SUETONIO" repetiam a chave do
     * `LazyColumn` e derrubavam a janela inteira.
     */
    @Test
    fun `dois integrantes com o mesmo apelido ganham rotulos distintos`() {
        val snapshot = snapshotOf(
            members = listOf("device-aaaaaaaa1111" to "SUETONIO", "device-bbbbbbbb2222" to "SUETONIO"),
            rows = listOf(
                row("device-aaaaaaaa1111", "s1", inputTokens = 2 * MILLION),
                row("device-bbbbbbbb2222", "s2", inputTokens = MILLION)
            )
        )

        val byMember = snapshot.toTeamBreakdown(window = ANCHORED_WINDOW, now = NOW).byMember

        assertEquals(
            listOf("SUETONIO (device-a)", "SUETONIO (device-b)"),
            byMember.map { bucket -> bucket.label }
        )
        assertEquals(byMember.size, byMember.mapNotNull { bucket -> bucket.label }.toSet().size)
    }

    /** O sufixo é para o caso excepcional; carimbá-lo em todos poluiria o normal. */
    @Test
    fun `apelido que nao se repete fica sem sufixo de maquina`() {
        val snapshot = snapshotOf(
            members = listOf("device-1" to "edilson", "device-2" to "maria"),
            rows = listOf(row("device-1", "s1"), row("device-2", "s2"))
        )

        val byMember = snapshot.toTeamBreakdown(window = ANCHORED_WINDOW, now = NOW).byMember

        assertEquals(setOf("edilson", "maria"), byMember.mapNotNull { bucket -> bucket.label }.toSet())
    }

    /** Quem não consumiu não entra no eixo, então não pode forçar desempate. */
    @Test
    fun `integrante sem consumo nao provoca desempate em quem aparece sozinho`() {
        val snapshot = snapshotOf(
            members = listOf("device-1" to "SUETONIO", "device-2" to "SUETONIO"),
            rows = listOf(row("device-1", "s1"))
        )

        val byMember = snapshot.toTeamBreakdown(window = ANCHORED_WINDOW, now = NOW).byMember

        assertEquals(listOf("SUETONIO"), byMember.map { bucket -> bucket.label })
    }

    @Test
    fun `integrante sem consumo na janela fica fora do eixo`() {
        val snapshot = snapshotOf(
            members = listOf("device-1" to "edilson", "device-2" to "parado"),
            rows = listOf(row("device-1", "s1"))
        )

        val byMember = snapshot.toTeamBreakdown(window = ANCHORED_WINDOW, now = NOW).byMember

        // Uma linha zerada por pessoa que não trabalhou é ruído — e a lista de
        // integrantes, que é a resposta certa para "quem não usou", já a mostra.
        assertEquals(listOf("edilson"), byMember.map { bucket -> bucket.label })
    }

    @Test
    fun `janela sem corte nao produz ritmo de queima`() {
        val snapshot = snapshotOf(
            members = listOf("device-1" to "edilson"),
            rows = listOf(row("device-1", "s1"))
        )

        val breakdown = snapshot.toTeamBreakdown(window = CliRangeWindow(), now = NOW)

        assertNull(breakdown.burnRate)
    }

    @Test
    fun `janela ancorada mede o ritmo sobre o tempo decorrido`() {
        val snapshot = snapshotOf(
            members = listOf("device-1" to "edilson"),
            rows = listOf(row("device-1", "s1", inputTokens = 2 * MILLION))
        )

        val burnRate = snapshot.toTeamBreakdown(window = ANCHORED_WINDOW, now = NOW).burnRate

        assertNotNull(burnRate)
        assertEquals(2 * 60 * 60 * 1_000L, burnRate.elapsedMillis)
        // Duas horas decorridas: o ritmo é metade do consumido, não o total.
        assertEquals(MILLION.toDouble(), burnRate.tokensPerHour, 1.0)
    }

    /**
     * O servidor agrega por sessão e modelo, nunca por hora nem por ferramenta.
     * As seções ficam vazias em vez de nascerem com número inventado.
     */
    @Test
    fun `sem grade de atividade nem ferramentas no resumo do time`() {
        val snapshot = snapshotOf(
            members = listOf("device-1" to "edilson"),
            rows = listOf(row("device-1", "s1"))
        )

        val breakdown = snapshot.toTeamBreakdown(window = ANCHORED_WINDOW, now = NOW)

        assertTrue(breakdown.byTool.isEmpty())
        assertTrue(breakdown.heatmap.isEmpty)
    }

    @Test
    fun `snapshot vazio devolve resumo vazio sem dividir por zero`() {
        val breakdown = TeamUsageSnapshot().toTeamBreakdown(window = ANCHORED_WINDOW, now = NOW)

        assertTrue(breakdown.isEmpty)
        assertTrue(breakdown.byMember.isEmpty())
        assertEquals(0.0, breakdown.cacheSavingsShare)
    }
}
