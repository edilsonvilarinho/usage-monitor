package com.usagemonitor

import com.usagemonitor.data.datasource.LocalCliSessionDataSource
import com.usagemonitor.data.datasource.LocalTeamSyncStateDataSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.CliProjectRoot
import com.usagemonitor.domain.entity.TeamConnectionFailure
import com.usagemonitor.domain.entity.TeamConnectionStage
import java.net.ConnectException
import com.usagemonitor.domain.entity.CliSessionDetail
import com.usagemonitor.domain.entity.TeamIngestPayload
import com.usagemonitor.domain.entity.TeamIngestReceipt
import com.usagemonitor.domain.entity.TeamIntegrationSettings
import com.usagemonitor.domain.entity.TeamIntegrations
import com.usagemonitor.domain.entity.TeamMemberIdentity
import com.usagemonitor.domain.entity.TeamPresenceReceipt
import com.usagemonitor.domain.entity.TeamUsageSnapshot
import com.usagemonitor.domain.repository.TeamUsageRepository
import com.usagemonitor.domain.repository.TeamUsageTrendData
import com.usagemonitor.data.datasource.TeamServerException
import com.usagemonitor.domain.usecase.PushTeamUsageUseCase
import com.usagemonitor.domain.usecase.TouchTeamPresenceUseCase
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val PROFILE_ID = "default"
private const val ACCOUNT_KEY = "account-uuid-aaa"
private const val ACCOUNT_EMAIL = "helio.sales@empresa.com"
private const val TEAM_A = "team-a"
private const val TEAM_B = "team-b"
private const val PROFILE_B = "outro"
private const val ACCOUNT_B = "account-uuid-bbb"
private const val CHAVE_DO_TIME = "chave-de-time-com-tamanho-suficiente"
private const val CHAVE_CONTA_A = "chave-da-conta-a-com-tamanho-suficiente"
private const val CHAVE_CONTA_B = "chave-da-conta-b-com-tamanho-suficiente"
private const val CHAVE_CONTA_B_NOVA = "chave-da-conta-b-rotacionada-com-tamanho"

/** Um envio com o perfil recebido e a chave que o repositório real usaria para ele. */
private class AccountSend(val profileId: String?, val payload: TeamIngestPayload, val key: String)

/** Captura os lotes enviados e permite forçar falha, sem tocar a rede. */
private class RecordingTeamRepository(
    var failure: Throwable? = null,
    /** Recusa só o envio de identidade, para separá-lo do envio de lotes. */
    private val rejectIdentityPush: Boolean = false,
    /** Falha só a batida de presença, para provar que ela não trava o resto. */
    var presenceFailure: Throwable? = null,
    /** Mesma tabela que o repositório real consulta para escolher a chave de cada conta. */
    private val settingsProvider: () -> TeamIntegrationSettings = { ACTIVE_SETTINGS }
) : TeamUsageRepository {
    val sends = mutableListOf<AccountSend>()

    /** Todo envio, lote ou identidade, na ordem em que chegou. */
    val pushed: List<TeamIngestPayload>
        get() = sends.map { send -> send.payload }

    /** Batidas de presença, na ordem em que chegaram. */
    val presenceTouches = mutableListOf<Pair<String, TeamMemberIdentity>>()
    val presenceEmails = mutableListOf<String?>()

    /** Perfil e chave de cada batida, na mesma ordem de [presenceTouches]. */
    val presenceAccounts = mutableListOf<Pair<String?, String>>()

    /** Lotes de verdade, sem os envios que só carregam a identidade. */
    val turnBatches: List<TeamIngestPayload>
        get() = pushed.filter { payload -> payload.turns.isNotEmpty() }

    /** Envios de identidade: apelido novo sem turno para acompanhar. */
    val identityPushes: List<TeamIngestPayload>
        get() = pushed.filter { payload -> payload.turns.isEmpty() }

    /** Envios de identidade com o perfil de cada conta. */
    val identitySends: List<AccountSend>
        get() = sends.filter { send -> send.payload.turns.isEmpty() }

    override suspend fun push(payload: TeamIngestPayload): Result<TeamIngestReceipt> = push(payload, null)

    override suspend fun push(payload: TeamIngestPayload, profileId: String?): Result<TeamIngestReceipt> {
        val current = failure
        if (current != null) {
            return Result.failure(current)
        }
        if (rejectIdentityPush && payload.turns.isEmpty()) {
            return Result.failure(IllegalStateException("identidade recusada"))
        }
        sends += AccountSend(profileId, payload, settingsProvider().apiKeyFor(profileId))
        return Result.success(
            TeamIngestReceipt(
                acceptedTurns = payload.turns.size,
                acceptedSessions = payload.sessions.size
            )
        )
    }

    override suspend fun fetch(accountKey: String, cutoffMillis: Long?): Result<TeamUsageSnapshot> {
        return Result.success(TeamUsageSnapshot())
    }

    override suspend fun fetchSessionDetail(
        accountKey: String,
        deviceId: String,
        sessionId: String
    ): Result<CliSessionDetail?> {
        return Result.success(null)
    }

    override suspend fun touchPresence(
        accountKey: String,
        member: TeamMemberIdentity
    ): Result<TeamPresenceReceipt> = recordPresence(accountKey, member, profileId = null)

    override suspend fun touchPresence(
        accountKey: String,
        accountEmail: String?,
        member: TeamMemberIdentity
    ): Result<TeamPresenceReceipt> {
        presenceEmails += accountEmail
        return recordPresence(accountKey, member, profileId = null)
    }

    override suspend fun touchPresence(
        accountKey: String,
        member: TeamMemberIdentity,
        profileId: String?
    ): Result<TeamPresenceReceipt> = recordPresence(accountKey, member, profileId)

    override suspend fun touchPresence(
        accountKey: String,
        accountEmail: String?,
        member: TeamMemberIdentity,
        profileId: String?
    ): Result<TeamPresenceReceipt> {
        presenceEmails += accountEmail
        return recordPresence(accountKey, member, profileId)
    }

    private fun recordPresence(
        accountKey: String,
        member: TeamMemberIdentity,
        profileId: String?
    ): Result<TeamPresenceReceipt> {
        val current = presenceFailure
        if (current != null) {
            return Result.failure(current)
        }
        presenceTouches += accountKey to member
        presenceAccounts += profileId to settingsProvider().apiKeyFor(profileId)
        return Result.success(TeamPresenceReceipt())
    }

    override suspend fun checkConnection(): Result<Unit> = Result.success(Unit)

    override suspend fun fetchTrend(accountKey: String, days: Int): Result<TeamUsageTrendData?> {
        return Result.success(null)
    }
}

