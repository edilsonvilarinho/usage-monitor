package com.usagemonitor.domain.usecase

import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.repository.AnthropicRepository

/**
 * Use Case: busca o estado atual de uso de tokens da Anthropic.
 *
 * O Use Case é a unidade de negócio da aplicação. Ele:
 * 1. Recebe o repositório via construtor (injeção de dependência)
 * 2. Coordena a lógica (aqui é simples; poderia ter validações, cache, etc.)
 * 3. Retorna o resultado para a camada de Presentation
 *
 * `operator fun invoke()` permite chamar o objeto como função:
 *   Em vez de `useCase.execute()`, escrevemos `useCase()`
 *   É um padrão comum em Kotlin para Use Cases — similar a funções em JS.
 */
class GetAnthropicUsageUseCase(
    private val repository: AnthropicRepository
) {
    suspend operator fun invoke(): Result<ApiUsageStats> {
        return repository.getUsage()
    }
}
