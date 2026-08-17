package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.CliQuotaWindows
import com.usagemonitor.domain.entity.CliSessionDetail
import com.usagemonitor.domain.entity.CliSessionRange
import com.usagemonitor.domain.entity.CliSessionSummary
import com.usagemonitor.domain.entity.CliSessionTurn
import com.usagemonitor.domain.entity.CliUsageGroupRow
import com.usagemonitor.domain.entity.TeamAccountDeletion
import com.usagemonitor.domain.entity.TeamAccountUsage
import com.usagemonitor.domain.entity.TeamIngestPayload
import com.usagemonitor.domain.entity.TeamIngestReceipt
import com.usagemonitor.domain.entity.TeamKeyEntry
import com.usagemonitor.domain.entity.TeamKeyVerification
import com.usagemonitor.domain.entity.TeamMemberIdentity
import com.usagemonitor.domain.entity.TeamMemberUsage
import com.usagemonitor.domain.entity.TeamPresenceReceipt
import com.usagemonitor.domain.entity.TeamUsageSnapshot
import com.usagemonitor.domain.repository.TeamAdminRepository
import com.usagemonitor.domain.repository.TeamUsageRepository
import com.usagemonitor.domain.repository.TeamUsageTrendData
import com.usagemonitor.domain.usecase.GetAdminTeamOverviewUseCase
import com.usagemonitor.domain.usecase.GetTeamSessionDetailUseCase
import com.usagemonitor.domain.usecase.GetTeamUsageUseCase
import com.usagemonitor.domain.usecase.RemoveTeamMemberUseCase
import com.usagemonitor.presentation.viewmodel.TeamSessionDetailUiState
import com.usagemonitor.presentation.viewmodel.TeamUsageUiState
import com.usagemonitor.presentation.viewmodel.TeamUsageView
import com.usagemonitor.presentation.viewmodel.TeamUsageViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private val TEAM_FIXED_NOW = Instant.parse("2026-08-11T12:00:00Z")
private const val TEAM_FIVE_HOURS_MILLIS = 5L * 60 * 60 * 1_000
private const val TEAM_THIRTY_DAYS_MILLIS = 30L * 24 * 60 * 60 * 1_000
private const val TEAM_LIVE_INTERVAL_MILLIS = 5_000L
private const val ACCOUNT_KEY = "account-uuid-aaa"

private class FakeTeamRepository(
    var snapshot: TeamUsageSnapshot = TeamUsageSnapshot()
) : TeamUsageRepository {
    var fetchCalls = 0
    var lastCutoffMillis: Long? = null
    var lastAccountKey: String? = null
    var fetchResult: Result<TeamUsageSnapshot>? = null
    val removedDeviceIds = mutableListOf<String>()

    /** Conta e máquina de cada remoção; a conta importa na visão global. */
    val removedTargets = mutableListOf<Pair<String, String>>()
    var removeResult: Result<Unit> = Result.success(Unit)

    override suspend fun push(payload: TeamIngestPayload): Result<TeamIngestReceipt> {
        return Result.success(TeamIngestReceipt())
    }

    override suspend fun touchPresence(
        accountKey: String,
        member: TeamMemberIdentity
    ): Result<TeamPresenceReceipt> {
        return Result.success(TeamPresenceReceipt())
    }

    override suspend fun removeMember(accountKey: String, deviceId: String): Result<Unit> {
        if (removeResult.isSuccess) {
            removedDeviceIds += deviceId
            removedTargets += accountKey to deviceId
        }
        return removeResult
    }

    override suspend fun fetch(accountKey: String, cutoffMillis: Long?): Result<TeamUsageSnapshot> {
        fetchCalls += 1
        lastAccountKey = accountKey
        lastCutoffMillis = cutoffMillis
        return fetchResult ?: Result.success(snapshot)
    }

    /** `success(null)` é o que o repositório real devolve num `404` do servidor. */
    var detailResult: Result<CliSessionDetail?> = Result.success(null)
    var detailCalls = 0
    var lastDetailDeviceId: String? = null
    var lastDetailSessionId: String? = null
    var lastDetailAccountKey: String? = null

    override suspend fun fetchSessionDetail(
        accountKey: String,
        deviceId: String,
        sessionId: String
    ): Result<CliSessionDetail?> {
        detailCalls += 1
        lastDetailDeviceId = deviceId
        lastDetailSessionId = sessionId
        lastDetailAccountKey = accountKey
        return detailResult
    }

    override suspend fun checkConnection(): Result<Unit> = Result.success(Unit)

    override suspend fun fetchTrend(accountKey: String, days: Int): Result<TeamUsageTrendData?> {
        return Result.success(null)
    }
}

private class TeamFixedClock(private val fixedNow: Instant) : Clock {
    override fun now(): Instant = fixedNow
}

/** Anda junto com o tempo virtual: avançar o laço e avançar o relógio viram um gesto só. */
private class TeamSchedulerClock(
    private val origin: Instant,
    private val scheduler: kotlinx.coroutines.test.TestCoroutineScheduler
) : Clock {
    override fun now(): Instant = origin + scheduler.currentTime.milliseconds
}

private fun session(id: String, tokens: Long = 0L, cost: Long = 0L): CliSessionSummary {
    return CliSessionSummary(
        sessionId = id,
        filePath = "",
        firstTs = TEAM_FIXED_NOW,
        lastTs = TEAM_FIXED_NOW,
        inputTokens = tokens,
        costMicros = cost
    )
}

