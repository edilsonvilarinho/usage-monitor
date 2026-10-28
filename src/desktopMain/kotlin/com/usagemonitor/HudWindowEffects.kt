package com.usagemonitor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.window.WindowState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Os efeitos da janela da HUD e o recorte da área de clique.
 *
 * Saiu de `HudWindow.kt` quando o arquivo passou de 800 linhas e
 * `HudWindowHost` passou de 300 (#426). É também o **dono do recorte nativo**:
 * `window.shape` só vive aqui e em `DesktopWindowFrame.kt`, e quem o move
 * precisa decidir, com medição, em que plataformas ele vale (issue #340).
 */

/**
 * Tudo o que a janela da HUD faz **com a própria janela**, uma vez composta:
 * entregar a âncora do app, registrar o que o sistema fez com o pedido,
 * observar o alfa, aplicar a opacidade e o recorte.
 *
 * Devolve se a janela tem alfa agora — é o que decide o recorte de emergência.
 */
@Composable
internal fun HudWindowEffects(
    window: java.awt.Window,
    windowState: WindowState,
    onWindowReady: suspend (java.awt.Window) -> Unit,
    windowOpacityPercent: Int,
    hitRegionMode: HudHitRegionMode,
    /** O recorte do notch parado; nulo quando o balão está aberto ou no arrasto. */
    restHitRegion: DpRect?,
    scale: Float,
    platform: AutoStartManager.Platform,
    /** Em arrasto: a medida do alfa é refeita assim que o gesto termina. */
    dragging: Boolean,
    onAlphaLost: suspend (HudTranslucencyState) -> Unit
): Boolean {
    LaunchedEffect(window) {
        onWindowReady(window)
    }
    // O que o sistema fez com o pedido de posição e de alfa (#416).
    LaunchedEffect(window) {
        recordHudWindowFacts(window, { windowState.position }, { windowState.size })
    }
    // Só leitura: a janela já nasce com alfa (#426). Isto diz ao recorte se o
    // ambiente entregou o que prometeu, e dispara o renascimento quando um
    // arrasto fez o AWT trocar a configuração da janela.
    val hasAlpha = rememberHudWindowAlpha(window, dragging = dragging, onAlphaLost = onAlphaLost)
    LaunchedEffect(windowOpacityPercent) {
        applyHudWindowOpacity(window, windowOpacityPercent, platform)
    }
    // Windows sempre; Linux só sem alfa; macOS nunca. A região nula **tira** o
    // recorte, e por isso o aplicador segue composto quando ele não vale mais.
    val region = restHitRegion.takeIf { hudHitRegionActive(hitRegionMode, hasAlpha) }
    ApplyHudHitRegion(window, region, scale, mode = hitRegionMode)
    return hasAlpha
}

/**
 * Síncrono na aplicação da composição: o quadro seguinte, que o hover espera
 * antes de abrir o balão, já sai sem o recorte.
 *
 * Em [HudHitRegionMode.NEVER] não aplica nada — só grava que pulou, para o
 * próximo relato vir com o que a HUD fez (issue #342).
 */
@Composable
private fun ApplyHudHitRegion(window: java.awt.Window, region: DpRect?, scale: Float, mode: HudHitRegionMode) {
    val diagnostics = remember { HudWindowDiagnostics() }
    // No X11 o recorte nulo não limpa (#340): ali a limpeza é o retângulo cheio.
    val applier = remember(mode) {
        HudHitRegionApplier(
            onEvent = diagnostics::record,
            clearWithFullBounds = mode == HudHitRegionMode.ONLY_WITHOUT_ALPHA
        )
    }
    LaunchedEffect(mode) {
        val event = if (mode == HudHitRegionMode.NEVER) HudHitRegionEvent.SKIPPED else HudHitRegionEvent.ENABLED
        withContext(Dispatchers.IO) { diagnostics.record(event) }
    }
    if (mode == HudHitRegionMode.NEVER) {
        return
    }
    SideEffect {
        applier.apply(window, region, scale)
    }
}

/**
 * Aplica o recorte de clique da janela parada, ou o tira com a região nula.
 *
 * Guarda o último retângulo aplicado porque `Window.getShape()` devolve uma cópia
 * em `Path2D`, que nunca é igual ao `Rectangle` pedido: comparar por ela
 * refaria a região da janela no sistema a cada recomposição.
 *
 * `Window.shape` exige suporte a janela recortada (`PERPIXEL_TRANSPARENT`). Sem
 * ele a HUD continua funcionando sem recorte: a área do balão volta a engolir
 * clique, mas o notch não pisca — o defeito menor dos dois. Em
 * [HudHitRegionMode.NEVER] o host nem o compõe.
 *
 * **Recorte nunca aplicado não é limpo** ([hasApplied]): no caminho em que o
 * recorte não vale — Linux com alfa — `window.shape` fica intocado. Medido na
 * #426: limpar mesmo assim, com o retângulo do tamanho da janela, cortava a
 * pintura. O retângulo era calculado na primeira composição, antes de as contas
 * chegarem e a janela crescer, e ficava congelado naquele tamanho; a barra
 * aparecia com o anel de cima e nada embaixo. É o balão cortado da #340 por
 * outro caminho.
 *
 * [clearWithFullBounds] é a outra lição da #340: no X11 `window.shape = null`
 * **não** tira o recorte. Quando há mesmo o que limpar — o alfa voltou depois
 * de o recorte de emergência ter entrado —, a limpeza é o retângulo do tamanho
 * da janela **naquele instante**, e ele é recalculado quando a janela muda de
 * tamanho, porque entra na comparação com o último aplicado.
 */
private class HudHitRegionApplier(
    private val onEvent: (HudHitRegionEvent, String?) -> Unit,
    private val clearWithFullBounds: Boolean = false
) {
    private var applied: java.awt.Rectangle? = null
    private var hasApplied = false

    fun apply(window: java.awt.Window, region: DpRect?, scale: Float) {
        val size = runCatching { window.size }.getOrNull()
        val target = hudHitRegionTarget(
            requested = region?.toAwtRectangle(scale),
            hasApplied = hasApplied,
            clearWithFullBounds = clearWithFullBounds,
            windowWidth = size?.width ?: 0,
            windowHeight = size?.height ?: 0
        )
        if (!target.touch) {
            return
        }
        val shape = target.shape
        if (hasApplied && shape == applied) {
            return
        }
        hasApplied = true
        applied = shape
        runCatching { window.shape = shape }
            .onFailure { error -> onEvent(HudHitRegionEvent.SET_FAILED, "${error::class.simpleName}: ${error.message}") }
            .onSuccess {
                if (shape == null && window.shape != null) {
                    onEvent(HudHitRegionEvent.CLEAR_NOT_EFFECTIVE, null)
                }
            }
    }
}
