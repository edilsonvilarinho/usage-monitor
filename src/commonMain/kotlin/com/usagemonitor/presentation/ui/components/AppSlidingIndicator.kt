package com.usagemonitor.presentation.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import com.usagemonitor.presentation.ui.theme.AppMotion
import com.usagemonitor.presentation.ui.theme.appSpring
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * O indicador de seleção que anda de uma opção para a outra.
 *
 * Aba, segmentado e navegação lateral trocavam o realce no mesmo quadro: a
 * opção antiga apagava e a nova acendia, e o olho não tinha como seguir a
 * mudança. Um indicador só, que desliza, diz **de onde para onde** a escolha
 * foi — é o movimento que falta quando a tela lê "sem fluidez".
 *
 * Cada opção informa onde foi posta ([reportIndicatorSpan]) e o indicador é
 * desenhado pelo contêiner, atrás ou abaixo delas. As posições são relativas ao
 * pai direto das opções, e por isso o indicador mora num `Box` que embrulha só
 * aquele pai: a mesma origem para os dois.
 */
@Immutable
internal data class IndicatorSpan(val start: Float, val size: Float)

@Stable
internal class SlidingIndicatorState {
    val spans = mutableStateMapOf<Int, IndicatorSpan>()
}

@Composable
internal fun rememberSlidingIndicatorState(): SlidingIndicatorState {
    return remember { SlidingIndicatorState() }
}

/** Publica a posição e o tamanho da opção [index] no eixo do controle. */
internal fun Modifier.reportIndicatorSpan(
    state: SlidingIndicatorState,
    index: Int,
    vertical: Boolean = false
): Modifier {
    return this.onPlaced { coordinates ->
        val position = coordinates.positionInParent()
        val span = if (vertical) {
            IndicatorSpan(position.y, coordinates.size.height.toFloat())
        } else {
            IndicatorSpan(position.x, coordinates.size.width.toFloat())
        }
        if (state.spans[index] != span) {
            state.spans[index] = span
        }
    }
}

/**
 * O trecho animado do indicador, em pixels, ou `null` antes da primeira medida.
 *
 * **A primeira posição é salto, não animação.** Sem isso o indicador nasceria na
 * origem e deslizaria até a opção inicial toda vez que a tela abrisse — um
 * movimento que não descreve mudança nenhuma. Mola `SNAPPY`, sem rebote: o
 * indicador que passa da opção e volta lê como hesitação.
 */
@Composable
internal fun animatedIndicatorSpan(state: SlidingIndicatorState, selectedIndex: Int): IndicatorSpan? {
    val target = state.spans[selectedIndex]
    val start = remember { Animatable(0f) }
    val size = remember { Animatable(0f) }
    var placed by remember { mutableStateOf(false) }
    val spec = appSpring<Float>(AppMotion.Springs.SNAPPY, visibilityThreshold = INDICATOR_VISIBILITY_THRESHOLD)

    LaunchedEffect(target) {
        if (target == null) {
            return@LaunchedEffect
        }
        if (!placed) {
            start.snapTo(target.start)
            size.snapTo(target.size)
            placed = true
            return@LaunchedEffect
        }
        coroutineScope {
            launch { start.animateTo(target.start, spec) }
            launch { size.animateTo(target.size, spec) }
        }
    }

    if (!placed) {
        return null
    }
    return IndicatorSpan(start.value, size.value)
}

/** Meio pixel: abaixo disso o indicador já está no lugar a olho nu. */
private const val INDICATOR_VISIBILITY_THRESHOLD = 0.5f
