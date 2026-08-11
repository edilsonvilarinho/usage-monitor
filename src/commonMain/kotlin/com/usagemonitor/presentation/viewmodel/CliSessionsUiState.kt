package com.usagemonitor.presentation.viewmodel

import com.usagemonitor.domain.entity.CliSessionRange
import com.usagemonitor.domain.entity.CliSessionSummary
import com.usagemonitor.domain.usecase.CliSessionDetailResult
import kotlinx.datetime.Instant

sealed interface CliSessionsUiState {

    data object Loading : CliSessionsUiState

    data class Error(
        val message: String,
        val range: CliSessionRange = CliSessionRange.DEFAULT,
        val profileLabel: String? = null
    ) : CliSessionsUiState

    data class Success(
        /** Sessões já recortadas por [range]: os agregados são os da janela. */
        val sessions: List<CliSessionSummary>,
        val range: CliSessionRange = CliSessionRange.DEFAULT,
        /** Fim da janela de quota quando o corte está ancorado nela. */
        val rangeEndsAt: Instant? = null,
        /** `true` quando o corte veio do reset da quota, não do relógio. */
        val rangeAnchored: Boolean = false,
        /** Conta Anthropic dona destas sessões; nula na visão agregada. */
        val profileLabel: String? = null,
        /** Falha de indexação que não impediu a leitura do índice já gravado. */
        val indexWarning: String? = null,
        /** `null` mostra a lista; qualquer outro valor mostra o detalhe. */
        val detail: CliSessionDetailUiState? = null,
        /**
         * Quando o conteúdo da lista mudou pela última vez.
         *
         * Não é o instante da última varredura: com a atualização ao vivo a
         * varredura acontece de segundos em segundos e carimbá-la aqui quebraria a
         * igualdade do estado, recompondo a tela sem nada ter mudado.
         */
        val lastChangedAt: Instant? = null,
        /**
         * Se o bloco Avançado do detalhe está aberto.
         *
         * Mora no estado, e não num `remember` da tela, porque o laço ao vivo
         * republica o `Success` a cada poucos segundos: um `remember` continuaria
         * de pé, mas então a escolha do usuário viveria em dois lugares. Aqui ela
         * é única e testável — desde que `loadSessions` a carregue do estado
         * anterior, ou o bloco fecharia sozinho a cada tique.
         */
        val advancedExpanded: Boolean = false
    ) : CliSessionsUiState {

        val totalCostMicros: Long
            get() = sessions.sumOf { session -> session.costMicros }

        val totalTokens: Long
            get() = sessions.sumOf { session -> session.totalTokens }

        val totalInputTokens: Long
            get() = sessions.sumOf { session -> session.inputTokens }

        val totalOutputTokens: Long
            get() = sessions.sumOf { session -> session.outputTokens }

        val totalCacheReadTokens: Long
            get() = sessions.sumOf { session -> session.cacheReadTokens }

        val totalCacheWriteTokens: Long
            get() = sessions.sumOf { session -> session.cacheWriteTokens }

        /** Ao menos uma sessão tem turnos sem preço: o total exibido é parcial. */
        val isTotalCostComplete: Boolean
            get() = sessions.all { session -> session.isCostComplete }
    }
}

sealed interface CliSessionDetailUiState {

    val sessionId: String

    data class Loading(override val sessionId: String) : CliSessionDetailUiState

    data class Error(
        override val sessionId: String,
        val message: String
    ) : CliSessionDetailUiState

    data class Ready(
        override val sessionId: String,
        val result: CliSessionDetailResult
    ) : CliSessionDetailUiState
}
