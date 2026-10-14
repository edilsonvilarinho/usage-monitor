package com.usagemonitor.presentation.viewmodel

import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.ui.TelegramAnalysisImageLabels
import com.usagemonitor.presentation.ui.UsageExportPayload
import com.usagemonitor.presentation.ui.UsageExportRequest
import com.usagemonitor.domain.entity.SessionAnalysis
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlin.time.Instant

sealed interface AnalysisUiState {
    data object Loading : AnalysisUiState
    /** A conta ainda não tem histórico, ou saiu do painel desde o clique. */
    data object Empty : AnalysisUiState
    data class Success(
        val analysis: SessionAnalysis,
        /** Resultado do "Relatório PDF" (B21b); `null` sem exportação ou com o diálogo cancelado. */
        val exportOutcome: CliExportOutcome? = null,
        /**
         * Quando a última releitura ao vivo falhou; os números continuam os da [analysis]
         * (`generatedAt`). `null` = a leitura mostrada é a mais recente.
         */
        val liveFailedAt: Instant? = null
    ) : AnalysisUiState
}

/**
 * Janela "Análise" de uma conta (rodada AB, B20): a mesma análise do `/analise` do bot.
 *
 * Ao vivo enquanto a janela está visível (rodada AD, A01): relê a cada leitura nova da conta
 * ([freshness] muda — o host passa o `fetchedAt` dela) e a cada [liveIntervalMillis], porque
 * ritmo da última hora, ritmo seguro e fatias andam com o relógio mesmo sem coleta nova.
 * [close] para o laço: a janela de modal só se esconde, e escondida não lê nada.
 * [load] recebe o `storageKey` da conta e vem de quem tem o histórico e o índice CLI —
 * a apresentação não importa `data`.
 */
class AnalysisViewModel(
    private val load: suspend (targetKey: String) -> SessionAnalysis?,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    /** Grava o PDF (B21b); `null` esconde o botão. */
    private val exportWriter: UsageExportWriter? = null,
    /** O PDF de uma página do `/analise pdf`, montado em `desktopMain` (PDFBox é JVM). */
    private val pdfOf: (suspend (SessionAnalysis, AppLanguage) -> ByteArray)? = null,
    /** Muda a cada leitura nova da conta; qualquer valor serve, só a troca importa. */
    private val freshness: (targetKey: String) -> Flow<Any?> = { emptyFlow() },
    private val liveIntervalMillis: Long = LIVE_INTERVAL_MILLIS,
    private val clock: () -> Instant = { kotlin.time.Clock.System.now() }
) {
    /** Há como exportar? Decide se a janela mostra "Relatório PDF". */
    val canExport: Boolean
        get() = exportWriter != null && pdfOf != null

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private var loadJob: Job? = null
    private var liveJob: Job? = null
    private var targetKey: String? = null

    private val _uiState = MutableStateFlow<AnalysisUiState>(AnalysisUiState.Loading)
    val uiState: StateFlow<AnalysisUiState> = _uiState.asStateFlow()

    private val _accountLabel = MutableStateFlow<String?>(null)
    val accountLabel: StateFlow<String?> = _accountLabel.asStateFlow()

    fun openForAccount(targetKey: String, accountLabel: String) {
        this.targetKey = targetKey
        _accountLabel.value = accountLabel
        _uiState.value = AnalysisUiState.Loading
        refresh()
        startLive(targetKey)
    }

    /** A janela se escondeu: nada de releitura até a próxima abertura. */
    fun close() {
        liveJob?.cancel()
        liveJob = null
    }

    private fun startLive(key: String) {
        liveJob?.cancel()
        val ticks = flow {
            while (true) {
                delay(liveIntervalMillis)
                emit(Unit)
            }
        }
        // Sem `drop(1)` no primeiro valor: se a coleta trocar o sinal entre a abertura e a
        // inscrição, o `StateFlow` entrega só o valor novo e ele seria descartado. O custo é
        // uma releitura a mais na abertura (~20 ms de SQL).
        val readings = freshness(key).distinctUntilChanged().map { }
        liveJob = scope.launch {
            merge(ticks, readings).collect { refresh() }
        }
    }

    private fun refresh() {
        val key = targetKey ?: return
        loadJob?.cancel()
        loadJob = scope.launch {
            val analysis = try {
                load(key)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                null
            }
            val shown = _uiState.value
            _uiState.value = when {
                analysis != null -> if (shown is AnalysisUiState.Success) shown.copy(analysis = analysis, liveFailedAt = null) else AnalysisUiState.Success(analysis)
                // Leitura que falha mantém os números anteriores e publica só a falha.
                shown is AnalysisUiState.Success -> shown.copy(liveFailedAt = clock())
                else -> AnalysisUiState.Empty
            }
        }
    }

    /**
     * "Relatório PDF" (AB15 no app): o mesmo arquivo do bot, com o mesmo nome. Cancelar o
     * diálogo não é falha — nada é publicado.
     */
    fun exportPdf(language: AppLanguage) {
        val writer = exportWriter ?: return
        val build = pdfOf ?: return
        val current = _uiState.value as? AnalysisUiState.Success ?: return
        scope.launch {
            val outcome = try {
                val bytes = build(current.analysis, language)
                val name = TelegramAnalysisImageLabels.reportFileName(current.analysis, language)
                writer.write(UsageExportRequest(name, UsageExportPayload.Binary(bytes)))?.let { path -> CliExportOutcome.Saved(path) }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                CliExportOutcome.Failed(error.message ?: error::class.simpleName.orEmpty())
            } ?: return@launch
            val latest = _uiState.value as? AnalysisUiState.Success ?: return@launch
            _uiState.value = latest.copy(exportOutcome = outcome)
        }
    }

    fun onDestroy() {
        scope.cancel()
    }

    companion object {
        /** O ritmo do índice do Claude (`SessionPulseViewModel`); a releitura custa ~20 ms de SQL (plano AD, D6). */
        const val LIVE_INTERVAL_MILLIS = 30_000L
    }
}
