package com.usagemonitor

import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.window.WindowPosition
import com.usagemonitor.data.diagnostics.AppLogFile
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
    val renderApi: String?
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
        "recorte=${facts.perPixelTransparent}; render=${facts.renderApi}"
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
        renderApi = runCatching { (window as? ComposeWindow)?.renderApi?.name }.getOrNull()
    )
}

/** Depois do primeiro quadro e da resposta do compositor; antes de o usuário mexer. */
internal const val HUD_FACTS_DELAY_MILLIS = 2_000L