private val ACTIVE_SETTINGS = TeamIntegrationSettings(
    enabled = true,
    serverUrl = "http://localhost:3000",
    apiKey = CHAVE_DO_TIME,
    alias = "edilson",
    deviceId = "device-1",
    id = TEAM_A,
    participatingProfileIds = setOf(PROFILE_ID)
)

/** Duas contas do mesmo time, cada uma com a sua chave; a chave do time continua definida. */
private val TWO_KEYED_ACCOUNTS_SETTINGS = ACTIVE_SETTINGS.copy(
    participatingProfileIds = setOf(PROFILE_ID, PROFILE_B),
    accountApiKeys = mapOf(PROFILE_ID to CHAVE_CONTA_A, PROFILE_B to CHAVE_CONTA_B)
)

class TeamSyncServiceTest {

    @Test
    fun `envia os turnos indexados e nao reenvia na passada seguinte`() = runTest {
        withFixture { root, indexer, syncState ->
            writeTranscript(
                root,
                "session-a",
                assistantLine("session-a", "msg-1", "2026-08-01T10:00:00Z", outputTokens = 100L),
                assistantLine("session-a", "msg-2", "2026-08-01T10:05:00Z", outputTokens = 200L)
            )
            indexer.syncIndex()

            val repository = RecordingTeamRepository()
            val service = newService(syncState, repository)

            val first = service.syncOnce()
            assertEquals(2, first.pushedTurns)
            assertEquals(1, first.batches)

            val payload = repository.turnBatches.single()
            assertEquals(ACCOUNT_KEY, payload.accountKey)
            assertEquals(ACCOUNT_EMAIL, payload.accountEmail)
            assertEquals("edilson", payload.member.alias)
            assertEquals(2, payload.turns.size)
            // As sessões dos turnos têm de viajar no mesmo lote: o servidor
            // rejeita turno órfão porque a consulta dele faz JOIN na sessão.
            assertEquals(setOf("session-a"), payload.sessions.map { it.sessionId }.toSet())

            val second = service.syncOnce()
            assertEquals(0, second.pushedTurns)
            assertEquals(1, repository.turnBatches.size)
        }
    }

    @Test
    fun `envia apenas os turnos novos apos uma passada bem sucedida`() = runTest {
        withFixture { root, indexer, syncState ->
            writeTranscript(root, "session-a", assistantLine("session-a", "msg-1", "2026-08-01T10:00:00Z"))
            indexer.syncIndex()

            val repository = RecordingTeamRepository()
            val service = newService(syncState, repository)
            service.syncOnce()

            writeTranscript(
                root,
                "session-a",
                assistantLine("session-a", "msg-1", "2026-08-01T10:00:00Z"),
                assistantLine("session-a", "msg-2", "2026-08-01T10:05:00Z", outputTokens = 50L)
            )
            indexer.syncIndex()

            val report = service.syncOnce()

            assertEquals(1, report.pushedTurns)
            assertEquals("msg-2", repository.turnBatches.last().turns.single().messageId)
        }
    }

    @Test
    fun `falha no envio nao avanca o marcador`() = runTest {
        withFixture { root, indexer, syncState ->
            writeTranscript(root, "session-a", assistantLine("session-a", "msg-1", "2026-08-01T10:00:00Z"))
            indexer.syncIndex()

            val repository = RecordingTeamRepository(failure = IllegalStateException("servidor fora do ar"))
            val service = newService(syncState, repository)

            val failed = service.syncOnce()
            assertTrue(failed.hasFailure)
            assertEquals(0, failed.pushedTurns)

            repository.failure = null
            val recovered = service.syncOnce()

            // O lote que falhou volta inteiro na passada seguinte.
            assertEquals(1, recovered.pushedTurns)
            assertEquals("msg-1", repository.turnBatches.single().turns.single().messageId)
        }
    }

    @Test
    fun `nao envia nada com a integracao desligada`() = runTest {
        withFixture { root, indexer, syncState ->
            writeTranscript(root, "session-a", assistantLine("session-a", "msg-1", "2026-08-01T10:00:00Z"))
            indexer.syncIndex()

            val repository = RecordingTeamRepository()
            val service = newService(syncState, repository, settings = ACTIVE_SETTINGS.copy(enabled = false))

            assertEquals(0, service.syncOnce().pushedTurns)
            assertTrue(repository.pushed.isEmpty())
        }
    }

