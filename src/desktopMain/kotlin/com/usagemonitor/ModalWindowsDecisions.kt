package com.usagemonitor

import com.usagemonitor.domain.entity.AccountCreditUsage
import com.usagemonitor.domain.entity.AnthropicQuotaLabels
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.CliQuotaWindows
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.displayName
import com.usagemonitor.presentation.viewmodel.UiState
import kotlin.time.Instant

/*
 * O que as janelas modais recebem do painel, fora de `ModalWindowsHost.kt`. Aquele
 * arquivo é cola de janela (abre cada `AppDialogWindow` e liga o view model a ela) e
 * sai do Kover por nome (onda 6.5 do plano `cobertura-95-execucao.md`); o que decide
 * alguma coisa mora aqui e segue contado e testado.
 */

/**
 * Reset da quota de 5h da conta aberta na tela de Sessões CLI.
 *
 * Devolve janelas vazias enquanto a conta não tiver coleta bem-sucedida — o
 * filtro então cai para a janela corrida em vez de esvaziar a lista.
 */
internal fun quotaWindowsForProfile(state: UiState, profileId: String?): CliQuotaWindows {
    if (profileId == null || state !is UiState.Success) {
        return CliQuotaWindows()
    }

    val stats = state.data.firstOrNull { item ->
        item.source == ApiSource.ANTHROPIC && item.targetKey.profileId == profileId
    } ?: return CliQuotaWindows()

    return CliQuotaWindows(fiveHourEndsAt = stats.quotaEndAt(PeriodType.INTERVAL))
}

/**
 * Créditos de uso da conta, na moeda **real** dela.
 *
 * `null` quando o recurso está desligado — `AnthropicMapper` só cria a terceira
 * cota com `is_enabled` verdadeiro, então a ausência aqui significa exatamente
 * isso e não uma falha de leitura.
 */
internal fun accountCreditsForProfile(state: UiState, profileId: String?): AccountCreditUsage? {
    if (profileId == null || state !is UiState.Success) {
        return null
    }

    val stats = state.data.firstOrNull { item ->
        item.source == ApiSource.ANTHROPIC && item.targetKey.profileId == profileId
    } ?: return null

    val quota = stats.quotas.firstOrNull { item ->
        item.label == AnthropicQuotaLabels.EXTRA_CREDITS
    } ?: return null

    return AccountCreditUsage(
        usedMinorUnits = quota.rawUsed.takeIf { value -> value > 0L } ?: quota.used,
        limitMinorUnits = quota.rawTotal.takeIf { value -> value > 0L } ?: quota.total,
        currencyCode = quota.currencyCode
    )
}

internal fun ApiUsageStats.quotaEndAt(periodType: PeriodType): Instant? {
    return quotas
        .firstOrNull { quota -> quota.periodType == periodType && quota.hasKnownResetAt }
        ?.periodEndAt
}

internal fun historyWindowTitle(source: ApiSource, language: AppLanguage): String {
    val sourceName = source.displayName()

    return if (language == AppLanguage.PT) {
        "Histórico - $sourceName"
    } else {
        "History - $sourceName"
    }
}
