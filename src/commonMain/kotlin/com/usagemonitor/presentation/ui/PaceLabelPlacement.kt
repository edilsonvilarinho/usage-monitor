package com.usagemonitor.presentation.ui

/** Retângulo de texto no `Canvas` da diagonal do ritmo, em px. */
internal data class PaceLabelBox(val left: Float, val top: Float, val width: Float, val height: Float) {
    val right: Float get() = left + width
    val bottom: Float get() = top + height

    fun overlapArea(other: PaceLabelBox): Float {
        val overlapWidth = minOf(right, other.right) - maxOf(left, other.left)
        val overlapHeight = minOf(bottom, other.bottom) - maxOf(top, other.top)
        return if (overlapWidth > 0f && overlapHeight > 0f) overlapWidth * overlapHeight else 0f
    }
}

/**
 * Onde vai o rótulo do ponto mais novo ("100% · 87% do tempo") (#477).
 *
 * Era sempre à esquerda do ponto, acima dele; com a cota perto de 100% ele caía na faixa da legenda
 * "acima da linha: esgota antes", fixa no topo esquerdo, e as duas frases se cruzavam. Agora tenta, nesta
 * ordem, acima-esquerda, abaixo-esquerda, acima-direita e abaixo-direita — cada uma presa à área — e fica
 * com a primeira que não encosta em nada de [avoid] nem no próprio ponto. Se todas encostam, a de menor
 * sobreposição: o rótulo é o dado e não some.
 */
internal fun placePacePointLabel(
    pointX: Float,
    pointY: Float,
    labelWidth: Float,
    labelHeight: Float,
    gap: Float,
    pointRadius: Float,
    areaWidth: Float,
    areaHeight: Float,
    avoid: List<PaceLabelBox>
): PaceLabelBox {
    val leftX = pointX - gap - labelWidth
    val rightX = pointX + gap
    val aboveY = pointY - gap - labelHeight
    val belowY = pointY + gap
    val candidates = listOf(leftX to aboveY, leftX to belowY, rightX to aboveY, rightX to belowY).map { (x, y) ->
        PaceLabelBox(
            left = x.coerceIn(0f, (areaWidth - labelWidth).coerceAtLeast(0f)),
            top = y.coerceIn(0f, (areaHeight - labelHeight).coerceAtLeast(0f)),
            width = labelWidth,
            height = labelHeight
        )
    }
    val point = PaceLabelBox(pointX - pointRadius, pointY - pointRadius, pointRadius * 2, pointRadius * 2)
    val obstacles = avoid + point
    fun overlapOf(candidate: PaceLabelBox) = obstacles.sumOf { obstacle -> candidate.overlapArea(obstacle).toDouble() }
    return candidates.firstOrNull { candidate -> overlapOf(candidate) == 0.0 } ?: candidates.minBy(::overlapOf)
}
