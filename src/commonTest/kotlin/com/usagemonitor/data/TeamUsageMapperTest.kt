package com.usagemonitor.data

import com.usagemonitor.data.dto.TeamMemberRowDto
import com.usagemonitor.data.dto.TeamSessionDetailResponseDto
import com.usagemonitor.data.dto.TeamSessionRowDto
import com.usagemonitor.data.dto.TeamSnapshotDto
import com.usagemonitor.data.dto.TeamTurnRowDto
import com.usagemonitor.data.dto.TeamUsageRowDto
import com.usagemonitor.data.mapper.toDomain
import com.usagemonitor.data.mapper.toDto
import com.usagemonitor.domain.entity.CliSessionSummary
import com.usagemonitor.domain.entity.CliSessionTurn
import com.usagemonitor.domain.entity.TeamIngestPayload
import com.usagemonitor.domain.entity.TeamMemberIdentity
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val OPUS = "claude-opus-4-5-20251101"
private const val HAIKU = "claude-haiku-4-5-20251001"
private const val MILLION = 1_000_000L

private fun row(
    deviceId: String = "device-1",
    sessionId: String = "session-1",
    model: String? = OPUS,
    turnCount: Int = 1,
    firstTs: Long = 1_000L,
    lastTs: Long = 2_000L,
    inputTokens: Long = 0L,
    outputTokens: Long = 0L,
    cacheReadTokens: Long = 0L,
    cwd: String? = "/home/dev/api-gateway",
    liveContextTokens: Long = 0L,
    liveContextModel: String? = null
) = TeamUsageRowDto(
    deviceId = deviceId,
    sessionId = sessionId,
    cwd = cwd,
    gitBranch = "main",
    liveContextTokens = liveContextTokens,
    liveContextModel = liveContextModel,
    model = model,
    turnCount = turnCount,
    firstTs = firstTs,
    lastTs = lastTs,
    inputTokens = inputTokens,
    outputTokens = outputTokens,
    cacheReadTokens = cacheReadTokens
)

private fun member(
    deviceId: String = "device-1",
    alias: String = "edilson",
    hostName: String? = "DESKTOP-A1",
    lastSeenAt: Long = 5_000L
) = TeamMemberRowDto(
    deviceId = deviceId,
    alias = alias,
    hostName = hostName,
    lastSeenAt = lastSeenAt
)

private fun detailTurn(
    messageId: String = "msg-1",
    ts: Long = 1_000L,
    model: String? = OPUS,
    isSidechain: Boolean = false,
    inputTokens: Long = 0L,
    outputTokens: Long = 0L,
    cacheReadTokens: Long = 0L,
    cacheWrite5mTokens: Long = 0L,
    cacheWrite1hTokens: Long = 0L
) = TeamTurnRowDto(
    messageId = messageId,
    ts = ts,
    model = model,
    isSidechain = isSidechain,
    inputTokens = inputTokens,
    outputTokens = outputTokens,
    cacheReadTokens = cacheReadTokens,
    cacheWrite5mTokens = cacheWrite5mTokens,
    cacheWrite1hTokens = cacheWrite1hTokens
)

private fun detailResponse(
    hostName: String? = "DESKTOP-A1",
    firstTs: Long = 1_000L,
    lastTs: Long = 2_000L,
    liveContextTokens: Long = 0L,
    liveContextModel: String? = null,
    turns: List<TeamTurnRowDto> = listOf(detailTurn())
) = TeamSessionDetailResponseDto(
    session = TeamSessionRowDto(
        deviceId = "device-1",
        sessionId = "session-1",
        hostName = hostName,
        cwd = "/home/dev/api-gateway",
        gitBranch = "main",
        firstTs = firstTs,
        lastTs = lastTs,
        liveContextTokens = liveContextTokens,
        liveContextModel = liveContextModel
    ),
    turns = turns
)

class TeamUsageMapperTest {

