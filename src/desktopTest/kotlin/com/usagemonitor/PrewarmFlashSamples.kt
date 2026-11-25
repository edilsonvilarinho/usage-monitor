package com.usagemonitor

/** Um quadro da amostragem; [lit] indica que algum pixel do quadrado saiu do magenta. */
internal data class PrewarmSample(val startNanos: Long, val endNanos: Long, val lit: Boolean)

/** Os dois jeitos de montar a janela: `WindowState` e `DialogState`. */
internal enum class PrewarmOverload { WINDOW_STATE, DIALOG_STATE }

/** Um ciclo: instantes do `componentShown` e do `componentHidden`, em nanoTime. */
internal data class PrewarmCycle(
    val index: Int,
    val overload: PrewarmOverload,
    val shownNanos: Long,
    val hiddenNanos: Long
)

/** Intervalo contínuo de quadros acesos. */
internal data class PrewarmFlash(val startNanos: Long, val endNanos: Long, val samples: Int)

/** Flash classificado; sem ciclo coincidente, [cycle], [overload] e os offsets ficam nulos (não medido). */
internal data class ClassifiedPrewarmFlash(
    val cycle: Int?,
    val overload: PrewarmOverload?,
    val startMsFromShown: Double?,
    val endMsFromShown: Double?,
    val startMsFromHidden: Double?,
    val endMsFromHidden: Double?
)

/** Lógica pura da sonda do flash (#669): agrupa, classifica e resume. */
internal object PrewarmFlashSamples {

    private const val NANOS_PER_MS = 1_000_000.0
    private const val NANOS_PER_SECOND = 1_000_000_000.0
    private val PREWARM_MS_PATTERN = Regex("""pré-aquecida em (\d+) ms""")

    /** Agrupa quadros acesos consecutivos; um quadro apagado fecha o flash em curso. */
    fun flashesOf(samples: List<PrewarmSample>): List<PrewarmFlash> {
        val flashes = mutableListOf<PrewarmFlash>()
        var open: PrewarmFlash? = null
        for (sample in samples) {
            if (sample.lit) {
                val current = open
                open = if (current == null) {
                    PrewarmFlash(sample.startNanos, sample.endNanos, 1)
                } else {
                    PrewarmFlash(current.startNanos, sample.endNanos, current.samples + 1)
                }
            } else if (open != null) {
                flashes.add(open)
                open = null
            }
        }
        if (open != null) flashes.add(open)
        return flashes
    }

    /**
     * Classifica cada flash pelo ciclo cujo intervalo [shown, hidden], estendido por [marginNanos] em cada lado,
     * se sobrepõe a ele. Entre candidatos vence o de menor distância ao intervalo não estendido. Sem candidato, tudo nulo.
     */
    fun classify(flashes: List<PrewarmFlash>, cycles: List<PrewarmCycle>, marginNanos: Long): List<ClassifiedPrewarmFlash> =
        flashes.map { flash ->
            val covering = nearestCycle(flash, cycles, marginNanos)
            if (covering == null) {
                ClassifiedPrewarmFlash(null, null, null, null, null, null)
            } else {
                ClassifiedPrewarmFlash(
                    cycle = covering.index,
                    overload = covering.overload,
                    startMsFromShown = offsetMs(flash.startNanos, covering.shownNanos),
                    endMsFromShown = offsetMs(flash.endNanos, covering.shownNanos),
                    startMsFromHidden = offsetMs(flash.startNanos, covering.hiddenNanos),
                    endMsFromHidden = offsetMs(flash.endNanos, covering.hiddenNanos)
                )
            }
        }

    /** Mediana; lista vazia devolve nulo (não medido). */
    fun median(values: List<Double>): Double? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2.0
    }

    /** Percentil 95 por posto mais próximo: posto = ceil(0.95 * n), em inteiro para não haver erro de ponto flutuante. */
    fun percentile95(values: List<Double>): Double? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val rank = (95 * sorted.size + 99) / 100
        return sorted[rank - 1]
    }

    /** Quadros por segundo: contagem dividida pelo intervalo do primeiro início ao último fim. */
    fun samplesPerSecond(samples: List<PrewarmSample>): Double {
        if (samples.isEmpty()) return 0.0
        val start = samples.minOf { it.startNanos }
        val end = samples.maxOf { it.endNanos }
        val span = end - start
        if (span <= 0L) return 0.0
        return samples.size * NANOS_PER_SECOND / span
    }

    /** Extrai os ms da mensagem do breadcrumb de pré-aquecimento; outra mensagem devolve nulo. */
    fun prewarmMillisOf(message: String): Long? =
        PREWARM_MS_PATTERN.find(message)?.groupValues?.get(1)?.toLongOrNull()

    /** Ciclo candidato de menor distância ao intervalo não estendido; empate fica com o primeiro. */
    private fun nearestCycle(flash: PrewarmFlash, cycles: List<PrewarmCycle>, marginNanos: Long): PrewarmCycle? {
        var best: PrewarmCycle? = null
        var bestGap = Long.MAX_VALUE
        for (cycle in cycles) {
            val overlaps = flash.startNanos <= cycle.hiddenNanos + marginNanos &&
                flash.endNanos >= cycle.shownNanos - marginNanos
            if (!overlaps) continue
            val gap = gapNanos(flash, cycle)
            if (gap < bestGap) {
                best = cycle
                bestGap = gap
            }
        }
        return best
    }

    /** Distância do flash ao intervalo [shown, hidden] sem margem; zero quando há sobreposição. */
    private fun gapNanos(flash: PrewarmFlash, cycle: PrewarmCycle): Long = when {
        flash.endNanos < cycle.shownNanos -> cycle.shownNanos - flash.endNanos
        flash.startNanos > cycle.hiddenNanos -> flash.startNanos - cycle.hiddenNanos
        else -> 0L
    }

    private fun offsetMs(instantNanos: Long, referenceNanos: Long): Double =
        (instantNanos - referenceNanos) / NANOS_PER_MS
}
