package com.usagemonitor.presentation.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.usagemonitor.presentation.CliSessionSort

/**
 * Índice para o qual a lista deve voltar depois de uma mudança de ordenação, ou `null` quando não
 * deve rolar. A primeira composição não rola: a lista nasce no topo. Rola só quando a ordenação
 * já tinha sido composta e mudou.
 */
internal fun sortScrollTarget(
    hasComposed: Boolean,
    previous: CliSessionSort?,
    current: CliSessionSort?
): Int? = if (!hasComposed || previous == current) null else 0

/**
 * Leva a lista ao primeiro item da nova ordem quando a ordenação muda (#577). Sem isso, a posição
 * de rolagem sobrevive à reordenação e a primeira linha nova fica fora da janela. O salto é
 * instantâneo, sem animação.
 */
@Composable
internal fun ScrollToTopOnSortChange(listState: LazyListState, sort: CliSessionSort?) {
    var composed by remember { mutableStateOf(false) }
    var previous by remember { mutableStateOf(sort) }
    LaunchedEffect(sort) {
        val target = sortScrollTarget(hasComposed = composed, previous = previous, current = sort)
        if (target != null) listState.scrollToItem(target)
        composed = true
        previous = sort
    }
}
