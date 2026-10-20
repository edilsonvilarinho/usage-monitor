package com.usagemonitor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.usagemonitor.data.diagnostics.AppLogFile
import java.awt.GraphicsDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * O alfa da janela da HUD, lido — **não** remendado (issue #426).
 *
 * **O defeito.** Uma janela transparente do AWT só guarda uma
 * `GraphicsConfiguration` de 32 bits no `GraphicsDevice` em que nasceu. Ao
 * cruzar para outro device, o AWT a troca pela **configuração padrão do
 * destino**, que no X11 é de 24 bits — e janela de 24 bits não tem canal alfa:
 * ela pinta o retângulo inteiro de preto, sobre todas as janelas e engolindo
 * clique.
 *
 * Medido no Bazzite 44 (KDE Plasma, Wayland com XWayland, dois monitores,
 * NVIDIA RTX 4070 SUPER), com sonda em AWT puro amostrada contra o `xwininfo`:
 * `32 → 24 → 32 → 24 → 32`. **Voltar para o primário também perde o alfa** — a
 * configuração padrão dos dois monitores é de 24 bits. O defeito é cruzar a
 * fronteira, não o monitor de destino.
 *
 * **Por que aqui não há remendo.** Houve um, e ele foi retirado. Devolver o
 * alfa a uma janela já aberta obriga o AWT a recriar a janela nativa (a visual
 * é propriedade dela, não dá para trocá-la em voo). Medido: o remendo devolvia
 * os 32 bits e o Compose **parava de emitir quadros** — com o dashboard vivo e
 * publicando a cada 60 s, a barra não mudou um pixel em 75 s, e só voltava a
 * pintar na recriação seguinte. Trocava o retângulo preto por uma barra
 * congelada.
 *
 * A correção está em [HudWindowFrame]: a janela **nasce** na configuração com
 * alfa do monitor certo e renasce quando esse monitor muda. O que sobra aqui é
 * a leitura, que alimenta a rede de segurança do recorte ([HudHitRegionMode]) e
 * o diagnóstico.
 */

/**
 * Intervalo entre leituras. O AWT não avisa quando troca a
 * `GraphicsConfiguration` de uma janela — não há evento nem property change
 * para isso —, então saber se o alfa caiu é por sondagem.
 *
 * Eram 500 ms, e foi medido o que isso custa: soltando a barra **de volta no
 * monitor de origem** depois de cruzar, o id do monitor não muda, nada dispara
 * o renascimento pela configuração, e a sondagem era a única a perceber — meio
 * segundo de retângulo preto do tamanho da janela encaixada, bem visível. São
 * duas leituras de campo por vez; 120 ms não pesa e encurta a tarja para o
 * limiar do perceptível.
 */
internal const val HUD_TRANSLUCENCY_POLL_INTERVAL_MILLIS = 120L

/** O que o AWT responde sobre o alfa da janela agora. `null` = não medido. */
internal data class HudTranslucencyState(
    val deviceId: String?,
    /** O monitor aceita alfa por pixel? É a pergunta de ambiente. */
    val perPixelTranslucent: Boolean?,
    /** A configuração **desta janela** tem alfa? É a pergunta que o defeito muda. */
    val translucencyCapable: Boolean?,
    /** 32 com alfa, 24 sem. É o `Depth:` do `xwininfo` visto de dentro do app. */
    val pixelSize: Int?,
    /**
     * Onde a janela estava na medida. Entrou porque o alfa cai de novo meio
     * segundo **depois** de a janela renascer certa, no mesmo monitor (#426):
     * sem a posição não dá para saber se ela encostou numa fronteira.
     */
    val bounds: java.awt.Rectangle? = null
)

internal fun readHudTranslucencyState(window: java.awt.Window): HudTranslucencyState {
    val configuration = runCatching { window.graphicsConfiguration }.getOrNull()
    val device = configuration?.device
    return HudTranslucencyState(
        deviceId = runCatching { device?.getIDstring() }.getOrNull(),
        perPixelTranslucent = device?.let { graphicsDevice ->
            runCatching {
                graphicsDevice.isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSLUCENT)
            }.getOrNull()
        },
        translucencyCapable = runCatching { configuration?.isTranslucencyCapable }.getOrNull(),
        pixelSize = runCatching { configuration?.colorModel?.pixelSize }.getOrNull(),
        bounds = runCatching { window.bounds }.getOrNull()
    )
}

/**
 * Se a janela tem alfa agora. `null` é não medido e vale como **tem**: a rede
 * de segurança recorta a janela, e recortar sem medida seria pior do que não
 * recortar.
 */
internal fun hudWindowHasAlpha(state: HudTranslucencyState): Boolean = state.translucencyCapable != false

