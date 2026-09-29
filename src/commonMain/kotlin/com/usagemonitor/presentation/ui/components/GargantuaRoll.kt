package com.usagemonitor.presentation.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import com.usagemonitor.presentation.ui.theme.AppGargantuaTokens
import com.usagemonitor.presentation.ui.theme.LocalAppMotionPolicy

/**
 * Um caractere que muda na troca do texto (D5 · horizonte de eventos): o índice
 * dele no texto novo, o do antigo que ele substitui (`null` quando o texto
 * cresceu e não havia nada ali) e a posição na cascata, da esquerda para a direita.
 */
data class GargantuaRollGlyph(val newIndex: Int, val oldIndex: Int?, val order: Int)

/**
 * Quais caracteres rolam de [old] para [new]. O começo comum fica parado (o
 * rótulo "7d " nunca rola), e o resto é comparado **pela direita**, como num
 * odômetro: "9%" → "12%" rola só o 9 → 2 e faz nascer o 1.
 */
fun gargantuaRollGlyphs(old: String, new: String): List<GargantuaRollGlyph> {
    if (old == new) return emptyList()
    var prefix = 0
    while (prefix < old.length && prefix < new.length && old[prefix] == new[prefix]) prefix++
    val changed = mutableListOf<Pair<Int, Int?>>()
    for (fromEnd in 1..(new.length - prefix)) {
        val newIndex = new.length - fromEnd
        val oldIndex = (old.length - fromEnd).takeIf { index -> index >= prefix }
        if (oldIndex != null && old[oldIndex] == new[newIndex]) continue
        changed += newIndex to oldIndex
    }
    return changed.sortedBy { (newIndex, _) -> newIndex }
        .mapIndexed { order, (newIndex, oldIndex) -> GargantuaRollGlyph(newIndex, oldIndex, order) }
}

/** A troca inteira dura um rolar mais a cascata dos seguintes. */
fun gargantuaRollTotalMillis(glyphs: Int): Int {
    if (glyphs <= 0) return 0
    return AppGargantuaTokens.rollMillis + AppGargantuaTokens.rollStaggerMillis * (glyphs - 1)
}

/**
 * Quanto um caractere já rolou, em `0..1`, [elapsedMillis] depois de a troca
 * começar. Curva padrão, sem rebote: número não passa do lugar.
 */
fun gargantuaRollProgress(elapsedMillis: Float, order: Int): Float {
    val start = order * AppGargantuaTokens.rollStaggerMillis
    val linear = ((elapsedMillis - start) / AppGargantuaTokens.rollMillis).coerceIn(0f, 1f)
    return FastOutSlowInEasing.transform(linear)
}

/**
 * O estado de uma troca em andamento: o texto antigo, o que rola e o relógio
 * dela. O layout do texto novo chega por [onTextLayout], do próprio `Text`.
 */
@Stable
class GargantuaRollState internal constructor(
    internal val old: AnnotatedString?,
    internal val glyphs: List<GargantuaRollGlyph>,
    internal val whole: Boolean,
    internal val elapsed: Animatable<Float, AnimationVector1D>,
    internal val totalMillis: Int
) {
    internal var layout: TextLayoutResult? = null

    val onTextLayout: (TextLayoutResult) -> Unit = { result -> layout = result }

    internal val running: Boolean get() = old != null && elapsed.value < totalMillis
}

/**
 * D5 · horizonte de eventos: quando [text] muda, só os caracteres diferentes
 * rolam — o antigo sobe e some pela borda de cima, o novo nasce de baixo, e a
 * base de cada um acende uma borda fina de luz quente, como matéria cruzando o
 * horizonte. Com [whole] a palavra inteira rola de uma vez (a pílula de estado).
 *
 * O `Text` continua medindo e desenhando o texto novo; o efeito é só desenho por
 * cima dele ([gargantuaRoll]), então a geometria da HUD, que mede os textos
 * antes da composição, não muda. Não anima na primeira composição nem com
 * "Reduzir animações".
 */
