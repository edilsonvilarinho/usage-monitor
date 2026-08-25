package com.usagemonitor.update

import java.io.File

/**
 * Argumento privado que o `linux-updater.sh` passa à versão recém-promovida.
 *
 * **Não é um valor novo em [com.usagemonitor.StartupOrigin]**, e nem chega perto
 * dele: aquele enum responde "autostart ou manual", e o health check não é uma
 * terceira origem — a versão nova pode ter sido lançada pelo script tanto num
 * arranque manual quanto num autostart. Ele é parseado por
 * [parseUpdateAckToken], que devolve `null` para tudo que não reconhece.
 */
internal const val UPDATE_ACK_ARGUMENT_PREFIX = "--update-ack="

/**
 * Canal de confirmação entre a versão recém-promovida e o script que a promoveu.
 *
 * **Arquivo e não socket**, pelo mesmo motivo do
 * [com.usagemonitor.FocusRequestChannel]: socket em loopback dispara o prompt de
 * firewall, e pedir permissão de rede para dizer "subi" é pior que o defeito.
 *
 * **O conteúdo é o token que o script gerou**, e não um carimbo de tempo. O
 * script inventa o token, apaga o arquivo, lança a versão nova e espera o
 * arquivo aparecer **com aquele token dentro**. Isso resolve o ACK sobrado de
 * uma sessão anterior sem que ninguém precise comparar relógios: um arquivo
 * antigo tem outro token, e nenhum token vale duas vezes. Comparar carimbos
 * exigiria que o shell soubesse o instante do lançamento e tolerasse a
 * granularidade do sistema de arquivos.
 *
 * Sem ACK o script faz rollback. Isso torna o piso de versão-alvo obrigatório e
 * não precaução: uma versão anterior a este código ignora o argumento, sobe
 * normalmente e **nunca confirma** — e o rollback desfaria uma atualização que
 * deu certo.
 */
internal class UpdateAckChannel(
    private val ackFile: File = defaultUpdateAckFile()
) {

    /**
     * Grava o token. Devolve `false` em vez de lançar: falhar aqui custa um
     * rollback, e derrubar o arranque do app custaria o app.
     */
    fun acknowledge(token: String): Boolean {
        if (parseUpdateAckToken(UPDATE_ACK_ARGUMENT_PREFIX + token) == null) {
            return false
        }
        return runCatching {
            ackFile.parentFile?.mkdirs()
            ackFile.writeText(token)
            true
        }.getOrDefault(false)
    }

    /**
     * Se o arquivo confirma **este** token. Existe para o teste afirmar o
     * contrato que o shell implementa do outro lado; o app nunca lê o ACK que
     * ele mesmo escreveu.
     */
    fun isAcknowledged(token: String): Boolean {
        val content = runCatching {
            if (!ackFile.isFile) null else ackFile.readText()
        }.getOrNull() ?: return false
        return content.trim() == token
    }

    internal companion object {
        fun defaultUpdateAckFile(): File {
            val home = System.getProperty("user.home")
                ?: throw IllegalStateException("Propriedade 'user.home' não disponível")
            return File(home, ".usage-monitor/update-ack")
        }
    }
}

/**
 * Token de ACK vindo da linha de comando, ou `null` quando o argumento não é um.
 *
 * O valor vem de `argv` e vira **conteúdo de arquivo** e comparação de igualdade
 * do outro lado; o alfabeto restrito é o que impede quebra de linha, espaço e
 * caractere de controle de chegarem lá. Sessenta e quatro caracteres cobrem com
 * folga o `<pid>-<epoch>` que o script gera.
 */
internal fun parseUpdateAckToken(argument: String): String? {
    val trimmed = argument.trim()
    if (!trimmed.startsWith(UPDATE_ACK_ARGUMENT_PREFIX)) {
        return null
    }
    val token = trimmed.removePrefix(UPDATE_ACK_ARGUMENT_PREFIX)
    return if (UPDATE_ACK_TOKEN_PATTERN.matches(token)) token else null
}

/** Primeiro token válido da linha de comando, ou `null`. */
internal fun updateAckTokenFrom(arguments: Array<String>): String? {
    return arguments.firstNotNullOfOrNull { argument -> parseUpdateAckToken(argument) }
}

private val UPDATE_ACK_TOKEN_PATTERN = Regex("""^[A-Za-z0-9_-]{1,64}$""")
