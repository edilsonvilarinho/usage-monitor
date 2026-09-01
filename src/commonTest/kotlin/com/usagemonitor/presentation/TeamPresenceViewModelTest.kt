package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.CliSessionDetail
import com.usagemonitor.domain.entity.CliSessionSummary
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
import com.usagemonitor.domain.usecase.DeleteTeamAccountUseCase
import com.usagemonitor.domain.usecase.GetAdminTeamPresenceUseCase
import com.usagemonitor.domain.usecase.GetTeamPresenceUseCase
import com.usagemonitor.domain.usecase.RemoveAdminTeamMemberUseCase
import com.usagemonitor.presentation.viewmodel.TeamPresenceUiState
import com.usagemonitor.presentation.viewmodel.TeamPresenceViewModel
import kotlinx.coroutines.test.TestCoroutineScheduler
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

private const val PRESENCE_LIVE_INTERVAL_MILLIS = 5_000L
private const val ACCOUNT_KEY = "account-uuid-aaa"
private const val OTHER_ACCOUNT_KEY = "account-uuid-bbb"

private val PRESENCE_ORIGIN = Instant.fromEpochMilliseconds(1_800_000_000_000)

/** Anda junto com o tempo virtual: avançar o laço e o relógio viram um gesto só. */
private class PresenceSchedulerClock(
    private val origin: Instant,
    private val scheduler: TestCoroutineScheduler
) : Clock {
    override fun now(): Instant = origin + scheduler.currentTime.milliseconds
}

private fun session(id: String, lastTs: Instant): CliSessionSummary {
    return CliSessionSummary(sessionId = id, filePath = "", firstTs = lastTs, lastTs = lastTs)
}

private fun member(
    deviceId: String = "device-1",
    alias: String = "edilson",
    lastSeenAt: Instant? = PRESENCE_ORIGIN,
    sessions: List<CliSessionSummary> = emptyList()
): TeamMemberUsage {
    return TeamMemberUsage(
        deviceId = deviceId,
        alias = alias,
        lastSeenAt = lastSeenAt,
        sessions = sessions
    )
}

private class FakePresenceRepository(
    var snapshot: TeamUsageSnapshot = TeamUsageSnapshot()
) : TeamUsageRepository {
    var fetchCalls = 0
    var lastCutoffMillis: Long? = null
    var lastAccountKey: String? = null
    var fetchResult: Result<TeamUsageSnapshot>? = null

    /**
     * Segura a leitura até ser liberado.
     *
     * Necessário para observar estados transitórios: com `UnconfinedTestDispatcher`
     * e um fake que responde na hora, o `Loading` seria substituído pelo `Success`
     * antes de qualquer asserção conseguir vê-lo.
     */
    var gate: kotlinx.coroutines.CompletableDeferred<Unit>? = null

    override suspend fun fetch(accountKey: String, cutoffMillis: Long?): Result<TeamUsageSnapshot> {
        gate?.await()
        fetchCalls += 1
        lastAccountKey = accountKey
        lastCutoffMillis = cutoffMillis
        return fetchResult ?: Result.success(snapshot)
    }

    override suspend fun push(payload: TeamIngestPayload): Result<TeamIngestReceipt> {
        return Result.success(TeamIngestReceipt())
    }

    override suspend fun touchPresence(
        accountKey: String,
        member: TeamMemberIdentity
    ): Result<TeamPresenceReceipt> {
        return Result.success(TeamPresenceReceipt())
    }

    override suspend fun fetchSessionDetail(
        accountKey: String,
        deviceId: String,
        sessionId: String
    ): Result<CliSessionDetail?> {
        return Result.success(null)
    }

    override suspend fun checkConnection(): Result<Unit> = Result.success(Unit)

    override suspend fun fetchTrend(accountKey: String, days: Int): Result<TeamUsageTrendData?> {
        return Result.success(null)
    }
}

