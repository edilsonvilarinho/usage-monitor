package com.usagemonitor.presentation.viewmodel

import com.usagemonitor.domain.entity.CliQuotaWindows
import com.usagemonitor.domain.entity.HudSessionSummary
import com.usagemonitor.domain.usecase.GetHudSessionSummaryUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Cadência da leitura, igual à do semáforo de sessão que já indexa nesse ritmo. */
private const val DEFAULT_INTERVAL_MILLIS = 30_000L

/**
 * O rodapé de consumo da barra HUD (issue #164).
 *
 * **Laço próprio, e não uma carona no `SessionPulseViewModel`.** Os dois leem o
 * índice em 30s, mas respondem perguntas diferentes: aquele pergunta "que sessão
 * precisa de atenção" e publica pulsos; este pergunta "quanto foi queimado na
 * janela". Enfiar o segundo no primeiro misturaria os dois contratos e faria o
 * pisca do card depender de uma consulta que ele não usa.
 *
 * **Só lê com o HUD na tela** ([isEnabled]). É a diferença que justifica o laço
 * extra: fora do modo HUD ninguém observa este número, e o `collectLatest`
 * cancela a espera em vez de continuar consultando o índice. Ligar o modo
 * dispara uma leitura **imediata** — esperar até 30s pelo primeiro número faria
 * o rodapé nascer vazio toda vez.
 *
 * **Leitura que falha mantém os números anteriores**, mesma regra do resumo por
 * eixo: o índice local falha por arquivo em escrita, e apagar a linha a cada
 * tropeço seria pior que mostrar o valor de trinta segundos atrás.
 */
class HudSummaryViewModel(
    private val getSummary: GetHudSessionSummaryUseCase,
    /** `true` enquanto a barra HUD estiver na tela. */
    private val isEnabled: StateFlow<Boolean>,
    private val quotaWindowsProvider: () -> CliQuotaWindows = { CliQuotaWindows() },
    private val intervalMillis: Long = DEFAULT_INTERVAL_MILLIS,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    autoStart: Boolean = true
) {
    private val viewModelScope = CoroutineScope(SupervisorJob() + dispatcher)
    private var loopJob: Job? = null

    private val _summary = MutableStateFlow<HudSessionSummary?>(null)

    /** `null` antes da primeira leitura: o rodapé não é composto. */
    val summary: StateFlow<HudSessionSummary?> = _summary.asStateFlow()

    init {
        if (autoStart) {
            start()
        }
    }

    fun start() {
        if (loopJob != null) {
            return
        }

        loopJob = viewModelScope.launch {
            isEnabled.collectLatest { enabled ->
                if (!enabled) {
                    return@collectLatest
                }

                while (isActive) {
                    refreshOnce()
                    delay(intervalMillis)
                }
            }
        }
    }

    suspend fun refreshOnce() {
        getSummary(quotaWindowsProvider()).onSuccess { value ->
            _summary.value = value
        }
    }

    fun onDestroy() {
        loopJob?.cancel()
        loopJob = null
        viewModelScope.cancel()
    }
}
