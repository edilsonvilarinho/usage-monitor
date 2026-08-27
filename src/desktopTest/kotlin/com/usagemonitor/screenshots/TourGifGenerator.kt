package com.usagemonitor.screenshots

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.CliSessionRange
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.domain.usecase.CliSessionDetailResult
import com.usagemonitor.domain.usecase.ComputeCliSessionAnalyticsUseCase
import com.usagemonitor.presentation.ui.CliSessionsContent
import com.usagemonitor.presentation.ui.HistoryScreen
import com.usagemonitor.presentation.ui.TeamUsageContent
import com.usagemonitor.presentation.ui.components.FooterBar
import com.usagemonitor.presentation.ui.components.ResponsiveDashboardCardGrid
import com.usagemonitor.presentation.ui.components.SettingsDialogContent
import com.usagemonitor.presentation.ui.components.TeamIntegrationSection
import com.usagemonitor.presentation.ui.theme.AppTheme
import com.usagemonitor.presentation.viewmodel.CliSessionDetailUiState
import com.usagemonitor.presentation.viewmodel.CliSessionsUiState
import com.usagemonitor.presentation.viewmodel.HistoryViewModel
import com.usagemonitor.presentation.viewmodel.TeamUsageUiState
import kotlinx.datetime.Instant
import java.awt.image.BufferedImage
import java.io.File
import com.usagemonitor.presentation.ui.theme.AppThemePreset

/**
 * Gera `img/tour.gif`: um passeio pelas telas da app, renderizado offscreen a
 * partir dos composables reais com os dados sintéticos de [ScreenshotFixtures].
 *
 * As telas não trocam por clique de verdade — trocam por mutação de estado, e o
 * ponteiro de [TourCursorOverlay] é desenhado por cima para o espectador ver o
 * que teria sido acionado. Clique real exigiria coordenada de componente, que
 * quebra a cada mexida de layout sem ninguém perceber.
 *
 * Rodar com `gradlew.bat generateTourGif`.
 */

/** Supersampling: a cena renderiza em 2x e cada quadro é reduzido à metade. */
private const val SCALE = 2

private const val WIDTH_DP = 1_100
private const val HEIGHT_DP = 720

/** 10 quadros por segundo — o suficiente para fade e spinner não picotarem. */
private const val FRAME_MILLIS = 100L

/**
 * Quadros gravados no fim de um movimento antes da pausa longa.
 *
 * A pausa é um único quadro com espera longa; sem estes dois, a animação que
 * ainda estava assentando seria cortada no meio.
 */
private const val SETTLE_FRAMES = 2

private const val CROSSFADE_MILLIS = 280

fun main(args: Array<String>) {
    val outputDir = File(args.firstOrNull() ?: "img")
    outputDir.mkdirs()

    val state = TourState()
    val historyViewModel = fixedHistoryViewModel()
    val recorder = TourRecorder()

    try {
        recorder.setContent { TourContent(state, historyViewModel) }
        recordTour(recorder, state)

        val file = File(outputDir, "tour.gif")
        GifEncoder.write(file, recorder.frames)

        val seconds = recorder.frames.sumOf { frame -> frame.delayMillis } / 1_000.0
        println("  ${file.name} (${WIDTH_DP}x$HEIGHT_DP, ${recorder.frames.size} quadros, %.1fs, %.1f MB)"
            .format(seconds, file.length() / (1_024.0 * 1_024.0)))
    } finally {
        recorder.close()
        historyViewModel.onDestroy()
    }
}

// --- Roteiro -----------------------------------------------------------------

