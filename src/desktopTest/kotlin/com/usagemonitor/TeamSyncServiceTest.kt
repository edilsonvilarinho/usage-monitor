package com.usagemonitor

import com.usagemonitor.data.datasource.LocalCliSessionDataSource
import com.usagemonitor.data.datasource.LocalTeamSyncStateDataSource
import com.usagemonitor.domain.entity.CliProjectRoot
import com.usagemonitor.domain.entity.CliSessionDetail
import com.usagemonitor.domain.entity.TeamIngestPayload
import com.usagemonitor.domain.entity.TeamIngestReceipt
import com.usagemonitor.domain.entity.TeamIntegrationSettings
import com.usagemonitor.domain.entity.TeamMemberIdentity
import com.usagemonitor.domain.entity.TeamPresenceReceipt
import com.usagemonitor.domain.entity.TeamUsageSnapshot
import com.usagemonitor.domain.repository.TeamUsageRepository
import com.usagemonitor.domain.usecase.PushTeamUsageUseCase
import com.usagemonitor.domain.usecase.TouchTeamPresenceUseCase
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val PROFILE_ID = "default"
private const val ACCOUNT_KEY = "account-uuid-aaa"

/** Captura os lotes enviados e permite forçar falha, sem tocar a rede. */
private class RecordingTeamRepository(
    var failure: Throwable? = null,
    /** Recusa só o envio de identidade, para separá-lo do envio de lotes. */
    private val rejectIdentityPush: Boolean = false,
    /** Falha só a batida de presença, para provar que ela não trava o resto. */
    var presenceFailure: Throwable? = null
) : TeamUsageRepository {
    val pushed = mutableListOf<TeamIngestPayload>()

    /** Batidas de presença, na ordem em que chegaram. */
    val presenceTouches = mutableListOf<Pair<String, TeamMemberIdentity>>()

    /** Lotes de verdade, sem os envios que só carregam a identidade. */
    val turnBatches: List<TeamIngestPayload>
        get() = pushed.filter { payload -> payload.turns.isNotEmpty() }

    /** Envios de identidade: apelido novo sem turno para acompanhar. */
    val identityPushes: List<TeamIngestPayload>
        get() = pushed.filter { payload -> payload.turns.isEmpty() }

    override suspend fun push(payload: TeamIngestPayload): Result<TeamIngestReceipt> {
        val current = failure
        if (current != null) {
            return Result.failure(current)
        }
        if (rejectIdentityPush && payload.turns.isEmpty()) {
            return Result.failure(IllegalStateException("identidade recusada"))
        }
        pushed += payload
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

    override suspend fun removeMember(accountKey: String, deviceId: String): Result<Unit> {
        return Result.success(Unit)
    }

    override suspend fun touchPresence(
        accountKey: String,
        member: TeamMemberIdentity
    ): Result<TeamPresenceReceipt> {
        val current = presenceFailure
        if (current != null) {
            return Result.failure(current)
        }
        presenceTouches += accountKey to member
        return Result.success(TeamPresenceReceipt())
    }

    override suspend fun checkConnection(): Result<Unit> = Result.success(Unit)
}

private val ACTIVE_SETTINGS = TeamIntegrationSettings(
    enabled = true,
    serverUrl = "http://localhost:3000",
    apiKey = "chave-de-time-com-tamanho-suficiente",
    alias = "edilson",
    deviceId = "device-1",
    participatingProfileIds = setOf(PROFILE_ID)
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

    private fun newService(
        syncState: LocalTeamSyncStateDataSource,
        repository: RecordingTeamRepository,
        settings: TeamIntegrationSettings = ACTIVE_SETTINGS,
        batchSize: Int = 2_000,
        ensureIndexFresh: (suspend () -> Unit)? = null,
        targets: List<TeamSyncTarget> = listOf(
            TeamSyncTarget(profileId = PROFILE_ID, accountKey = ACCOUNT_KEY)
        ),
        settingsProvider: () -> TeamIntegrationSettings = { settings },
        /** `null` reproduz uma instalação anterior à presença. */
        touchTeamPresence: TouchTeamPresenceUseCase? = TouchTeamPresenceUseCase(repository)
    ): TeamSyncService {
        return TeamSyncService(
            syncStateDataSource = syncState,
            pushTeamUsage = PushTeamUsageUseCase(repository),
            settingsProvider = settingsProvider,
            targetsProvider = { targets },
            ensureIndexFresh = ensureIndexFresh,
            touchTeamPresence = touchTeamPresence,
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