    @Test
    fun `dobra linhas por modelo numa unica sessao precificando cada trecho`() {
        val snapshot = TeamSnapshotDto(
            members = listOf(member()),
            rows = listOf(
                row(model = OPUS, turnCount = 3, inputTokens = MILLION, firstTs = 1_000L, lastTs = 4_000L),
                row(model = HAIKU, turnCount = 1, inputTokens = MILLION, firstTs = 500L, lastTs = 9_000L)
            )
        ).toDomain()

        val sessions = snapshot.members.single().sessions
        assertEquals(1, sessions.size)

        val session = sessions.single()
        assertEquals(4, session.turnCount)
        assertEquals(2 * MILLION, session.inputTokens)
        // Opus a 5 USD/M + Haiku a 1 USD/M sobre um milhão de tokens cada.
        assertEquals(6 * MILLION, session.costMicros)
        // A janela da sessão é a união dos trechos, não a do último grupo lido.
        assertEquals(Instant.fromEpochMilliseconds(500L), session.firstTs)
        assertEquals(Instant.fromEpochMilliseconds(9_000L), session.lastTs)
        // O modelo predominante é o que teve mais turnos na janela.
        assertEquals(OPUS, session.primaryModel)
    }

    @Test
    fun `modelo desconhecido marca o custo como incompleto`() {
        val snapshot = TeamSnapshotDto(
            members = listOf(member()),
            rows = listOf(
                row(model = OPUS, turnCount = 1, inputTokens = MILLION),
                row(model = "modelo-que-nao-existe", turnCount = 2, inputTokens = MILLION)
            )
        ).toDomain()

        val session = snapshot.members.single().sessions.single()
        assertEquals(2, session.unpricedTurnCount)
        assertFalse(session.isCostComplete)
        // O trecho sem preço não entra no custo, mas os tokens continuam contando.
        assertEquals(5 * MILLION, session.costMicros)
        assertEquals(2 * MILLION, session.inputTokens)
        assertFalse(snapshot.isTotalCostComplete)
    }

    @Test
    fun `agrupa sessoes por maquina`() {
        val snapshot = TeamSnapshotDto(
            members = listOf(member(), member(deviceId = "device-2", alias = "maria", hostName = "NOTE-B2")),
            rows = listOf(
                row(deviceId = "device-1", sessionId = "s1", inputTokens = MILLION),
                row(deviceId = "device-1", sessionId = "s2", inputTokens = MILLION),
                row(deviceId = "device-2", sessionId = "s3", inputTokens = MILLION)
            )
        ).toDomain()

        assertEquals(2, snapshot.members.size)
        val edilson = snapshot.members.first { it.alias == "edilson" }
        val maria = snapshot.members.first { it.alias == "maria" }

        assertEquals(2, edilson.sessionCount)
        assertEquals(1, maria.sessionCount)
        assertEquals(3, snapshot.sessionCount)
        assertEquals("NOTE-B2", maria.machineLabel)
    }

    @Test
    fun `preserva membro sem atividade na janela`() {
        val snapshot = TeamSnapshotDto(
            members = listOf(member(), member(deviceId = "device-2", alias = "joao", hostName = "NOTE-C3")),
            rows = listOf(row(deviceId = "device-1", inputTokens = MILLION))
        ).toDomain()

        val joao = snapshot.members.first { it.alias == "joao" }
        assertTrue(joao.sessions.isEmpty())
        assertFalse(joao.hasActivity)
        assertEquals(0L, joao.totalTokens)
        // Ele conta como integrante, mas não como integrante ativo no período.
        assertEquals(2, snapshot.members.size)
        assertEquals(1, snapshot.activeMemberCount)
    }

    @Test
    fun `nao descarta maquina com sessoes mas sem linha de membro`() {
        val snapshot = TeamSnapshotDto(
            members = listOf(member()),
            rows = listOf(
                row(deviceId = "device-1", inputTokens = MILLION),
                row(deviceId = "device-fantasma", sessionId = "s9", inputTokens = MILLION)
            )
        ).toDomain()

        assertEquals(2, snapshot.members.size)
        // Sem isso os totais do time ficariam menores que a soma real das sessões.
        assertEquals(2 * MILLION, snapshot.totalTokens)
        assertEquals(10 * MILLION, snapshot.totalCostMicros)
    }

    @Test
    fun `ordena integrantes por consumo decrescente`() {
        val snapshot = TeamSnapshotDto(
            members = listOf(
                member(deviceId = "device-1", alias = "edilson"),
                member(deviceId = "device-2", alias = "maria"),
                member(deviceId = "device-3", alias = "ana")
            ),
            rows = listOf(
                row(deviceId = "device-1", sessionId = "s1", inputTokens = 10L),
                row(deviceId = "device-2", sessionId = "s2", inputTokens = 99L)
            )
        ).toDomain()

        assertEquals(listOf("maria", "edilson", "ana"), snapshot.members.map { it.alias })
    }

