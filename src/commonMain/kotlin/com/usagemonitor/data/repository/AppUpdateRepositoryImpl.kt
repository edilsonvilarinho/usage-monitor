package com.usagemonitor.data.repository

import com.usagemonitor.data.datasource.RemoteApiDataSource
import com.usagemonitor.data.dto.GitHubReleaseAssetDto
import com.usagemonitor.domain.entity.AppUpdateArchitecture
import com.usagemonitor.domain.entity.AppUpdateArtifact
import com.usagemonitor.domain.entity.AppUpdateArtifactKind
import com.usagemonitor.domain.entity.AppUpdateInfo
import com.usagemonitor.domain.entity.AppUpdatePlatform
import com.usagemonitor.domain.repository.AppUpdateRepository

private const val RELEASE_REPOSITORY_OWNER = "edilsonvilarinho"
private const val RELEASE_REPOSITORY_NAME = "usage-monitor"

private const val SHA256_DIGEST_PREFIX = "sha256:"

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
                artifacts = mapArtifacts(latestRelease.assets)
            )
        }
    }
}

/**
 * Classifica os assets da release. Asset que não casa com nenhum tipo conhecido
 * é **descartado**, não mapeado para um valor "desconhecido": a lista alimenta a
 * escolha do pacote que será executado como instalador, e um `checksums.txt` ou
 * um `.exe` estranho ali seria um candidato a mais numa decisão que precisa de
 * candidatos de menos.
 */
internal fun mapArtifacts(assets: List<GitHubReleaseAssetDto>): List<AppUpdateArtifact> {
    return assets.mapNotNull { asset ->
        val kind = artifactKindOf(asset.name) ?: return@mapNotNull null
        AppUpdateArtifact(
            assetName = asset.name,
            downloadUrl = asset.browserDownloadUrl,
            sizeBytes = asset.size,
            sha256 = normalizedSha256(asset.digest),
            platform = platformOf(kind),
            architecture = architectureOf(asset.name),
            kind = kind
        )
    }
}

/**
 * O `.exe` só vira instalador NSIS quando o nome diz `setup`. O jpackage também
 * produz `.exe`, e é o artefato NSIS que entende o modo `/UPDATE`: mandar `/S
 * /UPDATE` para o outro faria uma instalação silenciosa comum, que para no
 * `MessageBox` do `.onInit` e nunca mais sai (medido na atividade A02).
 */
internal fun artifactKindOf(assetName: String): AppUpdateArtifactKind? {
    val name = assetName.lowercase()
    return when {
        name.endsWith(".exe") && name.contains("setup") -> AppUpdateArtifactKind.WINDOWS_NSIS
        name.endsWith(".msi") -> AppUpdateArtifactKind.WINDOWS_MSI
        name.endsWith(".deb") -> AppUpdateArtifactKind.LINUX_DEB
        name.endsWith(".rpm") -> AppUpdateArtifactKind.LINUX_RPM
        name.endsWith(".tar.gz") -> AppUpdateArtifactKind.LINUX_TARBALL
        name.endsWith(".dmg") -> AppUpdateArtifactKind.MACOS_DMG
        else -> null
    }
}

private fun platformOf(kind: AppUpdateArtifactKind): AppUpdatePlatform {
    return when (kind) {
        AppUpdateArtifactKind.WINDOWS_NSIS,
        AppUpdateArtifactKind.WINDOWS_MSI -> AppUpdatePlatform.WINDOWS

        AppUpdateArtifactKind.LINUX_DEB,
        AppUpdateArtifactKind.LINUX_RPM,
        AppUpdateArtifactKind.LINUX_TARBALL -> AppUpdatePlatform.LINUX

        AppUpdateArtifactKind.MACOS_DMG -> AppUpdatePlatform.MACOS
    }
}

/**
 * A arquitetura sai do **nome**, não do tipo de pacote. Hoje só os DMGs carregam
 * o token, e todo o resto é x64 — mas rotular x64 incondicionalmente é o que
 * transformaria a primeira release arm64 num download do pacote errado, em
 * silêncio. O default explícito é o que o workflow publica hoje.
 */
internal fun architectureOf(assetName: String): AppUpdateArchitecture {
    val name = assetName.lowercase()
    return when {
        name.contains("arm64") || name.contains("aarch64") -> AppUpdateArchitecture.ARM64
        else -> AppUpdateArchitecture.X64
    }
}

private fun normalizedSha256(digest: String?): String? {
    val trimmed = digest?.trim()?.lowercase() ?: return null
    if (!trimmed.startsWith(SHA256_DIGEST_PREFIX)) {
        // Digest em algoritmo que não sabemos conferir não é digest para este uso.
        return null
    }
    return trimmed.removePrefix(SHA256_DIGEST_PREFIX).takeIf { it.isNotBlank() }
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
