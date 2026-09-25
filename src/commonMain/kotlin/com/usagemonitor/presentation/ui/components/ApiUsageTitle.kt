package com.usagemonitor.presentation.ui.components

import com.usagemonitor.domain.entity.ApiUsageStats

/**
 * O título de uma fonte: o fornecedor e, quando há, o perfil — "Anthropic —
 * Padrão", "Codex".
 *
 * Dono único, usado pelo card e pela HUD. A HUD mostrava só o perfil
 * ("Padrão") e escondia de quem era a conta; com duas montagens do mesmo título
 * uma delas sempre acaba perdendo o fornecedor.
 */
fun ApiUsageStats.displayTitle(): String {
    return profileLabel?.let { label -> "$apiName — $label" } ?: apiName
}
