package com.usagemonitor

import com.usagemonitor.presentation.ui.HudAccount

/*
 * Buraco negro das contas minimizadas (issue #600). Função pura: a coluna
 * vertical separa as completas do buraco; o desenho só consome o resultado.
 */

/** Índices em `accounts`: as contas que ficam na coluna e as que vão para o buraco. */
internal data class HudColumnArrangement(val shown: List<Int>, val holeMembers: List<Int>)

/** Há pelo menos uma conta dentro do buraco. Sem minimizada, o buraco não existe. */
internal val HudColumnArrangement.hasHole: Boolean
    get() = holeMembers.isNotEmpty()

/**
 * Na borda vertical, as minimizadas (manuais e automáticas) saem da coluna e
 * formam o buraco, na ordem de publicação da lista. Na borda horizontal nada
 * muda: todas ficam na faixa e o buraco não existe.
 */
internal fun hudColumnArrangement(accounts: List<HudAccount>, edge: HudEdge): HudColumnArrangement {
    if (edge.isHorizontal) {
        return HudColumnArrangement(shown = accounts.indices.toList(), holeMembers = emptyList())
    }
    val shown = accounts.indices.filter { index -> !accounts[index].minimized }
    val holeMembers = accounts.indices.filter { index -> accounts[index].minimized }
    return HudColumnArrangement(shown = shown, holeMembers = holeMembers)
}
