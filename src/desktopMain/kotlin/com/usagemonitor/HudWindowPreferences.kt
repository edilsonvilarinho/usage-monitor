package com.usagemonitor

import com.russhwolf.settings.PreferencesSettings
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
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

private const val HUD_EDGE_KEY = "hudEdge"
private const val HUD_EDGE_OFFSET_KEY = "hudEdgeOffset"

/**
 * Onde o notch mora: a borda e o centro ao longo dela, em **fração** da tela.
 *
 * Fração e não dp: ao trocar de resolução ou de monitor o notch continua no
 * mesmo ponto relativo da borda, em vez de sair da tela por uma coordenada que
 * descrevia um monitor mais largo.
 *
 * **Migra a posição da pílula antiga** na primeira leitura: quem já tinha
 * arrastado a barra para um canto não pode perder a escolha só porque o
 * desenho mudou. O canto gravado vira a borda mais próxima dele, as chaves
 * novas são gravadas e as antigas apagadas — a migração acontece uma vez.
 * Meia posição (borda sem fração, ou o contrário) é ignorada, pela mesma razão
 * de [readPersistedHudPosition].
 */
internal fun readPersistedHudPlacement(settings: PreferencesSettings, area: ScreenWorkArea): HudPlacement {
    val edge = settings.getStringOrNull(HUD_EDGE_KEY)?.let { name ->
        HudEdge.entries.firstOrNull { entry -> entry.name == name }
    }
    val fraction = settings.getStringOrNull(HUD_EDGE_OFFSET_KEY)?.toFloatOrNull()
    if (edge != null && fraction != null && fraction.isFinite()) {
        return HudPlacement(edge, fraction.coerceIn(0f, 1f))
    }

    val legacy = readPersistedHudPosition(settings) ?: return HudPlacement.Default
    // O canto gravado era o superior esquerdo da pílula; o centro dela ficava
    // meia pílula adiante. Um palpite de 100dp basta: só decide a borda e o
    // ponto ao longo dela, e o usuário arrasta se não gostar.
    val migrated = nearestHudPlacement(
        centerX = (legacy.xDp + LEGACY_PILL_HALF_WIDTH_DP).dp,
        centerY = (legacy.yDp + LEGACY_PILL_HALF_HEIGHT_DP).dp,
        area = area
    )
    persistHudPlacement(settings, migrated)
    settings.remove(HUD_WINDOW_X_KEY)
    settings.remove(HUD_WINDOW_Y_KEY)
    return migrated
}

internal fun persistHudPlacement(settings: PreferencesSettings, placement: HudPlacement) {
    if (!placement.offsetFraction.isFinite()) {
        return
    }
    settings.putString(HUD_EDGE_KEY, placement.edge.name)
    settings.putString(HUD_EDGE_OFFSET_KEY, placement.offsetFraction.coerceIn(0f, 1f).toString())
}

private const val HUD_SCREEN_ID_KEY = "hudScreenId"
private const val HUD_SCREEN_BOUNDS_KEY = "hudScreenBounds"

/**
 * Em que monitor o notch mora (issue #273). A borda e a fração de
 * [readPersistedHudPlacement] sozinhas eram resolvidas sempre contra o monitor
 * padrão, e o notch solto num secundário voltava ao primário. Id **e** limites:
 * o Windows renumera os monitores ao reconectar, e é pelos limites que
 * [resolveScreen] reencontra o mesmo monitor com outro id. Ausente — instalação
 * anterior a esta chave, ou notch nunca solto num secundário — vale o primário.
 */
internal data class PersistedHudScreen(val id: String?, val bounds: ScreenWorkArea?)

internal fun readPersistedHudScreen(settings: PreferencesSettings): PersistedHudScreen {
    val id = settings.getStringOrNull(HUD_SCREEN_ID_KEY)?.takeIf { value -> value.isNotBlank() }
    val bounds = settings.getStringOrNull(HUD_SCREEN_BOUNDS_KEY)
        ?.split(',')
        ?.mapNotNull { part -> part.trim().toIntOrNull() }
        ?.takeIf { parts -> parts.size == 4 && parts[2] > 0 && parts[3] > 0 }
        ?.let { parts -> ScreenWorkArea(parts[0].dp, parts[1].dp, DpSize(parts[2].dp, parts[3].dp)) }
    return PersistedHudScreen(id, bounds)
}

internal fun persistHudScreen(settings: PreferencesSettings, screen: ScreenInfo) {
    val bounds = screen.bounds
    val values = listOf(bounds.x.value, bounds.y.value, bounds.size.width.value, bounds.size.height.value)
    if (values.any { value -> !value.isFinite() }) {
        return
    }
    settings.putString(HUD_SCREEN_ID_KEY, screen.id)
    settings.putString(HUD_SCREEN_BOUNDS_KEY, values.joinToString(",") { value -> value.roundToInt().toString() })
}

private const val LEGACY_PILL_HALF_WIDTH_DP = 100
private const val LEGACY_PILL_HALF_HEIGHT_DP = 14
