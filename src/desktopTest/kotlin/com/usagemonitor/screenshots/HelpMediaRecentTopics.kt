package com.usagemonitor.screenshots

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.CliSessionRange
import com.usagemonitor.domain.entity.ComparisonRow
import com.usagemonitor.domain.entity.OutputThroughput
import com.usagemonitor.domain.entity.TelegramChat
import com.usagemonitor.presentation.AnalysisFixture
import com.usagemonitor.presentation.ui.localized
import com.usagemonitor.presentation.ui.AnalysisContent
import com.usagemonitor.presentation.ui.ComparisonContent
import com.usagemonitor.presentation.ui.components.AppTone
import com.usagemonitor.presentation.ui.components.SettingsDialogContent
import com.usagemonitor.presentation.ui.components.SettingsTab
import com.usagemonitor.presentation.ui.components.TelegramBotSection
import com.usagemonitor.presentation.ui.components.TelegramBotSectionModel
import com.usagemonitor.presentation.ui.components.WebAccessSection
import com.usagemonitor.presentation.ui.components.WebAccessSectionModel
import com.usagemonitor.presentation.ui.components.WebAccessUiStatus
import com.usagemonitor.presentation.ui.help.HelpTopic
import com.usagemonitor.presentation.ui.theme.AppSpacing
import com.usagemonitor.presentation.ui.theme.AppThemePreset
import com.usagemonitor.presentation.viewmodel.AnalysisUiState
import com.usagemonitor.presentation.viewmodel.ComparisonUiState
import java.io.File
import kotlin.time.Duration.Companion.minutes

/**
 * Demos dos tópicos da ajuda que vieram com a #411 e a regravação de
 * "Integrações e cotas".
 *
 * Arquivo próprio para o gerador principal não crescer mais. Mesmo motor e mesma
 * regra: composables reais do app, dados sintéticos e o ponteiro por cima. A
 * demo de integrações deixou de gravar a grade de cards — a grade só existe nos
 * testes desde que a HUD virou a única tela, e a demo mostrava um layout que o
 * usuário não tem. Agora mostra a aba "APIs", que é onde o tópico manda ir.
 */

/** Integrações: a aba "APIs" das Configurações, com uma integração sendo ligada. */
internal fun recordIntegrations(options: HelpMediaOptions) {
    val state = DemoState(contentHeight = 420.dp)
    var enabled by mutableStateOf(ScreenshotFixtures.enabledApis)

    record(options, HelpTopic.DASHBOARD, state) { recorder ->
        recorder.setContent {
            DemoScene(state) {
                SettingsDialogContent(
                    currentTheme = AppThemePreset.OBSIDIANA_DARK,
                    currentLanguage = options.language,
                    enabledApis = enabled,
                    autoStartEnabled = true,
                    windowOpacityPercent = 92,
                    onThemeChange = {},
                    onLanguageChange = {},
                    onAutoStartChange = {},
                    onApiToggle = { _, _ -> },
                    anthropicProfiles = ScreenshotFixtures.anthropicProfiles,
                    initialTab = SettingsTab.APIS
                )
            }
        }

        recorder.animate(800) {}
        recorder.hold(1_200)
        recorder.moveCursor(state.cursor, x = INTEGRATION_SWITCH_X.dp, y = INTEGRATION_SWITCH_Y.dp, durationMillis = 600)
        recorder.click(state.cursor) { enabled = enabled + ApiSource.MINIMAX }
        recorder.animate(500) {}
        recorder.hold(1_800)
    }
}

