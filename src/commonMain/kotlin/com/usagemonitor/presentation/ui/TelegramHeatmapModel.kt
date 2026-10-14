package com.usagemonitor.presentation.ui

import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.ComparisonMetric
import com.usagemonitor.domain.entity.ComparisonRow
import com.usagemonitor.domain.entity.displayName
import com.usagemonitor.domain.entity.hasCliSessionIndex
import com.usagemonitor.domain.entity.heatIntensity

/** Uma célula do mapa: o texto sempre escrito e a intensidade da cor (0–1), `null` sem valor. */
data class TelegramHeatmapCell(val text: String, val intensity: Double?)

data class TelegramHeatmapRow(val label: String, val cells: List<TelegramHeatmapCell>)

/**
 * O mapa de calor do `/comparar` (#408, AA6) pronto para desenhar: a mesma tabela
 * da janela de comparação (#386, direção P7) — mesmos rótulos, mesmas células
 * (`comparisonCell`) e a mesma intensidade (`heatIntensity`, valor sobre o maior da
 * coluna). Montado aqui, puro; o PNG sai em `desktopMain`.
 */
data class TelegramHeatmap(val columns: List<String>, val rows: List<TelegramHeatmapRow>, val note: String) {
    val isEmpty: Boolean get() = rows.isEmpty()
}

/** Teto de linhas: o PNG tem altura fixa por linha e o Telegram reduz imagem alta. */
const val TELEGRAM_HEATMAP_MAX_ROWS = 12

fun buildTelegramHeatmap(rows: List<ComparisonRow>, language: AppLanguage): TelegramHeatmap {
    val metrics = ComparisonMetric.entries
    val shown = rows.take(TELEGRAM_HEATMAP_MAX_ROWS)
    return TelegramHeatmap(
        columns = metrics.map { metric -> ComparisonLabels.metric(metric, language) },
        rows = shown.map { row ->
            TelegramHeatmapRow(
                label = heatmapLabel(row, language),
                // Intensidade contra **todas** as linhas: cortar no teto não muda a escala.
                cells = metrics.map { metric -> TelegramHeatmapCell(comparisonCell(row, metric, language), heatIntensity(rows, row, metric)) }
            )
        },
        note = ComparisonLabels.notes(language).first()
    )
}

/** Modelo do Claude CLI e do Codex com a fonte na frente; linha de fonte já é o nome dela. */
private fun heatmapLabel(row: ComparisonRow, language: AppLanguage): String =
    if (row.source.hasCliSessionIndex()) "${row.source.displayName()} · ${row.label}" else row.label
