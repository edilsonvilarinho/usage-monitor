package com.usagemonitor

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import java.awt.GraphicsEnvironment

/**
 * Folga entre a janela e a borda da área útil.
 *
 * Existe porque `WindowPosition(Alignment.Center)` centraliza contra a tela
 * **inteira**, e não contra a área útil: uma janela do tamanho exato da área útil
 * nasceria metade da altura da barra de tarefas abaixo do limite de baixo. A folga
 * também deixa a borda de arrasto alcançável nos quatro lados.
 */
internal val WINDOW_SCREEN_MARGIN = 24.dp

/** Piso comum para impedir que qualquer janela fique sem área útil recuperável. */
internal val DEFAULT_MODAL_MIN_HEIGHT = 320.dp

/**
 * Piso absoluto de cada eixo, para uma área útil absurda não produzir janela de
 * alguns dp. Não é o tamanho mínimo de nenhuma janela — é rede contra medida
 * inválida da plataforma.
 */
private val MIN_WINDOW_EDGE = 320.dp

/**
 * Área útil do monitor, em `Dp`, com origem.
 *
 * A origem importa: barra de tarefas no topo ou à esquerda desloca o canto da área
 * útil, e prender a posição contra zero deixaria a janela debaixo dela. Eixo não
 * finito (`Dp.Unspecified`) significa "sem medida" e é tratado como "sem limite"
 * por todo mundo aqui.
 */
internal data class ScreenWorkArea(
    val x: Dp,
    val y: Dp,
    val size: DpSize
) {
    companion object {
        val Unknown = ScreenWorkArea(
            x = Dp.Unspecified,
            y = Dp.Unspecified,
            size = DpSize(Dp.Unspecified, Dp.Unspecified)
        )
    }
}

/**
 * Consulta a área útil do monitor padrão.
 *
 * `maximumWindowBounds` já desconta a barra de tarefas e vem em espaço de usuário —
 * já dividido pela escala do sistema —, o que a aproxima de `Dp` mas **não** a
 * iguala em todo monitor. É rede de segurança, não medida exata, pela mesma razão
 * documentada em [availableWindowSizeDp]. Falha na consulta devolve
 * [ScreenWorkArea.Unknown], que não limita nada.
 */
internal fun availableWindowAreaDp(): ScreenWorkArea {
    return runCatching {
        val bounds = GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
        ScreenWorkArea(
            x = bounds.x.dp,
            y = bounds.y.dp,
            size = DpSize(bounds.width.dp, bounds.height.dp)
        )
    }.getOrDefault(ScreenWorkArea.Unknown)
}

/**
 * Tamanho inicial de janela que cabe na tela.
 *
 * Todas as janelas do app são `undecorated` com barra de título própria: uma janela
 * mais alta que a tela nasce centralizada com o topo em coordenada negativa, e aí
 * **não existe botão de fechar** — não há moldura do sistema para socorrer. Vale
 * tanto para o tamanho default escalado quanto para o persistido: tamanho salvo num
 * monitor grande volta inacessível no notebook, e nesse caso a escolha do usuário
 * descreve uma tela que já não existe.
 */
internal fun fitWindowSize(
    desired: DpSize,
    workArea: DpSize,
    margin: Dp = WINDOW_SCREEN_MARGIN
): DpSize {
    return DpSize(
        width = desired.width.fittedTo(workArea.width, margin),
        height = desired.height.fittedTo(workArea.height, margin)
    )
}

internal fun fitWindowSize(
    desired: DpSize,
    workArea: ScreenWorkArea,
    margin: Dp = WINDOW_SCREEN_MARGIN
): DpSize = fitWindowSize(desired, workArea.size, margin)

/**
 * Posição persistida presa dentro da área útil.
 *
 * O caso que a issue #72 mostra é o eixo vertical: com a barra de título fora da
 * tela, fechar a janela deixa de ser possível pelo ponteiro. O eixo horizontal cai
 * na mesma regra porque os botões da barra ficam à direita.
 *
 * Só é aplicada a posição **absoluta**; `Alignment.Center` é resolvido pela
 * plataforma e, com o tamanho já ajustado, cai dentro da tela sozinho.
 */
internal fun fitWindowPosition(
    x: Dp,
    y: Dp,
    size: DpSize,
    workArea: ScreenWorkArea
): WindowPosition {
    return WindowPosition(
        x = x.fittedInside(workArea.x, workArea.size.width, size.width),
        y = y.fittedInside(workArea.y, workArea.size.height, size.height)
    )
}

// `Dp.Unspecified` é `NaN`, e comparar NaN por igualdade é o teste que falha em
// silêncio: quem decide aqui é `isFinite`, como em `scaledWindowSize`.
private fun Dp.fittedTo(limit: Dp, margin: Dp): Dp {
    if (!value.isFinite() || !limit.value.isFinite()) {
        return this
    }

    val usable = maxOf(limit - margin, MIN_WINDOW_EDGE)
    return minOf(this, usable)
}

private fun Dp.fittedInside(origin: Dp, available: Dp, edge: Dp): Dp {
    if (!value.isFinite() || !origin.value.isFinite() || !available.value.isFinite()) {
        return this
    }

    val edgeSize = if (edge.value.isFinite()) edge else 0.dp
    // Janela mais larga que a área útil (medida da plataforma imprecisa, ou monitor
    // secundário menor): o limite superior cai abaixo da origem e o canto de cima
    // vence — é ele que carrega a barra de título.
    val maxStart = maxOf(origin, origin + available - edgeSize)
    return coerceIn(origin, maxStart)
}