    @Test
    fun `ignora perfil que nao participa do time`() = runTest {
        withFixture { root, indexer, syncState ->
            writeTranscript(root, "session-a", assistantLine("session-a", "msg-1", "2026-08-01T10:00:00Z"))
            indexer.syncIndex()

            val repository = RecordingTeamRepository()
            val service = newService(
                syncState,
                repository,
                settings = ACTIVE_SETTINGS.copy(participatingProfileIds = setOf("outra-conta"))
            )

            assertEquals(0, service.syncOnce().pushedTurns)
            assertTrue(repository.pushed.isEmpty())
        }
    }

    @Test
    fun `quebra o backfill em varios lotes`() = runTest {
        withFixture { root, indexer, syncState ->
            val lines = (1..5).map { index ->
                assistantLine("session-a", "msg-$index", "2026-08-01T10:0$index:00Z", outputTokens = 10L)
            }
            writeTranscript(root, "session-a", *lines.toTypedArray())
            indexer.syncIndex()

            val repository = RecordingTeamRepository()
            val service = newService(syncState, repository, batchSize = 2)

            val report = service.syncOnce()

            assertEquals(5, report.pushedTurns)
            assertEquals(3, report.batches)
            assertEquals(listOf(2, 2, 1), repository.turnBatches.map { it.turns.size })
        }
    }

    @Test
    fun `reenvia a sessao quando o transcript e recriado e os seq recomecam`() = runTest {
        withFixture { root, indexer, syncState ->
            writeTranscript(
                root,
                "session-a",
                assistantLine("session-a", "msg-1", "2026-08-01T10:00:00Z"),
                assistantLine("session-a", "msg-2", "2026-08-01T10:05:00Z")
            )
            indexer.syncIndex()

            val repository = RecordingTeamRepository()
            val service = newService(syncState, repository)
            service.syncOnce()

            // Transcript truncado e reescrito no mesmo caminho: o índice
            // reconstrói a sessão do zero e os `seq` voltam a começar em 0.
            writeTranscript(root, "session-a", assistantLine("session-a", "msg-9", "2026-08-02T10:00:00Z"))
            indexer.syncIndex()

            val report = service.syncOnce()

            // Sem o reset do marcador o turno novo ficaria abaixo dele e nunca sairia.
            assertEquals(1, report.pushedTurns)
            assertEquals("msg-9", repository.turnBatches.last().turns.single().messageId)
        }
    }

    @Test
    fun `indexa o transcript antes de enviar`() = runTest {
        withFixture { root, indexer, syncState ->
            // De proposito sem syncIndex(): e o servico que tem de indexar. Sem
            // isso a latencia do time seria a do laco de background (10min).
            writeTranscript(root, "session-a", assistantLine("session-a", "msg-1", "2026-08-01T10:00:00Z"))

            val repository = RecordingTeamRepository()
            val service = newService(syncState, repository, ensureIndexFresh = { indexer.syncIndex() })

            val report = service.syncOnce()

            assertEquals(1, report.pushedTurns)
            assertEquals("msg-1", repository.turnBatches.single().turns.single().messageId)
        }
    }

    @Test
    fun `falha na indexacao nao impede o envio do que ja esta indexado`() = runTest {
        withFixture { root, indexer, syncState ->
            writeTranscript(root, "session-a", assistantLine("session-a", "msg-1", "2026-08-01T10:00:00Z"))
            indexer.syncIndex()

            val repository = RecordingTeamRepository()
            val service = newService(
                syncState,
                repository,
                ensureIndexFresh = { throw IllegalStateException("disco indisponivel") }
            )

            val report = service.syncOnce()

            assertTrue(report.hasFailure)
            assertEquals(1, report.pushedTurns)
            assertEquals("msg-1", repository.turnBatches.single().turns.single().messageId)
        }
    }

    @Test
    fun `indexa uma unica vez por passada mesmo com varias contas`() = runTest {
        withFixture { root, indexer, syncState ->
            writeTranscript(root, "session-a", assistantLine("session-a", "msg-1", "2026-08-01T10:00:00Z"))

            var indexCalls = 0
            val repository = RecordingTeamRepository()
            val service = newService(
                syncState,
                repository,
                ensureIndexFresh = {
                    indexCalls++
                    indexer.syncIndex()
                },
                targets = listOf(
                    TeamSyncTarget(profileId = PROFILE_ID, accountKey = ACCOUNT_KEY),
                    TeamSyncTarget(profileId = PROFILE_ID, accountKey = "account-uuid-bbb")
                )
            )

            service.syncOnce()

            // O indice e um so: varrer os transcritos por conta seria trabalho repetido.
            assertEquals(1, indexCalls)
        }
    }

    @Test
    fun `envia o apelido mesmo sem nenhum turno para mandar`() = runTest {
        withFixture { _, _, syncState ->
            val repository = RecordingTeamRepository()
            val service = newService(syncState, repository)

            val report = service.syncOnce()

            // Sem isto o apelido só chegaria ao servidor de carona num lote de
            // turnos, e quem renomeia e para de usar o CLI ficaria com o nome
            // velho na tela do time indefinidamente.
            assertEquals(0, report.pushedTurns)
            val identity = repository.identityPushes.single()
            assertEquals("edilson", identity.member.alias)
            assertEquals("device-1", identity.member.deviceId)
            assertEquals("DESKTOP-TEST", identity.member.hostName)
            assertTrue(identity.sessions.isEmpty())
        }
    }

