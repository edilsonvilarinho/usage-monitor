package com.usagemonitor.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.usagemonitor.presentation.ui.theme.AppSpacing
import kotlin.math.roundToLong

const val CLI_TURN_CHART_GRID_TAG = "cliTurnChartGrid"

/** Abaixo disso a grade cai para uma coluna: dois gráficos lado a lado não leem. */
private val TURN_CHART_GRID_TWO_COLUMNS_MIN = 600.dp

/** Altura de cada gráfico da grade: quatro deles na vista sem rolar a 720dp. */
internal val TURN_CHART_GRID_HEIGHT = 96.dp

/**
 * Grade 2×2 dos gráficos por turno do detalhe de sessão (#393, direção T3),
 * a mesma nas duas fontes. Linhas de `Row` + `weight`, e não `FlowRow`: com
 * `weight` dentro de `FlowRow` a célula some (armadilha registrada em
 * `compose-implementation.md`).
 */
@Composable
internal fun CliTurnChartGrid(cells: List<@Composable () -> Unit>, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth().testTag(CLI_TURN_CHART_GRID_TAG)) {
        val columns = if (maxWidth >= TURN_CHART_GRID_TWO_COLUMNS_MIN) 2 else 1
        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
            cells.chunked(columns).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                    verticalAlignment = Alignment.Top
                ) {
                    row.forEach { cell ->
                        Column(Modifier.weight(1f)) { cell() }
                    }
                    // Última linha ímpar: a célula fica na largura da coluna.
                    repeat(columns - row.size) {
                        Column(Modifier.weight(1f)) {}
                    }
                }
            }
        }
    }
}

/** Fração 0–1 em pontos-base para o gráfico, que é de `Long`; `null` continua `null`. */
internal fun fractionsAsBasisPoints(values: List<Double?>): List<Long?> {
    return values.map { value -> value?.let { (it.coerceIn(0.0, 1.0) * BASIS_POINTS).roundToLong() } }
}

internal fun formatBasisPointsPercent(value: Long): String = "${(value / (BASIS_POINTS / 100.0)).roundToLong()} %"

/** Tokens por segundo arredondados; `null` (não medido) continua `null`. */
internal fun ratesAsLong(values: List<Double?>): List<Long?> = values.map { value -> value?.roundToLong() }

private const val BASIS_POINTS = 10_000.0
