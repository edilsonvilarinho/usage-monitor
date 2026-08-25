package com.usagemonitor

import java.io.File

/**
 * Canal de pedido de foco entre uma segunda instancia e a que ja esta de pe.
 *
 * A segunda instancia nao consegue abrir janela -- o [SingleInstanceGuard] recusa
 * o arranque --, e ate agora ela apenas saia calada: clicar no atalho com o app
 * ja rodando nao produzia nada, o que e indistinguivel de "o app nao abre".
 * Aqui ela deixa um pedido no disco e a instancia viva o atende.
 *
 * **Arquivo e nao socket:** socket em loopback dispara o prompt do Firewall do
 * Windows no primeiro arranque, e pedir permissao de rede para focar a propria
 * janela e pior que o defeito.
 *
 * **O carimbo vai no conteudo, nao em `lastModified`:** a data de modificacao
 * depende da granularidade do sistema de arquivos e nao da para escrever num
 * teste sem mexer no relogio do arquivo.
 */
internal class FocusRequestChannel(
    private val requestFile: File = defaultRequestFile()
) {

    // Pedido que ja estava no disco quando esta instancia subiu nao e pedido: ele
    // sobrou de uma sessao anterior, e atende-lo faria a janela saltar para a
    // frente sozinha logo no arranque.
    private var lastSeenStamp: Long = readStamp()

    fun request(nowMillis: Long = System.currentTimeMillis()): Boolean {
        return runCatching {
            requestFile.parentFile?.mkdirs()
            requestFile.writeText(nowMillis.toString())
            true
        }.getOrDefault(false)
    }

    /** `true` uma unica vez por pedido novo. */
    fun consume(): Boolean {
        val stamp = readStamp()
        if (stamp == 0L || stamp == lastSeenStamp) {
            return false
        }

        lastSeenStamp = stamp
        return true
    }

    private fun readStamp(): Long {
        return runCatching {
            if (!requestFile.isFile) {
                return@runCatching 0L
            }
            requestFile.readText().trim().toLongOrNull() ?: 0L
        }.getOrDefault(0L)
    }

    internal companion object {
        // Um `readText` de treze bytes. O periodo e curto porque o que se mede
        // aqui e a distancia entre o clique e a janela aparecer.
        const val POLL_INTERVAL_MILLIS = 500L

        fun defaultRequestFile(): File {
            val homeDir = System.getProperty("user.home")
                ?: throw IllegalStateException("Propriedade 'user.home' não disponível")

            return File(homeDir, ".usage-monitor/focus.request")
        }
    }
}