/**
 * Observa o alfa da janela, sem tocar nela. Fora do Linux nem sonda: lá o
 * recorte não consulta o alfa, e a leitura não teria consumidor.
 */
@Composable
internal fun rememberHudWindowAlpha(
    window: java.awt.Window,
    /** Em arrasto: o fim do gesto reinicia a sondagem e mede na hora. */
    dragging: Boolean,
    platform: AutoStartManager.Platform = AutoStartManager.currentPlatform(),
    appLog: AppLogFile = AppLogFile(),
    /**
     * Chamado **de dentro da sondagem**, no quadro em que o alfa cai. Não pela
     * recomposição: o valor de retorno alimenta o recorte, mas depender dele
     * para disparar o renascimento não funcionou — a sondagem registrava
     * `capaz=false` e o efeito que observava o retorno nunca corria (#426).
     * Aqui o gatilho sai do mesmo laço que faz a medida.
     */
    onAlphaLost: suspend (HudTranslucencyState) -> Unit = {}
): Boolean {
    // Nasce `true`: o caso comum é a janela ter alfa, e o recorte de emergência
    // não pode piscar no primeiro quadro, antes da primeira medida.
    var hasAlpha by remember { mutableStateOf(true) }
    val currentOnAlphaLost by rememberUpdatedState(onAlphaLost)
    // Fora do laço: o efeito reinicia ao fim do arrasto, e o que já foi
    // registrado não pode virar linha repetida no log.
    val reported = remember(window) { arrayOfNulls<HudTranslucencyState>(1) }
    // [dragging] é chave de propósito: o fim do gesto **reinicia** o efeito, e a
    // primeira leitura acontece de imediato, sem esperar a sondagem. É o que
    // tira a tarja preta de ~500 ms de quem solta a barra de volta no monitor
    // de origem, onde o id não muda e nada mais dispara o renascimento (#426).
    LaunchedEffect(window, platform, dragging) {
        if (platform != AutoStartManager.Platform.LINUX) {
            return@LaunchedEffect
        }
        while (true) {
            val state = readHudTranslucencyState(window)
            val measured = hudWindowHasAlpha(state)
            hasAlpha = measured
            // Só a mudança vira linha: o que interessa num relato é o instante
            // em que o alfa caiu, não meio segundo de confirmação (#426).
            val previous = reported[0]
            val changed = previous == null ||
                previous.translucencyCapable != state.translucencyCapable ||
                previous.deviceId != state.deviceId
            if (changed) {
                reported[0] = state
                withContext(Dispatchers.IO) {
                    appLog.info("hud-alpha", hudTranslucencyStateMessage(state))
                }
                if (!measured) {
                    currentOnAlphaLost(state)
                }
            }
            delay(HUD_TRANSLUCENCY_POLL_INTERVAL_MILLIS)
        }
    }
    return hasAlpha
}

/**
 * Quantas vezes a janela pode renascer por perda de alfa, por monitor.
 *
 * O renascimento é a correção quando o AWT troca a configuração no meio de um
 * arrasto. Num ambiente que simplesmente **não compõe** alfa, porém, ele nunca
 * voltaria, e repetir seria um pisca eterno — depois do orçamento, quem assume
 * é o recorte de emergência ([HudHitRegionMode]). Três cobre uma corrida com o
 * compositor e para antes de virar laço.
 */
internal const val HUD_ALPHA_REBUILD_LIMIT = 3

/** O alfa da janela quando ele muda: é o instante que um relato precisa ver. */
internal fun hudTranslucencyStateMessage(state: HudTranslucencyState): String {
    val where = state.bounds?.let { r -> "${r.x},${r.y} ${r.width}x${r.height}" } ?: "?"
    return "hud-alfa estado ${state.deviceId ?: "?"} capaz=${state.translucencyCapable ?: "?"} " +
        "monitor=${state.perPixelTranslucent ?: "?"} profundidade=${state.pixelSize ?: "?"}bpp em $where"
}

/** A linha do renascimento no `app.log`, com a profundidade que motivou. */
internal fun hudAlphaRebuildMessage(state: HudTranslucencyState, attempt: Int): String =
    "hud-alfa renascer ${state.deviceId ?: "?"} capaz=${state.translucencyCapable ?: "?"} " +
        "profundidade=${state.pixelSize ?: "?"}bpp tentativa=$attempt"

/**
 * Registra que a janela vai renascer por ter perdido o alfa. A escrita vai
 * para a IO, porque a janela some logo em seguida.
 */
internal suspend fun recordHudAlphaRebuild(
    state: HudTranslucencyState,
    attempt: Int,
    appLog: AppLogFile = AppLogFile()
) {
    withContext(Dispatchers.IO) {
        appLog.info("hud-alpha", hudAlphaRebuildMessage(state, attempt))
    }
}