private fun recordTour(recorder: TourRecorder, state: TourState) {
    // 1. Dashboard entrando: os cards têm `delay(index * 90)` + fade próprio.
    recorder.animate(1_300) {}
    recorder.hold(800)

    // 2. Atualizar um card.
    state.cursor = state.cursor.copy(x = 300.dp, y = 400.dp, visible = true)
    recorder.moveCursor(state, x = 322.dp, y = 46.dp, durationMillis = 450)
    recorder.click(state) { state.refreshing = setOf(ScreenshotFixtures.primaryAnthropicTarget) }
    recorder.animate(700) {}
    state.refreshing = emptySet()
    recorder.hold(700)

    // 3. Histórico, com a rolagem revelando a previsão da cota.
    recorder.fadeTo(state, TourScreen.HISTORY, contentHeight = 1_040.dp)
    recorder.hold(1_100)
    recorder.pan(state, to = 230.dp, durationMillis = 600)
    recorder.hold(1_100)

    // 4. Sessões CLI: troca de janela e abertura do detalhe.
    recorder.fadeTo(state, TourScreen.CLI_SESSIONS, contentHeight = HEIGHT_DP.dp)
    recorder.hold(1_100)

    recorder.moveCursor(state, x = 118.dp, y = 136.dp, durationMillis = 400)
    recorder.click(state) { state.cliState = state.cliState.copy(range = CliSessionRange.LAST_7D) }
    recorder.animate(300) {}
    recorder.hold(900)

    recorder.moveCursor(state, x = 300.dp, y = 220.dp, durationMillis = 400)
    recorder.click(state) { state.openSessionDetail() }
    // Alto o bastante para o deslocamento não chegar ao fim da caixa: ali o
    // conteúdo acabaria e o quadro mostraria fundo vazio.
    state.contentHeight = 1_200.dp
    recorder.animate(500) {}
    recorder.hold(1_200)

    recorder.pan(state, to = 340.dp, durationMillis = 700)
    recorder.hold(1_200)

    // 5. Sessões do time, com um integrante expandido. Sem deslocamento: com os
    // três integrantes abertos o conteúdo ainda cabe na cena.
    state.cliState = state.cliState.copy(detail = null)
    recorder.fadeTo(state, TourScreen.TEAM, contentHeight = HEIGHT_DP.dp)
    recorder.hold(1_000)

    recorder.moveCursor(state, x = 300.dp, y = 218.dp, durationMillis = 400)
    recorder.click(state) {
        state.teamState = state.teamState.copy(
            expandedMemberKeys = setOf(ScreenshotFixtures.LOCAL_DEVICE_ID)
        )
    }
    recorder.animate(400) {}
    recorder.hold(1_400)

    // 6. Configurações e a seção de time.
    recorder.fadeTo(state, TourScreen.SETTINGS, contentHeight = HEIGHT_DP.dp)
    recorder.hold(1_200)
    recorder.fadeTo(state, TourScreen.SETTINGS_TEAM, contentHeight = HEIGHT_DP.dp)
    recorder.hold(1_200)

    // 7. Volta ao dashboard para o laço fechar sem corte seco.
    state.reset()
    recorder.fadeTo(state, TourScreen.DASHBOARD, contentHeight = HEIGHT_DP.dp)
    recorder.animate(1_000) {}
    recorder.hold(1_300)
}

// --- Estado do tour ----------------------------------------------------------

private enum class TourScreen { DASHBOARD, HISTORY, CLI_SESSIONS, TEAM, SETTINGS, SETTINGS_TEAM }

private class TourState {

    var screen by mutableStateOf(TourScreen.DASHBOARD)

    /**
     * Altura em que o conteúdo é medido, que pode passar da altura da cena.
     *
     * A tela de detalhe não cabe em 720dp. Medir alto e deslocar com [pan] é
     * determinístico; rolar por evento de ponteiro dependeria do multiplicador
     * de scroll da plataforma e do container certo estar sob o cursor.
     */
    var contentHeight by mutableStateOf(HEIGHT_DP.dp)

    var pan by mutableStateOf(0.dp)

    var refreshing by mutableStateOf(emptySet<UsageTargetKey>())

    var cursor by mutableStateOf(TourCursorPose(x = 300.dp, y = 400.dp, visible = false))

    var cliState by mutableStateOf(initialCliState())

    var teamState by mutableStateOf(initialTeamState())

    fun openSessionDetail() {
        val detail = ScreenshotFixtures.saturatedSessionDetail
        cliState = cliState.copy(
            detail = CliSessionDetailUiState.Ready(
                sessionId = detail.summary.sessionId,
                result = CliSessionDetailResult(
                    detail = detail,
                    analytics = ComputeCliSessionAnalyticsUseCase().invoke(detail)
                )
            ),
            advancedExpanded = true
        )
    }

    /** Volta ao estado inicial para o último quadro casar com o primeiro. */
    fun reset() {
        refreshing = emptySet()
        cliState = initialCliState()
        teamState = initialTeamState()
    }
}

