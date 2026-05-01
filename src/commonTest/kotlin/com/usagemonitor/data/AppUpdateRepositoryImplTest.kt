package com.usagemonitor.data

import com.usagemonitor.data.datasource.RemoteApiDataSource
import com.usagemonitor.data.dto.GitHubReleaseAssetDto
import com.usagemonitor.data.dto.GitHubReleaseDto
import com.usagemonitor.data.repository.AppUpdateRepositoryImpl
import com.usagemonitor.data.repository.isVersionNewer
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

    @Test
    fun `picks msi asset for windows installer url`() = runTest {
        val release = release(
            tag = "v9.0.0",
            assets = listOf(
                asset("UsageMonitor-9.0.0.msi", "https://example.test/win.msi"),
                asset("UsageMonitor-9.0.0.deb", "https://example.test/lin.deb")
            )
        )
        val repo = AppUpdateRepositoryImpl(fakeRemote(release))

        val update = repo.getLatestAvailableUpdate(currentVersion = currentVersion).getOrNull()

        assertEquals("https://example.test/win.msi", update?.windowsInstallerDownloadUrl)
    }

    @Test
    fun `picks deb asset for linux installer url`() = runTest {
        val release = release(
            tag = "v9.0.0",
            assets = listOf(asset("usage-monitor_9.0.0.deb", "https://example.test/lin.deb"))
        )
        val repo = AppUpdateRepositoryImpl(fakeRemote(release))

        val update = repo.getLatestAvailableUpdate(currentVersion = currentVersion).getOrNull()

        assertEquals("https://example.test/lin.deb", update?.linuxDebInstallerDownloadUrl)
        assertNull(update?.windowsInstallerDownloadUrl)
    }

    @Test
    fun `returns null download urls when no matching assets`() = runTest {
        val release = release(
            tag = "v9.0.0",
            assets = listOf(asset("notes.txt", "https://example.test/notes.txt"))
        )
        val repo = AppUpdateRepositoryImpl(fakeRemote(release))

        val update = repo.getLatestAvailableUpdate(currentVersion = currentVersion).getOrNull()

        assertNull(update?.windowsInstallerDownloadUrl)
        assertNull(update?.linuxDebInstallerDownloadUrl)
    }

    @Test
    fun `pre-release suffix is stripped via substringBefore dash`() {
        // Comportamento atual: "8.0.1-beta" -> [8,0,1] que é > "8.0.0".
        assertTrue(isVersionNewer(candidateVersion = "8.0.1-beta", currentVersion = "8.0.0"))
        // E "8.0.0-beta" tratado igual a "8.0.0".
        assertEquals(false, isVersionNewer(candidateVersion = "8.0.0-beta", currentVersion = "8.0.0"))
    }

    @Test
    fun `propagates failure when remote fetch throws`() = runTest {
        val throwingRemote = object : RemoteApiDataSource(noopHttpClient()) {
            override suspend fun fetchLatestGitHubRelease(owner: String, repository: String): GitHubReleaseDto {
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

    private fun asset(name: String, url: String): GitHubReleaseAssetDto {
        return GitHubReleaseAssetDto(name = name, browserDownloadUrl = url)
    }

    private fun fakeRemote(release: GitHubReleaseDto): RemoteApiDataSource {
        return object : RemoteApiDataSource(noopHttpClient()) {
            override suspend fun fetchLatestGitHubRelease(owner: String, repository: String): GitHubReleaseDto {
                return release
            }
        }
    }

    // HttpClient ocioso: o fake sobrescreve todos os métodos, mas a classe pai exige um cliente.
    private fun noopHttpClient(): HttpClient {
        return HttpClient(MockEngine { respond("") })
    }
}