    @Test
    fun `calcula a fatia de tokens de cada integrante`() {
        val snapshot = TeamSnapshotDto(
            members = listOf(member(), member(deviceId = "device-2", alias = "maria")),
            rows = listOf(
                row(deviceId = "device-1", sessionId = "s1", inputTokens = 75L),
                row(deviceId = "device-2", sessionId = "s2", inputTokens = 25L)
            )
        ).toDomain()

        assertEquals(0.75, snapshot.tokenShareOf(snapshot.members.first()))
        assertEquals(0.25, snapshot.tokenShareOf(snapshot.members.last()))
    }

    @Test
    fun `snapshot vazio nao divide por zero`() {
        val snapshot = TeamSnapshotDto().toDomain()

        assertEquals(0L, snapshot.totalTokens)
        assertEquals(0, snapshot.sessionCount)
        assertTrue(snapshot.isTotalCostComplete)
    }

    @Test
    fun `propaga contexto vivo da sessao`() {
        val snapshot = TeamSnapshotDto(
            members = listOf(member()),
            rows = listOf(row(liveContextTokens = 120_000L, liveContextModel = OPUS))
        ).toDomain()

        val session = snapshot.members.single().sessions.single()
        assertEquals(120_000L, session.liveContextTokens)
        assertEquals(OPUS, session.liveContextModel)
        assertEquals("api-gateway", session.projectName)
    }

    @Test
    fun `propaga a maquina do integrante para as sessoes da lista`() {
        val snapshot = TeamSnapshotDto(
            members = listOf(member(hostName = "DESKTOP-A1")),
            rows = listOf(row(inputTokens = MILLION))
        ).toDomain()

        // Sem isso o card de metadados do detalhe mostraria "Máquina —" para uma
        // máquina que o servidor conhece: o hostname é do integrante, não da linha.
        assertEquals("DESKTOP-A1", snapshot.members.single().sessions.single().hostName)
    }

    @Test
    fun `sessao de maquina sem linha de membro fica sem hostname em vez de chutar`() {
        val snapshot = TeamSnapshotDto(
            members = listOf(member()),
            rows = listOf(row(deviceId = "device-fantasma", sessionId = "s9", inputTokens = MILLION))
        ).toDomain()

        val ghost = snapshot.members.first { it.deviceId == "device-fantasma" }
        assertNull(ghost.sessions.single().hostName)
    }

    @Test
    fun `monta o detalhe da sessao a partir dos turnos crus`() {
        val detail = detailResponse(
            turns = listOf(
                detailTurn(messageId = "a", ts = 1_000L, inputTokens = 10L, cacheReadTokens = 100L),
                detailTurn(messageId = "b", ts = 4_000L, outputTokens = 20L, cacheReadTokens = 300L)
            )
        ).toDomain()

        assertEquals(2, detail.turns.size)
        // O `seq` é sintetizado da ordem da resposta: o servidor não o guarda.
        assertEquals(listOf(0, 1), detail.turns.map { turn -> turn.seq })
        assertEquals(listOf("a", "b"), detail.turns.map { turn -> turn.messageId })
        assertEquals(Instant.fromEpochMilliseconds(4_000L), detail.turns.last().ts)

        val summary = detail.summary
        assertEquals("session-1", summary.sessionId)
        assertEquals(2, summary.turnCount)
        assertEquals(10L, summary.inputTokens)
        assertEquals(20L, summary.outputTokens)
        assertEquals(400L, summary.cacheReadTokens)
        // A janela do resumo é a união dos turnos, não a declarada pela sessão.
        assertEquals(Instant.fromEpochMilliseconds(1_000L), summary.firstTs)
        assertEquals(Instant.fromEpochMilliseconds(4_000L), summary.lastTs)
        assertEquals(OPUS, summary.primaryModel)
    }

