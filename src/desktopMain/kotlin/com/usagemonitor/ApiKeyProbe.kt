package com.usagemonitor

import com.usagemonitor.data.datasource.RemoteApiDataSource
import com.usagemonitor.data.repository.DeepSeekRepositoryImpl
import com.usagemonitor.data.repository.MiniMaxRepositoryImpl
import com.usagemonitor.data.repository.OpenCodeGoRepositoryImpl
import com.usagemonitor.data.repository.OpenRouterRepositoryImpl
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageStats

/**
 * A coleta real da fonte, com uma chave candidata no lugar da guardada
 * (issue #204).
 *
 * **É o mesmo caminho da coleta, não um paralelo.** Cada repositório é montado
 * com o `apiKeyReader` apontando para a chave sob teste — o mesmo ponto de
 * injeção que o `Main.kt` já usa —, e o que volta é o `Result` que o dashboard
 * receberia. Endpoint próprio de teste passaria enquanto a coleta real falha; e
 * um `GET` cru com o status HTTP examinado na UI aprovaria uma chave inválida da
 * MiniMax, que responde `HTTP 200` com `status_code` de erro no corpo.
 *
 * Mora fora do `Main.kt` porque ali dentro seria mais bytecode num `main()` que
 * já está no limite do backend JVM.
 *
 * O `when` é **exaustivo e sem `else`**: fonte nova obriga a decidir se ela tem
 * chave testável, em vez de cair calada num ramo genérico. As fontes sem chave
 * lançam, e o texto diz por quê — nenhuma delas chega aqui, porque a aba APIs só
 * oferece o botão para as quatro de `requiresApiKey`.
 */
internal suspend fun testApiKeyUsage(
    source: ApiSource,
    apiDataSource: RemoteApiDataSource,
    apiKeyReader: () -> String?
): Result<ApiUsageStats> {
    return when (source) {
        ApiSource.MINIMAX -> MiniMaxRepositoryImpl(apiDataSource, apiKeyReader).getUsage()
        ApiSource.DEEPSEEK -> DeepSeekRepositoryImpl(apiDataSource, apiKeyReader).getUsage()
        ApiSource.OPENCODE_GO -> OpenCodeGoRepositoryImpl(apiDataSource, apiKeyReader).getUsage()
        ApiSource.OPENROUTER -> OpenRouterRepositoryImpl(apiDataSource, apiKeyReader).getUsage()
        ApiSource.ANTHROPIC,
        ApiSource.CODEX,
        ApiSource.OPENCODE,
        ApiSource.KILO -> Result.failure(
            IllegalStateException("${source.name} não usa chave de API local e não tem o que testar.")
        )
    }
}
