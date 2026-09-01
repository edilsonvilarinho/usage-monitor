package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.CliSessionDetail
import com.usagemonitor.domain.entity.CliSessionRange
import com.usagemonitor.domain.entity.CliSessionSummary
import com.usagemonitor.domain.entity.TeamAccountDeletion
import com.usagemonitor.domain.entity.TeamBlockedAccount
import com.usagemonitor.domain.entity.TeamAccountUsage
import com.usagemonitor.domain.entity.TeamKeyEntry
import com.usagemonitor.domain.entity.TeamKeyVerification
import com.usagemonitor.domain.entity.TeamMemberUsage
import com.usagemonitor.domain.entity.TeamUsageSnapshot
import com.usagemonitor.domain.repository.TeamAdminRepository
import com.usagemonitor.domain.usecase.CreateTeamKeyUseCase
import com.usagemonitor.domain.usecase.GetAdminTeamOverviewUseCase
import com.usagemonitor.domain.usecase.DeleteTeamAccountUseCase
import com.usagemonitor.domain.usecase.ListBlockedTeamAccountsUseCase
import com.usagemonitor.domain.usecase.ListTeamKeysUseCase
import com.usagemonitor.domain.usecase.RegenerateTeamKeyUseCase
import com.usagemonitor.domain.usecase.RevokeTeamKeyUseCase
import com.usagemonitor.domain.usecase.UnblockTeamAccountUseCase
import com.usagemonitor.domain.usecase.UnclaimTeamKeyAccountUseCase
import com.usagemonitor.domain.usecase.UpdateTeamKeyUseCase
import com.usagemonitor.presentation.viewmodel.TeamKeysAdminViewModel
import com.usagemonitor.presentation.viewmodel.TeamKeysUiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val ADMIN_FIXED_NOW = Instant.parse("2026-08-11T12:00:00Z")
private const val ADMIN_ACCOUNT_A = "account-uuid-aaa"
private const val ADMIN_ACCOUNT_B = "account-uuid-bbb"

private class FakeTeamAdminRepository : TeamAdminRepository {
    var keys = mutableListOf<TeamKeyEntry>()
    var listResult: Result<List<TeamKeyEntry>>? = null
    var actionResult: Result<TeamKeyEntry>? = null
    var overview: List<TeamAccountUsage> = emptyList()
    var blockedAccounts: List<TeamBlockedAccount> = emptyList()
    var blockedResult: Result<List<TeamBlockedAccount>>? = null
    val unblockedAccounts = mutableListOf<String>()
    var overviewResult: Result<List<TeamAccountUsage>>? = null

    var listCalls = 0
    var lastOverviewCutoff: Long? = null
    val createdLabels = mutableListOf<String>()
    val unclaimed = mutableListOf<Pair<String, String>>()
    val deletedAccounts = mutableListOf<String>()

    override suspend fun validateToken(): Result<Unit> = Result.success(Unit)

    override suspend fun listKeys(): Result<List<TeamKeyEntry>> {
        listCalls += 1
        return listResult ?: Result.success(keys.toList())
    }

    override suspend fun createKey(label: String, maxAccounts: Int): Result<TeamKeyEntry> {
        createdLabels += label
        val created = TeamKeyEntry(
            id = "key-${keys.size + 1}",
            label = label,
            key = "raw-${keys.size + 1}",
            keyPrefix = "raw-",
            maxAccounts = maxAccounts
        )
        val failure = actionResult
        if (failure != null && failure.isFailure) {
            return failure
        }
        keys += created
        return Result.success(created)
    }

    override suspend fun updateKey(
        id: String,
        label: String?,
        maxAccounts: Int?
    ): Result<TeamKeyEntry> {
        val failure = actionResult
        if (failure != null && failure.isFailure) {
            return failure
        }
        val index = keys.indexOfFirst { entry -> entry.id == id }
        val updated = keys[index].copy(
            label = label ?: keys[index].label,
            maxAccounts = maxAccounts ?: keys[index].maxAccounts
        )
        keys[index] = updated
        return Result.success(updated)
    }

    override suspend fun regenerateKey(id: String): Result<TeamKeyEntry> {
        val index = keys.indexOfFirst { entry -> entry.id == id }
        val updated = keys[index].copy(key = "${keys[index].key}-novo")
        keys[index] = updated
        return Result.success(updated)
    }

    override suspend fun revokeKey(id: String): Result<TeamKeyEntry> {
        val index = keys.indexOfFirst { entry -> entry.id == id }
        val updated = keys[index].copy(revokedAt = ADMIN_FIXED_NOW)
        keys[index] = updated
        return Result.success(updated)
    }

