package com.usagemonitor.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.CliSessionAnalytics
import com.usagemonitor.domain.entity.CliSessionHealth
import com.usagemonitor.domain.entity.CliSessionHealthTally
import com.usagemonitor.presentation.ui.components.AppBorderWidth
import com.usagemonitor.presentation.ui.components.AppDataRow
import com.usagemonitor.presentation.ui.components.AppDataSurfaceFlush
import com.usagemonitor.presentation.ui.components.AppExpandable
import com.usagemonitor.presentation.ui.components.AppMetricBlock
import com.usagemonitor.presentation.ui.components.AppSectionHeader
import com.usagemonitor.presentation.ui.components.AppTone
import com.usagemonitor.presentation.ui.components.HoverTooltipBox
import com.usagemonitor.presentation.ui.components.TooltipMetric
import com.usagemonitor.presentation.ui.theme.AppAccents
import com.usagemonitor.presentation.ui.theme.AppShapes
import com.usagemonitor.presentation.ui.theme.AppSpacing

/**
 * "1 saturada · 2 em atenção" no cabeçalho, ou nada quando não há o que alertar.
 *
 * A cor é a do pior caso presente: um "em atenção" laranja ao lado de um
 * "saturada" vermelho diluiria o segundo.
 *
 * `internal` porque os dois modais têm o mesmo problema — o do time ainda pior,
 * já que lá o veredito vive dois níveis abaixo, dentro de um integrante recolhido.
 */
/**
 * Cor do resumo de vereditos: a do pior estado presente.
 *
 * `null` quando não há aviso nenhum — aí o texto também não existe, e devolver
 * uma cor faria o chamador achar que há algo a pintar.
 */
@Composable
internal fun healthTallyColor(tally: CliSessionHealthTally): Color? {
    if (!tally.hasWarnings) {
        return null
    }
    return if (tally.saturated > 0) {
        healthColor(CliSessionHealth.SATURATED, AppAccents.current)
    } else {
        healthColor(CliSessionHealth.ATTENTION, AppAccents.current)
    }
}

@Composable
internal fun HealthTallyText(tally: CliSessionHealthTally, language: AppLanguage) {
    val label = CliSessionsLabels.healthTally(tally, language) ?: return
    val accents = AppAccents.current
    val accent = if (tally.saturated > 0) {
        healthColor(CliSessionHealth.SATURATED, accents)
    } else {
        healthColor(CliSessionHealth.ATTENTION, accents)
    }

    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = accent
    )
}

/**
 * Cor do veredito de saúde.
 *
 * [accents] sem default: a versão anterior caía para `darkAppAccents` sempre que
 * um chamador esquecesse o parâmetro, e foi assim que este veredito ficou preso
 * à paleta escura no tema claro (2,64:1 medido contra a `surface` clara). Quem
 * está dentro de uma composição passa `AppAccents.current`.
 */
internal fun healthColor(
    health: CliSessionHealth,
    accents: AppAccents
): Color {
    return when (health) {
        CliSessionHealth.HEALTHY -> accents.cacheRead
        CliSessionHealth.ATTENTION -> accents.cacheWrite
        CliSessionHealth.SATURATED -> accents.saturated
    }
}

/**
 * O mesmo veredito como severidade do sistema.
 *
 * Convive com [healthColor] em vez de substituí-lo: aquele ainda serve a quem
 * precisa da cor crua para pintar traço de gráfico, e este entrega o par
 * ponto + palavra que a lista e o detalhe usam.
 */
internal fun healthTone(health: CliSessionHealth): AppTone {
    return when (health) {
        CliSessionHealth.HEALTHY -> AppTone.OK
        CliSessionHealth.ATTENTION -> AppTone.WARNING
        CliSessionHealth.SATURATED -> AppTone.CRITICAL
    }
}

