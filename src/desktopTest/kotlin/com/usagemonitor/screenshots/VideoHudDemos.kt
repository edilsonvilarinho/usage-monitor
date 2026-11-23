package com.usagemonitor.screenshots

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.usagemonitor.HUD_BALLOON_ACTIONS
import com.usagemonitor.HUD_BALLOON_PADDING
import com.usagemonitor.HudEdge
import com.usagemonitor.HudOrbitParams
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.hudBlackHoleAccountBalloonHeight
import com.usagemonitor.hudBlackHoleListHeight
import com.usagemonitor.hudNotchSizes
import com.usagemonitor.presentation.ui.HudAccount
import com.usagemonitor.presentation.ui.HudAccountBalloonContent
import com.usagemonitor.presentation.ui.HudFloatingBalloon
import com.usagemonitor.presentation.ui.HudFloatingHud
import com.usagemonitor.presentation.ui.HudMinimizeButton
import com.usagemonitor.presentation.ui.HudMinimizeProgress
import com.usagemonitor.presentation.ui.HudNotch
import com.usagemonitor.presentation.ui.components.AppTone
import com.usagemonitor.presentation.ui.components.CardActionButton
import com.usagemonitor.presentation.ui.components.CardIconActionButton
import com.usagemonitor.presentation.ui.components.RefreshGlyph
import com.usagemonitor.presentation.ui.components.cardActionsFor
import com.usagemonitor.presentation.ui.localized
import com.usagemonitor.presentation.ui.theme.AppMotionPolicy
import com.usagemonitor.presentation.ui.theme.LocalAppMotionPolicy
import kotlin.math.ceil

/**
 * Demos da HUD só para o vídeo de lançamento (#633), gravadas com os composables
 * reais e dados sintéticos. O relógio do gravador é virtual, então o movimento
 * contínuo (órbita das contas) entra pela política `Live` em volta do conteúdo.
 */

/** O vídeo dá zoom na cena: grava a 4×, com a cena justa ao conteúdo e a margem do ritmo das demos. */
private const val VIDEO_SCALE = 4
private const val MARGIN_DP = VideoDemoPace.SCENE_MARGIN_DP

// Caixa em que a HUD flutuante é desenhada; o buraco fica no centro dela.
private const val FLOATING_BOX_WIDTH_DP = 1_000
private const val FLOATING_BOX_HEIGHT_DP = 420

// Conteúdo medido na caixa: coluna aberta à esquerda do buraco, alça acima dela (x 375..531, y -44..377).
private const val FLOATING_CONTENT_LEFT_DP = 375
private const val FLOATING_CONTENT_TOP_DP = -44
private const val FLOATING_SCENE_WIDTH_DP = 156 + 2 * MARGIN_DP
private const val FLOATING_SCENE_HEIGHT_DP = 421 + 2 * MARGIN_DP

// requiredSize maior que a cena centraliza a caixa; o deslocamento parte dessa posição centrada.
private const val FLOATING_OFFSET_X_DP =
    (MARGIN_DP - FLOATING_CONTENT_LEFT_DP) - (FLOATING_SCENE_WIDTH_DP - FLOATING_BOX_WIDTH_DP) / 2
private const val FLOATING_OFFSET_Y_DP =
    (MARGIN_DP - FLOATING_CONTENT_TOP_DP) - (FLOATING_SCENE_HEIGHT_DP - FLOATING_BOX_HEIGHT_DP) / 2

private const val FLOATING_ID = "video-hud-floating"
private const val MINIMIZE_ID = "video-hud-minimize"

/** HUD flutuante: buraco negro com as contas em órbita e a coluna aberta ao lado. */
internal fun recordHudFloating(options: HelpMediaOptions) {
    val accounts = ScreenshotFixtures.hudAccountsIn(options.language)
    val state = DemoState(FLOATING_SCENE_HEIGHT_DP.dp)
    var expanded by mutableStateOf(false)
    recordMedia(options, FLOATING_ID, state, { recorder ->
        recorder.setContent {
            CompositionLocalProvider(LocalAppMotionPolicy provides AppMotionPolicy.Live) {
                DemoScene(state) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopStart) {
                        FloatingHudContent(accounts, options, expanded)
                    }
                }
            }
        }
        // O ponteiro nasce dentro da cena, à direita do buraco, e anda até ele: nunca teleporta.
        state.cursor.pose = state.cursor.pose.copy(x = FLOATING_CURSOR_START_X.dp, y = FLOATING_CURSOR_START_Y.dp, visible = true)
        recorder.hold(VideoDemoPace.INTRO_HOLD)
        recorder.moveCursor(state.cursor, FLOATING_HOLE_X.dp, FLOATING_HOLE_Y.dp, VideoDemoPace.CURSOR_TRAVEL)
        recorder.hold(VideoDemoPace.DWELL_BEFORE_CLICK)
        recorder.click(state.cursor) { expanded = true }
        recorder.animate(VideoDemoPace.MIN_ANIMATION) {}
        recorder.hold(VideoDemoPace.STEP_HOLD)
        recorder.hold(VideoDemoPace.OUTRO_HOLD)
    }, FLOATING_SCENE_WIDTH_DP, FLOATING_SCENE_HEIGHT_DP, VIDEO_SCALE, transparent = true)
}

