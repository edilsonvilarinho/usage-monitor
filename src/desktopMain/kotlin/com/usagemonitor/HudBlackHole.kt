package com.usagemonitor

import androidx.compose.ui.unit.Dp
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.presentation.ui.HudAccount
import com.usagemonitor.presentation.ui.hudMinimizeFrame

/*
 * Buraco negro das contas minimizadas (issue #600). Função pura: a coluna
 * vertical separa as completas do buraco; o desenho só consome o resultado.
 */

/**
 * Índices em `accounts`. [shown] são as contas que ocupam slot na coluna (tudo fora do buraco),
 * [holeMembers] as que já chegaram ao buraco (contagem), [flying] as que estão em trânsito
 * entre a coluna e o buraco nos dois sentidos e são desenhadas interpoladas.
 */
internal data class HudColumnArrangement(
    val shown: List<Int>,
    val holeMembers: List<Int>,
    val flying: List<Int> = emptyList(),
)

/** Quadro do voo de uma conta até o buraco: posição ao longo da borda e diâmetro do anel. */
internal data class HudSinkFrame(val along: Float, val ringSize: Dp)

/**
 * Quadro do voo de [progress] (0 = na coluna, 1 = no buraco) entre [fromAlong] e [toAlong].
 * A posição anda linear no trajeto; o anel segue a curva de [hudMinimizeFrame]. Sem overshoot.
 */
internal fun hudSinkFrame(progress: Float, fromAlong: Float, toAlong: Float): HudSinkFrame {
    val clamped = progress.coerceIn(0f, 1f)
    return HudSinkFrame(
        along = fromAlong + (toAlong - fromAlong) * clamped,
        ringSize = hudMinimizeFrame(clamped).ringSize,
    )
}

/** Há pelo menos uma conta dentro do buraco. Sem minimizada, o buraco não existe. */
internal val HudColumnArrangement.hasHole: Boolean
    get() = holeMembers.isNotEmpty()

/**
 * As minimizadas entram no buraco só ao chegar ao progresso 1; enquanto [progress] (0..1 por conta)
 * não chega, seguem em voo. Conta sem entrada usa o próprio flag: minimizada começa em 1, completa em 0.
 * A borda (horizontal ou vertical) não muda o arranjo: o buraco é o mesmo nas duas (A04a, #612).
 */
internal fun hudColumnArrangement(
    accounts: List<HudAccount>,
    progress: Map<UsageTargetKey, Float> = emptyMap(),
): HudColumnArrangement {
    val value = accounts.map { account -> progress[account.targetKey] ?: if (account.minimized) 1f else 0f }
    val holeMembers = accounts.indices.filter { index ->
        accounts[index].minimized && value[index] >= 1f
    }
    val shown = accounts.indices.filter { index -> index !in holeMembers }
    val flying = accounts.indices.filter { index ->
        value[index] > 0f && !(accounts[index].minimized && value[index] >= 1f)
    }
    return HudColumnArrangement(shown = shown, holeMembers = holeMembers, flying = flying)
}
