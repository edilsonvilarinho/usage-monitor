package com.usagemonitor.presentation.ui.components

import kotlin.math.ceil
import kotlin.math.roundToInt

/** Como condensar os turnos que caem no mesmo bin. */
enum class BinMode {
    /** Soma o que foi gasto no trecho. Correto para cache write por turno. */
    SUM,

    /** Último valor do trecho. Correto para contexto: é o tamanho vivo ao fim do bin. */
    LAST,

    /** Maior valor do trecho. */
    MAX
}

/**
 * Condensa a série para no máximo [targetBins] pontos.
 *
 * Sem isso, 251 turnos em ~900 px viram barras de 1 px — ilegíveis. Séries
 * menores que o alvo passam intactas: nunca se interpola nem se inventa ponto.
 */
fun binSeries(values: List<Long>, targetBins: Int, mode: BinMode): List<Long> {
    if (targetBins <= 0) {
        return emptyList()
    }
    if (values.size <= targetBins) {
        return values
    }

    val binSize = ceil(values.size.toDouble() / targetBins.toDouble()).toInt().coerceAtLeast(1)

    return values.chunked(binSize) { chunk ->
        when (mode) {
            BinMode.SUM -> chunk.sum()
            BinMode.LAST -> chunk.last()
            BinMode.MAX -> chunk.max()
        }
    }
}

/**
 * Teto de escala resistente a outlier.
 *
 * Um único pico de cache write achata todo o resto do gráfico. Escalar pelo
 * percentil [percentile] mantém a série legível; o que passa disso é desenhado
 * clipado no topo e sinalizado.
 */
fun scaleCeiling(values: List<Long>, percentile: Double = 0.99): Long {
    val positive = values.filter { value -> value > 0L }
    if (positive.isEmpty()) {
        return 0L
    }

    val sorted = positive.sorted()
    val index = ((sorted.size - 1) * percentile).roundToInt().coerceIn(0, sorted.size - 1)
    val ceiling = sorted[index]

    // Com poucos pontos o percentil degenera; nunca cortar abaixo da mediana.
    val median = sorted[sorted.size / 2]
    return maxOf(ceiling, median).coerceAtLeast(1L)
}

/**
 * Índices onde a série cai em relação ao ponto anterior.
 *
 * Numa série de contexto, cada queda é uma compactação. O legado somava o
 * transcript inteiro a cada turno, então a curva dele era monotônica e essas
 * quedas eram invisíveis — é a informação mais útil do gráfico.
 */
fun dropIndices(values: List<Long>): List<Int> {
    if (values.size < 2) {
        return emptyList()
    }
    return (1 until values.size).filter { index -> values[index] < values[index - 1] }
}
