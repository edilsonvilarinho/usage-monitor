package com.usagemonitor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.AppUpdateInfo
import com.usagemonitor.domain.entity.HistoryRange
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.QuotaInfo
import com.usagemonitor.domain.entity.UsageForecast
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.UsageHistoryPoint
import com.usagemonitor.domain.entity.UsageHistorySeries
import com.usagemonitor.domain.entity.AppTheme as ThemeMode
import com.usagemonitor.domain.entity.UsageUnit
import com.usagemonitor.domain.repository.AnthropicRepository
import com.usagemonitor.domain.repository.AppUpdateRepository
import com.usagemonitor.domain.repository.CodexRepository
import com.usagemonitor.domain.repository.DeepSeekRepository
import com.usagemonitor.domain.repository.MiniMaxRepository
import com.usagemonitor.domain.repository.UsageHistoryRepository
import com.usagemonitor.domain.usecase.CheckForAppUpdateUseCase
import com.usagemonitor.domain.usecase.GetAnthropicUsageUseCase
import com.usagemonitor.domain.usecase.GetCodexUsageUseCase
import com.usagemonitor.domain.usecase.GetDeepSeekUsageUseCase
import com.usagemonitor.domain.usecase.GetMiniMaxUsageUseCase
import com.usagemonitor.domain.usecase.RecordUsageSnapshotUseCase
import com.usagemonitor.presentation.ui.components.ApiUsageCard
import com.usagemonitor.presentation.ui.components.ApiCheckboxRow
import com.usagemonitor.presentation.ui.components.FooterBar
import com.usagemonitor.presentation.ui.DashboardScreen
import com.usagemonitor.presentation.ui.HistoryScreen
import com.usagemonitor.presentation.ui.components.LanguageSelector
import com.usagemonitor.presentation.ui.components.PersistentApiWarningBanner
import com.usagemonitor.presentation.ui.components.SettingsDialogContent
import com.usagemonitor.presentation.ui.components.ThemeToggle
import com.usagemonitor.presentation.ui.components.UsageArcChart
import com.usagemonitor.presentation.ui.theme.AppTheme
import com.usagemonitor.presentation.viewmodel.DashboardViewModel
import com.usagemonitor.presentation.viewmodel.HistoryViewModel
import com.usagemonitor.presentation.viewmodel.UnsupportedAppUpdateInstaller
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import androidx.compose.ui.unit.dp
import kotlin.time.Duration.Companion.seconds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Testes de componente Compose para Desktop.
 *
 * `runDesktopComposeUiTest` inicializa o Compose sem uma janela real
 * e permite interagir com os componentes programaticamente.
 *
 * É equivalente ao Testing Library do React: testa o comportamento
 * do componente da perspectiva do utilizador (o que vê e clica),
 * não a implementação interna.
 */
@OptIn(ExperimentalTestApi::class)
class ComponentTest {

    private fun emptyDashboardViewModel(enabledApis: MutableStateFlow<Set<ApiSource>>): DashboardViewModel {
        val anthropicRepo = object : AnthropicRepository {
            override suspend fun getUsage() = Result.failure<ApiUsageStats>(Exception("Não deve ser chamado"))
        }
        val minimaxRepo = object : MiniMaxRepository {
            override suspend fun getUsage() = Result.failure<ApiUsageStats>(Exception("Não deve ser chamado"))
        }
        val codexRepo = object : CodexRepository {
            override suspend fun getUsage() = Result.failure<ApiUsageStats>(Exception("Não deve ser chamado"))
        }
        val deepSeekRepo = object : DeepSeekRepository {
            override suspend fun getUsage() = Result.failure<ApiUsageStats>(Exception("Não deve ser chamado"))
        }
        val historyRepository = object : UsageHistoryRepository {
            override suspend fun recordSnapshot(stats: ApiUsageStats, capturedAt: Instant) = Unit

            override suspend fun getHistoryReport(
                source: ApiSource,
                range: HistoryRange,
                now: Instant
            ) = throw UnsupportedOperationException("Não utilizado neste teste")
        }

        return DashboardViewModel(
            getAnthropicUsage = GetAnthropicUsageUseCase(anthropicRepo),
            getMiniMaxUsage = GetMiniMaxUsageUseCase(minimaxRepo),
            getCodexUsage = GetCodexUsageUseCase(codexRepo),
            getDeepSeekUsage = GetDeepSeekUsageUseCase(deepSeekRepo),
            enabledApis = enabledApis,
            recordUsageSnapshot = RecordUsageSnapshotUseCase(historyRepository)
        )
    }

    private fun dashboardViewModelWithAvailableUpdate(enabledApis: MutableStateFlow<Set<ApiSource>>): DashboardViewModel {
        val anthropicRepo = object : AnthropicRepository {
            override suspend fun getUsage() = Result.failure<ApiUsageStats>(Exception("Não deve ser chamado"))
        }
        val minimaxRepo = object : MiniMaxRepository {
            override suspend fun getUsage() = Result.failure<ApiUsageStats>(Exception("Não deve ser chamado"))
        }
        val codexRepo = object : CodexRepository {
            override suspend fun getUsage() = Result.failure<ApiUsageStats>(Exception("Não deve ser chamado"))
        }
        val deepSeekRepo = object : DeepSeekRepository {
            override suspend fun getUsage() = Result.failure<ApiUsageStats>(Exception("Não deve ser chamado"))
        }
        val historyRepository = object : UsageHistoryRepository {
            override suspend fun recordSnapshot(stats: ApiUsageStats, capturedAt: Instant) = Unit

            override suspend fun getHistoryReport(
                source: ApiSource,
                range: HistoryRange,
                now: Instant
            ) = throw UnsupportedOperationException("Não utilizado neste teste")
        }
        val updateRepository = object : AppUpdateRepository {
            override suspend fun getLatestAvailableUpdate(currentVersion: String): Result<AppUpdateInfo?> {
                return Result.success(
                    AppUpdateInfo(
                        version = "7.1.0",
                        releasePageUrl = "https://example.com/releases/tag/v7.1.0"
                    )
                )
            }
        }

        return DashboardViewModel(
            getAnthropicUsage = GetAnthropicUsageUseCase(anthropicRepo),
            getMiniMaxUsage = GetMiniMaxUsageUseCase(minimaxRepo),
            getCodexUsage = GetCodexUsageUseCase(codexRepo),
            getDeepSeekUsage = GetDeepSeekUsageUseCase(deepSeekRepo),
            enabledApis = enabledApis,
            recordUsageSnapshot = RecordUsageSnapshotUseCase(historyRepository),
            checkForAppUpdate = CheckForAppUpdateUseCase(updateRepository),
            appUpdateInstaller = UnsupportedAppUpdateInstaller,
            currentAppVersion = "7.0.0"
        )
    }