private fun initialCliState() = CliSessionsUiState.Success(
    sessions = ScreenshotFixtures.cliSessions,
    range = CliSessionRange.LAST_5H,
    rangeEndsAt = ScreenshotFixtures.NOW.shiftedBySeconds(2 * 3_600L),
    rangeAnchored = true,
    profileLabel = "Padrão",
    lastChangedAt = ScreenshotFixtures.NOW
)

private fun initialTeamState() = TeamUsageUiState.Success(
    members = ScreenshotFixtures.teamMembers,
    range = CliSessionRange.LAST_5H,
    rangeEndsAt = ScreenshotFixtures.NOW.shiftedBySeconds(2 * 3_600L),
    rangeAnchored = true,
    accountLabel = "dev@example.com — Example Org",
    lastChangedAt = ScreenshotFixtures.NOW
)

// --- Cena --------------------------------------------------------------------

@Composable
private fun TourContent(state: TourState, historyViewModel: HistoryViewModel) {
    Box(modifier = Modifier.fillMaxSize()) {
        Crossfade(
            targetState = state.screen,
            animationSpec = tween(CROSSFADE_MILLIS),
            modifier = Modifier.fillMaxSize()
        ) { screen ->
            PannedViewport(contentHeight = state.contentHeight, pan = state.pan) {
                when (screen) {
                    TourScreen.DASHBOARD -> DashboardTourScreen(state)
                    TourScreen.HISTORY -> HistoryScreen(
                        viewModel = historyViewModel,
                        language = AppLanguage.PT,
                        onBack = {},
                        focusedSource = ApiSource.ANTHROPIC
                    )

                    TourScreen.CLI_SESSIONS -> CliSessionsContent(
                        state = state.cliState,
                        language = AppLanguage.PT,
                        onSelectRange = {},
                        onOpenSession = {},
                        onCloseDetail = {}
                    )

                    TourScreen.TEAM -> TeamUsageContent(
                        state = state.teamState,
                        language = AppLanguage.PT,
                        onSelectRange = {},
                        onToggleMember = {}
                    )

                    TourScreen.SETTINGS -> SettingsTourScreen()
                    TourScreen.SETTINGS_TEAM -> TeamSettingsTourScreen()
                }
            }
        }

        TourCursorOverlay(state.cursor)
    }
}

/**
 * Mede o conteúdo em [contentHeight] e o desloca em [pan] dentro da cena.
 *
 * É um `Layout` na mão porque os dois modificadores prontos falham aqui:
 * `height` é coagido pelas constraints do pai e nunca passaria dos 720dp da
 * cena, e `requiredHeight` mede alto mas **centraliza** o que sobra — o topo da
 * tela sumia e o deslocamento revelava vazio no rodapé.
 */
@Composable
private fun PannedViewport(contentHeight: Dp, pan: Dp, content: @Composable () -> Unit) {
    Layout(
        content = content,
        modifier = Modifier.fillMaxSize().clipToBounds()
    ) { measurables, constraints ->
        val height = contentHeight.roundToPx()
        val placeables = measurables.map { measurable ->
            measurable.measure(Constraints.fixed(constraints.maxWidth, height))
        }
        layout(constraints.maxWidth, constraints.maxHeight) {
            val top = -pan.roundToPx()
            placeables.forEach { placeable -> placeable.place(0, top) }
        }
    }
}

@Composable
private fun DashboardTourScreen(state: TourState) {
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
                refreshingTargets = state.refreshing,
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
            nextRefreshAt = ScreenshotFixtures.NOW.shiftedBySeconds(437),
            onRefresh = {},
            onOpenSettings = {},
            nowProvider = { ScreenshotFixtures.NOW },
            countdownUpdatesEnabled = false
        )
    }
}

/**
 * As Configurações moram num diálogo: mostrá-las ocupando os 1100dp da cena
 * daria uma tela que não existe na app.
 */
@Composable
private fun SettingsTourScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier.width(660.dp).height(640.dp),
            color = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.large,
            shadowElevation = 12.dp
        ) {
            SettingsDialogContent(
                currentTheme = AppThemePreset.OBSIDIANA_DARK,
                currentLanguage = AppLanguage.PT,
                enabledApis = ScreenshotFixtures.enabledApis,
                autoStartEnabled = true,
                alwaysOnTopEnabled = false,
                windowOpacityPercent = 92,
                onThemeChange = {},
                onLanguageChange = {},
                onAutoStartChange = {},
                onAlwaysOnTopChange = {},
                onApiToggle = { _, _ -> },
                anthropicProfiles = ScreenshotFixtures.anthropicProfiles
            )
        }
    }
}