    @Test
    fun `o detalhe carrega a maquina de origem e o contexto vivo`() {
        val detail = detailResponse(
            hostName = "NOTE-B2",
            liveContextTokens = 120_000L,
            liveContextModel = OPUS
        ).toDomain()

        val summary = detail.summary
        assertEquals("NOTE-B2", summary.hostName)
        assertEquals("/home/dev/api-gateway", summary.cwd)
        assertEquals("main", summary.gitBranch)
        assertEquals(120_000L, summary.liveContextTokens)
        assertEquals(OPUS, summary.liveContextModel)
        // O caminho do transcript é local e não trafega: não há o que preencher.
        assertEquals("", summary.filePath)
    }

    @Test
    fun `o detalhe preserva o marcador de subagente`() {
        val detail = detailResponse(
            turns = listOf(
                detailTurn(messageId = "a", isSidechain = false),
                detailTurn(messageId = "b", isSidechain = true)
            )
        ).toDomain()

        assertEquals(listOf(false, true), detail.turns.map { turn -> turn.isSidechain })
    }

    @Test
    fun `o detalhe precifica cada turno pela tarifa do modelo dele`() {
        val detail = detailResponse(
            turns = listOf(
                detailTurn(messageId = "a", model = OPUS, inputTokens = MILLION),
                detailTurn(messageId = "b", model = HAIKU, inputTokens = MILLION)
            )
        ).toDomain()

        // Opus a 5 USD/M + Haiku a 1 USD/M: a mesma conta do resumo da lista.
        assertEquals(6 * MILLION, detail.summary.costMicros)
        assertTrue(detail.summary.isCostComplete)
    }

    @Test
    fun `sessao sem turno mantem a janela declarada pelo servidor`() {
        val detail = detailResponse(firstTs = 7_000L, lastTs = 9_000L, turns = emptyList()).toDomain()

        // O acumulador vazio devolveria época zero e o card de período mostraria
        // 01/01/1970 para uma sessão cujo intervalo o servidor conhece.
        assertTrue(detail.turns.isEmpty())
        assertEquals(Instant.fromEpochMilliseconds(7_000L), detail.summary.firstTs)
        assertEquals(Instant.fromEpochMilliseconds(9_000L), detail.summary.lastTs)
    }

    @Test
    fun `converte o lote de envio preservando tokens e timestamps`() {
        val payload = TeamIngestPayload(
            accountKey = "account-uuid",
            member = TeamMemberIdentity(
                deviceId = "device-1",
                alias = "edilson",
                hostName = "DESKTOP-A1",
                organizationUuid = "org-1",
                organizationName = "Empresa"
            ),
            sessions = listOf(
                CliSessionSummary(
                    sessionId = "session-1",
                    filePath = "/home/dev/.claude/projects/x/session-1.jsonl",
                    cwd = "/home/dev/api-gateway",
                    gitBranch = "main",
                    firstTs = Instant.fromEpochMilliseconds(1_000L),
                    lastTs = Instant.fromEpochMilliseconds(2_000L),
                    liveContextTokens = 42L,
                    liveContextModel = OPUS
                )
            ),
            turns = listOf(
                CliSessionTurn(
                    sessionId = "session-1",
                    seq = 7,
                    messageId = "msg-1",
                    ts = Instant.fromEpochMilliseconds(1_500L),
                    model = OPUS,
                    isSidechain = true,
                    inputTokens = 1L,
                    outputTokens = 2L,
                    cacheReadTokens = 3L,
                    cacheWrite5mTokens = 4L,
                    cacheWrite1hTokens = 5L
                )
            )
        )

        val dto = payload.toDto()

        assertEquals("account-uuid", dto.accountKey)
        assertEquals("DESKTOP-A1", dto.member.hostName)

        val session = dto.sessions.single()
        assertEquals(1_000L, session.firstTs)
        assertEquals(2_000L, session.lastTs)
        assertEquals(42L, session.liveContextTokens)
        // O caminho do transcript é local e não tem valor para o time: não trafega.

        val turn = dto.turns.single()
        assertEquals("msg-1", turn.messageId)
        assertEquals(1_500L, turn.ts)
        assertTrue(turn.isSidechain)
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), listOf(
            turn.inputTokens,
            turn.outputTokens,
            turn.cacheReadTokens,
            turn.cacheWrite5mTokens,
            turn.cacheWrite1hTokens
        ))
    }
}