    @Test
    fun `nao repete o envio de identidade enquanto o apelido nao muda`() = runTest {
        withFixture { _, _, syncState ->
            val repository = RecordingTeamRepository()
            var settings = ACTIVE_SETTINGS
            val service = newService(syncState, repository, settingsProvider = { settings })

            service.syncOnce()
            service.syncOnce()
            assertEquals(1, repository.identityPushes.size)

            settings = settings.copy(alias = "edilson-2")
            service.syncOnce()

            assertEquals(2, repository.identityPushes.size)
            assertEquals("edilson-2", repository.identityPushes.last().member.alias)
        }
    }

    @Test
    fun `trocar a chave de time reenvia a identidade mesmo sem turno novo`() = runTest {
        withFixture { _, _, syncState ->
            val repository = RecordingTeamRepository()
            var settings = ACTIVE_SETTINGS
            val service = newService(syncState, repository, settingsProvider = { settings })

            service.syncOnce()
            assertEquals(1, repository.identityPushes.size)

            // Sem isto o vínculo da chave nova com a conta — que no servidor só
            // nasce dentro de um ingest — nunca aconteceria numa máquina que já
            // enviou tudo, e o time inteiro ficaria sem conseguir ler.
            settings = settings.copy(apiKey = "chave-nova-com-tamanho-suficiente")
            service.syncOnce()

            assertEquals(2, repository.identityPushes.size)
            assertEquals("edilson", repository.identityPushes.last().member.alias)
        }
    }

    @Test
    fun `cada conta envia lotes e carimba presenca com a chave da propria conta`() = runTest {
        withTwoProfilesFixture { rootA, rootB, indexer, syncState ->
            writeTranscript(rootA, "session-a", assistantLine("session-a", "msg-a", "2026-08-01T10:00:00Z"))
            writeTranscript(rootB, "session-b", assistantLine("session-b", "msg-b", "2026-08-01T11:00:00Z"))
            indexer.syncIndex()
            val repository = RecordingTeamRepository(settingsProvider = { TWO_KEYED_ACCOUNTS_SETTINGS })
            val service = newTeamsService(
                syncState,
                mapOf(TEAM_A to repository),
                integrations = integrationsOf(TWO_KEYED_ACCOUNTS_SETTINGS)
            )

            service.syncOnce()

            // Conta trocada na credencial é vínculo errado no servidor: cada lote e
            // cada batida têm de sair com o perfil da conta e a chave dela.
            val ingestAccounts = repository.sends
                .filter { send -> send.payload.turns.isNotEmpty() }
                .map { send -> send.profileId to send.key }
            assertEquals(
                setOf(PROFILE_ID to CHAVE_CONTA_A, PROFILE_B to CHAVE_CONTA_B),
                ingestAccounts.toSet()
            )
            assertEquals(
                setOf(PROFILE_ID to CHAVE_CONTA_A, PROFILE_B to CHAVE_CONTA_B),
                repository.presenceAccounts.toSet()
            )
        }
    }

    @Test
    fun `conta sem chave propria envia e carimba com a chave do time`() = runTest {
        withTwoProfilesFixture { rootA, rootB, indexer, syncState ->
            writeTranscript(rootA, "session-a", assistantLine("session-a", "msg-a", "2026-08-01T10:00:00Z"))
            writeTranscript(rootB, "session-b", assistantLine("session-b", "msg-b", "2026-08-01T11:00:00Z"))
            indexer.syncIndex()
            val settings = ACTIVE_SETTINGS.copy(
                participatingProfileIds = setOf(PROFILE_ID, PROFILE_B),
                accountApiKeys = mapOf(PROFILE_ID to CHAVE_CONTA_A)
            )
            val repository = RecordingTeamRepository(settingsProvider = { settings })
            val service = newTeamsService(syncState, mapOf(TEAM_A to repository), integrations = integrationsOf(settings))

            service.syncOnce()

            // A conta B não tem chave própria: o servidor a recebe com a do time,
            // e a A continua com a dela.
            val ingestAccounts = repository.sends
                .filter { send -> send.payload.turns.isNotEmpty() }
                .map { send -> send.profileId to send.key }
            assertEquals(
                setOf(PROFILE_ID to CHAVE_CONTA_A, PROFILE_B to CHAVE_DO_TIME),
                ingestAccounts.toSet()
            )
            assertEquals(
                setOf(PROFILE_ID to CHAVE_CONTA_A, PROFILE_B to CHAVE_DO_TIME),
                repository.presenceAccounts.toSet()
            )
        }
    }

    @Test
    fun `trocar a chave propria de uma conta reenvia a identidade so dela`() = runTest {
        withTwoProfilesFixture { _, _, _, syncState ->
            var settings = TWO_KEYED_ACCOUNTS_SETTINGS
            val repository = RecordingTeamRepository(settingsProvider = { settings })
            val service = newTeamsService(
                syncState,
                mapOf(TEAM_A to repository),
                integrationsProvider = { integrationsOf(settings) }
            )

            service.syncOnce()
            assertEquals(1, repository.identitySends.count { send -> send.profileId == PROFILE_ID })
            assertEquals(1, repository.identitySends.count { send -> send.profileId == PROFILE_B })

            settings = settings.copy(accountApiKeys = settings.accountApiKeys + (PROFILE_B to CHAVE_CONTA_B_NOVA))
            service.syncOnce()

            // A conta A não mudou de chave: não pode haver reenvio dela.
            assertEquals(1, repository.identitySends.count { send -> send.profileId == PROFILE_ID })
            // A conta B trocou de chave: o vínculo novo só nasce dentro de um envio.
            assertEquals(2, repository.identitySends.count { send -> send.profileId == PROFILE_B })
            assertEquals(CHAVE_CONTA_B_NOVA, repository.identitySends.last().key)
        }
    }

