package com.usagemonitor.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.TeamMemberTrend
import com.usagemonitor.domain.entity.TeamUsageTrend
import com.usagemonitor.presentation.ui.theme.AppAccents
import com.usagemonitor.presentation.ui.theme.AppShapes
import com.usagemonitor.presentation.ui.theme.AppSpacing

const val TEAM_TREND_CHART_TAG = "teamTrendChart"
const val TEAM_TREND_LEGEND_TAG = "teamTrendLegend"

/** Altura da área de plotagem; três linhas de grade cabem sem apertar as barras. */
private val PLOT_HEIGHT = 150.dp

/** Vão entre as barras de um mesmo dia e entre os grupos de dias. */
private val BAR_GAP = 1.dp
private val GROUP_GAP = 10.dp

/**
 * Piso de largura por barra.
 *
 * Trinta dias por cinco integrantes são 150 barras: numa janela de 900dp isso dá
 * menos de 5dp por barra, e o gráfico vira um borrão. Abaixo do piso a área de
 * plotagem passa a rolar na horizontal — o mesmo recurso que a grade de atividade
 * já usa —, em vez de espremer.
 */
private val MIN_BAR_WIDTH = 5.dp

/**
 * Teto de largura por barra.
 *
 * Com sete dias e três integrantes numa janela larga, dividir a largura toda pelos
 * grupos dá barras de mais de 40dp: um retângulo desse tamanho lê como bloco, não
 * como barra, e o vão entre os dias some. O que sobra vira distância entre grupos,
 * que é o que separa um dia do seguinte.
 */
private val MAX_BAR_WIDTH = 16.dp

/** Altura mínima de um dia com consumo, para não desaparecer na linha de base. */
private const val MIN_VISIBLE_FRACTION = 0.06f

/** Quantas linhas de grade horizontais dividem a área de plotagem. */
private const val GRID_LINES = 3

/**
 * Um rótulo de dia a cada N grupos.
 *
 * Trinta rótulos `dd/MM` lado a lado não cabem, e a alternativa que existia —
 * imprimir só o primeiro e o último — deixava as barras do meio sem escala
 * horizontal: dava para ver que houve um pico, não em que dia.
 */
private const val MAX_DAY_LABELS = 8

