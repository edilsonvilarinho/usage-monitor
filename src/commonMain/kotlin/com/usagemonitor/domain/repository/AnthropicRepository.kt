package com.usagemonitor.domain.repository

import com.usagemonitor.domain.entity.ApiUsageStats

/**
 * Contrato (interface) que define como obter os dados da Anthropic.
 *
 * PRINCÍPIO DA INVERSÃO DE DEPENDÊNCIA (DIP):
 * O domain define O QUE precisa (esta interface).
 * A camada `data` define COMO buscar (implementação com Ktor).
 * O domain não sabe nada sobre Ktor, HTTP, ou JSON.
 *
 * Em Node.js seria equivalente a exportar um tipo TypeScript de serviço.
 *
 * `suspend` = função assíncrona (equivalente a `async` em JavaScript).
 * `Result<T>` = contêiner que pode ser sucesso ou falha (similar a try/catch).
 */
interface AnthropicRepository {
    suspend fun getUsage(): Result<ApiUsageStats>
}
