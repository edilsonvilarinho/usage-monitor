package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.Breadcrumb
import com.usagemonitor.domain.entity.BreadcrumbCategory
import com.usagemonitor.domain.repository.BreadcrumbRecorder
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Recorder que só guarda o que recebeu.
 *
 * Compartilhado entre os testes de trilha dos view models: dois deles precisam
 * exatamente disto, e duas cópias divergiriam na primeira vez que a interface
 * ganhasse um método. A trilha real tem teste próprio no `desktopTest`.
 *
 * **`CopyOnWriteArrayList`, não `mutableListOf`.** O `DashboardViewModel` grava
 * de threads reais de `Dispatchers.Default` (uma por fonte), enquanto
 * `awaitCondition` itera `steps` na thread do teste em polling — dois threads
 * mexendo num `ArrayList` comum (um escrevendo, outro iterando) é
 * `ConcurrentModificationException` clássica, e foi exatamente o que quebrou
 * o CI do commit `938ed69` sem nenhuma mudança de produção. `record()` é
 * escrita rara e `steps` é lido em loop apertado — o caso exato para o qual
 * `CopyOnWriteArrayList` existe.
 */
internal class RecordingBreadcrumbRecorder : BreadcrumbRecorder {
    val steps: MutableList<Pair<BreadcrumbCategory, String>> = CopyOnWriteArrayList()

    val messages: List<String>
        get() = steps.map { step -> step.second }

    override fun record(category: BreadcrumbCategory, message: String) {
        steps += category to message
    }

    override fun read(limit: Int): List<Breadcrumb> = emptyList()
}
