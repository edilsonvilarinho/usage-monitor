package com.usagemonitor.presentation.viewmodel

import com.usagemonitor.domain.entity.ApiSource

sealed interface DashboardToast {
    data class RateLimit(
        val source: ApiSource
    ) : DashboardToast

    data class ApiError(
        val source: ApiSource,
        val message: String
    ) : DashboardToast
}
