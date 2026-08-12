package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.CliQuotaWindows
import com.usagemonitor.domain.entity.CliSessionRange
import com.usagemonitor.domain.entity.CliSessionSummary
import com.usagemonitor.domain.entity.TeamIngestPayload
import com.usagemonitor.domain.entity.TeamIngestReceipt
import com.usagemonitor.domain.entity.TeamMemberUsage
import com.usagemonitor.domain.entity.TeamUsageSnapshot
import com.usagemonitor.domain.repository.TeamUsageRepository
import com.usagemonitor.domain.usecase.GetTeamUsageUseCase
import com.usagemonitor.domain.usecase.RemoveTeamMemberUseCase
import com.usagemonitor.presentation.viewmodel.TeamUsageUiState
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
    var removeResult: Result<Unit> = Result.success(Unit)

    override suspend fun push(payload: TeamIngestPayload): Result<TeamIngestReceipt> {
        return Result.success(TeamIngestReceipt())
    }

    override suspend fun removeMember(accountKey: String, deviceId: String): Result<Unit> {
        if (removeResult.isSuccess) {
            removedDeviceIds += deviceId
        }
        return removeResult
    }

    override suspend fun fetch(accountKey: String, cutoffMillis: Long?): Result<TeamUsageSnapshot> {
        fetchCalls += 1
        lastAccountKey = accountKey
        lastCutoffMillis = cutoffMillis
        return fetchResult ?: Result.success(snapshot)
    }

    override suspend fun checkConnection(): Result<Unit> = Result.success(Unit)
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
        assertEquals(setOf("device-1"), state.expandedDeviceIds)
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
        assertEquals(setOf("device-1"), state.expandedDeviceIds)
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

    /** Compartilha o `testScheduler` do `runTest`, senão `advanceTimeBy` não move o laço. */
    private fun TestScope.buildViewModel(
        repository: FakeTeamRepository,
        // O corte da janela é resolvido dentro do caso de uso; separá-lo do
        // relógio do carimbo permite mover o tempo só onde interessa.
        useCaseClock: Clock = TeamFixedClock(TEAM_FIXED_NOW)
    ): TeamUsageViewModel {
        return TeamUsageViewModel(
            getTeamUsage = GetTeamUsageUseCase(repository, useCaseClock),
            removeTeamMember = RemoveTeamMemberUseCase(repository),
            dispatcher = UnconfinedTestDispatcher(testScheduler),
            liveIntervalMillis = TEAM_LIVE_INTERVAL_MILLIS,
            clock = TeamFixedClock(TEAM_FIXED_NOW)
        )
    }
}
