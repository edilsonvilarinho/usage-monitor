package com.usagemonitor.data.datasource

import com.usagemonitor.domain.entity.ReportedModelQuota

interface AntigravityUsageDataSource {
    suspend fun readUsage(): List<ReportedModelQuota>
}
