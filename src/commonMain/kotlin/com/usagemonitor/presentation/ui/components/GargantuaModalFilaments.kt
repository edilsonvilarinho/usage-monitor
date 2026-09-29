package com.usagemonitor.presentation.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import com.usagemonitor.presentation.ui.theme.AppGargantuaTokens

/**
 * Um quadro de uma linha no E9 (filamentos de plasma), a abertura e o
 * fechamento de todo modal. [reveal] é a fração da largura já revelada, da
 * esquerda para a direita; o filamento corre sob a linha até a mesma ponta e só
 * desenha com [filamentAlpha] acima de zero.
 *
 * O dado nunca se move: a linha está no lugar final desde o primeiro quadro e é
 * só recortada. Nada cresce, nada passa do valor.
 */
data class ModalFilamentRowFrame(
    val reveal: Float = 1f,
    val filamentAlpha: Float = 0f
) {
    val settled: Boolean get() = this == Settled

    companion object {
        val Settled = ModalFilamentRowFrame()
    }
}

/** Em que ponto do E9 o modal está. Parado, toda linha aparece inteira. */
enum class ModalRevealPhase { SETTLED, OPENING, CLOSING }

/**
 * Abrir: as linhas entram em ordem de leitura, cada uma começando `0,5 / count`
 * depois da anterior (a partir de 8%). A cabeça do filamento revela a linha em
 * 35% do tempo e o filamento se apaga logo depois dela (30–42% depois do
 * início da linha). [progress] em `0..1`; em `1`, parado.
 */
fun modalFilamentOpenRowFrame(progress: Float, index: Int, count: Int): ModalFilamentRowFrame {
    val t = progress.coerceIn(0f, 1f)
    if (t >= 1f) return ModalFilamentRowFrame.Settled
    val start = OPEN_FIRST_ROW + index.coerceAtLeast(0) * openStep(count)
    val reveal = easeOut(span(t, start, start + OPEN_ROW_SPAN))
    val fade = 1f - span(t, start + OPEN_FADE_FROM, start + OPEN_FADE_TO)
    return ModalFilamentRowFrame(
        reveal = reveal,
        filamentAlpha = if (reveal > 0f) fade else 0f
    )
}

/**
 * Fechar: ordem inversa — a última linha recolhe primeiro, da direita para a
 * esquerda. O filamento só existe enquanto a linha está a meio caminho.
 */
fun modalFilamentCloseRowFrame(progress: Float, index: Int, count: Int): ModalFilamentRowFrame {
    val t = progress.coerceIn(0f, 1f)
    val fromEnd = (count - 1 - index).coerceAtLeast(0)
    val start = fromEnd * closeStep(count)
    val reveal = 1f - easeIn(span(t, start, start + CLOSE_ROW_SPAN))
    return ModalFilamentRowFrame(
        reveal = reveal,
        filamentAlpha = if (reveal > 0f && reveal < 1f) 1f else 0f
    )
}

/**
 * Opacidade da janela (ou do cartão do [AppDialog]) no E9: abre em 15% do tempo
 * (~100 ms) e só some nos últimos 15% do fechamento — a moldura fica enquanto os
 * filamentos recolhem o conteúdo.
 */
fun modalFilamentWindowAlpha(phase: ModalRevealPhase, progress: Float): Float {
    val t = progress.coerceIn(0f, 1f)
    return when (phase) {
        ModalRevealPhase.SETTLED -> 1f
        ModalRevealPhase.OPENING -> easeOut(span(t, 0f, WINDOW_OPEN_SPAN))
        ModalRevealPhase.CLOSING -> 1f - span(t, WINDOW_CLOSE_FROM, 1f)
    }
}

private fun openStep(count: Int): Float = OPEN_CASCADE / count.coerceAtLeast(1)

private fun closeStep(count: Int): Float = CLOSE_CASCADE / count.coerceAtLeast(1)

