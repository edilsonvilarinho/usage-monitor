package com.usagemonitor.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageHistoryReport
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.CliSessionRange
import com.usagemonitor.domain.entity.HistoryRange
import com.usagemonitor.domain.entity.UsageAccountContext
import com.usagemonitor.domain.entity.UsageAccountKey
import com.usagemonitor.domain.repository.UsageHistoryRepository
import com.usagemonitor.domain.usecase.ComputeCliSessionAnalyticsUseCase
import com.usagemonitor.domain.usecase.CliSessionDetailResult
import com.usagemonitor.domain.usecase.GetUsageHistoryUseCase
import com.usagemonitor.presentation.ui.CliSessionsContent
import com.usagemonitor.presentation.ui.HistoryScreen
import com.usagemonitor.presentation.ui.TeamPresenceContent
import com.usagemonitor.presentation.ui.TeamUsageContent
import com.usagemonitor.presentation.ui.components.FooterBar
import com.usagemonitor.presentation.ui.components.ResponsiveDashboardCardGrid
import com.usagemonitor.presentation.ui.components.SettingsDialogContent
import com.usagemonitor.presentation.ui.components.TeamIntegrationSection
import com.usagemonitor.presentation.ui.theme.AppSpacing
import com.usagemonitor.presentation.ui.theme.AppTheme
import com.usagemonitor.presentation.viewmodel.CliSessionDetailUiState
import com.usagemonitor.presentation.viewmodel.CliSessionsUiState
import com.usagemonitor.presentation.viewmodel.HistoryUiState
import com.usagemonitor.presentation.viewmodel.HistoryViewModel
import com.usagemonitor.presentation.viewmodel.TeamPresenceUiState
import com.usagemonitor.presentation.viewmodel.TeamUsageUiState
import com.usagemonitor.presentation.viewmodel.TeamUsageView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.Instant
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.time.Duration.Companion.seconds
import com.usagemonitor.domain.entity.AppTheme as AppThemePreference

/**
 * Gera as capturas de tela do README a partir dos composables reais, com os
 * dados sintéticos de [ScreenshotFixtures].
 *
 * A alternativa — fotografar a app rodando — colocaria e-mail, organização e
 * caminho de projeto reais em imagem publicada num repositório público. Aqui não
 * há o que censurar: o dado nunca existe.
 *
 * Rodar com `gradlew.bat generateScreenshots`.
 */

/** Fator de supersampling: 2 deixa o texto legível no README em telas HiDPI. */
private const val SCALE = 2

/**
 * Aquecimento antes do frame publicado.
 *
 * `ApiUsageCard` só aparece depois de `delay(index * AppMotion.stagger)` seguido de
 * `AnimatedVisibility`, e são dois relógios diferentes: o `delay` roda em
 * `Dispatchers.Unconfined`, que espera **tempo real**, enquanto a animação
 * consome o `nanoTime` passado a `render`. Avançar só um dos dois deixa a cena
 * em branco — foi o que aconteceu na primeira versão deste gerador.
 *
 * Por isso o laço dorme de verdade entre frames e ainda adianta o relógio da
 * cena. Um segundo real cobre com folga o maior `delay` da grade (180ms com o
 * stagger de 60), e dois segundos de tempo de cena encerram qualquer fade de
 * entrada. Encurtar a duração do motion é seguro aqui; **alongar** o stagger
 * acima de um segundo de acumulado exige subir [WARMUP_FRAMES].
 */
private const val WARMUP_FRAMES = 20
private const val WARMUP_SLEEP_MILLIS = 50L
private const val FRAME_STEP_NANOS = 100_000_000L

fun main(args: Array<String>) {
    val outputDir = File(args.firstOrNull() ?: "img")
    outputDir.mkdirs()

    val generator = ScreenshotGenerator(outputDir)

    generator.dashboard()
    generator.history()
    generator.settings()
    generator.settingsTeam()
    generator.cliSessions()
    generator.cliBreakdown()
    generator.cliSessionDetail()
    generator.teamUsage()
    generator.teamTrend()
    generator.presence(isDark = true)
    generator.presence(isDark = false)
    generator.presenceAccounts()

    println("Capturas geradas em ${outputDir.absolutePath}")
}

