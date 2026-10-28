package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.AnthropicProfileRef
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.CodexProfileRef
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.domain.repository.AnthropicRepository
import com.usagemonitor.domain.repository.AntigravityRepository
import com.usagemonitor.domain.repository.CodexRepository
import com.usagemonitor.domain.repository.CursorRepository
import com.usagemonitor.domain.repository.DeepSeekRepository
import com.usagemonitor.domain.repository.GeminiRepository
import com.usagemonitor.domain.repository.KiloRepository
import com.usagemonitor.domain.repository.MiniMaxRepository
import com.usagemonitor.domain.repository.OpenCodeGoRepository
import com.usagemonitor.domain.repository.OpenCodeRepository
import com.usagemonitor.domain.repository.OpenRouterRepository
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
import com.usagemonitor.presentation.viewmodel.DashboardTargetFetcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Qual caso de uso coleta cada alvo, e o que acontece com o perfil que sumiu (#329). */
class DashboardTargetFetcherTest {

    private val calls = mutableListOf<String>()

    private fun stats(source: ApiSource, name: String) = Result.success(ApiUsageStats(source = source, apiName = name, quotas = emptyList()))

    private fun answer(source: ApiSource, name: String = source.name): Result<ApiUsageStats> {
        calls += name
        return stats(source, name)
    }

    private val fetcher = DashboardTargetFetcher(
        getAnthropicUsage = GetAnthropicUsageUseCase(object : AnthropicRepository {
            override suspend fun getUsage() = answer(ApiSource.ANTHROPIC)
            override suspend fun getUsage(profile: AnthropicProfileRef) = answer(ApiSource.ANTHROPIC, "ANTHROPIC:${profile.id}")
        }),
        getMiniMaxUsage = GetMiniMaxUsageUseCase(object : MiniMaxRepository {
            override suspend fun getUsage() = answer(ApiSource.MINIMAX)
        }),
        getCodexUsage = GetCodexUsageUseCase(object : CodexRepository {
            override suspend fun getUsage() = answer(ApiSource.CODEX)
            override suspend fun getUsage(profile: CodexProfileRef) = answer(ApiSource.CODEX, "CODEX:${profile.id}")
        }),
        getDeepSeekUsage = GetDeepSeekUsageUseCase(object : DeepSeekRepository {
            override suspend fun getUsage() = answer(ApiSource.DEEPSEEK)
        }),
        getOpenCodeUsage = GetOpenCodeUsageUseCase(object : OpenCodeRepository {
            override suspend fun getUsage() = answer(ApiSource.OPENCODE)
        }),
        getOpenCodeGoUsage = GetOpenCodeGoUsageUseCase(object : OpenCodeGoRepository {
            override suspend fun getUsage() = answer(ApiSource.OPENCODE_GO)
        }),
        getKiloUsage = GetKiloUsageUseCase(object : KiloRepository {
            override suspend fun getUsage() = answer(ApiSource.KILO)
        }),
        getOpenRouterUsage = GetOpenRouterUsageUseCase(object : OpenRouterRepository {
            override suspend fun getUsage() = answer(ApiSource.OPENROUTER)
        }),
        getGeminiUsage = GetGeminiUsageUseCase(object : GeminiRepository {
            override suspend fun getUsage() = answer(ApiSource.GEMINI)
        }),
        getCursorUsage = GetCursorUsageUseCase(object : CursorRepository {
            override suspend fun getUsage() = answer(ApiSource.CURSOR)
        }),
        getAntigravityUsage = GetAntigravityUsageUseCase(object : AntigravityRepository {
            override suspend fun getUsage() = answer(ApiSource.ANTIGRAVITY)
        }),
        anthropicProfiles = MutableStateFlow(listOf(AnthropicProfileRef("work", "Trabalho"))),
        codexProfiles = MutableStateFlow(listOf(CodexProfileRef("codex-extra", "Extra")))
    )

    @Test
    fun `every source without profiles goes to its own use case`() = runTest {
        val plain = ApiSource.entries - setOf(ApiSource.ANTHROPIC)

        val names = plain.map { source -> fetcher.fetch(UsageTargetKey.forSource(source)).getOrThrow().apiName }

        assertEquals(plain.map { source -> source.name }, names)
        assertEquals(names, calls)
    }

    @Test
    fun `anthropic and extra codex accounts are fetched with their own profile`() = runTest {
        assertEquals("ANTHROPIC:work", fetcher.fetch(UsageTargetKey(ApiSource.ANTHROPIC, "work")).getOrThrow().apiName)
        assertEquals("CODEX:codex-extra", fetcher.fetch(UsageTargetKey(ApiSource.CODEX, "codex-extra")).getOrThrow().apiName)
    }

    @Test
    fun `a profile that is no longer configured fails without calling anyone`() = runTest {
        val anthropic = fetcher.fetch(UsageTargetKey(ApiSource.ANTHROPIC, "removido"))
        val codex = fetcher.fetch(UsageTargetKey(ApiSource.CODEX, "codex-removido"))

        assertEquals("Perfil Anthropic não configurado.", anthropic.exceptionOrNull()?.message)
        assertEquals("Perfil Codex não configurado.", codex.exceptionOrNull()?.message)
        assertEquals(emptyList<String>(), calls)
    }
}
