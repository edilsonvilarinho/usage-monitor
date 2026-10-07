package com.usagemonitor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.russhwolf.settings.PreferencesSettings

/**
 * Modo "recolher quando parada" (issue #400): o notch parado vira a faixa de
 * [HUD_RETRACTED_STRIP] e o ponteiro em cima o expande pela íris do eclipse (Z2).
 * Desligado — o padrão —, a HUD é a de sempre.
 *
 * Regras puras, para o host da janela só ligá-las ao laço de hover.
 */

/**
 * Espera antes de expandir a faixa: o ponteiro passa pela borda da tela o tempo
 * todo (abas, botão de fechar, a barra de tarefas), e sem ela a HUD abriria a
 * cada travessia. É só do modo recolher: a HUD de sempre já está à vista.
 */
internal const val HUD_RETRACT_INTENT_MILLIS = 200L

/** Quanto esperar com o ponteiro em cima antes de abrir. Já aberta, nada. */
internal fun hudOpenDelayMillis(autoRetract: Boolean, alreadyOpen: Boolean): Long =
    if (autoRetract && !alreadyOpen) HUD_RETRACT_INTENT_MILLIS else 0L

/**
 * Se o notch inteiro está à vista. Recolhe só com o modo ligado, parado e fora
 * do arrasto: carregando, a mão precisa do notch para o usuário ver o que move.
 */
internal fun hudNotchRevealed(autoRetract: Boolean, expanded: Boolean, dragging: Boolean): Boolean =
    !autoRetract || expanded || dragging

/**
 * O modo do host, alternado pelo alfinete ao lado da mão: estado de composição
 * lido de [readHudAutoRetract] uma vez e gravado a cada troca.
 */
internal class HudAutoRetractState(private val settings: PreferencesSettings) {
    var enabled by mutableStateOf(readHudAutoRetract(settings))
        private set

    fun toggle() {
        enabled = !enabled
        persistHudAutoRetract(settings, enabled)
    }
}
