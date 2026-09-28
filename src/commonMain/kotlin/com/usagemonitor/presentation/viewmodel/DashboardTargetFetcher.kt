package com.usagemonitor.presentation.viewmodel

import com.usagemonitor.domain.entity.AnthropicProfileRef
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.CodexProfileRef
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.domain.usecase.GetAnthropicUsageUseCase
import com.usagemonitor.domain.usecase.GetAntigravityUsageUseCase
import com.usagemonitor.domain.usecase.GetCodexUsageUseCase
import com.usagemonitor.domain.usecase.GetCursorUsageUseCase
import com.usagemonitor.domain.usecase.GetDeepSeekUsageUseCase
import com.usagemonitor.domain.usecase.GetGeminiUsageUseCase
import com.usagemonitor.domain.usecase.GetKiloUsageUseCase
import com.usagemonitor.domain.usecase.GetMiniMaxUsageUseCase
import com.usagemonitor.domain.usecase.GetOpenCodeGoUsageUseCase
import com.usagemonitor.domain.usecase.GetOpenCodeUsageUseCase
import com.usagemonitor.domain.usecase.GetOpenRouterUsageUseCase
import kotlinx.coroutines.flow.StateFlow

/**
 * Qual caso de uso coleta cada alvo, e quais alvos existem (issue #329).
 *
 * Saiu do [DashboardViewModel] pelo limite de 800 linhas quando o Codex passou a
 * ter contas extras: a resolução de perfil — Anthropic por `profileId`, Codex
 * sem perfil para a conta padrão e com perfil para as demais — é o que mudou, e
 * mora inteira aqui.
 */
internal class DashboardTargetFetcher(
    private val getAnthropicUsage: GetAnthropicUsageUseCase,
    private val getMiniMaxUsage: GetMiniMaxUsageUseCase,
    private val getCodexUsage: GetCodexUsageUseCase,
    private val getDeepSeekUsage: GetDeepSeekUsageUseCase,
    private val getOpenCodeUsage: GetOpenCodeUsageUseCase,
    private val getOpenCodeGoUsage: GetOpenCodeGoUsageUseCase,
    private val getKiloUsage: GetKiloUsageUseCase,
    private val getOpenRouterUsage: GetOpenRouterUsageUseCase,
    private val getGeminiUsage: GetGeminiUsageUseCase,
    private val getCursorUsage: GetCursorUsageUseCase,
    private val getAntigravityUsage: GetAntigravityUsageUseCase,
    private val anthropicProfiles: StateFlow<List<AnthropicProfileRef>>,
    private val codexProfiles: StateFlow<List<CodexProfileRef>>
) {
    fun enabledTargets(enabledSources: Set<ApiSource>): Set<UsageTargetKey> =
        enabledTargetsOf(enabledSources, anthropicProfiles.value, codexProfiles.value)

    suspend fun fetch(target: UsageTargetKey): Result<ApiUsageStats> {
        return when (target.source) {
            ApiSource.ANTHROPIC -> {
                val profile = anthropicProfiles.value.firstOrNull { it.id == target.profileId }
                    ?: return Result.failure(IllegalStateException("Perfil Anthropic não configurado."))
                getAnthropicUsage(profile)
            }
            ApiSource.MINIMAX -> getMiniMaxUsage()
            ApiSource.CODEX -> {
                val profileId = target.profileId ?: return getCodexUsage()
                val profile = codexProfiles.value.firstOrNull { it.id == profileId }
                    ?: return Result.failure(IllegalStateException("Perfil Codex não configurado."))
                getCodexUsage(profile)
            }
            ApiSource.DEEPSEEK -> getDeepSeekUsage()
            ApiSource.OPENCODE -> getOpenCodeUsage()
            ApiSource.OPENCODE_GO -> getOpenCodeGoUsage()
            ApiSource.KILO -> getKiloUsage()
            ApiSource.OPENROUTER -> getOpenRouterUsage()
            ApiSource.GEMINI -> getGeminiUsage()
            ApiSource.CURSOR -> getCursorUsage()
            ApiSource.ANTIGRAVITY -> getAntigravityUsage()
        }
    }
}
