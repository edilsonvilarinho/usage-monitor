package com.usagemonitor.presentation.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Estado do balão do notch (#612): a conta que ele mostra ([balloonIndex]) e, quando ela é o buraco negro, a conta
 * da lista do buraco que está aberta ([holeSelection]; `null` mostra a lista). Os gestos do anel e do buraco mudam
 * os dois juntos, por isso moram aqui: a lista não fica com uma conta aberta que o balão já deixou.
 */
internal class HudNotchBalloonState(initialBalloonIndex: Int?) {
    var balloonIndex: Int? by mutableStateOf(initialBalloonIndex)
    var holeSelection: Int? by mutableStateOf(null)

    /** Entrada do ponteiro num anel: abre o balão dele e fecha a lista do buraco. */
    fun ringHovered(index: Int) {
        balloonIndex = index
        holeSelection = null
    }

    /** Clique no buraco alterna a lista: aberta, fecha; fechada, ou com a conta da lista aberta, mostra a lista. */
    fun holeClicked(holeIndex: Int) {
        if (balloonIndex == holeIndex && holeSelection == null) {
            balloonIndex = null
        } else {
            balloonIndex = holeIndex
            holeSelection = null
        }
    }

    /** Entrada do ponteiro no buraco abre a lista (#612), salvo se ela já é a do buraco: não zera a conta aberta nela. */
    fun holeHovered(holeIndex: Int) {
        if (balloonIndex != holeIndex) {
            balloonIndex = holeIndex
            holeSelection = null
        }
    }

    /** Fecha o balão e a lista do buraco de uma vez (Esc, ou clique fora da lista). */
    fun closeHole() {
        balloonIndex = null
        holeSelection = null
    }
}

/** O estado do balão, lembrado pela composição; [initialBalloonIndex] vale só na primeira composição. */
@Composable
internal fun rememberHudNotchBalloonState(initialBalloonIndex: Int?): HudNotchBalloonState =
    remember { HudNotchBalloonState(initialBalloonIndex) }
