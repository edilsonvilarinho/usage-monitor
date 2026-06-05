package com.usagemonitor.ui

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.AppUpdateInfo
import com.usagemonitor.domain.entity.HistoryRange
import com.usagemonitor.domain.repository.AnthropicRepository
import com.usagemonitor.domain.repository.AppUpdateRepository
import com.usagemonitor.domain.repository.CodexRepository
import com.usagemonitor.domain.repository.DeepSeekRepository
import com.usagemonitor.domain.repository.MiniMaxRepository
import com.usagemonitor.domain.repository.UsageHistoryRepository
import com.usagemonitor.domain.usecase.CheckForAppUpdateUseCase
import com.usagemonitor.domain.usecase.GetAnthropicUsageUseCase
import com.usagemonitor.domain.usecase.GetCodexUsageUseCase
import com.usagemonitor.domain.usecase.GetDeepSeekUsageUseCase
import com.usagemonitor.domain.usecase.GetMiniMaxUsageUseCase
import com.usagemonitor.domain.usecase.RecordUsageSnapshotUseCase
import com.usagemonitor.presentation.viewmodel.AppUpdateInstaller
import com.usagemonitor.presentation.viewmodel.AutomaticUpdateStage
import com.usagemonitor.presentation.viewmodel.DashboardViewModel
import com.usagemonitor.presentation.viewmodel.PreparedUpdateAction
import com.usagemonitor.presentation.viewmodel.UnsupportedAppUpdateInstaller
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.Instant

internal fun emptyDashboardViewModel(enabledApis: MutableStateFlow<Set<ApiSource>>): DashboardViewModel {
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
    val historyRepository = object : UsageHistoryRepository {
        override suspend fun recordSnapshot(stats: ApiUsageStats, capturedAt: Instant) = Unit

        override suspend fun getHistoryReport(
            source: ApiSource,
            range: HistoryRange,
            now: Instant
        ) = throw UnsupportedOperationException("Não utilizado neste teste")
    }

    return DashboardViewModel(
        getAnthropicUsage = GetAnthropicUsageUseCase(anthropicRepo),
        getMiniMaxUsage = GetMiniMaxUsageUseCase(minimaxRepo),
        getCodexUsage = GetCodexUsageUseCase(codexRepo),
        getDeepSeekUsage = GetDeepSeekUsageUseCase(deepSeekRepo),
        enabledApis = enabledApis,
        recordUsageSnapshot = RecordUsageSnapshotUseCase(historyRepository)
    )
}

internal fun dashboardViewModelWithAvailableUpdate(enabledApis: MutableStateFlow<Set<ApiSource>>): DashboardViewModel {
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
    val historyRepository = object : UsageHistoryRepository {
        override suspend fun recordSnapshot(stats: ApiUsageStats, capturedAt: Instant) = Unit

        override suspend fun getHistoryReport(
            source: ApiSource,
            range: HistoryRange,
            now: Instant
        ) = throw UnsupportedOperationException("Não utilizado neste teste")
    }
    val updateRepository = object : AppUpdateRepository {
        override suspend fun getLatestAvailableUpdate(currentVersion: String): Result<AppUpdateInfo?> {
            return Result.success(
                AppUpdateInfo(
                    version = "7.1.0",
                    releasePageUrl = "https://example.com/releases/tag/v7.1.0"
                )
            )
        }
    }

    return DashboardViewModel(
        getAnthropicUsage = GetAnthropicUsageUseCase(anthropicRepo),
        getMiniMaxUsage = GetMiniMaxUsageUseCase(minimaxRepo),
        getCodexUsage = GetCodexUsageUseCase(codexRepo),
        getDeepSeekUsage = GetDeepSeekUsageUseCase(deepSeekRepo),
        enabledApis = enabledApis,
        recordUsageSnapshot = RecordUsageSnapshotUseCase(historyRepository),
        checkForAppUpdate = CheckForAppUpdateUseCase(updateRepository),
        appUpdateInstaller = UnsupportedAppUpdateInstaller,
        currentAppVersion = "7.0.0"
    )
}

internal fun dashboardViewModelWithManagedLinuxUpdate(enabledApis: MutableStateFlow<Set<ApiSource>>): DashboardViewModel {
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
    val historyRepository = object : UsageHistoryRepository {
        override suspend fun recordSnapshot(stats: ApiUsageStats, capturedAt: Instant) = Unit

        override suspend fun getHistoryReport(
            source: ApiSource,
            range: HistoryRange,
            now: Instant
        ) = throw UnsupportedOperationException("Não utilizado neste teste")
    }
    val updateRepository = object : AppUpdateRepository {
        override suspend fun getLatestAvailableUpdate(currentVersion: String): Result<AppUpdateInfo?> {
            return Result.success(
                AppUpdateInfo(
                    version = "7.1.0",
                    releasePageUrl = "https://example.com/releases/tag/v7.1.0",
                    linuxDebInstallerDownloadUrl = "https://example.com/UsageMonitor-7.1.0.deb"
                )
            )
        }
    }
    val installer = object : AppUpdateInstaller {
        override val isSupported: Boolean = true

        override fun canInstall(update: AppUpdateInfo): Boolean {
            return update.linuxDebInstallerDownloadUrl != null
        }

        override suspend fun prepareUpdateInstallation(
            update: AppUpdateInfo,
            onStageChanged: (AutomaticUpdateStage) -> Unit
        ): Result<PreparedUpdateAction> {
            onStageChanged(AutomaticUpdateStage.INSTALLING)
            onStageChanged(AutomaticUpdateStage.RESTARTING)
            return Result.success(PreparedUpdateAction.RestartAndExit)
        }
    }

    return DashboardViewModel(
        getAnthropicUsage = GetAnthropicUsageUseCase(anthropicRepo),
        getMiniMaxUsage = GetMiniMaxUsageUseCase(minimaxRepo),
        getCodexUsage = GetCodexUsageUseCase(codexRepo),
        getDeepSeekUsage = GetDeepSeekUsageUseCase(deepSeekRepo),
        enabledApis = enabledApis,
        recordUsageSnapshot = RecordUsageSnapshotUseCase(historyRepository),
        checkForAppUpdate = CheckForAppUpdateUseCase(updateRepository),
        appUpdateInstaller = installer,
        currentAppVersion = "7.0.0"
    )
}
