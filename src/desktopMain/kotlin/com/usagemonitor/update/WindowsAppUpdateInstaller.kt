package com.usagemonitor.update

import com.usagemonitor.domain.entity.AppUpdateArchitecture
import com.usagemonitor.domain.entity.AppUpdateArtifact
import com.usagemonitor.domain.entity.AppUpdateArtifactKind
import com.usagemonitor.domain.entity.AppUpdateInfo
import com.usagemonitor.domain.entity.AppUpdatePlatform
import com.usagemonitor.domain.entity.isVersionNewer
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
 * Primeira versão da release cujo `UsageMonitor-Setup-<v>.exe` entende `/UPDATE`.
 *
 * **Este gate não é precaução, é requisito.** O NSIS ignora parâmetro que não
 * reconhece: um instalador compilado antes do modo `/UPDATE`, recebendo
 * `/S /UPDATE`, faz uma instalação silenciosa comum — que cai no `MessageBox`
 * de "já instalado" do `.onInit`. E `MessageBox` sem `/SD` sob `/S` **exibe e
 * bloqueia**, medido na atividade A02: o processo fica pendurado num diálogo que
 * ninguém vê, para sempre.
 *
 * Quem decide não é a versão instalada, é a **versão baixada** — por isso a
 * comparação é contra `update.version`, não contra a versão em execução.
 *
 * `38.0.0` é a primeira release publicada com o `/UPDATE` no `.nsi`; a v37 e as
 * anteriores têm o instalador que trava. **Este número está amarrado à tag que
 * sair desta branch**: se a release for publicada com outro número, ele muda
 * junto. Errar para cima é inócuo — a app só enxerga versões mais novas que a
 * dela —, mas errar para baixo reabre o caminho travado, e é isso que o teste de
 * `AutoUpdateWiringTest` impede.
 */
internal const val MIN_UPDATABLE_TARGET_VERSION = "38.0.0"

/**
 * Última release cujo `Setup.exe` **não** entende `/UPDATE`.
 *
 * Não é usada em tempo de execução: existe para o portão de release afirmar, em
 * teste, que [MIN_UPDATABLE_TARGET_VERSION] está acima dela. É a única direção
 * de erro que é destrutiva.
 */
internal const val LAST_VERSION_WITHOUT_UPDATE_MODE = "37.0.0"

class WindowsAppUpdateInstaller(
    httpClient: HttpClient,
    private val updatesDirectory: File = defaultUpdatesDirectory(),
    private val downloader: UpdateArtifactDownloader = UpdateArtifactDownloader(httpClient, updatesDirectory),
    private val originProvider: () -> WindowsInstallOrigin = { WindowsInstallOriginResolver.current() },
    private val osNameProvider: () -> String = { System.getProperty("os.name").orEmpty() },
    private val osArchitectureProvider: () -> String = { System.getProperty("os.arch").orEmpty() },
    private val currentPidProvider: () -> Long = { ProcessHandle.current().pid() },
    /**
     * Devolve `Unit` e não `Process` de propósito: ninguém espera nem inspeciona
     * o processo lançado — o app está saindo. Um retorno que ninguém usa
     * obrigaria o teste a criar processo de verdade só para satisfazer o tipo.
     */
    private val processLauncher: (List<String>, File?) -> Unit = ::launchDetachedProcess,
    private val minUpdatableTargetVersion: String = MIN_UPDATABLE_TARGET_VERSION,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : AppUpdateInstaller {

    /**
     * Caminho do artefato já conferido, por versão.
     *
     * `@Volatile` porque `prepare` escreve numa corrotina e `schedule` lê na
     * thread do encerramento — o mesmo motivo pelo qual `scheduledRefreshAt`
     * é volátil no `DashboardViewModel`.
     */
    @Volatile
    private var preparedFile: Pair<String, File>? = null

    override fun support(): AppUpdateSupport {
        if (!osNameProvider().lowercase().contains("win")) {
            return AppUpdateSupport.UNSUPPORTED_PLATFORM
        }
        return when (originProvider()) {
            WindowsInstallOrigin.NSIS_PER_USER -> AppUpdateSupport.SUPPORTED
            WindowsInstallOrigin.UNMANAGED -> AppUpdateSupport.UNSUPPORTED_INSTALL_ORIGIN
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
                        "Version ${update.version} predates the installer that understands /UPDATE " +
                            "(minimum $minUpdatableTargetVersion)."
                    )
                }

                val artifact = selectArtifact(update)
                    ?: throw IllegalStateException("No verifiable NSIS installer was published for this platform.")

                val file = downloader.download(artifact, onProgress)
                // A poda vem depois do download, não antes: apagar o que já está
                // no disco para só então descobrir que a rede caiu deixaria o
                // usuário sem a versão anterior e sem a nova.
                downloader.prune(artifact.assetName)
                preparedFile = update.version to file

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
            val prepared = preparedFile
            if (prepared == null || prepared.first != preparation.version) {
                throw IllegalStateException("No prepared artifact for version ${preparation.version}.")
            }
            val file = prepared.second
            if (!file.isFile) {
                throw IllegalStateException("Prepared artifact ${file.name} is no longer on disk.")
            }
            if (support() != AppUpdateSupport.SUPPORTED) {
                throw IllegalStateException("Automatic updates stopped being available before scheduling.")
            }
            if (!isTargetUpdatable(preparation.version)) {
                throw IllegalStateException("Version ${preparation.version} is not schedulable.")
            }

            // O PID vai junto para o instalador poder ESPERAR este processo sair,
            // em vez de matá-lo. `taskkill /F` durante a escrita do SQLite é pior
            // que não atualizar.
            processLauncher(
                listOf(file.absolutePath, "/S", "/UPDATE", "/PID=${currentPidProvider()}"),
                file.parentFile
            )
            Unit
        }
    }

    internal fun isTargetUpdatable(targetVersion: String): Boolean {
        // Alvo elegível é o que NÃO é anterior ao mínimo.
        return !isVersionNewer(minUpdatableTargetVersion, targetVersion)
    }

    internal fun selectArtifact(update: AppUpdateInfo): AppUpdateArtifact? {
        val architecture = currentArchitecture() ?: return null
        return update.artifacts.firstOrNull { artifact ->
            artifact.platform == AppUpdatePlatform.WINDOWS &&
                artifact.kind == AppUpdateArtifactKind.WINDOWS_NSIS &&
                artifact.architecture == architecture &&
                artifact.sha256 != null
        }
    }

    /**
     * Arquitetura desconhecida devolve `null` e nenhum artefato é escolhido.
     * Chutar x64 numa máquina que não é x64 instalaria binário errado.
     */
    private fun currentArchitecture(): AppUpdateArchitecture? {
        return when (osArchitectureProvider().lowercase()) {
            "amd64", "x86_64", "x64" -> AppUpdateArchitecture.X64
            "aarch64", "arm64" -> AppUpdateArchitecture.ARM64
            else -> null
        }
    }
}

