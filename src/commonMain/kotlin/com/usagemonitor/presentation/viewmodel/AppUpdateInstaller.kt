package com.usagemonitor.presentation.viewmodel

import com.usagemonitor.domain.entity.AppUpdateInfo

interface AppUpdateInstaller {
    val isSupported: Boolean

    fun canInstall(update: AppUpdateInfo): Boolean

    suspend fun prepareUpdateInstallation(update: AppUpdateInfo): Result<Unit>
}

object UnsupportedAppUpdateInstaller : AppUpdateInstaller {
    override val isSupported: Boolean = false

    override fun canInstall(update: AppUpdateInfo): Boolean {
        return false
    }

    override suspend fun prepareUpdateInstallation(update: AppUpdateInfo): Result<Unit> {
        return Result.failure(
            IllegalStateException("Automatic update installation is not supported on this platform.")
        )
    }
}