// O buraco no centro da caixa (500, 210), em dp da cena; o ponteiro parte do canto direito, ainda dentro dela.
private const val FLOATING_HOLE_X = (FLOATING_BOX_WIDTH_DP / 2 - FLOATING_CONTENT_LEFT_DP + MARGIN_DP).toFloat()
private const val FLOATING_HOLE_Y = (FLOATING_BOX_HEIGHT_DP / 2 - FLOATING_CONTENT_TOP_DP + MARGIN_DP).toFloat()
private const val FLOATING_CURSOR_START_X = FLOATING_HOLE_X + 60f
private const val FLOATING_CURSOR_START_Y = FLOATING_HOLE_Y - 90f

@androidx.compose.runtime.Composable
private fun FloatingHudContent(accounts: List<HudAccount>, options: HelpMediaOptions, expanded: Boolean) {
    HudFloatingHud(
        accounts = accounts,
        params = HudOrbitParams(),
        language = options.language,
        expanded = expanded,
        balloon = null as HudFloatingBalloon?,
        balloonSide = HudEdge.RIGHT,
        update = null,
        gearDescription = localized(options.language, "Configurações", "Settings"),
        fallbackLabel = localized(options.language, "Sem conta", "No account"),
        fallbackTone = AppTone.OK,
        holeScreenY = (FLOATING_BOX_HEIGHT_DP / 2).dp,
        areaTop = 0.dp,
        areaBottom = FLOATING_BOX_HEIGHT_DP.dp,
        onHoverChange = {},
        onAccountClick = {},
        onGearClick = {},
        onToggleFloating = {},
        onDragStart = {},
        onDrag = {},
        onDragEnd = {},
        appBalloonHeight = 120.dp,
        tallestBalloonHeight = 400.dp,
        accountBalloon = { index ->
            HudAccountBalloonContent(accounts[index], options.language, { account -> BalloonActions(account, options) {} }, 400.dp)
        },
        appBalloon = { Text("Usage Monitor") },
        accountActions = { account -> BalloonActions(account, options) {} },
        onHoleHover = {},
        onHoleClick = {},
        onHoleSelect = {},
        onHoleBack = {},
        modifier = Modifier
            .offset(FLOATING_OFFSET_X_DP.dp, FLOATING_OFFSET_Y_DP.dp)
            .requiredSize(FLOATING_BOX_WIDTH_DP.dp, FLOATING_BOX_HEIGHT_DP.dp)
    )
}

/** Os botões do balão como o host os monta: ações do card, Minimizar/Restaurar e Atualizar. */
@androidx.compose.runtime.Composable
private fun BalloonActions(account: HudAccount, options: HelpMediaOptions, onToggleMinimize: (UsageTargetKey) -> Unit) {
    cardActionsFor(account.targetKey, setOf("padrao")).forEach { action ->
        CardActionButton(action, options.language, HUD_BALLOON_ACTIONS, 16.dp, onClick = {})
    }
    HudMinimizeButton(
        targetKey = account.targetKey,
        minimized = account.minimized,
        language = options.language,
        buttonSize = HUD_BALLOON_ACTIONS,
        onToggle = onToggleMinimize
    )
    CardIconActionButton(label = localized(options.language, "Atualizar", "Refresh"), onClick = {}, buttonSize = HUD_BALLOON_ACTIONS) { tint ->
        RefreshGlyph(refreshing = false, tint = tint, size = 16.dp)
    }
}

// Pontos do ponteiro, em dp da cena; medidos nos quadros gravados com margem de 24 dp, somada ao de hoje.
private const val MARGIN_SHIFT = (MARGIN_DP - 24).toFloat()
private const val MINIMIZE_BUTTON_X = 236f + MARGIN_SHIFT
private const val MINIMIZE_BUTTON_Y = 361f + MARGIN_SHIFT
private const val HOLE_ROW_X = 487f + MARGIN_SHIFT
private const val HOLE_ROW_Y = 163f + MARGIN_SHIFT
private const val RESTORE_BUTTON_X = 552f + MARGIN_SHIFT
private const val RESTORE_BUTTON_Y = 385f + MARGIN_SHIFT

// O ponteiro nasce perto do anel da primeira conta, não no canto da cena.
private const val CURSOR_START_X = 175f + MARGIN_SHIFT
private const val CURSOR_START_Y = 90f + MARGIN_SHIFT

