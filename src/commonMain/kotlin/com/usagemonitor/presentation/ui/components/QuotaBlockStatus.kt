package com.usagemonitor.presentation.ui.components

import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.QuotaBlockReason
import com.usagemonitor.presentation.ui.localized

/**
 * Texto e tom do estado de cota bloqueada (#567), compartilhados pelo card, pelo balão da HUD
 * e pela tooltip — um lugar só, para os três mostrarem o mesmo texto.
 */

/** Texto do motivo, PT/EN pelo `localized`. */
internal fun quotaBlockReasonLabel(reason: QuotaBlockReason, language: AppLanguage): String = when (reason) {
    QuotaBlockReason.OUT_OF_CREDITS -> localized(
        language = language,
        pt = "Sem créditos · trabalho extra pausado",
        en = "Out of credits · extra usage paused"
    )
    QuotaBlockReason.SPEND_LIMIT_REACHED -> localized(
        language = language,
        pt = "Limite mensal atingido",
        en = "Monthly limit reached"
    )
}

/** Severidade do estado de cota bloqueada: sem créditos é crítico; limite mensal é aviso. */
internal fun quotaBlockReasonTone(reason: QuotaBlockReason): AppTone = when (reason) {
    QuotaBlockReason.OUT_OF_CREDITS -> AppTone.CRITICAL
    QuotaBlockReason.SPEND_LIMIT_REACHED -> AppTone.WARNING
}

/** Rótulo da linha de motivo na tooltip, no lugar do "Reset". */
internal fun quotaBlockTooltipLabel(language: AppLanguage): String =
    localized(language = language, pt = "Motivo", en = "Reason")
