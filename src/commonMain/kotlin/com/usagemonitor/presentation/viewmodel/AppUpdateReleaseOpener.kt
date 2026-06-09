package com.usagemonitor.presentation.viewmodel

interface AppUpdateReleaseOpener {
    fun open(releasePageUrl: String): Result<Unit>
}

object UnsupportedAppUpdateReleaseOpener : AppUpdateReleaseOpener {
    override fun open(releasePageUrl: String): Result<Unit> {
        return Result.failure(
            IllegalStateException("Opening the release page is not supported on this platform.")
        )
    }
}
