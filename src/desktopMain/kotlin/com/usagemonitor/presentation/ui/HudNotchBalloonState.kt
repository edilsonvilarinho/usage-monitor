package com.usagemonitor.presentation.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.usagemonitor.domain.entity.UsageTargetKey

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

    /** Fecha o balão e a lista do buraco de uma vez (Esc, clique fora da lista, ou a conta mostrada mudou de minimizada). */
    fun closeHole() {
        balloonIndex = null
        holeSelection = null
    }
}

/** O estado do balão, lembrado pela composição; [initialBalloonIndex] vale só na primeira composição. */
@Composable
internal fun rememberHudNotchBalloonState(initialBalloonIndex: Int?): HudNotchBalloonState =
    remember { HudNotchBalloonState(initialBalloonIndex) }

/**
 * Regras do balão que dependem das contas: a lista do buraco perde a conta que saiu dela, e o buraco vazio fecha o
 * balão. Também fecha o balão quando muda o `minimized` da conta que ele mostra (balão de um anel, ou o detalhe
 * aberto a partir da lista do buraco, [HudNotchBalloonState.holeSelection]). Mudança de outra conta não fecha.
 */
@Composable
internal fun HudNotchBalloonState.FollowAccounts(accounts: List<HudAccount>, holeMembers: List<Int>, holeIndex: Int) {
    // A conta aberta deixa o buraco (Restaurar ou outro caminho): a lista volta sem ela, e o buraco vazio fecha o balão.
    LaunchedEffect(holeMembers) {
        if (holeSelection != null && holeSelection !in holeMembers) holeSelection = null
        if (holeMembers.isEmpty() && balloonIndex == holeIndex) balloonIndex = null
    }
    // Conta mostrada: a do anel, ou a do detalhe do buraco. Fora dela (lista do buraco, balão fechado) não há conta.
    val shown = shownAccountOf(accounts, holeIndex)
    val watch = remember { HudMinimizedWatch() }
    // Mesma conta com `minimized` trocado = o Minimizar (ou Restaurar) dela mudou: o balão fecha por inteiro.
    LaunchedEffect(shown?.targetKey, shown?.minimized) {
        val sameAccount = shown != null && watch.key == shown.targetKey
        if (sameAccount && watch.minimized != shown.minimized) closeHole()
        watch.key = shown?.targetKey
        watch.minimized = shown?.minimized
    }
}

/** A conta que o balão mostra agora, ou `null` se ele não mostra conta (fechado, ou lista do buraco). */
private fun HudNotchBalloonState.shownAccountOf(accounts: List<HudAccount>, holeIndex: Int): HudAccount? {
    val index = balloonIndex ?: return null
    if (index in accounts.indices) return accounts[index]
    if (index == holeIndex) return holeSelection?.let { accounts.getOrNull(it) }
    return null
}

/** Última chave e último `minimized` vistos do balão; lembrados pela composição, não publicados. */
private class HudMinimizedWatch {
    var key: UsageTargetKey? = null
    var minimized: Boolean? = null
}
