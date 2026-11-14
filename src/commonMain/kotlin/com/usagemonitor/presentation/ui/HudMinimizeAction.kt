package com.usagemonitor.presentation.ui

import com.usagemonitor.domain.entity.AppLanguage

/** Rótulo do botão de minimizar no balão da conta na HUD (#576): "Minimizar", ou "Restaurar" com a conta minimizada. */
internal fun hudMinimizeActionLabel(minimized: Boolean, language: AppLanguage): String =
    if (minimized) localized(language, "Restaurar", "Restore") else localized(language, "Minimizar", "Minimize")

/** Botão de minimizar/restaurar some na conta auto-minimizada (#596): ela não se restaura à mão. */
internal fun hudShowsMinimizeButton(account: HudAccount): Boolean = !account.autoMinimized
