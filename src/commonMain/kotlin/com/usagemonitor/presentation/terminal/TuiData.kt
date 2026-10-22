package com.usagemonitor.presentation.terminal

import com.usagemonitor.domain.entity.CollectorKind
import com.usagemonitor.domain.entity.OutputThroughput
import kotlin.time.Instant

/**
 * O que o modo interativo sabe da última leitura (#441). Cada aba lê só a parte dela; o
 * leitor do `desktopMain` monta o valor a cada releitura (15 s, ou `r`).
 */
data class TuiData(
    val now: Instant,
    /** Quem coleta agora (app ou serviço); `null` quando nada coleta. */
    val collector: CollectorKind?,
    /** A coleta mais recente entre as contas; `null` sem leitura nenhuma. */
    val lastCollectedAt: Instant?,
    /** Com time configurado, a aba 4 aparece. */
    val teamConfigured: Boolean = false,
    /** As contas na ordem do painel, com o que a Visão geral mostra de cada uma. */
    val accounts: List<TuiAccount> = emptyList(),
    /** A aba 2; `null` enquanto ela não foi aberta. */
    val sessions: TuiSessions? = null,
    /** A aba 3; `null` enquanto ela não foi aberta. */
    val history: TuiHistory? = null,
    /** A aba 4; `null` enquanto ela não foi aberta, ou sem time. */
    val team: TuiTeam? = null
) {
    /** A última tentativa de coleta falhou em alguma conta (6.2). */
    val lastFailed: Boolean
        get() = accounts.any { account -> account.failure != null }

    /**
     * A primeira trilha de cada conta na aba 3, que lista as cotas com percentual na ordem do
     * painel; `null` para a conta sem nenhuma.
     */
    val historyRowOfAccount: List<Int?>
        get() {
            var next = 0
            return accounts.map { item ->
                val count = item.account.quotas.count { quota -> quota.percent != null }
                val first = if (count > 0) next else null
                next += count
                first
            }
        }

    /** A leitura mais velha entre as que falharam: é dela que o status fala. */
    val oldestFailedReading: Instant?
        get() = accounts.filter { account -> account.failure != null }.mapNotNull { account -> account.account.fetchedAt }.minOrNull()
}

/** Uma conta na Visão geral (3.1): as cotas, e o que mais o detalhe mostra. */
data class TuiAccount(
    val account: TerminalAccount,
    /** A última coleta desta conta falhou; os números são os da anterior (6.2). */
    val failure: TuiReadFailure? = null,
    /** `null` quando a fonte não tem índice de sessões; [TuiActiveSession.NONE] sem sessão ativa. */
    val activeSession: TuiActiveSession? = null,
    /** As últimas 24 h da primeira cota com percentual; `null` sem histórico. */
    val last24h: TuiSpark? = null
)

/** O que o coletor diz da falha: a frase, quando tentou e quando tenta de novo. */
data class TuiReadFailure(
    val reason: String?,
    val attemptedAt: Instant?,
    val retryAt: Instant?
)

/** A sessão CLI em curso na conta: a mais recente com atividade nos últimos minutos. */
data class TuiActiveSession(
    val project: String?,
    val model: String?,
    val durationMillis: Long,
    /** `null` é sem tarifa (Codex) ou não medido; nunca custo zero. */
    val costMicros: Long?,
    val costIncomplete: Boolean,
    val throughput: OutputThroughput?
) {
    companion object {
        /** A fonte tem sessões, mas nenhuma ativa agora. */
        val NONE = TuiActiveSession(null, null, 0L, null, false, null)
    }
}