/**
 * Tendência diária do time: um grupo de barras por dia, uma cor por integrante.
 *
 * Barras e não linhas de propósito: os dias são discretos e a série tem buracos
 * legítimos — dias sem consumo. Uma linha ligaria dois dias distantes e sugeriria
 * uso contínuo onde houve silêncio.
 *
 * Todas as barras usam a **mesma escala** ([TeamUsageTrend.peakDailyCostMicros]);
 * normalizar cada integrante pelo próprio pico faria quem gasta centavos parecer
 * igual a quem gasta dezenas de dólares.
 *
 * **A cor identifica o integrante**, e isso é a exceção consciente à regra de que
 * acento é identidade de fonte e não de valor: é o que o protótipo desenha, e num
 * gráfico agrupado a cor é o único jeito de dizer de quem é a barra. A paleta sai
 * dos acentos de fonte já medidos contra as duas superfícies e **cicla** — com
 * sete ou mais integrantes duas séries repetem o tom, e quem as separa é a
 * legenda.
 *
 * Sem animação: a aba que a contém é republicada pelo laço ao vivo, e uma
 * transição infinita travaria o `waitForIdle` dos testes de componente.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TeamTrendChart(
    trend: TeamUsageTrend,
    language: AppLanguage,
    modifier: Modifier = Modifier
) {
    val members = trend.members.filter { member -> member.hasActivity }
    if (members.isEmpty() || trend.days.isEmpty()) {
        return
    }

    val palette = trendSeriesPalette()
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val emptyColor = MaterialTheme.colorScheme.surfaceVariant
    val peak = trend.peakDailyCostMicros

    AppDataSurface(modifier = modifier.testTag(TEAM_TREND_CHART_TAG)) {
        // Cabeçalho do gráfico: o que ele mede à esquerda, de quem é cada cor à
        // direita. A frase mora aqui e não num painel acima porque é legenda do
        // gráfico, não texto da tela.
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)
        ) {
            Text(
                text = TeamTrendLabels.chartCaption(language),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
                modifier = Modifier.testTag(TEAM_TREND_LEGEND_TAG),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)
            ) {
                members.forEachIndexed { index, member ->
                    // O total do período vem junto do apelido: ele estava na
                    // faixa por integrante que este gráfico substituiu, e a
                    // legenda é o único lugar onde cada pessoa aparece uma vez.
                    TrendLegendEntry(
                        label = "${member.alias} · " +
                            teamTrendTotalLabel(member.totalCostMicros, language),
                        color = palette[index % palette.size]
                    )
                }
            }
        }

        // O grupo ocupa a largura que sobra, dividida pelos dias — e só encolhe
        // até o piso por barra. Abaixo dele a área passa a rolar: espremer as
        // barras até sumirem seria pior que oferecer a rolagem.
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val barGaps = BAR_GAP * (members.size - 1)
            val minimumGroupWidth = MIN_BAR_WIDTH * members.size + barGaps
            val maximumGroupWidth = MAX_BAR_WIDTH * members.size + barGaps
            val naturalGroupWidth = (maxWidth - GROUP_GAP * (trend.days.size - 1)) / trend.days.size
            val groupWidth = naturalGroupWidth.coerceIn(minimumGroupWidth, maximumGroupWidth)
            val barWidth = (groupWidth - barGaps) / members.size
            val scrolls = groupWidth > naturalGroupWidth

            // O que sobra depois do teto vira distância entre os dias, e não
            // barra mais gorda: é o vão que faz um grupo ser um dia. Os dois
            // `Row` abaixo — barras e rótulos — recebem o mesmo vão, ou o rótulo
            // deixa de cair debaixo do grupo que descreve.
            val groupGap = if (scrolls || trend.days.size < 2) {
                GROUP_GAP
            } else {
                maxOf(GROUP_GAP, (maxWidth - groupWidth * trend.days.size) / (trend.days.size - 1))
            }

            Column(
                modifier = if (scrolls) {
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                } else {
                    Modifier.fillMaxWidth()
                },
                verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)
            ) {
                Row(
                    modifier = Modifier
                        .height(PLOT_HEIGHT)
                        // As linhas de grade são do fundo da área de plotagem, não
                        // de cada grupo: desenhadas por grupo elas ganhariam o vão
                        // entre eles e deixariam de ser uma linha.
                        .drawBehind {
                            val step = size.height / (GRID_LINES + 1)
                            repeat(GRID_LINES) { index ->
                                val y = step * (index + 1)
                                drawLine(
                                    color = gridColor,
                                    start = Offset(0f, y),
                                    end = Offset(size.width, y),
                                    strokeWidth = 1f
                                )
                            }
                        },
                    horizontalArrangement = Arrangement.spacedBy(groupGap),
                    verticalAlignment = Alignment.Bottom
                ) {
                    trend.days.forEachIndexed { dayIndex, _ ->
                        Row(
                            modifier = Modifier.width(groupWidth).fillMaxHeight(),
                            horizontalArrangement = Arrangement.spacedBy(BAR_GAP),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            members.forEachIndexed { memberIndex, member ->
                                TrendBar(
                                    member = member,
                                    dayIndex = dayIndex,
                                    peak = peak,
                                    color = palette[memberIndex % palette.size],
                                    emptyColor = emptyColor,
                                    width = barWidth
                                )
                            }
                        }
                    }
                }

                TrendDayAxis(days = trend.days, groupWidth = groupWidth, groupGap = groupGap)
            }
        }

        Text(
            text = TeamTrendLabels.chartNotice(language),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Paleta de séries: os acentos de identidade de fonte, nesta ordem.
 *
 * Reusa os tons que `AppAccentsContrastTest` já mede contra as duas superfícies —
 * uma paleta nova seria seis valores novos a auditar. A ordem começa nos dois
 * mais distantes entre si em matiz, para o caso comum de dois ou três
 * integrantes.
 */
@Composable
private fun trendSeriesPalette(): List<Color> {
    val accents = AppAccents.current
    return listOf(
        accents.anthropic,
        accents.codex,
        accents.deepseek,
        accents.minimax,
        accents.opencode,
        accents.kilo
    )
}

