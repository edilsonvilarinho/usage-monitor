package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.CliSessionDetail
import com.usagemonitor.domain.entity.CliHourlyUsageRow
import com.usagemonitor.domain.entity.CliSessionIndexReport
import com.usagemonitor.domain.entity.CliSessionSummary
import com.usagemonitor.domain.entity.CliToolUsage
import com.usagemonitor.domain.entity.CliUsageBreakdown
import com.usagemonitor.domain.entity.DEFAULT_ANTHROPIC_PROFILE_ID
import com.usagemonitor.domain.entity.TeamIngestPayload
import com.usagemonitor.domain.entity.TeamIngestReceipt
import com.usagemonitor.domain.entity.TeamMemberIdentity
import com.usagemonitor.domain.entity.TeamMemberUsage
import com.usagemonitor.domain.entity.TeamPresenceReceipt
import com.usagemonitor.domain.entity.TeamUsageSnapshot
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.domain.repository.CliSessionRepository
import com.usagemonitor.domain.repository.TeamUsageRepository
import com.usagemonitor.domain.usecase.GetActiveCliSessionPulsesUseCase
import com.usagemonitor.domain.usecase.GetActiveTeamSessionPulseUseCase
import com.usagemonitor.domain.usecase.SyncCliSessionIndexUseCase
import com.usagemonitor.presentation.viewmodel.SessionPulseViewModel
import com.usagemonitor.presentation.viewmodel.TeamPulseTarget
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

private val NOW = Instant.parse("2026-08-13T12:00:00Z")
private const val ACTIVE_WINDOW_MILLIS = 5L * 60 * 1_000
private const val INTERVAL_MILLIS = 30_000L

/** 65% da janela de 1M do Opus: sessão saturada. */
private const val SATURATED_CONTEXT_TOKENS = 650_000L

private val CONTA2 = UsageTargetKey(ApiSource.ANTHROPIC, "conta2")
private val DEFAULT_TARGET = UsageTargetKey(ApiSource.ANTHROPIC, DEFAULT_ANTHROPIC_PROFILE_ID)

@OptIn(ExperimentalCoroutinesApi::class)
class SessionPulseViewModelTest {

    @Test
    fun `local sessions are published by card`() = runTest {
        val repository = FakePulseCliRepository(listOf(session("a", profileId = "conta2")))
        val viewModel = buildViewModel(repository)

        viewModel.refreshOnce()

        assertEquals(setOf(CONTA2), viewModel.cliPulses.value.keys)
        assertEquals("a", viewModel.cliPulses.value.getValue(CONTA2).alerts.single().sessionId)
        viewModel.onDestroy()
    }

    /** Linha ainda não reatribuída: sem perfil, a sessão é da conta padrão. */
    @Test
    fun `a session without a profile falls back to the default account`() = runTest {
        val repository = FakePulseCliRepository(listOf(session("a", profileId = null)))
        val viewModel = buildViewModel(repository)

        viewModel.refreshOnce()

        assertEquals(setOf(DEFAULT_TARGET), viewModel.cliPulses.value.keys)
        viewModel.onDestroy()
    }

    @Test
    fun `the index is read only within the activity window`() = runTest {
        val repository = FakePulseCliRepository()
        val viewModel = buildViewModel(repository)

        viewModel.refreshOnce()

        assertNull(repository.lastProfileId)
        assertEquals(NOW.toEpochMilliseconds() - ACTIVE_WINDOW_MILLIS, repository.lastSinceEpochMillis)
        assertEquals(1, repository.syncCalls)
        viewModel.onDestroy()
    }

    @Test
    fun `a healthy machine leaves every button at rest`() = runTest {
        val repository = FakePulseCliRepository(listOf(session("a", liveContextTokens = 10_000L)))
        val viewModel = buildViewModel(repository)

        viewModel.refreshOnce()

        assertTrue(viewModel.cliPulses.value.isEmpty())
        viewModel.onDestroy()
    }

