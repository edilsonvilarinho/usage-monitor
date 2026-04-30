package com.usagemonitor.domain.entity

data class AppUpdateInfo(
    val version: String,
    val releasePageUrl: String,
    val windowsInstallerDownloadUrl: String? = null,
    val linuxDebInstallerDownloadUrl: String? = null
)
