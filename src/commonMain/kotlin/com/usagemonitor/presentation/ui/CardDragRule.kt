package com.usagemonitor.presentation.ui

/**
 * Intervalo de um item ao longo do eixo do arrasto (x na barra horizontal, y na vertical). Sem Compose: a HUD
 * converte as caixas medidas para este tipo antes de chamar a regra.
 */
internal data class CardAxisSpan(val start: Float, val end: Float) {
    val middle: Float
        get() = (start + end) / 2f
}

/**
 * Índice de destino do card arrastado, no mesmo sentido de [moveVisibleCardToIndex] (índice final, depois de
 * retirar o card da lista).
 *
 * Regra: o card troca com um vizinho quando o ponteiro passa do ponto médio dele. Por isso o destino é a contagem
 * dos outros itens cujo ponto médio está antes do [pointer]. Ponteiro fora da faixa é preso às bordas; lista vazia
 * dá 0.
 *
 * [spans] devem estar na ordem atual da barra, e [draggedIndex] é a posição do card arrastado nessa ordem.
 */
internal fun dragTargetIndex(
    spans: List<CardAxisSpan>,
    draggedIndex: Int,
    pointer: Float
): Int {
    if (spans.isEmpty()) {
        return 0
    }

    val passed = spans.withIndex().count { (index, span) ->
        index != draggedIndex && span.middle < pointer
    }

    return passed.coerceIn(0, spans.lastIndex)
}

/**
 * Deslocamento de cada item enquanto o card [draggedIndex] está sendo levado para [targetIndex]. O resultado tem um
 * valor por posição original, na mesma ordem de [spans].
 *
 * As vagas são fixas: cada item vai para a vaga do destino dele na ordem prévia e a diferença entre o início dessa
 * vaga e o início da própria vaga é o deslocamento. Assim a vaga aberta no destino fica livre para as vizinhas e
 * itens de larguras diferentes mantêm o encaixe. A entrada do card arrastado é o deslocamento até a vaga de destino;
 * quem segue o ponteiro durante o gesto ignora essa entrada.
 *
 * Índice fora da faixa não move nada: devolve zeros.
 */
internal fun dragShiftOffsets(
    spans: List<CardAxisSpan>,
    draggedIndex: Int,
    targetIndex: Int
): List<Float> {
    if (draggedIndex !in spans.indices || targetIndex !in spans.indices) {
        return spans.map { 0f }
    }

    val previewOrder = spans.indices.toMutableList()
    val moved = previewOrder.removeAt(draggedIndex)
    previewOrder.add(targetIndex, moved)

    val offsets = MutableList(spans.size) { 0f }
    previewOrder.forEachIndexed { slot, original ->
        offsets[original] = spans[slot].start - spans[original].start
    }

    return offsets
}
