package com.usagemonitor.data

import com.usagemonitor.data.datasource.RemoteApiDataSource
import com.usagemonitor.data.dto.GitHubReleaseAssetDto
import com.usagemonitor.data.dto.GitHubReleaseDto
import com.usagemonitor.data.repository.AppUpdateRepositoryImpl
import com.usagemonitor.data.repository.isVersionNewer
import com.usagemonitor.domain.entity.AppUpdateArchitecture
import com.usagemonitor.domain.entity.AppUpdateArtifactKind
import com.usagemonitor.domain.entity.AppUpdatePlatform
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppUpdateRepositoryImplTest {

    private val currentVersion = "8.0.0"
    private val releasePageUrl = "https://github.com/edilsonvilarinho/usage-monitor/releases/tag/v8.1.0"

    @Test
    fun `returns null when latest version equals current`() = runTest {
        val repo = AppUpdateRepositoryImpl(fakeRemote(release(tag = "v8.0.0")))

        val result = repo.getLatestAvailableUpdate(currentVersion = currentVersion)

        assertEquals(null, result.getOrNull())
    }

    @Test
    fun `returns null when latest version older than current`() = runTest {
        val repo = AppUpdateRepositoryImpl(fakeRemote(release(tag = "v7.5.0")))

        val result = repo.getLatestAvailableUpdate(currentVersion = currentVersion)

        assertEquals(null, result.getOrNull())
    }

    @Test
    fun `returns AppUpdateInfo on patch bump`() = runTest {
        val repo = AppUpdateRepositoryImpl(fakeRemote(release(tag = "v8.0.1")))

        val update = repo.getLatestAvailableUpdate(currentVersion = currentVersion).getOrNull()

        assertEquals("8.0.1", update?.version)
    }

    @Test
    fun `returns AppUpdateInfo on minor bump`() = runTest {
        val repo = AppUpdateRepositoryImpl(fakeRemote(release(tag = "v8.1.0")))

        val update = repo.getLatestAvailableUpdate(currentVersion = currentVersion).getOrNull()

        assertEquals("8.1.0", update?.version)
        assertEquals(releasePageUrl, update?.releasePageUrl)
    }

    @Test
    fun `strips v prefix from tag name`() = runTest {
        val repo = AppUpdateRepositoryImpl(fakeRemote(release(tag = "v9.0.0")))

        val update = repo.getLatestAvailableUpdate(currentVersion = currentVersion).getOrNull()

        assertEquals("9.0.0", update?.version)
    }

    /**
     * Regressão sobre os **nomes reais** publicados no v37.0.0. Renomear um asset
     * no workflow de release desliga a atualização automática em silêncio: o
     * classificador deixa de reconhecer o pacote, a lista de artefatos perde o
     * candidato e o app volta a só avisar. Este teste é o que transforma isso em
     * suíte vermelha.
     */
    @Test
    fun `classifies the real v37 asset names`() = runTest {
        val repo = AppUpdateRepositoryImpl(fakeRemote(release(tag = "v37.0.0", assets = realV37Assets())))

        val artifacts = repo.getLatestAvailableUpdate(currentVersion = currentVersion)
            .getOrNull()
            ?.artifacts
            .orEmpty()
            .associateBy { it.assetName }

        assertEquals(
            AppUpdateArtifactKind.WINDOWS_NSIS,
            artifacts.getValue("UsageMonitor-Setup-37.0.0.exe").kind
        )
        assertEquals(AppUpdateArtifactKind.WINDOWS_MSI, artifacts.getValue("Usage.Monitor-37.0.0.msi").kind)
        assertEquals(
            AppUpdateArtifactKind.LINUX_TARBALL,
            artifacts.getValue("usage-monitor_37.0.0_linux_x64.tar.gz").kind
        )
        assertEquals(AppUpdateArtifactKind.LINUX_DEB, artifacts.getValue("usage-monitor_37.0.0-1_amd64.deb").kind)
        assertEquals(AppUpdateArtifactKind.LINUX_RPM, artifacts.getValue("usage-monitor-37.0.0-1.x86_64.rpm").kind)
        assertEquals(
            AppUpdateArtifactKind.MACOS_DMG,
            artifacts.getValue("usage-monitor_37.0.0_macos_arm64.dmg").kind
        )

        // Os sete assets da release são sete pacotes reconhecíveis: nenhum se perde.
        assertEquals(7, artifacts.size)
    }

    @Test
    fun `derives platform and architecture from the real asset names`() = runTest {
        val repo = AppUpdateRepositoryImpl(fakeRemote(release(tag = "v37.0.0", assets = realV37Assets())))

        val artifacts = repo.getLatestAvailableUpdate(currentVersion = currentVersion)
            .getOrNull()
            ?.artifacts
            .orEmpty()
            .associateBy { it.assetName }

        assertEquals(AppUpdatePlatform.WINDOWS, artifacts.getValue("UsageMonitor-Setup-37.0.0.exe").platform)
        assertEquals(AppUpdatePlatform.LINUX, artifacts.getValue("usage-monitor_37.0.0-1_amd64.deb").platform)
        assertEquals(AppUpdatePlatform.MACOS, artifacts.getValue("usage-monitor_37.0.0_macos_x64.dmg").platform)

        // Só os DMGs carregam token de arquitetura hoje; o resto é x64 por default explícito.
        assertEquals(
            AppUpdateArchitecture.ARM64,
            artifacts.getValue("usage-monitor_37.0.0_macos_arm64.dmg").architecture
        )
        assertEquals(
            AppUpdateArchitecture.X64,
            artifacts.getValue("usage-monitor_37.0.0_macos_x64.dmg").architecture
        )
        assertEquals(
            AppUpdateArchitecture.X64,
            artifacts.getValue("UsageMonitor-Setup-37.0.0.exe").architecture
        )
        assertEquals(
            AppUpdateArchitecture.X64,
            artifacts.getValue("usage-monitor-37.0.0-1.x86_64.rpm").architecture
        )
    }

    @Test
    fun `strips the sha256 prefix and carries the size`() = runTest {
        val release = release(
            tag = "v9.0.0",
            assets = listOf(
                asset(
                    name = "UsageMonitor-Setup-9.0.0.exe",
                    url = "https://example.test/win-setup.exe",
                    size = 120054859L,
                    digest = "sha256:23222796BB56197309FB3FEC5A6705DD4C016EC65996F4DDF0470AF7D3CC40E3"
                )
            )
        )
        val repo = AppUpdateRepositoryImpl(fakeRemote(release))

        val artifact = repo.getLatestAvailableUpdate(currentVersion = currentVersion)
            .getOrNull()
            ?.artifacts
            ?.single()

        assertEquals(
            "23222796bb56197309fb3fec5a6705dd4c016ec65996f4ddf0470af7d3cc40e3",
            artifact?.sha256
        )
        assertEquals(120054859L, artifact?.sizeBytes)
    }

    @Test
    fun `asset without digest keeps a null sha256 instead of an empty one`() = runTest {
        val release = release(
            tag = "v9.0.0",
            assets = listOf(asset("UsageMonitor-Setup-9.0.0.exe", "https://example.test/win-setup.exe"))
        )
        val repo = AppUpdateRepositoryImpl(fakeRemote(release))

        val artifact = repo.getLatestAvailableUpdate(currentVersion = currentVersion)
            .getOrNull()
            ?.artifacts
            ?.single()

        assertNull(artifact?.sha256)
        assertNull(artifact?.sizeBytes)
    }

    @Test
    fun `digest in another algorithm is discarded, never carried as a sha256`() = runTest {
        val release = release(
            tag = "v9.0.0",
            assets = listOf(
                asset(
                    name = "UsageMonitor-Setup-9.0.0.exe",
                    url = "https://example.test/win-setup.exe",
                    digest = "sha512:aaaa"
                )
            )
        )
        val repo = AppUpdateRepositoryImpl(fakeRemote(release))

        val artifact = repo.getLatestAvailableUpdate(currentVersion = currentVersion)
            .getOrNull()
            ?.artifacts
            ?.single()

        assertNull(artifact?.sha256)
    }

    /**
     * Um `.exe` que não diz `setup` não é o instalador NSIS — pode ser o `.exe`
     * do jpackage. Ele não pode virar candidato: só o NSIS entende `/UPDATE`, e
     * mandar `/S /UPDATE` para o outro produz uma instalação silenciosa que para
     * no `MessageBox` do `.onInit` e nunca mais sai.
     */
    @Test
    fun `discards assets that are not recognizable packages`() = runTest {
        val release = release(
            tag = "v9.0.0",
            assets = listOf(
                asset("notes.txt", "https://example.test/notes.txt"),
                asset("checksums.txt", "https://example.test/checksums.txt"),
                asset("Usage.Monitor-9.0.0.exe", "https://example.test/win-jpackage.exe")
            )
        )
        val repo = AppUpdateRepositoryImpl(fakeRemote(release))

        val update = repo.getLatestAvailableUpdate(currentVersion = currentVersion).getOrNull()

        assertEquals(emptyList(), update?.artifacts)
    }

    @Test
    fun `pre-release suffix is stripped via substringBefore dash`() {
        // Comportamento atual: "8.0.1-beta" -> [8,0,1] que é > "8.0.0".
        assertTrue(isVersionNewer(candidateVersion = "8.0.1-beta", currentVersion = "8.0.0"))
        // E "8.0.0-beta" tratado igual a "8.0.0".
        assertEquals(false, isVersionNewer(candidateVersion = "8.0.0-beta", currentVersion = "8.0.0"))
    }

    /**
     * Sem a sobrescrita, cada tentativa de smoke test da atualizacao automatica
     * exigiria publicar uma release de verdade no GitHub.
     */
    @Test
    fun `the release feed url can be overridden by the environment`() = runTest {
        var seenOverride: String? = "nao lido"
        val remote = object : RemoteApiDataSource(noopHttpClient()) {
            override suspend fun fetchLatestGitHubRelease(
                owner: String,
                repository: String,
                feedUrlOverride: String?
            ): GitHubReleaseDto {
                seenOverride = feedUrlOverride
                return release(tag = "v9.0.0")
            }
        }
        val repo = AppUpdateRepositoryImpl(remote) { "http://localhost:8099/release.json" }

        repo.getLatestAvailableUpdate(currentVersion = currentVersion)

        assertEquals("http://localhost:8099/release.json", seenOverride)
    }

    @Test
    fun `without the environment variable the override is null`() = runTest {
        var seenOverride: String? = "nao lido"
        val remote = object : RemoteApiDataSource(noopHttpClient()) {
            override suspend fun fetchLatestGitHubRelease(
                owner: String,
                repository: String,
                feedUrlOverride: String?
            ): GitHubReleaseDto {
                seenOverride = feedUrlOverride
                return release(tag = "v9.0.0")
            }
        }
        val repo = AppUpdateRepositoryImpl(remote) { null }

        repo.getLatestAvailableUpdate(currentVersion = currentVersion)

        assertNull(seenOverride)
    }

    @Test
    fun `propagates failure when remote fetch throws`() = runTest {
        val throwingRemote = object : RemoteApiDataSource(noopHttpClient()) {
            override suspend fun fetchLatestGitHubRelease(
                owner: String,
                repository: String,
                feedUrlOverride: String?
            ): GitHubReleaseDto {
                throw IllegalStateException("network down")
            }
        }
        val repo = AppUpdateRepositoryImpl(throwingRemote)

        val result = repo.getLatestAvailableUpdate(currentVersion = currentVersion)

        assertTrue(result.isFailure)
        assertEquals("network down", result.exceptionOrNull()?.message)
    }

    private fun release(
        tag: String,
        assets: List<GitHubReleaseAssetDto> = emptyList()
    ): GitHubReleaseDto {
        return GitHubReleaseDto(
            tagName = tag,
            htmlUrl = releasePageUrl,
            assets = assets
        )
    }

    private fun asset(
        name: String,
        url: String,
        size: Long? = null,
        digest: String? = null
    ): GitHubReleaseAssetDto {
        return GitHubReleaseAssetDto(
            name = name,
            browserDownloadUrl = url,
            size = size,
            digest = digest
        )
    }

    /** Os sete nomes publicados no v37.0.0, copiados da resposta real da API. */
    private fun realV37Assets(): List<GitHubReleaseAssetDto> {
        return listOf(
            "usage-monitor-37.0.0-1.x86_64.rpm",
            "usage-monitor_37.0.0-1_amd64.deb",
            "usage-monitor_37.0.0_linux_x64.tar.gz",
            "usage-monitor_37.0.0_macos_arm64.dmg",
            "usage-monitor_37.0.0_macos_x64.dmg",
            "Usage.Monitor-37.0.0.msi",
            "UsageMonitor-Setup-37.0.0.exe"
        ).map { name ->
            asset(
                name = name,
                url = "https://github.com/edilsonvilarinho/usage-monitor/releases/download/v37.0.0/$name",
                size = 1_000L,
                digest = "sha256:${name.hashCode().toString(16)}"
            )
        }
    }

    private fun fakeRemote(release: GitHubReleaseDto): RemoteApiDataSource {
        return object : RemoteApiDataSource(noopHttpClient()) {
            override suspend fun fetchLatestGitHubRelease(
                owner: String,
                repository: String,
                feedUrlOverride: String?
            ): GitHubReleaseDto {
                return release
            }
        }
    }

    // HttpClient ocioso: o fake sobrescreve todos os métodos, mas a classe pai exige um cliente.
    private fun noopHttpClient(): HttpClient {
        return HttpClient(MockEngine { respond("") })
    }
}