    @Test
    fun `publica a falha da passada para a tela`() = runTest {
        withFixture { _, _, syncState ->
            val repository = RecordingTeamRepository(failure = IllegalStateException("servidor fora"))
            val service = newService(syncState, repository)

            service.syncOnce()

            val status = service.syncStatus.value
            assertTrue(status.isFailing)
            assertEquals("servidor fora", status.lastFailureMessage)
        }
    }

    @Test
    fun `publica a etapa em que a passada parou`() = runTest {
        withFixture { _, _, syncState ->
            val repository = RecordingTeamRepository(failure = ConnectException("Connection refused"))
            val service = newService(syncState, repository)

            service.syncOnce()

            assertEquals(TeamConnectionStage.CONNECTION, service.syncStatus.value.lastFailure?.stage)
        }
    }

    @Test
    fun `o texto da falha de envio diz a etapa e cai na mensagem sem ela`() {
        val classified = TeamSyncStatus(
            lastFailureMessage = "Connection refused",
            lastFailure = TeamConnectionFailure(stage = TeamConnectionStage.CONNECTION)
        )
        assertEquals("Parou em Conexão — nada responde", classified.failureText(AppLanguage.PT))
        assertEquals("HTTP 500", TeamSyncStatus(lastFailureMessage = "HTTP 500").failureText(AppLanguage.EN))
        assertEquals(null, TeamSyncStatus().failureText(AppLanguage.EN))
    }

    @Test
    fun `recusa do servidor fica presa na conta que a causou`() = runTest {
        withFixture { _, _, syncState ->
            val repository = RecordingTeamRepository(
                failure = TeamServerException(
                    statusCode = 403,
                    message = "Servidor de time recusou a envio ao time (HTTP 403): fora do rótulo",
                    detail = "fora do rótulo"
                )
            )
            val service = newService(syncState, repository)

            service.syncOnce()

            // O detalhe, e não a mensagem inteira: a linha da conta já diz de
            // quem é o problema, e o prefixo da operação ocuparia metade dela.
            val status = service.syncStatus.value
            assertEquals(mapOf(PROFILE_ID to "fora do rótulo"), status.rejectedProfiles)
        }
    }

    @Test
    fun `falha que nao e recusa de conta nao marca conta nenhuma`() = runTest {
        withFixture { _, _, syncState ->
            // 401 é chave errada e 5xx é o servidor: nenhum dos dois aponta para
            // uma conta específica, e marcar a linha mandaria o usuário desmarcar
            // uma conta que não tem defeito nenhum.
            val repository = RecordingTeamRepository(
                failure = TeamServerException(
                    statusCode = 401,
                    message = "chave inválida",
                    detail = "chave inválida"
                )
            )
            val service = newService(syncState, repository)

            service.syncOnce()

            assertTrue(service.syncStatus.value.isFailing)
            assertTrue(service.syncStatus.value.rejectedProfiles.isEmpty())
        }
    }

    @Test
    fun `desmarcar a conta recusada limpa o aviso dela`() = runTest {
        withFixture { _, _, syncState ->
            val repository = RecordingTeamRepository(
                failure = TeamServerException(
                    statusCode = 403,
                    message = "recusada",
                    detail = "recusada"
                )
            )
            val service = newService(syncState, repository)
            service.syncOnce()
            assertTrue(service.syncStatus.value.rejectedProfiles.isNotEmpty())

            repository.failure = null
            service.syncOnce()

            // A passada corrente é quem sabe quais contas ainda são recusadas:
            // acumular deixaria o aviso preso numa conta já consertada.
            assertTrue(service.syncStatus.value.rejectedProfiles.isEmpty())
        }
    }

    @Test
    fun `sucesso posterior encerra o aviso de falha`() = runTest {
        withFixture { _, _, syncState ->
            val repository = RecordingTeamRepository(failure = IllegalStateException("servidor fora"))
            val service = newService(syncState, repository)
            service.syncOnce()
            assertTrue(service.syncStatus.value.isFailing)

            repository.failure = null
            service.syncOnce()

            // O aviso é sobre o estado atual: uma mensagem que sobrevive ao
            // conserto manda o usuário atrás de um problema que já não existe.
            assertFalse(service.syncStatus.value.isFailing)
            assertEquals(null, service.syncStatus.value.lastFailureMessage)
        }
    }

    @Test
    fun `falha no envio de identidade nao impede o envio dos lotes`() = runTest {
        withFixture { root, indexer, syncState ->
            writeTranscript(root, "session-a", assistantLine("session-a", "msg-1", "2026-08-01T10:00:00Z"))
            indexer.syncIndex()

            // Recusa só o envio sem turnos: o marcador do apelido não avança e o
            // lote de verdade tem de sair na mesma passada.
            val repository = RecordingTeamRepository(rejectIdentityPush = true)
            val service = newService(syncState, repository)

            val report = service.syncOnce()

            assertTrue(report.hasFailure)
            assertEquals(1, report.pushedTurns)
            assertEquals("msg-1", repository.turnBatches.single().turns.single().messageId)
        }
    }

