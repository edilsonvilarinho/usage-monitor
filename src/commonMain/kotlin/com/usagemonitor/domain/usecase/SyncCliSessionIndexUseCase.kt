package com.usagemonitor.domain.usecase

import com.usagemonitor.domain.entity.CliSessionIndexReport
import com.usagemonitor.domain.repository.CliSessionRepository

/**
 * Sincroniza o índice sem carregar a lista.
 *
 * Existe para a indexação de background: o Claude Code apaga transcripts
 * antigos, e esperar o usuário abrir a tela para indexar perderia esse
 * histórico para sempre.
 */
class SyncCliSessionIndexUseCase(
    private val repository: CliSessionRepository
) {
    suspend operator fun invoke(): Result<CliSessionIndexReport> {
        return repository.syncIndex()
    }
}
