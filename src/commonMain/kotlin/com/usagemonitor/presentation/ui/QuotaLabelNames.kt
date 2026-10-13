package com.usagemonitor.presentation.ui

import com.usagemonitor.domain.entity.AnthropicQuotaLabels
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.DeepSeekQuotaLabels
import com.usagemonitor.domain.entity.OpenCodeGoQuotaLabels
import com.usagemonitor.domain.entity.OpenRouterQuotaLabels

/**
 * O rótulo de uma cota para mostrar no idioma do app (análise da sessão, B03). Algumas chaves
 * de série nasceram em português ("Saldo", "Créditos", "Go semanal") e **não podem ser
 * renomeadas** — são a chave do histórico. A tradução é só na exibição; rótulo neutro
 * ("Claude 5h", "Codex limit …") sai como veio.
 */
internal fun displayQuotaLabel(label: String, language: AppLanguage): String =
    if (language == AppLanguage.PT) label else ENGLISH_QUOTA_LABELS[label] ?: label

internal fun displayQuotaLabel(label: String, pt: Boolean): String =
    displayQuotaLabel(label, if (pt) AppLanguage.PT else AppLanguage.EN)

private val ENGLISH_QUOTA_LABELS: Map<String, String> = mapOf(
    AnthropicQuotaLabels.EXTRA_CREDITS to "Credits",
    DeepSeekQuotaLabels.BALANCE to "Balance",
    OpenRouterQuotaLabels.BALANCE to "Balance",
    DeepSeekQuotaLabels.GRANTED to "Granted",
    OpenCodeGoQuotaLabels.WEEKLY to "Go weekly",
    OpenCodeGoQuotaLabels.MONTHLY to "Go monthly"
)
