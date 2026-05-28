package com.usagemonitor.presentation.viewmodel

import com.usagemonitor.domain.entity.AppUpdateInfo

sealed interface AppUpdateUiState {
    data class Available(
        val update: AppUpdateInfo,
        val automaticInstallSupported: Boolean
    ) : AppUpdateUiState

    data class Downloading(
        val update: AppUpdateInfo
    ) : AppUpdateUiState

    data class Installing(
        val update: AppUpdateInfo
    ) : AppUpdateUiState

    data class Restarting(
        val update: AppUpdateInfo
    ) : AppUpdateUiState

    data class Failed(
        val update: AppUpdateInfo?,
        val message: String,
        val automaticInstallSupported: Boolean
    ) : AppUpdateUiState
}
