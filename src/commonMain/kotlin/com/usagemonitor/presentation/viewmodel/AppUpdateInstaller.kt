package com.usagemonitor.presentation.viewmodel

import com.usagemonitor.domain.entity.AppUpdateInfo

interface AppUpdateInstaller {
    val isSupported: Boolean

    fun canInstall(update: AppUpdateInfo): Boolean

    suspend fun prepareUpdateInstallation(
        update: AppUpdateInfo,
        onStageChanged: (AutomaticUpdateStage) -> Unit = {}
    ): Result<PreparedUpdateAction>
}

sealed interface PreparedUpdateAction {
    data object ExitAndInstall : PreparedUpdateAction
    data object RestartAndExit : PreparedUpdateAction
}

enum class AutomaticUpdateStage {
    INSTALLING,
    RESTARTING
}

object UnsupportedAppUpdateInstaller : AppUpdateInstaller {
    override val isSupported: Boolean = false

    override fun canInstall(update: AppUpdateInfo): Boolean {
        return false
    }

    override suspend fun prepareUpdateInstallation(
        update: AppUpdateInfo,
        onStageChanged: (AutomaticUpdateStage) -> Unit
    ): Result<PreparedUpdateAction> {
        return Result.failure(
            IllegalStateException("Automatic update installation is not supported on this platform.")
        )
    }
}