    @Test
    fun `requestImmediateSync envia sem esperar o intervalo do laco`() = runTest {
        withFixture { root, indexer, syncState ->
            writeTranscript(root, "session-a", assistantLine("session-a", "msg-1", "2026-08-01T10:00:00Z"))

            val repository = RecordingTeamRepository()
            val service = newService(syncState, repository, ensureIndexFresh = { indexer.syncIndex() })
            try {
                // Sem start(): o gatilho da janela sozinho tem de entregar o lote.
                service.requestImmediateSync().join()

                assertEquals("msg-1", repository.turnBatches.single().turns.single().messageId)
            } finally {
                service.onDestroy()
            }
        }
    }

    @Test
    fun `a passada carimba presenca mesmo sem turno pendente`() = runTest {
        withFixture { _, _, syncState ->
            val repository = RecordingTeamRepository()
            val service = newService(syncState, repository)

            service.syncOnce()

            // O caso normal de quem ja sincronizou tudo: nenhum lote sai, e sem a
            // batida a maquina ficaria indistinguivel de uma desligada.
            val (accountKey, member) = repository.presenceTouches.single()
            assertEquals(ACCOUNT_KEY, accountKey)
            assertEquals("device-1", member.deviceId)
            assertEquals("edilson", member.alias)
            assertEquals("DESKTOP-TEST", member.hostName)
            assertEquals(listOf<String?>(ACCOUNT_EMAIL), repository.presenceEmails)
            assertTrue(repository.turnBatches.isEmpty())
        }
    }

    @Test
    fun `a presenca sai a cada passada e nao so quando algo muda`() = runTest {
        withFixture { _, _, syncState ->
            val repository = RecordingTeamRepository()
            val service = newService(syncState, repository)

            service.syncOnce()
            service.syncOnce()
            service.syncOnce()

            assertEquals(3, repository.presenceTouches.size)
            // Identidade continua com o proprio marcador: sai uma vez so.
            assertEquals(1, repository.identityPushes.size)
        }
    }

    @Test
    fun `a presenca sai uma vez por conta participante`() = runTest {
        withFixture { _, _, syncState ->
            val repository = RecordingTeamRepository()
            val service = newService(
                syncState,
                repository,
                settings = ACTIVE_SETTINGS.copy(participatingProfileIds = setOf(PROFILE_ID, "outro")),
                targets = listOf(
                    TeamSyncTarget(profileId = PROFILE_ID, accountKey = ACCOUNT_KEY),
                    TeamSyncTarget(profileId = "outro", accountKey = "account-uuid-bbb")
                )
            )

            service.syncOnce()

            // `last_seen_at` e (conta, maquina): uma batida so cobriria uma conta.
            assertEquals(
                listOf(ACCOUNT_KEY, "account-uuid-bbb"),
                repository.presenceTouches.map { touch -> touch.first }
            )
        }
    }

    @Test
    fun `falha na presenca nao impede o envio dos turnos`() = runTest {
        withFixture { root, indexer, syncState ->
            writeTranscript(root, "session-a", assistantLine("session-a", "msg-1", "2026-08-01T10:00:00Z"))
            indexer.syncIndex()

            val repository = RecordingTeamRepository(
                presenceFailure = IllegalStateException("presenca recusada")
            )
            val service = newService(syncState, repository)

            val report = service.syncOnce()

            assertEquals(1, report.pushedTurns)
            assertTrue(report.hasFailure)
            assertTrue(repository.presenceTouches.isEmpty())
        }
    }

    @Test
    fun `falha na presenca aparece no status para as Configuracoes`() = runTest {
        withFixture { _, _, syncState ->
            val repository = RecordingTeamRepository(
                presenceFailure = IllegalStateException("presenca recusada")
            )
            val service = newService(syncState, repository)

            service.syncOnce()

            // Heartbeat falhando significa que a pessoa nao aparece online: e um
            // problema real e merece o mesmo aviso do resto da passada.
            assertTrue(service.syncStatus.value.isFailing)
        }
    }

    @Test
    fun `sem o caso de uso de presenca a passada segue igual`() = runTest {
        withFixture { root, indexer, syncState ->
            writeTranscript(root, "session-a", assistantLine("session-a", "msg-1", "2026-08-01T10:00:00Z"))
            indexer.syncIndex()

            val repository = RecordingTeamRepository()
            val service = newService(syncState, repository, touchTeamPresence = null)

            val report = service.syncOnce()

            assertEquals(1, report.pushedTurns)
            assertFalse(report.hasFailure)
            assertTrue(repository.presenceTouches.isEmpty())
        }
    }

    @Test
    fun `integracao desligada nao carimba presenca`() = runTest {
        withFixture { _, _, syncState ->
            val repository = RecordingTeamRepository()
            val service = newService(
                syncState,
                repository,
                settings = ACTIVE_SETTINGS.copy(enabled = false)
            )

            service.syncOnce()

            assertTrue(repository.presenceTouches.isEmpty())
        }
    }

