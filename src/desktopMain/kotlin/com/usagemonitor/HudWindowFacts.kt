package com.usagemonitor

import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.window.WindowPosition
import com.usagemonitor.data.diagnostics.AppLogFile
import com.usagemonitor.presentation.ui.windowDragMessage
import com.usagemonitor.presentation.ui.windowDragSink
import java.awt.GraphicsDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * O que o sistema fez com a janela da HUD, lido de volta depois de mapeada (issue #416).
 *
 * A #416 chegou como um retângulo preto longe da borda. Três perguntas separam as
 * hipóteses, e nenhuma era gravada: o ambiente diz suportar janela translúcida?
 * Que render API o Skiko usou de fato (ele cai para outro sozinho)? A janela
 * ficou onde o app pediu, ou o compositor a pôs em outro lugar?
 *
 * Uma linha `info` por processo, [HUD_FACTS_DELAY_MILLIS] depois de a janela
 * existir — o compositor aplica posição e o Skiko troca de render API depois do
 * primeiro quadro, e ler no mesmo instante mediria o pedido, não a resposta.
 */
internal data class HudWindowFacts(
    val requestedX: Int,
    val requestedY: Int,
    val requestedWidth: Int,
    val requestedHeight: Int,
    val actualX: Int,
    val actualY: Int,
    val actualWidth: Int,
    val actualHeight: Int,
    /** `null` = não medido (a pergunta lançou), nunca "medido e falso". */
    val perPixelTranslucent: Boolean?,
    val uniformTranslucent: Boolean?,
    val perPixelTransparent: Boolean?,
    val renderApi: String?,
    /**
     * `GraphicsDevice.getIDstring()` do monitor em que a janela está (`:0.1`,
     * `\\Display2`). A transparência é alocada por `GraphicsConfiguration`, e
     * cada monitor tem a sua: sem o id, um relato não diz em qual deles a janela
     * nasceu, e a troca de monitor fica invisível no log (#426).
     */
    val deviceId: String? = null,
    /**
     * Bits por pixel da `GraphicsConfiguration` da janela: 32 com alfa, 24 sem.
     * É o `Depth:` do `xwininfo` visto de dentro do app, e é o número que diz se
     * a barra **pode** ser transparente neste monitor (#426).
     */
    val pixelSize: Int? = null
)

/**
 * Curta de propósito: a mensagem do log passa pela fábrica da trilha, que corta
 * em 200 caracteres. Rótulos de uma palavra, números sem unidade (pixels AWT).
 */
internal fun hudWindowFactsMessage(facts: HudWindowFacts): String {
    val requested = "${facts.requestedX},${facts.requestedY} ${facts.requestedWidth}x${facts.requestedHeight}"
    val actual = "${facts.actualX},${facts.actualY} ${facts.actualWidth}x${facts.actualHeight}"
    val moved = if (requested == actual) "igual" else "diferente"
    return "hud pedido $requested obtido $actual ($moved); " +
        "alfa px=${facts.perPixelTranslucent} uni=${facts.uniformTranslucent} " +
        "recorte=${facts.perPixelTransparent}; render=${facts.renderApi}; " +
        "device=${facts.deviceId ?: "?"} ${facts.pixelSize ?: "?"}bpp"
}

internal suspend fun recordHudWindowFacts(
    window: java.awt.Window,
    requestedPosition: () -> WindowPosition,
    requestedSize: () -> DpSize,
    appLog: AppLogFile = AppLogFile()
) {
    delay(HUD_FACTS_DELAY_MILLIS)
    // Lido no EDT (o contexto do efeito), que é quem mexe na janela.
    val facts = runCatching { readHudWindowFacts(window, requestedPosition(), requestedSize()) }.getOrNull() ?: return
    withContext(Dispatchers.IO) {
        appLog.info("hud-window", hudWindowFactsMessage(facts))
    }
}

private fun readHudWindowFacts(window: java.awt.Window, position: WindowPosition, size: DpSize): HudWindowFacts {
    val device = window.graphicsConfiguration?.device
    fun supports(kind: GraphicsDevice.WindowTranslucency): Boolean? =
        device?.let { graphicsDevice -> runCatching { graphicsDevice.isWindowTranslucencySupported(kind) }.getOrNull() }
    return HudWindowFacts(
        // dp da composição = unidade lógica da AWT no Compose Desktop.
        requestedX = position.x.value.toInt(),
        requestedY = position.y.value.toInt(),
        requestedWidth = size.width.value.toInt(),
        requestedHeight = size.height.value.toInt(),
        actualX = window.x,
        actualY = window.y,
        actualWidth = window.width,
        actualHeight = window.height,
        perPixelTranslucent = supports(GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSLUCENT),
        uniformTranslucent = supports(GraphicsDevice.WindowTranslucency.TRANSLUCENT),
        perPixelTransparent = supports(GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSPARENT),
        renderApi = runCatching { (window as? ComposeWindow)?.renderApi?.name }.getOrNull(),
        deviceId = runCatching { device?.getIDstring() }.getOrNull(),
        pixelSize = runCatching { window.graphicsConfiguration?.colorModel?.pixelSize }.getOrNull()
    )
}

/** Depois do primeiro quadro e da resposta do compositor; antes de o usuário mexer. */
internal const val HUD_FACTS_DELAY_MILLIS = 2_000L

/**
 * O arrasto da HUD visto de fora (issue #416): onde a janela estava ao começar,
 * a última posição que o app pediu e onde ela está ao soltar. A janela é lida de
 * [appMainWindow] — a HUD é a âncora do app — para a chamada caber numa linha em
 * cada ponta do gesto, sem estado novo no host.
 */
internal object HudDragProbe {
    @Volatile
    private var start: java.awt.Point? = null

    fun begin() {
        start = runCatching { appMainWindow?.location }.getOrNull()
    }

    fun finish(requested: DpOffset?) {
        val requestedPoint = requested?.let { offset -> java.awt.Point(offset.x.value.toInt(), offset.y.value.toInt()) }
        val actual = runCatching { appMainWindow?.location }.getOrNull()
        runCatching {
            windowDragSink(windowDragMessage("hud", start, requestedPoint, actual))
        }
        start = null
    }
}