private fun member(
    deviceId: String,
    alias: String = deviceId,
    sessions: List<CliSessionSummary> = emptyList()
): TeamMemberUsage {
    return TeamMemberUsage(deviceId = deviceId, alias = alias, sessions = sessions)
}

private const val OPUS = "claude-opus-5-20260201"

/** Detalhe como o servidor de time o entrega, já mapeado para o domínio. */
private fun detail(sessionId: String, cacheReadTokens: Long = 0L): CliSessionDetail {
    return CliSessionDetail(
        summary = session(sessionId).copy(primaryModel = OPUS, turnCount = 1),
        turns = listOf(
            CliSessionTurn(
                sessionId = sessionId,
                seq = 0,
                messageId = "msg-1",
                ts = TEAM_FIXED_NOW,
                model = OPUS,
                cacheReadTokens = cacheReadTokens
            )
        )
    )
}

@OptIn(ExperimentalCoroutinesApi::class)
class TeamUsageViewModelTest {

    @Test
    fun `comeca em Loading antes de abrir uma conta`() = runTest {
        val viewModel = buildViewModel(FakeTeamRepository())

        assertIs<TeamUsageUiState.Loading>(viewModel.uiState.value)
        viewModel.onDestroy()
    }

    @Test
    fun `carrega os integrantes da conta aberta`() = runTest {
        val repository = FakeTeamRepository(
            snapshot = TeamUsageSnapshot(
                members = listOf(
                    member("device-1", "edilson", listOf(session("s1", tokens = 100L))),
                    member("device-2", "maria", listOf(session("s2", tokens = 50L)))
                )
            )
        )
        val viewModel = buildViewModel(repository)
        viewModel.openForAccount(ACCOUNT_KEY, "empresa@x.com")
        runCurrent()

        val state = assertIs<TeamUsageUiState.Success>(viewModel.uiState.value)
        assertEquals(listOf("edilson", "maria"), state.members.map { it.alias })
        assertEquals(150L, state.totalTokens)
        assertEquals(ACCOUNT_KEY, repository.lastAccountKey)
        viewModel.onDestroy()
    }

    @Test
    fun `remover integrante apaga no servidor e recarrega a lista`() = runTest {
        val repository = FakeTeamRepository(
            snapshot = TeamUsageSnapshot(
                members = listOf(
                    member("device-1", "edilson", listOf(session("s1", tokens = 100L))),
                    member("device-2", "fantasma")
                )
            )
        )
        val viewModel = buildViewModel(repository)
        viewModel.openForAccount(ACCOUNT_KEY, null)
        runCurrent()

        repository.snapshot = TeamUsageSnapshot(
            members = listOf(member("device-1", "edilson", listOf(session("s1", tokens = 100L))))
        )
        viewModel.removeMember("device-2")
        runCurrent()

        assertEquals(listOf("device-2"), repository.removedDeviceIds)
        // A lista só muda pelo que o servidor devolve depois: tirar da memória
        // mostraria como removido quem ainda pode estar lá.
        val state = assertIs<TeamUsageUiState.Success>(viewModel.uiState.value)
        assertEquals(listOf("edilson"), state.members.map { it.alias })
        assertEquals(null, viewModel.removalError.value)
        viewModel.onDestroy()
    }

    @Test
    fun `falha ao remover vira aviso e mantem a lista`() = runTest {
        val repository = FakeTeamRepository(
            snapshot = TeamUsageSnapshot(members = listOf(member("device-1", "edilson")))
        )
        val viewModel = buildViewModel(repository)
        viewModel.openForAccount(ACCOUNT_KEY, null)
        runCurrent()

        repository.removeResult = Result.failure(IllegalStateException("servidor fora do ar"))
        viewModel.removeMember("device-1")
        runCurrent()

        assertEquals("servidor fora do ar", viewModel.removalError.value)
        val state = assertIs<TeamUsageUiState.Success>(viewModel.uiState.value)
        assertEquals(listOf("edilson"), state.members.map { it.alias })

        viewModel.clearRemovalError()
        assertEquals(null, viewModel.removalError.value)
        viewModel.onDestroy()
    }

    @Test
    fun `abre com o filtro de 5h`() = runTest {
        val repository = FakeTeamRepository()
        val viewModel = buildViewModel(repository)
        viewModel.openForAccount(ACCOUNT_KEY, null)
        runCurrent()

        val state = assertIs<TeamUsageUiState.Success>(viewModel.uiState.value)
        assertEquals(CliSessionRange.LAST_5H, state.range)
        assertEquals(TEAM_FIXED_NOW.toEpochMilliseconds() - TEAM_FIVE_HOURS_MILLIS, repository.lastCutoffMillis)
        viewModel.onDestroy()
    }

    @Test
    fun `trocar de janela repassa o novo corte`() = runTest {
        val repository = FakeTeamRepository()
        val viewModel = buildViewModel(repository)
        viewModel.openForAccount(ACCOUNT_KEY, null)
        runCurrent()

        viewModel.setRange(CliSessionRange.LAST_30D)
        runCurrent()

        assertEquals(TEAM_FIXED_NOW.toEpochMilliseconds() - TEAM_THIRTY_DAYS_MILLIS, repository.lastCutoffMillis)

        viewModel.setRange(CliSessionRange.ALL)
        runCurrent()

        assertEquals(null, repository.lastCutoffMillis)
        viewModel.onDestroy()
    }