    @Test
    fun `cada time recebe so o lote da sua conta e o marcador usa o id do time`() = runTest {
        withTwoProfilesFixture { rootA, rootB, indexer, syncState ->
            writeTranscript(rootA, "session-a", assistantLine("session-a", "msg-1", "2026-08-01T10:00:00Z"))
            writeTranscript(rootB, "session-b", assistantLine("session-b", "msg-2", "2026-08-01T10:00:00Z"))
            indexer.syncIndex()

            val repositories = mapOf(TEAM_A to RecordingTeamRepository(), TEAM_B to RecordingTeamRepository())
            val service = newTeamsService(syncState, repositories)

            service.syncOnce()

            assertEquals(listOf("msg-1"), messageIds(repositories.getValue(TEAM_A)))
            assertEquals(listOf("msg-2"), messageIds(repositories.getValue(TEAM_B)))
            // Marcador por (time, perfil): o envio do time B não pode avançar o do time A.
            assertTrue(syncState.readPendingBatch(TEAM_A, PROFILE_ID, 10).isEmpty)
            assertTrue(syncState.readPendingBatch(TEAM_B, PROFILE_B, 10).isEmpty)
            assertFalse(syncState.readPendingBatch(TEAM_A, PROFILE_B, 10).isEmpty)
        }
    }

    @Test
    fun `falha de um time nao impede o envio do outro`() = runTest {
        withTwoProfilesFixture { rootA, rootB, indexer, syncState ->
            writeTranscript(rootA, "session-a", assistantLine("session-a", "msg-1", "2026-08-01T10:00:00Z"))
            writeTranscript(rootB, "session-b", assistantLine("session-b", "msg-2", "2026-08-01T10:00:00Z"))
            indexer.syncIndex()

            val repositories = mapOf(
                TEAM_A to RecordingTeamRepository(failure = IllegalStateException("time A fora")),
                TEAM_B to RecordingTeamRepository()
            )
            val service = newTeamsService(syncState, repositories)

            val report = service.syncOnce()

            assertTrue(report.hasFailure)
            assertEquals(listOf("msg-2"), messageIds(repositories.getValue(TEAM_B)))
        }
    }

    @Test
    fun `time sem chave de acesso nao recebe nenhum envio`() = runTest {
        withTwoProfilesFixture { rootA, rootB, indexer, syncState ->
            writeTranscript(rootA, "session-a", assistantLine("session-a", "msg-1", "2026-08-01T10:00:00Z"))
            writeTranscript(rootB, "session-b", assistantLine("session-b", "msg-2", "2026-08-01T10:00:00Z"))
            indexer.syncIndex()

            val teams = twoTeams().teams
            val integrations = TeamIntegrations(
                deviceId = "device-1",
                enabled = true,
                teams = listOf(teams[0], teams[1].copy(apiKey = ""))
            )
            val repositories = mapOf(TEAM_A to RecordingTeamRepository(), TEAM_B to RecordingTeamRepository())
            val service = newTeamsService(syncState, repositories, integrations)

            service.syncOnce()

            assertEquals(listOf("msg-1"), messageIds(repositories.getValue(TEAM_A)))
            assertTrue(messageIds(repositories.getValue(TEAM_B)).isEmpty())
            assertTrue(repositories.getValue(TEAM_B).presenceTouches.isEmpty())
        }
    }

    @Test
    fun `identidade confirmada num time nao suprime o envio de identidade ao outro`() = runTest {
        withTwoProfilesFixture { _, _, _, syncState ->
            // Mesma conta em dois times: o agregado do app não permite, mas a chave da
            // confirmação precisa separar os times mesmo assim.
            val repositories = mapOf(TEAM_A to RecordingTeamRepository(), TEAM_B to RecordingTeamRepository())
            val shared = TeamIntegrations(
                deviceId = "device-1",
                enabled = true,
                teams = listOf(
                    ACTIVE_SETTINGS.copy(id = TEAM_A),
                    ACTIVE_SETTINGS.copy(id = TEAM_B)
                )
            )
            val service = newTeamsService(
                syncState,
                repositories,
                shared,
                targets = listOf(TeamSyncTarget(profileId = PROFILE_ID, accountKey = ACCOUNT_KEY))
            )

            service.syncOnce()

            assertEquals(1, repositories.getValue(TEAM_A).identityPushes.size)
            assertEquals(1, repositories.getValue(TEAM_B).identityPushes.size)
        }
    }

    @Test
    fun `status de cada time fica separado`() = runTest {
        withTwoProfilesFixture { _, _, _, syncState ->
            val repositories = mapOf(
                TEAM_A to RecordingTeamRepository(failure = IllegalStateException("time A fora")),
                TEAM_B to RecordingTeamRepository()
            )
            val service = newTeamsService(syncState, repositories)

            service.syncOnce()

            val statuses = service.syncStatusByTeam.value
            assertNotNull(statuses[TEAM_B])
            assertTrue(statuses.getValue(TEAM_A).isFailing)
            assertFalse(statuses.getValue(TEAM_B).isFailing)
        }
    }

    private fun twoTeams(): TeamIntegrations = TeamIntegrations(
        deviceId = "device-1",
        enabled = true,
        teams = listOf(
            ACTIVE_SETTINGS.copy(id = TEAM_A, participatingProfileIds = setOf(PROFILE_ID)),
            ACTIVE_SETTINGS.copy(id = TEAM_B, participatingProfileIds = setOf(PROFILE_B))
        )
    )

