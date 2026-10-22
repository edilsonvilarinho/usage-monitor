package com.usagemonitor.terminal

import com.usagemonitor.data.datasource.LocalCliSessionDataSource
import com.usagemonitor.data.datasource.LocalCodexCliSessionDataSource
import com.usagemonitor.data.datasource.LocalUsageHistoryDataSource
import com.usagemonitor.data.repository.CliSessionRepositoryImpl
import com.usagemonitor.data.repository.CodexCliSessionRepositoryImpl
import com.usagemonitor.data.repository.UsageHistoryRepositoryImpl
import com.usagemonitor.domain.entity.CliSessionSummary
import com.usagemonitor.domain.entity.CodexCliSessionSummary
import com.usagemonitor.domain.usecase.GetCliSessionDetailUseCase
import com.usagemonitor.domain.usecase.GetCliSessionsUseCase
import com.usagemonitor.domain.usecase.GetCodexCliSessionDetailUseCase
import com.usagemonitor.domain.usecase.GetCodexCliSessionsUseCase
import com.usagemonitor.domain.usecase.GetUsageHistoryUseCase
import java.io.File

/**
 * Os bancos do app para o modo interativo (#441, decisão D2): os mesmos data sources,
 * repositórios e casos de uso da janela de Sessões e do Histórico, sobre conexões **somente
 * leitura**. Nada aqui cria schema, indexa transcript ou grava: quem escreve é o coletor (app
 * ou serviço), e a TUI de uma versão pode estar lendo o banco de outra (R2 do plano).
 *
 * Os view models do app não entram: eles sincronizam o índice e têm laços próprios de
 * releitura, e a TUI já tem o dela (D5).
 */
internal class TuiStores(
    cliIndex: File = LocalCliSessionDataSource.defaultDatabaseFile(),
    codexIndex: File = LocalCodexCliSessionDataSource.defaultDatabaseFile(),
    // O histórico de uso mora no mesmo `usage-history.db` do índice do Claude Code.
    history: File = LocalCliSessionDataSource.defaultDatabaseFile()
) : AutoCloseable {
    private val cliDataSource = LocalCliSessionDataSource(databaseFile = cliIndex, readOnly = true)
    private val codexDataSource = LocalCodexCliSessionDataSource(databaseFile = codexIndex, readOnly = true)
    private val historyDataSource = LocalUsageHistoryDataSource(databaseFile = history, readOnly = true)

    private val cliRepository = CliSessionRepositoryImpl(cliDataSource)
    private val codexRepository = CodexCliSessionRepositoryImpl(codexDataSource)

    val getCliSessions = GetCliSessionsUseCase(cliRepository)
    val getCliSessionDetail = GetCliSessionDetailUseCase(cliRepository)
    val getCodexSessions = GetCodexCliSessionsUseCase(codexRepository)
    val getCodexSessionDetail = GetCodexCliSessionDetailUseCase(codexRepository)
    val getUsageHistory = GetUsageHistoryUseCase(UsageHistoryRepositoryImpl(historyDataSource))

    /**
     * Sessões do Claude Code com corte por [sinceEpochMillis] (`null` são os agregados da
     * sessão inteira). Direto pelo repositório: o caso de uso só recebe `CliSessionRange`, e
     * corte novo vai por parâmetro, nunca por valor novo no enum (regra do CLAUDE.md).
     */
    suspend fun cliSessions(profileId: String?, sinceEpochMillis: Long?): Result<List<CliSessionSummary>> =
        cliRepository.getSessions(profileId, sinceEpochMillis)

    suspend fun codexSessions(sinceEpochMillis: Long?): Result<List<CodexCliSessionSummary>> =
        codexRepository.getSessions(sinceEpochMillis)

    override fun close() {
        runCatching { cliDataSource.close() }
        runCatching { codexDataSource.close() }
        runCatching { historyDataSource.close() }
    }
}