/** `internal` porque a poda de abertura precisa do mesmo caminho, e dois resolvedores divergiriam. */
internal fun defaultUpdatesDirectory(): File {
    val home = System.getProperty("user.home") ?: error("user.home is unavailable")
    return File(home, ".usage-monitor/updates")
}

/**
 * Processo solto: nem herda os fluxos do app nem é esperado por ele. O app está
 * saindo, e o instalador precisa sobreviver a essa saída.
 */
/**
 * Lança o instalador desacoplado deste processo, que está saindo.
 *
 * `Redirect.DISCARD` é um redirect de **escrita**, e `redirectInput` exige um de
 * leitura: passar `DISCARD` ali lança `IllegalArgumentException("Redirect invalid
 * for reading: WRITE")` **antes** de `start()` — o processo nunca chega a ser
 * criado. Era o defeito medido na A20: a atualização automática falhava em 100%
 * das tentativas, para todo usuário, e em silêncio.
 *
 * A entrada vai para o **dispositivo nulo do sistema**, resolvido por SO.
 * `Redirect.INHERIT` herdaria de um processo GUI sem console, e `NUL` fixo — que
 * era o que estava aqui — não existe no Linux: o `ProcessBuilder` lançaria
 * `IOException` ao abrir o arquivo, e o updater nunca seria criado.
 *
 * A saída pode ir para um arquivo: é assim que o `linux-updater.sh` escreve o
 * log dele, sem que o script precise conhecer o caminho. `null` mantém o
 * descarte, que é o que o instalador do Windows quer.
 */
internal fun launchDetachedProcess(
    command: List<String>,
    directory: File?,
    outputFile: File? = null
) {
    val output = if (outputFile == null) {
        ProcessBuilder.Redirect.DISCARD
    } else {
        outputFile.parentFile?.mkdirs()
        ProcessBuilder.Redirect.appendTo(outputFile)
    }

    val builder = ProcessBuilder(command)
        .redirectInput(ProcessBuilder.Redirect.from(nullInputDevice()))
        .redirectOutput(output)
        .redirectError(output)
    if (directory != null) {
        builder.directory(directory)
    }
    builder.start()
}

/**
 * `NUL` no Windows, `/dev/null` no resto.
 *
 * Não é detalhe portátil: o `Redirect.from` **abre o arquivo**, e um `NUL` num
 * Linux seria um caminho relativo inexistente.
 */
internal fun nullInputDevice(
    osName: String = System.getProperty("os.name").orEmpty()
): File {
    return if (osName.lowercase().contains("win")) File("NUL") else File("/dev/null")
}
