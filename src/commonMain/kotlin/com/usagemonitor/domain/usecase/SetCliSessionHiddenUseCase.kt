package com.usagemonitor.domain.usecase

import com.usagemonitor.domain.repository.CliSessionRepository

/**
 * Oculta ou reexibe uma sessão na lista.
 *
 * Não existe exclusão: a fonte é o transcript do Claude Code do próprio usuário
 * e apagá-lo destruiria o histórico do CLI. Só o índice é marcado.
 */
class SetCliSessionHiddenUseCase(
    private val repository: CliSessionRepository
) {
    suspend operator fun invoke(sessionId: String, hidden: Boolean): Result<Unit> {
        return repository.setSessionHidden(sessionId, hidden)
    }
}
