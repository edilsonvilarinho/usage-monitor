package com.usagemonitor.domain.entity

/** Porta padrão do acesso web local (#388): alta, fora das faixas comuns de dev. */
const val DEFAULT_WEB_ACCESS_PORT = 47110

/**
 * Acesso à HUD pelo navegador da rede local (#388).
 *
 * **Não é `data class`**: carrega o token, e o `toString` gerado o vazaria em
 * log e relatório de bug (precedente `CursorSessionCredentials`). Igualdade por
 * campos é escrita à mão porque o `StateFlow` depende dela para não republicar.
 */
class WebAccessSettings(
    val enabled: Boolean = false,
    val port: Int = DEFAULT_WEB_ACCESS_PORT,
    /** Segredo da URL; vazio até o primeiro "ativar", que o gera. */
    val token: String = ""
) {
    fun copy(enabled: Boolean = this.enabled, port: Int = this.port, token: String = this.token): WebAccessSettings =
        WebAccessSettings(enabled, port, token)

    override fun equals(other: Any?): Boolean =
        other is WebAccessSettings && other.enabled == enabled && other.port == port && other.token == token

    override fun hashCode(): Int = (enabled.hashCode() * 31 + port) * 31 + token.hashCode()

    override fun toString(): String = "WebAccessSettings(enabled=$enabled, port=$port, token=${if (token.isEmpty()) "" else "***"})"
}

/** Porta válida para o servidor: fora das privilegiadas e dentro do intervalo TCP. */
fun isValidWebAccessPort(port: Int): Boolean = port in 1024..65535
