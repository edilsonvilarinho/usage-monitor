package com.usagemonitor.screenshots

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.AccountCreditUsage
import com.usagemonitor.domain.entity.CliSessionRange
import com.usagemonitor.domain.entity.CliUsageBreakdown
import com.usagemonitor.domain.entity.MonthlyBudgetStatus
import com.usagemonitor.domain.entity.UsageAlertSettings
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.presentation.ui.CliSessionsContent
import com.usagemonitor.presentation.ui.HistoryScreen
import com.usagemonitor.presentation.ui.TeamUsageContent
import com.usagemonitor.presentation.ui.components.AlertSettingsSection
import com.usagemonitor.presentation.ui.components.FooterBar
import com.usagemonitor.presentation.ui.components.ResponsiveDashboardCardGrid
import com.usagemonitor.presentation.ui.help.HelpCatalog
import com.usagemonitor.presentation.ui.theme.AppSpacing
import com.usagemonitor.presentation.ui.help.HelpTopic
import com.usagemonitor.presentation.viewmodel.CliExportOutcome
import com.usagemonitor.presentation.viewmodel.CliSessionsUiState
import com.usagemonitor.presentation.viewmodel.CliSessionsView
import com.usagemonitor.presentation.viewmodel.TeamUsageUiState
import kotlinx.datetime.Instant
import java.io.File

/**
 * Gera as demos da janela de ajuda em `src/desktopMain/resources/help/`
 * (issue #184), com o mesmo motor de `img/tour.gif`: composables reais, dados
 * sintéticos de [ScreenshotFixtures] e o ponteiro sintético desenhado por cima.
 *
 * **A cena tem a largura de uma janela real, não a da faixa do modal.** As telas
 * deste app têm orçamento de coluna de ~1000dp — a de presença chega a exigir
 * 1030dp de janela —, e gravá-las estreitas mostraria um layout que o app não
 * tem. Em compensação a faixa do modal é larga (420dp de altura, ~930dp úteis
 * numa janela de 1180), então a demo é exibida perto de 1:1 e o texto continua
 * legível. Reduzir a gravação pela metade tornaria ilegível justamente o rótulo
 * que a demo existe para apontar.
 *
 * Rodar com `gradlew.bat generateHelpMedia`.
 */

private const val WIDTH_DP = 1_000
private const val HEIGHT_DP = 420

fun main(args: Array<String>) {
    val outputDir = File(args.firstOrNull() ?: "src/desktopMain/resources/help")
    outputDir.mkdirs()

    recordDashboard(outputDir)
    recordHistory(outputDir)
    recordCliSessions(outputDir)
    recordBreakdown(outputDir)
    recordBudget(outputDir)
    recordAlerts(outputDir)
    recordExport(outputDir)
    recordTeam(outputDir)

    println("Demos geradas em ${outputDir.absolutePath}")
}

// --- Roteiros ----------------------------------------------------------------

/**
 * Dashboard: a grade de cards e o rodapé, com uma coleta manual acontecendo.
 *
 * O rodapé entra na cena porque o primeiro passo de ativação deste tópico manda
 * abrir as Configurações pela engrenagem que mora nele.
 */
private fun recordDashboard(outputDir: File) {
    val state = DemoState()
    var refreshing by mutableStateOf(emptySet<UsageTargetKey>())

    record(outputDir, HelpTopic.DASHBOARD, state) { recorder ->
        recorder.setContent {
            DemoScene(state) {
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
                            refreshingTargets = refreshing,
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
                        // O contador não pode andar: um laço de um segundo por tique
                        // faria cada passada produzir quadros diferentes.
                        countdownUpdatesEnabled = false
                    )
                }
            }
        }

        // Os cards entram com atraso próprio por índice mais o fade deles.
        recorder.animate(1_300) {}
        recorder.hold(900)

        // A ponta do ponteiro cai sobre o botão de atualizar do primeiro card.
        // Medido no quadro gerado: em 462dp ela apontava para o de minimizar,
        // que é o vizinho, e a demo mostrava um clique num botão e a reação de
        // outro.
        recorder.moveCursor(state.cursor, x = 436.dp, y = 36.dp, durationMillis = 500)
        recorder.click(state.cursor) {
            refreshing = setOf(ScreenshotFixtures.primaryAnthropicTarget)
        }
        recorder.animate(800) {}
        refreshing = emptySet()
        recorder.hold(1_200)
    }
}

