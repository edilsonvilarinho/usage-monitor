package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.AppUpdateInfo
import com.usagemonitor.domain.repository.AnthropicRepository
import com.usagemonitor.domain.repository.CodexRepository
import com.usagemonitor.domain.repository.DeepSeekRepository
import com.usagemonitor.domain.repository.MiniMaxRepository
import com.usagemonitor.domain.usecase.GetAnthropicUsageUseCase
import com.usagemonitor.domain.usecase.GetCodexUsageUseCase
import com.usagemonitor.domain.usecase.GetDeepSeekUsageUseCase
import com.usagemonitor.domain.usecase.GetMiniMaxUsageUseCase
import com.usagemonitor.presentation.viewmodel.AppUpdateReleaseOpener
import com.usagemonitor.presentation.viewmodel.AppUpdateUiState
import com.usagemonitor.presentation.viewmodel.DashboardToast
import com.usagemonitor.presentation.viewmodel.DashboardViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelUpdateTest : DashboardViewModelTestSupport() {

    @Test
    fun `publishes available update when a newer version exists`() = runTest {
        val viewModel = updateViewModel(
            recordedSnapshots = mutableListOf(),
            checkForUpdate = { Result.success(AppUpdateInfo("7.1.0", "https://example.com/releases/tag/v7.1.0")) },
            config = virtualTimeConfig(testScheduler)
        )
        viewModel.cancelCountdown()

        try {
            runCurrent()

            val updateState = viewModel.appUpdateState.value
            assertIs<AppUpdateUiState.Available>(updateState)
            assertEquals("7.1.0", updateState.update.version)
        } finally {
            viewModel.onDestroy()
        }
    }

    @Test
    fun `rechecks for updates every 10 minutes while running`() = runTest {
        var checks = 0
        val viewModel = updateViewModel(
            recordedSnapshots = mutableListOf(),
            checkForUpdate = {
                checks += 1
                if (checks == 1) {
                    Result.success(null)
                } else {
                    Result.success(AppUpdateInfo("7.1.0", "https://example.com/releases/tag/v7.1.0"))
                }
            },
            config = periodicUpdateConfig(testScheduler)
        )
        viewModel.cancelCountdown()

        try {
            runCurrent()
            assertNull(viewModel.appUpdateState.value)

            advanceTimeBy(10 * 60 * 1_000L)
            runCurrent()

            val updateState = viewModel.appUpdateState.value
            assertIs<AppUpdateUiState.Available>(updateState)
            assertEquals(2, checks)
        } finally {
            viewModel.onDestroy()
        }
    }

    @Test
    fun `refresh triggers an immediate update recheck`() = runTest {
        var checks = 0
        val viewModel = updateViewModel(
            recordedSnapshots = mutableListOf(),
            checkForUpdate = {
                checks += 1
                if (checks == 1) {
                    Result.success(null)
                } else {
                    Result.success(AppUpdateInfo("7.1.0", "https://example.com/releases/tag/v7.1.0"))
                }
            },
            config = virtualTimeConfig(testScheduler)
        )
        viewModel.cancelCountdown()

        try {
            runCurrent()
            assertNull(viewModel.appUpdateState.value)

            viewModel.refresh()
            runCurrent()

            val updateState = viewModel.appUpdateState.value
            assertIs<AppUpdateUiState.Available>(updateState)
            assertEquals(2, checks)
        } finally {
            viewModel.onDestroy()
        }
    }

    @Test
    fun `opens the release page when the update banner action is used`() = runTest {
        var openedUrl: String? = null
        val viewModel = updateViewModel(
            recordedSnapshots = mutableListOf(),
            checkForUpdate = { Result.success(AppUpdateInfo("7.1.0", "https://example.com/releases/tag/v7.1.0")) },
            releaseOpener = object : AppUpdateReleaseOpener {
                override fun open(releasePageUrl: String): Result<Unit> {
                    openedUrl = releasePageUrl
                    return Result.success(Unit)
                }
            },
            config = virtualTimeConfig(testScheduler)
        )
        viewModel.cancelCountdown()

        try {
            runCurrent()

            viewModel.openUpdateReleasePage()

            assertEquals("https://example.com/releases/tag/v7.1.0", openedUrl)
            assertNull(viewModel.toastMessage.value)
        } finally {
            viewModel.onDestroy()
        }
    }

    @Test
    fun `shows toast when opening the release page fails and keeps update banner`() = runTest {
        val viewModel = updateViewModel(
            recordedSnapshots = mutableListOf(),
            checkForUpdate = { Result.success(AppUpdateInfo("7.1.0", "https://example.com/releases/tag/v7.1.0")) },
            releaseOpener = object : AppUpdateReleaseOpener {
                override fun open(releasePageUrl: String): Result<Unit> {
                    return Result.failure(IllegalStateException("browser indisponível"))
                }
            },
            config = virtualTimeConfig(testScheduler)
        )
        viewModel.cancelCountdown()

        try {
            runCurrent()

            viewModel.openUpdateReleasePage()

            assertIs<DashboardToast.ReleasePageError>(viewModel.toastMessage.value)
            assertIs<AppUpdateUiState.Available>(viewModel.appUpdateState.value)
        } finally {
            viewModel.onDestroy()
        }
    }

    private fun updateViewModel(
        recordedSnapshots: MutableList<ApiUsageStats>,
        checkForUpdate: suspend () -> Result<AppUpdateInfo?>,
        releaseOpener: AppUpdateReleaseOpener = object : AppUpdateReleaseOpener {
            override fun open(releasePageUrl: String): Result<Unit> = Result.success(Unit)
        },
        config: com.usagemonitor.presentation.viewmodel.DashboardViewModelConfig
    ): DashboardViewModel {
        val anthropicRepo = object : AnthropicRepository {
            override suspend fun getUsage() = Result.failure<ApiUsageStats>(Exception("Não deve ser chamado"))
        }
        val minimaxRepo = object : MiniMaxRepository {
            override suspend fun getUsage() = Result.failure<ApiUsageStats>(Exception("Não deve ser chamado"))
        }
        val codexRepo = object : CodexRepository {
            override suspend fun getUsage() = Result.failure<ApiUsageStats>(Exception("Não deve ser chamado"))
        }
        val deepSeekRepo = object : DeepSeekRepository {
            override suspend fun getUsage() = Result.failure<ApiUsageStats>(Exception("Não deve ser chamado"))
        }

        return DashboardViewModel(
            GetAnthropicUsageUseCase(anthropicRepo),
            GetMiniMaxUsageUseCase(minimaxRepo),
            GetCodexUsageUseCase(codexRepo),
            GetDeepSeekUsageUseCase(deepSeekRepo),
            MutableStateFlow(emptySet()),
            historyUseCase(recordedSnapshots),
            checkForAppUpdate = updateUseCase { checkForUpdate() },
            appUpdateReleaseOpener = releaseOpener,
            currentAppVersion = "7.0.0",
            clock = Clock.System,
            config = config
        )
    }
}
