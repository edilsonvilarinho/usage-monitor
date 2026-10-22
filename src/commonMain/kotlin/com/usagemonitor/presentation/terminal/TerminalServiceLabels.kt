package com.usagemonitor.presentation.terminal

import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.CollectorKind
import com.usagemonitor.presentation.ui.localized

/**
 * Os textos do serviço sem tela (#440), nos dois idiomas do app. Objeto próprio, ao
 * lado do [TerminalLabels]: o de leitura já cobre a #439 inteira, e somar a ele cada
 * comando da #440 o faria crescer sem fim.
 */
internal class TerminalServiceLabels(val language: AppLanguage) {

    fun alreadyCollecting(collector: CollectorKind): String =
        when (collector) {
            CollectorKind.APP -> localized(
                language,
                "O app já está coletando nesta pasta de dados. Só um coleta por vez: feche o app para rodar o serviço.",
                "The app is already collecting in this data folder. Only one collects at a time: close the app to run the service."
            )
            CollectorKind.SERVICE -> localized(
                language,
                "O serviço já está coletando nesta pasta de dados.",
                "The service is already collecting in this data folder."
            )
        }

    fun started(version: String, dataRoot: String): String =
        localized(
            language,
            "Usage Monitor $version coletando sem janela · dados em $dataRoot · encerre com Ctrl+C ou SIGTERM",
            "Usage Monitor $version collecting without a window · data in $dataRoot · stop with Ctrl+C or SIGTERM"
        )

    val stopped: String get() = localized(language, "Coleta encerrada.", "Collection stopped.")

    /** O app aberto num computador sem tela ficava parado em silêncio (medição da A01 da #440). */
    val noDisplay: String
        get() = localized(
            language,
            "Este computador não tem tela, e o app precisa de uma.\n" +
                "  Para ver o uso:          usage-monitor status\n" +
                "  Para coletar sem tela:   usage-monitor service run",
            "This computer has no display, and the app needs one.\n" +
                "  To see the usage:        usage-monitor status\n" +
                "  To collect without one:  usage-monitor service run"
        )
}
