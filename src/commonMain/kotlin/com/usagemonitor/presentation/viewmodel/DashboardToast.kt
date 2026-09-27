package com.usagemonitor.presentation.viewmodel

import com.usagemonitor.domain.entity.ApiSource
import kotlinx.datetime.Instant

sealed interface DashboardToast {
    data class RateLimit(
        val source: ApiSource,
        /** Até quando a fonte fica sem ir à rede; nulo quando não há backoff armado. */
        val retryAt: Instant? = null
    ) : DashboardToast

    data class ServiceUnavailable(
        val source: ApiSource
    ) : DashboardToast

    data class ApiError(
        val source: ApiSource,
        val message: String
    ) : DashboardToast

    data class ReleasePageError(
        val message: String
    ) : DashboardToast
}
