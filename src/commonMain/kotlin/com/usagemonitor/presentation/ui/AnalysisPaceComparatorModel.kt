package com.usagemonitor.presentation.ui

import com.usagemonitor.domain.entity.AnalysisWindowView
import com.usagemonitor.domain.entity.AppLanguage
import kotlin.math.pow
import kotlin.math.roundToLong

/** Linha do comparador: rótulo, valor em texto (`—` = não medido), fração da barra na escala comum e a palavra contra o seguro. */
internal data class PaceComparatorRow(val label: String, val value: String, val fraction: Float?, val verdict: String?, val aboveSafe: Boolean?)

/** Comparador de ritmo da janela (#610, D2/D3): média e última hora na mesma escala; `marker` é o seguro nessa escala. */
internal data class PaceComparatorModel(val eyebrow: String, val marker: Float?, val safeCaption: String?, val rows: List<PaceComparatorRow>)

/** Função pura do comparador: escala, frações, marca do seguro e palavras; testada sem tela. */
internal object AnalysisPaceComparatorModel {
    private val STEPS = listOf(1.0, 2.0, 3.0, 5.0)

    fun of(window: AnalysisWindowView, language: AppLanguage): PaceComparatorModel {
        val safe = window.safePace
        val scale = scaleCeiling(listOfNotNull(window.paceNow, window.paceAverage, safe).maxOrNull())
        val scaleText = scaleText(scale, language)
        return PaceComparatorModel(
            eyebrow = localized(language, "Ritmo · mesma escala · 0 a $scaleText %/h", "Pace · same scale · 0 to $scaleText %/h"),
            marker = safe?.let { (it / scale).toFloat() },
            safeCaption = safe?.let { localized(language, "seguro ${paceLabel(it, language)}", "safe ${paceLabel(it, language)}") },
            rows = listOf(
                row(localized(language, "Média", "Average"), window.paceAverage, safe, scale, language),
                row(localized(language, "Última hora", "Last hour"), window.paceNow, safe, scale, language)
            )
        )
    }

    /** Menor degrau 1-2-3-5 × 10^k que cobre o maior ritmo; sem ritmo medido positivo, 1 %/h. */
    fun scaleCeiling(value: Double?): Double {
        if (value == null || value <= 0.0) return 1.0
        for (exponent in -3..6) {
            val magnitude = 10.0.pow(exponent)
            for (step in STEPS) {
                if (step * magnitude >= value) return step * magnitude
            }
        }
        return value
    }

    private fun row(label: String, pace: Double?, safe: Double?, scale: Double, language: AppLanguage): PaceComparatorRow {
        if (pace == null) return PaceComparatorRow(label, "—", null, null, null)
        val above = safe != null && pace > safe
        val verdict = when {
            safe == null -> null
            above -> localized(language, "acima do seguro", "above the safe pace")
            else -> localized(language, "abaixo do seguro", "below the safe pace")
        }
        return PaceComparatorRow(label, paceLabel(pace, language), (pace / scale).toFloat().coerceIn(0f, 1f), verdict, if (safe == null) null else above)
    }

    private fun scaleText(scale: Double, language: AppLanguage): String =
        if (scale < 1.0) paceLabel(scale, language).removeSuffix(" %/h") else scale.roundToLong().toString()
}