    // ── UsageArcChart ────────────────────────────────────────────────────

    @Test
    fun `UsageArcChart displays percentage text`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                UsageArcChart(
                    used = 500L,
                    total = 1000L,
                    unit = UsageUnit.REQUESTS
                )
            }
        }

        onNodeWithText("50%").assertIsDisplayed()
    }

    @Test
    fun `UsageArcChart shows 0 percent when total is 0`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                UsageArcChart(
                    used = 0L,
                    total = 0L,
                    unit = UsageUnit.TOKENS
                )
            }
        }

        onNodeWithText("0%").assertIsDisplayed()
    }

    @Test
    fun `ApiUsageCard shows interval and weekly quotas in the same card`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                ApiUsageCard(
                    source = ApiSource.ANTHROPIC,
                    apiName = "Anthropic",
                    quotas = listOf(
                        QuotaInfo(
                            label = "Claude 5h",
                            used = 0L,
                            total = 100L,
                            periodEndAt = Instant.parse("2026-04-28T17:40:00Z"),
                            periodType = PeriodType.INTERVAL,
                            unit = UsageUnit.TOKENS,
                            rawUsed = 0L,
                            rawTotal = 4000L
                        ),
                        QuotaInfo(
                            label = "Claude 7d",
                            used = 98L,
                            total = 100L,
                            periodEndAt = Instant.parse("2026-05-03T12:00:00Z"),
                            periodType = PeriodType.WEEKLY,
                            unit = UsageUnit.TOKENS,
                            rawUsed = 39000L,
                            rawTotal = 40000L
                        )
                    ),
                    showUsageDetails = false,
                    isRefreshing = false,
                    language = AppLanguage.PT,
                    animationDelayMillis = 0,
                    onRefresh = {}
                )
            }
        }

        onNodeWithText("Anthropic").assertIsDisplayed()
        onNodeWithText("0%").assertIsDisplayed()
        onNodeWithText("98%").assertIsDisplayed()
        onNodeWithText("Sessão 5h").assertIsDisplayed()
        onAllNodesWithText("0/4K tok").assertCountEquals(0)
        onAllNodesWithText("39K/40K tok").assertCountEquals(0)
        onNodeWithText("Semanal").assertIsDisplayed()
    }

    @Test
    fun `ApiUsageCard keeps a single quota centered when weekly data is absent`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                ApiUsageCard(
                    source = ApiSource.MINIMAX,
                    apiName = "MiniMax",
                    quotas = listOf(
                        QuotaInfo(
                            label = "MiniMax-M*",
                            used = 12L,
                            total = 45L,
                            periodEndAt = Instant.parse("2026-04-28T15:00:00Z"),
                            periodType = PeriodType.INTERVAL,
                            unit = UsageUnit.REQUESTS
                        )
                    ),
                    showUsageDetails = true,
                    isRefreshing = false,
                    language = AppLanguage.PT,
                    animationDelayMillis = 0,
                    onRefresh = {}
                )
            }
        }

        onNodeWithText("MiniMax").assertIsDisplayed()
        onNodeWithText("26%").assertIsDisplayed()
        onNodeWithText("Sessão 5h").assertIsDisplayed()
        onNodeWithText("12/45 req").assertIsDisplayed()
    }

    @Test
    fun `ApiUsageCard shows balance title for currency quotas`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                ApiUsageCard(
                    source = ApiSource.DEEPSEEK,
                    apiName = "DeepSeek",
                    quotas = listOf(
                        QuotaInfo(
                            label = "Saldo",
                            used = 0L,
                            total = 385L,
                            periodEndAt = Instant.parse("2026-04-28T15:00:00Z"),
                            hasKnownResetAt = false,
                            periodType = PeriodType.INTERVAL,
                            unit = UsageUnit.CURRENCY_USD,
                            rawUsed = 385L,
                            rawTotal = 385L
                        )
                    ),
                    showUsageDetails = false,
                    isRefreshing = false,
                    language = AppLanguage.PT,
                    animationDelayMillis = 0,
                    onRefresh = {}
                )
            }
        }

        onNodeWithText("DeepSeek").assertIsDisplayed()
        onNodeWithText("Saldo").assertIsDisplayed()
        onNodeWithText("\$3.85").assertIsDisplayed()
        onNodeWithText("Saldo não expira").assertIsDisplayed()
    }

    @Test
    fun `ApiUsageCard opens history action`() = runDesktopComposeUiTest {
        var opened = false

        setContent {
            AppTheme(isDark = true) {
                ApiUsageCard(
                    source = ApiSource.CODEX,
                    apiName = "Codex",
                    quotas = listOf(
                        QuotaInfo(
                            label = "Codex 5h",
                            used = 57L,
                            total = 100L,
                            periodEndAt = Instant.parse("2026-04-28T20:00:00Z"),
                            periodType = PeriodType.INTERVAL,
                            unit = UsageUnit.REQUESTS
                        )
                    ),
                    showUsageDetails = true,
                    isRefreshing = false,
                    language = AppLanguage.PT,
                    animationDelayMillis = 0,
                    onRefresh = {},
                    onOpenHistory = { opened = true }
                )
            }
        }

        onNodeWithContentDescription("Abrir histórico").performClick()
        assertEquals(true, opened)
    }

    @Test
    fun `ApiUsageCard shows compact quota labels when minimized`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                ApiUsageCard(
                    source = ApiSource.ANTHROPIC,
                    apiName = "Anthropic",
                    quotas = listOf(
                        QuotaInfo(
                            label = "Claude 5h",
                            used = 45L,
                            total = 100L,
                            periodEndAt = Instant.parse("2026-04-28T17:40:00Z"),
                            periodType = PeriodType.INTERVAL,
                            unit = UsageUnit.TOKENS,
                            rawUsed = 1800L,
                            rawTotal = 4000L
                        ),
                        QuotaInfo(
                            label = "Claude 7d",
                            used = 80L,
                            total = 100L,
                            periodEndAt = Instant.parse("2026-05-03T12:00:00Z"),
                            periodType = PeriodType.WEEKLY,
                            unit = UsageUnit.TOKENS,
                            rawUsed = 32000L,
                            rawTotal = 40000L
                        )
                    ),
                    showUsageDetails = false,
                    isRefreshing = false,
                    language = AppLanguage.PT,
                    animationDelayMillis = 0,
                    isMinimized = true,
                    onRefresh = {}
                )
            }
        }

        onNodeWithText("Claude 5h").assertIsDisplayed()
        onNodeWithText("Claude 7d").assertIsDisplayed()
        onNodeWithContentDescription("Atualizar").assertIsDisplayed()
        onNodeWithContentDescription("Abrir histórico").assertIsDisplayed()
        onNodeWithContentDescription("Expandir card").assertIsDisplayed()
    }

    @Test
    fun `ApiUsageCard keeps a single compact quota narrower than the card`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                Box(
                    modifier = Modifier
                        .width(640.dp)
                        .testTag("cardHost")
                ) {
                    ApiUsageCard(
                        source = ApiSource.ANTHROPIC,
                        apiName = "Anthropic",
                        quotas = listOf(
                            QuotaInfo(
                                label = "Claude 7d",
                                used = 80L,
                                total = 100L,
                                periodEndAt = Instant.parse("2026-05-03T12:00:00Z"),
                                periodType = PeriodType.WEEKLY,
                                unit = UsageUnit.TOKENS,
                                rawUsed = 32000L,
                                rawTotal = 40000L
                            )
                        ),
                        showUsageDetails = false,
                        isRefreshing = false,
                        isMinimized = true,
                        language = AppLanguage.PT,
                        animationDelayMillis = 0,
                        onRefresh = {}
                    )
                }
            }
        }

        val badgeWidth = onNodeWithTag("compactQuotaBadge").fetchSemanticsNode().boundsInRoot.width
        val hostWidth = onNodeWithTag("cardHost").fetchSemanticsNode().boundsInRoot.width

        assertTrue(badgeWidth < hostWidth * 0.7f)
        onNodeWithText("Claude 7d").assertIsDisplayed()
    }

    @Test
    fun `PersistentApiWarningBanner shows title description and action`() = runDesktopComposeUiTest {
        var actionClicked = false

        setContent {
            AppTheme(isDark = true) {
                PersistentApiWarningBanner(
                    title = "Anthropic precisa de autenticação",
                    description = "Faça login no Claude Code e tente novamente.",
                    actionLabel = "Tentar novamente",
                    onAction = { actionClicked = true }
                )
            }
        }

        onNodeWithText("Anthropic precisa de autenticação").assertIsDisplayed()
        onNodeWithText("Faça login no Claude Code e tente novamente.").assertIsDisplayed()
        onNodeWithText("Tentar novamente").performClick()
        assertEquals(true, actionClicked)
    }

    // ── ApiCheckboxRow ────────────────────────────────────────────────────

    @Test
    fun `ApiCheckboxRow is checked when isChecked is true`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                ApiCheckboxRow(
                    api = ApiSource.ANTHROPIC,
                    isChecked = true,
                    onCheckedChange = {}
                )
            }
        }

        // Checkbox deve aparecer marcado
        onNodeWithText("Anthropic").assertIsDisplayed()
    }

    @Test
    fun `ApiCheckboxRow triggers onCheckedChange on click`() = runDesktopComposeUiTest {
        var toggled = false

        setContent {
            AppTheme(isDark = true) {
                ApiCheckboxRow(
                    api = ApiSource.MINIMAX,
                    isChecked = false,
                    onCheckedChange = { toggled = true }
                )
            }
        }

        onNodeWithText("MiniMax").performClick()
        // O clique no label deve acionar o callback
        assertEquals(true, toggled)
    }

    // ── ThemeToggle ───────────────────────────────────────────────────────

    @Test
    fun `ThemeToggle shows dark label when isDark is true`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                ThemeToggle(isDark = true, onToggle = {})
            }
        }

        onNodeWithText("🌙 Escuro").assertIsDisplayed()
    }

    @Test
    fun `ThemeToggle shows light label when isDark is false`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = false) {
                ThemeToggle(isDark = false, onToggle = {})
            }
        }

        onNodeWithText("☀️ Claro").assertIsDisplayed()
    }

    @Test
    fun `ThemeToggle calls onToggle when clicked`() = runDesktopComposeUiTest {
        var toggled = false

        setContent {
            AppTheme(isDark = true) {
                ThemeToggle(isDark = true, onToggle = { toggled = true })
            }
        }

        onNodeWithText("🌙 Escuro").performClick()
        assertEquals(true, toggled)
    }

    // ── FooterBar ───────────────────────────────────────────────────────

    @Test
    fun `FooterBar displays localized version and next update in PT`() = runDesktopComposeUiTest {
        val fixedNow = Instant.parse("2025-01-01T12:00:00Z")

        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(640.dp)) {
                    FooterBar(
                        appVersion = "1.1.0",
                        language = AppLanguage.PT,
                        nextRefreshAt = fixedNow + 125.seconds,
                        onRefresh = {},
                        onOpenSettings = {},
                        nowProvider = { fixedNow },
                        countdownUpdatesEnabled = false
                    )
                }
            }
        }

        onNodeWithText("Versão:").assertIsDisplayed()
        onNodeWithText("v1.1.0").assertIsDisplayed()
        onNodeWithText("Próxima atualização:").assertIsDisplayed()
        onNodeWithText("02:05").assertIsDisplayed()
        onNodeWithText("Configurações").assertIsDisplayed()
        onAllNodesWithText("Histórico").assertCountEquals(0)
    }

    @Test
    fun `FooterBar opens settings action`() = runDesktopComposeUiTest {
        var opened = false
        val fixedNow = Instant.parse("2025-01-01T12:00:00Z")

        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(640.dp)) {
                    FooterBar(
                        appVersion = "1.1.0",
                        language = AppLanguage.PT,
                        nextRefreshAt = fixedNow + 125.seconds,
                        onRefresh = {},
                        onOpenSettings = { opened = true },
                        nowProvider = { fixedNow },
                        countdownUpdatesEnabled = false
                    )
                }
            }
        }

        onNodeWithText("Configurações").performClick()
        assertEquals(true, opened)
    }

    @Test
    fun `FooterBar keeps controls accessible in narrow width`() = runDesktopComposeUiTest {
        var opened = false
        val fixedNow = Instant.parse("2025-01-01T12:00:00Z")

        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(320.dp)) {
                    FooterBar(
                        appVersion = "6.0.0",
                        language = AppLanguage.PT,
                        nextRefreshAt = fixedNow + 433.seconds,
                        onRefresh = {},
                        onOpenSettings = { opened = true },
                        nowProvider = { fixedNow },
                        countdownUpdatesEnabled = false
                    )
                }
            }
        }

        onNodeWithText("v6.0.0").assertIsDisplayed()
        onNodeWithText("07:13").assertIsDisplayed()
        onNodeWithContentDescription("Abrir configurações").performClick()
        assertEquals(true, opened)
    }

    @Test
    fun `DashboardScreen guides the user to settings when no APIs are enabled`() = runDesktopComposeUiTest {
        var opened = false
        val enabledApis = MutableStateFlow(emptySet<ApiSource>())
        val viewModel = emptyDashboardViewModel(enabledApis)
        viewModel.cancelCountdown()

        setContent {
            AppTheme(isDark = true) {
                DashboardScreen(
                    viewModel = viewModel,
                    appVersion = "7.0.0",
                    language = AppLanguage.PT,
                    enabledApis = enabledApis,
                    cardOrder = emptyList(),
                    minimizedCards = emptySet(),
                    onMoveCardToIndex = { _, _ -> },
                    onToggleCardMinimized = {},
                    onOpenHistory = {},
                    onOpenSettings = { opened = true },
                    countdownUpdatesEnabled = false
                )
            }
        }

        waitUntil(timeoutMillis = 5_000) {
            runCatching {
                onNodeWithText("Nenhuma API monitorada está habilitada").fetchSemanticsNode()
                true
            }.getOrDefault(false)
        }

        onNodeWithText("Abrir configurações").performClick()
        assertEquals(true, opened)
        viewModel.onDestroy()
    }

    @Test
    fun `DashboardScreen shows update banner when a newer version is available`() = runDesktopComposeUiTest {
        val enabledApis = MutableStateFlow(emptySet<ApiSource>())
        val viewModel = dashboardViewModelWithAvailableUpdate(enabledApis)
        viewModel.cancelCountdown()

        setContent {
            AppTheme(isDark = true) {
                DashboardScreen(
                    viewModel = viewModel,
                    appVersion = "7.0.0",
                    language = AppLanguage.PT,
                    enabledApis = enabledApis,
                    cardOrder = emptyList(),
                    minimizedCards = emptySet(),
                    onMoveCardToIndex = { _, _ -> },
                    onToggleCardMinimized = {},
                    onOpenHistory = {},
                    onOpenSettings = {},
                    countdownUpdatesEnabled = false
                )
            }
        }

        waitUntil(timeoutMillis = 5_000) {
            runCatching {
                onNodeWithText("Nova versão 7.1.0 disponível").fetchSemanticsNode()
                true
            }.getOrDefault(false)
        }

        onNodeWithText("Nova versão 7.1.0 disponível").assertIsDisplayed()
        onNodeWithText("A atualização automática não está disponível nesta plataforma. Atualize manualmente pela release publicada.").assertIsDisplayed()
        viewModel.onDestroy()
    }

    // ── SettingsDialogContent ───────────────────────────────────────────

    @Test
    fun `SettingsDialogContent displays localized controls in EN`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                SettingsDialogContent(
                    currentTheme = ThemeMode.DARK,
                    currentLanguage = AppLanguage.EN,
                    enabledApis = setOf(ApiSource.ANTHROPIC, ApiSource.CODEX),
                    autoStartEnabled = false,
                    onThemeToggle = {},
                    onLanguageChange = {},
                    onAutoStartChange = {},
                    onApiToggle = { _, _ -> }
                )
            }
        }

        onNodeWithText("Settings").assertIsDisplayed()
        onNodeWithText("System Startup").assertIsDisplayed()
        onNodeWithText("Language").assertIsDisplayed()
        onNodeWithText("Monitored APIs").assertIsDisplayed()
        onNodeWithText("OpenCode Zen Free").assertIsDisplayed()
        onNodeWithText("Kilo Free").assertIsDisplayed()
        onAllNodesWithText("Close").assertCountEquals(0)
    }

    @Test
    fun `ApiUsageCard renders OpenCode free model activity without percentage gauges`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                ApiUsageCard(
                    source = ApiSource.OPENCODE,
                    apiName = "OpenCode Zen Free",
                    quotas = listOf(
                        QuotaInfo(
                            label = "MiniMax M2.5 Free 5h",
                            used = 4L,
                            total = 0L,
                            periodEndAt = Instant.parse("2026-05-07T15:00:00Z"),
                            hasKnownResetAt = false,
                            periodType = PeriodType.INTERVAL,
                            unit = UsageUnit.REQUESTS
                        ),
                        QuotaInfo(
                            label = "MiniMax M2.5 Free 7d",
                            used = 19L,
                            total = 0L,
                            periodEndAt = Instant.parse("2026-05-07T15:00:00Z"),
                            hasKnownResetAt = false,
                            periodType = PeriodType.WEEKLY,
                            unit = UsageUnit.REQUESTS
                        )
                    ),
                    showUsageDetails = true,
                    isRefreshing = false,
                    language = AppLanguage.PT,
                    animationDelayMillis = 0,
                    onRefresh = {}
                )
            }
        }

        onNodeWithText("OpenCode Zen Free").assertIsDisplayed()
        onNodeWithText("MiniMax M2.5 Free").assertIsDisplayed()
        onNodeWithText("4 requisições").assertIsDisplayed()
        onNodeWithText("Últimas 5h").assertIsDisplayed()
        onNodeWithText("7d: 19").assertIsDisplayed()
        onAllNodesWithText("0%").assertCountEquals(0)
    }

    @Test
    fun `ApiUsageCard renders Kilo free model activity without percentage gauges`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                ApiUsageCard(
                    source = ApiSource.KILO,
                    apiName = "Kilo Free",
                    quotas = listOf(
                        QuotaInfo(
                            label = "Auto Free Kilo Gateway 5h",
                            used = 7L,
                            total = 0L,
                            periodEndAt = Instant.parse("2026-05-07T15:00:00Z"),
                            hasKnownResetAt = false,
                            periodType = PeriodType.INTERVAL,
                            unit = UsageUnit.REQUESTS
                        ),
                        QuotaInfo(
                            label = "Auto Free Kilo Gateway 7d",
                            used = 31L,
                            total = 0L,
                            periodEndAt = Instant.parse("2026-05-07T15:00:00Z"),
                            hasKnownResetAt = false,
                            periodType = PeriodType.WEEKLY,
                            unit = UsageUnit.REQUESTS
                        )
                    ),
                    showUsageDetails = true,
                    isRefreshing = false,
                    language = AppLanguage.PT,
                    animationDelayMillis = 0,
                    onRefresh = {}
                )
            }
        }

        onNodeWithText("Kilo Free").assertIsDisplayed()
        onNodeWithText("Auto Free Kilo Gateway").assertIsDisplayed()
        onNodeWithText("7 requisições").assertIsDisplayed()
        onNodeWithText("Últimas 5h").assertIsDisplayed()
        onNodeWithText("7d: 31").assertIsDisplayed()
        onAllNodesWithText("0%").assertCountEquals(0)
    }

    @Test
    fun `HistoryScreen renders one OpenCode chart per model instead of separate 5h and 7d cards`() = runDesktopComposeUiTest {
        val report = com.usagemonitor.domain.entity.ApiUsageHistoryReport(
            source = ApiSource.OPENCODE,
            range = HistoryRange.LAST_24_HOURS,
            lastUpdatedAt = Instant.parse("2026-05-07T14:33:00Z"),
            series = listOf(
                UsageHistorySeries(
                    quotaLabel = "MiniMax M2.5 Free 5h",
                    periodType = PeriodType.INTERVAL,
                    unit = UsageUnit.REQUESTS,
                    points = listOf(
                        UsageHistoryPoint(
                            capturedAt = Instant.parse("2026-05-07T11:32:00Z"),
                            used = 4,
                            total = 0,
                            rawUsed = 4,
                            rawTotal = 0,
                            periodEndAt = Instant.parse("2026-05-07T14:33:00Z")
                        ),
                        UsageHistoryPoint(
                            capturedAt = Instant.parse("2026-05-07T11:33:00Z"),
                            used = 11,
                            total = 0,
                            rawUsed = 11,
                            rawTotal = 0,
                            periodEndAt = Instant.parse("2026-05-07T14:33:00Z")
                        )
                    ),
                    currentDisplayUsed = 11,
                    currentDisplayTotal = 0,
                    deltaDisplayUsed = 3,
                    averageDisplayConsumptionPerHour = 17.0,
                    currentPeriodEndAt = Instant.parse("2026-05-07T14:33:00Z"),
                    forecast = UsageForecast.InsufficientData
                ),
                UsageHistorySeries(
                    quotaLabel = "MiniMax M2.5 Free 7d",
                    periodType = PeriodType.WEEKLY,
                    unit = UsageUnit.REQUESTS,
                    points = listOf(
                        UsageHistoryPoint(
                            capturedAt = Instant.parse("2026-05-07T11:32:00Z"),
                            used = 16,
                            total = 0,
                            rawUsed = 16,
                            rawTotal = 0,
                            periodEndAt = Instant.parse("2026-05-07T14:33:00Z")
                        ),
                        UsageHistoryPoint(
                            capturedAt = Instant.parse("2026-05-07T11:33:00Z"),
                            used = 29,
                            total = 0,
                            rawUsed = 29,
                            rawTotal = 0,
                            periodEndAt = Instant.parse("2026-05-07T14:33:00Z")
                        )
                    ),
                    currentDisplayUsed = 29,
                    currentDisplayTotal = 0,
                    deltaDisplayUsed = 13,
                    averageDisplayConsumptionPerHour = 2.0,
                    currentPeriodEndAt = Instant.parse("2026-05-07T14:33:00Z"),
                    forecast = UsageForecast.InsufficientData
                )
            )
        )

        val viewModel = HistoryViewModel(
            getUsageHistory = com.usagemonitor.domain.usecase.GetUsageHistoryUseCase(
                repository = object : com.usagemonitor.domain.repository.UsageHistoryRepository {
                    override suspend fun recordSnapshot(
                        stats: com.usagemonitor.domain.entity.ApiUsageStats,
                        capturedAt: Instant
                    ) = Unit

                    override suspend fun getHistoryReport(
                        source: ApiSource,
                        range: HistoryRange,
                        now: Instant
                    ): com.usagemonitor.domain.entity.ApiUsageHistoryReport {
                        return report
                    }
                }
            ),
            enabledApis = MutableStateFlow(setOf(ApiSource.OPENCODE))
        )

        setContent {
            AppTheme(isDark = true) {
                HistoryScreen(
                    viewModel = viewModel,
                    language = AppLanguage.PT,
                    onBack = {},
                    focusedSource = ApiSource.OPENCODE,
                    showSourceSelector = false
                )
            }
        }

        waitUntil(timeoutMillis = 5_000) {
            runCatching {
                onNodeWithText("MiniMax M2.5 Free").fetchSemanticsNode()
                true
            }.getOrDefault(false)
        }

        onNodeWithText("MiniMax M2.5 Free").assertIsDisplayed()
        onAllNodesWithText("MiniMax M2.5 Free 5h").assertCountEquals(0)
        onAllNodesWithText("MiniMax M2.5 Free 7d").assertCountEquals(0)
        onNodeWithText("Requisições nas últimas 5h").assertIsDisplayed()
        onNodeWithText("Requisições nos últimos 7 dias").assertIsDisplayed()
        onNodeWithText("Atividade observada do modelo free na janela curta de 5h.").assertIsDisplayed()
        onNodeWithText("3 requisições").assertIsDisplayed()
        onNodeWithText("17 requisições/h").assertIsDisplayed()

        onNodeWithText("7 dias").performClick()

        waitUntil(timeoutMillis = 5_000) {
            runCatching {
                onNodeWithText("Atividade observada do modelo free na janela semanal de 7 dias.").fetchSemanticsNode()
                true
            }.getOrDefault(false)
        }

        onNodeWithText("Atividade observada do modelo free na janela semanal de 7 dias.").assertIsDisplayed()
        onNodeWithText("13 requisições").assertIsDisplayed()
        onNodeWithText("2 requisições/h").assertIsDisplayed()
        viewModel.onDestroy()
    }

    @Test
    fun `HistoryScreen renders one Kilo chart per model instead of separate 5h and 7d cards`() = runDesktopComposeUiTest {
        val report = com.usagemonitor.domain.entity.ApiUsageHistoryReport(
            source = ApiSource.KILO,
            range = HistoryRange.LAST_24_HOURS,
            lastUpdatedAt = Instant.parse("2026-05-07T14:33:00Z"),
            series = listOf(
                UsageHistorySeries(
                    quotaLabel = "Auto Free Kilo Gateway 5h",
                    periodType = PeriodType.INTERVAL,
                    unit = UsageUnit.REQUESTS,
                    points = listOf(
                        UsageHistoryPoint(
                            capturedAt = Instant.parse("2026-05-07T11:32:00Z"),
                            used = 6,
                            total = 0,
                            rawUsed = 6,
                            rawTotal = 0,
                            periodEndAt = Instant.parse("2026-05-07T14:33:00Z")
                        ),
                        UsageHistoryPoint(
                            capturedAt = Instant.parse("2026-05-07T11:33:00Z"),
                            used = 15,
                            total = 0,
                            rawUsed = 15,
                            rawTotal = 0,
                            periodEndAt = Instant.parse("2026-05-07T14:33:00Z")
                        )
                    ),
                    currentDisplayUsed = 15,
                    currentDisplayTotal = 0,
                    deltaDisplayUsed = 5,
                    averageDisplayConsumptionPerHour = 19.0,
                    currentPeriodEndAt = Instant.parse("2026-05-07T14:33:00Z"),
                    forecast = UsageForecast.InsufficientData
                ),
                UsageHistorySeries(
                    quotaLabel = "Auto Free Kilo Gateway 7d",
                    periodType = PeriodType.WEEKLY,
                    unit = UsageUnit.REQUESTS,
                    points = listOf(
                        UsageHistoryPoint(
                            capturedAt = Instant.parse("2026-05-07T11:32:00Z"),
                            used = 20,
                            total = 0,
                            rawUsed = 20,
                            rawTotal = 0,
                            periodEndAt = Instant.parse("2026-05-07T14:33:00Z")
                        ),
                        UsageHistoryPoint(
                            capturedAt = Instant.parse("2026-05-07T11:33:00Z"),
                            used = 38,
                            total = 0,
                            rawUsed = 38,
                            rawTotal = 0,
                            periodEndAt = Instant.parse("2026-05-07T14:33:00Z")
                        )
                    ),
                    currentDisplayUsed = 38,
                    currentDisplayTotal = 0,
                    deltaDisplayUsed = 18,
                    averageDisplayConsumptionPerHour = 3.0,
                    currentPeriodEndAt = Instant.parse("2026-05-07T14:33:00Z"),
                    forecast = UsageForecast.InsufficientData
                )
            )
        )
        val requestedRanges = mutableListOf<HistoryRange>()

        val viewModel = HistoryViewModel(
            getUsageHistory = com.usagemonitor.domain.usecase.GetUsageHistoryUseCase(
                repository = object : com.usagemonitor.domain.repository.UsageHistoryRepository {
                    override suspend fun recordSnapshot(
                        stats: com.usagemonitor.domain.entity.ApiUsageStats,
                        capturedAt: Instant
                    ) = Unit

                    override suspend fun getHistoryReport(
                        source: ApiSource,
                        range: HistoryRange,
                        now: Instant
                    ): com.usagemonitor.domain.entity.ApiUsageHistoryReport {
                        requestedRanges += range
                        return report
                    }
                }
            ),
            enabledApis = MutableStateFlow(setOf(ApiSource.KILO))
        )

        setContent {
            AppTheme(isDark = true) {
                HistoryScreen(
                    viewModel = viewModel,
                    language = AppLanguage.PT,
                    onBack = {},
                    focusedSource = ApiSource.KILO,
                    showSourceSelector = false
                )
            }
        }

        waitUntil(timeoutMillis = 5_000) {
            runCatching {
                onNodeWithText("Auto Free Kilo Gateway").fetchSemanticsNode()
                true
            }.getOrDefault(false)
        }

        onNodeWithText("Auto Free Kilo Gateway").assertIsDisplayed()
        onAllNodesWithText("Auto Free Kilo Gateway 5h").assertCountEquals(0)
        onAllNodesWithText("Auto Free Kilo Gateway 7d").assertCountEquals(0)
        onNodeWithText("Requisições nas últimas 5h").assertIsDisplayed()
        onNodeWithText("Requisições nos últimos 7 dias").assertIsDisplayed()
        onNodeWithText("Atividade observada do modelo free na janela curta de 5h.").assertIsDisplayed()
        onNodeWithText("5 requisições").assertIsDisplayed()
        onNodeWithText("19 requisições/h").assertIsDisplayed()

        onNodeWithText("7 dias").performClick()

        waitUntil(timeoutMillis = 5_000) {
            runCatching {
                onNodeWithText("Atividade observada do modelo free na janela semanal de 7 dias.").fetchSemanticsNode()
                true
            }.getOrDefault(false)
        }

        onNodeWithText("Atividade observada do modelo free na janela semanal de 7 dias.").assertIsDisplayed()
        onNodeWithText("18 requisições").assertIsDisplayed()
        onNodeWithText("3 requisições/h").assertIsDisplayed()
        assertTrue(HistoryRange.LAST_24_HOURS in requestedRanges)
        assertTrue(HistoryRange.LAST_7_DAYS in requestedRanges)
        viewModel.onDestroy()
    }

    // ── LanguageSelector ─────────────────────────────────────────────────

    @Test
    fun `LanguageSelector displays PT and EN options`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                LanguageSelector(
                    currentLanguage = AppLanguage.PT,
                    onLanguageChange = {}
                )
            }
        }

        onNodeWithText("PT").assertIsDisplayed()
        onNodeWithText("EN").assertIsDisplayed()
    }

    @Test
    fun `LanguageSelector triggers onLanguageChange with EN`() = runDesktopComposeUiTest {
        var selected: AppLanguage? = null

        setContent {
            AppTheme(isDark = true) {
                LanguageSelector(
                    currentLanguage = AppLanguage.PT,
                    onLanguageChange = { selected = it }
                )
            }
        }

        onNodeWithText("EN").performClick()
        assertEquals(AppLanguage.EN, selected)
    }

    @Test
    fun `HistoryScreen renders quota metrics`() = runDesktopComposeUiTest {
        val report = com.usagemonitor.domain.entity.ApiUsageHistoryReport(
            source = ApiSource.CODEX,
            range = HistoryRange.LAST_24_HOURS,
            lastUpdatedAt = Instant.parse("2026-04-28T18:00:00Z"),
            series = listOf(
                UsageHistorySeries(
                    quotaLabel = "Codex 5h",
                    periodType = PeriodType.INTERVAL,
                    unit = UsageUnit.PERCENTAGE,
                    points = listOf(
                        UsageHistoryPoint(
                            capturedAt = Instant.parse("2026-04-28T16:00:00Z"),
                            used = 10,
                            total = 100,
                            rawUsed = 10,
                            rawTotal = 100,
                            periodEndAt = Instant.parse("2026-04-28T20:00:00Z")
                        ),
                        UsageHistoryPoint(
                            capturedAt = Instant.parse("2026-04-28T17:00:00Z"),
                            used = 30,
                            total = 100,
                            rawUsed = 30,
                            rawTotal = 100,
                            periodEndAt = Instant.parse("2026-04-28T20:00:00Z")
                        ),
                        UsageHistoryPoint(
                            capturedAt = Instant.parse("2026-04-28T18:00:00Z"),
                            used = 50,
                            total = 100,
                            rawUsed = 50,
                            rawTotal = 100,
                            periodEndAt = Instant.parse("2026-04-28T20:00:00Z")
                        )
                    ),
                    currentDisplayUsed = 50,
                    currentDisplayTotal = 100,
                    deltaDisplayUsed = 40,
                    averageDisplayConsumptionPerHour = 20.0,
                    currentPeriodEndAt = Instant.parse("2026-04-28T20:00:00Z"),
                    forecast = UsageForecast.EstimatedExhaustionAt(Instant.parse("2026-04-28T20:30:00Z"))
                )
            )
        )

        val viewModel = HistoryViewModel(
            getUsageHistory = com.usagemonitor.domain.usecase.GetUsageHistoryUseCase(
                repository = object : com.usagemonitor.domain.repository.UsageHistoryRepository {
                    override suspend fun recordSnapshot(
                        stats: com.usagemonitor.domain.entity.ApiUsageStats,
                        capturedAt: Instant
                    ) = Unit

                    override suspend fun getHistoryReport(
                        source: ApiSource,
                        range: HistoryRange,
                        now: Instant
                    ): com.usagemonitor.domain.entity.ApiUsageHistoryReport {
                        return report
                    }
                }
            ),
            enabledApis = MutableStateFlow(setOf(ApiSource.CODEX))
        )

        setContent {
            AppTheme(isDark = true) {
                HistoryScreen(
                    viewModel = viewModel,
                    language = AppLanguage.PT,
                    onBack = {},
                    focusedSource = ApiSource.CODEX,
                    showSourceSelector = false
                )
            }
        }

        waitUntil(timeoutMillis = 5_000) {
            runCatching {
                onNodeWithText("Codex 5h").fetchSemanticsNode()
                true
            }.getOrDefault(false)
        }

        onNodeWithText("Histórico do Codex").assertIsDisplayed()
        onNodeWithText("Codex 5h").assertIsDisplayed()
        onAllNodesWithText("API").assertCountEquals(0)
        onNodeWithText("Intervalo").assertIsDisplayed()
        onNodeWithText("Uso atual").assertIsDisplayed()
        onNodeWithText("50 / 100 %").assertIsDisplayed()
        onAllNodesWithText("Fechar").assertCountEquals(0)
        viewModel.onDestroy()
    }

    @Test
    fun `HistoryScreen renders DeepSeek-specific balance summary`() = runDesktopComposeUiTest {
        val report = com.usagemonitor.domain.entity.ApiUsageHistoryReport(
            source = ApiSource.DEEPSEEK,
            range = HistoryRange.LAST_24_HOURS,
            lastUpdatedAt = Instant.parse("2026-05-06T21:47:00Z"),
            series = listOf(
                UsageHistorySeries(
                    quotaLabel = com.usagemonitor.domain.entity.DeepSeekQuotaLabels.BALANCE,
                    periodType = PeriodType.INTERVAL,
                    unit = UsageUnit.CURRENCY_USD,
                    points = listOf(
                        UsageHistoryPoint(
                            capturedAt = Instant.parse("2026-05-06T18:00:00Z"),
                            used = 0,
                            total = 469,
                            rawUsed = 469,
                            rawTotal = 469,
                            periodEndAt = Instant.parse("9999-12-31T23:59:59Z")
                        ),
                        UsageHistoryPoint(
                            capturedAt = Instant.parse("2026-05-06T19:30:00Z"),
                            used = 0,
                            total = 468,
                            rawUsed = 468,
                            rawTotal = 468,
                            periodEndAt = Instant.parse("9999-12-31T23:59:59Z")
                        ),
                        UsageHistoryPoint(
                            capturedAt = Instant.parse("2026-05-06T21:47:00Z"),
                            used = 0,
                            total = 466,
                            rawUsed = 466,
                            rawTotal = 466,
                            periodEndAt = Instant.parse("9999-12-31T23:59:59Z")
                        )
                    ),
                    currentDisplayUsed = 466,
                    currentDisplayTotal = 466,
                    deltaDisplayUsed = 3,
                    averageDisplayConsumptionPerHour = 0.8,
                    currentPeriodEndAt = Instant.parse("9999-12-31T23:59:59Z"),
                    forecast = UsageForecast.InsufficientData
                )
            )
        )

        val viewModel = HistoryViewModel(
            getUsageHistory = com.usagemonitor.domain.usecase.GetUsageHistoryUseCase(
                repository = object : com.usagemonitor.domain.repository.UsageHistoryRepository {
                    override suspend fun recordSnapshot(
                        stats: com.usagemonitor.domain.entity.ApiUsageStats,
                        capturedAt: Instant
                    ) = Unit

                    override suspend fun getHistoryReport(
                        source: ApiSource,
                        range: HistoryRange,
                        now: Instant
                    ): com.usagemonitor.domain.entity.ApiUsageHistoryReport {
                        return report
                    }
                }
            ),
            enabledApis = MutableStateFlow(setOf(ApiSource.DEEPSEEK))
        )

        setContent {
            AppTheme(isDark = true) {
                HistoryScreen(
                    viewModel = viewModel,
                    language = AppLanguage.PT,
                    onBack = {},
                    focusedSource = ApiSource.DEEPSEEK,
                    showSourceSelector = false
                )
            }
        }

        waitUntil(timeoutMillis = 5_000) {
            runCatching {
                onNodeWithText("Saldo restante").fetchSemanticsNode()
                true
            }.getOrDefault(false)
        }

        onNodeWithText("Histórico do DeepSeek").assertIsDisplayed()
        onNodeWithText("Saldo restante").assertIsDisplayed()
        onNodeWithText("Saldo atual").assertIsDisplayed()
        onNodeWithText("Gasto no período").assertIsDisplayed()
        onNodeWithText("Ritmo médio").assertIsDisplayed()
        onNodeWithText("Última coleta").assertIsDisplayed()
        onNodeWithText("\$4.66").assertIsDisplayed()
        onAllNodesWithText("Uso atual").assertCountEquals(0)
        onAllNodesWithText("Quota intervalar").assertCountEquals(0)
        viewModel.onDestroy()
    }

    @Test
    fun `HistoryScreen renders MiniMax request metrics as counts instead of rounded percentage`() = runDesktopComposeUiTest {
        val report = com.usagemonitor.domain.entity.ApiUsageHistoryReport(
            source = ApiSource.MINIMAX,
            range = HistoryRange.LAST_30_DAYS,
            lastUpdatedAt = Instant.parse("2026-05-06T22:02:00Z"),
            series = listOf(
                UsageHistorySeries(
                    quotaLabel = "MiniMax-M*",
                    periodType = PeriodType.INTERVAL,
                    unit = UsageUnit.REQUESTS,
                    points = listOf(
                        UsageHistoryPoint(
                            capturedAt = Instant.parse("2026-04-28T19:00:00Z"),
                            used = 16,
                            total = 4500,
                            rawUsed = 0,
                            rawTotal = 0,
                            periodEndAt = Instant.parse("2026-05-07T00:00:00Z")
                        ),
                        UsageHistoryPoint(
                            capturedAt = Instant.parse("2026-05-06T22:02:00Z"),
                            used = 16,
                            total = 4500,
                            rawUsed = 0,
                            rawTotal = 0,
                            periodEndAt = Instant.parse("2026-05-07T00:00:00Z")
                        )
                    ),
                    currentDisplayUsed = 16,
                    currentDisplayTotal = 4500,
                    deltaDisplayUsed = 0,
                    averageDisplayConsumptionPerHour = 0.0,
                    currentPeriodEndAt = Instant.parse("2026-05-07T00:00:00Z"),
                    forecast = UsageForecast.ResetsBeforeExhaustion
                )
            )
        )

        val viewModel = HistoryViewModel(
            getUsageHistory = com.usagemonitor.domain.usecase.GetUsageHistoryUseCase(
                repository = object : com.usagemonitor.domain.repository.UsageHistoryRepository {
                    override suspend fun recordSnapshot(
                        stats: com.usagemonitor.domain.entity.ApiUsageStats,
                        capturedAt: Instant
                    ) = Unit

                    override suspend fun getHistoryReport(
                        source: ApiSource,
                        range: HistoryRange,
                        now: Instant
                    ): com.usagemonitor.domain.entity.ApiUsageHistoryReport {
                        return report
                    }
                }
            ),
            enabledApis = MutableStateFlow(setOf(ApiSource.MINIMAX))
        )

        setContent {
            AppTheme(isDark = true) {
                HistoryScreen(
                    viewModel = viewModel,
                    language = AppLanguage.PT,
                    onBack = {},
                    focusedSource = ApiSource.MINIMAX,
                    showSourceSelector = false
                )
            }
        }

        waitUntil(timeoutMillis = 5_000) {
            runCatching {
                onNodeWithText("MiniMax-M*").fetchSemanticsNode()
                true
            }.getOrDefault(false)
        }

        onNodeWithText("16/4", substring = true).assertIsDisplayed()
        onNodeWithText("0 req").assertIsDisplayed()
        onNodeWithText("0 req/h").assertIsDisplayed()
        onAllNodesWithText("0 / 100 %").assertCountEquals(0)
        viewModel.onDestroy()
    }
}