    @Test
    fun `team pulses come from the configured accounts`() = runTest {
        val teamRepository = FakePulseTeamRepository(
            TeamUsageSnapshot(
                members = listOf(
                    TeamMemberUsage(
                        deviceId = "device-1",
                        alias = "SUETONIO",
                        hostName = "devmachine",
                        sessions = listOf(session("t1"))
                    )
                )
            )
        )
        val viewModel = buildViewModel(
            repository = FakePulseCliRepository(),
            teamRepository = teamRepository,
            teamTargets = listOf(TeamPulseTarget(profileId = "conta2", accountKey = "acc-uuid"))
        )

        viewModel.refreshOnce()

        assertEquals(listOf("acc-uuid"), teamRepository.requestedAccounts)
        val alert = viewModel.teamPulses.value.getValue(CONTA2).alerts.single()
        assertEquals("SUETONIO", alert.memberAlias)
        assertEquals("devmachine", alert.machineLabel)
        viewModel.onDestroy()
    }

    @Test
    fun `without team targets the team buttons are cleared`() = runTest {
        val viewModel = buildViewModel(
            repository = FakePulseCliRepository(),
            teamRepository = FakePulseTeamRepository(),
            teamTargets = emptyList()
        )

        viewModel.refreshOnce()

        assertTrue(viewModel.teamPulses.value.isEmpty())
        viewModel.onDestroy()
    }

    /**
     * Um soluço de rede não pode apagar o aviso; um servidor fora do ar por
     * minutos também não pode mantê-lo aceso para sempre. Quem resolve os dois é
     * o envelhecimento do pulso.
     */
    @Test
    fun `a failed team reading keeps the previous pulse until it ages out`() = runTest {
        val clock = MutableTestClock(NOW)
        val teamRepository = FakePulseTeamRepository(
            TeamUsageSnapshot(
                members = listOf(
                    TeamMemberUsage(deviceId = "device-1", alias = "SUETONIO", sessions = listOf(session("t1")))
                )
            )
        )
        val viewModel = buildViewModel(
            repository = FakePulseCliRepository(),
            teamRepository = teamRepository,
            teamTargets = listOf(TeamPulseTarget(profileId = "conta2", accountKey = "acc-uuid")),
            clock = clock
        )

        viewModel.refreshOnce()
        assertTrue(viewModel.teamPulses.value.containsKey(CONTA2))

        teamRepository.fetchResult = Result.failure(IllegalStateException("servidor fora do ar"))
        clock.current = NOW + 1.minutes
        viewModel.refreshOnce()
        assertTrue(viewModel.teamPulses.value.containsKey(CONTA2))

        clock.current = NOW + 6.minutes
        viewModel.refreshOnce()
        assertTrue(viewModel.teamPulses.value.isEmpty())
        viewModel.onDestroy()
    }

    /**
     * A parte local não pode parar com a janela minimizada: é dela que sai o
     * alerta de sessão saturada, cujo destinatário é justamente quem não está
     * olhando a tela.
     */
    @Test
    fun `a hidden window keeps the local pass and suspends the team read`() = runTest {
        val repository = FakePulseCliRepository(listOf(session("a", profileId = "conta2")))
        val teamRepository = FakePulseTeamRepository()
        val isAppVisible = MutableStateFlow(false)
        val viewModel = buildViewModel(
            repository = repository,
            teamRepository = teamRepository,
            teamTargets = listOf(TeamPulseTarget(profileId = "conta2", accountKey = "acc-1")),
            isAppVisible = isAppVisible,
            dispatcher = UnconfinedTestDispatcher(testScheduler),
            autoStart = true
        )

        runCurrent()
        assertEquals(1, repository.syncCalls)
        assertEquals(setOf(CONTA2), viewModel.cliPulses.value.keys)
        assertTrue(teamRepository.requestedAccounts.isEmpty())

        isAppVisible.value = true
        advanceTimeBy(INTERVAL_MILLIS)
        runCurrent()

        assertEquals(2, repository.syncCalls)
        assertEquals(listOf("acc-1"), teamRepository.requestedAccounts)
        viewModel.onDestroy()
    }

    @Test
    fun `destroying the view model stops the loop`() = runTest {
        val repository = FakePulseCliRepository()
        val viewModel = buildViewModel(
            repository = repository,
            dispatcher = UnconfinedTestDispatcher(testScheduler),
            autoStart = true
        )

        runCurrent()
        val callsBeforeDestroy = repository.syncCalls
        viewModel.onDestroy()

        advanceTimeBy(INTERVAL_MILLIS * 3)
        runCurrent()

        assertEquals(callsBeforeDestroy, repository.syncCalls)
    }

