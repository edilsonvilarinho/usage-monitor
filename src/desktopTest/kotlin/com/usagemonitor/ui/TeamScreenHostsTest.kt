package com.usagemonitor.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.CliSessionDetail
import com.usagemonitor.domain.entity.CliSessionRange
import com.usagemonitor.domain.entity.CliSessionSummary
import com.usagemonitor.domain.entity.TeamIngestPayload
import com.usagemonitor.domain.entity.TeamIngestReceipt
import com.usagemonitor.domain.entity.TeamMemberIdentity
import com.usagemonitor.domain.entity.TeamMemberUsage
import com.usagemonitor.domain.entity.TeamPresenceReceipt
import com.usagemonitor.domain.entity.TeamUsageSnapshot
import com.usagemonitor.domain.repository.TeamUsageTrendData
import com.usagemonitor.domain.repository.TeamUsageRepository
import com.usagemonitor.domain.usecase.GetTeamPresenceUseCase
import com.usagemonitor.domain.usecase.GetTeamSessionDetailUseCase
import com.usagemonitor.domain.usecase.GetTeamUsageUseCase
import com.usagemonitor.presentation.ui.PRESENCE_ONLY_ONLINE_TAG
import com.usagemonitor.presentation.ui.TeamPresenceScreen
import com.usagemonitor.presentation.ui.TeamUsageScreen
import com.usagemonitor.presentation.viewmodel.TeamPresenceUiState
import com.usagemonitor.presentation.viewmodel.TeamPresenceViewModel
import com.usagemonitor.presentation.viewmodel.TeamUsageUiState
import com.usagemonitor.presentation.viewmodel.TeamUsageViewModel
import kotlinx.coroutines.Dispatchers
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * As duas telas do time ligadas ao view model de verdade: o estado lido do
 * servidor chega à tela e o controle da tela chega ao view model. Os testes de
 * tela vizinhos exercitam o conteúdo com callbacks soltos; aqui é a fiação.
 */
@OptIn(ExperimentalTestApi::class)
class TeamScreenHostsTest {

    private val now = Instant.parse("2026-10-28T15:00:00Z")
    private val clock = object : Clock {
        override fun now(): Instant = now
    }
    private val fetches = CopyOnWriteArrayList<Long?>()

    private val repository = object : TeamUsageRepository {
        override suspend fun push(payload: TeamIngestPayload): Result<TeamIngestReceipt> = error("sem envio")
        override suspend fun touchPresence(accountKey: String, member: TeamMemberIdentity): Result<TeamPresenceReceipt> = error("sem presença")
        override suspend fun fetch(accountKey: String, cutoffMillis: Long?): Result<TeamUsageSnapshot> {
            fetches += cutoffMillis
            return Result.success(
                TeamUsageSnapshot(
                    members = listOf(
                        TeamMemberUsage(
                            deviceId = "device-1",
                            alias = "edilson",
                            hostName = "DESKTOP-A1",
                            lastSeenAt = now,
                            // Sem sessão o integrante não tem atividade e a tela de consumo não o lista.
                            sessions = listOf(
                                CliSessionSummary(
                                    sessionId = "s1",
                                    filePath = "/tmp/s1.jsonl",
                                    cwd = "/home/dev/usage-monitor",
                                    gitBranch = "main",
                                    firstTs = Instant.parse("2026-10-28T13:00:00Z"),
                                    lastTs = Instant.parse("2026-10-28T14:00:00Z"),
                                    primaryModel = "claude-opus-5",
                                    turnCount = 3,
                                    inputTokens = 1_000L,
                                    costMicros = 250_000L
                                )
                            )
                        )
                    )
                )
            )
        }
        override suspend fun fetchTrend(accountKey: String, days: Int): Result<TeamUsageTrendData?> = Result.success(null)
        override suspend fun fetchSessionDetail(accountKey: String, deviceId: String, sessionId: String): Result<CliSessionDetail?> =
            Result.success(null)
        override suspend fun checkConnection(): Result<Unit> = Result.success(Unit)
    }

    @Test
    fun `the usage screen shows what the server answered and its range chip reaches the view model`() = runDesktopComposeUiTest {
        val viewModel = TeamUsageViewModel(
            getTeamUsage = GetTeamUsageUseCase(repository, clock),
            getTeamSessionDetail = GetTeamSessionDetailUseCase(repository),
            dispatcher = Dispatchers.Default,
            liveIntervalMillis = 3_600_000L,
            clock = clock
        )
        try {
            viewModel.openForAccount("account-1", "empresa@x.com")
            setContent {
                ScreenTestTheme(isDark = true) {
                    TeamUsageScreen(viewModel = viewModel, language = AppLanguage.PT)
                }
            }
            waitUntil(timeoutMillis = 5_000) { viewModel.uiState.value is TeamUsageUiState.Success }
            onNodeWithText("edilson").assertIsDisplayed()

            onNodeWithText("30 dias").performClick()

            waitUntil(timeoutMillis = 5_000) {
                (viewModel.uiState.value as? TeamUsageUiState.Success)?.range == CliSessionRange.LAST_30D
            }
            assertEquals(CliSessionRange.LAST_30D, assertIs<TeamUsageUiState.Success>(viewModel.uiState.value).range)
            // A faixa nova relê o servidor com o corte de 30 dias.
            val cutoff = now.toEpochMilliseconds() - checkNotNull(CliSessionRange.LAST_30D.durationMillis)
            waitUntil(timeoutMillis = 5_000) { cutoff in fetches }
        } finally {
            viewModel.onDestroy()
        }
    }

    @Test
    fun `the presence screen shows the member and its online filter reaches the view model`() = runDesktopComposeUiTest {
        val viewModel = TeamPresenceViewModel(
            getTeamPresence = GetTeamPresenceUseCase(repository, clock = clock),
            dispatcher = Dispatchers.Default,
            liveIntervalMillis = 3_600_000L,
            clock = clock
        )
        try {
            viewModel.openForAccount("account-1", "empresa@x.com")
            setContent {
                ScreenTestTheme(isDark = true) {
                    TeamPresenceScreen(viewModel = viewModel, language = AppLanguage.PT, localDeviceId = null)
                }
            }
            waitUntil(timeoutMillis = 5_000) { viewModel.uiState.value is TeamPresenceUiState.Success }
            onNodeWithText("edilson").assertIsDisplayed()

            onNodeWithTag(PRESENCE_ONLY_ONLINE_TAG).performClick()

            waitUntil(timeoutMillis = 5_000) { (viewModel.uiState.value as? TeamPresenceUiState.Success)?.onlyOnline == true }
        } finally {
            viewModel.onDestroy()
        }
    }
}
