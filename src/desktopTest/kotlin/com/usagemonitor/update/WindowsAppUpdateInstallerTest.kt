package com.usagemonitor.update

import com.usagemonitor.domain.entity.AppUpdateArchitecture
import com.usagemonitor.domain.entity.AppUpdateArtifact
import com.usagemonitor.domain.entity.AppUpdateArtifactKind
import com.usagemonitor.domain.entity.AppUpdateInfo
import com.usagemonitor.domain.entity.AppUpdatePlatform
import com.usagemonitor.domain.repository.AppUpdatePreparation
import com.usagemonitor.domain.repository.AppUpdateSupport
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WindowsAppUpdateInstallerTest {

    private val updatesDirectory: File = Files.createTempDirectory("usage-monitor-installer").toFile()
    private val payload: ByteArray = ByteArray(4_096) { index -> (index % 97).toByte() }
    private val launched = mutableListOf<Pair<List<String>, File?>>()

    @AfterTest
    fun cleanUp() {
        updatesDirectory.deleteRecursively()
    }

    // --- support ------------------------------------------------------------

    @Test
    fun `nsis install on windows is supported`() {
        assertEquals(AppUpdateSupport.SUPPORTED, installer().support())
    }

    @Test
    fun `linux and macos report an unsupported platform`() {
        assertEquals(
            AppUpdateSupport.UNSUPPORTED_PLATFORM,
            installer(osName = "Linux").support()
        )
        assertEquals(
            AppUpdateSupport.UNSUPPORTED_PLATFORM,
            installer(osName = "Mac OS X").support()
        )
    }

    @Test
    fun `unmanaged windows install reports an unsupported origin`() {
        assertEquals(
            AppUpdateSupport.UNSUPPORTED_INSTALL_ORIGIN,
            installer(origin = WindowsInstallOrigin.UNMANAGED).support()
        )
    }

    // --- gate de versao minima ----------------------------------------------

    /**
     * O gate é o que impede a primeira atualização automática de ser a
     * destrutiva: mandar `/S /UPDATE` para um instalador que não conhece a opção
     * produz uma instalação silenciosa que trava no `MessageBox` do `.onInit`.
     */
    @Test
    fun `target older than the minimum updatable version is refused before any download`() = runBlocking {
        var requests = 0
        val installer = installer(minVersion = "40.0.0", onRequest = { requests++ })

        val result = installer.prepare(update(version = "39.9.9"))

        assertTrue(result.isFailure)
        assertEquals(0, requests)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("/UPDATE"))
    }

    @Test
    fun `target equal to the minimum updatable version is accepted`() = runBlocking {
        val installer = installer(minVersion = "40.0.0")

        val result = installer.prepare(update(version = "40.0.0"))

        assertTrue(result.isSuccess, result.exceptionOrNull()?.message.orEmpty())
    }

    @Test
    fun `the production constant is unreachable while the installer has no update mode`() {
        // Guarda de release: baixar a constante para um valor real antes de o
        // .nsi entender /UPDATE reabriria o caminho destrutivo.
        assertEquals("999.0.0", MIN_UPDATABLE_TARGET_VERSION)
    }

    // --- selecao de artefato ------------------------------------------------

    @Test
    fun `picks the NSIS setup and ignores the msi and the other platforms`() {
        val artifact = installer().selectArtifact(update())

        assertEquals(ASSET_NAME, artifact?.assetName)
    }

    @Test
    fun `artifact without a digest is not selectable`() {
        val update = update(artifacts = listOf(nsisArtifact(sha256 = null)))

        assertNull(installer().selectArtifact(update))
    }

    @Test
    fun `architecture mismatch selects nothing instead of the wrong binary`() {
        val installer = installer(osArch = "aarch64")

        assertNull(installer.selectArtifact(update()))
    }

    @Test
    fun `unknown architecture selects nothing`() {
        val installer = installer(osArch = "s390x")

        assertNull(installer.selectArtifact(update()))
    }

    // --- prepare ------------------------------------------------------------

    @Test
    fun `prepare downloads the artifact and reports what will be applied`() = runBlocking {
        val preparation = installer(minVersion = "1.0.0").prepare(update()).getOrThrow()

        assertEquals("40.0.0", preparation.version)
        assertEquals(ASSET_NAME, preparation.assetName)
        assertEquals(payload.size.toLong(), preparation.sizeBytes)
        assertTrue(File(updatesDirectory, ASSET_NAME).isFile)
    }

    @Test
    fun `prepare prunes older artifacts only after the download succeeds`() = runBlocking {
        updatesDirectory.mkdirs()
        val stale = File(updatesDirectory, "UsageMonitor-Setup-36.0.0.exe")
        stale.writeBytes(ByteArray(16))

        // Rede caindo: a versão anterior no disco não pode ser apagada por uma
        // tentativa que não chegou a produzir substituto.
        val failing = installer(minVersion = "1.0.0", status = HttpStatusCode.ServiceUnavailable)
        assertTrue(failing.prepare(update()).isFailure)
        assertTrue(stale.isFile, "poda antes do download apagaria a versão anterior à toa")

        installer(minVersion = "1.0.0").prepare(update()).getOrThrow()
        assertTrue(!stale.exists())
    }

    @Test
    fun `prepare on an unsupported install fails without downloading`() = runBlocking {
        var requests = 0
        val installer = installer(
            minVersion = "1.0.0",
            origin = WindowsInstallOrigin.UNMANAGED,
            onRequest = { requests++ }
        )

        assertTrue(installer.prepare(update()).isFailure)
        assertEquals(0, requests)
    }

    // --- schedule -----------------------------------------------------------

    @Test
    fun `schedule launches the silent installer with the exiting pid`() = runBlocking {
        val installer = installer(minVersion = "1.0.0", pid = 4321L)
        val preparation = installer.prepare(update()).getOrThrow()

        val result = installer.schedule(preparation)

        assertTrue(result.isSuccess, result.exceptionOrNull()?.message.orEmpty())
        assertEquals(1, launched.size)
        val (command, workingDirectory) = launched.single()
        assertEquals(
            listOf(File(updatesDirectory, ASSET_NAME).absolutePath, "/S", "/UPDATE", "/PID=4321"),
            command
        )
        assertEquals(updatesDirectory, workingDirectory)
    }

    @Test
    fun `schedule without a prepared artifact launches nothing`() {
        val result = installer().schedule(
            AppUpdatePreparation(version = "40.0.0", assetName = ASSET_NAME, sizeBytes = 1)
        )

        assertTrue(result.isFailure)
        assertEquals(emptyList(), launched)
    }

    @Test
    fun `schedule for a different version than the prepared one launches nothing`() = runBlocking {
        val installer = installer(minVersion = "1.0.0")
        installer.prepare(update()).getOrThrow()

        val result = installer.schedule(
            AppUpdatePreparation(version = "41.0.0", assetName = ASSET_NAME, sizeBytes = 1)
        )

        assertTrue(result.isFailure)
        assertEquals(emptyList(), launched)
    }

    @Test
    fun `schedule launches nothing when the prepared file vanished from disk`() = runBlocking {
        val installer = installer(minVersion = "1.0.0")
        val preparation = installer.prepare(update()).getOrThrow()
        File(updatesDirectory, ASSET_NAME).delete()

        val result = installer.schedule(preparation)

        assertTrue(result.isFailure)
        assertEquals(emptyList(), launched)
    }

    // --- infraestrutura -----------------------------------------------------

    private fun installer(
        osName: String = "Windows 11",
        osArch: String = "amd64",
        origin: WindowsInstallOrigin = WindowsInstallOrigin.NSIS_PER_USER,
        minVersion: String = MIN_UPDATABLE_TARGET_VERSION,
        pid: Long = 1234L,
        status: HttpStatusCode = HttpStatusCode.OK,
        onRequest: () -> Unit = {}
    ): WindowsAppUpdateInstaller {
        val client = HttpClient(
            MockEngine {
                onRequest()
                respond(ByteReadChannel(payload), status)
            }
        ) {
            install(HttpTimeout) {
                requestTimeoutMillis = 20_000
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 20_000
            }
        }
        return WindowsAppUpdateInstaller(
            httpClient = client,
            updatesDirectory = updatesDirectory,
            originProvider = { origin },
            osNameProvider = { osName },
            osArchitectureProvider = { osArch },
            currentPidProvider = { pid },
            // O teste nunca lança processo de verdade; o contrato exercitado é o
            // comando, que é o que o instalador precisa acertar.
            processLauncher = { command, directory -> launched += command to directory },
            minUpdatableTargetVersion = minVersion,
            ioDispatcher = Dispatchers.Default
        )
    }

    private fun update(
        version: String = "40.0.0",
        artifacts: List<AppUpdateArtifact> = defaultArtifacts()
    ): AppUpdateInfo {
        return AppUpdateInfo(
            version = version,
            releasePageUrl = "https://github.com/edilsonvilarinho/usage-monitor/releases/tag/v$version",
            artifacts = artifacts
        )
    }

    private fun defaultArtifacts(): List<AppUpdateArtifact> {
        return listOf(
            artifact("Usage.Monitor-40.0.0.msi", AppUpdateArtifactKind.WINDOWS_MSI, AppUpdatePlatform.WINDOWS),
            artifact(
                "usage-monitor_40.0.0_linux_x64.tar.gz",
                AppUpdateArtifactKind.LINUX_TARBALL,
                AppUpdatePlatform.LINUX
            ),
            nsisArtifact()
        )
    }

    private fun nsisArtifact(sha256: String? = sha256Hex(payload)): AppUpdateArtifact {
        return artifact(
            assetName = ASSET_NAME,
            kind = AppUpdateArtifactKind.WINDOWS_NSIS,
            platform = AppUpdatePlatform.WINDOWS,
            sha256 = sha256
        )
    }

    private fun artifact(
        assetName: String,
        kind: AppUpdateArtifactKind,
        platform: AppUpdatePlatform,
        sha256: String? = sha256Hex(payload)
    ): AppUpdateArtifact {
        return AppUpdateArtifact(
            assetName = assetName,
            downloadUrl = "https://github.com/edilsonvilarinho/usage-monitor/releases/download/v40.0.0/$assetName",
            sizeBytes = payload.size.toLong(),
            sha256 = sha256,
            platform = platform,
            architecture = AppUpdateArchitecture.X64,
            kind = kind
        )
    }

    private fun sha256Hex(bytes: ByteArray): String {
        return MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { byte ->
            val value = byte.toInt() and 0xFF
            value.toString(16).padStart(2, '0')
        }
    }

    private companion object {
        const val ASSET_NAME = "UsageMonitor-Setup-40.0.0.exe"
    }
}