private class FakePresenceAdminRepository(
    var accounts: List<TeamAccountUsage> = emptyList()
) : TeamAdminRepository {

    val deletedAccounts = mutableListOf<String>()
    val removedMembers = mutableListOf<Pair<String, String>>()
    var removeResult: Result<Unit>? = null
    var deleteResult: Result<TeamAccountDeletion>? = null

    override suspend fun removeMember(accountKey: String, deviceId: String): Result<Unit> {
        removedMembers += accountKey to deviceId
        return removeResult ?: Result.success(Unit)
    }

    override suspend fun removeSession(
        accountKey: String,
        deviceId: String,
        sessionId: String
    ): Result<Unit> = Result.failure(UnsupportedOperationException())

    override suspend fun deleteAccount(accountKey: String): Result<TeamAccountDeletion> {
        deletedAccounts += accountKey
        return deleteResult ?: Result.success(
            TeamAccountDeletion(
                deletedTurns = 0,
                deletedSessions = 0,
                deletedMembers = 0,
                unlinkedKeys = 0
            )
        )
    }

    override suspend fun fetchOverview(cutoffMillis: Long?): Result<List<TeamAccountUsage>> {
        return Result.success(accounts)
    }

    override suspend fun fetchSessionDetail(
        accountKey: String,
        deviceId: String,
        sessionId: String
    ): Result<CliSessionDetail?> = Result.failure(UnsupportedOperationException())

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

    override suspend fun verifyKeyForAccount(
        accountKey: String,
        accountEmail: String?
    ): Result<TeamKeyVerification> =
        Result.success(TeamKeyVerification(authorized = true, claimed = true))

    override suspend fun claimKeyForAccount(
        accountKey: String,
        accountEmail: String?
    ): Result<TeamKeyVerification> =
        Result.success(TeamKeyVerification(authorized = true, claimed = true))
}

class TeamPresenceViewModelTest {

    /**
     * Constrói o ViewModel e garante o `onDestroy` no fim.
     *
     * O `finally` não é zelo: o laço ao vivo é um `while(true)` no mesmo
     * scheduler virtual, e uma asserção que falhasse antes do encerramento
     * deixaria o `runTest` drenando tarefas para sempre — o teste travaria em vez
     * de reportar a falha.
     */
    private fun TestScope.withViewModel(
        repository: FakePresenceRepository = FakePresenceRepository(),
        adminRepository: FakePresenceAdminRepository? = null,
        /** Desligado por padrão: é o estado de quem não administra o servidor. */
        canManage: Boolean = false,
        block: (TeamPresenceViewModel) -> Unit
    ) {
        val clock = PresenceSchedulerClock(PRESENCE_ORIGIN, testScheduler)
        val viewModel = TeamPresenceViewModel(
            getTeamPresence = GetTeamPresenceUseCase(repository, clock = clock),
            getAdminTeamPresence = adminRepository?.let { admin ->
                GetAdminTeamPresenceUseCase(admin, clock = clock)
            },
            removeTeamMember = if (canManage && adminRepository != null) {
                RemoveAdminTeamMemberUseCase(adminRepository)
            } else {
                null
            },
            deleteTeamAccount = if (canManage && adminRepository != null) {
                DeleteTeamAccountUseCase(adminRepository)
            } else {
                null
            },
            dispatcher = UnconfinedTestDispatcher(testScheduler),
            liveIntervalMillis = PRESENCE_LIVE_INTERVAL_MILLIS,
            clock = clock
        )
        try {
            block(viewModel)
        } finally {
            viewModel.onDestroy()
        }
    }

    @Test
    fun `a primeira carga classifica quem esta online`() = runTest {
        val repository = FakePresenceRepository(
            TeamUsageSnapshot(
                members = listOf(
                    member(deviceId = "device-online"),
                    member(
                        deviceId = "device-offline",
                        alias = "fulano",
                        lastSeenAt = PRESENCE_ORIGIN - 10.minutes()
                    )
                )
            )
        )

        withViewModel(repository) { viewModel ->
            viewModel.openForAccount(ACCOUNT_KEY, "conta")
            runCurrent()

            val state = assertIs<TeamPresenceUiState.Success>(viewModel.uiState.value)
            assertEquals(2, state.totalCount)
            assertEquals(1, state.onlineCount)
            assertEquals(ACCOUNT_KEY, repository.lastAccountKey)
        }
    }

    @Test
    fun `o laco ao vivo consulta o servidor a cada intervalo`() = runTest {
        val repository = FakePresenceRepository()

        withViewModel(repository) { viewModel ->
            viewModel.openForAccount(ACCOUNT_KEY, "conta")
            runCurrent()
            val afterOpen = repository.fetchCalls

            advanceTimeBy(PRESENCE_LIVE_INTERVAL_MILLIS * 3 + 1)
            runCurrent()

            assertEquals(afterOpen + 3, repository.fetchCalls)
        }
    }