    @Test
    fun `a janela de 5h ancora no reset da quota`() = runTest {
        val repository = FakeTeamRepository()
        val viewModel = buildViewModel(repository)
        val resetAt = TEAM_FIXED_NOW.plus(2.seconds)

        viewModel.openForAccount(ACCOUNT_KEY, null, CliQuotaWindows(fiveHourEndsAt = resetAt))
        runCurrent()

        // O mesmo corte que o modal da máquina aplica: sem isso os números do
        // time não fecham com os locais.
        assertEquals(resetAt.toEpochMilliseconds() - TEAM_FIVE_HOURS_MILLIS, repository.lastCutoffMillis)
        val state = assertIs<TeamUsageUiState.Success>(viewModel.uiState.value)
        assertTrue(state.rangeAnchored)
        viewModel.onDestroy()
    }

    /**
     * Issue #35: com o reset vencido o corte voltava a `now - 5h` e a tela do
     * time listava sessões de antes do reset junto com a janela nova.
     */
    @Test
    fun `um reset vencido corta no proprio reset`() = runTest {
        val repository = FakeTeamRepository()
        val viewModel = buildViewModel(repository)
        val expiredResetAt = TEAM_FIXED_NOW.minus(30.minutes)

        viewModel.openForAccount(ACCOUNT_KEY, null, CliQuotaWindows(fiveHourEndsAt = expiredResetAt))
        runCurrent()

        assertEquals(expiredResetAt.toEpochMilliseconds(), repository.lastCutoffMillis)
        val state = assertIs<TeamUsageUiState.Success>(viewModel.uiState.value)
        assertNull(state.rangeEndsAt)
        assertTrue(state.rangeAnchored)
        viewModel.onDestroy()
    }

    /**
     * `setQuotaWindows` só recarrega quando o valor muda, e o `fiveHourEndsAt`
     * não muda ao vencer: quem vira a chave é o tique do laço ao vivo.
     */
    @Test
    fun `o laco ao vivo reancora a janela quando o reset vence`() = runTest {
        val repository = FakeTeamRepository()
        val resetAt = TEAM_FIXED_NOW.plus(2.seconds)
        val viewModel = buildViewModel(
            repository = repository,
            useCaseClock = TeamSchedulerClock(TEAM_FIXED_NOW, testScheduler)
        )

        viewModel.openForAccount(ACCOUNT_KEY, null, CliQuotaWindows(fiveHourEndsAt = resetAt))
        runCurrent()
        assertEquals(resetAt.toEpochMilliseconds() - TEAM_FIVE_HOURS_MILLIS, repository.lastCutoffMillis)

        advanceTimeBy(TEAM_LIVE_INTERVAL_MILLIS)
        runCurrent()

        assertEquals(resetAt.toEpochMilliseconds(), repository.lastCutoffMillis)
        val state = assertIs<TeamUsageUiState.Success>(viewModel.uiState.value)
        assertNull(state.rangeEndsAt)
        assertTrue(state.rangeAnchored)
        viewModel.onDestroy()
    }

    @Test
    fun `o laco ao vivo consulta o servidor a cada intervalo`() = runTest {
        val repository = FakeTeamRepository()
        val viewModel = buildViewModel(repository)
        viewModel.openForAccount(ACCOUNT_KEY, null)
        runCurrent()

        val afterOpen = repository.fetchCalls
        advanceTimeBy(3 * TEAM_LIVE_INTERVAL_MILLIS + 1)
        runCurrent()

        assertEquals(afterOpen + 3, repository.fetchCalls)
        viewModel.onDestroy()
    }

    @Test
    fun `fechar a janela para o laco ao vivo`() = runTest {
        val repository = FakeTeamRepository()
        val viewModel = buildViewModel(repository)
        viewModel.openForAccount(ACCOUNT_KEY, null)
        runCurrent()
        viewModel.closeWindow()

        val afterClose = repository.fetchCalls
        advanceTimeBy(5 * TEAM_LIVE_INTERVAL_MILLIS)
        runCurrent()

        assertEquals(afterClose, repository.fetchCalls)
        viewModel.onDestroy()
    }

    @Test
    fun `o carimbo de alteracao nao anda quando nada muda`() = runTest {
        val repository = FakeTeamRepository(
            snapshot = TeamUsageSnapshot(members = listOf(member("device-1", sessions = listOf(session("s1")))))
        )
        val viewModel = buildViewModel(repository)
        viewModel.openForAccount(ACCOUNT_KEY, null)
        runCurrent()

        val first = assertIs<TeamUsageUiState.Success>(viewModel.uiState.value)
        advanceTimeBy(2 * TEAM_LIVE_INTERVAL_MILLIS + 1)
        runCurrent()

        val second = assertIs<TeamUsageUiState.Success>(viewModel.uiState.value)
        assertEquals(first.lastChangedAt, second.lastChangedAt)
        viewModel.onDestroy()
    }

    @Test
    fun `grupo expandido sobrevive aos tiques do tempo real`() = runTest {
        val repository = FakeTeamRepository(
            snapshot = TeamUsageSnapshot(members = listOf(member("device-1", sessions = listOf(session("s1")))))
        )
        val viewModel = buildViewModel(repository)
        viewModel.openForAccount(ACCOUNT_KEY, null)
        runCurrent()

        viewModel.toggleMember("device-1")
        advanceTimeBy(3 * TEAM_LIVE_INTERVAL_MILLIS + 1)
        runCurrent()

        val state = assertIs<TeamUsageUiState.Success>(viewModel.uiState.value)
        assertEquals(setOf("device-1"), state.expandedMemberKeys)
        viewModel.onDestroy()
    }