private class ScreenshotGenerator(private val outputDir: File) {

    @OptIn(ExperimentalComposeUiApi::class)
    fun capture(
        name: String,
        widthDp: Int,
        heightDp: Int,
        /** O tema claro tem paleta de acentos própria; capturá-lo é como se confere. */
        isDark: Boolean = true,
        content: @Composable () -> Unit
    ) {
        val scene = ImageComposeScene(
            width = widthDp * SCALE,
            height = heightDp * SCALE,
            density = Density(SCALE.toFloat())
        )

        try {
            scene.setContent {
                AppTheme(isDark = isDark) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        content()
                    }
                }
            }

            // A primeira passada dispara os efeitos; o laço deixa o tempo real e
            // o da cena avançarem juntos até tudo estar assentado.
            scene.render(0L)
            var sceneNanos = 0L
            repeat(WARMUP_FRAMES) {
                Thread.sleep(WARMUP_SLEEP_MILLIS)
                sceneNanos += FRAME_STEP_NANOS
                scene.render(sceneNanos)
            }

            val image = scene.render(sceneNanos + FRAME_STEP_NANOS)
            val data = image.encodeToData(EncodedImageFormat.PNG)
                ?: error("Falha ao codificar $name em PNG.")

            val file = File(outputDir, "$name.png")
            file.writeBytes(data.bytes)
            println("  ${file.name} (${widthDp * SCALE}x${heightDp * SCALE})")
        } finally {
            scene.close()
        }
    }

    fun dashboard() = capture("dashboard", widthDp = 1_040, heightDp = 690) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            ) {
                ResponsiveDashboardCardGrid(
                    items = ScreenshotFixtures.dashboardStats,
                    refreshingTargets = emptySet(),
                    minimizedCards = emptySet(),
                    riskSummaries = ScreenshotFixtures.dashboardRiskSummaries,
                    language = AppLanguage.PT,
                    onRefreshCard = {},
                    onMoveCardToIndex = { _, _ -> },
                    onToggleCardMinimized = {},
                    onOpenHistoryCard = { _, _ -> },
                    teamEnabledProfileIds = setOf("default"),
                    now = ScreenshotFixtures.NOW,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            FooterBar(
                appVersion = APP_VERSION,
                language = AppLanguage.PT,
                nextRefreshAt = ScreenshotFixtures.NOW.plusSeconds(437),
                onRefresh = {},
                onOpenSettings = {},
                nowProvider = { ScreenshotFixtures.NOW },
                // O contador só precisa do valor inicial; o laço de um segundo
                // por tique só produziria frames diferentes a cada execução.
                countdownUpdatesEnabled = false
            )
        }
    }

    fun history() = capture("history", widthDp = 880, heightDp = 840) {
        HistoryScreen(
            viewModel = fixedHistoryViewModel(),
            language = AppLanguage.PT,
            onBack = {},
            focusedSource = ApiSource.ANTHROPIC
        )
    }

    // O diálogo é dividido em abas, então a captura mostra a fileira de abas e a
    // primeira delas inteira. Altura maior sobraria como faixa vazia embaixo.
    fun settings() = capture("settings", widthDp = 640, heightDp = 460) {
        SettingsDialogContent(
            currentTheme = AppThemePreference.DARK,
            currentLanguage = AppLanguage.PT,
            enabledApis = ScreenshotFixtures.enabledApis,
            autoStartEnabled = true,
            alwaysOnTopEnabled = false,
            windowOpacityPercent = 92,
            onThemeToggle = {},
            onLanguageChange = {},
            onAutoStartChange = {},
            onAlwaysOnTopChange = {},
            onApiToggle = { _, _ -> },
            anthropicProfiles = ScreenshotFixtures.anthropicProfiles
        )
    }

    /**
     * A seção de time isolada, e não o diálogo inteiro.
     *
     * `SettingsDialogContent` rola internamente e a cena nasce no topo: pedir o
     * diálogo aqui devolveria de novo a captura de [settings], com a seção de
     * time fora do quadro.
     */
    fun settingsTeam() = capture("settings-team", widthDp = 620, heightDp = 610) {
        // Sem envoltório: a seção ja monta o próprio painel com borda, e o
        // `Surface` com alpha que existia aqui era um quinto degrau de
        // superfície — inventado pela captura, ausente do app.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(AppSpacing.lg)
        ) {
            run {
                TeamIntegrationSection(
                    settings = ScreenshotFixtures.teamSettings,
                    language = AppLanguage.PT,
                    profiles = ScreenshotFixtures.anthropicProfiles,
                    connection = ScreenshotFixtures.teamConnection,
                    onEnabledChange = {},
                    onServerUrlChange = {},
                    onApiKeyChange = {},
                    onAliasChange = {},
                    onProfileParticipationChange = { _, _ -> },
                    onTestConnection = {},
                    modifier = Modifier
                )
            }
        }
    }

    fun cliSessions() = capture("cli-sessions", widthDp = 1_060, heightDp = 440) {
        CliSessionsContent(
            state = CliSessionsUiState.Success(
                sessions = ScreenshotFixtures.cliSessions,
                range = CliSessionRange.LAST_5H,
                rangeEndsAt = ScreenshotFixtures.NOW.plusSeconds(2 * 3_600L),
                rangeAnchored = true,
                profileLabel = "Padrão",
                lastChangedAt = ScreenshotFixtures.NOW
            ),
            language = AppLanguage.PT,
            onSelectRange = {},
            onOpenSession = {},
            onCloseDetail = {}
        )
    }

    fun cliBreakdown() = capture("cli-breakdown", widthDp = 960, heightDp = 760) {
        CliSessionsContent(
            state = CliSessionsUiState.Success(
                sessions = ScreenshotFixtures.cliSessions,
                view = com.usagemonitor.presentation.viewmodel.CliSessionsView.BREAKDOWN,
                breakdown = ScreenshotFixtures.cliBreakdown,
                range = CliSessionRange.LAST_5H,
                rangeEndsAt = ScreenshotFixtures.NOW.plusSeconds(2 * 3_600L),
                rangeAnchored = true,
                profileLabel = "Padrão",
                lastChangedAt = ScreenshotFixtures.NOW
            ),
            language = AppLanguage.PT,
            onSelectRange = {},
            onOpenSession = {},
            onCloseDetail = {}
        )
    }

    fun cliSessionDetail() = capture("cli-session-detail", widthDp = 1_060, heightDp = 980) {
        val detail = ScreenshotFixtures.saturatedSessionDetail

        CliSessionsContent(
            state = CliSessionsUiState.Success(
                sessions = ScreenshotFixtures.cliSessions,
                range = CliSessionRange.LAST_5H,
                profileLabel = "Padrão",
                lastChangedAt = ScreenshotFixtures.NOW,
                detail = CliSessionDetailUiState.Ready(
                    sessionId = detail.summary.sessionId,
                    result = CliSessionDetailResult(
                        detail = detail,
                        analytics = ComputeCliSessionAnalyticsUseCase().invoke(detail)
                    )
                ),
                advancedExpanded = true
            ),
            language = AppLanguage.PT,
            onSelectRange = {},
            onOpenSession = {},
            onCloseDetail = {}
        )
    }

    fun teamTrend() = capture("team-trend", widthDp = 960, heightDp = 560) {
        TeamUsageContent(
            state = TeamUsageUiState.Success(
                members = ScreenshotFixtures.teamMembers,
                trend = ScreenshotFixtures.teamTrend,
                view = TeamUsageView.TREND,
                range = CliSessionRange.LAST_5H,
                rangeEndsAt = ScreenshotFixtures.NOW.plusSeconds(2 * 3_600L),
                rangeAnchored = true,
                accountLabel = "dev@example.com — Example Org",
                lastChangedAt = ScreenshotFixtures.NOW
            ),
            language = AppLanguage.PT,
            onSelectRange = {},
            onToggleMember = {}
        )
    }

    fun teamUsage() = capture("team-usage", widthDp = 1_060, heightDp = 600) {
        TeamUsageContent(
            state = TeamUsageUiState.Success(
                members = ScreenshotFixtures.teamMembers,
                range = CliSessionRange.LAST_5H,
                rangeEndsAt = ScreenshotFixtures.NOW.plusSeconds(2 * 3_600L),
                rangeAnchored = true,
                accountLabel = "dev@example.com — Example Org",
                expandedMemberKeys = setOf("device-a1"),
                lastChangedAt = ScreenshotFixtures.NOW
            ),
            language = AppLanguage.PT,
            onSelectRange = {},
            onToggleMember = {}
        )
    }

    /**
     * Presença de uma conta, nos dois temas.
     *
     * A largura é a mesma da janela real (960dp) — capturar mais largo esconderia
     * justamente a quebra de coluna que se quer conferir. O `canManage` aqui é
     * inerte: `TeamPresenceContent` só libera os botões destrutivos na visão
     * global, que é o que [presenceAccounts] captura.
     */
    fun presence(isDark: Boolean) {
        val name = if (isDark) "presence" else "presence-light"
        capture(name, widthDp = 960, heightDp = 460, isDark = isDark) {
            TeamPresenceContent(
                state = TeamPresenceUiState.Success(
                    entries = ScreenshotFixtures.teamPresence,
                    accountLabel = "dev@example.com — Example Org",
                    lastChangedAt = ScreenshotFixtures.NOW
                ),
                language = AppLanguage.PT,
                localDeviceId = ScreenshotFixtures.LOCAL_DEVICE_ID,
                canManage = true
            )
        }
    }

    /**
     * Presença na visão global do administrador.
     *
     * É a captura que prova a faixa de conta — superfície, marcador, a palavra
     * "Conta" e divisória — e a coluna de ação à direita, que é onde o botão de
     * apagar conta aparecia solto numa linha própria. Largura mínima da janela
     * (940dp): capturar mais largo esconderia o pior caso do orçamento de colunas.
     */
    fun presenceAccounts() {
        capture("presence-accounts", widthDp = 940, heightDp = 460, isDark = true) {
            TeamPresenceContent(
                state = TeamPresenceUiState.Success(
                    entries = ScreenshotFixtures.teamPresenceAccounts,
                    isAdminOverview = true,
                    expandedAccountKeys = setOf("account-primary"),
                    lastChangedAt = ScreenshotFixtures.NOW
                ),
                language = AppLanguage.PT,
                localDeviceId = ScreenshotFixtures.LOCAL_DEVICE_ID,
                canManage = true
            )
        }
    }
}