    override suspend fun unclaimAccount(id: String, accountKey: String): Result<TeamKeyEntry> {
        unclaimed += id to accountKey
        val index = keys.indexOfFirst { entry -> entry.id == id }
        val updated = keys[index].copy(accounts = keys[index].accounts - accountKey)
        keys[index] = updated
        return Result.success(updated)
    }

    override suspend fun removeMember(accountKey: String, deviceId: String): Result<Unit> =
        Result.failure(UnsupportedOperationException())

    override suspend fun removeSession(
        accountKey: String,
        deviceId: String,
        sessionId: String
    ): Result<Unit> = Result.failure(UnsupportedOperationException())

    override suspend fun deleteAccount(accountKey: String): Result<TeamAccountDeletion> {
        deletedAccounts += accountKey
        keys.replaceAll { entry -> entry.copy(accounts = entry.accounts - accountKey) }
        return Result.success(
            TeamAccountDeletion(
                deletedTurns = 1,
                deletedSessions = 1,
                deletedMembers = 1,
                unlinkedKeys = 1
            )
        )
    }

    override suspend fun fetchBlockedAccounts(): Result<List<TeamBlockedAccount>> =
        blockedResult ?: Result.success(blockedAccounts)

    override suspend fun unblockAccount(accountKey: String): Result<List<TeamBlockedAccount>> {
        unblockedAccounts += accountKey
        blockedAccounts = blockedAccounts.filterNot { it.accountKey == accountKey }
        return Result.success(blockedAccounts)
    }

    override suspend fun fetchOverview(cutoffMillis: Long?): Result<List<TeamAccountUsage>> {
        lastOverviewCutoff = cutoffMillis
        return overviewResult ?: Result.success(overview)
    }

    override suspend fun fetchSessionDetail(
        accountKey: String,
        deviceId: String,
        sessionId: String
    ): Result<CliSessionDetail?> = Result.failure(UnsupportedOperationException())

    override suspend fun verifyKeyForAccount(
        accountKey: String,
        accountEmail: String?
    ): Result<TeamKeyVerification> {
        return Result.success(TeamKeyVerification(authorized = true, claimed = true))
    }