@Composable
fun rememberGargantuaRoll(text: AnnotatedString, whole: Boolean = false): GargantuaRollState {
    val policy = LocalAppMotionPolicy.current
    val previous = remember { RollPrevious(text) }
    // Nasce já em zero na composição da troca: o texto novo não aparece
    // inteiro por um quadro antes de rolar.
    val state = remember(text) {
        val old = previous.text.takeIf { shown -> shown != text && !policy.reduced }
        val glyphs = when {
            old == null -> emptyList()
            whole -> listOf(GargantuaRollGlyph(0, 0, 0))
            else -> gargantuaRollGlyphs(old.text, text.text)
        }
        val total = gargantuaRollTotalMillis(glyphs.size)
        GargantuaRollState(
            old = old.takeIf { glyphs.isNotEmpty() },
            glyphs = glyphs,
            whole = whole,
            elapsed = Animatable(0f),
            totalMillis = total
        )
    }
    SideEffect { previous.text = text }
    LaunchedEffect(state) {
        if (state.old != null) {
            state.elapsed.animateTo(state.totalMillis.toFloat(), tween(state.totalMillis, easing = LinearEasing))
        }
    }
    return state
}

/** O último texto mostrado; memória entre composições, não estado. */
private class RollPrevious(var text: AnnotatedString)

/**
 * O desenho do rolar sobre o `Text` que recebeu o [state]. [style] é o do
 * `Text`, com a cor dele, para desenhar o texto antigo igual.
 */
@Composable
fun Modifier.gargantuaRoll(state: GargantuaRollState, style: TextStyle): Modifier {
    val measurer = rememberTextMeasurer()
    return drawWithContent {
        val layout = state.layout
        val old = state.old
        if (!state.running || layout == null || old == null) {
            drawContent()
            return@drawWithContent
        }
        val elapsed = state.elapsed.value
        val height = size.height
        val slots = state.glyphs.map { glyph ->
            if (state.whole) {
                Rect(0f, 0f, size.width, height)
            } else {
                val box = layout.getBoundingBox(glyph.newIndex)
                Rect(box.left, 0f, box.right, height)
            }
        }
        // O que não muda fica parado: o texto novo com os buracos dos que rolam.
        val holes = Path()
        slots.forEach { slot -> holes.addRect(slot) }
        clipPath(holes, ClipOp.Difference) { this@drawWithContent.drawContent() }
        val oldLayout = measurer.measure(
            old,
            style,
            maxLines = layout.layoutInput.maxLines,
            constraints = if (state.whole) Constraints(maxWidth = size.width.toInt().coerceAtLeast(0)) else Constraints()
        )
        state.glyphs.forEachIndexed { index, glyph ->
            val slot = slots[index]
            val progress = gargantuaRollProgress(elapsed, glyph.order)
            clipRect(slot.left, slot.top, slot.right, slot.bottom) {
                translate(top = (1f - progress) * height) { this@drawWithContent.drawContent() }
                val oldIndex = glyph.oldIndex
                if (oldIndex != null && oldIndex < old.length) {
                    val shift = if (state.whole) 0f else slot.left - oldLayout.getBoundingBox(oldIndex).left
                    translate(left = shift, top = -progress * height) { drawText(oldLayout) }
                }
            }
            val rim = pulse(progress)
            if (rim > 0f) {
                val y = height - RIM_WIDTH_PX / 2f
                drawLine(AppGargantuaTokens.gold.copy(alpha = rim * 0.6f), Offset(slot.left, y), Offset(slot.right, y), strokeWidth = RIM_WIDTH_PX * 3f)
                drawLine(AppGargantuaTokens.hot.copy(alpha = rim), Offset(slot.left, y), Offset(slot.right, y), strokeWidth = RIM_WIDTH_PX)
            }
        }
    }
}

/** A borda do horizonte: 1px, com um halo dourado de 3px por baixo. */
private const val RIM_WIDTH_PX = 1f
