package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.AnthropicProfileRef
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.CodexProfileRef
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.domain.repository.AnthropicRepository
import com.usagemonitor.domain.repository.CodexRepository
import com.usagemonitor.domain.repository.DeepSeekRepository
import com.usagemonitor.domain.repository.MiniMaxRepository
import com.usagemonitor.domain.usecase.GetAnthropicUsageUseCase
import com.usagemonitor.domain.usecase.GetCodexUsageUseCase
import com.usagemonitor.domain.usecase.GetDeepSeekUsageUseCase
import com.usagemonitor.domain.usecase.GetMiniMaxUsageUseCase
import com.usagemonitor.presentation.viewmodel.DashboardViewModel
import com.usagemonitor.presentation.viewmodel.UiState
import com.usagemonitor.presentation.viewmodel.enabledTargetsOf
import com.usagemonitor.presentation.viewmodel.uiApiErrorOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Contas Codex extras (issue #329): um card por conta, a padrão continua sem perfil. */
class DashboardViewModelCodexProfilesTest : DashboardViewModelTestSupport() {

    private val work = CodexProfileRef("codex-work", "trabalho")

    @Test
    fun `extra Codex accounts come right after the default one`() {
        val targets = enabledTargetsOf(
            setOf(ApiSource.MINIMAX, ApiSource.CODEX),
            listOf(AnthropicProfileRef.DEFAULT),
            listOf(work)
        )

        assertEquals(
            listOf(
                UsageTargetKey(ApiSource.MINIMAX),
                UsageTargetKey(ApiSource.CODEX),
                UsageTargetKey(ApiSource.CODEX, "codex-work")
            ),
            targets.toList()
        )
    }

    @Test
    fun `a failing extra account names itself in the banner`() {
        val error = uiApiErrorOf(
            UsageTargetKey(ApiSource.CODEX, "codex-work"),
            IllegalStateException("401"),
            profiles = emptyList(),
            codexProfiles = listOf(work)
        )

        assertEquals("Codex — trabalho", error.targetLabel)
    }

    @Test
    fun `each Codex account becomes its own card`() = runTest {
        val codexRepo = object : CodexRepository {
            override suspend fun getUsage() = Result.success(sampleCodexStats)
            override suspend fun getUsage(profile: CodexProfileRef) = Result.success(
                sampleCodexStats.copy(targetKey = UsageTargetKey(ApiSource.CODEX, profile.id), profileLabel = profile.label)
            )
        }
        val unused = Result.failure<ApiUsageStats>(Exception("Não deve ser chamado"))
        val viewModel = DashboardViewModel(
            GetAnthropicUsageUseCase(object : AnthropicRepository { override suspend fun getUsage() = unused }),
            GetMiniMaxUsageUseCase(object : MiniMaxRepository { override suspend fun getUsage() = unused }),
            GetCodexUsageUseCase(codexRepo),
            GetDeepSeekUsageUseCase(object : DeepSeekRepository { override suspend fun getUsage() = unused }),
            MutableStateFlow(setOf(ApiSource.CODEX)),
            historyUseCase(mutableListOf()),
            codexProfiles = MutableStateFlow(listOf(work)),
            config = manualRefreshConfig()
        )

        viewModel.refresh()
        val state = assertIs<UiState.Success>(awaitSettledState(viewModel))

        assertEquals(
            listOf(UsageTargetKey(ApiSource.CODEX), UsageTargetKey(ApiSource.CODEX, "codex-work")),
            state.data.map { stats -> stats.targetKey }
        )
        assertEquals(listOf(null, "trabalho"), state.data.map { stats -> stats.profileLabel })
        viewModel.onDestroy()
    }
}
