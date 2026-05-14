package com.usagemonitor.presentation.viewmodel

import com.usagemonitor.domain.entity.AppUpdateInfo

interface AppUpdateInstaller {
    val isSupported: Boolean

    fun canInstall(update: AppUpdateInfo): Boolean

    suspend fun prepareUpdateInstallation(update: AppUpdateInfo): Result<PreparedUpdateAction>
}

sealed interface PreparedUpdateAction {
    data object ExitAndInstall : PreparedUpdateAction
    data object InstallerOpened : PreparedUpdateAction
}

object UnsupportedAppUpdateInstaller : AppUpdateInstaller {
    override val isSupported: Boolean = false

    override fun canInstall(update: AppUpdateInfo): Boolean {
        return false
    }

    override suspend fun prepareUpdateInstallation(update: AppUpdateInfo): Result<PreparedUpdateAction> {
        return Result.failure(
            IllegalStateException("Automatic update installation is not supported on this platform.")
        )
    }
}
