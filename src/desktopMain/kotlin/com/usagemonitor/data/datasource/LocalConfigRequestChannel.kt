package com.usagemonitor.data.datasource

import com.usagemonitor.data.AppDataDirectory
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/**
 * Pedidos de configuração do terminal para o coletor vivo (#440, decisão D1), em
 * `~/.usage-monitor/control/`. Com app ou serviço coletando, só ele escreve a
 * configuração: o terminal deposita o pedido em `requests/`, o coletor o aplica e
 * responde em `replies/`.
 *
 * Os textos são opacos aqui (o formato é do `ConfigJson`). Pedido pode levar segredo
 * (chave de API), então nasce com permissão só do dono e é apagado ao ser lido. Um
 * pedido é de quem o tirou primeiro: o coletor o renomeia para `processing/` antes de
 * ler, e o terminal que desiste o apaga de `requests/`. Os dois são operações atômicas
 * no mesmo disco, então nunca acontecem as duas.
 *
 * No Windows, antivírus e indexador seguram por instantes um arquivo recém-escrito ou
 * recém-movido (#460): ler, apagar e responder tentam de novo algumas vezes antes de
 * desistir. As funções de arquivo são parâmetros só para o teste simular o arquivo preso.
 */
internal class LocalConfigRequestChannel(
    root: File = AppDataDirectory.file("control"),
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val readFile: (File) -> String = File::readText,
    private val deleteFile: (File) -> Boolean = File::delete,
    private val writeFile: (File, String) -> Unit = ::writeSecretFile,
    private val pause: (Long) -> Unit = Thread::sleep
) {
    private val requests = File(root, "requests")
    private val processing = File(root, "processing")
    private val replies = File(root, "replies")

    /**
     * Deposita um pedido e devolve o id dele. O nome começa pelo instante e por um contador
     * do processo, com zeros à esquerda: a ordem do nome é a de chegada, inclusive para dois
     * pedidos no mesmo milissegundo (o teste pegou o UUID decidindo a ordem).
     */
    fun submit(payload: String): String {
        val id = nowMillis().toString().padStart(MILLIS_DIGITS, '0') + "-" +
            sequence.incrementAndGet().toString().padStart(SEQUENCE_DIGITS, '0') + "-" + UUID.randomUUID()
        writeSecretFile(File(requests, "$id.json"), payload)
        return id
    }

    /**
     * Espera a resposta até [timeoutMillis]. A resposta é lida e apagada; `null` se não
     * chegou a tempo.
     */
    fun awaitReply(id: String, timeoutMillis: Long, pollMillis: Long = 100L, sleep: (Long) -> Unit = Thread::sleep): String? {
        val file = File(replies, "$id.json")
        var waited = 0L
        while (true) {
            if (file.isFile) {
                val text = runCatching { file.readText() }.getOrNull()
                if (text != null) {
                    file.delete()
                    return text
                }
            }
            if (waited >= timeoutMillis) {
                return null
            }
            sleep(pollMillis)
            waited += pollMillis
        }
    }

    /**
     * Recolhe um pedido ainda não tirado. `true`: estava lá e não será mais aplicado.
     * `false`: o coletor já o tirou, e a resposta pode chegar. Apagar que falha com o
     * arquivo ainda na fila é o arquivo preso, e não o coletor: tenta de novo.
     */
    fun withdraw(id: String): Boolean {
        val file = File(requests, "$id.json")
        return retrying {
            when {
                deleteFile(file) -> true
                !file.exists() -> false
                else -> null
            }
        } ?: false
    }

    /**
     * Os pedidos à espera, na ordem de chegada, já tirados da fila (e do disco). Pedido
     * tirado é de quem o tirou e precisa de resposta: se a leitura falhar mesmo depois das
     * novas tentativas, ele vem com o conteúdo `null`, e não some (#460).
     */
    fun takePending(): List<Pair<String, String?>> {
        val files = requests.listFiles { file -> file.isFile && file.name.endsWith(".json") }?.sortedBy { file -> file.name } ?: return emptyList()
        processing.mkdirs()
        return files.mapNotNull { file ->
            val claimed = File(processing, file.name)
            if (!file.renameTo(claimed)) {
                return@mapNotNull null
            }
            val text = retrying { runCatching { readFile(claimed) }.getOrNull() }
            // Pedido pode levar segredo: o que não sair agora o `discardStale` leva depois.
            retrying { if (deleteFile(claimed) || !claimed.exists()) true else null }
            file.name.removeSuffix(".json") to text
        }
    }

    /** Grava a resposta; a falha que sobra depois das novas tentativas sobe para quem atende. */
    fun reply(id: String, payload: String) {
        val target = File(replies, "$id.json")
        var lastFailure: Exception? = null
        retrying {
            try {
                writeFile(target, payload)
                true
            } catch (failure: IOException) {
                lastFailure = failure
                null
            }
        } ?: throw checkNotNull(lastFailure)
    }

    /** [attempt] até devolver um valor, com uma pausa curta entre as tentativas; `null` se nenhuma devolveu. */
    private fun <T : Any> retrying(attempt: () -> T?): T? {
        for (index in 1..TRANSIENT_ATTEMPTS) {
            val value = attempt()
            if (value != null) {
                return value
            }
            if (index < TRANSIENT_ATTEMPTS) {
                pause(TRANSIENT_PAUSE_MILLIS)
            }
        }
        return null
    }

    /**
     * Respostas que ninguém leu (o terminal desistiu) e pedidos esquecidos de um coletor
     * que caiu no meio, mais velhos que [maxAgeMillis]. Um pedido velho não é aplicado
     * tarde: quem o fez já ouviu que nada mudou.
     */
    fun discardStale(maxAgeMillis: Long = STALE_MILLIS) {
        val limit = nowMillis() - maxAgeMillis
        for (directory in listOf(requests, processing, replies)) {
            directory.listFiles()?.filter { file -> file.lastModified() < limit }?.forEach { file -> file.delete() }
        }
    }

    companion object {
        const val STALE_MILLIS = 60_000L

        /** Cinco tentativas a 100 ms: um arquivo preso por um antivírus solta em décimos de segundo. */
        const val TRANSIENT_ATTEMPTS = 5
        const val TRANSIENT_PAUSE_MILLIS = 100L
        private const val MILLIS_DIGITS = 15
        private const val SEQUENCE_DIGITS = 9
        private val sequence = AtomicLong()
    }
}