/** Versão exibida no rodapé; a real vem do build e mudaria a imagem a cada release. */
internal const val APP_VERSION = "27.0.0"

/**
 * `HistoryScreen` recebe o ViewModel, não o estado.
 *
 * O repositório falso responde na hora, mas a carga roda em `Dispatchers.Default`:
 * sem esperar o `Success`, a captura pegaria o spinner. Daí o bloqueio até o
 * estado chegar, com prazo para não travar o build se algo mudar.
 */
internal fun fixedHistoryViewModel(): HistoryViewModel {
    val repository = object : UsageHistoryRepository {
        override suspend fun recordSnapshot(stats: ApiUsageStats, capturedAt: Instant) = Unit

        override suspend fun listAccounts(source: ApiSource): List<UsageAccountContext> {
            return ScreenshotFixtures.historyAccounts
        }

        override suspend fun getHistoryReport(
            source: ApiSource,
            range: HistoryRange,
            now: Instant
        ): ApiUsageHistoryReport = ScreenshotFixtures.historyReport

        override suspend fun getHistoryReport(
            source: ApiSource,
            accountKey: UsageAccountKey?,
            range: HistoryRange,
            now: Instant
        ): ApiUsageHistoryReport = ScreenshotFixtures.historyReport
    }

    val viewModel = HistoryViewModel(
        getUsageHistory = GetUsageHistoryUseCase(repository),
        enabledApis = MutableStateFlow(setOf(ApiSource.ANTHROPIC, ApiSource.CODEX, ApiSource.DEEPSEEK))
    )

    runBlocking {
        withTimeout(10.seconds) {
            viewModel.uiState.first { state -> state is HistoryUiState.Success }
        }
    }

    return viewModel
}

private fun Instant.plusSeconds(seconds: Long): Instant =
    Instant.fromEpochMilliseconds(toEpochMilliseconds() + seconds * 1_000L)