/** Contas: a aba "Contas", com o editor de um perfil aberto por "Editar". */
internal fun recordAccounts(options: HelpMediaOptions) {
    val state = DemoState(contentHeight = 420.dp)
    var expanded by mutableStateOf<String?>(null)

    record(options, HelpTopic.ACCOUNTS, state) { recorder ->
        recorder.setContent {
            DemoScene(state) {
                SettingsDialogContent(
                    currentTheme = AppThemePreset.OBSIDIANA_DARK,
                    currentLanguage = options.language,
                    enabledApis = ScreenshotFixtures.enabledApis,
                    autoStartEnabled = true,
                    windowOpacityPercent = 92,
                    onThemeChange = {},
                    onLanguageChange = {},
                    onAutoStartChange = {},
                    onApiToggle = { _, _ -> },
                    anthropicProfiles = ScreenshotFixtures.anthropicProfiles,
                    expandedProfileId = expanded,
                    initialTab = SettingsTab.ACCOUNTS
                )
            }
        }

        recorder.animate(800) {}
        recorder.hold(1_200)
        recorder.moveCursor(state.cursor, x = ACCOUNTS_EDIT_X.dp, y = ACCOUNTS_EDIT_Y.dp, durationMillis = 600)
        recorder.click(state.cursor) { expanded = ScreenshotFixtures.anthropicProfiles.first().id }
        recorder.animate(600) {}
        recorder.hold(2_000)
    }
}

/** Análise da sessão: o painel único ao vivo (rodada AD) e a leitura seguinte chegando sozinha. */
internal fun recordSessionAnalysis(options: HelpMediaOptions) {
    val state = DemoState()
    var analysis by mutableStateOf(AnalysisFixture.analysis)

    record(options, HelpTopic.SESSION_ANALYSIS, state) { recorder ->
        recorder.setContent {
            DemoScene(state) {
                AnalysisContent(
                    state = AnalysisUiState.Success(analysis),
                    accountLabel = "Anthropic · Padrão",
                    language = options.language,
                    onExportPdf = {}
                )
            }
        }

        recorder.animate(800) {}
        recorder.hold(1_400)
        // O ponteiro vai até o selo "Ao vivo": é ele que diz de quando é a leitura.
        recorder.moveCursor(state.cursor, x = ANALYSIS_LIVE_X.dp, y = ANALYSIS_LIVE_Y.dp, durationMillis = 600)
        // A coleta seguinte: a janela relê sozinha, a barra da 5h anda e a hora no selo também.
        recorder.animate(600) {
            analysis = AnalysisFixture.analysis.copy(
                generatedAt = AnalysisFixture.analysis.generatedAt + 1.minutes,
                fiveHour = AnalysisFixture.analysis.fiveHour?.copy(percent = 71, projectedAtReset = 92)
            )
        }
        recorder.hold(2_000)
    }
}

/** Comparação: o mapa de calor e a troca de recorte. */
internal fun recordComparison(options: HelpMediaOptions) {
    val state = DemoState()
    var range by mutableStateOf(CliSessionRange.LAST_7D)

    record(options, HelpTopic.COMPARISON, state) { recorder ->
        recorder.setContent {
            DemoScene(state) {
                ComparisonContent(
                    state = ComparisonUiState.Success(comparisonRows(range), range, ScreenshotFixtures.NOW),
                    language = options.language,
                    onSelectRange = {},
                    onExportReport = {},
                    onRetry = {}
                )
            }
        }

        recorder.animate(800) {}
        recorder.hold(1_400)
        recorder.moveCursor(state.cursor, x = COMPARISON_RANGE_X.dp, y = COMPARISON_RANGE_Y.dp, durationMillis = 600)
        recorder.click(state.cursor) { range = CliSessionRange.LAST_30D }
        recorder.animate(500) {}
        recorder.hold(2_000)
    }
}

/** Bot do Telegram: os três passos, com o pareamento gerando o código. */
internal fun recordTelegramBot(options: HelpMediaOptions) {
    val state = DemoState()
    var code by mutableStateOf<String?>(null)

    record(options, HelpTopic.TELEGRAM_BOT, state) { recorder ->
        recorder.setContent {
            DemoScene(state) {
                Box(modifier = Modifier.fillMaxSize().padding(AppSpacing.lg)) {
                    TelegramBotSection(telegramModel(code, options.language), options.language)
                }
            }
        }

        recorder.animate(800) {}
        recorder.hold(1_200)
        recorder.moveCursor(state.cursor, x = TELEGRAM_PAIR_X.dp, y = TELEGRAM_PAIR_Y.dp, durationMillis = 600)
        recorder.click(state.cursor) { code = "AB12CD" }
        recorder.animate(500) {}
        recorder.hold(2_200)
    }
}