@Composable
private fun TeamSettingsTourScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier.width(660.dp),
            color = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.large,
            shadowElevation = 12.dp
        ) {
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
                modifier = Modifier.padding(20.dp)
            )
        }
    }
}

// --- Gravação ----------------------------------------------------------------

private class TourRecorder {

    val frames = mutableListOf<GifFrame>()

    @OptIn(ExperimentalComposeUiApi::class)
    private val scene = ImageComposeScene(
        width = WIDTH_DP * SCALE,
        height = HEIGHT_DP * SCALE,
        density = Density(SCALE.toFloat())
    )

    private var sceneNanos = 0L

    @OptIn(ExperimentalComposeUiApi::class)
    fun setContent(content: @Composable () -> Unit) {
        scene.setContent {
            AppTheme(isDark = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    content()
                }
            }
        }
        scene.render(0L)
    }

    /**
     * Um quadro adiante.
     *
     * Os dois relógios avançam juntos de propósito: os `delay` dos composables
     * esperam **tempo real** (rodam em `Dispatchers.Unconfined`), enquanto as
     * animações consomem o `nanoTime` passado a `render`. Avançar só um deixa a
     * cena em branco.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    private fun step(): BufferedImage {
        Thread.sleep(FRAME_MILLIS)
        sceneNanos += FRAME_MILLIS * 1_000_000L
        // A mutação de estado veio de fora de qualquer snapshot; sem isto o
        // recompositor só a enxergaria no quadro seguinte, ou em nenhum.
        Snapshot.sendApplyNotifications()
        return scene.render(sceneNanos).toBufferedImage().downsampleByTwo()
    }

    /** Grava [durationMillis] de movimento, com [onFrame] recebendo 0..1. */
    fun animate(durationMillis: Long, onFrame: (Float) -> Unit) {
        val count = (durationMillis / FRAME_MILLIS).toInt().coerceAtLeast(1)
        for (index in 1..count) {
            onFrame(index.toFloat() / count)
            frames += GifFrame(step(), FRAME_MILLIS.toInt())
        }
    }

    /** Uma pausa: [SETTLE_FRAMES] quadros normais e um quadro longo. */
    fun hold(durationMillis: Long) {
        repeat(SETTLE_FRAMES) {
            frames += GifFrame(step(), FRAME_MILLIS.toInt())
        }
        frames += GifFrame(step(), durationMillis.toInt())
    }

    @OptIn(ExperimentalComposeUiApi::class)
    fun close() = scene.close()
}

private fun TourRecorder.moveCursor(state: TourState, x: Dp, y: Dp, durationMillis: Long) {
    val fromX = state.cursor.x
    val fromY = state.cursor.y
    animate(durationMillis) { progress ->
        val eased = smoothStep(progress)
        state.cursor = state.cursor.copy(
            x = fromX + (x - fromX) * eased,
            y = fromY + (y - fromY) * eased,
            visible = true,
            clickProgress = null
        )
    }
}

/** Onda do clique; [action] dispara no meio dela, não no fim. */
private fun TourRecorder.click(state: TourState, action: () -> Unit) {
    var fired = false
    animate(400) { progress ->
        if (!fired && progress >= 0.35f) {
            action()
            fired = true
        }
        state.cursor = state.cursor.copy(clickProgress = progress)
    }
    state.cursor = state.cursor.copy(clickProgress = null)
}

private fun TourRecorder.pan(state: TourState, to: Dp, durationMillis: Long) {
    val from = state.pan
    animate(durationMillis) { progress ->
        state.pan = from + (to - from) * smoothStep(progress)
    }
}

/** Troca de tela: o ponteiro some, porque não há o que ele esteja acionando. */
private fun TourRecorder.fadeTo(state: TourState, screen: TourScreen, contentHeight: Dp) {
    state.cursor = state.cursor.copy(visible = false, clickProgress = null)
    state.pan = 0.dp
    state.contentHeight = contentHeight
    state.screen = screen
    animate(CROSSFADE_MILLIS + 200L) {}
}

private fun smoothStep(progress: Float): Float = progress * progress * (3f - 2f * progress)

private fun Instant.shiftedBySeconds(seconds: Long): Instant =
    Instant.fromEpochMilliseconds(toEpochMilliseconds() + seconds * 1_000L)
