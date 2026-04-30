package com.usagemonitor.data.repository

import com.usagemonitor.data.datasource.RemoteApiDataSource
import com.usagemonitor.domain.entity.AppUpdateInfo
import com.usagemonitor.domain.repository.AppUpdateRepository

private const val RELEASE_REPOSITORY_OWNER = "edilsonvilarinho"
private const val RELEASE_REPOSITORY_NAME = "usage-monitor"

class AppUpdateRepositoryImpl(
    private val remoteApiDataSource: RemoteApiDataSource
) : AppUpdateRepository {

    override suspend fun getLatestAvailableUpdate(currentVersion: String): Result<AppUpdateInfo?> {
        return Result.runCatching {
            val latestRelease = remoteApiDataSource.fetchLatestGitHubRelease(
                owner = RELEASE_REPOSITORY_OWNER,
                repository = RELEASE_REPOSITORY_NAME
            )
            val latestVersion = latestRelease.tagName.removePrefix("v")

            if (!isVersionNewer(latestVersion, currentVersion)) {
                return@runCatching null
            }

            AppUpdateInfo(
                version = latestVersion,
                releasePageUrl = latestRelease.htmlUrl,
                windowsInstallerDownloadUrl = latestRelease.assets.firstOrNull { asset ->
                    asset.name.endsWith(".msi", ignoreCase = true)
                }?.browserDownloadUrl
            )
        }
    }
}

internal fun isVersionNewer(candidateVersion: String, currentVersion: String): Boolean {
    return compareVersions(candidateVersion, currentVersion) > 0
}

private fun compareVersions(left: String, right: String): Int {
    val leftParts = versionParts(left)
    val rightParts = versionParts(right)
    val maxSize = maxOf(leftParts.size, rightParts.size)

    for (index in 0 until maxSize) {
        val leftPart = leftParts.getOrElse(index) { 0 }
        val rightPart = rightParts.getOrElse(index) { 0 }

        if (leftPart != rightPart) {
            return leftPart.compareTo(rightPart)
        }
    }

    return 0
}

private fun versionParts(version: String): List<Int> {
    val normalizedVersion = version
        .trim()
        .removePrefix("v")
        .substringBefore("-")

    if (normalizedVersion.isBlank()) {
        return listOf(0)
    }

    return normalizedVersion.split(".").map { token ->
        token.filter(Char::isDigit).toIntOrNull() ?: 0
    }
}
