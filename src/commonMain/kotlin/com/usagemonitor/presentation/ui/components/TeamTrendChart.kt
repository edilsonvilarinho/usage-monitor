package com.usagemonitor.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.TeamMemberTrend
import com.usagemonitor.domain.entity.TeamUsageTrend
import com.usagemonitor.presentation.ui.theme.AppShapes

const val TEAM_TREND_CHART_TAG = "teamTrendChart"

private val BAR_AREA_HEIGHT = 48.dp
private val BAR_GAP = 1.dp
private val ALIAS_COLUMN_WIDTH = 120.dp

/** Altura mínima de um dia com consumo, para não desaparecer na linha de base. */
private const val MIN_VISIBLE_FRACTION = 0.06f

/**
 * Tendência diária do time: uma faixa de barras por integrante.
 *
 * Barras e não linhas de propósito: os dias são discretos e a série tem buracos
 * legítimos — dias sem consumo. Uma linha ligaria dois dias distantes e
 * sugeriria uso contínuo onde houve silêncio.
 *
 * Todas as faixas usam a **mesma escala** ([TeamUsageTrend.peakDailyCostMicros]);
 * normalizar cada integrante pelo próprio pico faria quem gasta centavos parecer
 * igual a quem gasta dezenas de dólares.
 *
 * Sem animação: a lista que a contém é republicada pelo laço ao vivo, e uma
 * transição infinita travaria o `waitForIdle` dos testes de componente.
 */
@Composable
fun TeamTrendChart(
    trend: TeamUsageTrend,
    accent: Color,
    language: AppLanguage,
    modifier: Modifier = Modifier
) {
    val peak = trend.peakDailyCostMicros
    val emptyColor = MaterialTheme.colorScheme.surfaceVariant

    Column(
        modifier = modifier.fillMaxWidth().testTag(TEAM_TREND_CHART_TAG),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Integrante sem nenhum dia de consumo fica de fora: uma faixa vazia
        // ocuparia a altura de uma linha para dizer nada.
        trend.members.filter { member -> member.hasActivity }.forEach { member ->
            TrendRow(member = member, peak = peak, accent = accent, emptyColor = emptyColor, language = language)
        }

        TrendDayAxis(days = trend.days)
    }
}

/**
 * Primeiro e último dia da série, uma vez só, abaixo de todas as faixas.
 *
 * Sem eles as barras são retângulos sem escala horizontal: dá para ver que houve
 * um pico, não em que dia. Só os extremos porque trinta rótulos não cabem, e
 * repeti-los por integrante seria ruído — o eixo é o mesmo para todas as faixas.
 */
@Composable
private fun TrendDayAxis(days: List<LocalDate>) {
    val first = days.firstOrNull() ?: return
    val last = days.lastOrNull() ?: return

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Recuo igual ao da coluna de apelidos: sem ele os rótulos nasceriam
        // deslocados da primeira e da última barra que descrevem.
        Spacer(modifier = Modifier.width(ALIAS_COLUMN_WIDTH))
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = shortDayLabel(first),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (last != first) {
                Text(
                    text = shortDayLabel(last),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** `dd/MM`, o mesmo formato curto que o resto da UI usa para data sem hora. */
private fun shortDayLabel(date: LocalDate): String {
    val day = date.dayOfMonth.toString().padStart(2, '0')
    val month = date.monthNumber.toString().padStart(2, '0')
    return "$day/$month"
}

@Composable
private fun TrendRow(
    member: TeamMemberTrend,
    peak: Long,
    accent: Color,
    emptyColor: Color,
    language: AppLanguage
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(modifier = Modifier.width(ALIAS_COLUMN_WIDTH)) {
            Text(
                text = member.alias,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = teamTrendTotalLabel(member.totalCostMicros, language),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(
            modifier = Modifier.weight(1f).height(BAR_AREA_HEIGHT),
            horizontalArrangement = Arrangement.spacedBy(BAR_GAP),
            verticalAlignment = Alignment.Bottom
        ) {
            member.points.forEach { point ->
                val fraction = if (peak <= 0L) {
                    0f
                } else {
                    (point.costMicros.toDouble() / peak.toDouble()).toFloat().coerceIn(0f, 1f)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(
                            if (point.costMicros > 0L) {
                                maxOf(fraction, MIN_VISIBLE_FRACTION)
                            } else {
                                // Traço fino de base: um dia sem consumo tem de
                                // ser distinguível de um dia fora da janela.
                                MIN_VISIBLE_FRACTION / 2f
                            }
                        )
                        // Raio 4: a barra de um dia é estreita, e um raio de
                        // botão arredonda tanto que o topo deixa de marcar a
                        // altura — que é o dado que a barra existe para dar.
                        .clip(AppShapes.extraSmall)
                        .background(if (point.costMicros > 0L) accent else emptyColor)
                )
            }
        }
    }
}

internal fun teamTrendTotalLabel(totalCostMicros: Long, language: AppLanguage): String {
    val dollars = totalCostMicros / 1_000_000L
    val cents = (totalCostMicros % 1_000_000L) / 10_000L
    val formatted = "$$dollars.${cents.toString().padStart(2, '0')}"
    return if (language == AppLanguage.PT) "$formatted no período" else "$formatted in range"
}
