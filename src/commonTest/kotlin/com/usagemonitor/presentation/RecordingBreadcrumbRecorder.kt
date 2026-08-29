package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.Breadcrumb
import com.usagemonitor.domain.entity.BreadcrumbCategory
import com.usagemonitor.domain.repository.BreadcrumbRecorder

/**
 * Recorder que só guarda o que recebeu.
 *
 * Compartilhado entre os testes de trilha dos view models: dois deles precisam
 * exatamente disto, e duas cópias divergiriam na primeira vez que a interface
 * ganhasse um método. A trilha real tem teste próprio no `desktopTest`.
 */
internal class RecordingBreadcrumbRecorder : BreadcrumbRecorder {
    val steps = mutableListOf<Pair<BreadcrumbCategory, String>>()

    val messages: List<String>
        get() = steps.map { step -> step.second }

    override fun record(category: BreadcrumbCategory, message: String) {
        steps += category to message
    }

    override fun read(limit: Int): List<Breadcrumb> = emptyList()
}
