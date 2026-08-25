package com.usagemonitor.domain.usecase

import com.usagemonitor.domain.entity.ReleaseNotes
import com.usagemonitor.domain.repository.AppUpdateRepository

class GetReleaseNotesUseCase(
    private val repository: AppUpdateRepository
) {
    suspend operator fun invoke(version: String, previousVersion: String?): Result<ReleaseNotes?> {
        return repository.getReleaseNotes(version, previousVersion)
    }
}
