package com.usagemonitor.presentation.viewmodel

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

data class DashboardViewModelConfig(
    val workerDispatcher: CoroutineDispatcher = Dispatchers.Default,
    val pollInterval: Duration = 600.seconds,
    val updateCheckIntervalWhileRunning: Duration = 10.minutes
)
