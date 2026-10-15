package com.usagemonitor

import com.usagemonitor.data.diagnostics.AppLogFile
import com.usagemonitor.data.diagnostics.AppLogLevel
import java.awt.EventQueue
import kotlin.concurrent.thread

/**
 * Detecta o EDT parado e grava onde ele está preso (issue #416).
 *
 * "O app fica travado" chega sem nada que separe as duas causas possíveis — a
 * janela engolindo clique ou a thread da interface parada. A segunda deixa
 * rastro só aqui: a cada [pollIntervalMillis] o watchdog posta um ping no EDT; se
 * o ping não é atendido em [stallThresholdMillis], grava **uma** linha `warn` com
 * a pilha da thread do EDT naquele instante, e ao destravar grava quanto durou.
 *
 * A pilha é a do EDT, não a do watchdog: ela vem num `Throwable` sintético
 * ([EdtStallSnapshot]) porque é por exceção que o [AppLogFile] grava quadros.
 *
 * **Suspensão do sistema não é travamento:** se o próprio laço do watchdog ficou
 * parado mais que [SUSPEND_GAP_FACTOR] intervalos, o processo inteiro dormiu
 * (tampa fechada) e o ping pendente é recomeçado em vez de denunciado.
 *
 * Função [tick] separada do laço para o teste dirigir o relógio sem thread.
 */
internal class EdtWatchdog(
    private val appLog: AppLogFile,
    private val stallThresholdMillis: Long = DEFAULT_STALL_THRESHOLD_MILLIS,
    private val pollIntervalMillis: Long = DEFAULT_POLL_INTERVAL_MILLIS,
    private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000L },
    private val post: (Runnable) -> Unit = EventQueue::invokeLater
) {
    private val lock = Any()
    private var pingPostedAt: Long? = null
    private var stallReported = false
    private var lastTickAt: Long? = null

    /** A thread que atendeu o último ping: o EDT pode ser trocado pela AWT. */
    @Volatile
    private var edtThread: Thread? = null

    fun start() {
        thread(name = "edt-watchdog", isDaemon = true) {
            while (true) {
                tick()
                Thread.sleep(pollIntervalMillis)
            }
        }
    }

    internal fun tick() {
        val now = nowMillis()
        synchronized(lock) {
            val previousTick = lastTickAt
            lastTickAt = now
            val posted = pingPostedAt
            if (posted == null) {
                postPing(now)
                return
            }
            if (previousTick != null && now - previousTick > pollIntervalMillis * SUSPEND_GAP_FACTOR) {
                // O watchdog também ficou parado: foi o processo que dormiu.
                pingPostedAt = now
                return
            }
            if (!stallReported && now - posted >= stallThresholdMillis) {
                stallReported = true
                reportStall(now - posted)
            }
        }
    }

    private fun postPing(now: Long) {
        pingPostedAt = now
        post(Runnable { onPingServed() })
    }

    private fun onPingServed() {
        edtThread = Thread.currentThread()
        val now = nowMillis()
        synchronized(lock) {
            val posted = pingPostedAt ?: return
            if (stallReported) {
                appLog.warn("edt-watchdog", "EDT voltou a responder após ${now - posted} ms")
            }
            pingPostedAt = null
            stallReported = false
        }
    }

    private fun reportStall(stalledMillis: Long) {
        // Travado antes do primeiro ping atendido, a thread ainda não é conhecida
        // por ele: procura pelo nome que a AWT dá ao EDT.
        val edt = edtThread ?: Thread.getAllStackTraces().keys.firstOrNull { thread ->
            thread.name.startsWith(EDT_THREAD_NAME_PREFIX)
        }
        val snapshot = edt?.let { thread ->
            EdtStallSnapshot("EDT parado em ${thread.state}").apply { stackTrace = thread.stackTrace }
        }
        appLog.log(
            level = AppLogLevel.WARN,
            source = "edt-watchdog",
            message = "EDT sem responder há $stalledMillis ms",
            error = snapshot,
            threadName = edt?.name ?: "desconhecida"
        )
    }

    internal companion object {
        const val DEFAULT_STALL_THRESHOLD_MILLIS = 5_000L
        const val DEFAULT_POLL_INTERVAL_MILLIS = 1_000L
        const val SUSPEND_GAP_FACTOR = 3
    }
}

/** Carrega a pilha do EDT parado até o log; não é lançada. */
internal class EdtStallSnapshot(message: String) : Throwable(message)
