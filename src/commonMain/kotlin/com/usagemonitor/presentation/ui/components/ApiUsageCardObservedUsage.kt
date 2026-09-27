package com.usagemonitor.presentation.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.QuotaInfo
import com.usagemonitor.domain.entity.UsageUnit
import com.usagemonitor.domain.entity.displayName
import com.usagemonitor.presentation.ui.theme.AppSpacing

private const val OBSERVED_ACTIVITY_TRACK_TAG_PREFIX = "observedActivityTrack:"

private const val OBSERVED_ACTIVITY_VALUE_TAG_PREFIX = "observedActivityValue:"

internal fun observedActivityTrackTag(modelName: String, label: String): String =
    "$OBSERVED_ACTIVITY_TRACK_TAG_PREFIX$modelName:$label"

internal fun observedActivityValueTag(modelName: String, label: String): String =
    "$OBSERVED_ACTIVITY_VALUE_TAG_PREFIX$modelName:$label"

@Composable
internal fun ObservedUsageSummary(
    source: ApiSource,
    quotas: List<QuotaInfo>,
    language: AppLanguage,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    val modelSummaries = remember(quotas) { buildObservedUsageSummaries(quotas) }

    if (modelSummaries.isEmpty()) {
        // Superfície neutra com borda, como todo bloco de dado do sistema. O
        // fundo pintado com a cor da fonte era o resto do card colorido que a
        // refatoração tirou do resto do dashboard: aqui ele sobreviveu porque
        // OpenCode e Kilo não entram nas capturas.
        Column(
            modifier = modifier
                .fillMaxWidth()
                .appSurfaceBlock()
                .padding(horizontal = AppSpacing.md, vertical = AppSpacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)
        ) {
            Text(
                text = if (source == ApiSource.GEMINI) {
                    if (language == AppLanguage.PT) "Nenhum token registrado" else "No tokens recorded"
                } else {
                    if (language == AppLanguage.PT) "Nenhum uso free detectado" else "No free usage detected"
                },
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (source == ApiSource.GEMINI) {
                    if (language == AppLanguage.PT) {
                        "O uso aparece quando o Gemini CLI registra sessões locais com contagem de tokens."
                    } else {
                        "Usage appears when Gemini CLI records local sessions with token counts."
                    }
                } else if (language == AppLanguage.PT) {
                    "Abra o ${source.displayName(language)} e use um modelo free para começar a preencher este card."
                } else {
                    "Use a free ${source.displayName(language)} model to start populating this card."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        return
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp)
    ) {
        modelSummaries.forEach { summary ->
            ObservedUsageModelRow(
                source = source,
                summary = summary,
                language = language,
                compact = compact
            )
        }
    }
}

@Composable
private fun ObservedUsageModelRow(
    source: ApiSource,
    summary: ObservedUsageModelSummary,
    language: AppLanguage,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    // No modo compacto a linha não renderiza as barras internas (que já têm
    // tooltip própria), então é seguro dar tooltip à linha inteira.
    if (compact) {
        HoverTooltipBox(
            title = summary.modelName,
            subtitle = if (language == AppLanguage.PT) "Atividade observada" else "Observed activity",
            metrics = buildObservedUsageTooltipMetrics(summary = summary, language = language),
            modifier = modifier
        ) {
            ObservedUsageModelRowContent(
                source = source,
                summary = summary,
                language = language,
                compact = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        return
    }

    ObservedUsageModelRowContent(
        source = source,
        summary = summary,
        language = language,
        compact = false,
        modifier = modifier
    )
}

@Composable
private fun ObservedUsageModelRowContent(
    source: ApiSource,
    summary: ObservedUsageModelSummary,
    language: AppLanguage,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    // A identidade da fonte fica no marcador de 2dp do cabeçalho do card, como em
    // todos os outros. Aqui ela pintava o bloco inteiro, e num card com três
    // modelos eram três retângulos coloridos dentro de um card já identificado.
    Column(
        modifier = modifier
            .fillMaxWidth()
            .appSurfaceBlock()
            .padding(horizontal = AppSpacing.md, vertical = if (compact) AppSpacing.sm else AppSpacing.md),
        verticalArrangement = Arrangement.spacedBy(if (compact) 0.dp else AppSpacing.md)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = summary.modelName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (source == ApiSource.GEMINI) {
                        if (language == AppLanguage.PT) "Métrica local; cota da conta separada" else "Local metric; account quota is separate"
                    } else {
                        if (language == AppLanguage.PT) "Limite oficial indisponível" else "Official limit unavailable"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = localizedObservedCount(summary.amountFiveHours, summary.unit, language),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = observedPrimaryWindowLabel(language),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = observedSecondaryWindowLabel(
                        value = summary.amountSevenDays,
                        source = source,
                        unit = summary.unit,
                        language = language
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (!compact) {
            ObservedUsageInlineComparisonChart(
                source = source,
                summary = summary,
                language = language
            )
        }
    }
}

@Composable
private fun ObservedUsageInlineComparisonChart(
    source: ApiSource,
    summary: ObservedUsageModelSummary,
    language: AppLanguage,
    modifier: Modifier = Modifier
) {
    val maxValue = maxOf(summary.amountFiveHours, summary.amountSevenDays, 1L).toFloat()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = if (language == AppLanguage.PT) {
                "Atividade observada"
            } else {
                "Observed activity"
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        ObservedUsageInlineBar(
            modelName = summary.modelName,
            label = "5h",
            value = summary.amountFiveHours,
            fraction = summary.amountFiveHours / maxValue,
            unit = summary.unit,
            language = language
        )
        ObservedUsageInlineBar(
            modelName = summary.modelName,
            label = "7d",
            value = summary.amountSevenDays,
            fraction = summary.amountSevenDays / maxValue,
            unit = summary.unit,
            language = language
        )
    }
}

@Composable
private fun ObservedUsageInlineBar(
    modelName: String,
    label: String,
    value: Long,
    fraction: Float,
    unit: UsageUnit,
    language: AppLanguage,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        HoverTooltipBox(
            title = modelName,
            subtitle = if (language == AppLanguage.PT) {
                "Atividade observada"
            } else {
                "Observed activity"
            },
            metrics = listOf(
                TooltipMetric(
                    label = if (language == AppLanguage.PT) "Janela" else "Window",
                    value = label
                ),
                TooltipMetric(
                    label = when {
                        unit == UsageUnit.TOKENS -> "Tokens"
                        language == AppLanguage.PT -> "Requisições"
                        else -> "Requests"
                    },
                    value = localizedObservedCount(value, unit, language)
                )
            ),
            modifier = Modifier.weight(1f)
        ) {
            // A barra do sistema: 4dp, com borda e trilha neutra. Esta era a
            // única do app com 8dp e superfície com alpha própria.
            AppProgressTrack(
                fraction = fraction,
                tone = AppTone.INFO,
                modifier = Modifier.testTag(observedActivityTrackTag(modelName, label))
            )
        }

        Text(
            text = compactObservedCount(value, unit),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            softWrap = false,
            // O valor é uma coluna de dados, não texto flexível. Sem um piso
            // ele recebe a largura residual e o Compose quebra cada caractere
            // verticalmente quando o card fica estreito.
            modifier = Modifier
                .widthIn(min = 42.dp)
                .testTag(observedActivityValueTag(modelName, label))
        )
    }
}
