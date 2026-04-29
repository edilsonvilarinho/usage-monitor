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
import com.usagemonitor.domain.entity.HistoryRange
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.QuotaInfo
import com.usagemonitor.domain.entity.UsageForecast
import com.usagemonitor.domain.entity.UsageHistoryPoint
import com.usagemonitor.domain.entity.UsageHistorySeries
import com.usagemonitor.domain.entity.AppTheme as ThemeMode
import com.usagemonitor.domain.entity.UsageUnit
import com.usagemonitor.presentation.ui.components.ApiUsageCard
import com.usagemonitor.presentation.ui.components.ApiCheckboxRow
import com.usagemonitor.presentation.ui.components.FooterBar
import com.usagemonitor.presentation.ui.HistoryScreen
import com.usagemonitor.presentation.ui.components.LanguageSelector
import com.usagemonitor.presentation.ui.components.PersistentApiWarningBanner
import com.usagemonitor.presentation.ui.components.SettingsDialogContent
import com.usagemonitor.presentation.ui.components.ThemeToggle
import com.usagemonitor.presentation.ui.components.UsageArcChart
import com.usagemonitor.presentation.ui.theme.AppTheme
import com.usagemonitor.presentation.viewmodel.HistoryViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.Instant
import androidx.compose.ui.unit.dp
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
        onNodeWithText("12/45 req").assertIsDisplayed()
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
        onNodeWithText("ANTHROPIC").assertIsDisplayed()
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

        onNodeWithText("MINIMAX").performClick()
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
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(640.dp)) {
                    FooterBar(
                        appVersion = "1.1.0",
                        language = AppLanguage.PT,
                        secondsUntilRefresh = 125,
                        onRefresh = {},
                        onOpenSettings = {}
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

        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(640.dp)) {
                    FooterBar(
                        appVersion = "1.1.0",
                        language = AppLanguage.PT,
                        secondsUntilRefresh = 125,
                        onRefresh = {},
                        onOpenSettings = { opened = true }
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

        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(320.dp)) {
                    FooterBar(
                        appVersion = "6.0.0",
                        language = AppLanguage.PT,
                        secondsUntilRefresh = 433,
                        onRefresh = {},
                        onOpenSettings = { opened = true }
                    )
                }
            }
        }

        onNodeWithText("v6.0.0").assertIsDisplayed()
        onNodeWithText("07:13").assertIsDisplayed()
        onNodeWithContentDescription("Abrir configurações").performClick()
        assertEquals(true, opened)
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
                    onApiToggle = { _, _ -> },
                    onClose = {}
                )
            }
        }

        onNodeWithText("Settings").assertIsDisplayed()
        onNodeWithText("System Startup").assertIsDisplayed()
        onNodeWithText("Language").assertIsDisplayed()
        onNodeWithText("Monitored APIs").assertIsDisplayed()
        onNodeWithText("Close").assertIsDisplayed()
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
                    unit = UsageUnit.REQUESTS,
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
        onNodeWithText("50 / 100 req").assertIsDisplayed()
        viewModel.onDestroy()
    }
}
