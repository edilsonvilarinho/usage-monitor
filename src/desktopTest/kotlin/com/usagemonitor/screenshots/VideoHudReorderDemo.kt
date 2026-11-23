package com.usagemonitor.screenshots

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.usagemonitor.HudEdge
import com.usagemonitor.hudNotchSizes
import com.usagemonitor.presentation.ui.HudNotch
import com.usagemonitor.presentation.ui.theme.AppMotionPolicy
import com.usagemonitor.presentation.ui.theme.LocalAppMotionPolicy
import kotlin.math.ceil

/**
 * Demo de reordenar as contas da HUD só para o vídeo de lançamento (#633): pressão longa no
 * terceiro anel e arrasto até a vaga do primeiro, com o composable real do notch. A ordem nova
 * vem do callback do próprio gesto e é aplicada ao estado da cena, como o host faz.
 */
private const val REORDER_ID = "video-hud-reorder"

/** Mesma escala e margem das outras demos de HUD: o vídeo dá zoom na cena. */
private const val REORDER_SCALE = 4
private const val REORDER_MARGIN_DP = VideoDemoPace.SCENE_MARGIN_DP

// Centros dos anéis em dp da cena (notch no topo, sem balão); medidos no quadro 0 com margem de 24 dp.
private const val REORDER_MARGIN_SHIFT = (REORDER_MARGIN_DP - 24).toFloat()
private const val RING_Y = 63f + REORDER_MARGIN_SHIFT
private const val RING_FIRST_X = 156f + REORDER_MARGIN_SHIFT
private const val RING_THIRD_X = 451f + REORDER_MARGIN_SHIFT

// O ponteiro sintético nasce perto do terceiro anel, não no canto da cena.
private const val REORDER_CURSOR_OFFSET_X = 40f
private const val REORDER_CURSOR_OFFSET_Y = 30f

// Tempo da pressão longa antes do arrasto; o gesto real do notch espera esse intervalo.
private const val LONG_PRESS_HOLD = 600L

/** Notch: segurar o terceiro anel, arrastá-lo até a vaga do primeiro e soltar; a ordem nova assenta. */
internal fun recordHudReorder(options: HelpMediaOptions) {
    val initial = ScreenshotFixtures.hudAccountsIn(options.language)
    val sizes = hudNotchSizes(accounts = initial, edge = HudEdge.TOP, fallbackLabel = "Carregando", hasUpdateIndicator = false)
    val sceneWidth = ceil(sizes.expanded.width.value).toInt() + 2 * REORDER_MARGIN_DP
    val sceneHeight = ceil(sizes.expanded.height.value).toInt() + 2 * REORDER_MARGIN_DP
    val state = DemoState(sceneHeight.dp)
    var accounts by mutableStateOf(initial)
    recordMedia(options, REORDER_ID, state, { recorder ->
        recorder.setContent {
            CompositionLocalProvider(LocalAppMotionPolicy provides AppMotionPolicy.Live) {
                DemoScene(state) {
                    Box(Modifier.fillMaxSize().padding(REORDER_MARGIN_DP.dp), contentAlignment = Alignment.TopCenter) {
                        HudNotch(
                            accounts = accounts,
                            edge = HudEdge.TOP,
                            sizes = sizes,
                            fallbackLabel = "Carregando",
                            expanded = true,
                            language = options.language,
                            onReorderAccounts = { order ->
                                accounts = order.map { key -> accounts.first { account -> account.targetKey == key } }
                            }
                        )
                    }
                }
            }
        }
        state.cursor.pose = state.cursor.pose.copy(
            x = (RING_THIRD_X + REORDER_CURSOR_OFFSET_X).dp, y = (RING_Y + REORDER_CURSOR_OFFSET_Y).dp, visible = true
        )
        recorder.hold(VideoDemoPace.INTRO_HOLD)
        recorder.moveCursor(state.cursor, RING_THIRD_X.dp, RING_Y.dp, VideoDemoPace.CURSOR_TRAVEL)
        recorder.hold(VideoDemoPace.DWELL_BEFORE_CLICK)
        recorder.drag(
            Offset(RING_THIRD_X, RING_Y), Offset(RING_FIRST_X, RING_Y),
            holdMillis = LONG_PRESS_HOLD, moveMillis = VideoDemoPace.MIN_ANIMATION
        ) { position ->
            state.cursor.pose = state.cursor.pose.copy(x = position.x.dp, y = position.y.dp, visible = true)
        }
        // A ordem nova assenta, depois o estado final fica parado.
        recorder.animate(VideoDemoPace.MIN_ANIMATION) {}
        recorder.hold(VideoDemoPace.OUTRO_HOLD)
    }, sceneWidth, sceneHeight, REORDER_SCALE, transparent = true)
}