    @Test
    fun `a aba escolhida sobrevive aos tiques do tempo real`() = runTest {
        val repository = FakeTeamRepository(
            snapshot = TeamUsageSnapshot(members = listOf(member("device-1", sessions = listOf(session("s1")))))
        )
        val viewModel = buildViewModel(repository)
        viewModel.openForAccount(ACCOUNT_KEY, null)
        runCurrent()

        viewModel.setView(TeamUsageView.BREAKDOWN)
        advanceTimeBy(3 * TEAM_LIVE_INTERVAL_MILLIS + 1)
        runCurrent()

        // Sem carregar a aba do estado anterior, o tique de 5s devolveria à lista
        // quem está lendo o resumo.
        val state = assertIs<TeamUsageUiState.Success>(viewModel.uiState.value)
        assertEquals(TeamUsageView.BREAKDOWN, state.view)
        viewModel.onDestroy()
    }

    @Test
    fun `o resumo por eixo vem junto com a lista e soma o mesmo custo`() = runTest {
        val repository = FakeTeamRepository(
            snapshot = TeamUsageSnapshot(
                members = listOf(
                    member(
                        "device-1",
                        "edilson",
                        sessions = listOf(session("s1", tokens = 1_000L, cost = 5_000L))
                    ).copy(
                        groupRows = listOf(
                            CliUsageGroupRow(
                                sessionId = "s1",
                                cwd = "/home/dev/alpha",
                                gitBranch = "main",
                                model = OPUS,
                                turnCount = 2,
                                inputTokens = 1_000L
                            )
                        )
                    )
                )
            )
        )
        val viewModel = buildViewModel(repository)
        viewModel.openForAccount(ACCOUNT_KEY, null)
        runCurrent()

        // Vem da mesma resposta que os integrantes: nenhuma segunda consulta.
        assertEquals(1, repository.fetchCalls)
        val state = assertIs<TeamUsageUiState.Success>(viewModel.uiState.value)
        val breakdown = assertNotNull(state.breakdown)
        assertEquals(listOf("alpha"), breakdown.byProject.mapNotNull { bucket -> bucket.label })
        assertEquals(listOf("edilson"), breakdown.byMember.mapNotNull { bucket -> bucket.label })
        assertEquals(2, breakdown.totals.turnCount)
        viewModel.onDestroy()
    }

    @Test
    fun `a visao global nao oferece a aba de tendencia`() = runTest {
        val repository = FakeTeamRepository()
        val admin = FakeAdminOverviewRepository(
            accounts = listOf(overviewAccount(ACCOUNT_KEY, "fulano@empresa.com", "device-1", tokens = 10))
        )
        val viewModel = buildViewModel(repository, adminRepository = admin)
        viewModel.openForAllAccounts()
        runCurrent()

        // A série é por conta e a visão global mistura várias: um chip que nunca
        // mostraria nada é pior que chip nenhum.
        val state = assertIs<TeamUsageUiState.Success>(viewModel.uiState.value)
        assertFalse(state.isTrendAvailable)
        viewModel.onDestroy()
    }

    @Test
    fun `aba de tendencia guardada cai na lista quando ela deixa de existir`() = runTest {
        val repository = FakeTeamRepository(
            snapshot = TeamUsageSnapshot(members = listOf(member("device-1", sessions = listOf(session("s1")))))
        )
        val admin = FakeAdminOverviewRepository(
            accounts = listOf(overviewAccount(ACCOUNT_KEY, "fulano@empresa.com", "device-1", tokens = 10))
        )
        val viewModel = buildViewModel(repository, adminRepository = admin)
        viewModel.openForAccount(ACCOUNT_KEY, null)
        runCurrent()
        viewModel.setView(TeamUsageView.TREND)

        viewModel.openForAllAccounts()
        runCurrent()

        // Desenhar painel nenhum seria uma janela vazia sem explicação.
        val state = assertIs<TeamUsageUiState.Success>(viewModel.uiState.value)
        assertEquals(TeamUsageView.MEMBERS, state.effectiveView)
        viewModel.onDestroy()
    }

    @Test
    fun `integrante que sumiu da resposta sai do conjunto expandido`() = runTest {
        val repository = FakeTeamRepository(
            snapshot = TeamUsageSnapshot(
                members = listOf(
                    member("device-1", sessions = listOf(session("s1"))),
                    member("device-2", sessions = listOf(session("s2")))
                )
            )
        )
        val viewModel = buildViewModel(repository)
        viewModel.openForAccount(ACCOUNT_KEY, null)
        runCurrent()

        viewModel.toggleMember("device-1")
        viewModel.toggleMember("device-2")

        repository.snapshot = TeamUsageSnapshot(members = listOf(member("device-1", sessions = listOf(session("s1")))))
        advanceTimeBy(TEAM_LIVE_INTERVAL_MILLIS + 1)
        runCurrent()

        val state = assertIs<TeamUsageUiState.Success>(viewModel.uiState.value)
        assertEquals(setOf("device-1"), state.expandedMemberKeys)
        viewModel.onDestroy()
    }

    @Test
    fun `falha na primeira carga vira estado de erro`() = runTest {
        val repository = FakeTeamRepository()
        repository.fetchResult = Result.failure(IllegalStateException("servidor fora do ar"))
        val viewModel = buildViewModel(repository)
        viewModel.openForAccount(ACCOUNT_KEY, null)
        runCurrent()

        val state = assertIs<TeamUsageUiState.Error>(viewModel.uiState.value)
        assertEquals("servidor fora do ar", state.message)
        viewModel.onDestroy()
    }