/** Histórico: o gráfico do intervalo e, deslocando, a previsão de esgotamento. */
private fun recordHistory(outputDir: File) {
    val state = DemoState(contentHeight = 900.dp)
    val historyViewModel = fixedHistoryViewModel()

    try {
        record(outputDir, HelpTopic.HISTORY, state) { recorder ->
            recorder.setContent {
                DemoScene(state) {
                    HistoryScreen(
                        viewModel = historyViewModel,
                        language = AppLanguage.PT,
                        onBack = {},
                        focusedSource = ApiSource.ANTHROPIC
                    )
                }
            }

            recorder.animate(1_000) {}
            recorder.hold(1_300)
            recorder.panTo(state, to = 300.dp, durationMillis = 700)
            recorder.hold(1_500)
        }
    } finally {
        historyViewModel.onDestroy()
    }
}

/** Sessões CLI: o cabeçalho da janela e a lista, percorrida de cima a baixo. */
private fun recordCliSessions(outputDir: File) {
    val state = DemoState(contentHeight = 520.dp)

    record(outputDir, HelpTopic.CLI_SESSIONS, state) { recorder ->
        recorder.setContent {
            DemoScene(state) {
                CliSessionsContent(
                    state = sessionsState(),
                    language = AppLanguage.PT,
                    onSelectRange = {},
                    onOpenSession = {},
                    onCloseDetail = {}
                )
            }
        }

        recorder.animate(900) {}
        recorder.hold(1_400)
        recorder.panTo(state, to = 78.dp, durationMillis = 600)
        recorder.hold(1_500)
    }
}

/** Resumo por eixo: os totais da janela e, deslocando, os baldes do eixo. */
private fun recordBreakdown(outputDir: File) {
    val state = DemoState(contentHeight = 700.dp)

    record(outputDir, HelpTopic.BREAKDOWN, state) { recorder ->
        recorder.setContent {
            DemoScene(state) {
                CliSessionsContent(
                    state = sessionsState(
                        view = CliSessionsView.BREAKDOWN,
                        breakdown = ScreenshotFixtures.cliBreakdown
                    ),
                    language = AppLanguage.PT,
                    onSelectRange = {},
                    onOpenSession = {},
                    onCloseDetail = {}
                )
            }
        }

        recorder.animate(900) {}
        recorder.hold(1_400)
        recorder.panTo(state, to = 180.dp, durationMillis = 700)
        recorder.hold(1_500)
    }
}

/**
 * Orçamento mensal: o teto contra o gasto do mês, no resumo por eixo.
 *
 * O painel vive no resumo e não numa tela própria, então a demo mostra onde ele
 * de fato aparece — o passo de ativação é que manda ao campo das Configurações.
 */
private fun recordBudget(outputDir: File) {
    val state = DemoState(contentHeight = 700.dp)

    record(outputDir, HelpTopic.BUDGET, state) { recorder ->
        recorder.setContent {
            DemoScene(state) {
                CliSessionsContent(
                    state = sessionsState(
                        view = CliSessionsView.BREAKDOWN,
                        breakdown = ScreenshotFixtures.cliBreakdown,
                        budget = ScreenshotFixtures.monthlyBudget,
                        accountCredits = ScreenshotFixtures.accountCredits
                    ),
                    language = AppLanguage.PT,
                    onSelectRange = {},
                    onOpenSession = {},
                    onCloseDetail = {}
                )
            }
        }

        recorder.animate(900) {}
        recorder.hold(1_200)
        // O painel do orçamento fica abaixo dos baldes do eixo: com 120dp de
        // deslocamento o quadro final parava no cabeçalho dele.
        recorder.panTo(state, to = 300.dp, durationMillis = 700)
        recorder.hold(1_800)
    }
}

