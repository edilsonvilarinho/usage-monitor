package com.usagemonitor.presentation.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.*
import com.usagemonitor.presentation.ui.components.*
import com.usagemonitor.presentation.ui.theme.AppSpacing
import com.usagemonitor.presentation.viewmodel.HistoryQuotaView
import com.usagemonitor.presentation.viewmodel.HistoryUiState
import com.usagemonitor.presentation.viewmodel.HistoryViewModel

const val HISTORY_EXPORT_PDF_TAG = "historyExportPdf"
const val HISTORY_CONTROLS_TAG = "historyControls"
const val HISTORY_ACCOUNT_MENU_TAG = "historyAccountMenu"
const val HISTORY_RANGE_MENU_TAG = "historyRangeMenu"
internal enum class HistoryFilterMenu { SOURCE, ACCOUNT, RANGE }

private data class HistoryFilterValues(val sources: List<ApiSource>, val source: ApiSource?, val range: HistoryRange, val accounts: List<UsageAccountContext>, val account: UsageAccountContext?)

/** Conta é seleção sob demanda, nunca uma lista que aumenta o cabeçalho. */
@Composable
internal fun HistoryFilters(
    state: HistoryUiState, viewModel: HistoryViewModel, language: AppLanguage, showSourceSelector: Boolean,
    expandedMenu: HistoryFilterMenu?, onMenuChange: (HistoryFilterMenu?) -> Unit
) {
    val filters = when (state) {
        is HistoryUiState.Success -> HistoryFilterValues(state.availableSources, state.selectedSource, state.selectedRange, state.availableAccounts, state.selectedAccount)
        is HistoryUiState.Empty -> HistoryFilterValues(state.availableSources, state.selectedSource, state.selectedRange, state.availableAccounts, state.selectedAccount)
        is HistoryUiState.Error -> HistoryFilterValues(state.availableSources, state.selectedSource, state.selectedRange, state.availableAccounts, state.selectedAccount)
        HistoryUiState.Loading -> null
    }
    val success = state as? HistoryUiState.Success
    val pt = language == AppLanguage.PT
    val disabledReason = when {
        success == null || success.report.isEmpty -> if (pt) "PDF indisponível: não há dados carregados." else "PDF unavailable: no loaded data."
        success.isRefreshing -> if (pt) "Aguarde a leitura do novo intervalo." else "Wait for the new range to load."
        success.isExporting -> if (pt) "Exportando relatório…" else "Exporting report…"
        !viewModel.canWriteReport -> if (pt) "Exportação não configurada neste ambiente." else "Export is not configured in this environment."
        else -> null
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val menuWidth = maxWidth
        val menuHeight = (maxHeight - AppSpacing.lg).coerceAtLeast(28.dp)
        val narrow = maxWidth < 600.dp
        val showApi = showSourceSelector && filters != null && filters.sources.size > 1
        val singleRow = maxWidth >= 720.dp && !showApi
        val quotaLabels = success?.report?.let { quotaViewLabels(it, language) }
        val account: @Composable (Modifier) -> Unit = { modifier ->
            if (filters?.source?.requiresUsageAccount == true) HistoryFilterDropdown(
                label = if (pt) "Conta" else "Account", value = filters.account?.displayLabel ?: if (pt) "Nenhuma conta" else "No account",
                options = filters.accounts.map { AppMenuOption(it.displayLabel, historyAccountChipTag(it)) },
                selectedIndex = filters.accounts.indexOfFirst { it.key == filters.account?.key },
                menu = HistoryFilterMenu.ACCOUNT, expandedMenu = expandedMenu, onMenuChange = onMenuChange,
                onSelect = { viewModel.selectAccount(filters.accounts[it]) }, modifier = modifier,
                tag = HISTORY_ACCOUNT_MENU_TAG, menuWidth = menuWidth, menuHeight = menuHeight
            )
        }
        val pdf: @Composable () -> Unit = {
            AppButton("PDF", { viewModel.exportReport(language) }, enabled = disabledReason == null,
                modifier = Modifier.testTag(HISTORY_EXPORT_PDF_TAG).semantics { contentDescription = ExportLabels.exportPdf(language) })
        }
        val quota: @Composable () -> Unit = {
            if (quotaLabels != null) AppSegmentedControl(
                options = HistoryQuotaView.entries.mapIndexed { index, view -> AppSegment(quotaLabels[index], historyQuotaViewChipTag(view)) },
                selectedIndex = success!!.selectedQuotaView.ordinal, onSelect = { viewModel.selectQuotaView(HistoryQuotaView.entries[it]) },
                modifier = Modifier.semantics { contentDescription = if (pt) "Cota" else "Quota" }
            )
        }
        AppDataSurface(modifier = Modifier.testTag(HISTORY_CONTROLS_TAG), contentPadding = AppSpacing.sm) {
            if (filters == null) AppToolbar { pdf() }
            else if (singleRow) AppToolbar(spacing = AppSpacing.sm) {
                account(Modifier.weight(1f))
                HistoryRangeSegments(filters.range, language, viewModel::selectRange)
                quota()
                pdf()
            } else {
                if (showApi) HistoryFilterDropdown(
                    label = "API", value = filters.source?.let { sourceLabel(it) } ?: "—",
                    options = filters.sources.map { AppMenuOption(sourceLabel(it), historySourceChipTag(it)) },
                    selectedIndex = filters.sources.indexOf(filters.source), menu = HistoryFilterMenu.SOURCE,
                    expandedMenu = expandedMenu, onMenuChange = onMenuChange, onSelect = { viewModel.selectSource(filters.sources[it]) },
                    modifier = Modifier.fillMaxWidth(), tag = "historySourceMenu", menuWidth = menuWidth, menuHeight = menuHeight
                )
                if (filters.source?.requiresUsageAccount == true || !narrow) AppToolbar(spacing = AppSpacing.sm) {
                    account(Modifier.weight(1f))
                    if (!narrow) pdf()
                }
                AppToolbar(spacing = AppSpacing.sm) {
                    if (narrow) {
                        HistoryFilterDropdown(
                            label = if (pt) "Intervalo" else "Range", value = rangeLabel(filters.range, language),
                            options = HistoryRange.entries.map { AppMenuOption(rangeLabel(it, language), historyRangeChipTag(it)) },
                            selectedIndex = filters.range.ordinal, menu = HistoryFilterMenu.RANGE,
                            expandedMenu = expandedMenu, onMenuChange = onMenuChange, onSelect = { viewModel.selectRange(HistoryRange.entries[it]) },
                            modifier = Modifier.weight(1f), tag = HISTORY_RANGE_MENU_TAG, menuWidth = menuWidth, menuHeight = menuHeight
                        )
                        pdf()
                    } else {
                        HistoryRangeSegments(filters.range, language, viewModel::selectRange)
                        quota()
                    }
                }
                if (narrow && quotaLabels != null) AppToolbar { quota() }
            }
            if (disabledReason != null) Text(disabledReason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HistoryRangeSegments(range: HistoryRange, language: AppLanguage, onSelect: (HistoryRange) -> Unit) {
    AppSegmentedControl(
        options = HistoryRange.entries.map { AppSegment(rangeLabel(it, language), historyRangeChipTag(it)) },
        selectedIndex = range.ordinal, onSelect = { onSelect(HistoryRange.entries[it]) },
        modifier = Modifier.semantics { contentDescription = if (language == AppLanguage.PT) "Intervalo" else "Range" }
    )
}

@Composable
private fun HistoryFilterDropdown(
    label: String, value: String, options: List<AppMenuOption>, selectedIndex: Int,
    menu: HistoryFilterMenu, expandedMenu: HistoryFilterMenu?, onMenuChange: (HistoryFilterMenu?) -> Unit,
    onSelect: (Int) -> Unit, modifier: Modifier, tag: String, menuWidth: Dp, menuHeight: Dp
) {
    val expanded = expandedMenu == menu
    AppMenu(
        expanded = expanded, options = options, selectedIndex = selectedIndex,
        onSelect = { onMenuChange(null); onSelect(it) }, onDismissRequest = { onMenuChange(null) },
        modifier = modifier, maxWidth = menuWidth, maxHeight = menuHeight, wrapLabels = true
    ) {
        AppButton("$label: $value ▾", { onMenuChange(if (expanded) null else menu) }, enabled = options.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().testTag(tag).semantics { contentDescription = "$label: $value"; stateDescription = if (expanded) "Expanded" else "Collapsed" })
    }
}