    @Test
    fun `falha depois de carregar nao apaga a lista da tela`() = runTest {
        val repository = FakeTeamRepository(
            snapshot = TeamUsageSnapshot(members = listOf(member("device-1", sessions = listOf(session("s1")))))
        )
        val viewModel = buildViewModel(repository)
        viewModel.openForAccount(ACCOUNT_KEY, null)
        runCurrent()

        repository.fetchResult = Result.failure(IllegalStateException("timeout"))
        advanceTimeBy(TEAM_LIVE_INTERVAL_MILLIS + 1)
        runCurrent()

        // O usuário está lendo a tela: uma falha intermitente não pode arrancar
        // o conteúdo dela.
        val state = assertIs<TeamUsageUiState.Success>(viewModel.uiState.value)
        assertEquals(1, state.members.size)
        viewModel.onDestroy()
    }

    @Test
    fun `custo incompleto de um integrante torna o total incompleto`() = runTest {
        val repository = FakeTeamRepository(
            snapshot = TeamUsageSnapshot(
                members = listOf(
                    member("device-1", sessions = listOf(session("s1", cost = 10L))),
                    member(
                        "device-2",
                        sessions = listOf(session("s2", cost = 5L).copy(unpricedTurnCount = 2))
                    )
                )
            )
        )
        val viewModel = buildViewModel(repository)
        viewModel.openForAccount(ACCOUNT_KEY, null)
        runCurrent()

        val state = assertIs<TeamUsageUiState.Success>(viewModel.uiState.value)
        assertFalse(state.isTotalCostComplete)
        assertEquals(15L, state.totalCostMicros)
        viewModel.onDestroy()
    }

    @Test
    fun `trocar de conta limpa a lista antes de carregar a nova`() = runTest {
        val repository = FakeTeamRepository(
            snapshot = TeamUsageSnapshot(members = listOf(member("device-1", sessions = listOf(session("s1")))))
        )
        val viewModel = buildViewModel(repository)
        viewModel.openForAccount(ACCOUNT_KEY, "conta-1")
        runCurrent()
        assertIs<TeamUsageUiState.Success>(viewModel.uiState.value)

        repository.fetchResult = Result.failure(IllegalStateException("conta desconhecida"))
        viewModel.openForAccount("outra-conta", "conta-2")

        // Sem o reset, os integrantes da conta anterior continuariam na tela
        // enquanto a nova carrega — dados de outro time.
        val state = assertIs<TeamUsageUiState.Error>(viewModel.uiState.value)
        assertEquals("conta desconhecida", state.message)
        viewModel.onDestroy()
    }

    @Test
    fun `contagem de integrantes ativos ignora quem nao usou no periodo`() = runTest {
        val repository = FakeTeamRepository(
            snapshot = TeamUsageSnapshot(
                members = listOf(
                    member("device-1", sessions = listOf(session("s1", tokens = 10L))),
                    member("device-2")
                )
            )
        )
        val viewModel = buildViewModel(repository)
        viewModel.openForAccount(ACCOUNT_KEY, null)
        runCurrent()

        val state = assertIs<TeamUsageUiState.Success>(viewModel.uiState.value)
        assertEquals(2, state.members.size)
        assertEquals(1, state.activeMemberCount)
        assertFalse(state.isEmpty)
        viewModel.onDestroy()
    }

    @Test
    fun `abrir uma sessao carrega o detalhe daquela maquina`() = runTest {
        val repository = FakeTeamRepository(
            snapshot = TeamUsageSnapshot(members = listOf(member("device-1", sessions = listOf(session("s1")))))
        )
        repository.detailResult = Result.success(detail("s1", cacheReadTokens = 10_000L))
        val viewModel = buildViewModel(repository)
        viewModel.openForAccount(ACCOUNT_KEY, null)
        runCurrent()

        viewModel.openSession(memberKey = "device-1", sessionId = "s1")
        runCurrent()

        assertEquals("device-1", repository.lastDetailDeviceId)
        assertEquals("s1", repository.lastDetailSessionId)
        val state = assertIs<TeamUsageUiState.Success>(viewModel.uiState.value)
        val ready = assertIs<TeamSessionDetailUiState.Ready>(state.detail)
        assertFalse(ready.turnsUnavailable)
        assertEquals(1, ready.result.detail.turns.size)
        assertEquals(listOf(10_000L), ready.result.analytics.contextPerTurn)
        viewModel.onDestroy()
    }

    @Test
    fun `o detalhe aberto sobrevive aos tiques do tempo real`() = runTest {
        val repository = FakeTeamRepository(
            snapshot = TeamUsageSnapshot(members = listOf(member("device-1", sessions = listOf(session("s1")))))
        )
        repository.detailResult = Result.success(detail("s1"))
        val viewModel = buildViewModel(repository)
        viewModel.openForAccount(ACCOUNT_KEY, null)
        runCurrent()

        viewModel.openSession(memberKey = "device-1", sessionId = "s1")
        runCurrent()
        advanceTimeBy(3 * TEAM_LIVE_INTERVAL_MILLIS + 1)
        runCurrent()

        // Sem carregar o detalhe do estado anterior em `loadTeam`, o tique de 5s
        // fecharia o painel na cara de quem está lendo.
        val state = assertIs<TeamUsageUiState.Success>(viewModel.uiState.value)
        val ready = assertIs<TeamSessionDetailUiState.Ready>(state.detail)
        assertEquals("s1", ready.sessionId)
        viewModel.onDestroy()
    }

