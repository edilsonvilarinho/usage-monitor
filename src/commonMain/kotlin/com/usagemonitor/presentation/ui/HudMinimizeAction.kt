package com.usagemonitor.presentation.ui

import com.usagemonitor.domain.entity.AppLanguage

/** Rótulo do botão de minimizar no balão da conta na HUD (#576): "Minimizar", ou "Restaurar" com a conta minimizada. */
internal fun hudMinimizeActionLabel(minimized: Boolean, language: AppLanguage): String =
    if (minimized) localized(language, "Restaurar", "Restore") else localized(language, "Minimizar", "Minimize")
