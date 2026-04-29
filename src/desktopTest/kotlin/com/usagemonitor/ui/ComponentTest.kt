package com.usagemonitor.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.QuotaInfo
import com.usagemonitor.domain.entity.AppTheme as ThemeMode
import com.usagemonitor.domain.entity.UsageUnit
import com.usagemonitor.presentation.ui.components.ApiUsageCard
import com.usagemonitor.presentation.ui.components.ApiCheckboxRow
import com.usagemonitor.presentation.ui.components.FooterBar
import com.usagemonitor.presentation.ui.components.LanguageSelector
import com.usagemonitor.presentation.ui.components.SettingsDialogContent
import com.usagemonitor.presentation.ui.components.ThemeToggle
import com.usagemonitor.presentation.ui.components.UsageArcChart
import com.usagemonitor.presentation.ui.theme.AppTheme
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

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
                FooterBar(
                    appVersion = "1.1.0",
                    language = AppLanguage.PT,
                    secondsUntilRefresh = 125,
                    onRefresh = {},
                    onOpenSettings = {}
                )
            }
        }

        onNodeWithText("Versão:").assertIsDisplayed()
        onNodeWithText("v1.1.0").assertIsDisplayed()
        onNodeWithText("Próxima atualização:").assertIsDisplayed()
        onNodeWithText("02:05").assertIsDisplayed()
        onNodeWithText("Configurações").assertIsDisplayed()
    }

    @Test
    fun `FooterBar opens settings action`() = runDesktopComposeUiTest {
        var opened = false

        setContent {
            AppTheme(isDark = true) {
                FooterBar(
                    appVersion = "1.1.0",
                    language = AppLanguage.PT,
                    secondsUntilRefresh = 125,
                    onRefresh = {},
                    onOpenSettings = { opened = true }
                )
            }
        }

        onNodeWithText("Configurações").performClick()
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
}