    @Test
    fun `o carimbo de alteracao nao anda quando nada muda`() = runTest {
        val repository = FakePresenceRepository(TeamUsageSnapshot(members = listOf(member())))

        withViewModel(repository) { viewModel ->
            viewModel.openForAccount(ACCOUNT_KEY, "conta")
            runCurrent()
            val first = assertIs<TeamPresenceUiState.Success>(viewModel.uiState.value).lastChangedAt

            advanceTimeBy(PRESENCE_LIVE_INTERVAL_MILLIS * 2 + 1)
            runCurrent()

            val later = assertIs<TeamPresenceUiState.Success>(viewModel.uiState.value)
            assertEquals(first, later.lastChangedAt)
        }
    }

    @Test
    fun `falha depois de carregar mantem a lista na tela`() = runTest {
        val repository = FakePresenceRepository(TeamUsageSnapshot(members = listOf(member())))

        withViewModel(repository) { viewModel ->
            viewModel.openForAccount(ACCOUNT_KEY, "conta")
            runCurrent()

            repository.fetchResult = Result.failure(IllegalStateException("servidor fora do ar"))
            advanceTimeBy(PRESENCE_LIVE_INTERVAL_MILLIS + 1)
            runCurrent()

            val state = assertIs<TeamPresenceUiState.Success>(viewModel.uiState.value)
            assertEquals(1, state.totalCount)
        }
    }

    @Test
    fun `falha sem nada na tela vira erro`() = runTest {
        val repository = FakePresenceRepository()
        repository.fetchResult = Result.failure(IllegalStateException("chave invalida"))

        withViewModel(repository) { viewModel ->
            viewModel.openForAccount(ACCOUNT_KEY, "conta")
            runCurrent()

            val state = assertIs<TeamPresenceUiState.Error>(viewModel.uiState.value)
            assertEquals("chave invalida", state.message)
        }
    }

    @Test
    fun `reabrir na mesma conta nao volta para Loading`() = runTest {
        val repository = FakePresenceRepository(TeamUsageSnapshot(members = listOf(member())))

        withViewModel(repository) { viewModel ->
            viewModel.openForAccount(ACCOUNT_KEY, "conta")
            runCurrent()
            viewModel.openForAccount(ACCOUNT_KEY, "conta")

            assertIs<TeamPresenceUiState.Success>(viewModel.uiState.value)
        }
    }

    @Test
    fun `trocar de conta zera para Loading`() = runTest {
        val repository = FakePresenceRepository(TeamUsageSnapshot(members = listOf(member())))

        withViewModel(repository) { viewModel ->
            viewModel.openForAccount(ACCOUNT_KEY, "conta")
            runCurrent()

            // A carga da conta nova fica presa: é durante essa espera que o
            // usuário veria a lista antiga, se ela não fosse zerada.
            repository.gate = kotlinx.coroutines.CompletableDeferred()
            viewModel.openForAccount(OTHER_ACCOUNT_KEY, "outra conta")
            runCurrent()

            // Manter os integrantes da conta anterior mostraria pessoas de outro
            // time como conectadas.
            assertIs<TeamPresenceUiState.Loading>(viewModel.uiState.value)

            repository.gate?.complete(Unit)
            repository.gate = null
            runCurrent()
            assertIs<TeamPresenceUiState.Success>(viewModel.uiState.value)
        }
    }

    @Test
    fun `resposta do escopo anterior nao e publicada sobre o novo`() = runTest {
        val repository = FakePresenceRepository(
            TeamUsageSnapshot(members = listOf(member(deviceId = "device-da-conta-antiga")))
        )

        withViewModel(repository) { viewModel ->
            repository.gate = kotlinx.coroutines.CompletableDeferred()
            viewModel.openForAccount(ACCOUNT_KEY, "conta")
            runCurrent()

            // Troca de escopo com a leitura anterior ainda em voo.
            viewModel.openForAccount(OTHER_ACCOUNT_KEY, "outra conta")
            runCurrent()

            repository.snapshot = TeamUsageSnapshot(
                members = listOf(member(deviceId = "device-da-conta-nova"))
            )
            repository.gate?.complete(Unit)
            repository.gate = null
            runCurrent()

            val state = assertIs<TeamPresenceUiState.Success>(viewModel.uiState.value)
            assertEquals(
                listOf("device-da-conta-nova"),
                state.entries.map { entry -> entry.deviceId }
            )
        }
    }

