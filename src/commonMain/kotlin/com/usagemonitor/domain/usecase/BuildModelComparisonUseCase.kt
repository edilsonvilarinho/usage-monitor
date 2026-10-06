package com.usagemonitor.domain.usecase

import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.ComparisonRow
import com.usagemonitor.domain.entity.buildComparisonRows
import com.usagemonitor.domain.repository.CliSessionRepository
import com.usagemonitor.domain.repository.CodexCliSessionRepository

/**
 * Comparação entre modelos e fontes num recorte (#386).
 *
 * O resumo por modelo do Claude é a informação principal; vazão e Codex são
 * acessórios — falha num deles deixa a métrica nula ("não medido") em vez de
 * derrubar a tela, a mesma regra do resumo do modal de sessões.
 */
class BuildModelComparisonUseCase(
    private val cliSessions: CliSessionRepository,
    private val codexSessions: CodexCliSessionRepository
) {
    suspend operator fun invoke(sinceEpochMillis: Long, stats: List<ApiUsageStats>): Result<List<ComparisonRow>> {
        val breakdown = cliSessions.getUsageBreakdown(profileId = null, sinceEpochMillis = sinceEpochMillis)
            .getOrElse { error -> return Result.failure(error) }
        val throughputs = cliSessions.getModelThroughputs(profileId = null, sinceEpochMillis = sinceEpochMillis)
            .getOrDefault(emptyMap())
        val codex = codexSessions.getModelUsage(sinceEpochMillis).getOrDefault(emptyList())
        return Result.success(buildComparisonRows(breakdown.byModel, throughputs, codex, stats))
    }
}
