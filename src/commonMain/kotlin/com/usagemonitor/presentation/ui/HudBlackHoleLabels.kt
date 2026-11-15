package com.usagemonitor.presentation.ui

import com.usagemonitor.domain.entity.AppLanguage

/** Contagem das contas que estão no buraco negro da HUD (issue #600). */
fun hudBlackHoleCountLabel(count: Int, language: AppLanguage): String =
    if (count == 1) {
        localized(language, "1 minimizada", "1 minimized")
    } else {
        localized(language, "$count minimizadas", "$count minimized")
    }
