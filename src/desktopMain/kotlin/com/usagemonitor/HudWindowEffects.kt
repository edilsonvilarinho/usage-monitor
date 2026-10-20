package com.usagemonitor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.window.WindowState
import kotlin.math.ceil
import kotlin.math.floor
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

/** Quando a HUD recorta a área de clique da janela parada por `Window.shape`. */
internal enum class HudHitRegionMode {
    /** Windows: medido na #294 (C11), é o comportamento normal da janela parada. */
    ALWAYS,

    /**
     * Linux: **só** quando a janela está sem alfa. Com a transparência
     * funcionando — o caso normal depois do reparo da #426 — `Window.shape` não
     * é tocado, e o balão cortado da #340 não tem como voltar. Sem alfa, a
     * janela pintaria 442×557 de preto sobre tudo, engolindo clique; recortada
     * ao notch, o pior caso vira um notch sozinho.
     */
    ONLY_WITHOUT_ALPHA,

    /** macOS e o resto: nunca foi medido, e fica do lado seguro. */
    NEVER
}

/**
 * O recorte por plataforma. Era um booleano "só Windows" (#340) e virou três
 * estados quando o Linux ganhou o recorte de emergência (#426).
 */
internal fun hudHitRegionMode(platform: AutoStartManager.Platform): HudHitRegionMode = when (platform) {
    AutoStartManager.Platform.WINDOWS -> HudHitRegionMode.ALWAYS
    AutoStartManager.Platform.LINUX -> HudHitRegionMode.ONLY_WITHOUT_ALPHA
    AutoStartManager.Platform.MACOS, AutoStartManager.Platform.OTHER -> HudHitRegionMode.NEVER
}

/** O recorte vale **agora**, com o que se sabe do alfa da janela. */
internal fun hudHitRegionActive(mode: HudHitRegionMode, hasAlpha: Boolean): Boolean = when (mode) {
    HudHitRegionMode.ALWAYS -> true
    HudHitRegionMode.ONLY_WITHOUT_ALPHA -> !hasAlpha
    HudHitRegionMode.NEVER -> false
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

/** Pixels da AWT a partir do retângulo em dp da composição. */
private fun DpRect.toAwtRectangle(scale: Float): java.awt.Rectangle {
    val left = floor(this.left.value * scale).toInt()
    val top = floor(this.top.value * scale).toInt()
    val right = ceil(this.right.value * scale).toInt()
    val bottom = ceil(this.bottom.value * scale).toInt()
    return java.awt.Rectangle(left, top, right - left, bottom - top)
}

/**
 * O que fazer com `window.shape`: [touch] falso é **não mexer**, que é diferente
 * de mexer passando `null`.
 */
internal data class HudHitRegionTarget(
    val touch: Boolean,
    val shape: java.awt.Rectangle?
)

/**
 * A decisão do recorte, separada da janela para caber num teste.
 *
 * Três casos. Com [requested], aplica — é o recorte do notch parado.
 * Sem [requested] e sem nada aplicado antes, **não toca**: é o Linux com alfa,
 * onde o recorte nunca valeu. Sem [requested] mas com algo aplicado, limpa — e
 * aí [clearWithFullBounds] decide entre o `null` (Windows, onde ele limpa) e o
 * retângulo do tamanho da janela (X11, onde o `null` não limpa, #340).
 *
 * O caso do meio custou uma regressão medida na #426: limpar o que nunca foi
 * aplicado punha um retângulo do tamanho da janela **na primeira composição**,
 * antes de as contas chegarem e a janela crescer. O retângulo ficava congelado
 * naquele tamanho e cortava a pintura: a barra aparecia com o anel de cima e
 * nada embaixo.
 */
internal fun hudHitRegionTarget(
    requested: java.awt.Rectangle?,
    hasApplied: Boolean,
    clearWithFullBounds: Boolean,
    windowWidth: Int,
    windowHeight: Int
): HudHitRegionTarget {
    if (requested != null) {
        return HudHitRegionTarget(touch = true, shape = requested)
    }
    if (!hasApplied) {
        return HudHitRegionTarget(touch = false, shape = null)
    }
    if (!clearWithFullBounds || windowWidth <= 0 || windowHeight <= 0) {
        return HudHitRegionTarget(touch = true, shape = null)
    }
    return HudHitRegionTarget(touch = true, shape = java.awt.Rectangle(0, 0, windowWidth, windowHeight))
}