    @Test
    fun `os blocos recolhiveis do detalhe sobrevivem aos tiques`() = runTest {
        val repository = FakeTeamRepository(
            snapshot = TeamUsageSnapshot(members = listOf(member("device-1", sessions = listOf(session("s1")))))
        )
        repository.detailResult = Result.success(detail("s1"))
        val viewModel = buildViewModel(repository)
        viewModel.openForAccount(ACCOUNT_KEY, null)
        runCurrent()

        viewModel.openSession(memberKey = "device-1", sessionId = "s1")
        runCurrent()
        viewModel.toggleAdvanced()
        viewModel.toggleGlossary()
        advanceTimeBy(2 * TEAM_LIVE_INTERVAL_MILLIS + 1)
        runCurrent()

        val state = assertIs<TeamUsageUiState.Success>(viewModel.uiState.value)
        assertTrue(state.advancedExpanded)
        assertTrue(state.glossaryExpanded)
        viewModel.onDestroy()
    }

    @Test
    fun `servidor sem a rota de detalhe cai no agregado em vez de quebrar`() = runTest {
        val repository = FakeTeamRepository(
            snapshot = TeamUsageSnapshot(
                members = listOf(
                    member(
                        "device-1",
                        sessions = listOf(
                            session("s1", tokens = 400L, cost = 1_000L)
                                .copy(liveContextTokens = 650_000L, liveContextModel = OPUS, primaryModel = OPUS)
                        )
                    )
                )
            )
        )
        // O repositório real converte o 404 do servidor em `success(null)`: para o
        // cliente, rota ausente e sessão ausente são o mesmo desfecho.
        repository.detailResult = Result.success(null)
        val viewModel = buildViewModel(repository)
        viewModel.openForAccount(ACCOUNT_KEY, null)
        runCurrent()

        viewModel.openSession(memberKey = "device-1", sessionId = "s1")
        runCurrent()

        val state = assertIs<TeamUsageUiState.Success>(viewModel.uiState.value)
        val ready = assertIs<TeamSessionDetailUiState.Ready>(state.detail)
        assertTrue(ready.turnsUnavailable)
        assertTrue(ready.result.detail.turns.isEmpty())
        // O veredito de contexto sai do resumo e continua exato.
        assertEquals(0.65, ready.result.analytics.contextSaturation)
        // O que só o turno prova fica em zero e não é exibido.
        assertTrue(ready.result.analytics.contextPerTurn.isEmpty())
        viewModel.onDestroy()
    }

    @Test
    fun `sessao desconhecida e sem detalhe no servidor vira erro`() = runTest {
        val repository = FakeTeamRepository(
            snapshot = TeamUsageSnapshot(members = listOf(member("device-1", sessions = listOf(session("s1")))))
        )
        repository.detailResult = Result.success(null)
        val viewModel = buildViewModel(repository)
        viewModel.openForAccount(ACCOUNT_KEY, null)
        runCurrent()

        // Sem agregado na lista não há nada a apresentar no lugar dos turnos.
        viewModel.openSession(memberKey = "device-1", sessionId = "s-que-nao-existe")
        runCurrent()

        val state = assertIs<TeamUsageUiState.Success>(viewModel.uiState.value)
        assertIs<TeamSessionDetailUiState.Error>(state.detail)
        viewModel.onDestroy()
    }

    @Test
    fun `falha de rede no detalhe vira erro do painel e nao da lista`() = runTest {
        val repository = FakeTeamRepository(
            snapshot = TeamUsageSnapshot(members = listOf(member("device-1", sessions = listOf(session("s1")))))
        )
        repository.detailResult = Result.failure(IllegalStateException("timeout"))
        val viewModel = buildViewModel(repository)
        viewModel.openForAccount(ACCOUNT_KEY, null)
        runCurrent()

        viewModel.openSession(memberKey = "device-1", sessionId = "s1")
        runCurrent()

        val state = assertIs<TeamUsageUiState.Success>(viewModel.uiState.value)
        val error = assertIs<TeamSessionDetailUiState.Error>(state.detail)
        assertEquals("timeout", error.message)
        assertEquals(1, state.members.size)
        viewModel.onDestroy()
    }

    @Test
    fun `fechar o detalhe volta para a lista`() = runTest {
        val repository = FakeTeamRepository(
            snapshot = TeamUsageSnapshot(members = listOf(member("device-1", sessions = listOf(session("s1")))))
        )
        repository.detailResult = Result.success(detail("s1"))
        val viewModel = buildViewModel(repository)
        viewModel.openForAccount(ACCOUNT_KEY, null)
        runCurrent()

        viewModel.openSession(memberKey = "device-1", sessionId = "s1")
        runCurrent()
        viewModel.closeDetail()

        val state = assertIs<TeamUsageUiState.Success>(viewModel.uiState.value)
        assertEquals(null, state.detail)
        viewModel.onDestroy()
    }

    @Test
    fun `fechar a janela descarta o detalhe aberto`() = runTest {
        val repository = FakeTeamRepository(
            snapshot = TeamUsageSnapshot(members = listOf(member("device-1", sessions = listOf(session("s1")))))
        )
        repository.detailResult = Result.success(detail("s1"))
        val viewModel = buildViewModel(repository)
        viewModel.openForAccount(ACCOUNT_KEY, null)
        runCurrent()

        viewModel.openSession(memberKey = "device-1", sessionId = "s1")
        runCurrent()
        viewModel.closeWindow()

        val state = assertIs<TeamUsageUiState.Success>(viewModel.uiState.value)
        assertEquals(null, state.detail)
        viewModel.onDestroy()
    }

