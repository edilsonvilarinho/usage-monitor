package com.usagemonitor

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.Breadcrumb
import com.usagemonitor.domain.entity.BreadcrumbCategory
import com.usagemonitor.domain.entity.CliSessionDetail
import com.usagemonitor.domain.entity.TeamIngestPayload
import com.usagemonitor.domain.entity.TeamIngestReceipt
import com.usagemonitor.domain.entity.TeamKeyVerification
import com.usagemonitor.domain.entity.TeamMemberIdentity
import com.usagemonitor.domain.entity.TeamPresenceReceipt
import com.usagemonitor.domain.entity.TeamUsageSnapshot
import com.usagemonitor.domain.repository.BreadcrumbRecorder
import com.usagemonitor.domain.repository.TeamUsageRepository
import com.usagemonitor.domain.repository.TeamUsageTrendData
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import java.io.File
import java.nio.file.Files
import java.util.UUID
import java.util.prefs.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

internal class RecordingBreadcrumbRecorder : BreadcrumbRecorder {
    val messages = mutableListOf<String>()
    override fun record(category: BreadcrumbCategory, message: String) {
        synchronized(messages) { messages += message }
    }
    override fun read(limit: Int): List<Breadcrumb> = emptyList()
}

/**
 * Pasta de dados, nó de preferências e escopo temporários para as regras das
 * Configurações rodarem sem `AppGraph` nem `AppViewModels`: o [store] é o mesmo
 * tipo que o app monta, só que apontado para cá.
 */
internal class SettingsTestHarness(
    /** Prepara a pasta antes do `store` ler (contas, chaves). */
    prepare: (File) -> Unit = {}
) : AutoCloseable {
    val dir: File = Files.createTempDirectory("settings-actions-").toFile()
    val node: Preferences = Preferences.userRoot().node("com.usagemonitor.tests.${UUID.randomUUID()}")
    val breadcrumbs = RecordingBreadcrumbRecorder()
    private val job = SupervisorJob()
    val scope = CoroutineScope(job + Dispatchers.Default)

    init {
        prepare(dir)
    }

    val store = AppSettingsStore(
        breadcrumbs, null, node, dataFile = { name -> File(dir, name) }, homeDirectory = { dir }, environment = { null }
    )
    val shell = AppShellState(store.settings, initialTargets = emptyList(), languageSelection = store.languageSelection)
    val modal = AppModalState(bugReportOpenAtStart = false)

    var health: Result<Unit> = Result.success(Unit)
    val claims = mutableListOf<Pair<String, String?>>()
    var claimResult: (String) -> Result<TeamKeyVerification> = { Result.success(TeamKeyVerification(authorized = true, claimed = true)) }
    var adminTokenResult: Result<Unit> = Result.success(Unit)
    var httpHandler: MockRequestHandler = { error("o teste não esperava HTTP") }
    var language = AppLanguage.PT

    val teamRepository = object : TeamUsageRepository {
        override suspend fun push(payload: TeamIngestPayload): Result<TeamIngestReceipt> = error("sem envio")
        override suspend fun touchPresence(accountKey: String, member: TeamMemberIdentity): Result<TeamPresenceReceipt> = error("sem presença")
        override suspend fun fetch(accountKey: String, cutoffMillis: Long?): Result<TeamUsageSnapshot> = error("sem leitura")
        override suspend fun fetchTrend(accountKey: String, days: Int): Result<TeamUsageTrendData?> = error("sem tendência")
        override suspend fun fetchSessionDetail(accountKey: String, deviceId: String, sessionId: String): Result<CliSessionDetail?> =
            error("sem detalhe")
        override suspend fun checkConnection(): Result<Unit> = health
    }

    val feedback = AppSettingsFeedback(
        store = store,
        breadcrumbs = breadcrumbs,
        teamUsageRepository = teamRepository,
        claimTeamKey = { accountKey, email ->
            synchronized(claims) { claims += accountKey to email }
            claimResult(accountKey)
        },
        validateAdminTokenWithServer = { adminTokenResult },
        scope = scope,
        language = { language },
        profileLabel = { profileId -> if (profileId == "default") "Pessoal" else null },
        httpClientFactory = {
            HttpClient(MockEngine { request -> httpHandler(this, request) }) {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            }
        }
    )

    val refreshed = mutableListOf<ApiSource>()
    var teamSyncs = 0
    var teamKeysOpened = 0
    val targets = object : SettingsActionTargets {
        override fun refreshSource(api: ApiSource) {
            refreshed += api
        }
        override fun requestTeamSync() {
            teamSyncs += 1
        }
        override fun openTeamKeys() {
            teamKeysOpened += 1
        }
    }

    /** Espera o que as ações lançaram no escopo (testes de conexão, passagem ao relatório). */
    fun settle() {
        runBlocking {
            job.children.toList().forEach { child -> child.join() }
        }
    }

    override fun close() {
        job.cancel()
        node.removeNode()
        dir.deleteRecursively()
    }

    companion object {
        /** A conta padrão (`~/.claude` + `~/.claude.json`) com `accountUuid`. */
        fun writeDefaultAccount(home: File, accountId: String = "uuid-1", email: String = "me@example.com") {
            File(home, ".claude").mkdirs()
            File(home, ".claude/.credentials.json").writeText("{\"claudeAiOauth\":{\"accessToken\":\"test-token\"}}")
            File(home, ".claude.json").writeText(
                "{\"oauthAccount\":{\"accountUuid\":\"$accountId\",\"emailAddress\":\"$email\"}}"
            )
        }
    }
}