/** Notch: balão aberto, "Minimizar", a conta cai no buraco, a lista "N minimizadas" aparece e "Restaurar" a devolve. */
internal fun recordHudMinimize(options: HelpMediaOptions) {
    val initial = ScreenshotFixtures.hudAccountsIn(options.language)
    val target = initial.first().targetKey
    val full = hudNotchSizes(accounts = initial, edge = HudEdge.TOP, fallbackLabel = "Carregando", hasUpdateIndicator = false)
    val minimizedFirst = initial.mapIndexed { index, account -> if (index == 0) account.copy(minimized = true) else account }
    // A altura do balão do buraco segue o conteúdo: a lista (cabeçalho + linhas) e depois a conta com a faixa de volta.
    val listSizes = full.copy(balloon = DpSize(full.balloon.width, hudBlackHoleListHeight(1) + HUD_BALLOON_PADDING * 2))
    val accountSizes = full.copy(balloon = DpSize(full.balloon.width, hudBlackHoleAccountBalloonHeight(minimizedFirst.first(), full.balloon.height)))
    val sceneWidth = ceil(full.expanded.width.value).toInt() + 2 * MARGIN_DP
    val sceneHeight = ceil(full.expanded.height.value).toInt() + 2 * MARGIN_DP
    val state = DemoState(sceneHeight.dp)
    var accounts by mutableStateOf(initial)
    var sizes by mutableStateOf(full)
    var progress by mutableStateOf(HudMinimizeProgress(emptyMap(), moving = false))
    // O balão inicial só vale na criação do notch: trocar a chave o recria já com a lista do buraco aberta.
    var balloonIndex by mutableStateOf(0)
    recordMedia(options, MINIMIZE_ID, state, { recorder ->
        recorder.setContent {
            CompositionLocalProvider(LocalAppMotionPolicy provides AppMotionPolicy.Live) {
                DemoScene(state) {
                    Box(Modifier.fillMaxSize().padding(MARGIN_DP.dp), contentAlignment = Alignment.TopCenter) {
                        key(balloonIndex) {
                            HudNotch(
                                accounts = accounts,
                                edge = HudEdge.TOP,
                                sizes = sizes,
                                fallbackLabel = "Carregando",
                                expanded = true,
                                language = options.language,
                                initialBalloonIndex = balloonIndex,
                                minimize = progress,
                                accountActions = { account -> BalloonActions(account, options) {} }
                            )
                        }
                    }
                }
            }
        }
        state.cursor.pose = state.cursor.pose.copy(x = CURSOR_START_X.dp, y = CURSOR_START_Y.dp, visible = true)
        recorder.hold(VideoDemoPace.INTRO_HOLD)
        recorder.moveCursor(state.cursor, MINIMIZE_BUTTON_X.dp, MINIMIZE_BUTTON_Y.dp, VideoDemoPace.CURSOR_TRAVEL)
        recorder.hold(VideoDemoPace.DWELL_BEFORE_CLICK)
        recorder.click(state.cursor) { }
        // O voo da conta até o buraco: no mínimo 1,6 s de quadros intermediários.
        recorder.animate(VideoDemoPace.MIN_ANIMATION) { fraction ->
            progress = HudMinimizeProgress(mapOf(target to fraction), moving = fraction < 1f)
        }
        accounts = minimizedFirst
        progress = HudMinimizeProgress(mapOf(target to 1f), moving = false)
        recorder.hold(VideoDemoPace.STEP_HOLD)
        // Lista do buraco (índice sentinela = quantidade de contas): "N minimizadas".
        sizes = listSizes
        balloonIndex = accounts.size
        recorder.animate(VideoDemoPace.MIN_ANIMATION) {}
        recorder.hold(VideoDemoPace.STEP_HOLD)
        recorder.moveCursor(state.cursor, HOLE_ROW_X.dp, HOLE_ROW_Y.dp, VideoDemoPace.CURSOR_TRAVEL)
        recorder.moveMouse(HOLE_ROW_X, HOLE_ROW_Y)
        recorder.hold(VideoDemoPace.DWELL_BEFORE_CLICK)
        // A altura do balão troca no mesmo instante em que a conta abre, nunca antes.
        recorder.click(state.cursor) {
            recorder.clickMouse(HOLE_ROW_X, HOLE_ROW_Y)
            sizes = accountSizes
        }
        recorder.animate(VideoDemoPace.MIN_ANIMATION) {}
        recorder.hold(VideoDemoPace.STEP_HOLD)
        recorder.moveCursor(state.cursor, RESTORE_BUTTON_X.dp, RESTORE_BUTTON_Y.dp, VideoDemoPace.CURSOR_TRAVEL)
        recorder.hold(VideoDemoPace.DWELL_BEFORE_CLICK)
        recorder.click(state.cursor) { }
        accounts = initial
        recorder.animate(VideoDemoPace.MIN_ANIMATION) { fraction ->
            progress = HudMinimizeProgress(mapOf(target to 1f - fraction), moving = fraction < 1f)
        }
        progress = HudMinimizeProgress(emptyMap(), moving = false)
        recorder.hold(VideoDemoPace.OUTRO_HOLD)
    }, sceneWidth, sceneHeight, VIDEO_SCALE, transparent = true)
}
