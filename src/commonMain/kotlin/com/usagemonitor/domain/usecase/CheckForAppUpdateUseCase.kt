package com.usagemonitor.domain.usecase

import com.usagemonitor.domain.entity.AppUpdateInfo
import com.usagemonitor.domain.repository.AppUpdateRepository

class CheckForAppUpdateUseCase(
    private val repository: AppUpdateRepository
) {
    suspend operator fun invoke(
        currentVersion: String,
        includePrereleases: Boolean = false
    ): Result<AppUpdateInfo?> {
        return repository.getLatestAvailableUpdate(currentVersion, includePrereleases)
    }
}