    private fun newTeamsService(
        syncState: LocalTeamSyncStateDataSource,
        repositories: Map<String, RecordingTeamRepository>,
        integrations: TeamIntegrations = twoTeams(),
        integrationsProvider: () -> TeamIntegrations = { integrations },
        targets: List<TeamSyncTarget> = listOf(
            TeamSyncTarget(profileId = PROFILE_ID, accountKey = ACCOUNT_KEY, accountEmail = ACCOUNT_EMAIL),
            TeamSyncTarget(profileId = PROFILE_B, accountKey = ACCOUNT_B)
        )
    ): TeamSyncService {
        return TeamSyncService(
            syncStateDataSource = syncState,
            pushTeamUsageFor = { teamId -> PushTeamUsageUseCase(repositories.getValue(teamId)) },
            integrationsProvider = integrationsProvider,
            targetsProvider = { targets },
            touchTeamPresenceFor = { teamId -> TouchTeamPresenceUseCase(repositories.getValue(teamId)) },
            hostNameProvider = { "DESKTOP-TEST" }
        )
    }

    private fun messageIds(repository: RecordingTeamRepository): List<String> =
        repository.turnBatches.flatMap { payload -> payload.turns.map { turn -> turn.messageId } }

    private fun integrationsOf(settings: TeamIntegrationSettings): TeamIntegrations =
        TeamIntegrations(deviceId = settings.deviceId, enabled = settings.enabled, teams = listOf(settings))

    private suspend fun withTwoProfilesFixture(
        block: suspend (File, File, LocalCliSessionDataSource, LocalTeamSyncStateDataSource) -> Unit
    ) {
        val tempDir = createTempDirectory().toFile()
        val rootA = File(tempDir, "projects-a").also { it.mkdirs() }
        val rootB = File(tempDir, "projects-b").also { it.mkdirs() }
        val indexer = LocalCliSessionDataSource(
            projectRootsProvider = {
                listOf(CliProjectRoot(PROFILE_ID, rootA.absolutePath), CliProjectRoot(PROFILE_B, rootB.absolutePath))
            },
            databaseFile = File(tempDir, "usage-history.db")
        )
        val syncState = LocalTeamSyncStateDataSource(indexer.sharedConnectionManager)
        try {
            block(rootA, rootB, indexer, syncState)
        } finally {
            indexer.close()
            tempDir.deleteRecursively()
        }
    }

    private fun newService(
        syncState: LocalTeamSyncStateDataSource,
        repository: RecordingTeamRepository,
        settings: TeamIntegrationSettings = ACTIVE_SETTINGS,
        batchSize: Int = 2_000,
        ensureIndexFresh: (suspend () -> Unit)? = null,
        targets: List<TeamSyncTarget> = listOf(
            TeamSyncTarget(
                profileId = PROFILE_ID,
                accountKey = ACCOUNT_KEY,
                accountEmail = ACCOUNT_EMAIL
            )
        ),
        settingsProvider: () -> TeamIntegrationSettings = { settings },
        /** `null` reproduz uma instalação anterior à presença. */
        touchTeamPresence: TouchTeamPresenceUseCase? = TouchTeamPresenceUseCase(repository)
    ): TeamSyncService {
        return TeamSyncService(
            syncStateDataSource = syncState,
            pushTeamUsageFor = { PushTeamUsageUseCase(repository) },
            integrationsProvider = { integrationsOf(settingsProvider()) },
            targetsProvider = { targets },
            ensureIndexFresh = ensureIndexFresh,
            touchTeamPresenceFor = { touchTeamPresence },
            hostNameProvider = { "DESKTOP-TEST" },
            batchSize = batchSize
        )
    }

    private suspend fun withFixture(
        block: suspend (File, LocalCliSessionDataSource, LocalTeamSyncStateDataSource) -> Unit
    ) {
        val tempDir = createTempDirectory().toFile()
        val projectsRoot = File(tempDir, "projects").also { it.mkdirs() }
        val indexer = LocalCliSessionDataSource(
            projectRootsProvider = { listOf(CliProjectRoot(PROFILE_ID, projectsRoot.absolutePath)) },
            databaseFile = File(tempDir, "usage-history.db")
        )
        // Mesma conexão do índice, de propósito: duas conexões para o mesmo
        // arquivo disputariam a escrita e dariam SQLITE_BUSY.
        val syncState = LocalTeamSyncStateDataSource(indexer.sharedConnectionManager)
        try {
            block(projectsRoot, indexer, syncState)
        } finally {
            indexer.close()
            tempDir.deleteRecursively()
        }
    }

    private fun writeTranscript(root: File, sessionId: String, vararg lines: String): File {
        val projectDir = File(root, "-workspace-usage-monitor").also { it.mkdirs() }
        val file = File(projectDir, "$sessionId.jsonl")
        file.writeText(lines.joinToString(separator = "\n", postfix = "\n"))
        return file
    }

    private fun assistantLine(
        sessionId: String,
        messageId: String,
        timestamp: String,
        model: String = "claude-opus-5",
        inputTokens: Long = 0L,
        outputTokens: Long = 0L,
        cacheReadTokens: Long = 0L
    ): String {
        return """{"type":"assistant","uuid":"uuid-$messageId","sessionId":"$sessionId",""" +
            """"timestamp":"$timestamp","cwd":"/workspace/usage-monitor","gitBranch":"main",""" +
            """"version":"2.1.226","isSidechain":false,""" +
            """"message":{"id":"$messageId","model":"$model","stop_reason":"end_turn","usage":{""" +
            """"input_tokens":$inputTokens,"output_tokens":$outputTokens,""" +
            """"cache_read_input_tokens":$cacheReadTokens,""" +
            """"cache_creation_input_tokens":0,""" +
            """"cache_creation":{"ephemeral_5m_input_tokens":0,""" +
            """"ephemeral_1h_input_tokens":0},"service_tier":"standard"}}}"""
    }
}
