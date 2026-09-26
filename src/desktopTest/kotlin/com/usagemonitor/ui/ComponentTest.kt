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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
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
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.ActiveSessionAlert
import com.usagemonitor.domain.entity.AntigravityQuotaLabels
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageNotice
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.CliSessionHealth
import com.usagemonitor.domain.entity.CursorQuotaLabels
import com.usagemonitor.domain.entity.OpenCodeGoQuotaLabels
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.QuotaInfo
import com.usagemonitor.domain.entity.QuotaRiskSummary
import com.usagemonitor.domain.entity.QuotaSeriesKey
import com.usagemonitor.domain.entity.SessionPulse
import com.usagemonitor.domain.entity.TeamIntegrationSettings
import com.usagemonitor.domain.entity.UsageAccountContext
import com.usagemonitor.domain.entity.UsageAccountKey
import com.usagemonitor.domain.entity.UsageAlertSettings
import com.usagemonitor.domain.entity.UsageRiskLevel
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.domain.entity.UsageUnit
import com.usagemonitor.presentation.ui.APP_UPDATE_BANNER_ACTION_TAG
import com.usagemonitor.presentation.ui.APP_UPDATE_BANNER_TAG
import com.usagemonitor.presentation.ui.DashboardScreen
import com.usagemonitor.presentation.ui.components.ACCOUNT_COLOR_OPTION_TEST_TAG_PREFIX
import com.usagemonitor.presentation.ui.components.ACCOUNT_EMOJI_OPTION_TEST_TAG_PREFIX
import com.usagemonitor.presentation.ui.components.ALERT_SETTINGS_QUIET_SWITCH_TEST_TAG
import com.usagemonitor.presentation.ui.components.ALERT_SETTINGS_STALLED_SWITCH_TEST_TAG
import com.usagemonitor.presentation.ui.components.ALERT_SETTINGS_STALL_THRESHOLD_TEST_TAG
import com.usagemonitor.presentation.ui.components.API_USAGE_CARD_EMOJI_TAG
import com.usagemonitor.presentation.ui.components.API_USAGE_CARD_PLAN_TAG
import com.usagemonitor.presentation.ui.components.API_USAGE_CARD_STATUS_HINT_TAG
import com.usagemonitor.presentation.ui.components.API_USAGE_CARD_STATUS_TAG
import com.usagemonitor.presentation.ui.components.AlertSettingsSection
import com.usagemonitor.presentation.ui.components.AnthropicProfileUiModel
import com.usagemonitor.presentation.ui.components.AnthropicProfileUiStatus
import com.usagemonitor.presentation.ui.components.ApiCheckboxRow
import com.usagemonitor.presentation.ui.components.ApiUsageCard
import com.usagemonitor.presentation.ui.components.AppRingArc
import com.usagemonitor.presentation.ui.components.AppTone
import com.usagemonitor.presentation.ui.components.AppUsageRing
import com.usagemonitor.presentation.ui.components.FOOTER_VERSION_TEST_TAG
import com.usagemonitor.presentation.ui.components.FOOTER_WINDOW_MODE_TEST_TAG
import com.usagemonitor.presentation.ui.components.LanguageSelector
import com.usagemonitor.presentation.ui.components.PersistentApiWarningBanner
import com.usagemonitor.presentation.ui.components.SettingsDialogContent
import com.usagemonitor.presentation.ui.components.SettingsTab
import com.usagemonitor.presentation.ui.components.TEAM_ALIAS_FIELD_TEST_TAG
import com.usagemonitor.presentation.ui.components.TeamConnectionUiState
import com.usagemonitor.presentation.ui.components.TeamIntegrationSection
import com.usagemonitor.presentation.ui.components.ThemeToggle
import com.usagemonitor.presentation.ui.components.WindowMode
import com.usagemonitor.presentation.ui.components.WindowOpacitySlider
import com.usagemonitor.presentation.ui.components.accountAccentColor
import com.usagemonitor.presentation.ui.components.apiSelectorEditKeyTestTag
import com.usagemonitor.presentation.ui.components.apiSelectorSwitchTestTag
import com.usagemonitor.presentation.ui.components.observedActivityTrackTag
import com.usagemonitor.presentation.ui.components.observedActivityValueTag
import com.usagemonitor.presentation.ui.components.quotaBlockTag
import com.usagemonitor.presentation.ui.components.quotaProgressTrackTag
import com.usagemonitor.presentation.ui.components.riskDotTooltipSubtitle
import com.usagemonitor.presentation.ui.theme.AccountAccent
import com.usagemonitor.presentation.ui.theme.AccountEmoji
import com.usagemonitor.presentation.ui.theme.AppAccents
import com.usagemonitor.presentation.ui.theme.AppThemePreset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.Instant

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

    // ── AppUsageRing ─────────────────────────────────────────────────────

    /** O anel nunca informa sozinho: a frase inteira vai na semântica. */
    @Test
    fun `AppUsageRing carries account word and quotas in its description`() = runDesktopComposeUiTest {
        setContent {
            ScreenTestTheme(isDark = true) {
                AppUsageRing(
                    arcs = listOf(
                        AppRingArc(fraction = 0.88f, tone = AppTone.CRITICAL),
                        AppRingArc(fraction = 0.09f, tone = AppTone.OK)
                    ),
                    description = "Padrão · Crítico · 5h 88% · 7d 9%"
                )
            }
        }

        onNodeWithContentDescription("Padrão · Crítico · 5h 88% · 7d 9%").assertIsDisplayed()
    }

    /**
     * Sessão ativa e atenção sob a política estática: o anel não cria animação
     * sem fim — chegar ao assert já prova que o `waitForIdle` voltou.
     */
    @Test
    fun `AppUsageRing active and attention do not animate forever without the policy`() = runDesktopComposeUiTest {
        setContent {
            ScreenTestTheme(isDark = true) {
                AppUsageRing(
                    arcs = listOf(AppRingArc(fraction = 1.4f, tone = AppTone.CRITICAL, hasForecast = false)),
                    description = "Codex · Crítico",
                    active = true,
                    attention = true
                )
            }
        }

        onNodeWithContentDescription("Codex · Crítico").assertIsDisplayed()
    }

    /** O plano da conta sai como selo ao lado do nome, e sem plano não há selo. */
    @Test
    fun `ApiUsageCard shows the account plan beside the title`() = runDesktopComposeUiTest {
        var plan by mutableStateOf<String?>("Max 20x")
        setContent {
            ScreenTestTheme(isDark = true) {
                ApiUsageCard(
                    source = ApiSource.ANTHROPIC,
                    apiName = "Anthropic — Padrão",
                    quotas = emptyList(),
                    planLabel = plan,
                    showUsageDetails = false,
                    isRefreshing = false,
                    language = AppLanguage.PT,
                    onRefresh = {},
                    onOpenHistory = {},
                    animationDelayMillis = 0
                )
            }
        }

        onNodeWithTag(API_USAGE_CARD_PLAN_TAG).assertTextEquals("Max 20x")
        plan = null
        waitForIdle()
        onNodeWithTag(API_USAGE_CARD_PLAN_TAG).assertDoesNotExist()
    }

    /** O emoji da conta (issue #287) sai ao lado da marca, e o título continua achável pelo texto. */
    @Test
    fun `ApiUsageCard shows the account emoji beside the provider mark`() = runDesktopComposeUiTest {
        var emoji by mutableStateOf<AccountEmoji?>(AccountEmoji.FOX)
        setContent {
            ScreenTestTheme(isDark = true) {
                ApiUsageCard(
                    source = ApiSource.ANTHROPIC,
                    apiName = "Anthropic — Trabalho",
                    quotas = emptyList(),
                    emoji = emoji,
                    showUsageDetails = false,
                    isRefreshing = false,
                    language = AppLanguage.PT,
                    onRefresh = {},
                    onOpenHistory = {},
                    animationDelayMillis = 0
                )
            }
        }

        onNodeWithTag(API_USAGE_CARD_EMOJI_TAG, useUnmergedTree = true).assertExists()
        onNodeWithText("Anthropic — Trabalho").assertIsDisplayed()
        emoji = null
        waitForIdle()
        onNodeWithTag(API_USAGE_CARD_EMOJI_TAG, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `ApiUsageCard shows interval and weekly quotas in the same card`() = runDesktopComposeUiTest {
        setContent {
            ScreenTestTheme(isDark = true) {
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
            ScreenTestTheme(isDark = true) {
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
            ScreenTestTheme(isDark = true) {
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
            ScreenTestTheme(isDark = true) {
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
            ScreenTestTheme(isDark = true) {
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
            ScreenTestTheme(isDark = true) {
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
            ScreenTestTheme(isDark = true) {
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
            ScreenTestTheme(isDark = true) {
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
            ScreenTestTheme(isDark = true) {
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
            ScreenTestTheme(isDark = true) {
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
            ScreenTestTheme(isDark = true) {
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
            ScreenTestTheme(isDark = true) {
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
     * Issue #195: conta em yuan aparecia com cifrão de dólar porque o mapper
     * descartava `balance_infos[].currency`. O símbolo cai no código ISO de
     * propósito — `¥` é compartilhado por CNY e JPY.
     */
    @Test
    fun `ApiUsageCard prints the balance in the account currency`() = runDesktopComposeUiTest {
        setContent {
            ScreenTestTheme(isDark = true) {
                ApiUsageCard(
                    source = ApiSource.DEEPSEEK,
                    apiName = "DeepSeek",
                    quotas = listOf(
                        QuotaInfo(
                            label = "Saldo",
                            used = 0L,
                            total = 10_000L,
                            periodEndAt = Instant.parse("2026-04-28T15:00:00Z"),
                            hasKnownResetAt = false,
                            periodType = PeriodType.INTERVAL,
                            unit = UsageUnit.CURRENCY_USD,
                            rawUsed = 10_000L,
                            rawTotal = 10_000L,
                            currencyCode = "CNY"
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

        onNodeWithText("CNY 100.00").assertIsDisplayed()
    }

    /** Mesmo desenho de saldo pré-pago do DeepSeek — fonte e acento diferentes. */
    @Test
    fun `ApiUsageCard renders OpenRouter balance`() = runDesktopComposeUiTest {
        setContent {
            ScreenTestTheme(isDark = true) {
                ApiUsageCard(
                    source = ApiSource.OPENROUTER,
                    apiName = "OpenRouter",
                    quotas = listOf(
                        QuotaInfo(
                            label = "Saldo",
                            used = 0L,
                            total = 500L,
                            periodEndAt = Instant.parse("2026-04-28T15:00:00Z"),
                            hasKnownResetAt = false,
                            periodType = PeriodType.INTERVAL,
                            unit = UsageUnit.CURRENCY_USD,
                            rawUsed = 500L,
                            rawTotal = 500L
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

        onNodeWithText("OpenRouter").assertIsDisplayed()
        onNodeWithText("Saldo").assertIsDisplayed()
        onNodeWithText("\$5.00").assertIsDisplayed()
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
            ScreenTestTheme(isDark = true) {
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
            ScreenTestTheme(isDark = true) {
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
            ScreenTestTheme(isDark = true) {
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
            ScreenTestTheme(isDark = true) {
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
    fun `ApiUsageCard opens Codex CLI sessions from the Codex card`() = runDesktopComposeUiTest {
        var opened = false

        setContent {
            ScreenTestTheme(isDark = true) {
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
                    onOpenCodexCliSessions = { opened = true }
                )
            }
        }

        onNodeWithContentDescription("Sessões Codex CLI desta conta").performClick()
        assertEquals(true, opened)
    }

    @Test
    fun `ApiUsageCard shows compact quota labels when minimized`() = runDesktopComposeUiTest {
        setContent {
            ScreenTestTheme(isDark = true) {
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
            ScreenTestTheme(isDark = true) {
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
            ScreenTestTheme(isDark = true) {
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
     * Card estreito (issue #215): sem tooltip para explicar o semáforo, a
     * mesma frase do rodapé da tooltip vira texto sempre visível — a
     * explicação não some, só o popup.
     */
    @Test
    fun `ApiUsageCard shows the risk summary as text on a narrow compact card`() = runDesktopComposeUiTest {
        val quota = QuotaInfo(
            label = "Claude 7d",
            used = 90L,
            total = 100L,
            periodEndAt = Instant.parse("2026-05-03T12:00:00Z"),
            periodType = PeriodType.WEEKLY,
            unit = UsageUnit.TOKENS
        )
        val risk = QuotaRiskSummary(level = UsageRiskLevel.WILL_EXCEED, estimatedExhaustionAt = null)

        setContent {
            ScreenTestTheme(isDark = true) {
                Box(modifier = Modifier.width(240.dp)) {
                    ApiUsageCard(
                        source = ApiSource.ANTHROPIC,
                        apiName = "Anthropic",
                        quotas = listOf(quota),
                        riskByQuotaKey = mapOf(QuotaSeriesKey(quota.label, quota.periodType) to risk),
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

        onNodeWithText(riskDotTooltipSubtitle(risk = risk, language = AppLanguage.PT)).assertIsDisplayed()
    }

    /** Mesma explicação, agora no card expandido (uma linha por cota). */
    @Test
    fun `ApiUsageCard shows the risk summary as text on a narrow expanded card`() = runDesktopComposeUiTest {
        val quota = QuotaInfo(
            label = "Claude 7d",
            used = 90L,
            total = 100L,
            periodEndAt = Instant.parse("2026-05-03T12:00:00Z"),
            periodType = PeriodType.WEEKLY,
            unit = UsageUnit.TOKENS
        )
        val risk = QuotaRiskSummary(level = UsageRiskLevel.WILL_EXCEED, estimatedExhaustionAt = null)

        setContent {
            ScreenTestTheme(isDark = true) {
                Box(modifier = Modifier.width(240.dp)) {
                    ApiUsageCard(
                        source = ApiSource.ANTHROPIC,
                        apiName = "Anthropic",
                        quotas = listOf(quota),
                        riskByQuotaKey = mapOf(QuotaSeriesKey(quota.label, quota.periodType) to risk),
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
        }

        onNodeWithText(riskDotTooltipSubtitle(risk = risk, language = AppLanguage.PT)).assertIsDisplayed()
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
            ScreenTestTheme(isDark = true) {
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
            ScreenTestTheme(isDark = true) {
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

            ScreenTestTheme(isDark = true) {
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
            ScreenTestTheme(isDark = true) {
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
            ScreenTestTheme(isDark = true) {
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
            ScreenTestTheme(isDark = true) {
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
            ScreenTestTheme(isDark = true) {
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
            ScreenTestTheme(isDark = true) {
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
            ScreenTestTheme(isDark = true) {
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
            ScreenTestTheme(isDark = true) {
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
            ScreenTestTheme(isDark = true) {
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
            ScreenTestTheme(isDark = true) {
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
            ScreenTestTheme(isDark = true) {
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
            ScreenTestTheme(isDark = true) {
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
            ScreenTestTheme(isDark = true) {
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
            ScreenTestTheme(isDark = true) {
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
            ScreenTestTheme(isDark = true) {
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
            ScreenTestTheme(isDark = true) {
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
            ScreenTestTheme(isDark = true) {
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
            ScreenTestTheme(isDark = true) {
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
            ScreenTestTheme(isDark = true) {
                ApiCheckboxRow(
                    api = ApiSource.MINIMAX,
                    isChecked = true,
                    hasConfiguredApiKey = true,
                    onCheckedChange = { toggled = true },
                    onEditApiKey = { edited = true }
                )
            }
        }

        // O rótulo carrega o nome da fonte: são três lápis no mesmo painel, e
        // três `contentDescription` iguais anunciariam ações indistinguíveis.
        onNodeWithContentDescription("Gerenciar chave — MiniMax").performClick()

        assertEquals(true, edited)
        assertEquals(false, toggled)
        onNodeWithTag(apiSelectorSwitchTestTag(ApiSource.MINIMAX)).assertIsOn()
    }

    /**
     * Issue #125: o painel tem três lápis, e o que os separa — o nome da fonte —
     * está num nó irmão que o leitor de tela não lê junto com a ação.
     */
    @Test
    fun `ApiSelector gives each edit icon a distinct accessible label`() = runDesktopComposeUiTest {
        setContent {
            ScreenTestTheme(isDark = true) {
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
                    initialTab = SettingsTab.APIS
                )
            }
        }

        onNodeWithContentDescription("Gerenciar chave — MiniMax").assertExists()
        onNodeWithContentDescription("Gerenciar chave — DeepSeek").assertExists()
        onNodeWithContentDescription("Gerenciar chave — OpenCode Go").assertExists()
        onNodeWithContentDescription("Gerenciar chave — OpenRouter").assertExists()
    }

    // ── ThemeToggle ───────────────────────────────────────────────────────

    @Test
    fun `ThemeToggle shows dark label when isDark is true`() = runDesktopComposeUiTest {
        setContent {
            ScreenTestTheme(isDark = true) {
                ThemeToggle(isDark = true, onToggle = {})
            }
        }

        onNodeWithText("Escuro").assertIsSelected()
    }

    @Test
    fun `ThemeToggle shows light label when isDark is false`() = runDesktopComposeUiTest {
        setContent {
            ScreenTestTheme(isDark = false) {
                ThemeToggle(isDark = false, onToggle = {})
            }
        }

        onNodeWithText("Claro").assertIsSelected()
    }

    @Test
    fun `ThemeToggle calls onToggle when clicked`() = runDesktopComposeUiTest {
        var toggled = false

        setContent {
            ScreenTestTheme(isDark = true) {
                ThemeToggle(isDark = true, onToggle = { toggled = true })
            }
        }

        onNodeWithText("Claro").performClick()
        assertEquals(true, toggled)
    }

    // ── FooterBar ───────────────────────────────────────────────────────

    /**
     * O menu de modos (issue #187) mora no rodapé, e o rodapé mora na
     * `DashboardScreen`: sem este repasse o controle existiria no `FooterBar` e
     * nunca chegaria à tela. `null` no callback é o estado dos geradores de
     * captura, que montam a tela sem despachar nada.
     */
    @Test
    fun `DashboardScreen repassa o menu de modos de janela ao rodape`() = runDesktopComposeUiTest {
        val enabledApis = MutableStateFlow(setOf(ApiSource.ANTHROPIC))
        val viewModel = emptyDashboardViewModel(enabledApis)
        viewModel.cancelCountdown()
        val chosen = mutableListOf<WindowMode>()

        setContent {
            ScreenTestTheme(isDark = true) {
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
                    countdownUpdatesEnabled = false,
                    windowMode = WindowMode.STANDARD,
                    onWindowModeChange = { mode -> chosen += mode }
                )
            }
        }

        onNodeWithTag(FOOTER_WINDOW_MODE_TEST_TAG).performClick()
        waitForIdle()
        onNodeWithText("Barra HUD").performClick()
        waitForIdle()

        assertEquals(listOf(WindowMode.HUD), chosen)
    }

    @Test
    fun `DashboardScreen shows refresh warning dialog and only refreshes on confirm`() = runDesktopComposeUiTest {
        val enabledApis = MutableStateFlow(setOf(ApiSource.ANTHROPIC, ApiSource.MINIMAX))
        val fetchCount = java.util.concurrent.atomic.AtomicInteger(0)
        val viewModel = successDashboardViewModelCountingFetches(enabledApis, fetchCount)
        viewModel.cancelCountdown()

        setContent {
            ScreenTestTheme(isDark = true) {
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
            ScreenTestTheme(isDark = true) {
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
            ScreenTestTheme(isDark = true) {
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
        onNodeWithText("Baixar atualização").assertIsDisplayed()
        viewModel.onDestroy()
    }

    @Test
    // O botão da faixa abre a release (issue #291): a faixa em si deixou de ser
    // clicável quando a ação voltou a ser um botão.
    fun `DashboardScreen opens release page from update banner action`() = runDesktopComposeUiTest {
        val enabledApis = MutableStateFlow(emptySet<ApiSource>())
        var opened = false
        val viewModel = dashboardViewModelWithAvailableUpdateAction(enabledApis) {
            opened = true
        }
        viewModel.cancelCountdown()

        setContent {
            ScreenTestTheme(isDark = true) {
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

        onNodeWithTag(APP_UPDATE_BANNER_ACTION_TAG).performClick()
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
            ScreenTestTheme(isDark = true) {
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
            ScreenTestTheme(isDark = true) {
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

    @Test
    fun `WindowOpacitySlider reports the snapped percent and updates its label`() = runDesktopComposeUiTest {
        var lastReportedPercent = -1

        setContent {
            ScreenTestTheme(isDark = true) {
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
            ScreenTestTheme(isDark = true) {
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

    /**
     * A cor da conta (issue #275) é escolhida na parte expandida do perfil, entre
     * "Padrão" e as oito da paleta, e a escolha corrente fica marcada.
     */
    @Test
    fun `accounts tab picks an account color and marks the current one`() = runDesktopComposeUiTest {
        val picked = mutableListOf<Pair<String, AccountAccent?>>()
        setContent {
            ScreenTestTheme(isDark = true) {
                var color by remember { mutableStateOf<AccountAccent?>(null) }
                SettingsDialogContent(
                    currentTheme = AppThemePreset.OBSIDIANA_DARK,
                    currentLanguage = AppLanguage.PT,
                    enabledApis = setOf(ApiSource.ANTHROPIC),
                    autoStartEnabled = false,
                    onThemeChange = {},
                    onLanguageChange = {},
                    onAutoStartChange = {},
                    onApiToggle = { _, _ -> },
                    anthropicProfiles = listOf(
                        AnthropicProfileUiModel(
                            id = "work",
                            label = "Trabalho",
                            path = "C:\\Users\\test\\.claude-work",
                            enabled = true,
                            removable = true,
                            identityLabel = "work@example.com",
                            status = AnthropicProfileUiStatus.READY,
                            color = color
                        )
                    ),
                    expandedProfileId = "work",
                    onAnthropicProfileColorChange = { profileId, accent ->
                        picked += profileId to accent
                        color = accent
                    },
                    initialTab = SettingsTab.ACCOUNTS
                )
            }
        }

        onNodeWithTag(ACCOUNT_COLOR_OPTION_TEST_TAG_PREFIX + "work_DEFAULT").performScrollTo().assertIsSelected()
        onNodeWithTag(ACCOUNT_COLOR_OPTION_TEST_TAG_PREFIX + "work_VIOLET").performScrollTo().performClick()
        waitForIdle()

        assertEquals(listOf<Pair<String, AccountAccent?>>("work" to AccountAccent.VIOLET), picked)
        onNodeWithTag(ACCOUNT_COLOR_OPTION_TEST_TAG_PREFIX + "work_VIOLET").assertIsSelected()
        onNodeWithTag(ACCOUNT_COLOR_OPTION_TEST_TAG_PREFIX + "work_DEFAULT").assertIsNotSelected()
        onNodeWithText("Violeta").assertExists()
    }

    /**
     * O emoji da conta (issue #287) é escolhido na parte expandida do perfil,
     * entre "Nenhum" e os do conjunto fixo, cada opção nomeada na semântica; a
     * escolha corrente fica marcada e aparece na linha do perfil.
     */
    @Test
    fun `accounts tab picks an account emoji and marks the current one`() = runDesktopComposeUiTest {
        val picked = mutableListOf<Pair<String, AccountEmoji?>>()
        setContent {
            ScreenTestTheme(isDark = true) {
                var emoji by remember { mutableStateOf<AccountEmoji?>(null) }
                SettingsDialogContent(
                    currentTheme = AppThemePreset.OBSIDIANA_DARK,
                    currentLanguage = AppLanguage.PT,
                    enabledApis = setOf(ApiSource.ANTHROPIC),
                    autoStartEnabled = false,
                    onThemeChange = {},
                    onLanguageChange = {},
                    onAutoStartChange = {},
                    onApiToggle = { _, _ -> },
                    anthropicProfiles = listOf(
                        AnthropicProfileUiModel(
                            id = "work",
                            label = "Trabalho",
                            path = "C:\\Users\\test\\.claude-work",
                            enabled = true,
                            removable = true,
                            identityLabel = "work@example.com",
                            status = AnthropicProfileUiStatus.READY,
                            emoji = emoji
                        )
                    ),
                    expandedProfileId = "work",
                    onAnthropicProfileEmojiChange = { profileId, chosen ->
                        picked += profileId to chosen
                        emoji = chosen
                    },
                    initialTab = SettingsTab.ACCOUNTS
                )
            }
        }

        onNodeWithTag(ACCOUNT_EMOJI_OPTION_TEST_TAG_PREFIX + "work_NONE").performScrollTo().assertIsSelected()
        onNodeWithTag(ACCOUNT_EMOJI_OPTION_TEST_TAG_PREFIX + "work_BRIEFCASE")
            .performScrollTo()
            .assertContentDescriptionEquals("Maleta")
            .performClick()
        waitForIdle()

        assertEquals(listOf<Pair<String, AccountEmoji?>>("work" to AccountEmoji.BRIEFCASE), picked)
        onNodeWithTag(ACCOUNT_EMOJI_OPTION_TEST_TAG_PREFIX + "work_BRIEFCASE").assertIsSelected()
        onNodeWithTag(ACCOUNT_EMOJI_OPTION_TEST_TAG_PREFIX + "work_NONE").assertIsNotSelected()
    }

    /** A dona única da cor: escolha da conta vence o acento da fonte, e sem escolha fica o da fonte. */
    @Test
    fun `account accent uses the chosen color and falls back to the source accent`() = runDesktopComposeUiTest {
        val work = UsageTargetKey(ApiSource.ANTHROPIC, "work")
        val personal = UsageTargetKey(ApiSource.ANTHROPIC, "personal")
        val codex = UsageTargetKey.forSource(ApiSource.CODEX)
        val colors = mapOf("work" to AccountAccent.ROSE)
        var resolved = emptyList<Color>()
        var expected = emptyList<Color>()
        setContent {
            ScreenTestTheme(isDark = true) {
                resolved = listOf(
                    accountAccentColor(work, colors),
                    accountAccentColor(personal, colors),
                    accountAccentColor(codex, colors)
                )
                expected = listOf(AccountAccent.ROSE.dark, AppAccents.current.anthropic, AppAccents.current.codex)
            }
        }
        waitForIdle()
        assertEquals(expected, resolved)
    }

    @Test
    fun `AlertSettingsSection toggles a threshold without dropping the others`() = runDesktopComposeUiTest {
        var current = UsageAlertSettings.DEFAULT

        setContent {
            ScreenTestTheme(isDark = true) {
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
            ScreenTestTheme(isDark = true) {
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
    fun `AlertSettingsSection picks the stall threshold and hides it when disabled`() = runDesktopComposeUiTest {
        var current = UsageAlertSettings.DEFAULT

        setContent {
            ScreenTestTheme(isDark = true) {
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

        // Ligado por padrão: o segmentado do limiar já está na tela.
        onNodeWithTag(ALERT_SETTINGS_STALL_THRESHOLD_TEST_TAG).assertIsDisplayed()

        onNodeWithText("30min").performClick()
        assertEquals(30L * 60 * 1_000, current.effectiveStallThresholdMillis)

        onNodeWithText("4h").performClick()
        assertEquals(4L * 60 * 60 * 1_000, current.effectiveStallThresholdMillis)

        // Desligado, o limiar some: controle sem efeito é pior que controle ausente.
        onNodeWithTag(ALERT_SETTINGS_STALLED_SWITCH_TEST_TAG).performClick()
        onAllNodesWithTag(ALERT_SETTINGS_STALL_THRESHOLD_TEST_TAG).assertCountEquals(0)
    }

    @Test
    fun `AlertSettingsSection reveals the quiet range only when it is enabled`() = runDesktopComposeUiTest {
        setContent {
            ScreenTestTheme(isDark = true) {
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

    // ── TeamIntegrationSection ──────────────────────────────────────────

    @Test
    fun `TeamIntegrationSection commits the alias after the typing pause`() = runDesktopComposeUiTest {
        var committed: String? = null

        setContent {
            ScreenTestTheme(isDark = true) {
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
            ScreenTestTheme(isDark = true) {
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
            ScreenTestTheme(isDark = true) {
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
            ScreenTestTheme(isDark = true) {
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

    /**
     * Fora do Codex, `SOURCE_UNSTABLE` é a marca da última leitura guardada depois
     * de uma falha (issue #267). A frase do contrato do Codex não pode aparecer no
     * card do Cursor, e cada franquia do ciclo tem o nome no título.
     */
    @Test
    fun `Cursor card names each allowance and explains a stale reading`() = runDesktopComposeUiTest {
        setContent {
            ScreenTestTheme(isDark = true) {
                ApiUsageCard(
                    source = ApiSource.CURSOR,
                    apiName = "Cursor",
                    quotas = listOf(
                        QuotaInfo(
                            label = CursorQuotaLabels.AUTO,
                            used = 34L,
                            total = 100L,
                            periodEndAt = Instant.parse("2026-10-01T00:00:00Z"),
                            periodType = PeriodType.MONTHLY,
                            unit = UsageUnit.PERCENTAGE
                        ),
                        QuotaInfo(
                            label = CursorQuotaLabels.API,
                            used = 11L,
                            total = 100L,
                            periodEndAt = Instant.parse("2026-10-01T00:00:00Z"),
                            periodType = PeriodType.MONTHLY,
                            unit = UsageUnit.PERCENTAGE
                        )
                    ),
                    notices = setOf(ApiUsageNotice.SOURCE_UNSTABLE),
                    showUsageDetails = false,
                    isRefreshing = false,
                    language = AppLanguage.PT,
                    animationDelayMillis = 0,
                    onRefresh = {},
                    now = Instant.parse("2026-09-24T10:00:00Z")
                )
            }
        }

        onNodeWithText("Auto · Mensal").assertIsDisplayed()
        onNodeWithText("API · Mensal").assertIsDisplayed()
        onNodeWithContentDescription("última leitura válida", substring = true).assertIsDisplayed()
        onAllNodesWithContentDescription("Codex", substring = true).assertCountEquals(0)
    }

    /**
     * O Antigravity tem um limite semanal **por grupo de modelos**. O título do
     * bloco sai do `periodType`, e sem o grupo os dois blocos diriam "Semanal".
     */
    @Test
    fun `Antigravity card renders one percentage quota per model group`() = runDesktopComposeUiTest {
        setContent {
            ScreenTestTheme(isDark = true) {
                ApiUsageCard(
                    source = ApiSource.ANTIGRAVITY,
                    apiName = "Antigravity CLI",
                    quotas = listOf(
                        QuotaInfo(
                            label = AntigravityQuotaLabels.label("Gemini", "7d"),
                            used = 4L,
                            total = 100L,
                            periodEndAt = Instant.parse("2026-09-30T21:57:08Z"),
                            periodType = PeriodType.WEEKLY,
                            unit = UsageUnit.PERCENTAGE
                        ),
                        QuotaInfo(
                            label = AntigravityQuotaLabels.label("Claude/GPT", "7d"),
                            used = 0L,
                            total = 100L,
                            periodEndAt = Instant.parse("2100-01-01T00:00:00Z"),
                            hasKnownResetAt = false,
                            periodType = PeriodType.WEEKLY,
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

        onNodeWithText("Antigravity CLI").assertIsDisplayed()
        onNodeWithText("Gemini · Semanal").assertIsDisplayed()
        onNodeWithText("Claude/GPT · Semanal").assertIsDisplayed()
        onNodeWithText("4%").assertIsDisplayed()
        onAllNodesWithText("Semanal").assertCountEquals(0)
    }

    @Test
    fun `OpenCode and Kilo observed activity values stay horizontal in narrow expanded cards`() = runDesktopComposeUiTest(width = 260, height = 1_200) {
        mainClock.autoAdvance = false
        setContent {
            ScreenTestTheme(isDark = true) {
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

    // ── LanguageSelector ─────────────────────────────────────────────────

    @Test
    fun `LanguageSelector displays PT and EN options`() = runDesktopComposeUiTest {
        setContent {
            ScreenTestTheme(isDark = true) {
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
            ScreenTestTheme(isDark = true) {
                LanguageSelector(
                    currentLanguage = AppLanguage.PT,
                    onLanguageChange = { selected = it }
                )
            }
        }

        onNodeWithText("EN").performClick()
        assertEquals(AppLanguage.EN, selected)
    }
}

/** Integração ligada e completa: é o estado em que a seção mostra os campos. */
private val ACTIVE_TEAM_SETTINGS = TeamIntegrationSettings(
    enabled = true,
    serverUrl = "http://localhost:3000",
    apiKey = "chave-de-time-com-tamanho-suficiente",
    alias = "EDILSON",
    deviceId = "device-1"
)

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
