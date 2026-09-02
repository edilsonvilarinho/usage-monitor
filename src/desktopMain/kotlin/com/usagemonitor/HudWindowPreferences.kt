package com.usagemonitor

import com.russhwolf.settings.PreferencesSettings
import kotlin.math.roundToInt

private const val HUD_WINDOW_X_KEY = "hudWindowX"
private const val HUD_WINDOW_Y_KEY = "hudWindowY"

/**
 * Onde a pílula HUD ficou da última vez (issue #164).
 *
 * A primeira versão ancorava no canto superior direito e ponto — e é justamente
 * ali que IDE, navegador e editor põem controles, que a pílula passava a cobrir
 * por ser `alwaysOnTop`. Com o arrasto, a escolha de onde ela mora é do usuário,
 * e uma escolha que não sobrevive ao fechamento do app é uma escolha que ele
 * refaz todo dia.
 *
 * **Chave própria, e não `MainWindowSnapshot`.** Aquele descreve a janela normal
 * e nunca carregou posição (a janela normal não precisava dela); o coletor que o
 * grava, aliás, ignora tudo enquanto o modo HUD está ligado, senão a pílula de
 * 24dp seria gravada como "tamanho normal" e o app nasceria nela.
 *
 * Mesmo armazenamento das demais preferências de moldura — registro no Windows,
 * plist no macOS, via `PreferencesSettings` — e **não** `~/.usage-monitor/`: ali
 * moram os segredos do time, e a posição de uma janela não é segredo. Mesmo
 * formato de `HistoryWindowPreferences`: `Int` em texto, os dois eixos ou
 * nenhum.
 */
internal data class PersistedHudPosition(
    val xDp: Int,
    val yDp: Int
)

/**
 * `null` quando nunca foi arrastada — quem chama cai no canto superior direito,
 * que continua sendo a posição de estreia. Meia posição gravada (um eixo sem o
 * outro) também é `null`: um eixo herdado e o outro default poria a pílula num
 * canto que ninguém escolheu.
 */
internal fun readPersistedHudPosition(settings: PreferencesSettings): PersistedHudPosition? {
    val xDp = settings.getStringOrNull(HUD_WINDOW_X_KEY)?.toIntOrNull()
    val yDp = settings.getStringOrNull(HUD_WINDOW_Y_KEY)?.toIntOrNull()

    if (xDp == null || yDp == null) {
        return null
    }

    return PersistedHudPosition(xDp = xDp, yDp = yDp)
}

/**
 * Grava a posição em que a pílula foi solta.
 *
 * Coordenada não finita (`Dp.Unspecified` chega como `NaN`) **não é gravada**:
 * ela significa "sem medida", e persistir `NaN` arredondado poria a janela em
 * zero na próxima abertura. A escrita é dos dois eixos ou de nenhum, para a
 * leitura acima nunca encontrar meia posição.
 */
internal fun persistHudPosition(settings: PreferencesSettings, xDp: Float, yDp: Float) {
    if (!xDp.isFinite() || !yDp.isFinite()) {
        return
    }

    settings.putString(HUD_WINDOW_X_KEY, xDp.roundToInt().toString())
    settings.putString(HUD_WINDOW_Y_KEY, yDp.roundToInt().toString())
}
