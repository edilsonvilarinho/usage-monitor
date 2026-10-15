package com.usagemonitor

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/**
 * A sonda de coroutine de `UncaughtExceptionRoutingTest`, rodada numa JVM própria.
 *
 * Dentro da JVM da suíte, o `kotlinx-coroutines-test` registra um coletor global
 * de exceções: a falha proposital desta sonda ficava guardada nele e estourava no
 * próximo `runTest` de outra classe (`UncaughtExceptionsBeforeTest`). Numa JVM
 * em que nenhum `runTest` rodou, o coletor fica inerte — que é a situação do app
 * em produção, onde ele nem está no classpath.
 *
 * Imprime `origem|mensagem` e sai com 0; sem falha recebida em 5 s, sai com 2.
 */
internal object CoroutineRoutingProbeMain {
    @JvmStatic
    fun main(args: Array<String>) {
        val latch = CountDownLatch(1)
        Thread.setDefaultUncaughtExceptionHandler { failedThread, error ->
            println("${uncaughtOriginOf(failedThread, error).wireValue}|${error.message}")
            System.out.flush()
            latch.countDown()
        }
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            throw IllegalStateException("sonda-coroutine")
        }
        exitProcess(if (latch.await(5, TimeUnit.SECONDS)) 0 else 2)
    }
}
