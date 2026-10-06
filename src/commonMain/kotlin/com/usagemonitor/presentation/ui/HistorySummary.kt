package com.usagemonitor.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.ui.components.AppButton
import com.usagemonitor.presentation.ui.components.AppButtonTone
import com.usagemonitor.presentation.ui.components.AppExpandable
import com.usagemonitor.presentation.ui.components.AppMetricBlock
import com.usagemonitor.presentation.ui.components.AppDataSurface
import com.usagemonitor.presentation.ui.theme.AppSpacing

/** Resumo curto; texto completo permanece no bloco de detalhes. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HistorySummary(entries: List<HistoryMetricEntry>, tag: String) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 600.dp) {
            AppDataSurface(contentPadding = AppSpacing.sm, modifier = Modifier.testTag("historySummary:$tag")
                .clearAndSetSemantics { contentDescription = entries.take(3).joinToString("; ") { "${it.label}: ${it.value}" } }) {
                HistoryMetricTable(entries.take(3))
            }
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(AppSpacing.md), verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                entries.take(3).forEachIndexed { index, entry ->
                    AppMetricBlock(entry.label, entry.value, modifier = Modifier.widthIn(min = 148.dp, max = 240.dp)
                        .testTag("historySummary:$tag:$index").clearAndSetSemantics { contentDescription = "${entry.label}: ${entry.value}" })
                }
            }
        }
    }
}

@Composable
internal fun HistoryDetailsSection(title: String, tag: String, expanded: Boolean, onToggle: () -> Unit, language: AppLanguage, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
        AppButton(
            label = "${if (expanded) "▾" else "▸"} $title",
            onClick = onToggle,
            tone = AppButtonTone.GHOST,
            modifier = Modifier.testTag(tag).semantics {
                stateDescription = if (language == AppLanguage.PT) { if (expanded) "Expandido" else "Recolhido" } else { if (expanded) "Expanded" else "Collapsed" }
            }
        )
        AppExpandable(expanded = expanded) { content() }
    }
}
