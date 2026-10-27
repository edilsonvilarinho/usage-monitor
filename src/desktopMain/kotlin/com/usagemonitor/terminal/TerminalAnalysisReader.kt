package com.usagemonitor.terminal

import com.usagemonitor.domain.entity.ANALYSIS_SOURCES
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.HistoryRange
import com.usagemonitor.domain.entity.SessionAnalysis
import com.usagemonitor.domain.entity.analysisThroughputCuts
import com.usagemonitor.domain.entity.buildSessionAnalysis
import com.usagemonitor.presentation.terminal.TerminalAccount
import kotlinx.coroutines.runBlocking
import kotlin.time.Instant

/**
 * A análise de uma conta no terminal (#463, AI2 e AI3): o histórico de 7 dias e a vazão "desde"
 * cada recorte que a análise pede, pelos bancos em somente leitura — a mesma conta do
 * `sessionAnalysisReader` do app. O Claude Code lê o índice do perfil da conta; o Codex, o da
 * máquina. Índice que falha deixa a vazão nula; histórico que falha deixa a análise nula.
 */
internal object TerminalAnalysisReader {

    /** As contas com análise, na ordem do painel: só as fontes com índice CLI. */
    fun eligible(accounts: List<TerminalAccount>): List<TerminalAccount> =
        accounts.filter { account -> account.source in ANALYSIS_SOURCES }

    fun read(account: TerminalAccount, stores: TuiStores, now: Instant): SessionAnalysis? = runBlocking {
        val report = runCatching { stores.getUsageHistory(account.source, HistoryRange.LAST_7_DAYS, account.accountKey, now) }.getOrNull() ?: return@runBlocking null
        val cumulative = analysisThroughputCuts(report.series, now).mapNotNull { since ->
            val byModel = if (account.source == ApiSource.ANTHROPIC) {
                stores.cliModelThroughputs(account.targetProfileId, since)
            } else {
                stores.codexModelThroughputs(since)
            }
            byModel.getOrNull()?.let { value -> since to value }
        }.toMap()
        val label = listOfNotNull(account.name, account.profile?.takeIf { profile -> profile.isNotBlank() }).joinToString(" · ")
        buildSessionAnalysis(label, account.source, report.series, cumulative, now)
    }
}
