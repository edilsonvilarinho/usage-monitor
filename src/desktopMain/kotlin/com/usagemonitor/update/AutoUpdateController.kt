package com.usagemonitor.update

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import com.russhwolf.settings.PreferencesSettings
import com.usagemonitor.CURRENT_APP_VERSION
import com.usagemonitor.data.repository.UPDATE_FEED_URL_ENV_VAR
import com.usagemonitor.domain.entity.AppUpdatePlatform
import com.usagemonitor.domain.entity.AppUpdateReceipt
import com.usagemonitor.domain.entity.shouldDiscardUpdateArtifacts
import com.usagemonitor.domain.repository.AppUpdateInstaller
import com.usagemonitor.domain.repository.AppUpdateSupport
import com.usagemonitor.persistAutoUpdateEnabled
import com.usagemonitor.readPersistedAutoUpdateEnabled
import io.ktor.client.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext

/**
 * Se **esta build** traz o mecanismo de atualização automática.
 *
 * Falso enquanto o instalador NSIS não entender `/UPDATE` (atividade A16 do
 * plano). Com ele falso, `installer` é nulo e nenhum caminho de código lança
 * instalador nenhum — a decisão 8 do plano deixa de poder virar acidente.
 *
 * A atividade A19 vira este valor **junto** com [MIN_UPDATABLE_TARGET_VERSION],
 * e há um teste que reprova a combinação inconsistente.
 *
 * Ligado na A19, depois de o `.nsi` entender `/UPDATE` (A16), dos seis cenários
 * do instalador passarem (A17) e de a #78 fechar o falso positivo do portão de
 * origem — sem aquele, uma instalação MSI passava como `NSIS_PER_USER` e a
 * atualização automática rodaria sobre uma árvore do Windows Installer.
 */
internal const val AUTO_UPDATE_SHIPPED = true

/**
 * Se **esta build** traz a atualização automática do Linux.
 *
 * **A14 concluída em 2026-08-29**, numa Bazzite real (issue #121): com a v38.0.1
 * publicada já trazendo os dois fixes (symlink ostree em `LinuxInstallOrigin` e
 * `LD_LIBRARY_PATH` obsoleto no `linux-updater.sh`), um binário local com esta
 * flag ligada detectou a v38.0.1 no feed real do GitHub, baixou o tarball
 * verdadeiro, promoveu, relançou e recebeu o ACK — `~/.usage-monitor/diagnostics/
 * linux-update.log` registrou `OK promoted 38.0.1` e o recibo saiu
 * `status=success`, a primeira vez que o ciclo inteiro fechou sem rollback.
 * Antes disso ficava em `false` pelo mesmo motivo que manteve o caminho do
 * Windows inerte até o `.nsi` entender `/UPDATE`: com ele falso, `installer` é
 * nulo no Linux e não existe caminho de código que extraia tarball ou troque
 * diretório de instalação — um mecanismo que ainda não tinha sido exercitado
 * numa máquina real não podia virar acidente.
 *
 * Vira **junto** com [MIN_LINUX_UPDATABLE_TARGET_VERSION], e
 * `AutoUpdateWiringTest` reprova a combinação inconsistente nos dois sentidos.
 */
internal const val LINUX_AUTO_UPDATE_SHIPPED = true

/**
 * Tudo que a janela principal precisa saber sobre atualização automática.
 *
 * Existe como classe própria, e não como mais um punhado de `remember` dentro do
 * `main()`, porque aquele composable já está no limite do backend JVM: a análise
 * de fluxo de controle sobre o método inteiro estoura em `OutOfMemoryError`
 * dentro do ASM. O `main()` ganha **uma** chamada.
 */
internal class AutoUpdateController(
    val installer: AppUpdateInstaller?,
    /**
     * Plataforma em execução, ou `null` quando o app não a reconhece.
     *
     * Vai para a tela porque dois dos motivos de indisponibilidade nomeiam o
     * instalador da plataforma: `.exe` no Windows, `.sh` em user-space no Linux.
     * Sem ela o texto continuaria dizendo "MSI" numa máquina Linux.
     */
    val platform: AppUpdatePlatform?,
    val enabled: MutableStateFlow<Boolean>,
    val lastReceipt: AppUpdateReceipt?,
    /** Valor de USAGE_MONITOR_UPDATE_FEED_URL, quando definida. */
    val feedUrlOverride: String?,
    private val persist: (Boolean) -> Unit
) {

    /**
     * Sem instalador, o motivo ainda depende da plataforma.
     *
     * `UNAVAILABLE` no Windows e no Linux é literal — a build não traz o
     * mecanismo, ou não o traz ainda. No macOS seria **falso**: ali não é a
     * build que falta, é o pacote que não é assinado, e o Gatekeeper exige
     * liberação manual. Colapsar os dois faria a tela prometer que uma versão
     * futura resolveria o que não é problema de versão.
     */
    val support: AppUpdateSupport
        get() = installer?.support() ?: when (platform) {
            AppUpdatePlatform.MACOS, null -> AppUpdateSupport.UNSUPPORTED_PLATFORM
            AppUpdatePlatform.WINDOWS, AppUpdatePlatform.LINUX -> AppUpdateSupport.UNAVAILABLE
        }

    fun setEnabled(value: Boolean) {
        enabled.value = value
        persist(value)
    }

    /**
     * Encerramento ordenado, ligado depois da construção.
     *
     * A indireção existe porque o `DashboardViewModel` é criado antes de o
     * `main()` montar a rotina de saída, e a alternativa seria mais um estado
     * mutável dentro do composable que não pode crescer.
     */
    private var restartAction: () -> Unit = {}

    fun bindRestart(action: () -> Unit) {
        restartAction = action
    }

    fun requestRestart() {
        restartAction()
    }
}

