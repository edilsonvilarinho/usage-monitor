package com.usagemonitor.presentation.ui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.usagemonitor.HUD_BALLOON_SECTION_GAP
import com.usagemonitor.HUD_BALLOON_QUOTA_TITLE
import com.usagemonitor.HUD_OBSERVED_MODEL_HEIGHT
import com.usagemonitor.HUD_OBSERVED_NOTE_HEIGHT
import com.usagemonitor.HUD_OBSERVED_TABLE_HEADER_HEIGHT
import com.usagemonitor.HUD_OBSERVED_UNIT_HEIGHT
import com.usagemonitor.hudObservedViewportHeight
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.UsageUnit
import com.usagemonitor.presentation.ui.components.AppCellValue
import com.usagemonitor.presentation.ui.components.AppColumnHeaderLabel
import com.usagemonitor.presentation.ui.components.AppColumnHeaderRow
import com.usagemonitor.presentation.ui.components.AppDataRow
import com.usagemonitor.presentation.ui.components.AppDivider
import com.usagemonitor.presentation.ui.components.HoverTooltipBox
import com.usagemonitor.presentation.ui.components.TooltipMetric
import com.usagemonitor.presentation.ui.theme.AppSpacing
import java.text.NumberFormat
import java.util.Locale

internal const val HUD_OBSERVED_MODELS_TAG = "hudObservedModels"
internal const val HUD_OBSERVED_TABLE_HEADER_TAG = "hudObservedTableHeader"
private const val MODEL_WEIGHT = 0.42f
private const val COUNT_WEIGHT = 0.29f

/** Colunas e unidade ficam fixas; somente os modelos rolam (direção 02, #379). */
@Composable
internal fun HudObservedBalloonContent(account: HudAccount, language: AppLanguage, maxBodyHeight: Dp) {
    val scroll = rememberScrollState()
    val tokens = account.observedModels.firstOrNull()?.unit == UsageUnit.TOKENS
    val unit = if (tokens) "tokens" else if (language == AppLanguage.PT) "requisições" else "requests"
    Spacer(Modifier.height(HUD_BALLOON_SECTION_GAP))
    Text(
        text = if (language == AppLanguage.PT) {
            if (tokens) "Tokens observados" else "Requisições observadas"
        } else {
            if (tokens) "Observed tokens" else "Observed requests"
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.height(HUD_OBSERVED_UNIT_HEIGHT)
    )
    Spacer(Modifier.height(AppSpacing.sm))
    Column(Modifier.padding(end = AppSpacing.md).height(HUD_OBSERVED_TABLE_HEADER_HEIGHT)) {
        AppColumnHeaderRow(
            modifier = Modifier.weight(1f).testTag(HUD_OBSERVED_TABLE_HEADER_TAG),
            horizontalPadding = 0.dp, startGutter = 0.dp, spacing = AppSpacing.sm
        ) {
            AppColumnHeaderLabel(if (language == AppLanguage.PT) "MODELO" else "MODEL", Modifier.weight(MODEL_WEIGHT))
            AppColumnHeaderLabel(
                if (language == AppLanguage.PT) "ÚLTIMAS\n5H" else "LAST\n5H",
                Modifier.weight(COUNT_WEIGHT), maxLines = 2, textAlign = TextAlign.End
            )
            AppColumnHeaderLabel(
                if (language == AppLanguage.PT) "ÚLTIMOS\n7 DIAS" else "LAST\n7 DAYS",
                Modifier.weight(COUNT_WEIGHT), maxLines = 2, textAlign = TextAlign.End
            )
        }
        AppDivider()
    }
    Box(Modifier.fillMaxWidth().height(hudObservedViewportHeight(account, maxBodyHeight))) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(scroll).padding(end = AppSpacing.md)
                .testTag(HUD_OBSERVED_MODELS_TAG)
        ) {
            account.observedModels.forEachIndexed { index, model ->
                ObservedModelTableRow(model, language, unit, showDivider = index != account.observedModels.lastIndex)
            }
        }
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(scroll),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
        )
    }
    Spacer(Modifier.height(HUD_BALLOON_SECTION_GAP))
    Text(
        text = if (language == AppLanguage.PT) "Contagem local; limite oficial indisponível" else "Local count; official limit unavailable",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        modifier = Modifier.height(HUD_OBSERVED_NOTE_HEIGHT)
    )
}

@Composable
private fun ObservedModelTableRow(model: HudObservedModel, language: AppLanguage, unit: String, showDivider: Boolean) {
    val formatter = remember(language) {
        NumberFormat.getIntegerInstance(if (language == AppLanguage.PT) Locale.forLanguageTag("pt-BR") else Locale.US)
    }
    val fiveHours = formatter.format(model.amountFiveHours)
    val sevenDays = formatter.format(model.amountSevenDays)
    val fiveLabel = if (language == AppLanguage.PT) "Últimas 5h" else "Last 5h"
    val sevenLabel = if (language == AppLanguage.PT) "Últimos 7 dias" else "Last 7 days"
    val singular = if (language == AppLanguage.PT) "requisição" else "request"
    val fiveUnit = if (model.unit == UsageUnit.REQUESTS && model.amountFiveHours == 1L) singular else unit
    val sevenUnit = if (model.unit == UsageUnit.REQUESTS && model.amountSevenDays == 1L) singular else unit
    val description = "${model.modelName}. $fiveLabel: $fiveHours $fiveUnit. $sevenLabel: $sevenDays $sevenUnit."
    HoverTooltipBox(
        title = model.modelName,
        metrics = listOf(TooltipMetric(fiveLabel, "$fiveHours $fiveUnit"), TooltipMetric(sevenLabel, "$sevenDays $sevenUnit"))
    ) {
        AppDataRow(
            modifier = Modifier.height(HUD_OBSERVED_MODEL_HEIGHT).testTag("hudObservedRow:${model.modelName}")
                .semantics(mergeDescendants = true) { contentDescription = description },
            horizontalPadding = 0.dp, verticalPadding = AppSpacing.sm, showDivider = showDivider
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().height(HUD_BALLOON_QUOTA_TITLE * 2),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    model.modelName,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(MODEL_WEIGHT).testTag("hudObservedName:${model.modelName}")
                )
                AppCellValue(fiveHours, Modifier.weight(COUNT_WEIGHT).testTag("hudObservedFiveHours:${model.modelName}"),
                    style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End)
                AppCellValue(sevenDays, Modifier.weight(COUNT_WEIGHT).testTag("hudObservedSevenDays:${model.modelName}"),
                    style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End)
            }
        }
    }
}