/**
 * O relógio do E9 de uma superfície — a janela modal ou o cartão do diálogo — e
 * o registro das linhas que ele revela.
 *
 * **A ordem sai da posição**, não da composição: as linhas publicam a própria
 * caixa ([place]) e a ordem de leitura é topo, depois esquerda. Composição não
 * serve — a coluna da navegação lateral é composta antes do conteúdo e iria
 * inteira na frente. Empate exato cai na ordem de chegada, e a lista é total e
 * determinística como toda lista do app.
 *
 * [progress] é lido só no desenho: o quadro a quadro invalida a pintura das
 * linhas, nunca a composição.
 *
 * **Estados em cadeia.** A raiz é a superfície (a janela, o cartão do diálogo);
 * cada [AppModalRevealScope] é um filho com relógio próprio, que refaz o E9 só
 * nas linhas dele quando o conteúdo troca. A linha se registra na cadeia inteira
 * — a abertura da janela ainda a inclui na cascata de todas — e o quadro dela
 * sai da raiz enquanto a raiz se move (abrir e fechar são da janela), senão do
 * escopo mais interno que estiver tocando.
 */
@Stable
class ModalRevealState(
    initialPhase: ModalRevealPhase = ModalRevealPhase.SETTLED,
    val parent: ModalRevealState? = null
) {
    var phase by mutableStateOf(initialPhase)
    var progress by mutableFloatStateOf(0f)

    /**
     * Só na raiz: se a superfície anima. O host da janela liga no Windows sem
     * "Reduzir animações" — o mesmo critério da abertura —, e escopo nenhum
     * repete o E9 numa superfície que abriu seca.
     */
    var replayEnabled by mutableStateOf(false)

    val root: ModalRevealState get() = parent?.root ?: this

    /** Um escopo só repete o E9 com a janela parada na tela. */
    val canReplay: Boolean
        get() = root.replayEnabled && root.phase == ModalRevealPhase.SETTLED

    private val rows = LinkedHashMap<Any, Rect>()
    private var ordered: List<Any>? = null

    fun begin(next: ModalRevealPhase) {
        phase = next
        progress = 0f
    }

    fun settle() {
        phase = ModalRevealPhase.SETTLED
        progress = 1f
    }

    val rowCount: Int get() = rows.size

    fun place(key: Any, bounds: Rect) {
        if (rows[key] != bounds) {
            rows[key] = bounds
            ordered = null
        }
    }

    fun remove(key: Any) {
        if (rows.remove(key) != null) {
            ordered = null
        }
    }

    /** Posição da linha na ordem de leitura; linha ainda sem caixa vai para o fim. */
    fun indexOf(key: Any): Int {
        val order = ordered ?: rows.entries
            .sortedWith(compareBy<Map.Entry<Any, Rect>>({ it.value.top }, { it.value.left }))
            .map { it.key }
            .also { ordered = it }
        val index = order.indexOf(key)
        return if (index < 0) order.size else index
    }

    fun placeInChain(key: Any, bounds: Rect) {
        var state: ModalRevealState? = this
        while (state != null) {
            state.place(key, bounds)
            state = state.parent
        }
    }

    fun removeFromChain(key: Any) {
        var state: ModalRevealState? = this
        while (state != null) {
            state.remove(key)
            state = state.parent
        }
    }

    /** O quadro da linha: a raiz em movimento manda; parada, o escopo mais interno que toca. */
    fun frameInChain(key: Any): ModalFilamentRowFrame {
        val top = root
        if (top.phase != ModalRevealPhase.SETTLED) return top.frameOf(key)
        var state: ModalRevealState? = this
        while (state != null && state !== top) {
            if (state.phase != ModalRevealPhase.SETTLED) return state.frameOf(key)
            state = state.parent
        }
        return ModalFilamentRowFrame.Settled
    }

    fun frameOf(key: Any): ModalFilamentRowFrame {
        val count = rows.size.coerceAtLeast(1)
        return when (phase) {
            ModalRevealPhase.SETTLED -> ModalFilamentRowFrame.Settled
            ModalRevealPhase.OPENING -> modalFilamentOpenRowFrame(progress, indexOf(key), count)
            ModalRevealPhase.CLOSING -> modalFilamentCloseRowFrame(progress, indexOf(key), count)
        }
    }
}