    @Test
    fun `o filtro de conectados nao dispara rede`() = runTest {
        val repository = FakePresenceRepository(TeamUsageSnapshot(members = listOf(member())))

        withViewModel(repository) { viewModel ->
            viewModel.openForAccount(ACCOUNT_KEY, "conta")
            runCurrent()
            val before = repository.fetchCalls

            viewModel.setOnlyOnline(true)

            assertEquals(before, repository.fetchCalls)
            assertTrue(assertIs<TeamPresenceUiState.Success>(viewModel.uiState.value).onlyOnline)
        }
    }

    @Test
    fun `o filtro sobrevive aos tiques do laco`() = runTest {
        val repository = FakePresenceRepository(TeamUsageSnapshot(members = listOf(member())))

        withViewModel(repository) { viewModel ->
            viewModel.openForAccount(ACCOUNT_KEY, "conta")
            runCurrent()
            viewModel.setOnlyOnline(true)

            advanceTimeBy(PRESENCE_LIVE_INTERVAL_MILLIS * 3 + 1)
            runCurrent()

            assertTrue(assertIs<TeamPresenceUiState.Success>(viewModel.uiState.value).onlyOnline)
        }
    }

    @Test
    fun `a conta expandida sobrevive aos tiques e some quando a conta desaparece`() = runTest {
        val adminRepository = FakePresenceAdminRepository(
            accounts = listOf(
                TeamAccountUsage(
                    accountKey = ACCOUNT_KEY,
                    label = "time-a",
                    snapshot = TeamUsageSnapshot(members = listOf(member()))
                )
            )
        )

        withViewModel(adminRepository = adminRepository) { viewModel ->
            viewModel.openForAllAccounts()
            runCurrent()
            viewModel.toggleAccount(ACCOUNT_KEY)

            advanceTimeBy(PRESENCE_LIVE_INTERVAL_MILLIS * 2 + 1)
            runCurrent()
            assertEquals(
                setOf(ACCOUNT_KEY),
                assertIs<TeamPresenceUiState.Success>(viewModel.uiState.value).expandedAccountKeys
            )

            adminRepository.accounts = emptyList()
            advanceTimeBy(PRESENCE_LIVE_INTERVAL_MILLIS + 1)
            runCurrent()

            // Sem a limpeza a conta reapareceria aberta se voltasse horas depois.
            assertTrue(
                assertIs<TeamPresenceUiState.Success>(viewModel.uiState.value)
                    .expandedAccountKeys.isEmpty()
            )
        }
    }

    @Test
    fun `a visao global carimba a conta em cada integrante`() = runTest {
        val adminRepository = FakePresenceAdminRepository(
            accounts = listOf(
                TeamAccountUsage(
                    accountKey = ACCOUNT_KEY,
                    label = "time-a",
                    snapshot = TeamUsageSnapshot(members = listOf(member(deviceId = "device-1")))
                ),
                TeamAccountUsage(
                    accountKey = OTHER_ACCOUNT_KEY,
                    label = "time-b",
                    snapshot = TeamUsageSnapshot(members = listOf(member(deviceId = "device-1")))
                )
            )
        )

        withViewModel(adminRepository = adminRepository) { viewModel ->
            viewModel.openForAllAccounts()
            runCurrent()

            val state = assertIs<TeamPresenceUiState.Success>(viewModel.uiState.value)
            // A mesma máquina em duas contas precisa de duas linhas distintas.
            assertEquals(
                listOf("$ACCOUNT_KEY/device-1", "$OTHER_ACCOUNT_KEY/device-1"),
                state.entries.map { entry -> entry.memberKey }.sorted()
            )
            assertEquals(2, state.presenceGroups.size)
        }
    }

