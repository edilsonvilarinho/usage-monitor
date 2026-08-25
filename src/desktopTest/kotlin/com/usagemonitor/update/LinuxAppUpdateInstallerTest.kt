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
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.GZIPOutputStream
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LinuxAppUpdateInstallerTest {

    private val workDirectory: File = Files.createTempDirectory("usage-monitor-linux-installer").toFile()
    private val downloadsDirectory = File(workDirectory, "downloads")
    private val installRoot = File(workDirectory, "root")
    private val layout = LinuxInstallLayout(installRoot.absolutePath.replace(File.separatorChar, '/'))
    private val tarball: ByteArray = buildTarball()
    private val launched = mutableListOf<Triple<List<String>, File?, File?>>()

    @AfterTest
    fun cleanUp() {
        workDirectory.deleteRecursively()
    }

    // --- support ------------------------------------------------------------

    @Test
    fun `a managed xdg install on linux x64 is supported`() {
        assertEquals(AppUpdateSupport.SUPPORTED, installer().support())
    }

    @Test
    fun `windows and macos report an unsupported platform`() {
        assertEquals(AppUpdateSupport.UNSUPPORTED_PLATFORM, installer(osName = "Windows 11").support())
        assertEquals(AppUpdateSupport.UNSUPPORTED_PLATFORM, installer(osName = "Mac OS X").support())
    }

    /**
     * ARM64 não é "plataforma sem suporte": a plataforma tem suporte e o pacote
     * é que não existe. Dizer o contrário prometeria que reinstalar resolve.
     */
    @Test
    fun `arm64 reports a missing architecture, not a missing platform`() {
        assertEquals(
            AppUpdateSupport.UNSUPPORTED_ARCHITECTURE,
            installer(osArchitecture = "aarch64").support()
        )
        assertEquals(
            AppUpdateSupport.UNSUPPORTED_ARCHITECTURE,
            installer(osArchitecture = "riscv64").support()
        )
    }

    @Test
    fun `an unmanaged install reports an unsupported origin`() {
        assertEquals(
            AppUpdateSupport.UNSUPPORTED_INSTALL_ORIGIN,
            installer(origin = LinuxInstallOrigin.UNMANAGED).support()
        )
    }

    // --- seleção de artefato -------------------------------------------------

    @Test
    fun `the linux x64 tarball is the artifact`() {
        val selected = installer().selectArtifact(updateWith(everyArtifact()))

        assertEquals("usage-monitor_39.0.0_linux_x64.tar.gz", selected?.assetName)
    }

    /**
     * O `.deb` e o `.rpm` da mesma release são artefatos Linux x64 legítimos e
     * não podem entrar aqui: aplicá-los exigiria gerenciador de pacotes e
     * `sudo`, que é o que esta instalação não usa.
     */
    @Test
    fun `native packages are never selected`() {
        val update = updateWith(
            listOf(
                artifact("usage-monitor_39.0.0_amd64.deb", AppUpdateArtifactKind.LINUX_DEB),
                artifact("usage-monitor-39.0.0.x86_64.rpm", AppUpdateArtifactKind.LINUX_RPM)
            )
        )

        assertNull(installer().selectArtifact(update))
    }

    @Test
    fun `an arm64 asset is not taken on an x64 machine`() {
        val update = updateWith(
            listOf(artifact("usage-monitor_39.0.0_linux_arm64.tar.gz", AppUpdateArtifactKind.LINUX_TARBALL))
        )

        assertNull(installer().selectArtifact(update))
    }

    /** É o SHA-256 vindo da API por TLS que barra artefato trocado. */
    @Test
    fun `an artifact without a digest is not eligible`() {
        val update = updateWith(
            listOf(
                artifact("usage-monitor_39.0.0_linux_x64.tar.gz", AppUpdateArtifactKind.LINUX_TARBALL)
                    .copy(sha256 = null)
            )
        )

        assertNull(installer().selectArtifact(update))
    }

    // --- piso de versão-alvo -------------------------------------------------

    /**
     * Enquanto o sentinela estiver de pé, nenhuma release é alvo aceito. Baixá-lo
     * antes de o binário emitir o ACK faria o script desfazer atualizações boas.
     */
    @Test
    fun `no release is a target while the sentinel stands`() {
        val subject = installer()

        assertFalse(subject.isTargetUpdatable("39.0.0"))
        assertFalse(subject.isTargetUpdatable("998.9.9"))
        assertTrue(subject.isTargetUpdatable("999.0.0"))
        assertTrue(subject.isTargetUpdatable("1000.0.0"))
    }

    @Test
    fun `preparing a target below the floor fails`() = runBlocking {
        val result = installer().prepare(updateWith(everyArtifact()))

        assertTrue(result.isFailure)
        assertTrue(
            result.exceptionOrNull()!!.message!!.contains("acknowledges the health check"),
            result.exceptionOrNull()!!.message!!
        )
    }

    // --- prepare -------------------------------------------------------------

    @Test
    fun `preparing downloads, verifies and extracts into the staging directory`() = runBlocking {
        val result = installer(minTargetVersion = "1.0.0").prepare(updateWith(everyArtifact()))

        assertTrue(result.isSuccess, result.exceptionOrNull()?.message.orEmpty())
        assertEquals("usage-monitor_39.0.0_linux_x64.tar.gz", result.getOrThrow().assetName)
        val staging = File(layout.stagingPath("39.0.0"))
        assertTrue(File(staging, "Usage Monitor/bin/Usage Monitor").isFile)
    }

    /**
     * Staging sobrado de uma tentativa anterior é lixo, não retomada: extração
     * interrompida não tem como ser distinguida de completa.
     */
    @Test
    fun `a leftover staging directory is replaced`() = runBlocking {
        val staging = File(layout.stagingPath("39.0.0"))
        staging.mkdirs()
        File(staging, "sobra.txt").writeText("de outra tentativa")

        val result = installer(minTargetVersion = "1.0.0").prepare(updateWith(everyArtifact()))

        assertTrue(result.isSuccess, result.exceptionOrNull()?.message.orEmpty())
        assertFalse(File(staging, "sobra.txt").exists())
    }

    @Test
    fun `preparing on an unmanaged install fails`() = runBlocking {
        val result = installer(minTargetVersion = "1.0.0", origin = LinuxInstallOrigin.UNMANAGED)
            .prepare(updateWith(everyArtifact()))

        assertTrue(result.isFailure)
    }

    // --- schedule ------------------------------------------------------------

    @Test
    fun `scheduling launches the updater with the exact command`() = runBlocking {
        val subject = installer(minTargetVersion = "1.0.0")
        subject.prepare(updateWith(everyArtifact())).getOrThrow()

        val result = subject.schedule(preparation())

        assertTrue(result.isSuccess, result.exceptionOrNull()?.message.orEmpty())
        assertEquals(1, launched.size)
        val (command, directory, logFile) = launched.single()
        assertEquals("/bin/sh", command[0])
        assertEquals(File(layout.updatesPath, LINUX_UPDATER_SCRIPT_NAME).absolutePath, command[1])
        assertEquals(layout.rootPath, command[2])
        assertEquals("39.0.0", command[3])
        assertEquals("38.0.0", command[4])
        assertEquals("4321", command[5])
        assertEquals("4321-1756000000", command[6])
        assertEquals("/home/edils/.local/bin/usage-monitor", command[7])
        assertEquals(File(workDirectory, "update-ack").absolutePath, command[8])
        assertEquals(File(workDirectory, "update-receipt.properties").absolutePath, command[9])
        assertEquals(layout.updatesDirectory.absolutePath, directory?.absolutePath)
        // A saída vai para um arquivo, e é assim que o log do updater é escrito
        // sem o script precisar conhecer o caminho.
        assertEquals(File(workDirectory, "linux-update.log").absolutePath, logFile?.absolutePath)
    }

    @Test
    fun `scheduling materializes the updater script next to the staging`() = runBlocking {
        val subject = installer(minTargetVersion = "1.0.0")
        subject.prepare(updateWith(everyArtifact())).getOrThrow()

        subject.schedule(preparation()).getOrThrow()

        assertTrue(File(layout.updatesPath, LINUX_UPDATER_SCRIPT_NAME).isFile)
    }

    @Test
    fun `scheduling without a prepared staging fails`() {
        val result = installer(minTargetVersion = "1.0.0").schedule(preparation())

        assertTrue(result.isFailure)
        assertTrue(launched.isEmpty())
    }

    /**
     * O staging pode ter sumido entre o `prepare` e o encerramento — uma limpeza
     * de disco, outro processo. Lançar o updater sem ele produziria um rollback.
     */
    @Test
    fun `scheduling with a vanished staging fails`() = runBlocking {
        val subject = installer(minTargetVersion = "1.0.0")
        subject.prepare(updateWith(everyArtifact())).getOrThrow()
        File(layout.stagingPath("39.0.0")).deleteRecursively()

        val result = subject.schedule(preparation())

        assertTrue(result.isFailure)
        assertTrue(launched.isEmpty())
    }

    @Test
    fun `support lost between preparing and scheduling stops the update`() = runBlocking {
        var origin = LinuxInstallOrigin.MANAGED_XDG
        val subject = installer(minTargetVersion = "1.0.0", originProvider = { origin })
        subject.prepare(updateWith(everyArtifact())).getOrThrow()
        origin = LinuxInstallOrigin.UNMANAGED

        val result = subject.schedule(preparation())

        assertTrue(result.isFailure)
        assertTrue(launched.isEmpty())
    }

    // --- fixtures ------------------------------------------------------------

    private fun preparation() = AppUpdatePreparation(
        version = "39.0.0",
        assetName = "usage-monitor_39.0.0_linux_x64.tar.gz",
        sizeBytes = tarball.size.toLong()
    )

    private fun installer(
        osName: String = "Linux",
        osArchitecture: String = "amd64",
        origin: LinuxInstallOrigin = LinuxInstallOrigin.MANAGED_XDG,
        originProvider: () -> LinuxInstallOrigin = { origin },
        minTargetVersion: String = MIN_LINUX_UPDATABLE_TARGET_VERSION
    ): LinuxAppUpdateInstaller {
        val engine = MockEngine { respond(ByteReadChannel(tarball), HttpStatusCode.OK) }
        val client = HttpClient(engine) { install(HttpTimeout) }
        return LinuxAppUpdateInstaller(
            httpClient = client,
            updatesDirectory = downloadsDirectory,
            layoutProvider = { layout },
            originProvider = originProvider,
            osNameProvider = { osName },
            osArchitectureProvider = { osArchitecture },
            currentPidProvider = { 4321L },
            currentVersionProvider = { "38.0.0" },
            launcherPathProvider = { "/home/edils/.local/bin/usage-monitor" },
            ackFileProvider = { File(workDirectory, "update-ack") },
            receiptFileProvider = { File(workDirectory, "update-receipt.properties") },
            logFileProvider = { File(workDirectory, "linux-update.log") },
            ackTokenProvider = { "4321-1756000000" },
            processLauncher = { command, directory, log ->
                launched.add(Triple(command, directory, log))
            },
            minUpdatableTargetVersion = minTargetVersion,
            ioDispatcher = Dispatchers.Unconfined
        )
    }

    private fun updateWith(artifacts: List<AppUpdateArtifact>) = AppUpdateInfo(
        version = "39.0.0",
        releasePageUrl = "https://github.com/edilsonvilarinho/usage-monitor/releases/tag/v39.0.0",
        artifacts = artifacts
    )

    private fun everyArtifact() = listOf(
        artifact("UsageMonitor-Setup-39.0.0.exe", AppUpdateArtifactKind.WINDOWS_NSIS),
        artifact("usage-monitor_39.0.0_amd64.deb", AppUpdateArtifactKind.LINUX_DEB),
        artifact("usage-monitor-39.0.0.x86_64.rpm", AppUpdateArtifactKind.LINUX_RPM),
        artifact("usage-monitor_39.0.0_linux_x64.tar.gz", AppUpdateArtifactKind.LINUX_TARBALL)
    )

    private fun artifact(assetName: String, kind: AppUpdateArtifactKind): AppUpdateArtifact {
        val platform = when (kind) {
            AppUpdateArtifactKind.WINDOWS_NSIS, AppUpdateArtifactKind.WINDOWS_MSI -> AppUpdatePlatform.WINDOWS
            AppUpdateArtifactKind.MACOS_DMG -> AppUpdatePlatform.MACOS
            else -> AppUpdatePlatform.LINUX
        }
        val architecture = if (assetName.contains("arm64")) {
            AppUpdateArchitecture.ARM64
        } else {
            AppUpdateArchitecture.X64
        }
        return AppUpdateArtifact(
            assetName = assetName,
            downloadUrl = "https://github.com/edilsonvilarinho/usage-monitor/releases/download/v39.0.0/$assetName",
            sizeBytes = tarball.size.toLong(),
            sha256 = sha256Of(tarball),
            platform = platform,
            architecture = architecture,
            kind = kind
        )
    }

    private fun buildTarball(): ByteArray {
        val buffer = ByteArrayOutputStream()
        TarArchiveOutputStream(GZIPOutputStream(buffer)).use { output ->
            output.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
            listOf("Usage Monitor/", "Usage Monitor/bin/").forEach { name ->
                output.putArchiveEntry(TarArchiveEntry(name, true).apply { mode = 0b111_101_101 })
                output.closeArchiveEntry()
            }
            val body = "#!/bin/sh\n".toByteArray()
            output.putArchiveEntry(
                TarArchiveEntry("Usage Monitor/bin/Usage Monitor", true).apply {
                    mode = 0b111_101_101
                    size = body.size.toLong()
                }
            )
            output.write(body)
            output.closeArchiveEntry()
        }
        return buffer.toByteArray()
    }

    private fun sha256Of(bytes: ByteArray): String {
        return MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { byte ->
            (byte.toInt() and 0xFF).toString(16).padStart(2, '0')
        }
    }
}
