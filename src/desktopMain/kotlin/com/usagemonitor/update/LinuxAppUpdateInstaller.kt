package com.usagemonitor.update

import com.usagemonitor.CURRENT_APP_VERSION
import com.usagemonitor.data.repository.isVersionNewer
import com.usagemonitor.domain.entity.AppUpdateArchitecture
import com.usagemonitor.domain.entity.AppUpdateArtifact
import com.usagemonitor.domain.entity.AppUpdateArtifactKind
import com.usagemonitor.domain.entity.AppUpdateInfo
import com.usagemonitor.domain.entity.AppUpdatePlatform
import com.usagemonitor.domain.repository.AppUpdateInstaller
import com.usagemonitor.domain.repository.AppUpdatePreparation
import com.usagemonitor.domain.repository.AppUpdateSupport
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Primeira versão cujo binário **emite o ACK** do health check.
 *
 * **Este gate não é precaução, é requisito**, e pela mesma razão do gate do
 * Windows, com outro mecanismo: a versão promovida é quem confirma que subiu, e
 * um binário anterior a este código **nunca lê** `USAGE_MONITOR_UPDATE_ACK`.
 * Ele sobe normalmente, nunca confirma, e o script então desfaz uma
 * atualização que deu certo.
 *
 * Quem decide é a **versão baixada**, não a instalada.
 *
 * **`38.0.1` é a versão testada de verdade** (A14, 2026-08-29, issue #121): é a
 * primeira release publicada depois dos fixes de symlink ostree e de
 * `LD_LIBRARY_PATH` obsoleto, e foi contra o tarball real dela, baixado do
 * `github.com`, que o ciclo completo de promoção + ACK foi observado com
 * `status=success` pela primeira vez. Antes disso o valor era o sentinela
 * inalcançável `999.0.0`: enquanto ele ficava de pé, nenhuma release era alvo
 * aceito. `AutoUpdateWiringTest` reprova a combinação inconsistente com
 * [LINUX_AUTO_UPDATE_SHIPPED] nos dois sentidos.
 */
internal const val MIN_LINUX_UPDATABLE_TARGET_VERSION = "38.0.1"

/**
 * Log do updater, ao lado do `startup.jsonl`.
 *
 * Não vai para `$XDG_STATE_HOME`, como o plano da issue propunha: seria um
 * terceiro dono de diagnóstico, e `~/.usage-monitor/diagnostics/` já é onde
 * alguém procura.
 */
internal fun defaultLinuxUpdateLogFile(): File {
    val home = System.getProperty("user.home") ?: error("user.home is unavailable")
    return File(home, ".usage-monitor/diagnostics/linux-update.log")
}

/**
 * Token do health check: PID mais o instante, no alfabeto que
 * [isValidUpdateAckToken] aceita.
 *
 * Não precisa ser imprevisível — não é segredo, e quem o lê é o script que o
 * gerou. Precisa ser **diferente a cada tentativa**, e é isso que faz um ACK
 * sobrado de outra sessão nunca confirmar esta.
 */
internal fun defaultUpdateAckToken(
    pid: Long = ProcessHandle.current().pid(),
    nowMillis: Long = System.currentTimeMillis()
): String = "$pid-$nowMillis"

/**
 * Atualização automática numa instalação Linux XDG gerenciada.
 *
 * Mesmo contrato de duas fases do Windows, e mesmo downloader: [prepare] baixa,
 * confere o SHA-256 e **extrai** enquanto o app roda; [schedule] entrega o swap
 * ao `linux-updater.sh` no encerramento. Extrair 125 MB no caminho de saída não
 * teria nem tela nem tempo.
 */
internal class LinuxAppUpdateInstaller(
    httpClient: HttpClient,
    private val updatesDirectory: File = defaultUpdatesDirectory(),
    private val downloader: UpdateArtifactDownloader = UpdateArtifactDownloader(httpClient, updatesDirectory),
    private val layoutProvider: () -> LinuxInstallLayout? = { resolveLinuxInstallLayout() },
    private val originProvider: () -> LinuxInstallOrigin = { LinuxInstallOriginResolver.current() },
    private val osNameProvider: () -> String = { System.getProperty("os.name").orEmpty() },
    private val osArchitectureProvider: () -> String = { System.getProperty("os.arch").orEmpty() },
    private val currentPidProvider: () -> Long = { ProcessHandle.current().pid() },
    private val currentVersionProvider: () -> String = { CURRENT_APP_VERSION },
    private val launcherPathProvider: () -> String? = { resolveLinuxStableLauncherPath() },
    private val ackFileProvider: () -> File = { UpdateAckChannel.defaultUpdateAckFile() },
    private val receiptFileProvider: () -> File = { defaultUpdateReceiptFile() },
    private val logFileProvider: () -> File = { defaultLinuxUpdateLogFile() },
    private val ackTokenProvider: () -> String = { defaultUpdateAckToken() },
    private val extractor: TarballExtractor = TarballExtractor(),
    private val scriptMaterializer: (File) -> File = { directory -> materializeLinuxUpdaterScript(directory) },
    /**
     * Três parâmetros e não dois: aqui a saída **vai para um arquivo**, que é
     * como o log do updater é escrito sem o script precisar conhecer o caminho.
     */
    private val processLauncher: (List<String>, File?, File?) -> Unit = ::launchDetachedProcess,
    private val minUpdatableTargetVersion: String = MIN_LINUX_UPDATABLE_TARGET_VERSION,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : AppUpdateInstaller {

    /**
     * Staging já extraído e conferido, por versão.
     *
     * `@Volatile` pela mesma razão do instalador do Windows: `prepare` escreve
     * numa corrotina e `schedule` lê na thread do encerramento.
     */
    @Volatile
    private var preparedStaging: Pair<String, File>? = null

    override fun support(): AppUpdateSupport {
        if (!osNameProvider().lowercase().contains("linux")) {
            return AppUpdateSupport.UNSUPPORTED_PLATFORM
        }
        // Arquitetura antes de origem: "não existe pacote para esta máquina" é
        // uma resposta mais fundamental que "esta instalação não veio do
        // instalador certo", e numa ARM64 a segunda seria uma promessa de que
        // reinstalar resolveria.
        if (currentArchitecture() != AppUpdateArchitecture.X64) {
            return AppUpdateSupport.UNSUPPORTED_ARCHITECTURE
        }
        return when (originProvider()) {
            LinuxInstallOrigin.MANAGED_XDG -> AppUpdateSupport.SUPPORTED
            LinuxInstallOrigin.UNMANAGED -> AppUpdateSupport.UNSUPPORTED_INSTALL_ORIGIN
        }
    }

    override suspend fun prepare(
        update: AppUpdateInfo,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit
    ): Result<AppUpdatePreparation> {
        return withContext(ioDispatcher) {
            try {
                val support = support()
                if (support != AppUpdateSupport.SUPPORTED) {
                    throw IllegalStateException("Automatic updates are not available here ($support).")
                }
                if (!isTargetUpdatable(update.version)) {
                    throw IllegalStateException(
                        "Version ${update.version} predates the build that acknowledges the health check " +
                            "(minimum $minUpdatableTargetVersion)."
                    )
                }

                val layout = layoutProvider()
                    ?: throw IllegalStateException("The managed install root could not be resolved.")

                val artifact = selectArtifact(update)
                    ?: throw IllegalStateException("No verifiable Linux tarball was published for this platform.")

                val file = downloader.download(artifact, onProgress)
                // A poda vem depois do download, e não antes, pelo mesmo motivo
                // do Windows: apagar o que já está no disco para só então
                // descobrir que a rede caiu deixaria o usuário sem as duas.
                downloader.prune(artifact.assetName)

                // Staging sobrado de uma tentativa anterior é lixo, não retomada:
                // uma extração interrompida não tem como ser distinguida de uma
                // completa, e o extrator recusa destino existente.
                val staging = File(layout.stagingPath(update.version))
                staging.deleteRecursively()
                extractor.extract(file, staging)
                preparedStaging = update.version to staging

                Result.success(
                    AppUpdatePreparation(
                        version = update.version,
                        assetName = artifact.assetName,
                        sizeBytes = artifact.sizeBytes
                    )
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                Result.failure(error)
            }
        }
    }

    override fun schedule(preparation: AppUpdatePreparation): Result<Unit> {
        return runCatching {
            val prepared = preparedStaging
            if (prepared == null || prepared.first != preparation.version) {
                throw IllegalStateException("No prepared staging for version ${preparation.version}.")
            }
            val staging = prepared.second
            if (!staging.isDirectory) {
                throw IllegalStateException("Prepared staging ${staging.name} is no longer on disk.")
            }
            if (support() != AppUpdateSupport.SUPPORTED) {
                throw IllegalStateException("Automatic updates stopped being available before scheduling.")
            }
            if (!isTargetUpdatable(preparation.version)) {
                throw IllegalStateException("Version ${preparation.version} is not schedulable.")
            }

            val layout = layoutProvider()
                ?: throw IllegalStateException("The managed install root could not be resolved.")
            val launcherPath = launcherPathProvider()
                ?: throw IllegalStateException("The stable launcher path could not be resolved.")

            val script = scriptMaterializer(layout.updatesDirectory)
            val logFile = logFileProvider()

            // O PID vai junto para o script poder ESPERAR este processo sair, em
            // vez de matá-lo: um `kill` durante a escrita do SQLite é pior que
            // não atualizar. É o mesmo motivo do `/PID=` do instalador Windows.
            processLauncher(
                linuxUpdaterCommand(
                    script = script,
                    rootPath = layout.rootPath,
                    version = preparation.version,
                    previousVersion = currentVersionProvider(),
                    previousPid = currentPidProvider(),
                    ackToken = ackTokenProvider(),
                    launcherPath = launcherPath,
                    ackFilePath = ackFileProvider().absolutePath,
                    receiptFilePath = receiptFileProvider().absolutePath,
                    // Mesmo arquivo do `outputFile` abaixo: o processo relançado
                    // (passo 6/9 do script) escreve nele também, em vez de
                    // `/dev/null` — ver o comentário em `linuxUpdaterCommand`.
                    logFilePath = logFile.absolutePath
                ),
                layout.updatesDirectory,
                logFile
            )
            Unit
        }
    }

    internal fun isTargetUpdatable(targetVersion: String): Boolean {
        // Alvo elegível é o que NÃO é anterior ao mínimo.
        return !isVersionNewer(minUpdatableTargetVersion, targetVersion)
    }

    /**
     * `LINUX` + `X64` + `LINUX_TARBALL` + digest, e nada mais.
     *
     * O `.deb` e o `.rpm` da mesma release são artefatos Linux x64 legítimos e
     * **não podem** entrar aqui: aplicá-los exigiria gerenciador de pacotes e
     * `sudo`, que é justamente o que esta instalação não usa. Sem digest o
     * artefato é inelegível — é o SHA-256 vindo da API por TLS que barra
     * artefato trocado.
     */
    internal fun selectArtifact(update: AppUpdateInfo): AppUpdateArtifact? {
        val architecture = currentArchitecture() ?: return null
        return update.artifacts.firstOrNull { artifact ->
            artifact.platform == AppUpdatePlatform.LINUX &&
                artifact.kind == AppUpdateArtifactKind.LINUX_TARBALL &&
                artifact.architecture == architecture &&
                artifact.sha256 != null
        }
    }

    /**
     * Arquitetura desconhecida devolve `null`, e `support()` a lê como
     * [AppUpdateSupport.UNSUPPORTED_ARCHITECTURE]. Chutar x64 numa máquina que
     * não é x64 instalaria binário errado.
     */
    private fun currentArchitecture(): AppUpdateArchitecture? {
        return when (osArchitectureProvider().lowercase()) {
            "amd64", "x86_64", "x64" -> AppUpdateArchitecture.X64
            "aarch64", "arm64" -> AppUpdateArchitecture.ARM64
            else -> null
        }
    }
}
