package com.usagemonitor.presentation.ui.components

/**
 * Um quadro da íris do eclipse (Z2, issue #400), que expande e recolhe a HUD no
 * modo "recolher quando parada". Um disco centrado no meio da faixa, rente à
 * borda da tela, revela o notch dentro dele; a borda do disco é um fio de luz
 * quente que só existe durante a transição.
 *
 * [radius] é fração do alcance (`1` cobre o notch inteiro, `0` não mostra
 * nada); [rimAlpha] é a opacidade do fio. Nada de dado anima: o disco recorta o
 * notch já no valor, nenhum arco cresce por efeito.
 */
data class GargantuaIrisFrame(
    val radius: Float = 1f,
    val rimAlpha: Float = 0f
) {
    val settled: Boolean get() = this == Open

    companion object {
        /** Notch inteiro, sem fio: o estado aberto. */
        val Open = GargantuaIrisFrame()

        /** Só a faixa: o estado recolhido. */
        val Closed = GargantuaIrisFrame(radius = 0f, rimAlpha = 0f)
    }
}

/**
 * Expandir: o disco cresce com desaceleração (`easeOut`) e o fio acende e apaga
 * por um seno, no pico no meio do caminho. [progress] em `0..1`.
 */
fun gargantuaIrisOpenFrame(progress: Float): GargantuaIrisFrame {
    val t = progress.coerceIn(0f, 1f)
    if (t >= 1f) return GargantuaIrisFrame.Open
    if (t <= 0f) return GargantuaIrisFrame.Closed
    val radius = easeOut(t)
    return GargantuaIrisFrame(radius = radius, rimAlpha = IRIS_RIM_PEAK * pulse(radius))
}

/**
 * Recolher: o disco fecha acelerando (`easeIn` sobre o que falta), de volta ao
 * meio da faixa. [progress] em `0..1`.
 */
fun gargantuaIrisCloseFrame(progress: Float): GargantuaIrisFrame {
    val t = progress.coerceIn(0f, 1f)
    if (t >= 1f) return GargantuaIrisFrame.Closed
    if (t <= 0f) return GargantuaIrisFrame.Open
    val radius = easeIn(1f - t)
    return GargantuaIrisFrame(radius = radius, rimAlpha = IRIS_RIM_PEAK * pulse(radius))
}

/**
 * De onde começa a expansão quando o disco já tem [radius] — o ponteiro voltou
 * no meio de um recolher. Inverso de [gargantuaIrisOpenFrame]: sem ele o disco
 * saltaria para zero e cresceria de novo.
 */
fun gargantuaIrisOpenProgressFor(radius: Float): Float =
    1f - cbrt(1f - radius.coerceIn(0f, 1f))

/** De onde começa o recolher com o disco em [radius]. Inverso de [gargantuaIrisCloseFrame]. */
fun gargantuaIrisCloseProgressFor(radius: Float): Float =
    1f - cbrt(radius.coerceIn(0f, 1f))

private fun cbrt(value: Float): Float = kotlin.math.cbrt(value.toDouble()).toFloat()

/** O fio no pico: o protótipo Z2 usa 80% do dourado. */
internal const val IRIS_RIM_PEAK = 0.8f
