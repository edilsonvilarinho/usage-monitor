package com.usagemonitor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.usagemonitor.data.diagnostics.AppLogFile
import java.awt.Color
import java.awt.GraphicsDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * A HUD perde o canal alfa ao cruzar de monitor, e este arquivo o devolve
 * (issue #426, relatada na #416).
 *
 * **O que acontece.** A janela nasce no monitor padrão e, por ser
 * `transparent=true`, o AWT escolhe ali uma `GraphicsConfiguration` de 32 bits.
 * Quando ela cruza para outro `GraphicsDevice`, o AWT a troca pela
 * **configuração padrão do monitor de destino**, que é de 24 bits. Uma janela
 * X11 de 24 bits não tem canal alfa: ela não pode ser transparente e pinta o
 * retângulo inteiro de preto — 442×557 sobre todas as janelas, engolindo clique
 * (fora do Windows a janela ainda não tem recorte, #340).
 *
 * **Medido** no Bazzite 44 (KDE Plasma, Wayland com XWayland, dois monitores),
 * com uma sonda em AWT puro amostrada contra o `xwininfo`:
 *
 * | Etapa | Device | `pixelSize` | `xwininfo` |
 * |---|---|---|---|
 * | nasce no primário | `:0.0` | 32 | 32bpp |
 * | movida ao secundário | `:0.1` | 24 | 24bpp |
 * | depois do reparo | `:0.1` | 32 | 32bpp |
 * | **de volta ao primário** | `:0.0` | **24** | **24bpp** |
 * | reparo no primário | `:0.0` | 32 | 32bpp |
 *
 * Voltar para o primário **também** perde o alfa: a configuração padrão dos
 * dois monitores é de 24 bits. O defeito é cruzar a fronteira, não o monitor de
 * destino — e por isso o relato original diz "travado para sempre": sem este
 * reparo, só reiniciar o app devolve a transparência.
 *
 * **O reparo.** `java.awt.Window.setBackground` só troca a
 * `GraphicsConfiguration` por uma com alfa quando o **alfa antigo é 255 e o novo
 * é menor**. O fundo da HUD já é alfa 0, então repetir `Color(0,0,0,0)` é um
 * no-op — foi exatamente nisso que a primeira tentativa (`a1812d57`) tropeçou,
 * registrando `reaplicar=ok` sem ter feito nada. Passar `null` antes faz o AWT
 * ler alfa 255; aí a condição passa, ele chama `setGraphicsConfiguration` com a
 * configuração que tem alfa e recria a janela nativa com 32 bits.
 *
 * **Só no Linux** ([hudTranslucencyRepairNeeded]). No Windows a janela nasce e
 * fica com alfa — há versões em produção medindo isso; o macOS não foi medido.
 * Comportamento nativo medido numa plataforma nasce restrito a ela (#340).
 */

/**
 * Teto de tentativas por monitor. O reparo recria a janela nativa: se ele não
 * pegar, repetir a cada meio segundo custaria um pisca eterno. Três é o bastante
 * para cobrir uma corrida com o compositor e pouco para não virar laço.
 */
internal const val HUD_TRANSLUCENCY_REPAIR_ATTEMPTS = 3

/**
 * Intervalo entre leituras. O AWT não avisa quando troca a
 * `GraphicsConfiguration` de uma janela — não há evento nem property change
 * para isso —, então a verificação é por sondagem. Meio segundo é curto o
 * bastante para o preto não durar e barato o bastante para uma leitura de campo.
 */
internal const val HUD_TRANSLUCENCY_POLL_INTERVAL_MILLIS = 500L

/** O que o AWT responde sobre o alfa da janela agora. `null` = não medido. */
internal data class HudTranslucencyState(
    val deviceId: String?,
    /** O monitor aceita alfa por pixel? É a pergunta de ambiente. */
    val perPixelTranslucent: Boolean?,
    /** A configuração **desta janela** tem alfa? É a pergunta que o defeito muda. */
    val translucencyCapable: Boolean?,
    /** 32 com alfa, 24 sem. É o `Depth:` do `xwininfo` visto de dentro do app. */
    val pixelSize: Int?
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
        pixelSize = runCatching { configuration?.colorModel?.pixelSize }.getOrNull()
    )
}

/**
 * Vale tentar o reparo? Função pura, para a decisão caber num teste sem abrir
 * janela — a suíte não exercita o sistema de janelas em plataforma nenhuma.
 *
 * Repara só quando o monitor **aceita** alfa por pixel e a configuração da
 * janela **não** é a que tem alfa: é essa diferença que caracteriza o defeito.
 * Ambiente que simplesmente não compõe alfa responde `perPixelTranslucent=false`
 * e não entra aqui — ali não há o que recuperar, e quem cuida é o recorte.
 */
internal fun hudTranslucencyRepairNeeded(
    platform: AutoStartManager.Platform,
    devicePerPixelTranslucent: Boolean?,
    configTranslucencyCapable: Boolean?,
    attempts: Int
): Boolean {
    if (platform != AutoStartManager.Platform.LINUX) {
        return false
    }
    // `null` é não medido: sem medida não se mexe na janela.
    if (devicePerPixelTranslucent != true || configTranslucencyCapable != false) {
        return false
    }
    return attempts < HUD_TRANSLUCENCY_REPAIR_ATTEMPTS
}

/** O que o reparo encontrou e o que deixou. */
internal data class HudTranslucencyRepair(
    val deviceId: String?,
    val capableBefore: Boolean?,
    val capableAfter: Boolean?,
    val pixelSizeBefore: Int?,
    val pixelSizeAfter: Int?,
    val attempt: Int,
    /** `null` quando o AWT aceitou a troca. */
    val error: String?,
    /** Posição, tamanho, `alwaysOnTop` e opacidade reafirmados no peer novo. */
    val restoreError: String?
)

/**
 * Curta de propósito: a fábrica da trilha corta em 200 caracteres. É esta linha
 * que um relato futuro mostra para dizer se o reparo pegou — por isso ela traz a
 * profundidade **antes e depois**, que é a medida que faltava na #416.
 */
internal fun hudTranslucencyRepairMessage(repair: HudTranslucencyRepair): String {
    val depth = "${repair.pixelSizeBefore ?: "?"}->${repair.pixelSizeAfter ?: "?"}"
    val capable = "${repair.capableBefore ?: "?"}->${repair.capableAfter ?: "?"}"
    val outcome = repair.error?.let { reason -> "falha=$reason" } ?: "restaurar=${repair.restoreError ?: "ok"}"
    return "hud-alfa ${repair.deviceId ?: "?"} capaz=$capable profundidade=$depth " +
        "tentativa=${repair.attempt}; $outcome"
}

/**
 * Devolve o alfa à janela, na EDT. Reafirma depois o que a recriação do peer
 * nativo pode não herdar — posição, tamanho, `alwaysOnTop` e opacidade. O
 * recorte **não** entra aqui: `window.shape` tem donos fixos (#340), e quem o
 * reaplica é o host.
 */
internal fun repairHudTranslucency(window: java.awt.Window, attempt: Int): HudTranslucencyRepair {
    val before = readHudTranslucencyState(window)
    val bounds = runCatching { window.bounds }.getOrNull()
    val alwaysOnTop = runCatching { window.isAlwaysOnTop }.getOrNull()
    val opacity = runCatching { window.opacity }.getOrNull()
    val error = runCatching {
        // `null` faz o AWT ler alfa 255. Só então a condição interna
        // (alfaAntigo == 255 && alfaNovo < 255) passa e ele troca a
        // GraphicsConfiguration pela que tem alfa. Repetir o alfa 0 é no-op.
        window.background = null
        window.background = Color(0, 0, 0, 0)
    }.exceptionOrNull()
    val restoreError = runCatching {
        if (bounds != null && window.bounds != bounds) {
            window.bounds = bounds
        }
        if (alwaysOnTop != null && window.isAlwaysOnTop != alwaysOnTop) {
            window.isAlwaysOnTop = alwaysOnTop
        }
        if (opacity != null && window.opacity != opacity) {
            window.opacity = opacity
        }
    }.exceptionOrNull()
    val after = readHudTranslucencyState(window)
    return HudTranslucencyRepair(
        deviceId = after.deviceId ?: before.deviceId,
        capableBefore = before.translucencyCapable,
        capableAfter = after.translucencyCapable,
        pixelSizeBefore = before.pixelSize,
        pixelSizeAfter = after.pixelSize,
        attempt = attempt,
        error = error?.let(::hudTranslucencyFailureOf),
        restoreError = restoreError?.let(::hudTranslucencyFailureOf)
    )
}

/**
 * Comprimento máximo do motivo da falha. A fábrica da trilha corta a linha
 * inteira em 200 caracteres, e o resto dela (monitor, capacidade, profundidade,
 * tentativa) já come perto de 90: sem este teto, uma mensagem de exceção longa
 * empurraria o número que interessa para fora do log.
 */
internal const val HUD_TRANSLUCENCY_FAILURE_LENGTH = 100

/** Tipo e mensagem, sem pilha: a linha do log tem 200 caracteres. */
internal fun hudTranslucencyFailureOf(error: Throwable): String {
    val name = error::class.simpleName ?: "Throwable"
    val message = error.message?.takeIf(String::isNotBlank) ?: return name
    val reason = "$name: $message"
    if (reason.length <= HUD_TRANSLUCENCY_FAILURE_LENGTH) {
        return reason
    }
    return reason.take(HUD_TRANSLUCENCY_FAILURE_LENGTH - 3) + "..."
}

/**
 * Observa o alfa da janela, o repara quando ele cai, e devolve se a janela
 * **tem** alfa agora — que é o que decide o recorte de emergência do host.
 *
 * O gatilho é a **falta de alfa**, não a troca de monitor: com um caminho só
 * cobre o app que abre direto no secundário (o caso do relato), o arrasto nos
 * dois sentidos, e ainda o monitor religado e a sessão desbloqueada, que também
 * fazem o AWT renegociar a configuração.
 *
 * [repairEnabled] fica falso durante o arrasto: o reparo recria o peer nativo,
 * e fazer isso no meio do gesto derrubaria os eventos de ponteiro. O efeito
 * re-roda ao fim do arrasto e verifica antes da primeira espera, então o preto
 * não sobrevive ao soltar.
 *
 * Fora do Linux a sondagem nem começa: não há o que reparar, e o recorte dessas
 * plataformas não consulta o alfa. Ali a resposta é um `true` constante.
 */
@Composable
internal fun rememberHudWindowAlpha(
    window: java.awt.Window,
    repairEnabled: Boolean,
    platform: AutoStartManager.Platform = AutoStartManager.currentPlatform(),
    appLog: AppLogFile = AppLogFile()
): Boolean {
    // Nasce `true`: o caso comum é a janela ter alfa, e o recorte de emergência
    // não pode piscar no primeiro quadro, antes da primeira medida.
    var hasAlpha by remember { mutableStateOf(true) }
    LaunchedEffect(window, repairEnabled, platform) {
        if (platform != AutoStartManager.Platform.LINUX || !repairEnabled) {
            return@LaunchedEffect
        }
        var deviceId: String? = null
        var attempts = 0
        while (true) {
            // O corpo do efeito já roda na EDT; `awaitAwtEventTurn` garante que
            // a operação de janela não acontece dentro da pintura do Skia.
            awaitAwtEventTurn()
            val state = readHudTranslucencyState(window)
            if (state.deviceId != deviceId) {
                deviceId = state.deviceId
                attempts = 0
            }
            val needed = hudTranslucencyRepairNeeded(
                platform = platform,
                devicePerPixelTranslucent = state.perPixelTranslucent,
                configTranslucencyCapable = state.translucencyCapable,
                attempts = attempts
            )
            if (needed) {
                attempts += 1
                val repair = repairHudTranslucency(window, attempts)
                // `null` é não medido: só uma medida negativa acende o recorte.
                hasAlpha = repair.capableAfter != false
                withContext(Dispatchers.IO) {
                    appLog.info("hud-alpha", hudTranslucencyRepairMessage(repair))
                }
            } else {
                hasAlpha = state.translucencyCapable != false
            }
            delay(HUD_TRANSLUCENCY_POLL_INTERVAL_MILLIS)
        }
    }
    return hasAlpha
}