/** HUD no navegador: o interruptor ligado e o endereço aparecendo. */
internal fun recordWebAccess(options: HelpMediaOptions) {
    val state = DemoState()
    var enabled by mutableStateOf(false)

    record(options, HelpTopic.WEB_ACCESS, state) { recorder ->
        recorder.setContent {
            DemoScene(state) {
                Box(modifier = Modifier.fillMaxSize().padding(AppSpacing.lg)) {
                    WebAccessSection(webAccessModel(enabled), options.language)
                }
            }
        }

        recorder.animate(800) {}
        recorder.hold(1_200)
        recorder.moveCursor(state.cursor, x = WEB_SWITCH_X.dp, y = WEB_SWITCH_Y.dp, durationMillis = 600)
        recorder.click(state.cursor) { enabled = true }
        recorder.animate(500) {}
        recorder.hold(2_200)
    }
}

// --- Dados sintéticos ---------------------------------------------------------

/** Mesmas linhas do teste da tela; no recorte de 30 dias os volumes crescem. */
private fun comparisonRows(range: CliSessionRange): List<ComparisonRow> {
    val factor = if (range == CliSessionRange.LAST_30D) 4 else 1
    return listOf(
        ComparisonRow(ApiSource.ANTHROPIC, "claude-opus-5-5", 112 * factor, 6_320_000L * factor, 9_100_000L * factor, 0, 0.96, OutputThroughput(94_000, 1_000_000), 68),
        ComparisonRow(ApiSource.ANTHROPIC, "claude-sonnet-5", 61 * factor, 2_540_000L * factor, 2_310_000L * factor, 0, 0.95, OutputThroughput(118_000, 1_000_000), 68),
        ComparisonRow(ApiSource.MINIMAX, "MiniMax", null, null, null, 0, null, null, 22),
        ComparisonRow(ApiSource.CODEX, "gpt-5.6-luna", 73 * factor, 3_100_000L * factor, null, 0, 0.88, OutputThroughput(33_000, 1_000_000), 41)
    )
}

// Os textos de status são os do `TelegramBotActions`, em cada idioma.
private fun telegramModel(code: String?, language: AppLanguage) = TelegramBotSectionModel(
    enabled = true,
    token = "123:abc",
    statusLabel = localized(language, "Conectado como @usage_monitor_bot · 1 conversa(s)", "Connected as @usage_monitor_bot · 1 chat(s)"),
    statusTone = AppTone.OK,
    chats = listOf(TelegramChat(51L, "@edilson")),
    pairingCode = code,
    pairingHint = code?.let { localized(language, "Vale por mais 10 min.", "Valid for 10 more min.") },
    statusPreview = "Usage Monitor\ncoleta 11:42 BRT · há 1 min\n\nAnthropic · Padrão — Crítico\nClaude 5h · reinicia 13:05\n▰▰▰▰▰▰▱▱▱▱  68%",
    onEnabledChange = {},
    onTokenChange = {},
    onStartPairing = {},
    onRemoveChat = {},
    onSendTest = {},
    connected = true
)

private fun webAccessModel(enabled: Boolean) = WebAccessSectionModel(
    enabled = enabled,
    portText = "8787",
    status = if (enabled) WebAccessUiStatus.RUNNING else WebAccessUiStatus.STOPPED,
    urls = if (enabled) listOf("http://192.168.0.12:8787/?t=7f3c9a2e") else emptyList(),
    failureMessage = null,
    onEnabledChange = {},
    onPortChange = {},
    onRegenerateToken = {}
)

// Pontas do ponteiro, em dp da cena de 1000×420. Medidas no quadro gerado: o
// clique tem de cair no controle que a demo diz acionar.
private const val INTEGRATION_SWITCH_X = 957
private const val INTEGRATION_SWITCH_Y = 107
private const val ACCOUNTS_EDIT_X = 958
private const val ACCOUNTS_EDIT_Y = 97
private const val COMPARISON_RANGE_X = 771
private const val ANALYSIS_LIVE_X = 786
private const val ANALYSIS_LIVE_Y = 33
private const val COMPARISON_RANGE_Y = 34
private const val TELEGRAM_PAIR_X = 115
private const val TELEGRAM_PAIR_Y = 272
private const val WEB_SWITCH_X = 956
private const val WEB_SWITCH_Y = 34
