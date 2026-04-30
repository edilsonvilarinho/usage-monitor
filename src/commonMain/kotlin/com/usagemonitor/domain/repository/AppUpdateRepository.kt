package com.usagemonitor.domain.repository

import com.usagemonitor.domain.entity.AppUpdateInfo

interface AppUpdateRepository {
    suspend fun getLatestAvailableUpdate(currentVersion: String): Result<AppUpdateInfo?>
}
