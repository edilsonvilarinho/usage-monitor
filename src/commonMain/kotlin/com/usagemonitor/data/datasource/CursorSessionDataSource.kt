package com.usagemonitor.data.datasource

/**
 * O par que monta o cookie `WorkosCursorSessionToken`. **Não é `data class`**: o
 * `toString()` gerado imprimiria o token em qualquer log ou mensagem que
 * interpolasse o objeto.
 */
class CursorSessionCredentials(
    val accountId: String,
    val accessToken: String
) {
    override fun equals(other: Any?): Boolean =
        other is CursorSessionCredentials && other.accountId == accountId && other.accessToken == accessToken

    override fun hashCode(): Int = 31 * accountId.hashCode() + accessToken.hashCode()

    override fun toString(): String = "CursorSessionCredentials(accountId=$accountId, accessToken=[REDACTED])"
}

/**
 * Lê, sem gravar, a sessão que o próprio editor Cursor já mantém.
 *
 * Lança `CursorUsageException` com `NOT_INSTALLED` quando o banco do editor não
 * existe e `SIGNED_OUT` quando existe sem sessão utilizável: são respostas
 * diferentes para o usuário, e a primeira versão devolvia `null` para as duas.
 */
interface CursorSessionDataSource {
    suspend fun readCredentials(): CursorSessionCredentials
}
