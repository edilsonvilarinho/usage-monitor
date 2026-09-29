package com.usagemonitor.screenshots

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.domain.entity.displayName
import com.usagemonitor.presentation.ui.HudAccount
import com.usagemonitor.presentation.ui.HudQuota
import com.usagemonitor.presentation.ui.HudSessionSignal
import com.usagemonitor.presentation.ui.HudUsedLeft
import com.usagemonitor.presentation.ui.components.AppTone
import com.usagemonitor.presentation.ui.theme.AccountEmoji

/** Dados sintéticos: cobrem formas de leitura, nunca acessam credenciais ou APIs. */
internal object GargantuaPreviewFixtures {
    val accounts: List<HudAccount> = ApiSource.entries.map { source ->
        val quotas = when (source) {
            ApiSource.ANTHROPIC -> listOf(
                quota("5h", 51, PeriodType.INTERVAL),
                quota("7d", 94, PeriodType.WEEKLY, AppTone.WARNING)
            )
            ApiSource.CODEX -> listOf(quota("5h", 0, PeriodType.INTERVAL), quota("7d", 8, PeriodType.WEEKLY))
            ApiSource.OPENCODE_GO -> listOf(
                quota("5h", 36, PeriodType.INTERVAL),
                quota("7d", 62, PeriodType.WEEKLY),
                quota("30d", 78, PeriodType.MONTHLY)
            )
            ApiSource.CURSOR -> listOf(quota("30d", 67, PeriodType.MONTHLY))
            ApiSource.ANTIGRAVITY -> listOf(
                quota("7d", 100, PeriodType.WEEKLY, AppTone.CRITICAL).copy(group = "Grupo A"),
                quota("7d", 22, PeriodType.WEEKLY).copy(group = "Grupo B")
            )
            ApiSource.DEEPSEEK, ApiSource.OPENROUTER -> listOf(
                HudQuota("Saldo", "\$2.27", 0f, AppTone.NEUTRAL, null, false, periodType = PeriodType.REPORTED)
            )
            ApiSource.GEMINI -> listOf(
                HudQuota("Tokens", "12.4k", 0f, AppTone.NEUTRAL, null, false, periodType = PeriodType.REPORTED)
            )
            ApiSource.OPENCODE, ApiSource.KILO -> listOf(
                HudQuota("Req.", "128", 0f, AppTone.NEUTRAL, null, false, periodType = PeriodType.REPORTED)
            )
            ApiSource.MINIMAX -> listOf(quota("5h", 42, PeriodType.INTERVAL))
        }
        val tone = when (source) {
            ApiSource.ANTHROPIC -> AppTone.WARNING
            ApiSource.ANTIGRAVITY -> AppTone.CRITICAL
            ApiSource.DEEPSEEK, ApiSource.OPENROUTER, ApiSource.GEMINI, ApiSource.OPENCODE, ApiSource.KILO -> AppTone.NEUTRAL
            else -> AppTone.OK
        }
        HudAccount(
            targetKey = UsageTargetKey.forSource(source),
            label = source.displayName(),
            statusLabel = when (tone) {
                AppTone.WARNING -> "Atenção"
                AppTone.CRITICAL -> "Crítico"
                AppTone.NEUTRAL -> "Sem projeção"
                else -> "Normal"
            },
            tone = tone,
            quotas = quotas,
            focusIndex = quotas.indices.maxBy { index -> quotas[index].fraction },
            sessionActive = source == ApiSource.ANTHROPIC,
            accountEmoji = if (source == ApiSource.ANTHROPIC) AccountEmoji.HOUSE else null,
            // O balão da conta completo (F10): o aviso de sessões e a linha de plano.
            sessionSignals = if (source == ApiSource.ANTHROPIC) {
                listOf(HudSessionSignal("Contexto crescendo · 1 sessão", AppTone.WARNING))
            } else {
                emptyList()
            },
            planLabel = if (source == ApiSource.ANTHROPIC) "Pro" else null,
            originLabel = if (source == ApiSource.ANTHROPIC) "via Claude Code" else null
        )
    }

    val showcase: List<HudAccount> = listOf(ApiSource.ANTHROPIC, ApiSource.CODEX, ApiSource.OPENCODE_GO)
        .map { source -> accounts.first { account -> account.source == source } }

    private fun quota(label: String, percent: Int, period: PeriodType, tone: AppTone = AppTone.OK) = HudQuota(
        shortLabel = label,
        percentText = "$percent%",
        fraction = percent / 100f,
        tone = tone,
        resetText = when (period) {
            PeriodType.INTERVAL -> "21h30"
            PeriodType.WEEKLY -> "Ter 1h00"
            else -> "01/10"
        },
        hasForecast = true,
        title = when (period) {
            PeriodType.INTERVAL -> "Sessão 5h"
            PeriodType.WEEKLY -> "Semanal"
            PeriodType.MONTHLY -> "Mensal"
            else -> label
        },
        usedLeft = HudUsedLeft(used = "$percent% usado", left = "${100 - percent}% restante"),
        periodType = period
    )
}