    @Test
    fun `mesmo e-mail agrupa dois UUIDs e exclusao continua escopada ao selecionado`() = runTest {
        val adminRepository = FakePresenceAdminRepository(
            accounts = listOf(
                TeamAccountUsage(
                    accountKey = ACCOUNT_KEY,
                    label = "Pessoa",
                    accountEmail = "pessoa@empresa.com",
                    snapshot = TeamUsageSnapshot(
                        members = listOf(member(deviceId = "device-1", alias = "visivel"))
                    )
                ),
                TeamAccountUsage(
                    accountKey = OTHER_ACCOUNT_KEY,
                    label = "Pessoa",
                    accountEmail = "pessoa@empresa.com",
                    snapshot = TeamUsageSnapshot(
                        members = listOf(member(deviceId = "device-1", alias = "oculto"))
                    )
                )
            )
        )

        withViewModel(adminRepository = adminRepository, canManage = true) { viewModel ->
            viewModel.openForAllAccounts()
            runCurrent()

            val state = assertIs<TeamPresenceUiState.Success>(viewModel.uiState.value)
            val emailGroup = state.emailGroups.single()
            assertEquals(2, emailGroup.accounts.size)
            assertEquals(2, emailGroup.entries.map { entry -> entry.memberKey }.distinct().size)
            assertEquals(
                emailGroup.groupKey,
                state.copy(query = "visivel").emailGroups.single().groupKey
            )

            viewModel.deleteAccount(OTHER_ACCOUNT_KEY)
            runCurrent()
            assertEquals(listOf(OTHER_ACCOUNT_KEY), adminRepository.deletedAccounts)
        }
    }

    @Test
    fun `remover integrante usa a conta dele e recarrega a lista`() = runTest {
        val repository = FakePresenceRepository()
        val adminRepository = FakePresenceAdminRepository(
            accounts = listOf(
                TeamAccountUsage(
                    accountKey = ACCOUNT_KEY,
                    label = "time-a",
                    snapshot = TeamUsageSnapshot(members = listOf(member(deviceId = "device-1")))
                ),
                TeamAccountUsage(
                    accountKey = OTHER_ACCOUNT_KEY,
                    label = "time-b",
                    snapshot = TeamUsageSnapshot(members = listOf(member(deviceId = "device-1")))
                )
            )
        )

        withViewModel(repository, adminRepository, canManage = true) { viewModel ->
            viewModel.openForAllAccounts()
            runCurrent()

            viewModel.removeMember("$OTHER_ACCOUNT_KEY/device-1")
            runCurrent()

            // A conta é a do integrante, não a da janela: com a da janela isto
            // apagaria o histórico da conta errada.
            assertEquals(listOf(OTHER_ACCOUNT_KEY to "device-1"), adminRepository.removedMembers)
            assertNull(viewModel.actionError.value)
        }
    }

    @Test
    fun `apagar conta chama o servidor e recarrega`() = runTest {
        val adminRepository = FakePresenceAdminRepository(
            accounts = listOf(
                TeamAccountUsage(
                    accountKey = ACCOUNT_KEY,
                    label = "time-a",
                    snapshot = TeamUsageSnapshot(members = listOf(member()))
                )
            )
        )

        withViewModel(adminRepository = adminRepository, canManage = true) { viewModel ->
            viewModel.openForAllAccounts()
            runCurrent()

            adminRepository.accounts = emptyList()
            viewModel.deleteAccount(ACCOUNT_KEY)
            runCurrent()

            assertEquals(listOf(ACCOUNT_KEY), adminRepository.deletedAccounts)
            // A leitura seguinte é a única prova de que o servidor apagou.
            assertTrue(assertIs<TeamPresenceUiState.Success>(viewModel.uiState.value).isEmpty)
        }
    }

    @Test
    fun `falha ao remover vira aviso sem apagar a lista`() = runTest {
        val adminRepository = FakePresenceAdminRepository(
            accounts = listOf(
                TeamAccountUsage(
                    accountKey = ACCOUNT_KEY,
                    label = "conta",
                    snapshot = TeamUsageSnapshot(members = listOf(member(deviceId = "device-1")))
                )
            )
        )
        adminRepository.removeResult = Result.failure(IllegalStateException("servidor fora do ar"))

        withViewModel(adminRepository = adminRepository, canManage = true) { viewModel ->
            viewModel.openForAllAccounts()
            runCurrent()

            viewModel.removeMember("$ACCOUNT_KEY/device-1")
            runCurrent()

            assertEquals(listOf(ACCOUNT_KEY to "device-1"), adminRepository.removedMembers)
            assertEquals("servidor fora do ar", viewModel.actionError.value)
            // Mostrar como removido o que continua no servidor seria mentir.
            assertEquals(
                1,
                assertIs<TeamPresenceUiState.Success>(viewModel.uiState.value).entries.size
            )

            viewModel.clearActionError()
            assertNull(viewModel.actionError.value)
        }
    }