@Composable
private fun TrendLegendEntry(label: String, color: Color) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(AppShapes.extraSmall)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
private fun TrendBar(
    member: TeamMemberTrend,
    dayIndex: Int,
    peak: Long,
    color: Color,
    emptyColor: Color,
    width: Dp
) {
    val point = member.points.getOrNull(dayIndex)
    val costMicros = point?.costMicros ?: 0L
    val fraction = if (peak <= 0L) {
        0f
    } else {
        (costMicros.toDouble() / peak.toDouble()).toFloat().coerceIn(0f, 1f)
    }

    Box(
        modifier = Modifier
            .width(width)
            .fillMaxHeight(
                if (costMicros > 0L) {
                    maxOf(fraction, MIN_VISIBLE_FRACTION)
                } else {
                    // Traço fino de base: um dia sem consumo tem de ser
                    // distinguível de um dia fora da janela.
                    MIN_VISIBLE_FRACTION / 2f
                }
            )
            // Raio 4: a barra de um dia é estreita, e um raio de botão arredonda
            // tanto que o topo deixa de marcar a altura — que é o dado que a
            // barra existe para dar.
            .clip(AppShapes.extraSmall)
            .background(if (costMicros > 0L) color else emptyColor)
    )
}

/**
 * Rótulos de dia sob os grupos que eles descrevem.
 *
 * Cada rótulo ocupa a largura do próprio grupo, então ele cai debaixo das barras
 * daquele dia mesmo com a área rolando. Os dias sem rótulo entram como vão da
 * mesma largura — sem eles a fileira encolheria e os rótulos escorregariam.
 */
@Composable
private fun TrendDayAxis(days: List<LocalDate>, groupWidth: Dp, groupGap: Dp) {
    val step = ((days.size + MAX_DAY_LABELS - 1) / MAX_DAY_LABELS).coerceAtLeast(1)

    Row(horizontalArrangement = Arrangement.spacedBy(groupGap)) {
        days.forEachIndexed { index, date ->
            // O último dia sempre ganha rótulo: é a ponta que diz até quando a
            // série vai, e ela cai fora de qualquer passo regular.
            val labeled = index % step == 0 || index == days.lastIndex
            if (labeled) {
                Text(
                    text = shortDayLabel(date),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    // A largura é a do grupo, mas o texto **não** trunca: um
                    // `dd/MM` é mais largo que um grupo de três barras e
                    // transborda para os vizinhos, que estão vazios. Prendê-lo de
                    // verdade daria "12/…", que não é uma data.
                    modifier = Modifier.width(groupWidth)
                )
            } else {
                Spacer(modifier = Modifier.width(groupWidth))
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

/**
 * Textos do gráfico.
 *
 * Ficam aqui, e não em `TeamUsageFormatting`, porque descrevem o desenho — o que
 * a barra mede e o que a altura mínima significa — e não a tela que o contém.
 */
internal object TeamTrendLabels {

    fun chartCaption(language: AppLanguage): String {
        return if (language == AppLanguage.PT) {
            "Custo por dia · escala única entre integrantes"
        } else {
            "Cost per day · one scale across members"
        }
    }

    fun chartNotice(language: AppLanguage): String {
        return if (language == AppLanguage.PT) {
            "Todo integrante ganha ponto em todo dia — série com buracos desenharia " +
                "continuidade onde houve silêncio. Barra de altura mínima visível é zero " +
                "explícito, não ausência de dado."
        } else {
            "Every member gets a point on every day — a series with gaps would draw " +
                "continuity where there was silence. A minimum-height bar is an explicit " +
                "zero, not missing data."
        }
    }
}

internal fun teamTrendTotalLabel(totalCostMicros: Long, language: AppLanguage): String {
    val dollars = totalCostMicros / 1_000_000L
    val cents = (totalCostMicros % 1_000_000L) / 10_000L
    val formatted = "$$dollars.${cents.toString().padStart(2, '0')}"
    return if (language == AppLanguage.PT) "$formatted no período" else "$formatted in range"
}
