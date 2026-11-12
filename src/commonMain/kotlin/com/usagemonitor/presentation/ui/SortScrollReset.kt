package com.usagemonitor.presentation.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue

/**
 * Índice para o qual a lista deve voltar depois de uma mudança de ordenação, ou `null` quando não
 * deve rolar. A primeira composição não rola: a lista nasce no topo. Rola só quando a ordenação
 * já tinha sido composta e mudou.
 */
internal fun sortScrollTarget(
    hasComposed: Boolean,
    previous: Any?,
    current: Any?
): Int? = if (!hasComposed || previous == current) null else 0

/**
 * Leva a lista ao primeiro item da nova ordem quando a ordenação muda (#577). Sem isso, a posição
 * de rolagem sobrevive à reordenação e a primeira linha nova fica fora da janela. O salto é
 * instantâneo, sem animação.
 */
@Composable
internal fun ScrollToTopOnSortChange(listState: LazyListState, sort: Any?) {
    var composed by remember { mutableStateOf(false) }
    var previous by remember { mutableStateOf(sort) }
    LaunchedEffect(sort) {
        val target = sortScrollTarget(hasComposed = composed, previous = previous, current = sort)
        if (target != null) listState.scrollToItem(target)
        composed = true
        previous = sort
    }
}

/**
 * Índice para o qual a lista deve rolar depois de uma mudança de ordenação, ou `null` quando não deve
 * rolar. Mesma regra de [sortScrollTarget], com o alvo vindo do cabeçalho clicado (#577, time).
 */
internal fun sortScrollIndexTarget(
    hasComposed: Boolean,
    previous: Any?,
    current: Any?,
    clickedIndex: Int?
): Int? = if (!hasComposed || previous == current) null else clickedIndex

/** Índice do item visível com a chave dada, ou `null` quando ele não está na janela. */
internal fun LazyListState.visibleIndexOf(key: Any): Int? =
    layoutInfo.visibleItemsInfo.firstOrNull { item -> item.key == key }?.index

/**
 * Rola até o item clicado (`targetIndex`) quando a ordenação muda, sem animação (#577, time). A
 * primeira composição não rola.
 */
@Composable
internal fun ScrollToIndexOnSortChange(listState: LazyListState, sort: Any?, targetIndex: Int?) {
    var composed by remember { mutableStateOf(false) }
    var previous by remember { mutableStateOf(sort) }
    val clickedIndex by rememberUpdatedState(targetIndex)
    LaunchedEffect(sort) {
        val target = sortScrollIndexTarget(
            hasComposed = composed,
            previous = previous,
            current = sort,
            clickedIndex = clickedIndex
        )
        if (target != null) listState.scrollToItem(target)
        composed = true
        previous = sort
    }
}
