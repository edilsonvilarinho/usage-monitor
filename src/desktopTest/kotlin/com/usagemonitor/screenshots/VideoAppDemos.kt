package com.usagemonitor.screenshots

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.AnalysisPeriod
import com.usagemonitor.domain.entity.AnalysisPeriodBar
import com.usagemonitor.domain.entity.SessionAnalysis
import com.usagemonitor.presentation.AnalysisFixture
import com.usagemonitor.presentation.ui.AnalysisContent
import com.usagemonitor.presentation.ui.CodexCliSessionDetailPane
import com.usagemonitor.presentation.ui.codexResumeSessionCommand
import com.usagemonitor.presentation.ui.components.CopySessionCommandButton
import com.usagemonitor.presentation.ui.theme.AppMotionPolicy
import com.usagemonitor.presentation.ui.theme.LocalAppMotionPolicy
import com.usagemonitor.presentation.viewmodel.AnalysisUiState
import kotlin.time.Duration.Companion.days

/**
 * Demos de telas do app só para o vídeo de lançamento (#633): o composable real de cada tela, sem
 * view model, com o ponteiro sintético indo até o controle que o roteiro cita.
 */
private const val CODEX_ID = "video-codex-sessions"
private const val ANALYSIS_ID = "video-analysis-all"

/** Mesma escala das outras demos de vídeo: o vídeo dá zoom na cena. */
private const val APP_DEMO_SCALE = 4
private const val APP_MARGIN_DP = VideoDemoPace.SCENE_MARGIN_DP

// Conteúdo de cada tela mais a margem em volta: nenhum quadro aparece cortado.
private const val CODEX_SCENE_WIDTH_DP = 1_000 + 2 * APP_MARGIN_DP
private const val CODEX_SCENE_HEIGHT_DP = 1_000 + 2 * APP_MARGIN_DP

/**
 * O clique dispara antes do 2º quadro da onda (progresso 0,5), e é nele que o botão mostra "Copiado".
 * O "Copiado" do botão dura 2 s de tempo REAL (`CopySessionCommandButton.kt:61`, `delay`), e cada quadro a 4x
 * leva ~0,7 s para renderizar: esperar depois do clique só gravaria o rótulo já revertido. A espera longa
 * vai então no próprio quadro do "Copiado", e os quadros seguintes da onda são descartados.
 */
private const val COPIED_FRAME_OFFSET = 1

/** Mantém só os quadros até o do "Copiado" ([copiedIndex]) e dá a ele a espera [holdMillis]. */
private fun holdCopiedFrame(frames: MutableList<GifFrame>, copiedIndex: Int, holdMillis: Long) {
    val copied = frames[copiedIndex]
    while (frames.size > copiedIndex) {
        frames.removeAt(frames.size - 1)
    }
    frames += GifFrame(copied.image, holdMillis.toInt())
}

private const val ANALYSIS_SCENE_WIDTH_DP = 1_000 + 2 * APP_MARGIN_DP
private const val ANALYSIS_SCENE_HEIGHT_DP = 1_320 + 2 * APP_MARGIN_DP

// Centros dos controles em dp da cena; medidos no quadro 0 (o seletor e o comparador não expõem limites).
private const val ANALYSIS_ALL_X = 277f + APP_MARGIN_DP
private const val ANALYSIS_ALL_Y = 76f + APP_MARGIN_DP
private const val ANALYSIS_COMPARATOR_X = 200f + APP_MARGIN_DP
private const val ANALYSIS_COMPARATOR_Y = 618f + APP_MARGIN_DP

/** Ponteiro nasce a esta distância do alvo, não no canto da cena. */
private const val APP_CURSOR_OFFSET_X = 120f
private const val APP_CURSOR_OFFSET_Y = 60f