/**
 * Alertas: a seção das Configurações, com um interruptor sendo ligado.
 *
 * É a única demo em que o clique muda o próprio controle apontado — nas demais o
 * ponteiro aciona e a tela reage em outro lugar.
 */
private fun recordAlerts(outputDir: File) {
    val state = DemoState(contentHeight = 620.dp)
    var settings by mutableStateOf(
        UsageAlertSettings.DEFAULT.copy(stalledSessionAlertsEnabled = false)
    )

    record(outputDir, HelpTopic.ALERTS, state) { recorder ->
        recorder.setContent {
            DemoScene(state) {
                Box(modifier = Modifier.fillMaxSize().padding(AppSpacing.lg)) {
                    AlertSettingsSection(
                        settings = settings,
                        language = AppLanguage.PT,
                        onSettingsChange = {},
                        budgetText = "150.00"
                    )
                }
            }
        }

        recorder.animate(700) {}
        recorder.hold(1_300)
        recorder.moveCursor(state.cursor, x = 950.dp, y = 194.dp, durationMillis = 600)
        recorder.click(state.cursor) {
            settings = settings.copy(stalledSessionAlertsEnabled = true)
        }
        recorder.animate(500) {}
        recorder.hold(1_600)
    }
}

/**
 * Exportação: os três botões da barra e o retorno da gravação.
 *
 * O clique tem de produzir reação visível — na vida real ele abre o diálogo de
 * arquivo, que não existe numa cena offscreen. O que a demo mostra é o estado
 * seguinte, que é o que a tela realmente exibe quando a gravação termina.
 */
private fun recordExport(outputDir: File) {
    val state = DemoState(contentHeight = 460.dp)
    var outcome by mutableStateOf<CliExportOutcome?>(null)

    record(outputDir, HelpTopic.EXPORT, state) { recorder ->
        recorder.setContent {
            DemoScene(state) {
                CliSessionsContent(
                    state = sessionsState(exportOutcome = outcome),
                    language = AppLanguage.PT,
                    onSelectRange = {},
                    onOpenSession = {},
                    onCloseDetail = {}
                )
            }
        }

        recorder.animate(800) {}
        recorder.hold(1_100)
        recorder.moveCursor(state.cursor, x = 946.dp, y = 44.dp, durationMillis = 600)
        recorder.click(state.cursor) {
            outcome = CliExportOutcome.Saved("~/Documentos/usage-monitor-sessoes.pdf")
        }
        recorder.animate(400) {}
        recorder.hold(1_800)
    }
}

/** Visão de time: o consumo por integrante, com um deles expandido. */
private fun recordTeam(outputDir: File) {
    val state = DemoState(contentHeight = 560.dp)
    var expanded by mutableStateOf(emptySet<String>())

    record(outputDir, HelpTopic.TEAM, state) { recorder ->
        recorder.setContent {
            DemoScene(state) {
                TeamUsageContent(
                    state = TeamUsageUiState.Success(
                        members = ScreenshotFixtures.teamMembers,
                        range = CliSessionRange.LAST_5H,
                        rangeEndsAt = ScreenshotFixtures.NOW.plusSeconds(2 * 3_600L),
                        rangeAnchored = true,
                        accountLabel = "dev@example.com — Example Org",
                        lastChangedAt = ScreenshotFixtures.NOW,
                        expandedMemberKeys = expanded
                    ),
                    language = AppLanguage.PT,
                    onSelectRange = {},
                    onToggleMember = {}
                )
            }
        }

        recorder.animate(800) {}
        recorder.hold(1_200)
        recorder.moveCursor(state.cursor, x = 300.dp, y = 262.dp, durationMillis = 500)
        recorder.click(state.cursor) {
            expanded = setOf(ScreenshotFixtures.LOCAL_DEVICE_ID)
        }
        recorder.animate(500) {}
        recorder.hold(1_700)
    }
}

