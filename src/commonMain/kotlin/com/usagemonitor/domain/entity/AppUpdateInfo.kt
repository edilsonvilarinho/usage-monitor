package com.usagemonitor.domain.entity

data class AppUpdateInfo(
    val version: String,
    val releasePageUrl: String,
    /**
     * Artefatos publicados na release, já classificados por plataforma,
     * arquitetura e tipo de pacote.
     *
     * Substitui os antigos `windowsInstallerDownloadUrl` e
     * `linuxDebInstallerDownloadUrl`, que eram duas perguntas fixas sobre uma
     * lista variável e cujo único leitor eram os próprios testes. Asset que o
     * classificador não reconhece **não entra na lista** em vez de entrar com
     * tipo desconhecido: um `.exe` que não é o instalador NSIS não pode chegar
     * perto do caminho que executa o instalador em silêncio.
     */
    val artifacts: List<AppUpdateArtifact> = emptyList()
)

data class AppUpdateArtifact(
    val assetName: String,
    val downloadUrl: String,
    /** Nulo é "não informado". Ver [com.usagemonitor.data.dto.GitHubReleaseAssetDto.size]. */
    val sizeBytes: Long?,
    /** Hex puro, sem o prefixo `sha256:` que a API manda. Nulo torna o artefato inelegível. */
    val sha256: String?,
    val platform: AppUpdatePlatform,
    val architecture: AppUpdateArchitecture,
    val kind: AppUpdateArtifactKind
)

enum class AppUpdatePlatform { WINDOWS, LINUX, MACOS }

enum class AppUpdateArchitecture { X64, ARM64 }

enum class AppUpdateArtifactKind {
    WINDOWS_NSIS,
    WINDOWS_MSI,
    LINUX_DEB,
    LINUX_RPM,
    LINUX_TARBALL,
    MACOS_DMG
}
