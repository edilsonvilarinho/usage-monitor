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
     * `GraphicsDevice.getIDstring()` da janela lida (issue #416 secundário). O
     * primário e o secundário do X11 ficam em devices diferentes, e a
     * transparência do Skiko/OpenGL é alocada na criação com a visual do device
     * de origem. Sem o id, a próxima troca de monitor é invisível no log.
     */
    val deviceId: String? = null
)

/**
 * Curta de propósito: a mensagem do log passa pela fábrica da trilha, que corta
 * em 200 caracteres. Rótulos de uma palavra, números sem unidade (pixels AWT).
 */
internal fun hudWindowFactsMessage(facts: HudWindowFacts): String {
    val requested = "${facts.requestedX},${facts.requestedY} ${facts.requestedWidth}x${facts.requestedHeight}"
    val actual = "${facts.actualX},${facts.actualY} ${facts.actualWidth}x${facts.actualHeight}"
    val moved = if (requested == actual) "igual" else "diferente"
    val device = facts.deviceId ?: "?"
    return "hud pedido $requested obtido $actual ($moved); " +
        "alfa px=${facts.perPixelTranslucent} uni=${facts.uniformTranslucent} " +
        "recorte=${facts.perPixelTransparent}; render=${facts.renderApi}; " +
        "device=$device"
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
        deviceId = runCatching { device?.getIDstring() }.getOrNull()
    )
}

/** Depois do primeiro quadro e da resposta do compositor; antes de o usuário mexer. */
internal const val HUD_FACTS_DELAY_MILLIS = 2_000L

/**
 * Intervalo entre leituras do `GraphicsDevice` da janela (issue #416 secundário).
 *
 * 500ms é o bastante para reagir a um arrasto entre monitores sem custar a EDT
 * ociosa. Mais agressivo vira barulho no log quando a janela só recebe um evento
 * de mover dentro do mesmo device; mais lento deixaria o balão preto visível
 * por até meio segundo depois do cruzamento.
 */
internal const val HUD_DEVICE_POLL_INTERVAL_MILLIS = 500L

/**
 * Lê o `GraphicsDevice` corrente da janela e o devolve como `String` (`\Display1`,
 * `:0.0`…). `null` quando o AWT não tem como responder — a janela ainda não
 * foi mapeada, ou o sistema está saindo.
 */
internal fun hudDeviceIdOf(window: java.awt.Window): String? =
    runCatching { window.graphicsConfiguration?.device?.getIDstring() }.getOrNull()

/**
 * O que o AWT/Skiko responderam **na nova** `GraphicsDevice` depois da janela
 * mudar de monitor. Mesmo conjunto de perguntas que [HudWindowFacts] faz no
 * arranque, agora aplicado ao device de destino — é o que separa a
 * transparência perdida (secundário não suporta alfa) da transparência nunca
 * recuperada (Skiko preso ao contexto GL antigo, o caso da #416).
 */
internal data class HudDeviceChangeFacts(
    val prevId: String,
    val newId: String,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val perPixelTranslucent: Boolean?,
    val uniformTranslucent: Boolean?,
    val perPixelTransparent: Boolean?,
    val renderApi: String?,
    /**
     * Resultado do reapply: `setBackground(Color(0,0,0,0)) + invalidate + repaint`.
     * `ok` aqui só quer dizer "AWT não lançou" — o Skiko pode continuar preso
     * ao contexto do device de origem. O log abaixo diz qual `renderApi` ficou
     * ativa depois; se a reapply acordou o stack, ele é o mesmo; se não, o
     * `backgroundAlpha` e o `isOpaque` dizem o que o peer está pintando.
     */
    val reapplied: Boolean,
    val reapplyError: String?,
    /**
     * A janela foi **re-mapeada** (toggle de `isVisible` + nudge de `opacity`).
     * É a saída nuclear quando a reapply suave não acorda o Skiko: o peer AWT
     * sai e volta, e o renderer é alocado de novo com a visual do device de
     * destino. Custa um pisca, mas é o que tira a janela do estado opaco no
     * secundário. Os campos abaixo (`isDisplayable`, `isShowing`) confirmam
     * se o peer voltou.
     */
    val visibilityRemapped: Boolean,
    val remapError: String?,
    /** O que o peer AWT está pintando **depois** da reapply. */
    val backgroundAlpha: Int?,
    val isOpaque: Boolean?,
    /** O peer AWT foi **pego de volta** pelo toolkit depois do remap. */
    val isDisplayable: Boolean?,
    /** O peer AWT está visível na tela. */
    val isShowing: Boolean?,
    /**
     * O `BufferStrategy` foi recriado. Mudar o tamanho por 1px e voltar
     * força o AWT a descartar o buffer atual e alocar outro; quando o
     * device de destino não tem o mesmo formato de pixel, é a única forma
     * de puxar um back buffer com alfa.
     */
    val bufferRecreated: Boolean,
    val bufferRecreateError: String?
)

/**
 * Curta de propósito: a mesma fábrica da trilha corta em 200, e esta é a
 * linha que aparece no log depois de uma troca de monitor. Cabe inteira mesmo
 * com ids longos de device.
 */
internal fun hudDeviceChangeMessage(facts: HudDeviceChangeFacts): String {
    val position = "${facts.x},${facts.y} ${facts.width}x${facts.height}"
    val reapply = if (facts.reapplied) {
        "ok"
    } else {
        "falha=${facts.reapplyError ?: "?"}"
    }
    val remap = if (facts.visibilityRemapped) {
        "ok"
    } else if (facts.remapError != null) {
        "falha=${facts.remapError}"
    } else {
        "pulado"
    }
    val buffer = if (facts.bufferRecreated) {
        "recriado"
    } else if (facts.bufferRecreateError != null) {
        "falha=${facts.bufferRecreateError}"
    } else {
        "pulado"
    }
    val background = facts.backgroundAlpha?.let { "fundo=$it" } ?: "fundo=?"
    val opaque = facts.isOpaque?.let { "opaco=$it" } ?: "opaco=?"
    val displayable = facts.isDisplayable?.let { "mapeada=$it" } ?: "mapeada=?"
    val showing = facts.isShowing?.let { "mostrando=$it" } ?: "mostrando=?"
    return "hud-device ${facts.prevId}->${facts.newId} em $position; " +
        "alfa px=${facts.perPixelTranslucent} uni=${facts.uniformTranslucent} " +
        "recorte=${facts.perPixelTransparent}; render=${facts.renderApi}; " +
        "reaplicar=$reapply; remapear=$remap; buffer=$buffer; $background; $opaque; " +
        "$displayable; $showing"
}

/**
 * Grava a troca de monitor no `app.log` no mesmo formato das facts iniciais.
 * Disparado pelo `LaunchedEffect` que observa `window.graphicsConfiguration`;
 * a EDT é quem mede, a IO é quem escreve para a linha chegar ao disco mesmo
 * com a janela piscando.
 */
internal suspend fun recordHudDeviceChange(
    window: java.awt.Window,
    facts: HudDeviceChangeFacts,
    appLog: AppLogFile = AppLogFile()
) {
    withContext(Dispatchers.IO) {
        appLog.info("hud-device", hudDeviceChangeMessage(facts))
    }
}

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
