package com.usagemonitor.update

import java.io.File

/**
 * Variável de ambiente que o `linux-updater.sh` define ao lançar a versão
 * recém-promovida.
 *
 * **Não é argumento de linha de comando**, e chegou a ser (`--update-ack=X`)
 * até isso quebrar ao vivo numa Bazzite real (issue #118): o launcher nativo
 * do jpackage deixava esse `--xxx=yyy` vazar como opção da própria JVM em vez
 * de repassá-lo para `main()` — `Unrecognized option`, e a JVM nem chegava a
 * subir. Como a saída ia para `/dev/null`, o sintoma era só "a versão nunca
 * confirma", sem rastro nenhum. Variável de ambiente não passa por parser de
 * argv de nenhuma camada (launcher nativo, JVM, shell) — é como
 * [com.usagemonitor.SingleInstanceGuard]/`app.lock` evitam qualquer coisa que
 * dependa de interpretação de linha de comando.
 *
 * **Não é um valor novo em [com.usagemonitor.StartupOrigin]**, e nem chega perto
 * dele: aquele enum responde "autostart ou manual", e o health check não é uma
 * terceira origem — a versão nova pode ter sido lançada pelo script tanto num
 * arranque manual quanto num autostart.
 */
internal const val UPDATE_ACK_ENV_VAR = "USAGE_MONITOR_UPDATE_ACK"

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
 * não precaução: uma versão anterior a este código ignora a variável, sobe
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
        if (!isValidUpdateAckToken(token)) {
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
 * Alfabeto restrito do token: ele vira **conteúdo de arquivo** e comparação de
 * igualdade do outro lado, e quebra de linha, espaço e caractere de controle
 * não podem chegar lá. Sessenta e quatro caracteres cobrem com folga o
 * `<pid>-<epoch>` que o script gera.
 */
internal fun isValidUpdateAckToken(token: String): Boolean {
    return UPDATE_ACK_TOKEN_PATTERN.matches(token)
}

/**
 * Token de ACK vindo do ambiente deste processo, ou `null` quando a variável
 * não existe ou não passa no alfabeto restrito.
 */
internal fun updateAckTokenFromEnv(env: (String) -> String? = System::getenv): String? {
    val raw = env(UPDATE_ACK_ENV_VAR)?.trim() ?: return null
    return raw.takeIf(::isValidUpdateAckToken)
}

private val UPDATE_ACK_TOKEN_PATTERN = Regex("""^[A-Za-z0-9_-]{1,64}$""")
