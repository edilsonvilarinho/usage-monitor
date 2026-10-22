package com.usagemonitor.terminal

import com.usagemonitor.AppSettingsStore
import com.usagemonitor.ConfigApplier
import com.usagemonitor.ConfigChange
import com.usagemonitor.ConfigJson
import com.usagemonitor.ConfigOutcome
import com.usagemonitor.ConfigRefusals
import com.usagemonitor.data.datasource.LocalConfigRequestChannel
import com.usagemonitor.domain.entity.CollectorKind
import com.usagemonitor.domain.repository.NoOpBreadcrumbRecorder

/** O que aconteceu com uma mudança pedida pelo terminal. */
internal sealed interface ConfigDelivery {
    /** Aplicada ou recusada por [collector] (`null`: aplicada direto nos arquivos, sem coletor). */
    data class Answered(val outcome: ConfigOutcome, val collector: CollectorKind?) : ConfigDelivery

    /** O coletor não respondeu a tempo, e o pedido foi recolhido: nada mudou. */
    data class NotAnswered(val collector: CollectorKind) : ConfigDelivery
}

/**
 * Entrega uma [ConfigChange] (decisão D1 da #440): com coletor vivo, como pedido para
 * ele, que é o único escritor; sem coletor, aplicada aqui mesmo, pelo mesmo
 * [ConfigApplier], sobre um [AppSettingsStore] que só existe durante o comando.
 */
internal class TerminalConfigClient(
    private val collector: () -> CollectorKind? = { TerminalReader().collector() },
    private val channel: LocalConfigRequestChannel = LocalConfigRequestChannel(),
    private val localStore: () -> AppSettingsStore = { AppSettingsStore(NoOpBreadcrumbRecorder) },
    private val timeoutMillis: Long = REPLY_TIMEOUT_MILLIS,
    private val sleep: (Long) -> Unit = Thread::sleep
) {

    fun apply(change: ConfigChange): ConfigDelivery {
        val kind = collector()
        if (kind == null) {
            val store = localStore()
            try {
                return ConfigDelivery.Answered(ConfigApplier(store).apply(change), collector = null)
            } finally {
                store.close()
            }
        }
        val id = channel.submit(ConfigJson.encodeChange(change))
        channel.awaitReply(id, timeoutMillis, sleep = sleep)?.let { reply -> return answered(reply, kind) }
        if (channel.withdraw(id)) {
            return ConfigDelivery.NotAnswered(kind)
        }
        // O coletor tirou o pedido no último instante: a resposta está a caminho.
        channel.awaitReply(id, timeoutMillis, sleep = sleep)?.let { reply -> return answered(reply, kind) }
        return ConfigDelivery.NotAnswered(kind)
    }

    private fun answered(reply: String, kind: CollectorKind): ConfigDelivery =
        ConfigDelivery.Answered(
            ConfigJson.decodeOutcome(reply) ?: ConfigOutcome.Refused(ConfigRefusals.FAILED, "unreadable reply"),
            kind
        )

    companion object {
        /** Trinta vezes o intervalo do coletor: um app ocupado ainda responde. */
        const val REPLY_TIMEOUT_MILLIS = 10_000L
    }
}
