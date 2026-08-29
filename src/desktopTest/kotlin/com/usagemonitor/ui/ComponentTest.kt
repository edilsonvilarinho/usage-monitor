package com.usagemonitor.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.usagemonitor.domain.entity.ActiveSessionAlert
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageNotice
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.UsageAlertSettings
import com.usagemonitor.domain.entity.CliSessionHealth
import com.usagemonitor.domain.entity.SessionPulse
import com.usagemonitor.domain.entity.HistoryRange
import com.usagemonitor.domain.entity.OpenCodeGoQuotaLabels
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.QuotaInfo
import com.usagemonitor.domain.entity.QuotaRiskSummary
import com.usagemonitor.domain.entity.QuotaSeriesKey
import com.usagemonitor.domain.entity.UsageForecast
import com.usagemonitor.domain.entity.UsageRiskLevel
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.UsageHistoryPoint
import com.usagemonitor.domain.entity.UsageHistorySeries
import com.usagemonitor.domain.entity.UsageAccountContext
import com.usagemonitor.domain.entity.UsageAccountKey
import com.usagemonitor.domain.entity.TeamIntegrationSettings
import com.usagemonitor.presentation.ui.theme.AppThemePreset
import com.usagemonitor.domain.entity.UsageUnit
import com.usagemonitor.presentation.ui.components.API_USAGE_CARD_STATUS_TAG
import com.usagemonitor.presentation.ui.components.API_USAGE_CARD_STATUS_HINT_TAG
import com.usagemonitor.presentation.ui.components.ApiUsageCard
import com.usagemonitor.presentation.ui.components.quotaProgressTrackTag
import com.usagemonitor.presentation.ui.components.observedActivityTrackTag
import com.usagemonitor.presentation.ui.components.observedActivityValueTag
import com.usagemonitor.presentation.ui.components.ApiCheckboxRow
import com.usagemonitor.presentation.ui.components.apiSelectorEditKeyTestTag
import com.usagemonitor.presentation.ui.components.apiSelectorSwitchTestTag
import com.usagemonitor.presentation.ui.components.API_KEY_DIALOG_FIELD_TEST_TAG
import com.usagemonitor.presentation.ui.components.API_KEY_DIALOG_REMOVE_TEST_TAG
import com.usagemonitor.presentation.ui.APP_UPDATE_BANNER_TAG
import com.usagemonitor.presentation.ui.DashboardScreen
import com.usagemonitor.presentation.ui.HistoryScreen
import com.usagemonitor.presentation.ui.components.LanguageSelector
import com.usagemonitor.presentation.ui.components.CARDS_ONLY_MODE_SWITCH_TEST_TAG
import com.usagemonitor.presentation.ui.components.FOOTER_VERSION_TEST_TAG
import com.usagemonitor.presentation.ui.components.PersistentApiWarningBanner
import com.usagemonitor.presentation.ui.components.SettingsDialogContent
import com.usagemonitor.presentation.ui.components.SettingsTab
import com.usagemonitor.presentation.ui.components.settingsTabTestTag
import com.usagemonitor.presentation.ui.components.AnthropicProfileUiModel
import com.usagemonitor.presentation.ui.components.AnthropicProfileUiStatus
import com.usagemonitor.presentation.ui.components.ALERT_SETTINGS_QUIET_SWITCH_TEST_TAG
import com.usagemonitor.presentation.ui.components.AlertSettingsSection
import com.usagemonitor.presentation.ui.components.SETTINGS_TOAST_HOST_TEST_TAG
import com.usagemonitor.presentation.ui.components.UI_SCALE_VALUE_TEST_TAG
import com.usagemonitor.presentation.ui.components.WINDOW_OPACITY_VALUE_TEST_TAG
import com.usagemonitor.presentation.ui.components.TEAM_ALIAS_FIELD_TEST_TAG
import com.usagemonitor.presentation.ui.components.TeamConnectionUiState
import com.usagemonitor.presentation.ui.components.TeamIntegrationSection
import com.usagemonitor.presentation.ui.components.ThemeToggle
import com.usagemonitor.presentation.ui.components.UsageArcChart
import com.usagemonitor.presentation.ui.components.WindowOpacitySlider
import com.usagemonitor.presentation.ui.components.quotaBlockTag
import com.usagemonitor.presentation.ui.historyAccountChipTag
import com.usagemonitor.presentation.ui.theme.AppTheme
import com.usagemonitor.presentation.viewmodel.DashboardViewModel
import com.usagemonitor.presentation.viewmodel.HistoryViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.Instant
import androidx.compose.ui.unit.dp
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Integração ligada e completa: é o estado em que a seção mostra os campos. */
private val ACTIVE_TEAM_SETTINGS = TeamIntegrationSettings(
    enabled = true,
    serverUrl = "http://localhost:3000",
    apiKey = "chave-de-time-com-tamanho-suficiente",
    alias = "EDILSON",
    deviceId = "device-1"
)

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

    /**
     * A tela de Histórico virou tabela: cada métrica ocupa uma linha de rótulo e
     * valor em vez de um bloco de duas linhas espremido num `FlowRow`, e a coluna
     * ficou mais alta que os 768px da cena padrão. Os asserts falhavam por
     * viewport — o nó existe, só está abaixo do corte.
     */
    private companion object {
        const val HISTORY_SCENE_HEIGHT = 1_600
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
                    onRefresh = {},
                    // Relogio antes dos dois resets: sem ele o rotulo dependeria
                    // da data em que a suite roda.
                    now = Instant.parse("2026-04-28T10:00:00Z")
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
        onNodeWithText("Reinício: Ter 14h40 BRT").assertIsDisplayed()
        onNodeWithText("Reinício: Dom 03/05 9h00 BRT").assertIsDisplayed()
    }

    @Test
    fun `ApiUsageCard shows account from last successful snapshot`() = runDesktopComposeUiTest {
        val account = UsageAccountContext(
            key = UsageAccountKey(
                source = ApiSource.CODEX,
                providerAccountId = "user-a",
                workspaceId = "workspace-a"
            ),
            email = "conta-codex-muito-longa@example.com",
            workspaceName = "Equipe Principal"
        )
        setContent {
            AppTheme(isDark = true) {
                ApiUsageCard(
                    source = ApiSource.CODEX,
                    apiName = "Codex",
                    quotas = listOf(
                        QuotaInfo(
                            label = "Codex 5h",
                            used = 42L,
                            total = 100L,
                            periodEndAt = Instant.parse("2026-04-28T20:00:00Z"),
                            periodType = PeriodType.INTERVAL,
                            unit = UsageUnit.PERCENTAGE
                        ),
                        QuotaInfo(
                            label = "Codex 7d",
                            used = 17L,
                            total = 100L,
                            periodEndAt = Instant.parse("2026-05-03T12:00:00Z"),
                            periodType = PeriodType.WEEKLY,
                            unit = UsageUnit.PERCENTAGE
                        )
                    ),
                    accountContext = account,
                    showUsageDetails = true,
                    isRefreshing = false,
                    language = AppLanguage.PT,
                    animationDelayMillis = 0,
                    onRefresh = {}
                )
            }
        }

        onNodeWithTag("usageAccountLabel", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText(account.displayLabel).assertIsDisplayed()
        onNodeWithContentDescription("Conta da última coleta: ${account.displayLabel}").assertIsDisplayed()
    }

    @Test
    fun `ApiUsageCard keeps the session buttons plain without a pulse`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                AnthropicCardWithSessionButtons(
                    cliPulse = SessionPulse.EMPTY,
                    teamPulse = SessionPulse.EMPTY
                )
            }
        }

        onNodeWithContentDescription("Sessões CLI desta conta").assertIsDisplayed()
        onNodeWithContentDescription("Sessões do time nesta conta").assertIsDisplayed()
    }

    /**
     * O `autoAdvance` fica desligado porque o pisca é uma animação infinita: com
     * ele ligado o `waitForIdle` do teste nunca retornaria.
     */
    @Test
    fun `ApiUsageCard explains why a session button is pulsing`() = runDesktopComposeUiTest {
        mainClock.autoAdvance = false
        val activity = Instant.parse("2026-04-28T10:00:00Z")
        setContent {
            AppTheme(isDark = true) {
                AnthropicCardWithSessionButtons(
                    cliPulse = SessionPulse(
                        listOf(
                            ActiveSessionAlert(
                                sessionId = "2991339c",
                                health = CliSessionHealth.SATURATED,
                                lastActivityAt = activity,
                                projectName = "usage-monitor"
                            )
                        )
                    ),
                    teamPulse = SessionPulse(
                        listOf(
                            ActiveSessionAlert(
                                sessionId = "aaaa1111",
                                health = CliSessionHealth.ATTENTION,
                                lastActivityAt = activity,
                                projectName = "mdlog-web-compras",
                                memberAlias = "SUETONIO",
                                machineLabel = "devmachine"
                            )
                        )
                    )
                )
            }
        }

        onNodeWithContentDescription(
            "Sessões CLI desta conta — 1 sessão ativa agora pede atenção:\n• Saturada — usage-monitor"
        ).assertIsDisplayed()
        onNodeWithContentDescription(
            "Sessões do time nesta conta — 1 sessão ativa agora pede atenção:" +
                "\n• SUETONIO · devmachine — Atenção (mdlog-web-compras)"
        ).assertIsDisplayed()
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
    fun `ApiUsageCard keeps both Codex quotas while showing an inline notice`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                ApiUsageCard(
                    source = ApiSource.CODEX,
                    apiName = "Codex",
                    quotas = listOf(
                        QuotaInfo(
                            label = "Codex 5h",
                            used = 42L,
                            total = 100L,
                            periodEndAt = Instant.parse("2026-04-28T20:00:00Z"),
                            periodType = PeriodType.INTERVAL,
                            unit = UsageUnit.PERCENTAGE
                        ),
                        QuotaInfo(
                            label = "Codex 7d",
                            used = 17L,
                            total = 100L,
                            periodEndAt = Instant.parse("2026-05-03T12:00:00Z"),
                            periodType = PeriodType.WEEKLY,
                            unit = UsageUnit.PERCENTAGE
                        )
                    ),
                    notices = setOf(
                        ApiUsageNotice.SOURCE_UNSTABLE,
                        ApiUsageNotice.WEEKLY_QUOTA_UNAVAILABLE
                    ),
                    showUsageDetails = true,
                    isRefreshing = false,
                    language = AppLanguage.PT,
                    animationDelayMillis = 0,
                    onRefresh = {},
                    now = Instant.parse("2026-04-28T10:00:00Z")
                )
            }
        }

        onNodeWithText("Codex").assertIsDisplayed()
        onNodeWithText("42%").assertIsDisplayed()
        onNodeWithText("17%").assertIsDisplayed()
        onNodeWithText("Sessão 5h").assertIsDisplayed()
        onNodeWithText("Semanal").assertIsDisplayed()
        // Os dois avisos deixaram de ser banner e viraram uma exclamação no
        // cabeçalho (issue #76): o texto vive na descrição do ícone e na tooltip,
        // não mais no corpo do card.
        onNodeWithContentDescription(
            "Quota 7d indisponível na fonte semanal do Codex",
            substring = true
        ).assertIsDisplayed()
        onNodeWithContentDescription(
            "Fonte de uso do Codex instável: o contrato mudou e os limites podem oscilar até estabilizar.",
            substring = true
        ).assertIsDisplayed()
        onAllNodesWithText("Quota 7d indisponível na fonte semanal do Codex").assertCountEquals(0)
    }

    @Test
    fun `ApiUsageCard keeps both Codex quotas when usage is zero`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                ApiUsageCard(
                    source = ApiSource.CODEX,
                    apiName = "Codex",
                    quotas = listOf(
                        QuotaInfo(
                            label = "Codex 5h",
                            used = 0L,
                            total = 100L,
                            periodEndAt = Instant.parse("2026-04-28T20:00:00Z"),
                            periodType = PeriodType.INTERVAL,
                            unit = UsageUnit.PERCENTAGE
                        ),
                        QuotaInfo(
                            label = "Codex 7d",
                            used = 0L,
                            total = 100L,
                            periodEndAt = Instant.parse("2026-05-03T12:00:00Z"),
                            periodType = PeriodType.WEEKLY,
                            unit = UsageUnit.PERCENTAGE
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

        onNodeWithText("Sessão 5h").assertIsDisplayed()
        onNodeWithText("Semanal").assertIsDisplayed()
        onAllNodesWithText("0%").assertCountEquals(2)
    }

    @Test
    fun `ApiUsageCard renders stable Codex quotas with progress tracks when expanded`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                ApiUsageCard(
                    source = ApiSource.CODEX,
                    apiName = "Codex",
                    quotas = listOf(
                        QuotaInfo(
                            label = "Codex 5h",
                            used = 23L,
                            total = 100L,
                            periodEndAt = Instant.parse("2026-04-28T20:00:00Z"),
                            periodType = PeriodType.INTERVAL,
                            unit = UsageUnit.PERCENTAGE
                        ),
                        QuotaInfo(
                            label = "Codex 7d",
                            used = 11L,
                            total = 100L,
                            periodEndAt = Instant.parse("2026-05-03T12:00:00Z"),
                            periodType = PeriodType.WEEKLY,
                            unit = UsageUnit.PERCENTAGE
                        )
                    ),
                    showUsageDetails = true,
                    isRefreshing = false,
                    language = AppLanguage.PT,
                    animationDelayMillis = 0,
                    onRefresh = {},
                    now = Instant.parse("2026-04-28T10:00:00Z")
                )
            }
        }

        onNodeWithText("Codex").assertIsDisplayed()
        onNodeWithText("Sessão 5h").assertIsDisplayed()
        onNodeWithText("Semanal").assertIsDisplayed()
        onNodeWithText("23%").assertIsDisplayed()
        onNodeWithText("11%").assertIsDisplayed()
        onNodeWithText("Reinício: Ter 17h00 BRT").assertIsDisplayed()
        onNodeWithText("Reinício: Dom 03/05 9h00 BRT").assertIsDisplayed()
        onNodeWithTag(quotaProgressTrackTag("Codex 5h"), useUnmergedTree = true).assertExists()
        onNodeWithTag(quotaProgressTrackTag("Codex 7d"), useUnmergedTree = true).assertExists()
    }

    @Test
    fun `ApiUsageCard keeps Codex compact badges without progress tracks when minimized`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                ApiUsageCard(
                    source = ApiSource.CODEX,
                    apiName = "Codex",
                    quotas = listOf(
                        QuotaInfo(
                            label = "Codex 5h",
                            used = 23L,
                            total = 100L,
                            periodEndAt = Instant.parse("2026-04-28T20:00:00Z"),
                            periodType = PeriodType.INTERVAL,
                            unit = UsageUnit.PERCENTAGE
                        ),
                        QuotaInfo(
                            label = "Codex 7d",
                            used = 11L,
                            total = 100L,
                            periodEndAt = Instant.parse("2026-05-03T12:00:00Z"),
                            periodType = PeriodType.WEEKLY,
                            unit = UsageUnit.PERCENTAGE
                        )
                    ),
                    showUsageDetails = true,
                    isRefreshing = false,
                    isMinimized = true,
                    language = AppLanguage.PT,
                    animationDelayMillis = 0,
                    onRefresh = {},
                    now = Instant.parse("2026-04-28T10:00:00Z")
                )
            }
        }

        onNodeWithText("Codex 5h").assertIsDisplayed()
        onNodeWithText("Codex 7d").assertIsDisplayed()
        onNodeWithText("23%").assertIsDisplayed()
        onNodeWithText("11%").assertIsDisplayed()
        onNodeWithTag(quotaProgressTrackTag("Codex 5h"), useUnmergedTree = true).assertDoesNotExist()
        onNodeWithTag(quotaProgressTrackTag("Codex 7d"), useUnmergedTree = true).assertDoesNotExist()
    }

    /**
     * O texto que saiu do corpo do card tem de continuar alcançável: o hint é o
     * único caminho até ele, e um ícone cuja tooltip não abre é aviso perdido.
     */
    @Test
    fun `ApiUsageCard notice hint opens the notice texts on hover`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                ApiUsageCard(
                    source = ApiSource.CODEX,
                    apiName = "Codex",
                    quotas = listOf(
                        QuotaInfo(
                            label = "Codex atual",
                            used = 42L,
                            total = 100L,
                            periodEndAt = Instant.parse("2026-04-28T20:00:00Z"),
                            periodType = PeriodType.REPORTED,
                            unit = UsageUnit.PERCENTAGE
                        )
                    ),
                    notices = setOf(
                        ApiUsageNotice.SOURCE_UNSTABLE,
                        ApiUsageNotice.WEEKLY_QUOTA_UNAVAILABLE
                    ),
                    showUsageDetails = true,
                    isRefreshing = false,
                    language = AppLanguage.PT,
                    animationDelayMillis = 0,
                    onRefresh = {},
                    now = Instant.parse("2026-04-28T10:00:00Z")
                )
            }
        }

        onAllNodesWithText("Avisos").assertCountEquals(0)

        onNodeWithContentDescription("Avisos:", substring = true)
            .performMouseInput { moveTo(center) }

        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithText("Avisos").fetchSemanticsNodes().isNotEmpty()
        }

        // Com dois avisos cada frase entra com bullet, e as duas moram no mesmo
        // nó de texto da tooltip.
        onNodeWithText(
            "• Quota 7d indisponível na fonte semanal do Codex",
            substring = true
        ).assertIsDisplayed()
        onNodeWithText(
            "• Fonte de uso do Codex instável: o contrato mudou e os limites podem oscilar até estabilizar.",
            substring = true
        ).assertIsDisplayed()
    }

    @Test
    fun `ApiUsageCard shows the missing credits notice on a minimized card`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                ApiUsageCard(
                    source = ApiSource.ANTHROPIC,
                    apiName = "Anthropic",
                    quotas = listOf(
                        QuotaInfo(
                            label = "Claude 5h",
                            used = 11L,
                            total = 100L,
                            periodEndAt = Instant.parse("2026-08-17T17:00:00Z"),
                            periodType = PeriodType.INTERVAL,
                            unit = UsageUnit.PERCENTAGE
                        ),
                        QuotaInfo(
                            label = "Claude 7d",
                            used = 98L,
                            total = 100L,
                            periodEndAt = Instant.parse("2026-08-18T03:00:00Z"),
                            periodType = PeriodType.WEEKLY,
                            unit = UsageUnit.PERCENTAGE
                        )
                    ),
                    notices = setOf(ApiUsageNotice.EXTRA_CREDITS_UNAVAILABLE),
                    showUsageDetails = true,
                    isRefreshing = false,
                    isMinimized = true,
                    language = AppLanguage.PT,
                    animationDelayMillis = 0,
                    onRefresh = {},
                    now = Instant.parse("2026-08-17T12:00:00Z")
                )
            }
        }

        // Com o card fechado o aviso continua visível: foi o card minimizado que
        // escondeu o sumiço dos créditos em agosto/2026. Como exclamação no
        // cabeçalho ele sobrevive aos dois estados — o cabeçalho é composto nos
        // dois.
        onNodeWithContentDescription(
            "Créditos de uso não vieram nesta coleta. O saldo no claude.ai continua valendo.",
            substring = true
        ).assertIsDisplayed()
        onNodeWithText("Claude 7d").assertIsDisplayed()
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

    /**
     * Issue #124: o card do plano Go é o card de cotas normal — três barras de
     * percentual —, e não o resumo de atividade observada do Zen gratuito. É o que
     * `isObservedActivitySource()` decide, e a diferença é visível: aquele resumo
     * não desenha percentual nenhum.
     */
    @Test
    fun `ApiUsageCard renders OpenCode Go as percentage quotas`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                ApiUsageCard(
                    source = ApiSource.OPENCODE_GO,
                    apiName = "OpenCode Go",
                    quotas = listOf(
                        QuotaInfo(
                            label = OpenCodeGoQuotaLabels.ROLLING,
                            used = 12L,
                            total = 100L,
                            periodEndAt = Instant.parse("2026-08-29T18:00:00Z"),
                            periodType = PeriodType.INTERVAL,
                            unit = UsageUnit.PERCENTAGE
                        ),
                        QuotaInfo(
                            label = OpenCodeGoQuotaLabels.WEEKLY,
                            used = 51L,
                            total = 100L,
                            periodEndAt = Instant.parse("2026-08-31T00:00:00Z"),
                            periodType = PeriodType.WEEKLY,
                            unit = UsageUnit.PERCENTAGE
                        ),
                        QuotaInfo(
                            label = OpenCodeGoQuotaLabels.MONTHLY,
                            used = 47L,
                            total = 100L,
                            periodEndAt = Instant.parse("2026-09-05T18:47:55Z"),
                            periodType = PeriodType.MONTHLY,
                            unit = UsageUnit.PERCENTAGE
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

        // Os títulos saem de `expandedQuotaTitle`, derivados do `periodType`; o
        // rótulo da cota é a chave da série histórica e não aparece no card.
        onNodeWithText("OpenCode Go").assertIsDisplayed()
        onNodeWithText("Sessão 5h").assertIsDisplayed()
        onNodeWithText("Semanal").assertIsDisplayed()
        onNodeWithText("Mensal").assertIsDisplayed()
        onNodeWithText("51%").assertIsDisplayed()
    }

    @Test
    fun `ApiUsageCard shows anthropic extra credits in the account currency`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(420.dp)) {
                    ApiUsageCard(
                        source = ApiSource.ANTHROPIC,
                        apiName = "Anthropic",
                        quotas = listOf(
                            QuotaInfo(
                                label = "Claude 5h",
                                used = 21L,
                                total = 100L,
                                periodEndAt = Instant.parse("2026-04-28T20:00:00Z"),
                                periodType = PeriodType.INTERVAL,
                                unit = UsageUnit.PERCENTAGE,
                                rawUsed = 968L,
                                rawTotal = 4500L
                            ),
                            QuotaInfo(
                                label = "Claude 7d",
                                used = 50L,
                                total = 100L,
                                periodEndAt = Instant.parse("2026-05-02T20:00:00Z"),
                                periodType = PeriodType.WEEKLY,
                                unit = UsageUnit.PERCENTAGE,
                                rawUsed = 22500L,
                                rawTotal = 45000L
                            ),
                            QuotaInfo(
                                label = "Créditos",
                                used = 60L,
                                total = 100L,
                                periodEndAt = Instant.parse("2100-01-01T00:00:00Z"),
                                hasKnownResetAt = false,
                                periodType = PeriodType.REPORTED,
                                unit = UsageUnit.PERCENTAGE,
                                rawUsed = 32784L,
                                rawTotal = 55000L,
                                currencyCode = "BRL"
                            )
                        ),
                        // Igual ao dashboard: o card da Anthropic esconde os detalhes
                        // de uso, e ainda assim os créditos precisam aparecer.
                        showUsageDetails = false,
                        isRefreshing = false,
                        language = AppLanguage.PT,
                        animationDelayMillis = 0,
                        onRefresh = {}
                    )
                }
            }
        }

        onNodeWithText("Anthropic").assertIsDisplayed()
        onNodeWithText("Sessão 5h").assertIsDisplayed()
        onNodeWithText("Semanal").assertIsDisplayed()
        onNodeWithText("Créditos de uso").assertIsDisplayed()
        onNodeWithText("60%").assertIsDisplayed()
        onNodeWithText("R$327.84/R$550.00").assertIsDisplayed()
        onNodeWithText("Reinicia no início do mês").assertIsDisplayed()
        onAllNodesWithText("Uso atual").assertCountEquals(0)
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
                            label = "Codex atual",
                            used = 57L,
                            total = 100L,
                            periodEndAt = Instant.parse("2026-04-28T20:00:00Z"),
                            periodType = PeriodType.REPORTED,
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
    fun `ApiUsageCard opens CLI sessions when the account provides the action`() = runDesktopComposeUiTest {
        var opened = false

        setContent {
            AppTheme(isDark = true) {
                ApiUsageCard(
                    source = ApiSource.ANTHROPIC,
                    apiName = "Anthropic — Padrão",
                    quotas = listOf(
                        QuotaInfo(
                            label = "Claude 5h",
                            used = 56L,
                            total = 100L,
                            periodEndAt = Instant.parse("2026-04-28T20:00:00Z"),
                            periodType = PeriodType.INTERVAL,
                            unit = UsageUnit.TOKENS
                        )
                    ),
                    showUsageDetails = false,
                    isRefreshing = false,
                    language = AppLanguage.PT,
                    animationDelayMillis = 0,
                    onRefresh = {},
                    onOpenCliSessions = { opened = true }
                )
            }
        }

        onNodeWithContentDescription("Sessões CLI desta conta").performClick()
        assertEquals(true, opened)
    }

    @Test
    fun `ApiUsageCard hides the CLI action for non Anthropic sources`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                ApiUsageCard(
                    source = ApiSource.CODEX,
                    apiName = "Codex",
                    quotas = listOf(
                        QuotaInfo(
                            label = "Codex atual",
                            used = 57L,
                            total = 100L,
                            periodEndAt = Instant.parse("2026-04-28T20:00:00Z"),
                            periodType = PeriodType.REPORTED,
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

        onAllNodesWithContentDescription("Sessões CLI desta conta").assertCountEquals(0)
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
    fun `ApiUsageCard shows quota tooltip on hover while minimized`() = runDesktopComposeUiTest {
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
                        )
                    ),
                    showUsageDetails = false,
                    isRefreshing = false,
                    isMinimized = true,
                    language = AppLanguage.PT,
                    animationDelayMillis = 0,
                    onRefresh = {},
                    now = Instant.parse("2026-04-28T10:00:00Z")
                )
            }
        }

        // "Uso atual" só existe como rótulo de métrica da tooltip: no card resumido
        // com quota INTERVAL o subtítulo é "Sessão 5h".
        onAllNodesWithText("Uso atual").assertCountEquals(0)

        onNodeWithText("Claude 5h").performMouseInput { moveTo(center) }

        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithText("Uso atual").fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithText("Restante").assertIsDisplayed()
        onNodeWithText("Percentual").assertIsDisplayed()
        onNodeWithText("Reset").assertIsDisplayed()
        onNodeWithText("Reinício: Ter 14h40 BRT").assertIsDisplayed()
    }

    /**
     * Abaixo do piso de largura o popup de cota cobre o card inteiro — a janela do
     * modo somente cards tem ~230dp de largura útil. Ali o hover não abre nada.
     */
    @Test
    fun `ApiUsageCard drops the quota tooltip on a narrow card`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(240.dp)) {
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
                            )
                        ),
                        showUsageDetails = false,
                        isRefreshing = false,
                        isMinimized = true,
                        language = AppLanguage.PT,
                        animationDelayMillis = 0,
                        onRefresh = {},
                        now = Instant.parse("2026-04-28T10:00:00Z")
                    )
                }
            }
        }

        onNodeWithText("Claude 5h").performMouseInput { moveTo(center) }
        waitForIdle()

        // `waitForIdle` e não `waitUntil`: este espera algo aparecer, e aqui a
        // afirmação é que nada aparece.
        onAllNodesWithText("Uso atual").assertCountEquals(0)
        onAllNodesWithText("Restante").assertCountEquals(0)

        // A `testTag` do bloco saiu do `HoverTooltipBox` e desceu para o conteúdo:
        // sem isso o nó sumiria da árvore junto com a tooltip.
        onNodeWithTag(quotaBlockTag("Claude 5h"), useUnmergedTree = true).assertExists()
    }

    /**
     * O ponto do semáforo nunca teve tooltip própria — os dois usos passam
     * `showTooltip = false`, porque dois `TooltipBox` aninhados disputam o hover.
     * A explicação vive no rodapé da tooltip da cota.
     */
    @Test
    fun `ApiUsageCard explains the risk dot in the tooltip footnote`() = runDesktopComposeUiTest {
        val quota = QuotaInfo(
            label = "Claude 7d",
            used = 60L,
            total = 100L,
            periodEndAt = Instant.parse("2026-05-03T12:00:00Z"),
            periodType = PeriodType.WEEKLY,
            unit = UsageUnit.TOKENS
        )

        setContent {
            AppTheme(isDark = true) {
                ApiUsageCard(
                    source = ApiSource.ANTHROPIC,
                    apiName = "Anthropic",
                    quotas = listOf(quota),
                    riskByQuotaKey = mapOf(
                        QuotaSeriesKey(quota.label, quota.periodType) to QuotaRiskSummary(
                            level = UsageRiskLevel.ON_TRACK,
                            estimatedExhaustionAt = null
                        )
                    ),
                    showUsageDetails = false,
                    isRefreshing = false,
                    isMinimized = false,
                    language = AppLanguage.PT,
                    animationDelayMillis = 0,
                    onRefresh = {},
                    now = Instant.parse("2026-04-28T10:00:00Z")
                )
            }
        }

        onNodeWithText("Semanal").performMouseInput { moveTo(center) }

        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithText("No ritmo atual, a cota deve resetar antes de esgotar.")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        // O valor continua na métrica; o rodapé diz o que ele significa.
        onNodeWithText("Projeção de uso").assertIsDisplayed()
    }

    /**
     * Issue #36: com o reset já vencido o card mostrava o horário passado como se
     * fosse futuro, ao lado do percentual saturado da janela anterior.
     */
    @Test
    fun `ApiUsageCard marks an expired quota window instead of showing a past reset`() = runDesktopComposeUiTest {
        val resetsAt = Instant.parse("2026-04-28T17:40:00Z")

        setContent {
            AppTheme(isDark = true) {
                ApiUsageCard(
                    source = ApiSource.ANTHROPIC,
                    apiName = "Anthropic",
                    quotas = listOf(
                        QuotaInfo(
                            label = "Claude 5h",
                            used = 100L,
                            total = 100L,
                            periodEndAt = resetsAt,
                            periodType = PeriodType.INTERVAL,
                            unit = UsageUnit.PERCENTAGE
                        )
                    ),
                    showUsageDetails = false,
                    isRefreshing = false,
                    language = AppLanguage.PT,
                    animationDelayMillis = 0,
                    onRefresh = {},
                    now = resetsAt + 3.minutes
                )
            }
        }

        onNodeWithText("Janela reiniciada · coletando dados").assertIsDisplayed()
        onAllNodesWithText("Reinício: Ter 14h40 BRT").assertCountEquals(0)
        // O número continua na tela: zerá-lo seria inventar um dado que só a
        // próxima coleta pode trazer.
        onNodeWithText("100%").assertIsDisplayed()
    }

    /** A virada é temporal: nada nos dados muda quando a janela vence. */
    @Test
    fun `ApiUsageCard flips to the expired label when the clock crosses the reset`() = runDesktopComposeUiTest {
        val resetsAt = Instant.parse("2026-04-28T17:40:00Z")
        val quota = QuotaInfo(
            label = "Claude 5h",
            used = 100L,
            total = 100L,
            periodEndAt = resetsAt,
            periodType = PeriodType.INTERVAL,
            unit = UsageUnit.PERCENTAGE
        )

        setContent {
            var now by remember { mutableStateOf(resetsAt - 1.minutes) }

            AppTheme(isDark = true) {
                Column {
                    // Só para o teste mover o relógio; na app quem move é a
                    // DashboardScreen, que dorme até o próximo periodEndAt.
                    Text(
                        text = "avançar relógio",
                        modifier = Modifier.clickable { now = resetsAt + 1.minutes }
                    )
                    ApiUsageCard(
                        source = ApiSource.ANTHROPIC,
                        apiName = "Anthropic",
                        quotas = listOf(quota),
                        showUsageDetails = false,
                        isRefreshing = false,
                        language = AppLanguage.PT,
                        animationDelayMillis = 0,
                        onRefresh = {},
                        now = now
                    )
                }
            }
        }

        onNodeWithText("Reinício: Ter 14h40 BRT").assertIsDisplayed()

        onNodeWithText("avançar relógio").performClick()

        onNodeWithText("Janela reiniciada · coletando dados").assertIsDisplayed()
        onAllNodesWithText("Reinício: Ter 14h40 BRT").assertCountEquals(0)
    }

    @Test
    fun `ApiUsageCard tooltip includes projection row when risk is known while minimized`() = runDesktopComposeUiTest {
        val quota = QuotaInfo(
            label = "Claude 5h",
            used = 45L,
            total = 100L,
            periodEndAt = Instant.parse("2026-04-28T17:40:00Z"),
            periodType = PeriodType.INTERVAL,
            unit = UsageUnit.TOKENS
        )
        setContent {
            AppTheme(isDark = true) {
                ApiUsageCard(
                    source = ApiSource.ANTHROPIC,
                    apiName = "Anthropic",
                    quotas = listOf(quota),
                    riskByQuotaKey = mapOf(
                        QuotaSeriesKey(quota.label, quota.periodType) to QuotaRiskSummary(
                            level = UsageRiskLevel.WILL_EXCEED,
                            estimatedExhaustionAt = Instant.parse("2026-04-28T16:00:00Z")
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

        onNodeWithText("Claude 5h").performMouseInput { moveTo(center) }

        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithText("Projeção de uso").fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithText("Crítico").assertIsDisplayed()
    }

    @Test
    fun `ApiUsageCard stacks compact quota badges on very narrow cards`() = runDesktopComposeUiTest {
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
                            unit = UsageUnit.TOKENS
                        ),
                        QuotaInfo(
                            label = "Claude 7d",
                            used = 80L,
                            total = 100L,
                            periodEndAt = Instant.parse("2026-05-03T12:00:00Z"),
                            periodType = PeriodType.WEEKLY,
                            unit = UsageUnit.TOKENS
                        )
                    ),
                    showUsageDetails = false,
                    isRefreshing = false,
                    isMinimized = true,
                    language = AppLanguage.PT,
                    animationDelayMillis = 0,
                    onRefresh = {},
                    modifier = Modifier.width(200.dp)
                )
            }
        }

        // Ancorado no bloco e não no texto: o rótulo deixa de ser o nó externo
        // do badge quando a cota vira linha, e aí a posição medida seria outra.
        val fiveHourTop = onNodeWithTag(quotaBlockTag("Claude 5h"), useUnmergedTree = true)
            .getBoundsInRoot().top
        val weeklyTop = onNodeWithTag(quotaBlockTag("Claude 7d"), useUnmergedTree = true)
            .getBoundsInRoot().top

        assertTrue(
            weeklyTop > fiveHourTop,
            "Badges deveriam empilhar: 5h em $fiveHourTop, 7d em $weeklyTop"
        )
    }

    @Test
    fun `ApiUsageCard keeps compact quota badges side by side on wide cards`() = runDesktopComposeUiTest {
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
                            unit = UsageUnit.TOKENS
                        ),
                        QuotaInfo(
                            label = "Claude 7d",
                            used = 80L,
                            total = 100L,
                            periodEndAt = Instant.parse("2026-05-03T12:00:00Z"),
                            periodType = PeriodType.WEEKLY,
                            unit = UsageUnit.TOKENS
                        )
                    ),
                    showUsageDetails = false,
                    isRefreshing = false,
                    isMinimized = true,
                    language = AppLanguage.PT,
                    animationDelayMillis = 0,
                    onRefresh = {},
                    modifier = Modifier.width(400.dp)
                )
            }
        }

        val fiveHourTop = onNodeWithTag(quotaBlockTag("Claude 5h"), useUnmergedTree = true)
            .getBoundsInRoot().top
        val weeklyTop = onNodeWithTag(quotaBlockTag("Claude 7d"), useUnmergedTree = true)
            .getBoundsInRoot().top

        assertEquals(fiveHourTop, weeklyTop)
    }

    // ── Badge de estado do cabeçalho ─────────────────────────────────────

    @Test
    fun `card header shows the worst risk among quotas as dot and word`() = runDesktopComposeUiTest {
        val now = Instant.parse("2026-04-28T15:00:00Z")
        val fiveHour = QuotaInfo(
            label = "Claude 5h",
            used = 40L,
            total = 100L,
            periodEndAt = Instant.parse("2026-04-28T20:00:00Z"),
            periodType = PeriodType.INTERVAL,
            unit = UsageUnit.TOKENS
        )
        val weekly = QuotaInfo(
            label = "Claude 7d",
            used = 80L,
            total = 100L,
            periodEndAt = Instant.parse("2026-05-03T12:00:00Z"),
            periodType = PeriodType.WEEKLY,
            unit = UsageUnit.TOKENS
        )
        setContent {
            AppTheme(isDark = true) {
                ApiUsageCard(
                    source = ApiSource.ANTHROPIC,
                    apiName = "Anthropic",
                    quotas = listOf(fiveHour, weekly),
                    riskByQuotaKey = mapOf(
                        QuotaSeriesKey(fiveHour.label, fiveHour.periodType) to QuotaRiskSummary(
                            level = UsageRiskLevel.ON_TRACK,
                            estimatedExhaustionAt = null
                        ),
                        QuotaSeriesKey(weekly.label, weekly.periodType) to QuotaRiskSummary(
                            level = UsageRiskLevel.AT_RISK,
                            estimatedExhaustionAt = Instant.parse("2026-05-02T12:00:00Z")
                        )
                    ),
                    showUsageDetails = false,
                    isRefreshing = false,
                    language = AppLanguage.PT,
                    animationDelayMillis = 0,
                    now = now,
                    onRefresh = {}
                )
            }
        }

        // O pior entre as duas, não a primeira: a ordem do enum é quem decide.
        onNodeWithTag(API_USAGE_CARD_STATUS_TAG).assertIsDisplayed()
        onNodeWithText("Atenção").assertIsDisplayed()
    }

    @Test
    fun `card header keeps the status badge while minimized`() = runDesktopComposeUiTest {
        val now = Instant.parse("2026-04-28T15:00:00Z")
        val quota = QuotaInfo(
            label = "Claude 5h",
            used = 95L,
            total = 100L,
            periodEndAt = Instant.parse("2026-04-28T20:00:00Z"),
            periodType = PeriodType.INTERVAL,
            unit = UsageUnit.TOKENS
        )
        setContent {
            AppTheme(isDark = true) {
                ApiUsageCard(
                    source = ApiSource.ANTHROPIC,
                    apiName = "Anthropic",
                    quotas = listOf(quota),
                    riskByQuotaKey = mapOf(
                        QuotaSeriesKey(quota.label, quota.periodType) to QuotaRiskSummary(
                            level = UsageRiskLevel.WILL_EXCEED,
                            estimatedExhaustionAt = Instant.parse("2026-04-28T18:00:00Z")
                        )
                    ),
                    showUsageDetails = false,
                    isRefreshing = false,
                    isMinimized = true,
                    language = AppLanguage.PT,
                    animationDelayMillis = 0,
                    now = now,
                    onRefresh = {}
                )
            }
        }

        onNodeWithTag(API_USAGE_CARD_STATUS_TAG).assertIsDisplayed()
        onNodeWithText("Crítico").assertIsDisplayed()
    }

    @Test
    fun `card status hint explains the quota that caused the severity`() = runDesktopComposeUiTest {
        val quota = QuotaInfo(
            label = "Claude 5h",
            used = 95L,
            total = 100L,
            periodEndAt = Instant.parse("2026-04-28T20:00:00Z"),
            periodType = PeriodType.INTERVAL,
            unit = UsageUnit.PERCENTAGE
        )
        setContent {
            AppTheme(isDark = true) {
                ApiUsageCard(
                    source = ApiSource.ANTHROPIC,
                    apiName = "Anthropic",
                    quotas = listOf(quota),
                    riskByQuotaKey = mapOf(
                        QuotaSeriesKey(quota.label, quota.periodType) to QuotaRiskSummary(
                            level = UsageRiskLevel.WILL_EXCEED,
                            estimatedExhaustionAt = Instant.parse("2026-04-28T18:00:00Z")
                        )
                    ),
                    showUsageDetails = false,
                    isRefreshing = false,
                    language = AppLanguage.PT,
                    animationDelayMillis = 0,
                    now = Instant.parse("2026-04-28T15:00:00Z"),
                    onRefresh = {}
                )
            }
        }

        onNodeWithTag(API_USAGE_CARD_STATUS_HINT_TAG, useUnmergedTree = true)
            .performMouseInput { moveTo(center) }
        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithText("Projeção de uso").fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithText("Cota").assertIsDisplayed()
        onNodeWithText("Claude 5h").assertIsDisplayed()
        onNodeWithText("No ritmo atual, a cota deve esgotar antes do reset", substring = true)
            .assertIsDisplayed()
        onNodeWithContentDescription(
            "Status Crítico. Cota Claude 5h.",
            substring = true
        ).assertIsDisplayed()
    }

    @Test
    fun `card header has no status badge when no quota has a projection`() = runDesktopComposeUiTest {
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
                            periodEndAt = Instant.parse("2026-04-28T20:00:00Z"),
                            periodType = PeriodType.INTERVAL,
                            unit = UsageUnit.TOKENS
                        )
                    ),
                    showUsageDetails = false,
                    isRefreshing = false,
                    language = AppLanguage.PT,
                    animationDelayMillis = 0,
                    now = Instant.parse("2026-04-28T15:00:00Z"),
                    onRefresh = {}
                )
            }
        }

        onNodeWithTag(API_USAGE_CARD_STATUS_TAG).assertDoesNotExist()
    }

    @Test
    fun `card header drops the status badge when the only projected quota expired`() = runDesktopComposeUiTest {
        // A janela descreve um período que já não existe: a projeção sobre ela
        // não diz nada sobre agora, e afirmar "Normal" ali seria uma garantia
        // que ninguém deu.
        val quota = QuotaInfo(
            label = "Claude 5h",
            used = 45L,
            total = 100L,
            periodEndAt = Instant.parse("2026-04-28T12:00:00Z"),
            periodType = PeriodType.INTERVAL,
            unit = UsageUnit.TOKENS
        )
        setContent {
            AppTheme(isDark = true) {
                ApiUsageCard(
                    source = ApiSource.ANTHROPIC,
                    apiName = "Anthropic",
                    quotas = listOf(quota),
                    riskByQuotaKey = mapOf(
                        QuotaSeriesKey(quota.label, quota.periodType) to QuotaRiskSummary(
                            level = UsageRiskLevel.ON_TRACK,
                            estimatedExhaustionAt = null
                        )
                    ),
                    showUsageDetails = false,
                    isRefreshing = false,
                    language = AppLanguage.PT,
                    animationDelayMillis = 0,
                    now = Instant.parse("2026-04-28T15:00:00Z"),
                    onRefresh = {}
                )
            }
        }

        onNodeWithTag(API_USAGE_CARD_STATUS_TAG).assertDoesNotExist()
    }

    // ── RiskSemaphoreDot ─────────────────────────────────────────────────

    @Test
    fun `RiskSemaphoreDot appears minimized with content description for each risk level`() = runDesktopComposeUiTest {
        val quota = QuotaInfo(
            label = "Claude 5h",
            used = 90L,
            total = 100L,
            periodEndAt = Instant.parse("2026-04-28T20:00:00Z"),
            periodType = PeriodType.INTERVAL,
            unit = UsageUnit.TOKENS
        )
        setContent {
            AppTheme(isDark = true) {
                ApiUsageCard(
                    source = ApiSource.ANTHROPIC,
                    apiName = "Anthropic",
                    quotas = listOf(quota),
                    riskByQuotaKey = mapOf(
                        QuotaSeriesKey(quota.label, quota.periodType) to QuotaRiskSummary(
                            level = UsageRiskLevel.WILL_EXCEED,
                            estimatedExhaustionAt = Instant.parse("2026-04-28T19:00:00Z")
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

        onNodeWithContentDescription("Risco de estouro Claude 5h: Crítico").assertIsDisplayed()
    }

    @Test
    fun `RiskSemaphoreDot appears expanded with content description matching risk level`() = runDesktopComposeUiTest {
        val quota = QuotaInfo(
            label = "Claude 7d",
            used = 60L,
            total = 100L,
            periodEndAt = Instant.parse("2026-05-03T12:00:00Z"),
            periodType = PeriodType.WEEKLY,
            unit = UsageUnit.TOKENS
        )
        setContent {
            AppTheme(isDark = true) {
                ApiUsageCard(
                    source = ApiSource.ANTHROPIC,
                    apiName = "Anthropic",
                    quotas = listOf(quota),
                    riskByQuotaKey = mapOf(
                        QuotaSeriesKey(quota.label, quota.periodType) to QuotaRiskSummary(
                            level = UsageRiskLevel.ON_TRACK,
                            estimatedExhaustionAt = null
                        )
                    ),
                    showUsageDetails = false,
                    isRefreshing = false,
                    isMinimized = false,
                    language = AppLanguage.PT,
                    animationDelayMillis = 0,
                    onRefresh = {}
                )
            }
        }

        onNodeWithContentDescription("Risco de estouro Claude 7d: Normal").assertIsDisplayed()
    }

    @Test
    fun `RiskSemaphoreDot is absent when no risk summary is provided for the quota`() = runDesktopComposeUiTest {
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
                            unit = UsageUnit.TOKENS
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

        onNodeWithText("Claude 5h").assertIsDisplayed()
        onAllNodesWithContentDescription("Risco de estouro Claude 5h: Normal").assertCountEquals(0)
        onAllNodesWithContentDescription("Risco de estouro Claude 5h: Atenção").assertCountEquals(0)
        onAllNodesWithContentDescription("Risco de estouro Claude 5h: Crítico").assertCountEquals(0)
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

        // O badge resumido passou a ser âncora de tooltip, que agrega os descendentes
        // na árvore merged — a tag só é alcançável na árvore unmerged.
        val badgeWidth = onNodeWithTag("compactQuotaBadge", useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
            .width
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
    fun `ApiCheckboxRow keeps Codex row plain by default`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                ApiCheckboxRow(
                    api = ApiSource.CODEX,
                    isChecked = true,
                    onCheckedChange = {}
                )
            }
        }

        onNodeWithText("Codex").assertIsDisplayed()
        onAllNodesWithText("Instável").assertCountEquals(0)
        onAllNodesWithText(
            "Monitoramento em transição: o contrato de uso mudou e os limites podem oscilar até a fonte estabilizar."
        ).assertCountEquals(0)
    }

    /**
     * Issue #125: o alvo do clique é o interruptor, não a linha. O `toggleable`
     * saiu dela para o `AppIconButton` de edição poder conviver ali — com ele na
     * linha, `mergeDescendants` engoliria o `contentDescription` do ícone.
     */
    @Test
    fun `ApiCheckboxRow triggers onCheckedChange from the switch`() = runDesktopComposeUiTest {
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

        onNodeWithTag(apiSelectorSwitchTestTag(ApiSource.MINIMAX)).assertIsOff().performClick()
        assertEquals(true, toggled)
    }

    /**
     * Issue #125: fonte sem chave local não tem o que gerenciar, e um lápis que
     * abrisse um diálogo vazio seria pior que ícone nenhum.
     */
    @Test
    fun `ApiCheckboxRow omits the edit icon when there is no key to manage`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                ApiCheckboxRow(
                    api = ApiSource.ANTHROPIC,
                    isChecked = true,
                    onCheckedChange = {}
                )
            }
        }

        onAllNodesWithTag(apiSelectorEditKeyTestTag(ApiSource.ANTHROPIC)).assertCountEquals(0)
    }

    /**
     * O ponto que decide o desenho da linha: com o `toggleable` ainda na linha
     * inteira, `mergeDescendants` mesclaria este `contentDescription` no nó do
     * pai e o clique alternaria o interruptor em vez de abrir o diálogo.
     */
    @Test
    fun `ApiCheckboxRow edit icon opens the key dialog without toggling`() = runDesktopComposeUiTest {
        var edited = false
        var toggled = false

        setContent {
            AppTheme(isDark = true) {
                ApiCheckboxRow(
                    api = ApiSource.MINIMAX,
                    isChecked = true,
                    hasConfiguredApiKey = true,
                    onCheckedChange = { toggled = true },
                    onEditApiKey = { edited = true }
                )
            }
        }

        onNodeWithContentDescription("Gerenciar chave").performClick()

        assertEquals(true, edited)
        assertEquals(false, toggled)
        onNodeWithTag(apiSelectorSwitchTestTag(ApiSource.MINIMAX)).assertIsOn()
    }

    // ── ThemeToggle ───────────────────────────────────────────────────────

    @Test
    fun `ThemeToggle shows dark label when isDark is true`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                ThemeToggle(isDark = true, onToggle = {})
            }
        }

        onNodeWithText("Escuro").assertIsSelected()
    }

    @Test
    fun `ThemeToggle shows light label when isDark is false`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = false) {
                ThemeToggle(isDark = false, onToggle = {})
            }
        }

        onNodeWithText("Claro").assertIsSelected()
    }

    @Test
    fun `ThemeToggle calls onToggle when clicked`() = runDesktopComposeUiTest {
        var toggled = false

        setContent {
            AppTheme(isDark = true) {
                ThemeToggle(isDark = true, onToggle = { toggled = true })
            }
        }

        onNodeWithText("Claro").performClick()
        assertEquals(true, toggled)
    }

    // ── FooterBar ───────────────────────────────────────────────────────

    @Test
    fun `DashboardScreen shows refresh warning dialog and only refreshes on confirm`() = runDesktopComposeUiTest {
        val enabledApis = MutableStateFlow(setOf(ApiSource.ANTHROPIC, ApiSource.MINIMAX))
        val fetchCount = java.util.concurrent.atomic.AtomicInteger(0)
        val viewModel = successDashboardViewModelCountingFetches(enabledApis, fetchCount)
        viewModel.cancelCountdown()

        setContent {
            AppTheme(isDark = true) {
                DashboardScreen(
                    viewModel = viewModel,
                    appVersion = "7.0.0",
                    language = AppLanguage.PT,
                    cardOrder = emptyList(),
                    minimizedCards = emptySet(),
                    onMoveCardToIndex = { _, _ -> },
                    onToggleCardMinimized = {},
                    onOpenHistory = { _, _ -> },
                    onOpenSettings = {},
                    countdownUpdatesEnabled = false
                )
            }
        }

        waitUntil(timeoutMillis = 5_000) {
            fetchCount.get() >= 1
        }
        val fetchCountBeforeManualRefresh = fetchCount.get()

        onNodeWithContentDescription("Atualizar agora").performClick()
        onNodeWithText("Atualizar agora?").assertIsDisplayed()
        onNodeWithText("Cancelar").performClick()
        assertEquals(fetchCountBeforeManualRefresh, fetchCount.get())

        onNodeWithContentDescription("Atualizar agora").performClick()
        onNodeWithText("Atualizar agora?").assertIsDisplayed()
        onNodeWithText("Atualizar").performClick()

        waitUntil(timeoutMillis = 5_000) {
            fetchCount.get() > fetchCountBeforeManualRefresh
        }
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
                    cardOrder = emptyList(),
                    minimizedCards = emptySet(),
                    onMoveCardToIndex = { _, _ -> },
                    onToggleCardMinimized = {},
                    onOpenHistory = { _, _ -> },
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
                    cardOrder = emptyList(),
                    minimizedCards = emptySet(),
                    onMoveCardToIndex = { _, _ -> },
                    onToggleCardMinimized = {},
                    onOpenHistory = { _, _ -> },
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
        onNodeWithText("Baixar atualização →").assertIsDisplayed()
        viewModel.onDestroy()
    }

    @Test
    // Clique em qualquer ponto da faixa abre a release: não há mais botão, e é a
    // linha inteira que carrega a ação (issue #67).
    fun `DashboardScreen opens release page from update banner action`() = runDesktopComposeUiTest {
        val enabledApis = MutableStateFlow(emptySet<ApiSource>())
        var opened = false
        val viewModel = dashboardViewModelWithAvailableUpdateAction(enabledApis) {
            opened = true
        }
        viewModel.cancelCountdown()

        setContent {
            AppTheme(isDark = true) {
                DashboardScreen(
                    viewModel = viewModel,
                    appVersion = "7.0.0",
                    language = AppLanguage.PT,
                    cardOrder = emptyList(),
                    minimizedCards = emptySet(),
                    onMoveCardToIndex = { _, _ -> },
                    onToggleCardMinimized = {},
                    onOpenHistory = { _, _ -> },
                    onOpenSettings = {},
                    countdownUpdatesEnabled = false
                )
            }
        }

        waitUntil(timeoutMillis = 5_000) {
            runCatching {
                onNodeWithTag(APP_UPDATE_BANNER_TAG).fetchSemanticsNode()
                true
            }.getOrDefault(false)
        }

        onNodeWithTag(APP_UPDATE_BANNER_TAG).performClick()
        assertEquals(true, opened)
        viewModel.onDestroy()
    }

    /**
     * Issue #70: no modo somente cards a barra de estado sai da janela, e com
     * ela a versão, a contagem regressiva e as quatro ações do rodapé.
     */
    @Test
    fun `DashboardScreen hides the footer in cards only mode`() = runDesktopComposeUiTest {
        val enabledApis = MutableStateFlow(emptySet<ApiSource>())
        val viewModel = emptyDashboardViewModel(enabledApis)
        viewModel.cancelCountdown()

        setContent {
            AppTheme(isDark = true) {
                DashboardScreen(
                    viewModel = viewModel,
                    appVersion = "7.0.0",
                    language = AppLanguage.PT,
                    cardOrder = emptyList(),
                    minimizedCards = emptySet(),
                    onMoveCardToIndex = { _, _ -> },
                    onToggleCardMinimized = {},
                    onOpenHistory = { _, _ -> },
                    onOpenSettings = {},
                    showFooter = false,
                    countdownUpdatesEnabled = false
                )
            }
        }

        onNodeWithTag(FOOTER_VERSION_TEST_TAG, useUnmergedTree = true).assertDoesNotExist()
        onAllNodesWithContentDescription("Abrir configurações").assertCountEquals(0)
        onAllNodesWithContentDescription("Atualizar agora").assertCountEquals(0)
        viewModel.onDestroy()
    }

    @Test
    fun `DashboardScreen keeps the footer by default`() = runDesktopComposeUiTest {
        val enabledApis = MutableStateFlow(emptySet<ApiSource>())
        val viewModel = emptyDashboardViewModel(enabledApis)
        viewModel.cancelCountdown()

        setContent {
            AppTheme(isDark = true) {
                DashboardScreen(
                    viewModel = viewModel,
                    appVersion = "7.0.0",
                    language = AppLanguage.PT,
                    cardOrder = emptyList(),
                    minimizedCards = emptySet(),
                    onMoveCardToIndex = { _, _ -> },
                    onToggleCardMinimized = {},
                    onOpenHistory = { _, _ -> },
                    onOpenSettings = {},
                    countdownUpdatesEnabled = false
                )
            }
        }

        onNodeWithTag(FOOTER_VERSION_TEST_TAG, useUnmergedTree = true).assertIsDisplayed()
        viewModel.onDestroy()
    }

    // ── SettingsDialogContent ───────────────────────────────────────────

    @Test
    fun `SettingsDialogContent requests an API key before enabling MiniMax`() = runDesktopComposeUiTest {
        var toggledApi: ApiSource? = null
        var savedKey: String? = null

        setContent {
            AppTheme(isDark = true) {
                SettingsDialogContent(
                    currentTheme = AppThemePreset.OBSIDIANA_DARK,
                    currentLanguage = AppLanguage.PT,
                    enabledApis = emptySet(),
                    configuredApiKeys = emptySet(),
                    autoStartEnabled = false,
                    onThemeChange = {},
                    onLanguageChange = {},
                    onAutoStartChange = {},
                    onApiToggle = { api, checked ->
                        if (checked) toggledApi = api
                    },
                    onApiKeySave = { api, key ->
                        savedKey = "$api:$key"
                        true
                    },
                    initialTab = SettingsTab.APIS
                )
            }
        }

        onNodeWithTag(apiSelectorSwitchTestTag(ApiSource.MINIMAX)).performClick()
        onNodeWithText("Configurar MiniMax").assertIsDisplayed()
        onNodeWithTag(API_KEY_DIALOG_FIELD_TEST_TAG).performTextReplacement("minimax-secret")
        onNodeWithText("Salvar").performClick()

        assertEquals("MINIMAX:minimax-secret", savedKey)
        assertEquals(ApiSource.MINIMAX, toggledApi)
    }

    /**
     * Issue #124: a assinatura Go é a terceira fonte que depende de chave local, e
     * o caminho é o mesmo do MiniMax — ligar sem chave abre o diálogo em vez de
     * persistir um interruptor que só produziria erro na próxima coleta.
     */
    @Test
    fun `SettingsDialogContent requests an API key before enabling OpenCode Go`() = runDesktopComposeUiTest {
        var toggledApi: ApiSource? = null
        var savedKey: String? = null

        setContent {
            AppTheme(isDark = true) {
                SettingsDialogContent(
                    currentTheme = AppThemePreset.OBSIDIANA_DARK,
                    currentLanguage = AppLanguage.PT,
                    enabledApis = emptySet(),
                    configuredApiKeys = emptySet(),
                    autoStartEnabled = false,
                    onThemeChange = {},
                    onLanguageChange = {},
                    onAutoStartChange = {},
                    onApiToggle = { api, checked ->
                        if (checked) toggledApi = api
                    },
                    onApiKeySave = { api, key ->
                        savedKey = "$api:$key"
                        true
                    },
                    initialTab = SettingsTab.APIS
                )
            }
        }

        onNodeWithTag(apiSelectorSwitchTestTag(ApiSource.OPENCODE_GO)).performScrollTo().performClick()
        onNodeWithText("Configurar OpenCode Go").assertIsDisplayed()
        onNodeWithTag(API_KEY_DIALOG_FIELD_TEST_TAG).performTextReplacement("opencode-secret")
        onNodeWithText("Salvar").performClick()

        assertEquals("OPENCODE_GO:opencode-secret", savedKey)
        assertEquals(ApiSource.OPENCODE_GO, toggledApi)
    }

    /**
     * O plano gratuito do Zen não tem chave: ligar a linha dele grava direto, sem
     * diálogo. É o que separa as duas fontes de OpenCode na mesma lista.
     */
    @Test
    fun `SettingsDialogContent toggles the free OpenCode source without asking for a key`() = runDesktopComposeUiTest {
        var toggledApi: ApiSource? = null

        setContent {
            AppTheme(isDark = true) {
                SettingsDialogContent(
                    currentTheme = AppThemePreset.OBSIDIANA_DARK,
                    currentLanguage = AppLanguage.PT,
                    enabledApis = emptySet(),
                    configuredApiKeys = emptySet(),
                    autoStartEnabled = false,
                    onThemeChange = {},
                    onLanguageChange = {},
                    onAutoStartChange = {},
                    onApiToggle = { api, checked ->
                        if (checked) toggledApi = api
                    },
                    onApiKeySave = { _, _ -> true },
                    initialTab = SettingsTab.APIS
                )
            }
        }

        onNodeWithTag(apiSelectorSwitchTestTag(ApiSource.OPENCODE)).performScrollTo().performClick()

        assertEquals(ApiSource.OPENCODE, toggledApi)
        onAllNodesWithText("Configurar OpenCode Zen Free").assertCountEquals(0)
    }

    /**
     * Issue #125: o caminho que não existia. Até esta passada o diálogo só abria
     * ao **ligar** uma fonte sem chave; cadastrada uma vez, ela era definitiva
     * pela interface. O lápis abre o mesmo diálogo com a fonte já configurada.
     */
    @Test
    fun `SettingsDialogContent opens the key dialog from the pencil of a configured source`() = runDesktopComposeUiTest {
        var savedKey: String? = null

        setContent {
            AppTheme(isDark = true) {
                SettingsDialogContent(
                    currentTheme = AppThemePreset.OBSIDIANA_DARK,
                    currentLanguage = AppLanguage.PT,
                    enabledApis = setOf(ApiSource.MINIMAX),
                    configuredApiKeys = setOf(ApiSource.MINIMAX),
                    autoStartEnabled = false,
                    onThemeChange = {},
                    onLanguageChange = {},
                    onAutoStartChange = {},
                    onApiToggle = { _, _ -> },
                    onApiKeySave = { api, key ->
                        savedKey = "$api:$key"
                        true
                    },
                    initialTab = SettingsTab.APIS
                )
            }
        }

        onNodeWithTag(apiSelectorEditKeyTestTag(ApiSource.MINIMAX)).performScrollTo().performClick()
        onNodeWithText("Configurar MiniMax").assertIsDisplayed()
        // O campo nunca vem pré-preenchido com a chave guardada: para trocar,
        // digita-se a nova.
        onNodeWithTag(API_KEY_DIALOG_FIELD_TEST_TAG).performTextReplacement("minimax-rotated")
        onNodeWithText("Salvar").performClick()

        assertEquals("MINIMAX:minimax-rotated", savedKey)
    }

    /** Fonte sem chave local não ganha lápis: não há o que gerenciar. */
    @Test
    fun `SettingsDialogContent omits the pencil for sources without a local key`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                SettingsDialogContent(
                    currentTheme = AppThemePreset.OBSIDIANA_DARK,
                    currentLanguage = AppLanguage.PT,
                    enabledApis = setOf(ApiSource.ANTHROPIC),
                    configuredApiKeys = emptySet(),
                    autoStartEnabled = false,
                    onThemeChange = {},
                    onLanguageChange = {},
                    onAutoStartChange = {},
                    onApiToggle = { _, _ -> },
                    initialTab = SettingsTab.APIS
                )
            }
        }

        onAllNodesWithTag(apiSelectorEditKeyTestTag(ApiSource.ANTHROPIC)).assertCountEquals(0)
        onAllNodesWithTag(apiSelectorEditKeyTestTag(ApiSource.OPENCODE)).assertCountEquals(0)
        onNodeWithTag(apiSelectorEditKeyTestTag(ApiSource.DEEPSEEK)).performScrollTo().assertExists()
    }

    /**
     * Issue #125: apagar a chave era impossível pela interface. O botão fica no
     * mesmo diálogo, como `GHOST` — `PRIMARY` é uma por tela e continua sendo o
     * "Salvar", que é o que o diálogo propõe.
     */
    @Test
    fun `SettingsDialogContent removes a stored key from the dialog`() = runDesktopComposeUiTest {
        var removed: ApiSource? = null

        setContent {
            AppTheme(isDark = true) {
                SettingsDialogContent(
                    currentTheme = AppThemePreset.OBSIDIANA_DARK,
                    currentLanguage = AppLanguage.PT,
                    enabledApis = setOf(ApiSource.DEEPSEEK),
                    configuredApiKeys = setOf(ApiSource.DEEPSEEK),
                    autoStartEnabled = false,
                    onThemeChange = {},
                    onLanguageChange = {},
                    onAutoStartChange = {},
                    onApiToggle = { _, _ -> },
                    onApiKeyRemove = { api ->
                        removed = api
                        true
                    },
                    initialTab = SettingsTab.APIS
                )
            }
        }

        onNodeWithTag(apiSelectorEditKeyTestTag(ApiSource.DEEPSEEK)).performScrollTo().performClick()
        onNodeWithTag(API_KEY_DIALOG_REMOVE_TEST_TAG).performClick()

        assertEquals(ApiSource.DEEPSEEK, removed)
        // Gravação confirmada fecha o diálogo.
        onAllNodesWithText("Configurar DeepSeek").assertCountEquals(0)
    }

    /**
     * Ligar uma fonte que nunca foi configurada abre o mesmo diálogo, e ali um
     * botão de remover não teria o que remover.
     */
    @Test
    fun `SettingsDialogContent hides the remove button when there is no stored key`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                SettingsDialogContent(
                    currentTheme = AppThemePreset.OBSIDIANA_DARK,
                    currentLanguage = AppLanguage.PT,
                    enabledApis = emptySet(),
                    configuredApiKeys = emptySet(),
                    autoStartEnabled = false,
                    onThemeChange = {},
                    onLanguageChange = {},
                    onAutoStartChange = {},
                    onApiToggle = { _, _ -> },
                    onApiKeySave = { _, _ -> true },
                    initialTab = SettingsTab.APIS
                )
            }
        }

        onNodeWithTag(apiSelectorSwitchTestTag(ApiSource.MINIMAX)).performScrollTo().performClick()
        onNodeWithText("Configurar MiniMax").assertIsDisplayed()
        onAllNodesWithTag(API_KEY_DIALOG_REMOVE_TEST_TAG).assertCountEquals(0)
    }

    /** Remoção recusada pela camada de dados mantém o diálogo aberto. */
    @Test
    fun `SettingsDialogContent keeps the dialog open when removal fails`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                SettingsDialogContent(
                    currentTheme = AppThemePreset.OBSIDIANA_DARK,
                    currentLanguage = AppLanguage.PT,
                    enabledApis = setOf(ApiSource.DEEPSEEK),
                    configuredApiKeys = setOf(ApiSource.DEEPSEEK),
                    autoStartEnabled = false,
                    onThemeChange = {},
                    onLanguageChange = {},
                    onAutoStartChange = {},
                    onApiToggle = { _, _ -> },
                    onApiKeyRemove = { false },
                    initialTab = SettingsTab.APIS
                )
            }
        }

        onNodeWithTag(apiSelectorEditKeyTestTag(ApiSource.DEEPSEEK)).performScrollTo().performClick()
        onNodeWithTag(API_KEY_DIALOG_REMOVE_TEST_TAG).performClick()

        onNodeWithText("Configurar DeepSeek").assertIsDisplayed()
    }

    /**
     * Issue #125: trocar a chave de uma fonte já ligada não mexe no interruptor.
     * Reafirmá-lo regravaria a preferência, dispararia uma segunda coleta e
     * trocaria o aviso de "chave de API salva" pelo de "APIs monitoradas".
     */
    @Test
    fun `SettingsDialogContent rotates a key without re-enabling the source`() = runDesktopComposeUiTest {
        var savedKey: String? = null
        var toggleCalls = 0

        setContent {
            AppTheme(isDark = true) {
                SettingsDialogContent(
                    currentTheme = AppThemePreset.OBSIDIANA_DARK,
                    currentLanguage = AppLanguage.PT,
                    enabledApis = setOf(ApiSource.MINIMAX),
                    configuredApiKeys = setOf(ApiSource.MINIMAX),
                    autoStartEnabled = false,
                    onThemeChange = {},
                    onLanguageChange = {},
                    onAutoStartChange = {},
                    onApiToggle = { _, _ -> toggleCalls += 1 },
                    onApiKeySave = { api, key ->
                        savedKey = "$api:$key"
                        true
                    },
                    initialTab = SettingsTab.APIS
                )
            }
        }

        onNodeWithTag(apiSelectorEditKeyTestTag(ApiSource.MINIMAX)).performScrollTo().performClick()
        onNodeWithTag(API_KEY_DIALOG_FIELD_TEST_TAG).performTextReplacement("minimax-rotated")
        onNodeWithText("Salvar").performClick()

        assertEquals("MINIMAX:minimax-rotated", savedKey)
        assertEquals(0, toggleCalls)
        onNodeWithTag(apiSelectorSwitchTestTag(ApiSource.MINIMAX)).performScrollTo().assertIsOn()
    }

    /**
     * Issue #70: o interruptor que esconde a moldura da janela mora ao lado de
     * "manter sempre visível" — as duas são propriedades da moldura.
     */
    @Test
    fun `SettingsDialogContent emits the cards only mode change`() = runDesktopComposeUiTest {
        var enabled: Boolean? = null

        setContent {
            AppTheme(isDark = true) {
                SettingsDialogContent(
                    currentTheme = AppThemePreset.OBSIDIANA_DARK,
                    currentLanguage = AppLanguage.PT,
                    enabledApis = setOf(ApiSource.ANTHROPIC),
                    autoStartEnabled = false,
                    cardsOnlyMode = false,
                    onCardsOnlyModeChange = { value -> enabled = value },
                    onThemeChange = {},
                    onLanguageChange = {},
                    onAutoStartChange = {},
                    onApiToggle = { _, _ -> }
                )
            }
        }

        onNodeWithTag(CARDS_ONLY_MODE_SWITCH_TEST_TAG).performClick()

        assertEquals(true, enabled)
    }

    @Test
    fun `SettingsDialogContent displays localized controls in EN`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                SettingsDialogContent(
                    currentTheme = AppThemePreset.OBSIDIANA_DARK,
                    currentLanguage = AppLanguage.EN,
                    enabledApis = setOf(ApiSource.ANTHROPIC, ApiSource.CODEX),
                    autoStartEnabled = false,
                    windowOpacityPercent = 75,
                    uiScalePercent = 115,
                    onThemeChange = {},
                    onLanguageChange = {},
                    onAutoStartChange = {},
                    onApiToggle = { _, _ -> },
                    anthropicProfiles = listOf(
                        AnthropicProfileUiModel(
                            id = "default",
                            label = "Personal",
                            path = "C:\\Users\\test\\.claude",
                            enabled = true,
                            removable = false,
                            identityLabel = "personal@example.com",
                            status = AnthropicProfileUiStatus.READY
                        )
                    )
                )
            }
        }

        // A aba Geral é a que abre; o resto do diálogo só existe depois do clique
        // na aba correspondente.
        onNodeWithText("System Startup").assertIsDisplayed()
        onNodeWithText("Window opacity").assertIsDisplayed()
        // Por tag: "75%" também é rótulo de limiar no cartão de alertas.
        onNodeWithTag(WINDOW_OPACITY_VALUE_TEST_TAG).assertTextEquals("75%")
        onNodeWithText("Interface size").assertIsDisplayed()
        // Mesma razão da tag de opacidade: "115%" também aparece como limiar.
        onNodeWithTag(UI_SCALE_VALUE_TEST_TAG).assertTextEquals("115%")
        onNodeWithText("Language").assertIsDisplayed()

        onNodeWithTag(settingsTabTestTag(SettingsTab.APIS)).performClick()
        onNodeWithText("Monitored APIs").assertIsDisplayed()
        onNodeWithText("OpenCode Zen Free").performScrollTo().assertIsDisplayed()
        onNodeWithText("OpenCode Go").performScrollTo().assertIsDisplayed()
        onNodeWithText("Kilo Free").performScrollTo().assertIsDisplayed()

        onNodeWithTag(settingsTabTestTag(SettingsTab.ACCOUNTS)).performClick()
        onNodeWithText("Anthropic accounts").assertIsDisplayed()
        onNodeWithText("personal@example.com").assertIsDisplayed()

        onAllNodesWithText("Close").assertCountEquals(0)
    }

    @Test
    fun `SettingsDialogContent shows one tab at a time`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                SettingsDialogContent(
                    currentTheme = AppThemePreset.OBSIDIANA_DARK,
                    currentLanguage = AppLanguage.EN,
                    enabledApis = setOf(ApiSource.ANTHROPIC),
                    autoStartEnabled = false,
                    onThemeChange = {},
                    onLanguageChange = {},
                    onAutoStartChange = {},
                    onApiToggle = { _, _ -> }
                )
            }
        }

        // O conteúdo das outras abas não está apenas fora da vista: ele não está
        // na composição. Sem isso as abas seriam decoração sobre a mesma coluna.
        onNodeWithText("System Startup").assertIsDisplayed()
        onAllNodesWithText("Monitored APIs").assertCountEquals(0)
        onAllNodesWithText("Anthropic accounts").assertCountEquals(0)

        onNodeWithTag(settingsTabTestTag(SettingsTab.TEAM)).performClick()
        onAllNodesWithText("System Startup").assertCountEquals(0)
    }

    @Test
    fun `WindowOpacitySlider reports the snapped percent and updates its label`() = runDesktopComposeUiTest {
        var lastReportedPercent = -1

        setContent {
            AppTheme(isDark = true) {
                var percent by remember { mutableStateOf(75) }
                WindowOpacitySlider(
                    percent = percent,
                    language = AppLanguage.EN,
                    onPercentChange = { updated ->
                        lastReportedPercent = updated
                        percent = updated
                    }
                )
            }
        }

        onNodeWithText("75%").assertIsDisplayed()

        // O slider é contínuo; a granularidade de 1 ponto percentual vem do roundToInt.
        onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
            .performSemanticsAction(SemanticsActions.SetProgress) { setProgress -> setProgress(62.4f) }

        assertEquals(62, lastReportedPercent)
        onNodeWithText("62%").assertIsDisplayed()
    }

    @Test
    fun `WindowOpacitySlider explains why the control is unavailable when disabled`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                WindowOpacitySlider(
                    percent = 100,
                    language = AppLanguage.EN,
                    enabled = false,
                    onPercentChange = {}
                )
            }
        }

        onNodeWithText("Transparency is not supported on this system.").assertIsDisplayed()
    }

    @Test
    fun `SettingsDialogContent expands Anthropic profile editor only after Edit click`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                var expandedProfileId by remember { mutableStateOf<String?>(null) }
                SettingsDialogContent(
                    currentTheme = AppThemePreset.OBSIDIANA_DARK,
                    currentLanguage = AppLanguage.EN,
                    enabledApis = setOf(ApiSource.ANTHROPIC),
                    autoStartEnabled = false,
                    onThemeChange = {},
                    onLanguageChange = {},
                    onAutoStartChange = {},
                    onApiToggle = { _, _ -> },
                    anthropicProfiles = listOf(
                        AnthropicProfileUiModel(
                            id = "default",
                            label = "Personal",
                            path = "C:\\Users\\test\\.claude",
                            enabled = true,
                            removable = false,
                            identityLabel = "personal@example.com",
                            status = AnthropicProfileUiStatus.READY
                        )
                    ),
                    expandedProfileId = expandedProfileId,
                    onToggleProfileExpanded = { profileId ->
                        expandedProfileId = if (expandedProfileId == profileId) null else profileId
                    },
                    // As contas moram na aba própria; o teste é sobre o editor do
                    // perfil, não sobre a navegação entre abas.
                    initialTab = SettingsTab.ACCOUNTS
                )
            }
        }

        // Colapsado por padrão: identidade visível, campo de edição do apelido ainda não.
        onNodeWithText("personal@example.com").performScrollTo().assertIsDisplayed()
        onAllNodesWithText("Label").assertCountEquals(0)

        onNodeWithContentDescription("Edit").performScrollTo().performClick()

        // Expandido após clicar em "Editar": campo de edição do apelido aparece.
        onNodeWithText("Label").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `AlertSettingsSection toggles a threshold without dropping the others`() = runDesktopComposeUiTest {
        var current = UsageAlertSettings.DEFAULT

        setContent {
            AppTheme(isDark = true) {
                var settings by remember { mutableStateOf(UsageAlertSettings.DEFAULT) }
                AlertSettingsSection(
                    settings = settings,
                    language = AppLanguage.PT,
                    onSettingsChange = { updated ->
                        settings = updated
                        current = updated
                    }
                )
            }
        }

        onNodeWithText("90%").performClick()
        assertEquals(listOf(75, 100), current.effectiveQuotaPercents)

        onNodeWithText("50%").performClick()
        assertEquals(listOf(50, 75, 100), current.effectiveQuotaPercents)
    }

    /**
     * Um limiar gravado fora da lista oferecida tem de continuar visível: sem
     * isso ele sumiria da tela e seria apagado no primeiro clique em outro chip.
     */
    @Test
    fun `AlertSettingsSection shows a stored threshold outside the offered list`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                AlertSettingsSection(
                    settings = UsageAlertSettings.DEFAULT.copy(quotaPercents = listOf(63, 90)),
                    language = AppLanguage.PT,
                    onSettingsChange = {}
                )
            }
        }

        onNodeWithText("63%").assertIsDisplayed()
        onNodeWithText("90%").assertIsDisplayed()
    }

    @Test
    fun `AlertSettingsSection reveals the quiet range only when it is enabled`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                var settings by remember { mutableStateOf(UsageAlertSettings.DEFAULT) }
                AlertSettingsSection(
                    settings = settings,
                    language = AppLanguage.PT,
                    onSettingsChange = { updated -> settings = updated }
                )
            }
        }

        onAllNodesWithText("Das").assertCountEquals(0)

        onNodeWithTag(ALERT_SETTINGS_QUIET_SWITCH_TEST_TAG).performClick()

        onNodeWithText("Das").assertIsDisplayed()
        onNodeWithText("22h").assertIsDisplayed()
        onNodeWithText("08h").assertIsDisplayed()
    }

    @Test
    fun `SettingsDialogContent hosts its own toast area`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                SettingsDialogContent(
                    currentTheme = AppThemePreset.OBSIDIANA_DARK,
                    currentLanguage = AppLanguage.PT,
                    enabledApis = setOf(ApiSource.ANTHROPIC),
                    autoStartEnabled = false,
                    onThemeChange = {},
                    onLanguageChange = {},
                    onAutoStartChange = {},
                    onApiToggle = { _, _ -> }
                )
            }
        }

        // O diálogo é uma janela separada: o SnackbarHost do dashboard não
        // desenha por cima dela, então o aviso de "salvo" precisa deste host.
        // A exibição em si é do Material3 e não é reencenada aqui — o teste do
        // conteúdo da mensagem é `SettingsToastMessageTest`, em commonTest.
        onNodeWithTag(SETTINGS_TOAST_HOST_TEST_TAG).assertExists()
    }

    // ── TeamIntegrationSection ──────────────────────────────────────────

    @Test
    fun `TeamIntegrationSection commits the alias after the typing pause`() = runDesktopComposeUiTest {
        var committed: String? = null

        setContent {
            AppTheme(isDark = true) {
                TeamIntegrationSection(
                    settings = ACTIVE_TEAM_SETTINGS,
                    language = AppLanguage.PT,
                    profiles = emptyList(),
                    connection = TeamConnectionUiState(),
                    onEnabledChange = {},
                    onServerUrlChange = {},
                    onApiKeyChange = {},
                    onAliasChange = { alias -> committed = alias },
                    onProfileParticipationChange = { _, _ -> },
                    onTestConnection = {}
                )
            }
        }

        onNodeWithTag(TEAM_ALIAS_FIELD_TEST_TAG).performTextReplacement("SUETONIO")

        // Gravar por tecla escreveria em disco a cada caractere e faria o aviso
        // de "salvo" piscar oito vezes.
        assertEquals(null, committed)

        waitUntil(timeoutMillis = 5_000) { committed != null }
        assertEquals("SUETONIO", committed)
    }

    @Test
    fun `TeamIntegrationSection refuses to clear an alias already saved`() = runDesktopComposeUiTest {
        var committed: String? = null

        setContent {
            AppTheme(isDark = true) {
                TeamIntegrationSection(
                    settings = ACTIVE_TEAM_SETTINGS,
                    language = AppLanguage.PT,
                    profiles = emptyList(),
                    connection = TeamConnectionUiState(),
                    onEnabledChange = {},
                    onServerUrlChange = {},
                    onApiKeyChange = {},
                    onAliasChange = { alias -> committed = alias },
                    onProfileParticipationChange = { _, _ -> },
                    onTestConnection = {}
                )
            }
        }

        onNodeWithTag(TEAM_ALIAS_FIELD_TEST_TAG).performTextClearance()

        // Apelido vazio derruba `isConfigured`, para o laço de envio e faz o
        // servidor recusar o ingest com 400.
        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithText("O apelido não pode ficar vazio.")
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }
        assertEquals(null, committed)
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
    fun `OpenCode and Kilo observed activity values stay horizontal in narrow expanded cards`() = runDesktopComposeUiTest(width = 260, height = 1_200) {
        mainClock.autoAdvance = false
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(260.dp).height(1_200.dp)) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        ApiUsageCard(
                            source = ApiSource.OPENCODE,
                            apiName = "OpenCode Zen Free",
                            quotas = observedActivityQuotas("OpenCode model", 1L, 7L),
                            showUsageDetails = true,
                            isRefreshing = false,
                            language = AppLanguage.PT,
                            animationDelayMillis = 0,
                            animateEntrance = false,
                            modifier = Modifier.width(260.dp),
                            onRefresh = {}
                        )
                        ApiUsageCard(
                            source = ApiSource.KILO,
                            apiName = "Kilo Free",
                            quotas = observedActivityQuotas("Kilo model", 2L, 17L),
                            showUsageDetails = true,
                            isRefreshing = false,
                            language = AppLanguage.PT,
                            animationDelayMillis = 0,
                            animateEntrance = false,
                            modifier = Modifier.width(260.dp),
                            onRefresh = {}
                        )
                    }
                }
            }
        }

        mainClock.advanceTimeBy(16)
        mainClock.advanceTimeBy(1_000)
        onNodeWithText("1 req.", useUnmergedTree = true).assertExists()
        onNodeWithText("17 req.", useUnmergedTree = true).assertExists()
        onNodeWithTag(
            observedActivityValueTag("OpenCode model", "5h"),
            useUnmergedTree = true
        ).assertExists()
        onNodeWithTag(
            observedActivityValueTag("Kilo model", "7d"),
            useUnmergedTree = true
        ).assertExists()
        onNodeWithTag(
            observedActivityTrackTag("OpenCode model", "5h"),
            useUnmergedTree = true
        ).assertExists()
        onNodeWithTag(
            observedActivityTrackTag("Kilo model", "7d"),
            useUnmergedTree = true
        ).assertExists()
    }

    @Test
    fun `HistoryScreen renders one OpenCode chart per model instead of separate 5h and 7d cards`() = runDesktopComposeUiTest(height = HISTORY_SCENE_HEIGHT) {
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
                    forecast = UsageForecast.InsufficientData,
                    riskSummary = null
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
                    forecast = UsageForecast.InsufficientData,
                    riskSummary = null
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
    fun `HistoryScreen renders one Kilo chart per model instead of separate 5h and 7d cards`() = runDesktopComposeUiTest(height = HISTORY_SCENE_HEIGHT) {
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
                    forecast = UsageForecast.InsufficientData,
                    riskSummary = null
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
                    forecast = UsageForecast.InsufficientData,
                    riskSummary = null
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

        onNodeWithText("Total").performClick()

        waitUntil(timeoutMillis = 5_000) {
            requestedRanges.contains(HistoryRange.TOTAL)
        }

        onNodeWithText("Atividade observada do modelo free na janela semanal de 7 dias.").assertIsDisplayed()
        assertTrue(HistoryRange.LAST_24_HOURS in requestedRanges)
        assertTrue(HistoryRange.LAST_7_DAYS in requestedRanges)
        assertTrue(HistoryRange.TOTAL in requestedRanges)
        viewModel.onDestroy()
    }

    @Test
    fun `HistoryScreen renders one Claude chart instead of separate 5h and 7d cards`() = runDesktopComposeUiTest(height = HISTORY_SCENE_HEIGHT) {
        val report = com.usagemonitor.domain.entity.ApiUsageHistoryReport(
            source = ApiSource.ANTHROPIC,
            range = HistoryRange.LAST_24_HOURS,
            lastUpdatedAt = Instant.parse("2026-05-07T14:33:00Z"),
            series = listOf(
                UsageHistorySeries(
                    quotaLabel = "Claude 5h",
                    periodType = PeriodType.INTERVAL,
                    unit = UsageUnit.PERCENTAGE,
                    points = listOf(
                        UsageHistoryPoint(
                            capturedAt = Instant.parse("2026-05-07T11:32:00Z"),
                            used = 4,
                            total = 100,
                            rawUsed = 180,
                            rawTotal = 4500,
                            periodEndAt = Instant.parse("2026-05-07T14:33:00Z")
                        )
                    ),
                    currentDisplayUsed = 180,
                    currentDisplayTotal = 4500,
                    deltaDisplayUsed = 20,
                    averageDisplayConsumptionPerHour = 5.0,
                    currentPeriodEndAt = Instant.parse("2026-05-07T14:33:00Z"),
                    forecast = UsageForecast.InsufficientData,
                    riskSummary = null
                ),
                UsageHistorySeries(
                    quotaLabel = "Claude 7d",
                    periodType = PeriodType.WEEKLY,
                    unit = UsageUnit.PERCENTAGE,
                    points = listOf(
                        UsageHistoryPoint(
                            capturedAt = Instant.parse("2026-05-07T11:32:00Z"),
                            used = 46,
                            total = 100,
                            rawUsed = 20700,
                            rawTotal = 45000,
                            periodEndAt = Instant.parse("2026-05-10T14:33:00Z")
                        )
                    ),
                    currentDisplayUsed = 20700,
                    currentDisplayTotal = 45000,
                    deltaDisplayUsed = 900,
                    averageDisplayConsumptionPerHour = 30.0,
                    currentPeriodEndAt = Instant.parse("2026-05-10T14:33:00Z"),
                    forecast = UsageForecast.ResetsBeforeExhaustion,
                    riskSummary = null
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
            enabledApis = MutableStateFlow(setOf(ApiSource.ANTHROPIC))
        )

        setContent {
            AppTheme(isDark = true) {
                HistoryScreen(
                    viewModel = viewModel,
                    language = AppLanguage.PT,
                    onBack = {},
                    focusedSource = ApiSource.ANTHROPIC,
                    showSourceSelector = false
                )
            }
        }

        waitUntil(timeoutMillis = 5_000) {
            runCatching {
                onNodeWithText("Claude").fetchSemanticsNode()
                true
            }.getOrDefault(false)
        }

        onNodeWithText("Claude").assertIsDisplayed()
        onAllNodesWithText("Claude 5h").assertCountEquals(0)
        onAllNodesWithText("Claude 7d").assertCountEquals(0)
        onNodeWithText("Cota intervalar atual").assertIsDisplayed()
        onNodeWithText("Cota semanal atual").assertIsDisplayed()
        onAllNodesWithText("Início do recorte").assertCountEquals(0)
        onAllNodesWithText("Arraste no gráfico para comparar dois pontos.").assertCountEquals(0)
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
    fun `HistoryScreen lists accounts and allows selecting another workspace`() = runDesktopComposeUiTest {
        val accountA = UsageAccountContext(
            key = UsageAccountKey(ApiSource.CODEX, "same-user", "workspace-a"),
            email = "same@example.com",
            workspaceName = "Workspace A"
        )
        val accountB = UsageAccountContext(
            key = UsageAccountKey(ApiSource.CODEX, "same-user", "workspace-b"),
            email = "same@example.com",
            workspaceName = "Workspace B"
        )
        val report = com.usagemonitor.domain.entity.ApiUsageHistoryReport(
            source = ApiSource.CODEX,
            range = HistoryRange.LAST_24_HOURS,
            lastUpdatedAt = null,
            series = emptyList()
        )
        val repository = object : com.usagemonitor.domain.repository.UsageHistoryRepository {
            override suspend fun recordSnapshot(stats: ApiUsageStats, capturedAt: Instant) = Unit

            override suspend fun listAccounts(source: ApiSource): List<UsageAccountContext> {
                return listOf(accountA, accountB)
            }

            override suspend fun getHistoryReport(
                source: ApiSource,
                range: HistoryRange,
                now: Instant
            ) = report
        }
        val viewModel = HistoryViewModel(
            getUsageHistory = com.usagemonitor.domain.usecase.GetUsageHistoryUseCase(repository),
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
                onNodeWithText(accountA.displayLabel).fetchSemanticsNode()
                true
            }.getOrDefault(false)
        }
        onNodeWithText("Conta").assertIsDisplayed()
        // Pela tag: o rótulo da conta é `email — workspace`, texto longo e livre
        // que também aparece no card do dashboard.
        onNodeWithTag(historyAccountChipTag(accountA)).assertIsSelected()
        onNodeWithTag(historyAccountChipTag(accountB)).performClick()
        waitUntil(timeoutMillis = 5_000) {
            runCatching {
                onNodeWithTag(historyAccountChipTag(accountB)).assertIsSelected()
                true
            }.getOrDefault(false)
        }
        viewModel.onDestroy()
    }

    @Test
    fun `HistoryScreen renders reported Codex series without inferred metrics`() = runDesktopComposeUiTest(height = HISTORY_SCENE_HEIGHT) {
        val report = com.usagemonitor.domain.entity.ApiUsageHistoryReport(
            source = ApiSource.CODEX,
            range = HistoryRange.LAST_24_HOURS,
            lastUpdatedAt = Instant.parse("2026-04-28T18:00:00Z"),
            series = listOf(
                UsageHistorySeries(
                    quotaLabel = "Codex atual",
                    periodType = PeriodType.REPORTED,
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
                    averageDisplayConsumptionPerHour = 0.0,
                    currentPeriodEndAt = Instant.parse("2026-04-28T20:00:00Z"),
                    forecast = UsageForecast.InsufficientData,
                    riskSummary = null
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
                onNodeWithText("Codex atual").fetchSemanticsNode()
                true
            }.getOrDefault(false)
        }

        onNodeWithText("Histórico do Codex").assertIsDisplayed()
        onNodeWithText("Codex atual").assertIsDisplayed()
        onNodeWithText("Janela reportada").assertIsDisplayed()
        onAllNodesWithText("API").assertCountEquals(0)
        onNodeWithText("Intervalo").assertIsDisplayed()
        onNodeWithText("Total").assertIsDisplayed()
        onAllNodesWithText("Início do recorte").assertCountEquals(0)
        onAllNodesWithText("Atual").assertCountEquals(0)
        onAllNodesWithText("Variação no recorte").assertCountEquals(0)
        onAllNodesWithText("Arraste no gráfico para comparar dois pontos.").assertCountEquals(0)
        onNodeWithText("Uso atual").assertIsDisplayed()
        onNodeWithText("50 / 100 %").assertIsDisplayed()
        onNodeWithText("Variação observada").assertIsDisplayed()
        onNodeWithText("40 %").assertIsDisplayed()
        onNodeWithText("Último reinício reportado").assertIsDisplayed()
        onNodeWithText("28/04 17:00 BRT").assertIsDisplayed()
        onAllNodesWithText("Média por hora").assertCountEquals(0)
        onAllNodesWithText("Previsão").assertCountEquals(0)
        onAllNodesWithText("Fechar").assertCountEquals(0)
        viewModel.onDestroy()
    }

    @Test
    fun `HistoryScreen keeps reported Codex series separate from legacy series`() = runDesktopComposeUiTest(height = HISTORY_SCENE_HEIGHT) {
        val report = com.usagemonitor.domain.entity.ApiUsageHistoryReport(
            source = ApiSource.CODEX,
            range = HistoryRange.LAST_24_HOURS,
            lastUpdatedAt = Instant.parse("2026-04-28T18:00:00Z"),
            series = listOf(
                UsageHistorySeries(
                    quotaLabel = "Codex atual",
                    periodType = PeriodType.REPORTED,
                    unit = UsageUnit.PERCENTAGE,
                    points = listOf(
                        UsageHistoryPoint(
                            capturedAt = Instant.parse("2026-04-28T17:00:00Z"),
                            used = 16,
                            total = 100,
                            rawUsed = 16,
                            rawTotal = 100,
                            periodEndAt = Instant.parse("2026-04-28T20:00:00Z")
                        )
                    ),
                    currentDisplayUsed = 16,
                    currentDisplayTotal = 100,
                    deltaDisplayUsed = 0,
                    averageDisplayConsumptionPerHour = 0.0,
                    currentPeriodEndAt = Instant.parse("2026-04-28T20:00:00Z"),
                    forecast = UsageForecast.InsufficientData,
                    riskSummary = null
                ),
                UsageHistorySeries(
                    quotaLabel = "Codex 5h",
                    periodType = PeriodType.INTERVAL,
                    unit = UsageUnit.PERCENTAGE,
                    points = listOf(
                        UsageHistoryPoint(
                            capturedAt = Instant.parse("2026-04-28T16:00:00Z"),
                            used = 5,
                            total = 100,
                            rawUsed = 5,
                            rawTotal = 100,
                            periodEndAt = Instant.parse("2026-04-28T20:00:00Z")
                        )
                    ),
                    currentDisplayUsed = 5,
                    currentDisplayTotal = 100,
                    deltaDisplayUsed = 0,
                    averageDisplayConsumptionPerHour = 0.0,
                    currentPeriodEndAt = Instant.parse("2026-04-28T20:00:00Z"),
                    forecast = UsageForecast.InsufficientData,
                    riskSummary = null
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
                onNodeWithText("Codex atual").fetchSemanticsNode()
                true
            }.getOrDefault(false)
        }

        onNodeWithText("Codex atual").assertIsDisplayed()
        onNodeWithText("Codex 5h").assertIsDisplayed()
        onNodeWithText("Quota intervalar").assertIsDisplayed()
        viewModel.onDestroy()
    }

    @Test
    fun `HistoryScreen renders DeepSeek-specific balance summary`() = runDesktopComposeUiTest(height = HISTORY_SCENE_HEIGHT) {
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
                    forecast = UsageForecast.InsufficientData,
                    riskSummary = null
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
    fun `HistoryScreen renders MiniMax request metrics as counts instead of rounded percentage`() = runDesktopComposeUiTest(height = HISTORY_SCENE_HEIGHT) {
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
                    forecast = UsageForecast.ResetsBeforeExhaustion,
                    riskSummary = null
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

        onAllNodesWithText("16/4", substring = true).assertCountEquals(1)
        onNodeWithText("0 req").assertIsDisplayed()
        onNodeWithText("0 req/h").assertIsDisplayed()
        onAllNodesWithText("0 / 100 %").assertCountEquals(0)
        viewModel.onDestroy()
    }
}

/** Card Anthropic com os dois botões de sessão, o alvo do semáforo. */
@Composable
private fun AnthropicCardWithSessionButtons(
    cliPulse: SessionPulse,
    teamPulse: SessionPulse
) {
    ApiUsageCard(
        source = ApiSource.ANTHROPIC,
        apiName = "Anthropic",
        quotas = listOf(
            QuotaInfo(
                label = "Claude 5h",
                used = 20L,
                total = 100L,
                periodEndAt = Instant.parse("2026-04-28T17:40:00Z"),
                periodType = PeriodType.INTERVAL,
                unit = UsageUnit.PERCENTAGE
            )
        ),
        showUsageDetails = false,
        isRefreshing = false,
        language = AppLanguage.PT,
        animationDelayMillis = 0,
        onRefresh = {},
        onOpenCliSessions = {},
        onOpenTeamUsage = {},
        cliSessionPulse = cliPulse,
        teamSessionPulse = teamPulse,
        now = Instant.parse("2026-04-28T10:00:00Z")
    )
}

private fun observedActivityQuotas(modelName: String, fiveHour: Long, sevenDay: Long): List<QuotaInfo> {
    return listOf(
        QuotaInfo(
            label = "$modelName 5h",
            used = fiveHour,
            total = 0L,
            periodEndAt = Instant.parse("2026-05-07T15:00:00Z"),
            hasKnownResetAt = false,
            periodType = PeriodType.INTERVAL,
            unit = UsageUnit.REQUESTS
        ),
        QuotaInfo(
            label = "$modelName 7d",
            used = sevenDay,
            total = 0L,
            periodEndAt = Instant.parse("2026-05-07T15:00:00Z"),
            hasKnownResetAt = false,
            periodType = PeriodType.WEEKLY,
            unit = UsageUnit.REQUESTS
        )
    )
}