    /** Compartilha o `testScheduler` do `runTest`, senão `advanceTimeBy` não move o laço. */
    private fun TestScope.buildViewModel(
        repository: FakeTeamRepository,
        // O corte da janela é resolvido dentro do caso de uso; separá-lo do
        // relógio do carimbo permite mover o tempo só onde interessa.
        useCaseClock: Clock = TeamFixedClock(TEAM_FIXED_NOW),
        adminRepository: FakeAdminOverviewRepository? = null
    ): TeamUsageViewModel {
        return TeamUsageViewModel(
            getTeamUsage = GetTeamUsageUseCase(repository, useCaseClock),
            removeTeamMember = RemoveTeamMemberUseCase(repository),
            getTeamSessionDetail = GetTeamSessionDetailUseCase(repository),
            getAdminOverview = adminRepository?.let { admin ->
                GetAdminTeamOverviewUseCase(admin, useCaseClock)
            },
            dispatcher = UnconfinedTestDispatcher(testScheduler),
            liveIntervalMillis = TEAM_LIVE_INTERVAL_MILLIS,
            clock = TeamFixedClock(TEAM_FIXED_NOW)
        )
    }

    @Test
    fun `visao global agrupa contas e carimba cada integrante`() = runTest {
        val repository = FakeTeamRepository()
        val admin = FakeAdminOverviewRepository(
            accounts = listOf(
                overviewAccount(ACCOUNT_KEY, "fulano@empresa.com", "device-1", tokens = 10),
                overviewAccount(OTHER_ACCOUNT_KEY, null, "device-2", tokens = 30)
            )
        )
        val viewModel = buildViewModel(repository, adminRepository = admin)

        viewModel.openForAllAccounts()
        runCurrent()

        val state = assertIs<TeamUsageUiState.Success>(viewModel.uiState.value)
        assertTrue(state.isAdminOverview)
        // Ordena pelo consumo do time inteiro, não por conta.
        assertEquals("device-2", state.members.first().deviceId)
        assertEquals(OTHER_ACCOUNT_KEY, state.members.first().accountKey)
        assertEquals(2, state.memberGroups.size)
        assertEquals("fulano@empresa.com", state.memberGroups.last().accountLabel)
        assertEquals(0, repository.fetchCalls)
        viewModel.onDestroy()
    }

    /** Issue #45: dezenas de máquinas de uma vez escondem a comparação entre contas. */
    @Test
    fun `visao global abre com as contas recolhidas`() = runTest {
        val repository = FakeTeamRepository()
        val admin = FakeAdminOverviewRepository(
            accounts = listOf(
                overviewAccount(ACCOUNT_KEY, "fulano@empresa.com", "device-1", tokens = 10),
                overviewAccount(OTHER_ACCOUNT_KEY, null, "device-2", tokens = 30)
            )
        )
        val viewModel = buildViewModel(repository, adminRepository = admin)

        viewModel.openForAllAccounts()
        runCurrent()

        val state = assertIs<TeamUsageUiState.Success>(viewModel.uiState.value)
        assertEquals(emptySet(), state.expandedAccountKeys)
        assertTrue(state.memberGroups.none { group -> state.isAccountExpanded(group) })
        viewModel.onDestroy()
    }

    @Test
    fun `conta expandida sobrevive aos tiques do tempo real`() = runTest {
        val repository = FakeTeamRepository()
        val admin = FakeAdminOverviewRepository(
            accounts = listOf(
                overviewAccount(ACCOUNT_KEY, "fulano@empresa.com", "device-1", tokens = 10),
                overviewAccount(OTHER_ACCOUNT_KEY, null, "device-2", tokens = 30)
            )
        )
        val viewModel = buildViewModel(repository, adminRepository = admin)
        viewModel.openForAllAccounts()
        runCurrent()

        viewModel.toggleAccount(ACCOUNT_KEY)
        advanceTimeBy(3 * TEAM_LIVE_INTERVAL_MILLIS + 1)
        runCurrent()

        val state = assertIs<TeamUsageUiState.Success>(viewModel.uiState.value)
        assertEquals(setOf(ACCOUNT_KEY), state.expandedAccountKeys)
        val expandedGroup = state.memberGroups.first { group -> group.accountKey == ACCOUNT_KEY }
        assertTrue(state.isAccountExpanded(expandedGroup))

        viewModel.toggleAccount(ACCOUNT_KEY)
        runCurrent()
        val collapsed = assertIs<TeamUsageUiState.Success>(viewModel.uiState.value)
        assertEquals(emptySet(), collapsed.expandedAccountKeys)
        viewModel.onDestroy()
    }

    /** O modal de uma conta não tem faixa para clicar: recolher não existe lá. */
    @Test
    fun `modal de uma conta mostra os integrantes sem depender do conjunto expandido`() = runTest {
        val repository = FakeTeamRepository(
            snapshot = TeamUsageSnapshot(members = listOf(member("device-1", sessions = listOf(session("s1")))))
        )
        val viewModel = buildViewModel(repository)
        viewModel.openForAccount(ACCOUNT_KEY, null)
        runCurrent()

        val state = assertIs<TeamUsageUiState.Success>(viewModel.uiState.value)
        assertEquals(emptySet(), state.expandedAccountKeys)
        assertTrue(state.memberGroups.all { group -> state.isAccountExpanded(group) })
        viewModel.onDestroy()
    }

