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
import com.usagemonitor.presentation.viewmodel.AppUpdateInstaller
import com.usagemonitor.presentation.viewmodel.AppUpdateUiState
import com.usagemonitor.presentation.viewmodel.AutomaticUpdateStage
import com.usagemonitor.presentation.viewmodel.DashboardViewModel
import com.usagemonitor.presentation.viewmodel.PreparedUpdateAction
import com.usagemonitor.presentation.viewmodel.UnsupportedAppUpdateInstaller
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelUpdateTest : DashboardViewModelTestSupport() {

    @Test
    fun `publishes available update when a newer version exists without automatic installer`() = runTest {
        val recordedSnapshots = mutableListOf<ApiUsageStats>()
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

        val viewModel = DashboardViewModel(
            GetAnthropicUsageUseCase(anthropicRepo),
            GetMiniMaxUsageUseCase(minimaxRepo),
            GetCodexUsageUseCase(codexRepo),
            GetDeepSeekUsageUseCase(deepSeekRepo),
            MutableStateFlow(emptySet()),
            historyUseCase(recordedSnapshots),
            checkForAppUpdate = updateUseCase(
                Result.success(
                    AppUpdateInfo(
                        version = "7.1.0",
                        releasePageUrl = "https://example.com/releases/tag/v7.1.0"
                    )
                )
            ),
            appUpdateInstaller = UnsupportedAppUpdateInstaller,
            currentAppVersion = "7.0.0",
            clock = Clock.System,
            config = virtualTimeConfig(testScheduler)
        )
        viewModel.cancelCountdown()

        try {
            runCurrent()

            val updateState = viewModel.appUpdateState.value
            assertIs<AppUpdateUiState.Available>(updateState)
            assertEquals("7.1.0", updateState.update.version)
            assertEquals(false, updateState.automaticInstallSupported)
            assertEquals(false, viewModel.shouldExitForUpdate.value)
        } finally {
            viewModel.onDestroy()
        }
    }

    @Test
    fun `prepares automatic update and requests app exit`() = runTest {
        val recordedSnapshots = mutableListOf<ApiUsageStats>()
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
        var preparedVersion: String? = null
        val installer = object : AppUpdateInstaller {
            override val isSupported: Boolean = true

            override fun canInstall(update: AppUpdateInfo): Boolean {
                return update.windowsInstallerDownloadUrl != null || update.linuxDebInstallerDownloadUrl != null
            }

            override suspend fun prepareUpdateInstallation(
                update: AppUpdateInfo,
                onStageChanged: (AutomaticUpdateStage) -> Unit
            ): Result<PreparedUpdateAction> {
                preparedVersion = update.version
                return Result.success(PreparedUpdateAction.ExitAndInstall)
            }
        }

        val viewModel = DashboardViewModel(
            GetAnthropicUsageUseCase(anthropicRepo),
            GetMiniMaxUsageUseCase(minimaxRepo),
            GetCodexUsageUseCase(codexRepo),
            GetDeepSeekUsageUseCase(deepSeekRepo),
            MutableStateFlow(emptySet()),
            historyUseCase(recordedSnapshots),
            checkForAppUpdate = updateUseCase(
                Result.success(
                    AppUpdateInfo(
                        version = "7.1.0",
                        releasePageUrl = "https://example.com/releases/tag/v7.1.0",
                        linuxDebInstallerDownloadUrl = "https://example.com/UsageMonitor-7.1.0.deb"
                    )
                )
            ),
            appUpdateInstaller = installer,
            currentAppVersion = "7.0.0",
            clock = Clock.System,
            config = virtualTimeConfig(testScheduler)
        )
        viewModel.cancelCountdown()

        try {
            runCurrent()
            advanceTimeBy(1_500)
            runCurrent()

            val updateState = viewModel.appUpdateState.value
            assertIs<AppUpdateUiState.Installing>(updateState)
            assertEquals("7.1.0", preparedVersion)
            assertEquals(true, viewModel.shouldExitForUpdate.value)
        } finally {
            viewModel.onDestroy()
        }
    }

    @Test
    fun `prepares linux update and exits only after managed restart is ready`() = runTest {
        val recordedSnapshots = mutableListOf<ApiUsageStats>()
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
        var preparedVersion: String? = null
        val stages = mutableListOf<AutomaticUpdateStage>()
        val installer = object : AppUpdateInstaller {
            override val isSupported: Boolean = true

            override fun canInstall(update: AppUpdateInfo): Boolean {
                return update.linuxDebInstallerDownloadUrl != null
            }

            override suspend fun prepareUpdateInstallation(
                update: AppUpdateInfo,
                onStageChanged: (AutomaticUpdateStage) -> Unit
            ): Result<PreparedUpdateAction> {
                preparedVersion = update.version
                stages += AutomaticUpdateStage.INSTALLING
                onStageChanged(AutomaticUpdateStage.INSTALLING)
                stages += AutomaticUpdateStage.RESTARTING
                onStageChanged(AutomaticUpdateStage.RESTARTING)
                return Result.success(PreparedUpdateAction.RestartAndExit)
            }
        }

        val viewModel = DashboardViewModel(
            GetAnthropicUsageUseCase(anthropicRepo),
            GetMiniMaxUsageUseCase(minimaxRepo),
            GetCodexUsageUseCase(codexRepo),
            GetDeepSeekUsageUseCase(deepSeekRepo),
            MutableStateFlow(emptySet()),
            historyUseCase(recordedSnapshots),
            checkForAppUpdate = updateUseCase(
                Result.success(
                    AppUpdateInfo(
                        version = "7.1.0",
                        releasePageUrl = "https://example.com/releases/tag/v7.1.0",
                        linuxDebInstallerDownloadUrl = "https://example.com/UsageMonitor-7.1.0.deb"
                    )
                )
            ),
            appUpdateInstaller = installer,
            currentAppVersion = "7.0.0",
            clock = Clock.System,
            config = virtualTimeConfig(testScheduler)
        )
        viewModel.cancelCountdown()

        try {
            runCurrent()
            advanceTimeBy(800)
            runCurrent()

            val updateState = viewModel.appUpdateState.value
            assertIs<AppUpdateUiState.Restarting>(updateState)
            assertEquals("7.1.0", preparedVersion)
            assertEquals(
                listOf(AutomaticUpdateStage.INSTALLING, AutomaticUpdateStage.RESTARTING),
                stages
            )
            assertEquals(true, viewModel.shouldExitForUpdate.value)
        } finally {
            viewModel.onDestroy()
        }
    }

    @Test
    fun `allows retrying automatic update after a preparation failure`() = runTest {
        val recordedSnapshots = mutableListOf<ApiUsageStats>()
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
        var attempts = 0
        val installer = object : AppUpdateInstaller {
            override val isSupported: Boolean = true

            override fun canInstall(update: AppUpdateInfo): Boolean {
                return update.windowsInstallerDownloadUrl != null || update.linuxDebInstallerDownloadUrl != null
            }

            override suspend fun prepareUpdateInstallation(
                update: AppUpdateInfo,
                onStageChanged: (AutomaticUpdateStage) -> Unit
            ): Result<PreparedUpdateAction> {
                attempts += 1
                return if (attempts == 1) {
                    Result.failure(IllegalStateException("Falha ao baixar o pacote"))
                } else {
                    Result.success(PreparedUpdateAction.ExitAndInstall)
                }
            }
        }

        val viewModel = DashboardViewModel(
            GetAnthropicUsageUseCase(anthropicRepo),
            GetMiniMaxUsageUseCase(minimaxRepo),
            GetCodexUsageUseCase(codexRepo),
            GetDeepSeekUsageUseCase(deepSeekRepo),
            MutableStateFlow(emptySet()),
            historyUseCase(recordedSnapshots),
            checkForAppUpdate = updateUseCase(
                Result.success(
                    AppUpdateInfo(
                        version = "7.1.0",
                        releasePageUrl = "https://example.com/releases/tag/v7.1.0",
                        linuxDebInstallerDownloadUrl = "https://example.com/UsageMonitor-7.1.0.deb"
                    )
                )
            ),
            appUpdateInstaller = installer,
            currentAppVersion = "7.0.0",
            clock = Clock.System,
            config = virtualTimeConfig(testScheduler)
        )
        viewModel.cancelCountdown()

        try {
            runCurrent()

            viewModel.retryUpdateInstallation()

            runCurrent()
            advanceTimeBy(1_500)
            runCurrent()

            assertEquals(2, attempts)
            assertEquals(true, viewModel.shouldExitForUpdate.value)
        } finally {
            viewModel.onDestroy()
        }
    }
}