/** Detalhe da sessão Codex: o ponteiro clica em "Copiar comando de retomada" e o botão mostra "Copiado". */
internal fun recordCodexSessions(options: HelpMediaOptions) {
    val detail = codexDetailFixture()
    val command = codexResumeSessionCommand(detail.summary.sessionId)
    val state = DemoState(CODEX_SCENE_HEIGHT_DP.dp)
    var targetX = 0f
    var targetY = 0f
    recordMedia(options, CODEX_ID, state, { recorder ->
        recorder.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalAppMotionPolicy provides AppMotionPolicy.Live) {
                DemoScene(state) {
                    Column(Modifier.fillMaxSize().padding(APP_MARGIN_DP.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            CopySessionCommandButton(
                                sessionId = detail.summary.sessionId,
                                language = options.language,
                                showLabel = true,
                                modifier = Modifier.onGloballyPositioned { coordinates ->
                                    val origin = coordinates.positionInRoot()
                                    targetX = with(density) { (origin.x + coordinates.size.width / 2f).toDp().value }
                                    targetY = with(density) { (origin.y + coordinates.size.height / 2f).toDp().value }
                                },
                                onCopy = {},
                                command = command
                            )
                        }
                        CodexCliSessionDetailPane(detail, options.language, {}, Modifier.weight(1f))
                    }
                }
            }
        }
        state.cursor.pose = state.cursor.pose.copy(
            x = (targetX - APP_CURSOR_OFFSET_X).dp, y = (targetY + APP_CURSOR_OFFSET_Y).dp, visible = true
        )
        recorder.hold(VideoDemoPace.INTRO_HOLD)
        recorder.moveCursor(state.cursor, targetX.dp, targetY.dp, VideoDemoPace.CURSOR_TRAVEL)
        recorder.hold(VideoDemoPace.DWELL_BEFORE_CLICK)
        val framesBeforeClick = recorder.frames.size
        recorder.click(state.cursor) { recorder.clickMouse(targetX, targetY) }
        holdCopiedFrame(recorder.frames, framesBeforeClick + COPIED_FRAME_OFFSET, VideoDemoPace.STEP_HOLD)
        recorder.hold(VideoDemoPace.OUTRO_HOLD)
    }, CODEX_SCENE_WIDTH_DP, CODEX_SCENE_HEIGHT_DP, APP_DEMO_SCALE)
}

/** Análise do período "Tudo": a mesma fixture com as barras de pico por janela do recorte longo. */
private fun analysisAllFor(period: AnalysisPeriod): SessionAnalysis = AnalysisFixture.analysis.copy(
    period = period,
    periodBars = if (period == AnalysisPeriod.ALL) {
        List(4) { index ->
            AnalysisPeriodBar(
                start = AnalysisFixture.now - ((4 - index) * 7).days,
                peakPercent = 40 + index,
                current = index == 3
            )
        }
    } else {
        AnalysisFixture.analysis.periodBars
    }
)

/** Análise: o ponteiro escolhe "Tudo" no seletor de período e desce até o comparador "Média · Última hora". */
internal fun recordAnalysisAll(options: HelpMediaOptions) {
    val state = DemoState(ANALYSIS_SCENE_HEIGHT_DP.dp)
    var period by mutableStateOf(AnalysisPeriod.NOW)
    recordMedia(options, ANALYSIS_ID, state, { recorder ->
        recorder.setContent {
            CompositionLocalProvider(LocalAppMotionPolicy provides AppMotionPolicy.Live) {
                DemoScene(state) {
                    Box(Modifier.fillMaxSize().padding(APP_MARGIN_DP.dp)) {
                        AnalysisContent(
                            state = AnalysisUiState.Success(analysisAllFor(period)),
                            accountLabel = "Anthropic · Padrão",
                            language = options.language,
                            onExportPdf = {},
                            period = period,
                            onSelectPeriod = { chosen -> period = chosen }
                        )
                    }
                }
            }
        }
        state.cursor.pose = state.cursor.pose.copy(
            x = (ANALYSIS_ALL_X - APP_CURSOR_OFFSET_X).dp, y = (ANALYSIS_ALL_Y + APP_CURSOR_OFFSET_Y).dp, visible = true
        )
        recorder.hold(VideoDemoPace.INTRO_HOLD)
        recorder.moveCursor(state.cursor, ANALYSIS_ALL_X.dp, ANALYSIS_ALL_Y.dp, VideoDemoPace.CURSOR_TRAVEL)
        recorder.hold(VideoDemoPace.DWELL_BEFORE_CLICK)
        recorder.click(state.cursor) { recorder.clickMouse(ANALYSIS_ALL_X, ANALYSIS_ALL_Y) }
        recorder.animate(VideoDemoPace.MIN_ANIMATION) {}
        recorder.hold(VideoDemoPace.STEP_HOLD)
        recorder.moveCursor(state.cursor, ANALYSIS_COMPARATOR_X.dp, ANALYSIS_COMPARATOR_Y.dp, VideoDemoPace.CURSOR_TRAVEL)
        recorder.hold(VideoDemoPace.OUTRO_HOLD)
    }, ANALYSIS_SCENE_WIDTH_DP, ANALYSIS_SCENE_HEIGHT_DP, APP_DEMO_SCALE)
}