/**
 * Valor corrente do interruptor, como estado de composição.
 *
 * Função de extensão em vez de um `collectAsState` no `main()`: ali não pode
 * entrar mais estado, e uma chamada não é estado.
 */
@Composable
internal fun AutoUpdateController.isEnabled(): Boolean = enabled.collectAsState().value

@Composable
internal fun rememberAutoUpdateController(
    settings: PreferencesSettings,
    httpClient: HttpClient
): AutoUpdateController {
    val controller = remember(settings, httpClient) {
        val platform = currentUpdatePlatform()
        // Um instalador por plataforma, e nenhum onde o mecanismo não foi
        // exercitado. O instalador do Windows respondia `UNSUPPORTED_PLATFORM`
        // fora do Windows e por isso podia ser construído em qualquer lugar;
        // manter isso com dois instaladores significaria construir o errado e
        // depender de ele se recusar.
        val installer = when (platform) {
            AppUpdatePlatform.WINDOWS ->
                if (AUTO_UPDATE_SHIPPED) WindowsAppUpdateInstaller(httpClient = httpClient) else null

            AppUpdatePlatform.LINUX ->
                if (LINUX_AUTO_UPDATE_SHIPPED) LinuxAppUpdateInstaller(httpClient = httpClient) else null

            AppUpdatePlatform.MACOS, null -> null
        }
        AutoUpdateController(
            installer = installer,
            platform = platform,
            enabled = MutableStateFlow(readPersistedAutoUpdateEnabled(settings)),
            // Lido uma vez, na abertura: o recibo é escrito pelo instalador
            // enquanto o app está fechado, e reler a cada recomposição seria
            // I/O de disco para um valor que não muda com a janela aberta.
            lastReceipt = readUpdateReceipt(),
            feedUrlOverride = System.getenv(UPDATE_FEED_URL_ENV_VAR),
            persist = { value -> persistAutoUpdateEnabled(settings, value) }
        )
    }

    // Poda do artefato já aplicado. Mora aqui, e não no `main()`, porque aquele
    // composable está no limite do backend JVM e não pode receber estado novo;
    // e num LaunchedEffect, e não no corpo do `remember` acima, porque apagar
    // arquivo é escrita e escrita não vai na thread de composição.
    //
    // Independe do interruptor: o artefato já foi aplicado, e desligar a
    // atualização automática depois disso não o torna útil de novo. Idempotente,
    // então não há marcador a guardar — na abertura seguinte não sobra nada.
    LaunchedEffect(controller) {
        if (shouldDiscardUpdateArtifacts(controller.lastReceipt, CURRENT_APP_VERSION)) {
            withContext(Dispatchers.IO) {
                pruneUpdateArtifacts(defaultUpdatesDirectory(), keepAssetName = null)
            }
        }
    }

    return controller
}

/**
 * Plataforma em execução, no vocabulário dos artefatos de release.
 *
 * `null` para o que não se reconhece — e não um chute em Windows. O valor
 * escolhe qual instalador o texto da tela vai nomear, e nomear o errado é pior
 * que não nomear nenhum.
 */
internal fun currentUpdatePlatform(
    osName: String = System.getProperty("os.name").orEmpty()
): AppUpdatePlatform? {
    val name = osName.lowercase()
    // macOS vem ANTES de Windows: "darwin" contém "win", e com a ordem trocada
    // um sistema Darwin seria classificado como Windows. Medido — o teste
    // `mac names are recognized in both spellings` reprova a ordem invertida.
    // O `os.name` de um JDK em macOS é "Mac OS X" e nunca "Darwin", então o
    // defeito seria latente e invisível até deixar de ser.
    return when {
        name.contains("mac") || name.contains("darwin") -> AppUpdatePlatform.MACOS
        name.contains("win") -> AppUpdatePlatform.WINDOWS
        name.contains("linux") -> AppUpdatePlatform.LINUX
        else -> null
    }
}
