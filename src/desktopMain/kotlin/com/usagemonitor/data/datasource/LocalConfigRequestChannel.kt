package com.usagemonitor.data.datasource

import com.usagemonitor.data.AppDataDirectory
import java.io.File
import java.util.UUID

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
 */
internal class LocalConfigRequestChannel(
    root: File = AppDataDirectory.file("control"),
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    private val requests = File(root, "requests")
    private val processing = File(root, "processing")
    private val replies = File(root, "replies")

    /** Deposita um pedido e devolve o id dele. O nome começa pelo instante: a ordem de chegada. */
    fun submit(payload: String): String {
        val id = "${nowMillis()}-${UUID.randomUUID()}"
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
     * `false`: o coletor já o tirou, e a resposta pode chegar.
     */
    fun withdraw(id: String): Boolean = File(requests, "$id.json").delete()

    /** Os pedidos à espera, na ordem de chegada, já tirados da fila (e do disco). */
    fun takePending(): List<Pair<String, String>> {
        val files = requests.listFiles { file -> file.isFile && file.name.endsWith(".json") }?.sortedBy { file -> file.name } ?: return emptyList()
        processing.mkdirs()
        return files.mapNotNull { file ->
            val claimed = File(processing, file.name)
            if (!file.renameTo(claimed)) {
                return@mapNotNull null
            }
            val text = runCatching { claimed.readText() }.getOrNull()
            claimed.delete()
            text?.let { payload -> file.name.removeSuffix(".json") to payload }
        }
    }

    fun reply(id: String, payload: String) {
        writeSecretFile(File(replies, "$id.json"), payload)
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
    }
}
