package com.usagemonitor.presentation.viewmodel

import com.usagemonitor.domain.entity.AppUpdateInfo

sealed interface AppUpdateUiState {
    data class Available(
        val update: AppUpdateInfo
    ) : AppUpdateUiState
}
