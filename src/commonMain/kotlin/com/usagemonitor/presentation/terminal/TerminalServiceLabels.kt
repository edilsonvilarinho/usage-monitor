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

    val onlyLinux: String
        get() = localized(
            language,
            "O serviço do sistema só existe no Linux (systemd). No Windows e no macOS, o app inicia com o sistema:",
            "The system service only exists on Linux (systemd). On Windows and macOS, the app starts with the system:"
        )

    val customDataRoot: String
        get() = localized(
            language,
            "Com --user-data-dir, o serviço não é instalado: iniciar com o sistema só vale na pasta de dados padrão (#419). Para coletar nesta pasta, rode em primeiro plano:",
            "With --user-data-dir, the service is not installed: starting with the system only applies to the default data folder (#419). To collect in this folder, run it in the foreground:"
        )

    val notInstalledApp: String
        get() = localized(language, "Este Usage Monitor não foi instalado (sem o caminho do app). Instale pelo pacote ou pelo script do Linux.", "This Usage Monitor was not installed (no app path). Install it from the package or the Linux script.")

    fun noSystemd(detail: String): String =
        localized(language, "O systemd do usuário não respondeu (systemctl --user", "The user systemd did not answer (systemctl --user") +
            (if (detail.isNotBlank()) ": $detail" else "") +
            localized(language, "). Numa sessão sem login gráfico, ele precisa do linger:", "). In a session without a graphical login, it needs linger:")

    fun unitWritten(path: String): String = localized(language, "serviço criado em $path", "service created at $path")
    val enabledAndStarted: String get() = localized(language, "ativo e marcado para iniciar com o sistema", "active and set to start with the system")
    val lingerHint: String get() = localized(language, "Para continuar rodando depois que você sair do SSH, rode uma vez:", "To keep running after you leave SSH, run once:")
    val appCollecting: String get() = localized(language, "O app está aberto e coletando: o serviço assume quando ele fechar.", "The app is open and collecting: the service takes over when it closes.")
    val removed: String get() = localized(language, "serviço removido", "service removed")
    val notInstalled: String get() = localized(language, "O serviço não está instalado. Instale com:", "The service is not installed. Install it with:")

    fun actionDone(action: String): String =
        when (action) {
            "start" -> localized(language, "serviço iniciado", "service started")
            "stop" -> localized(language, "serviço parado", "service stopped")
            else -> localized(language, "serviço reiniciado", "service restarted")
        }

    fun actionFailed(output: String): String = localized(language, "O systemd recusou: $output", "systemd refused: $output")

    val appLogNote: String get() = localized(language, "O log do próprio app:", "The app's own log:")
}