    private fun buildViewModel(
        repository: FakePulseCliRepository,
        teamRepository: FakePulseTeamRepository = FakePulseTeamRepository(),
        teamTargets: List<TeamPulseTarget> = emptyList(),
        isAppVisible: MutableStateFlow<Boolean> = MutableStateFlow(true),
        clock: Clock = MutableTestClock(NOW),
        dispatcher: kotlinx.coroutines.CoroutineDispatcher = UnconfinedTestDispatcher(),
        autoStart: Boolean = false
    ): SessionPulseViewModel {
        return SessionPulseViewModel(
            getCliPulses = GetActiveCliSessionPulsesUseCase(repository, clock),
            getTeamPulse = GetActiveTeamSessionPulseUseCase(teamRepository, clock),
            syncCliSessionIndex = SyncCliSessionIndexUseCase(repository),
            teamTargetsProvider = { teamTargets },
            isAppVisible = isAppVisible,
            intervalMillis = INTERVAL_MILLIS,
            dispatcher = dispatcher,
            clock = clock,
            autoStart = autoStart
        )
    }

    private fun session(
        sessionId: String,
        profileId: String? = "conta2",
        liveContextTokens: Long = SATURATED_CONTEXT_TOKENS
    ): CliSessionSummary {
        return CliSessionSummary(
            sessionId = sessionId,
            filePath = "/tmp/$sessionId.jsonl",
            profileId = profileId,
            cwd = "/home/dev/$sessionId",
            firstTs = NOW - 30.minutes,
            lastTs = NOW,
            primaryModel = "claude-opus-5",
            liveContextTokens = liveContextTokens,
            liveContextModel = "claude-opus-5"
        )
    }
}

private class MutableTestClock(var current: Instant) : Clock {
    override fun now(): Instant = current
}

private class FakePulseCliRepository(
    var sessions: List<CliSessionSummary> = emptyList()
) : CliSessionRepository {

    var syncCalls: Int = 0
    var lastSinceEpochMillis: Long? = null
    var lastProfileId: String? = null

    override suspend fun syncIndex(): Result<CliSessionIndexReport> {
        syncCalls++
        return Result.success(CliSessionIndexReport())
    }

    override suspend fun getSessions(
        profileId: String?,
        sinceEpochMillis: Long?
    ): Result<List<CliSessionSummary>> {
        lastProfileId = profileId
        lastSinceEpochMillis = sinceEpochMillis
        return Result.success(sessions)
    }

    override suspend fun getSessionDetail(sessionId: String): Result<CliSessionDetail?> {
        return Result.success(null)
    }

    override suspend fun getUsageBreakdown(
        profileId: String?,
        sinceEpochMillis: Long
    ): Result<CliUsageBreakdown> {
        return Result.success(CliUsageBreakdown())
    }

    override suspend fun getHourlyUsage(
        profileId: String?,
        sinceEpochMillis: Long
    ): Result<List<CliHourlyUsageRow>> {
        return Result.success(emptyList())
    }

    override suspend fun getToolUsage(
        profileId: String?,
        sinceEpochMillis: Long
    ): Result<List<CliToolUsage>> {
        return Result.success(emptyList())
    }
}

private class FakePulseTeamRepository(
    var snapshot: TeamUsageSnapshot = TeamUsageSnapshot()
) : TeamUsageRepository {

    var fetchResult: Result<TeamUsageSnapshot>? = null
    val requestedAccounts = mutableListOf<String>()

    override suspend fun fetch(accountKey: String, cutoffMillis: Long?): Result<TeamUsageSnapshot> {
        requestedAccounts += accountKey
        return fetchResult ?: Result.success(snapshot)
    }

    override suspend fun push(payload: TeamIngestPayload): Result<TeamIngestReceipt> {
        return Result.success(TeamIngestReceipt())
    }

    override suspend fun fetchSessionDetail(
        accountKey: String,
        deviceId: String,
        sessionId: String
    ): Result<CliSessionDetail?> {
        return Result.success(null)
    }

    override suspend fun removeMember(accountKey: String, deviceId: String): Result<Unit> {
        return Result.success(Unit)
    }

    override suspend fun touchPresence(
        accountKey: String,
        member: TeamMemberIdentity
    ): Result<TeamPresenceReceipt> {
        return Result.success(TeamPresenceReceipt())
    }

    override suspend fun checkConnection(): Result<Unit> {
        return Result.success(Unit)
    }
}
