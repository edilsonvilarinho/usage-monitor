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

/*
 * A leitura do alfa na janela AWT e a sondagem composta. É cola de janela e sai do
 * Kover por nome (onda 6.5 do plano `cobertura-95-execucao.md`); as decisões e as
 * linhas do `app.log` ficam em `HudWindowTranslucency.kt`, contadas e testadas.
 */


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
            if (hudTranslucencyChanged(previous, state)) {
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
