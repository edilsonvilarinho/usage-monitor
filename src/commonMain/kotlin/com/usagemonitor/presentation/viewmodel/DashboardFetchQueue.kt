package com.usagemonitor.presentation.viewmodel

import com.usagemonitor.domain.entity.UsageTargetKey
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Serializa as coletas do [DashboardViewModel]: uma roda por vez, e os pedidos
 * que chegam durante ela são fundidos num só (`mergePendingFetch`) e drenados em
 * seguida. Saiu do view model pelo limite de 800 linhas (#269) sem mudar nada.
 */
internal class DashboardFetchQueue(
    private val allTargets: () -> Set<UsageTargetKey>,
    private val perform: suspend (targets: Set<UsageTargetKey>, preserveDataOnFailure: Boolean) -> Unit
) {
    private val fetchMutex = Mutex()
    private val pendingFetchMutex = Mutex()
    private var pendingFetchRequest: PendingFetchRequest? = null

    suspend fun request(targets: Set<UsageTargetKey>, preserveDataOnFailure: Boolean = false) {
        if (!fetchMutex.tryLock()) {
            enqueue(targets, preserveDataOnFailure)
            fetchMutex.withLock {
                drainPending() ?: return
            }
            return
        }

        try {
            drain(PendingFetchRequest(targets, preserveDataOnFailure))
        } finally {
            fetchMutex.unlock()
        }
    }

    private suspend fun drain(initialRequest: PendingFetchRequest) {
        var currentRequest: PendingFetchRequest? = initialRequest

        while (currentRequest != null) {
            perform(currentRequest.targets, currentRequest.preserveDataOnFailure)
            currentRequest = dequeue()
        }
    }

    private suspend fun drainPending(): PendingFetchRequest? {
        val pendingRequest = dequeue() ?: return null
        drain(pendingRequest)
        return pendingRequest
    }

    private suspend fun enqueue(targets: Set<UsageTargetKey>, preserveDataOnFailure: Boolean) {
        pendingFetchMutex.withLock {
            pendingFetchRequest = mergePendingFetch(
                existing = pendingFetchRequest,
                targets = targets,
                preserveDataOnFailure = preserveDataOnFailure,
                allTargets = allTargets
            )
        }
    }

    private suspend fun dequeue(): PendingFetchRequest? {
        return pendingFetchMutex.withLock {
            val request = pendingFetchRequest ?: return@withLock null
            pendingFetchRequest = null
            request
        }
    }
}
