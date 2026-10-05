package com.usagemonitor.screenshots

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.domain.entity.UsageUnit
import com.usagemonitor.presentation.ui.HudAccount
import com.usagemonitor.presentation.ui.HudObservedModel
import com.usagemonitor.presentation.ui.components.AppTone

/** Valores sintéticos para conferir unidade, agrupamento e rolagem. */
internal fun observedHudFixture(source: ApiSource): HudAccount {
    val names = when (source) {
        ApiSource.GEMINI -> listOf("Gemini Flash", "Gemini Pro", "Gemini Flash Lite", "Gemini Preview", "Gemini Experimental")
        ApiSource.OPENCODE -> listOf("Big Pickle", "MiniMax M2.5 Free", "Trinity Large Preview Free", "Nemotron 3 Super Free", "Model Preview Free")
        else -> listOf("Kilo Model A", "Kilo Model B", "Kilo Model C", "Kilo Model D", "Kilo Model E")
    }
    val tokens = source == ApiSource.GEMINI
    return HudAccount(
        targetKey = UsageTargetKey.forSource(source),
        label = when (source) {
            ApiSource.OPENCODE -> "OpenCode Zen Free"
            ApiSource.GEMINI -> "Gemini CLI"
            else -> "Kilo Free"
        },
        statusLabel = "Atividade local", tone = AppTone.NEUTRAL, quotas = emptyList(), focusIndex = 0,
        observedModels = names.mapIndexed { index, name -> HudObservedModel(name,
            if (tokens) UsageUnit.TOKENS else UsageUnit.REQUESTS,
            if (tokens) 12_000L + index else when (index) { 0 -> 332L; 1 -> 1L; 3 -> 0L; else -> 18L + index },
            if (tokens) 75_000L + index else when (index) { 0 -> 332L; 1 -> 1L; else -> 120L + index }) }
    )
}