/**
 * O E9 da superfície em volta. `null` fora de modal — a HUD, o balão, os cards —
 * e aí [appModalRevealRow] não faz nada.
 */
val LocalModalReveal = staticCompositionLocalOf<ModalRevealState?> { null }

/**
 * Marca uma linha de modal para o E9: ela se registra no [LocalModalReveal],
 * é revelada da esquerda para a direita atrás da cabeça do filamento e recolhe
 * ao fechar. As primitivas de linha ([AppDataRow], [AppSectionHeader],
 * [AppToolbar], [AppMetricBlock], [AppBanner]…) já aplicam; tela só aplica em
 * bloco que não passa por elas (um gráfico, a demonstração da Ajuda).
 *
 * Não aninhe: linha marcada dentro de linha marcada é recortada duas vezes.
 */
@Composable
fun Modifier.appModalRevealRow(): Modifier {
    val reveal = LocalModalReveal.current ?: return this
    val key = remember { Any() }
    DisposableEffect(reveal, key) {
        onDispose { reveal.removeFromChain(key) }
    }
    return this
        .onGloballyPositioned { coordinates -> reveal.placeInChain(key, coordinates.boundsInRoot()) }
        .drawWithContent {
            val frame = reveal.frameInChain(key)
            when {
                frame.settled -> drawContent()
                frame.reveal <= 0f -> Unit
                else -> {
                    clipRect(right = size.width * frame.reveal) {
                        this@drawWithContent.drawContent()
                    }
                    if (frame.filamentAlpha > 0f) {
                        drawModalFilament(frame.reveal, frame.filamentAlpha)
                    }
                }
            }
        }
}

/**
 * O filamento: brasa transparente na cauda, ouro no corpo e luz quente na
 * cabeça, com um halo largo e fraco por baixo e um ponto de luz na ponta.
 * Mora dentro da caixa da linha, colado na borda de baixo — fora dela o recorte
 * do pai o cortaria.
 */
private fun DrawScope.drawModalFilament(reveal: Float, alpha: Float) {
    val head = size.width * reveal
    if (head <= 0f) return
    val core = FILAMENT_WIDTH.toPx()
    val y = size.height - core / 2f
    val brush = Brush.horizontalGradient(
        0f to AppGargantuaTokens.ember.copy(alpha = 0f),
        0.7f to AppGargantuaTokens.gold.copy(alpha = 0.6f * alpha),
        1f to AppGargantuaTokens.hot.copy(alpha = alpha),
        startX = 0f,
        endX = head
    )
    drawLine(
        color = AppGargantuaTokens.gold.copy(alpha = 0.22f * alpha),
        start = Offset(head * 0.4f, y),
        end = Offset(head, y),
        strokeWidth = core * 3f
    )
    drawLine(brush = brush, start = Offset(0f, y), end = Offset(head, y), strokeWidth = core)
    if (reveal < 1f) {
        val radius = FILAMENT_HEAD_RADIUS.toPx()
        drawCircle(
            brush = Brush.radialGradient(
                0f to AppGargantuaTokens.hot.copy(alpha = 0.8f * alpha),
                1f to AppGargantuaTokens.gold.copy(alpha = 0f),
                center = Offset(head, y),
                radius = radius
            ),
            radius = radius,
            center = Offset(head, y)
        )
    }
}

private const val OPEN_FIRST_ROW = 0.08f
private const val OPEN_CASCADE = 0.5f
private const val OPEN_ROW_SPAN = 0.35f
private const val OPEN_FADE_FROM = 0.3f
private const val OPEN_FADE_TO = 0.42f
private const val CLOSE_CASCADE = 0.45f
private const val CLOSE_ROW_SPAN = 0.3f
private const val WINDOW_OPEN_SPAN = 0.15f
private const val WINDOW_CLOSE_FROM = 0.85f
private val FILAMENT_WIDTH = 1.2.dp
private val FILAMENT_HEAD_RADIUS = 5.dp