// --- Máquina de gravação -----------------------------------------------------

/**
 * Estado comum a todos os roteiros: ponteiro e deslocamento.
 *
 * [contentHeight] pode passar da altura da cena. Medir alto e deslocar com
 * [panTo] é determinístico; rolar por evento de ponteiro dependeria do
 * multiplicador de scroll da plataforma e do container certo estar sob o cursor.
 */
private class DemoState(contentHeight: Dp = HEIGHT_DP.dp) {

    val cursor = CursorTrack()

    val contentHeight: Dp = contentHeight

    var pan by mutableStateOf(0.dp)
}

/**
 * A cena de um roteiro: o conteúdo deslocável e o ponteiro por cima.
 *
 * O ponteiro **precisa** estar aqui: as demos não clicam de verdade, mudam
 * estado, e sem ele o espectador vê a tela reagir sozinha. Foi o que aconteceu
 * na primeira passada — a demo do dashboard trocava para "atualizando" sem nada
 * indicar o que tinha sido acionado.
 */
@Composable
private fun DemoScene(state: DemoState, content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        PannedViewport(contentHeight = state.contentHeight, pan = state.pan, content = content)
        TourCursorOverlay(state.cursor.pose)
    }
}

private fun SceneRecorder.panTo(state: DemoState, to: Dp, durationMillis: Long) {
    panValue(from = state.pan, to = to, durationMillis = durationMillis) { value -> state.pan = value }
}

/**
 * Grava um roteiro e escreve `<mediaId>.gif`.
 *
 * O nome do arquivo sai de [HelpCatalog], nunca de um literal aqui: é o catálogo
 * que a janela consulta para achar o recurso, e um nome digitado duas vezes
 * eventualmente divergiria — a demo simplesmente sumiria da tela, sem erro.
 */
private fun record(
    outputDir: File,
    topic: HelpTopic,
    state: DemoState,
    script: (SceneRecorder) -> Unit
) {
    val recorder = SceneRecorder(widthDp = WIDTH_DP, heightDp = HEIGHT_DP)
    try {
        state.cursor.pose = state.cursor.pose.copy(visible = false)
        script(recorder)

        val file = File(outputDir, "${HelpCatalog.mediaId(topic)}.gif")
        GifEncoder.write(file, recorder.frames)

        val seconds = recorder.frames.sumOf { frame -> frame.delayMillis } / 1_000.0
        println(
            "  ${file.name} (${WIDTH_DP}x$HEIGHT_DP, ${recorder.frames.size} quadros, %.1fs, %.0f KB)"
                .format(seconds, file.length() / 1_024.0)
        )
    } finally {
        recorder.close()
    }
}

// --- Estados sintéticos ------------------------------------------------------

private fun sessionsState(
    view: CliSessionsView = CliSessionsView.SESSIONS,
    breakdown: CliUsageBreakdown? = null,
    budget: MonthlyBudgetStatus? = null,
    accountCredits: AccountCreditUsage? = null,
    exportOutcome: CliExportOutcome? = null
) = CliSessionsUiState.Success(
    sessions = ScreenshotFixtures.cliSessions,
    view = view,
    breakdown = breakdown,
    budget = budget,
    accountCredits = accountCredits,
    exportOutcome = exportOutcome,
    range = CliSessionRange.LAST_5H,
    rangeEndsAt = ScreenshotFixtures.NOW.plusSeconds(2 * 3_600L),
    rangeAnchored = true,
    profileLabel = "Padrão",
    lastChangedAt = ScreenshotFixtures.NOW
)

private fun Instant.plusSeconds(seconds: Long): Instant =
    Instant.fromEpochMilliseconds(toEpochMilliseconds() + seconds * 1_000L)