    override suspend fun claimKeyForAccount(
        accountKey: String,
        accountEmail: String?
    ): Result<TeamKeyVerification> {
        return Result.success(TeamKeyVerification(authorized = true, claimed = true))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class TeamKeysAdminViewModelTest {

    @Test
    fun `carrega as chaves na abertura`() = runTest {
        val repository = FakeTeamAdminRepository()
        repository.keys += entry(id = "key-1", label = "fulano@empresa.com")
        val viewModel = buildViewModel(repository)

        viewModel.open()
        runCurrent()

        val state = assertIs<TeamKeysUiState.Success>(viewModel.uiState.value)
        assertEquals(1, state.keys.size)
        assertEquals("fulano@empresa.com", state.keys.first().label)
    }

    @Test
    fun `falha na primeira carga vira erro de tela`() = runTest {
        val repository = FakeTeamAdminRepository()
        repository.listResult = Result.failure(IllegalStateException("servidor fora"))
        val viewModel = buildViewModel(repository)

        viewModel.open()
        runCurrent()

        val state = assertIs<TeamKeysUiState.Error>(viewModel.uiState.value)
        assertEquals("servidor fora", state.message)
    }

    @Test
    fun `falha depois de carregar preserva a lista e vira aviso`() = runTest {
        val repository = FakeTeamAdminRepository()
        repository.keys += entry(id = "key-1", label = "fulano@empresa.com")
        val viewModel = buildViewModel(repository)
        viewModel.open()
        runCurrent()

        repository.actionResult = Result.failure(IllegalStateException("limite excedido"))
        viewModel.setMaxAccounts("key-1", 1)
        runCurrent()

        // Uma ação recusada não mudou o servidor: esconder a lista transformaria
        // um erro pontual numa tela vazia.
        val state = assertIs<TeamKeysUiState.Success>(viewModel.uiState.value)
        assertEquals(1, state.keys.size)
        assertEquals("limite excedido", state.actionError)
    }

    @Test
    fun `criar recarrega a lista`() = runTest {
        val repository = FakeTeamAdminRepository()
        val viewModel = buildViewModel(repository)
        viewModel.open()
        runCurrent()
        val callsAfterOpen = repository.listCalls

        viewModel.create("sicrano@empresa.com", maxAccounts = 2)
        runCurrent()

        assertEquals(listOf("sicrano@empresa.com"), repository.createdLabels)
        // A listagem seguinte é a única prova de que o servidor gravou.
        assertTrue(repository.listCalls > callsAfterOpen)
        val state = assertIs<TeamKeysUiState.Success>(viewModel.uiState.value)
        assertEquals(2, state.keys.first().maxAccounts)
    }

    @Test
    fun `criar com rotulo vazio nao chama o servidor`() = runTest {
        val repository = FakeTeamAdminRepository()
        val viewModel = buildViewModel(repository)
        viewModel.open()
        runCurrent()

        viewModel.create("   ", maxAccounts = 1)
        runCurrent()

        assertTrue(repository.createdLabels.isEmpty())
    }

    @Test
    fun `regerar troca a chave crua na lista`() = runTest {
        val repository = FakeTeamAdminRepository()
        repository.keys += entry(id = "key-1", label = "fulano@empresa.com")
        val viewModel = buildViewModel(repository)
        viewModel.open()
        runCurrent()

        viewModel.regenerate("key-1")
        runCurrent()

        val state = assertIs<TeamKeysUiState.Success>(viewModel.uiState.value)
        assertEquals("raw-key-novo", state.keys.first().key)
    }

    @Test
    fun `desvincular remove a conta da chave`() = runTest {
        val repository = FakeTeamAdminRepository()
        repository.keys += entry(
            id = "key-1",
            label = "fulano@empresa.com",
            accounts = listOf(ADMIN_ACCOUNT_A)
        )
        val viewModel = buildViewModel(repository)
        viewModel.open()
        runCurrent()

        viewModel.unclaim("key-1", ADMIN_ACCOUNT_A)
        runCurrent()

        assertEquals(listOf("key-1" to ADMIN_ACCOUNT_A), repository.unclaimed)
        val state = assertIs<TeamKeysUiState.Success>(viewModel.uiState.value)
        assertTrue(state.keys.first().accounts.isEmpty())
    }

    @Test
    fun `dispensar limpa o aviso da acao`() = runTest {
        val repository = FakeTeamAdminRepository()
        repository.keys += entry(id = "key-1", label = "fulano@empresa.com")
        val viewModel = buildViewModel(repository)
        viewModel.open()
        runCurrent()
        repository.actionResult = Result.failure(IllegalStateException("boom"))
        viewModel.setMaxAccounts("key-1", 1)
        runCurrent()

        viewModel.clearActionError()

        val state = assertIs<TeamKeysUiState.Success>(viewModel.uiState.value)
        assertNull(state.actionError)
    }

    @Test
    fun `carrega as contas fora do time junto das chaves`() = runTest {
        val repository = FakeTeamAdminRepository()
        repository.keys += entry(id = "key-1", label = "fulano@empresa.com")
        repository.blockedAccounts = listOf(
            TeamBlockedAccount(accountKey = ADMIN_ACCOUNT_B, accountEmail = "pessoal@gmail.com")
        )
        val viewModel = buildViewModel(repository)

        viewModel.open()
        runCurrent()

        // Na mesma carga, e não numa leitura por abertura de seção: remover uma
        // conta muda as duas listas, e lê-las em momentos diferentes mostraria a
        // tela pela metade.
        val state = assertIs<TeamKeysUiState.Success>(viewModel.uiState.value)
        assertEquals(1, state.keys.size)
        assertEquals(listOf("pessoal@gmail.com"), state.blockedAccounts.map { it.accountEmail })
    }

    @Test
    fun `remover a conta do time recarrega as duas listas`() = runTest {
        val repository = FakeTeamAdminRepository()
        repository.keys += entry(id = "key-1", label = "fulano@empresa.com")
        val viewModel = buildViewModel(repository)
        viewModel.open()
        runCurrent()
        val callsAfterOpen = repository.listCalls

        repository.blockedAccounts = listOf(TeamBlockedAccount(accountKey = ADMIN_ACCOUNT_A))
        viewModel.removeAccountFromTeam(ADMIN_ACCOUNT_A)
        runCurrent()

        assertEquals(listOf(ADMIN_ACCOUNT_A), repository.deletedAccounts)
        assertTrue(repository.listCalls > callsAfterOpen)
        val state = assertIs<TeamKeysUiState.Success>(viewModel.uiState.value)
        assertEquals(1, state.blockedAccounts.size)
    }

    @Test
    fun `devolver a conta ao time tira a linha da lista`() = runTest {
        val repository = FakeTeamAdminRepository()
        repository.keys += entry(id = "key-1", label = "fulano@empresa.com")
        repository.blockedAccounts = listOf(TeamBlockedAccount(accountKey = ADMIN_ACCOUNT_B))
        val viewModel = buildViewModel(repository)
        viewModel.open()
        runCurrent()

        viewModel.unblock(ADMIN_ACCOUNT_B)
        runCurrent()

        assertEquals(listOf(ADMIN_ACCOUNT_B), repository.unblockedAccounts)
        val state = assertIs<TeamKeysUiState.Success>(viewModel.uiState.value)
        assertTrue(state.blockedAccounts.isEmpty())
    }

    // Contra servidor anterior à 0.11.0 a leitura devolve lista vazia; se ela
    // falhasse, a tela de chaves inteira sumiria por causa de uma seção acessória.
    @Test
    fun `falha ao ler as bloqueadas nao derruba a lista de chaves`() = runTest {
        val repository = FakeTeamAdminRepository()
        repository.keys += entry(id = "key-1", label = "fulano@empresa.com")
        repository.blockedResult = Result.failure(IllegalStateException("servidor fora"))
        val viewModel = buildViewModel(repository)

        viewModel.open()
        runCurrent()

        val state = assertIs<TeamKeysUiState.Success>(viewModel.uiState.value)
        assertEquals(1, state.keys.size)
        assertTrue(state.blockedAccounts.isEmpty())
    }

    private fun buildViewModel(repository: FakeTeamAdminRepository): TeamKeysAdminViewModel {
        return TeamKeysAdminViewModel(
            listKeys = ListTeamKeysUseCase(repository),
            createKey = CreateTeamKeyUseCase(repository),
            updateKey = UpdateTeamKeyUseCase(repository),
            regenerateKey = RegenerateTeamKeyUseCase(repository),
            revokeKey = RevokeTeamKeyUseCase(repository),
            unclaimAccount = UnclaimTeamKeyAccountUseCase(repository),
            deleteAccount = DeleteTeamAccountUseCase(repository),
            listBlockedAccounts = ListBlockedTeamAccountsUseCase(repository),
            unblockAccount = UnblockTeamAccountUseCase(repository),
            dispatcher = UnconfinedTestDispatcher()
        )
    }

    private fun entry(
        id: String,
        label: String,
        accounts: List<String> = emptyList()
    ): TeamKeyEntry {
        return TeamKeyEntry(
            id = id,
            label = label,
            key = "raw-key",
            keyPrefix = "raw-key",
            maxAccounts = 1,
            accounts = accounts
        )
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class GetAdminTeamOverviewUseCaseTest {

    @Test
    fun `recorta a janela sem ancorar em reset de conta`() = runTest {
        val repository = FakeTeamAdminRepository()

        val result = GetAdminTeamOverviewUseCase(
            repository = repository,
            clock = object : kotlinx.datetime.Clock {
                override fun now(): Instant = ADMIN_FIXED_NOW
            }
        )(range = CliSessionRange.LAST_5H)

        // Contas diferentes resetam em horas diferentes: ancorar numa delas daria
        // um recorte que não corresponde a nenhuma.
        assertTrue(result.isSuccess)
        assertEquals(false, result.getOrThrow().window.isAnchored)
        assertEquals(
            ADMIN_FIXED_NOW.toEpochMilliseconds() - 5L * 60 * 60 * 1_000,
            repository.lastOverviewCutoff
        )
    }

    @Test
    fun `agrupa contas na resposta`() = runTest {
        val repository = FakeTeamAdminRepository()
        repository.overview = listOf(
            accountUsage(ADMIN_ACCOUNT_A, "fulano@empresa.com", "device-1"),
            accountUsage(ADMIN_ACCOUNT_B, null, "device-2")
        )

        val result = GetAdminTeamOverviewUseCase(repository)(range = CliSessionRange.LAST_30D)

        val accounts = result.getOrThrow().accounts
        assertEquals(2, accounts.size)
        assertEquals("fulano@empresa.com", accounts.first().label)
        // Conta sem chave emitida continua na lista, identificada só pelo uuid.
        assertNull(accounts[1].label)
    }
}

private fun accountUsage(
    accountKey: String,
    label: String?,
    deviceId: String
): TeamAccountUsage {
    return TeamAccountUsage(
        accountKey = accountKey,
        label = label,
        snapshot = TeamUsageSnapshot(
            members = listOf(
                TeamMemberUsage(
                    deviceId = deviceId,
                    alias = deviceId,
                    sessions = listOf(
                        CliSessionSummary(
                            sessionId = "s1",
                            filePath = "",
                            firstTs = ADMIN_FIXED_NOW,
                            lastTs = ADMIN_FIXED_NOW
                        )
                    )
                )
            )
        )
    )
}
