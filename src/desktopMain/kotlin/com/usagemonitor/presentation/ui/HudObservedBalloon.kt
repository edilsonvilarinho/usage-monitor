package com.usagemonitor.presentation.ui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.usagemonitor.HUD_BALLOON_QUOTA_TITLE
import com.usagemonitor.HUD_BALLOON_SECTION_GAP
import com.usagemonitor.HUD_OBSERVED_NOTE_HEIGHT
import com.usagemonitor.HUD_OBSERVED_MODEL_HEIGHT
import com.usagemonitor.HUD_WORD_LINE
import com.usagemonitor.hudObservedViewportHeight
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.ui.components.AppDataRow
import com.usagemonitor.presentation.ui.components.localizedObservedCount
import com.usagemonitor.presentation.ui.theme.AppSpacing

internal const val HUD_OBSERVED_MODELS_TAG = "hudObservedModels"

/** Somente o trecho de modelos rola; cabeçalho, nota e ações ficam fixos. */
@Composable
internal fun HudObservedBalloonContent(account: HudAccount, language: AppLanguage, maxBodyHeight: Dp) {
    val scroll = rememberScrollState()
    Spacer(Modifier.height(HUD_BALLOON_SECTION_GAP))
    Box(Modifier.fillMaxWidth().height(hudObservedViewportHeight(account, maxBodyHeight))) {
        Column(Modifier.fillMaxWidth().verticalScroll(scroll).testTag(HUD_OBSERVED_MODELS_TAG)) {
            account.observedModels.forEachIndexed { index, model ->
                AppDataRow(
                    modifier = Modifier.height(HUD_OBSERVED_MODEL_HEIGHT),
                    horizontalPadding = 0.dp,
                    verticalPadding = AppSpacing.sm,
                    showDivider = index != account.observedModels.lastIndex
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        Text(
                            model.modelName,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.height(HUD_BALLOON_QUOTA_TITLE)
                        )
                        ObservedWindowCount(model, language, fiveHours = true)
                        ObservedWindowCount(model, language, fiveHours = false)
                    }
                }
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
private fun ObservedWindowCount(model: HudObservedModel, language: AppLanguage, fiveHours: Boolean) {
    val window = if (fiveHours) {
        if (language == AppLanguage.PT) "Últimas 5h" else "Last 5h"
    } else {
        if (language == AppLanguage.PT) "Últimos 7 dias" else "Last 7 days"
    }
    val amount = if (fiveHours) model.amountFiveHours else model.amountSevenDays
    Text(
        text = "$window — ${localizedObservedCount(amount, model.unit, language)}",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.height(HUD_WORD_LINE)
    )
}
