package com.usagemonitor.presentation.viewmodel

import com.usagemonitor.domain.entity.AppUpdateInfo
import com.usagemonitor.domain.entity.breadcrumbFailureReasonOf
import com.usagemonitor.domain.repository.BreadcrumbRecorder
import com.usagemonitor.domain.repository.AppUpdateInstaller
import com.usagemonitor.domain.repository.AppUpdatePreparation
import com.usagemonitor.domain.repository.AppUpdateSupport
import com.usagemonitor.domain.usecase.CheckForAppUpdateUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * A atualização automática do app, fora do [DashboardViewModel] (#304).
 *
 * É um estado próprio — versão em voo, artefato preparado, espera entre
 * tentativas — que não conversa com a coleta das cotas: o view model só lhe
 * empresta o escopo, o relógio, a configuração e a trilha, e publica o
 * [state] que a tela já lia dele.
 */
internal class DashboardUpdateCoordinator(
    private val checkForAppUpdate: CheckForAppUpdateUseCase?,
    private val appUpdateReleaseOpener: AppUpdateReleaseOpener,
    private val appUpdateInstaller: AppUpdateInstaller?,
    private val autoUpdateEnabled: StateFlow<Boolean>,
    private val onRestartAndUpdateRequested: () -> Unit,
    private val onUpdateScheduleFailure: (String, String) -> Unit,
    private val currentAppVersion: String,
    private val clock: Clock,
    private val config: DashboardViewModelConfig,
    private val breadcrumbs: BreadcrumbRecorder,
    private val scope: CoroutineScope,
    private val onReleasePageError: (String) -> Unit
) {
    private val _state = MutableStateFlow<AppUpdateUiState?>(null)
    val state: StateFlow<AppUpdateUiState?> = _state.asStateFlow()

    private val updateMutex = Mutex()
    private var lastUpdateCheckFailureKey: String? = null

    /**
     * Artefato já baixado e conferido, esperando o encerramento.
     *
     * `@Volatile` porque quem escreve é uma corrotina e quem lê é a thread do
     * shutdown hook — mesmo motivo do `scheduledRefreshAt` acima.
     */
    @Volatile private var preparedUpdate: AppUpdatePreparation? = null

    /**
     * Versão **em voo**, e não só a preparada.
     *
     * É esta variável que impede o poll de 10 min de cancelar e reiniciar um
     * download em andamento: durante o download `preparedUpdate` é nulo, e
     * comparar só por ele fazia o ciclo recomeçar do zero a cada passada — um
     * download de 120 MB que levasse mais de 10 min nunca terminaria.
     */
    @Volatile private var downloadingVersion: String? = null
    private var updateDownloadJob: Job? = null

    /**
     * Espera antes de tentar de novo a mesma versão.
     *
     * Sem ela, uma falha recorrente rebaixaria 120 MB a cada 10 min — ~17 GB por
     * dia, indefinidamente. Zerada quando a versão anunciada muda: release nova
     * é uma tentativa nova.
     */
    private var updateBackoff: UpdateBackoff? = null

    private data class UpdateBackoff(
        val version: String,
        val attempts: Int,
        val retryAfter: Instant,
        val reason: AppUpdateFailureReason
    )

    fun startCheckLoop() {
        scope.launch {
            checkForUpdate()
            while (true) {
                delay(config.updateCheckIntervalWhileRunning)
                checkForUpdate()
            }
        }
    }

    fun openReleasePage() {
        // Qualquer estado com versão anunciada abre a página: a faixa oferece o
        // caminho manual também depois de a atualização automática falhar.
        val update = _state.value?.update ?: return
        appUpdateReleaseOpener.open(update.releasePageUrl)
            .onFailure { error ->
                breadcrumbs.recordFailure("abrir página da versão", error)
                onReleasePageError(error.message ?: "Unknown error")
            }
    }

    suspend fun checkForUpdate() {
        val updateUseCase = checkForAppUpdate ?: return

        updateMutex.withLock {
            updateUseCase(currentAppVersion)
                .onSuccess { update ->
                    lastUpdateCheckFailureKey = null
                    if (update == null) {
                        forgetPendingUpdate()
                        _state.value = null
                        return@onSuccess
                    }

                    onUpdateAnnounced(update)
                }
                .onFailure { error ->
                    // Falha silenciosa: UI mantém estado anterior; próxima janela de poll tenta de novo.
                    val failureKey = breadcrumbFailureReasonOf(error)
                    if (failureKey != lastUpdateCheckFailureKey) {
                        lastUpdateCheckFailureKey = failureKey
                        breadcrumbs.recordFailure("consultar atualização do app", error)
                    }
                }
        }
    }

    /**
     * Decide o que fazer com a versão anunciada. Chamada a cada passada do laço
     * de verificação, então a ordem das guardas é o que impede trabalho repetido.
     */
    private fun onUpdateAnnounced(update: AppUpdateInfo) {
        // Release diferente da que estava em curso: o que foi baixado ou falhou
        // antes não descreve mais nada.
        if (trackedVersion() != null && trackedVersion() != update.version) {
            forgetPendingUpdate()
        }

        val prepared = preparedUpdate
        if (prepared != null && prepared.version == update.version) {
            _state.value = AppUpdateUiState.Ready(update)
            return
        }

        // Download em voo da mesma versão: não recomeçar. Esta é a guarda que
        // faltava e que fazia o poll de 10 min reiniciar o download do zero.
        if (downloadingVersion == update.version && updateDownloadJob?.isActive == true) {
            return
        }

        if (!canDownloadAutomatically()) {
            _state.value = AppUpdateUiState.Available(update)
            return
        }

        val backoff = updateBackoff
        if (backoff != null && backoff.version == update.version) {
            if (clock.now() < backoff.retryAfter) {
                // Continua mostrando a falha: a versão instalada está intacta e o
                // caminho manual segue oferecido na faixa.
                _state.value = AppUpdateUiState.Failed(update, backoff.reason)
                return
            }
            if (backoff.attempts >= config.updateRetryBackoff.size) {
                _state.value = AppUpdateUiState.Failed(update, backoff.reason)
                return
            }
        }

        startUpdateDownload(update)
    }

    private fun canDownloadAutomatically(): Boolean {
        val installer = appUpdateInstaller ?: return false
        if (!autoUpdateEnabled.value) {
            return false
        }
        return installer.support() == AppUpdateSupport.SUPPORTED
    }

    private fun trackedVersion(): String? {
        return downloadingVersion ?: preparedUpdate?.version ?: updateBackoff?.version
    }

    private fun startUpdateDownload(update: AppUpdateInfo) {
        val installer = appUpdateInstaller ?: return

        downloadingVersion = update.version
        _state.value = AppUpdateUiState.Downloading(update, percent = null)

        updateDownloadJob = scope.launch {
            // O percentual só é publicado quando o número inteiro muda: são ~1900
            // blocos de 64 KB em 120 MB, e emitir a cada bloco faria a tela
            // recompor duas mil vezes para mostrar a mesma dezena.
            var lastPublishedPercent = -1
            val result = installer.prepare(update) { downloadedBytes, totalBytes ->
                val percent = percentOf(downloadedBytes, totalBytes)
                if (percent != null && percent != lastPublishedPercent) {
                    lastPublishedPercent = percent
                    _state.value = AppUpdateUiState.Downloading(update, percent)
                }
            }

            downloadingVersion = null
            result
                .onSuccess { preparation ->
                    preparedUpdate = preparation
                    updateBackoff = null
                    _state.value = AppUpdateUiState.Ready(update)
                }
                .onFailure { error ->
                    breadcrumbs.recordFailure("baixar atualização do app", error)
                    registerUpdateFailure(update, AppUpdateFailureReason.DOWNLOAD)
                }
        }
    }

    private fun percentOf(downloadedBytes: Long, totalBytes: Long?): Int? {
        if (totalBytes == null || totalBytes <= 0L) {
            return null
        }
        return ((downloadedBytes * 100L) / totalBytes).toInt().coerceIn(0, 100)
    }

    private fun registerUpdateFailure(update: AppUpdateInfo, reason: AppUpdateFailureReason) {
        val previousAttempts = updateBackoff?.takeIf { it.version == update.version }?.attempts ?: 0
        val attempts = previousAttempts + 1
        val waitFor = config.updateRetryBackoff.getOrNull(attempts - 1)
            ?: config.updateRetryBackoff.last()

        updateBackoff = UpdateBackoff(
            version = update.version,
            attempts = attempts,
            retryAfter = clock.now() + waitFor,
            reason = reason
        )
        _state.value = AppUpdateUiState.Failed(update, reason)
    }

    private fun forgetPendingUpdate() {
        updateDownloadJob?.cancel()
        updateDownloadJob = null
        downloadingVersion = null
        preparedUpdate = null
        updateBackoff = null
    }

    /**
     * Reage ao interruptor das Configurações, **nos dois sentidos**.
     *
     * Desligar no meio do download cancela o job **e descarta o que já estava
     * pronto**: um artefato preparado seria aplicado no encerramento, que é
     * exatamente o que o usuário acabou de recusar.
     *
     * Ligar reavalia a versão que a tela já está anunciando. Sem isso o
     * interruptor parece inerte: quem o liga com a faixa de "nova versão" na tela
     * fica olhando para ela sem nada acontecer até o poll seguinte, que pode
     * estar a 10 minutos de distância. Medido na atividade A20, com o usuário
     * ligando o interruptor e relatando que o download não começou.
     *
     * `onUpdateAnnounced` é o mesmo caminho do poll, e não um segundo: ele já
     * carrega as guardas de download em voo, de artefato pronto e de backoff.
     */
    fun startAutoUpdateSwitchWatcher() {
        scope.launch {
            autoUpdateEnabled.collect { enabled ->
                val current = _state.value
                if (enabled) {
                    if (current != null) {
                        onUpdateAnnounced(current.update)
                    }
                    return@collect
                }
                forgetPendingUpdate()
                if (current != null) {
                    _state.value = AppUpdateUiState.Available(current.update)
                }
            }
        }
    }

    /**
     * Entrega o pacote ao sistema. Chamada no encerramento, depois de o resto do
     * app ter fechado — o instalador espera este processo sair de qualquer forma,
     * mas a ordem correta não custa nada.
     */
    fun scheduleOnExit() {
        val installer = appUpdateInstaller ?: return
        if (!autoUpdateEnabled.value) {
            return
        }
        val preparation = preparedUpdate ?: return
        // O Result não pode ser descartado aqui. Este é o último ponto do
        // processo em que ainda se sabe alguma coisa: se a entrega falhar, o
        // instalador não roda, não escreve recibo, e o usuário vê o app fechar e
        // não voltar — sem uma linha no disco explicando.
        installer.schedule(preparation).onFailure { error ->
            breadcrumbs.recordFailure("agendar instalação da atualização", error)
            val reason = error.message?.takeIf { it.isNotBlank() }
                ?: error::class.simpleName
                ?: "unknown"
            onUpdateScheduleFailure(preparation.version, reason)
        }
    }

    /** Ação da faixa no estado pronto. Sem artefato preparado não faz nada. */
    fun restartAndUpdateNow() {
        if (appUpdateInstaller == null || preparedUpdate == null) {
            return
        }
        onRestartAndUpdateRequested()
    }
}