    @Test
    fun `visao global remove o integrante na conta dele`() = runTest {
        val repository = FakeTeamRepository()
        val admin = FakeAdminOverviewRepository(
            accounts = listOf(
                overviewAccount(ACCOUNT_KEY, "fulano@empresa.com", "device-1", tokens = 10),
                overviewAccount(OTHER_ACCOUNT_KEY, null, "device-1", tokens = 30)
            )
        )
        val viewModel = buildViewModel(repository, adminRepository = admin)
        viewModel.openForAllAccounts()
        runCurrent()

        // Mesma máquina em duas contas: o `deviceId` sozinho não identifica a
        // linha, e usar a conta da janela apagaria o histórico da conta errada.
        viewModel.removeMember("$OTHER_ACCOUNT_KEY/device-1")
        runCurrent()

        assertEquals(listOf(OTHER_ACCOUNT_KEY to "device-1"), repository.removedTargets)
        viewModel.onDestroy()
    }

    @Test
    fun `visao global abre o detalhe na conta do integrante`() = runTest {
        val repository = FakeTeamRepository()
        val admin = FakeAdminOverviewRepository(
            accounts = listOf(
                overviewAccount(ACCOUNT_KEY, "fulano@empresa.com", "device-1", tokens = 10),
                overviewAccount(OTHER_ACCOUNT_KEY, null, "device-1", tokens = 30)
            )
        )
        val viewModel = buildViewModel(repository, adminRepository = admin)
        viewModel.openForAllAccounts()
        runCurrent()

        viewModel.openSession(memberKey = "$ACCOUNT_KEY/device-1", sessionId = "s1")
        runCurrent()

        assertEquals(ACCOUNT_KEY, repository.lastDetailAccountKey)
        val state = assertIs<TeamUsageUiState.Success>(viewModel.uiState.value)
        assertEquals(ACCOUNT_KEY, state.detail?.accountKey)
        viewModel.onDestroy()
    }

    @Test
    fun `trocar da visao global para uma conta zera a lista`() = runTest {
        val repository = FakeTeamRepository(
            snapshot = TeamUsageSnapshot(members = listOf(member("device-9")))
        )
        val admin = FakeAdminOverviewRepository(
            accounts = listOf(overviewAccount(ACCOUNT_KEY, "fulano@empresa.com", "device-1"))
        )
        val viewModel = buildViewModel(repository, adminRepository = admin)
        viewModel.openForAllAccounts()
        runCurrent()

        viewModel.openForAccount(ACCOUNT_KEY, null)
        runCurrent()

        val state = assertIs<TeamUsageUiState.Success>(viewModel.uiState.value)
        assertFalse(state.isAdminOverview)
        assertEquals(listOf("device-9"), state.members.map { entry -> entry.deviceId })
        assertNull(state.members.first().accountKey)
        viewModel.onDestroy()
    }

    @Test
    fun `sem caso de uso de administracao a visao global nao abre`() = runTest {
        val repository = FakeTeamRepository()
        val viewModel = buildViewModel(repository)

        viewModel.openForAllAccounts()
        runCurrent()

        assertIs<TeamUsageUiState.Loading>(viewModel.uiState.value)
        assertEquals(0, repository.fetchCalls)
        viewModel.onDestroy()
    }
}

private const val OTHER_ACCOUNT_KEY = "account-uuid-bbb"

/** Só o que a visão global precisa; o resto do contrato não é exercitado aqui. */
private class FakeAdminOverviewRepository(
    private val accounts: List<TeamAccountUsage>
) : TeamAdminRepository {
    override suspend fun validateToken(): Result<Unit> = Result.success(Unit)

    override suspend fun listKeys(): Result<List<TeamKeyEntry>> = Result.success(emptyList())

    override suspend fun createKey(label: String, maxAccounts: Int): Result<TeamKeyEntry> =
        Result.failure(UnsupportedOperationException())

    override suspend fun updateKey(
        id: String,
        label: String?,
        maxAccounts: Int?
    ): Result<TeamKeyEntry> = Result.failure(UnsupportedOperationException())

    override suspend fun regenerateKey(id: String): Result<TeamKeyEntry> =
        Result.failure(UnsupportedOperationException())

    override suspend fun revokeKey(id: String): Result<TeamKeyEntry> =
        Result.failure(UnsupportedOperationException())

    override suspend fun unclaimAccount(id: String, accountKey: String): Result<TeamKeyEntry> =
        Result.failure(UnsupportedOperationException())

    override suspend fun deleteAccount(accountKey: String): Result<TeamAccountDeletion> =
        Result.failure(UnsupportedOperationException())

    override suspend fun fetchOverview(cutoffMillis: Long?): Result<List<TeamAccountUsage>> =
        Result.success(accounts)

    override suspend fun verifyKeyForAccount(accountKey: String): Result<TeamKeyVerification> =
        Result.success(TeamKeyVerification(authorized = true, claimed = true))

    override suspend fun claimKeyForAccount(accountKey: String): Result<TeamKeyVerification> =
        Result.success(TeamKeyVerification(authorized = true, claimed = true))
}

private fun overviewAccount(
    accountKey: String,
    label: String?,
    deviceId: String,
    tokens: Long = 0L
): TeamAccountUsage {
    return TeamAccountUsage(
        accountKey = accountKey,
        label = label,
        snapshot = TeamUsageSnapshot(
            members = listOf(member(deviceId, sessions = listOf(session("s1", tokens = tokens))))
        )
    )
}
