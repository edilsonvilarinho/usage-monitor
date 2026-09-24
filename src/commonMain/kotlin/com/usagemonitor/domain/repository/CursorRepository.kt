package com.usagemonitor.domain.repository

import com.usagemonitor.domain.entity.ApiUsageStats

interface CursorRepository {
    suspend fun getUsage(): Result<ApiUsageStats>
}

/**
 * Falhas do Cursor que tentar de novo não resolve: dependem de o usuário instalar
 * ou entrar no editor, ou são o estado normal do plano. A tela as trata como
 * configuração — banner sem "Tentar novamente" e sem toast a cada coleta.
 * [safeMessage] é a mensagem inteira que chega à tela e o texto que a
 * classificação procura; nenhuma delas carrega token, cookie ou caminho local.
 */
enum class CursorUsageFailureKind(val safeMessage: String) {
    NOT_INSTALLED("Cursor is not installed on this machine"),
    SIGNED_OUT("Cursor has no signed-in session on this machine"),
    SESSION_REJECTED("Cursor rejected the local session"),

    /** Plano gratuito sem franquia ou plano ilimitado: não há o que medir, e isso não é consumo zero. */
    NOTHING_METERED("Cursor reports nothing to meter on this plan")
}

class CursorUsageException(val kind: CursorUsageFailureKind) : IllegalStateException(kind.safeMessage)
