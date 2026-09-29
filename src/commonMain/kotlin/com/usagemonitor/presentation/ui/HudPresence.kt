package com.usagemonitor.presentation.ui

import com.usagemonitor.domain.entity.UsageTargetKey

/**
 * O momento de uma conta na faixa da HUD. Enum próprio (não valor novo em enum
 * existente): nascer e sair são transições finitas que a janela precisa ver para
 * manter o espaço do item enquanto ele colapsa.
 */
enum class HudPresence { SHOWN, ENTERING, LEAVING }

/** Intervalo entre o nascimento de uma conta e o da seguinte, quando nascem juntas. */
const val HUD_BIRTH_STAGGER_MILLIS = 140

/**
 * A posição de cada conta na fila de nascimento: 0 para a primeira que está
 * nascendo, 1 para a segunda... `null` para quem não está nascendo.
 */
fun hudBirthOrder(accounts: List<HudAccount>): List<Int?> {
    var next = 0
    return accounts.map { account ->
        if (account.presence == HudPresence.ENTERING) next++ else null
    }
}

/**
 * Casa a lista em tela com a lista viva. Conta nova entra como
 * [HudPresence.ENTERING]; conta que sumiu fica no **mesmo lugar** como
 * [HudPresence.LEAVING] até a janela confirmar o fim do colapso
 * ([settleHudPresence]). A primeira lista também nasce: iniciar o app ou ligar
 * a HUD mostra o surgimento, em cascata ([HUD_BIRTH_STAGGER_MILLIS]). Sem
 * animação, a lista viva passa direto.
 */
fun mergeHudPresence(displayed: List<HudAccount>, current: List<HudAccount>, animate: Boolean): List<HudAccount> {
    if (!animate) return current.map { account -> account.copy(presence = HudPresence.SHOWN) }
    val before = displayed.associateBy { account -> account.targetKey }
    val currentKeys = current.map { account -> account.targetKey }.toSet()
    val merged = current.map { account ->
        val previous = before[account.targetKey]
        val presence = when {
            previous != null && previous.presence != HudPresence.LEAVING -> previous.presence
            else -> HudPresence.ENTERING
        }
        account.copy(presence = presence)
    }.toMutableList()
    displayed.forEachIndexed { index, previous ->
        if (previous.targetKey !in currentKeys) {
            merged.add(index.coerceAtMost(merged.size), previous.copy(presence = HudPresence.LEAVING))
        }
    }
    return merged
}

/** Fim da transição de uma conta: quem nasceu fica, quem saiu some. */
fun settleHudPresence(displayed: List<HudAccount>, key: UsageTargetKey): List<HudAccount> {
    return displayed.mapNotNull { account ->
        when {
            account.targetKey != key -> account
            account.presence == HudPresence.LEAVING -> null
            else -> account.copy(presence = HudPresence.SHOWN)
        }
    }
}