@Composable
internal fun DetailSection(
    title: String,
    accent: Color,
    // Sem default: um `help` acompanhado de um idioma implícito renderizaria
    // português no meio da tela em inglês, e o compilador não reclamaria.
    language: AppLanguage,
    trailing: String? = null,
    help: List<GlossaryTerm> = emptyList(),
    content: @Composable () -> Unit
) {
    AppDataSurfaceFlush(
        modifier = Modifier.fillMaxWidth(),
        header = {
            AppSectionHeader(
                title = title,
                markerColor = accent,
                trailing = {
                    if (help.isNotEmpty()) {
                        HelpDot(terms = help, language = language)
                    }
                    if (trailing != null) {
                        Text(
                            text = trailing,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        }
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(AppSpacing.md)) {
            content()
        }
    }
}

/**
 * O bloco de métrica da tela de sessões.
 *
 * O desenho é [AppMetricBlock], que é a primitiva compartilhada; aqui ficam só
 * as duas coisas que pertencem a esta tela: a largura fixa das fileiras de
 * métrica do detalhe e o `?` do glossário.
 */
@Composable
internal fun MetricCard(
    label: String,
    value: String,
    accent: Color,
    // Mesma razão de [DetailSection]: idioma implícito vaza português.
    language: AppLanguage,
    footer: String? = null,
    help: GlossaryTerm? = null
) {
    AppMetricBlock(
        label = label,
        value = value,
        modifier = Modifier.width(METRIC_BLOCK_WIDTH),
        footer = footer,
        footerColor = accent,
        labelTrailing = help?.let { term ->
            { HelpDot(terms = listOf(term), language = language) }
        }
    )
}

/**
 * Só o valor, sem a legenda.
 *
 * Existe para listas que carregam as legendas numa faixa de cabeçalho: repeti-las
 * dentro de cada linha dobra o texto da lista sem acrescentar informação. Fica ao
 * lado de [MetricText] e com a mesma tipografia de valor de propósito — as duas
 * anatomias têm de cair na mesma linha de base quando aparecem lado a lado.
 */
@Composable
internal fun MetricValue(
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier
) {
    Text(
        text = value,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = valueColor,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

// `internal`, e não `private`, porque a tela de time reaproveita estes blocos:
// duas listas com a mesma anatomia não podem ter duas implementações de célula.
//
// O valor é `label*` — mono — e não `body*`. A escala divide as duas famílias por
// papel, não por tamanho: `body*` é sans e existe para texto corrido, e estas são
// as células de valor de duas listas tabulares. Número em fonte proporcional não
// alinha coluna, que é a razão de a mono estar aqui.
@Composable
internal fun MetricText(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = valueColor
        )
    }
}

@Composable
internal fun NoticeText(message: String, color: Color) {
    Text(text = message, style = MaterialTheme.typography.labelSmall, color = color)
}

/**
 * O `?` ao lado de um título, com a definição no hover.
 *
 * A tooltip é persistente (ver `HoverTooltipBox`): explicação de três linhas não
 * se lê no tempo de uma tooltip que some sozinha.
 */
@Composable
internal fun HelpDot(terms: List<GlossaryTerm>, language: AppLanguage) {
    val entries = terms.map { term -> CliSessionsGlossary.entry(term, language) }
    val first = entries.first()

    HoverTooltipBox(
        title = first.title,
        subtitle = first.explanation,
        // Os termos seguintes entram como métricas para não empilhar tooltips:
        // um gráfico pode carregar duas dúvidas, e são duas linhas, não dois `?`.
        metrics = entries.drop(1).map { entry -> TooltipMetric(entry.title, entry.explanation) }
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(AppShapes.extraSmall)
                .border(AppBorderWidth, MaterialTheme.colorScheme.outlineVariant, AppShapes.extraSmall),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "?",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * "Como ler esta tela": o glossário inteiro, recolhido.
 *
 * Existe porque o `?` só responde a quem já sabe onde tem dúvida. Quem não
 * conhece o vocabulário precisa de um lugar único para lê-lo de ponta a ponta.
 */
@Composable
internal fun GlossaryPanel(
    expanded: Boolean,
    language: AppLanguage,
    onToggle: () -> Unit
) {
    AppDataSurfaceFlush(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
        header = {
            AppSectionHeader(
                title = CliSessionsLabels.glossaryTitle(language),
                trailing = {
                    Text(
                        text = if (expanded) "▾" else "▸",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )
        }
    ) {
        AppExpandable(expanded) {
            Column {
                // Cada termo é uma linha do painel: título em mono, explicação em sans.
                // O glossário é o único lugar da tela com texto de duas ou três linhas
                // seguidas, e monoespaçada em texto corrido é ~8% mais larga e mais
                // lenta de ler.
                for (term in CliSessionsGlossary.readingOrder) {
                    val entry = CliSessionsGlossary.entry(term, language)
                    AppDataRow(showDivider = term != CliSessionsGlossary.readingOrder.last()) {
                        Column {
                            Text(
                                text = entry.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = entry.explanation,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun CostDistributionBar(analytics: CliSessionAnalytics) {
    val breakdown = analytics.costBreakdown
    val accents = AppAccents.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(12.dp)
            .clip(AppShapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        for ((value, color) in costSegments(analytics, accents)) {
            val weight = breakdown.fractionOf(value).toFloat()
            if (weight <= 0f) {
                continue
            }
            // Segmento chapado: o gradiente vertical dava a cada faixa dois tons
            // da mesma cor, e quatro faixas lado a lado viravam oito.
            Box(
                modifier = Modifier
                    .weight(weight)
                    .fillMaxHeight()
                    .background(color)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CostDistributionLegend(analytics: CliSessionAnalytics, language: AppLanguage) {
    val breakdown = analytics.costBreakdown
    val accents = AppAccents.current
    val labels = listOf(
        CliSessionsLabels.input(language),
        CliSessionsLabels.output(language),
        CliSessionsLabels.cacheRead(language),
        CliSessionsLabels.cacheWrite(language)
    )

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        costSegments(analytics, accents).forEachIndexed { index, (value, color) ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(8.dp).clip(AppShapes.small).background(color))
                Text(
                    text = "${labels[index]} ${formatPercent(breakdown.fractionOf(value))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun costSegments(analytics: CliSessionAnalytics, accents: AppAccents): List<Pair<Long, Color>> {
    val breakdown = analytics.costBreakdown
    return listOf(
        breakdown.inputMicros to accents.input,
        breakdown.outputMicros to accents.output,
        breakdown.cacheReadMicros to accents.cacheRead,
        breakdown.cacheWriteMicros to accents.cacheWrite
    )
}