    @Test
    fun `sem administracao nenhuma acao destrutiva executa`() = runTest {
        val repository = FakePresenceRepository(
            TeamUsageSnapshot(members = listOf(member(deviceId = "device-1")))
        )
        val adminRepository = FakePresenceAdminRepository()

        withViewModel(repository, adminRepository, canManage = true) { viewModel ->
            viewModel.openForAccount(ACCOUNT_KEY, "conta")
            runCurrent()

            viewModel.removeMember("device-1")
            viewModel.deleteAccount(ACCOUNT_KEY)
            runCurrent()

            assertTrue(adminRepository.removedMembers.isEmpty())
            assertTrue(adminRepository.deletedAccounts.isEmpty())
            assertNull(viewModel.actionError.value)
        }
    }

    @Test
    fun `sem caso de uso de administracao a visao global nao abre`() = runTest {
        withViewModel { viewModel ->
            viewModel.openForAllAccounts()
            runCurrent()

            assertIs<TeamPresenceUiState.Loading>(viewModel.uiState.value)
        }
    }

    @Test
    fun `fechar a janela para o laco ao vivo`() = runTest {
        val repository = FakePresenceRepository()

        withViewModel(repository) { viewModel ->
            viewModel.openForAccount(ACCOUNT_KEY, "conta")
            runCurrent()
            viewModel.closeWindow()
            val afterClose = repository.fetchCalls

            advanceTimeBy(PRESENCE_LIVE_INTERVAL_MILLIS * 4 + 1)
            runCurrent()

            assertEquals(afterClose, repository.fetchCalls)
        }
    }

    @Test
    fun `quem tem turno recente conta como trabalhando agora`() = runTest {
        val repository = FakePresenceRepository(
            TeamUsageSnapshot(
                members = listOf(
                    member(
                        deviceId = "device-working",
                        sessions = listOf(session("s-1", PRESENCE_ORIGIN - 1.minutes()))
                    ),
                    member(deviceId = "device-idle", alias = "fulano")
                )
            )
        )

        withViewModel(repository) { viewModel ->
            viewModel.openForAccount(ACCOUNT_KEY, "conta")
            runCurrent()

            val state = assertIs<TeamPresenceUiState.Success>(viewModel.uiState.value)
            assertEquals(1, state.workingCount)
            assertEquals(2, state.onlineCount)
            // Trabalhando vem primeiro na lista.
            assertEquals("device-working", state.entries.first().deviceId)
        }
    }

    @Test
    fun `o recorte enviado ao servidor e o de presenca e nao uma janela de quota`() = runTest {
        val repository = FakePresenceRepository()

        withViewModel(repository) { viewModel ->
            viewModel.openForAccount(ACCOUNT_KEY, "conta")
            runCurrent()

            val cutoff = repository.lastCutoffMillis
            assertTrue(cutoff != null && cutoff < PRESENCE_ORIGIN.toEpochMilliseconds())
        }
    }

    @Test
    fun `sem desvio medido o aviso de relogio fica desligado`() = runTest {
        val repository = FakePresenceRepository(TeamUsageSnapshot(members = listOf(member())))

        withViewModel(repository) { viewModel ->
            viewModel.openForAccount(ACCOUNT_KEY, "conta")
            runCurrent()

            val state = assertIs<TeamPresenceUiState.Success>(viewModel.uiState.value)
            assertFalse(state.clockSkewSuspected)
            assertEquals(0L, state.clockSkewMinutes)
        }
    }

    @Test
    fun `carimbo no futuro denuncia desvio de relogio`() = runTest {
        val repository = FakePresenceRepository(
            TeamUsageSnapshot(
                members = listOf(member(lastSeenAt = PRESENCE_ORIGIN + 60.minutes()))
            )
        )

        withViewModel(repository) { viewModel ->
            viewModel.openForAccount(ACCOUNT_KEY, "conta")
            runCurrent()

            assertTrue(
                assertIs<TeamPresenceUiState.Success>(viewModel.uiState.value).clockSkewSuspected
            )
        }
    }
}

private fun Int.minutes() = (this * 60L * 1_000).milliseconds
