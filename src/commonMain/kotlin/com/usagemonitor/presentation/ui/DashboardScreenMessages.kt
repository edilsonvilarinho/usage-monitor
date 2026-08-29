package com.usagemonitor.presentation.ui

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.viewmodel.DashboardToast

internal fun decodeToastMessage(toast: DashboardToast, language: AppLanguage): String {
    return when (toast) {
        is DashboardToast.RateLimit -> {
            val sourceLabel = sourceLabelFromKey(toast.source)
            if (language == AppLanguage.PT) {
                "$sourceLabel limitado temporariamente - aguardando próxima atualização..."
            } else {
                "$sourceLabel temporarily limited - waiting for the next refresh..."
            }
        }

        is DashboardToast.ServiceUnavailable -> {
            val sourceLabel = sourceLabelFromKey(toast.source)
            if (language == AppLanguage.PT) {
                "$sourceLabel temporariamente indisponível - tente novamente em instantes."
            } else {
                "$sourceLabel is temporarily unavailable - retry in a few moments."
            }
        }

        is DashboardToast.ApiError -> {
            val sourceLabel = sourceLabelFromKey(toast.source)
            "$sourceLabel: ${toast.message}"
        }

        is DashboardToast.ReleasePageError -> {
            if (language == AppLanguage.PT) {
                "Não foi possível abrir a página da release. ${toast.message}"
            } else {
                "Could not open the release page. ${toast.message}"
            }
        }
    }
}

internal fun sourceLabelFromKey(source: ApiSource): String {
    return when (source) {
        ApiSource.ANTHROPIC -> "Anthropic"
        ApiSource.MINIMAX -> "MiniMax"
        ApiSource.CODEX -> "Codex"
        ApiSource.DEEPSEEK -> "DeepSeek"
        ApiSource.OPENCODE -> "OpenCode Zen Free"
        ApiSource.OPENCODE_GO -> "OpenCode Go"
        ApiSource.KILO -> "Kilo Free"
    }
}
