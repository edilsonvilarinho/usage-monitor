package com.usagemonitor

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.usagemonitor.presentation.ui.HudAccount
import com.usagemonitor.presentation.ui.components.HUD_BLACK_HOLE_GLYPH_SIZE
import com.usagemonitor.presentation.ui.components.HUD_BLACK_HOLE_ORBIT_RADIUS

/** Cabeçalho da lista do buraco e altura de cada linha (#600, A05). */
internal val HUD_BLACK_HOLE_LIST_HEADER = 24.dp
internal val HUD_BLACK_HOLE_LIST_ROW = 28.dp

/** Faixa "← N no buraco" no topo do balão de uma conta vinda do buraco (#600, A05). */
internal val HUD_BLACK_HOLE_BACK_STRIP = 24.dp

/** Nota "volta sozinha quando couber" sob a conta auto-minimizada do buraco (#600, A05). Altura fixa. */
internal val HUD_BLACK_HOLE_AUTO_NOTE = 24.dp

/**
 * Célula do buraco (#602, A05): a órbita mais meio glifo para cada lado, 2 × (45 + 12) = 114 dp.
 * Constante, nunca por quadro.
 */
internal val HUD_BLACK_HOLE_CELL_SIZE: Dp = (HUD_BLACK_HOLE_ORBIT_RADIUS + HUD_BLACK_HOLE_GLYPH_SIZE / 2f) * 2f

/** Altura da lista do buraco com [count] contas: cabeçalho mais uma linha por conta (#600, A05). */
internal fun hudBlackHoleListHeight(count: Int): Dp = HUD_BLACK_HOLE_LIST_HEADER + HUD_BLACK_HOLE_LIST_ROW * count

/**
 * Altura do balão de uma conta aberta a partir do buraco (#600, A05): o conteúdo, a faixa de volta e, na
 * auto-minimizada, a nota de altura fixa. Sem a nota, ela ficava fora da altura medida.
 */
internal fun hudBlackHoleAccountBalloonHeight(account: HudAccount, maxBodyHeight: Dp = Dp.Infinity): Dp =
    hudBalloonHeight(account, maxBodyHeight) + HUD_BLACK_HOLE_BACK_STRIP +
        if (account.autoMinimized) HUD_BLACK_HOLE_AUTO_NOTE else 0.dp
