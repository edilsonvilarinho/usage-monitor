package com.usagemonitor.presentation.viewmodel

import com.usagemonitor.domain.entity.CliSessionHealthTally
import com.usagemonitor.domain.entity.CliSessionRange
import com.usagemonitor.domain.entity.CliSessionSummary
import com.usagemonitor.domain.entity.AccountCreditUsage
import com.usagemonitor.domain.entity.CliUsageBreakdown
import com.usagemonitor.domain.entity.MonthlyBudgetStatus
import com.usagemonitor.domain.entity.tallyHealth
import com.usagemonitor.domain.usecase.CliSessionDetailResult
import kotlinx.datetime.Instant

/**
 * Qual das duas leituras a janela mostra.
 *
 * Enum novo, e não um valor a mais em algum enum existente: os `when` exaustivos
 * de `CliSessionRange` e companhia não têm nada a ver com esta escolha.
 */
enum class CliSessionsView { SESSIONS, BREAKDOWN }

/**
 * Resultado da última exportação.
 *
 * Carrega o fato; a frase nasce na borda da UI, como em [DashboardToast]. O
 * cancelamento do diálogo não produz resultado nenhum — não é sucesso nem erro,
 * e anunciá-lo seria ruído.
 */
sealed interface CliExportOutcome {
    data class Saved(val path: String) : CliExportOutcome
    data class Failed(val message: String) : CliExportOutcome
}

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
        val advancedExpanded: Boolean = false,
        /** Painel "Como ler esta tela". Mesmo tratamento de [advancedExpanded]. */
        val glossaryExpanded: Boolean = false,
        /** Aba corrente. Mesmo tratamento de [advancedExpanded]: mora no estado. */
        val view: CliSessionsView = CliSessionsView.SESSIONS,
        /**
         * Resumo por eixo da mesma janela.
         *
         * `null` significa "ainda não lido" — a aba só é carregada quando o
         * usuário a abre. Uma leitura que falha **mantém** o valor anterior: no
         * laço ao vivo o usuário está lendo a tela, não esperando por ela.
         */
        val breakdown: CliUsageBreakdown? = null,
        /** Falha da última leitura do resumo, sem apagar o resumo já exibido. */
        val breakdownError: String? = null,
        /** Resultado da última exportação; `null` enquanto nenhuma aconteceu. */
        val exportOutcome: CliExportOutcome? = null,
        /**
         * Orçamento do mês corrente. `null` sem teto configurado.
         *
         * Independe de [range]: orçamento é mensal, e amarrá-lo ao chip de 5h
         * daria um número sem significado.
         */
        val budget: MonthlyBudgetStatus? = null,
        /**
         * Créditos de uso da conta, na moeda **real** dela.
         *
         * Fica ao lado de [budget] e nunca somado a ele: o custo do índice é
         * sempre USD e este pode não ser.
         */
        val accountCredits: AccountCreditUsage? = null
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

        /**
         * Quantas sessões estão saturadas ou em atenção.
         *
         * Vai para o cabeçalho: o veredito por sessão já está na linha, mas com
         * a lista rolada ele some da vista, e a pergunta "tem alguma sessão
         * pedindo /compact?" não deveria exigir varrer a lista inteira.
         */
        val healthTally: CliSessionHealthTally
            get() = sessions.tallyHealth()
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
