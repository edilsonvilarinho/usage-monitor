package com.usagemonitor.data.repository

import kotlinx.coroutines.CancellationException

/**
 * Substitui `runCatching` em chamadas `suspend` dos repositórios.
 *
 * `runCatching` captura `Throwable`, inclusive `CancellationException`, e a devolve como
 * `Result.failure`, o que faz o cancelamento do escopo sumir. Aqui o cancelamento é relançado;
 * as demais falhas continuam virando `Result.failure`.
 */
inline fun <T> suspendRunCatching(block: () -> T): Result<T> {
    return try {
        Result.success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Throwable) {
        Result.failure(failure)
    }
}
