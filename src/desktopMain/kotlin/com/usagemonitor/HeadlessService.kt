package com.usagemonitor

import com.usagemonitor.data.AppDataDirectory
import com.usagemonitor.data.datasource.LocalCollectorRegistrationDataSource
import com.usagemonitor.data.diagnostics.AppLogFile
import com.usagemonitor.data.diagnostics.LocalBreadcrumbRecorder
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.CollectorKind
import com.usagemonitor.presentation.terminal.TerminalServiceLabels
import com.usagemonitor.update.AutoUpdateController
import com.usagemonitor.update.currentUpdatePlatform
import java.util.concurrent.CountDownLatch
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * `usage-monitor service run` (#440): o [CollectorEngine] sem janela, em primeiro plano,
 * até o `SIGTERM` (ou Ctrl+C). É o que o serviço do systemd executa.
 *
 * O que o arranque do app faz e o serviço também precisa: trava de instância única
 * (um coletor por pasta de dados), `app.log`, trilha de eventos e tratador de falha. O
 * que fica de fora: janela, bandeja, foco de segunda instância e atualização
 * automática — no servidor, a versão é a do pacote (apt, dnf ou o script do Linux).
 */
internal object HeadlessService {

    private const val LOG_SOURCE = "service"
    private const val EXIT_ALREADY_COLLECTING = 1

    fun run(languageOverride: AppLanguage?, labels: TerminalServiceLabels, print: (String) -> Unit): Int {
        // Antes de qualquer classe da AWT: o motor não abre janela, e num servidor sem
        // bibliotecas gráficas a AWT nem carregaria (medição da A01).
        System.setProperty("java.awt.headless", "true")
        val guard = SingleInstanceGuard.acquireAtStartup()
        if (guard == null) {
            print(labels.alreadyCollecting(LocalCollectorRegistrationDataSource().read()?.kind ?: CollectorKind.APP))
            return EXIT_ALREADY_COLLECTING
        }
        val appLog = AppLogFile()
        val breadcrumbs = LocalBreadcrumbRecorder(errorSink = { message -> appLog.error("breadcrumb", message) })
        CrashHandler(breadcrumbs = breadcrumbs, appLog = appLog).install()

        val graph = AppGraph(breadcrumbs, languageOverride)
        // Sem janela para ancorar: o bot pode confirmar um reinício assim que o motor sobe.
        graph.appAnchored.value = true
        val engine = CollectorEngine(graph, headlessAutoUpdate(), CollectorKind.SERVICE)

        val stopped = CountDownLatch(1)
        Runtime.getRuntime().addShutdownHook(
            Thread {
                appLog.info(LOG_SOURCE, "encerrando a coleta")
                engine.shutdown(guard)
                print(labels.stopped)
                stopped.countDown()
            }
        )
        appLog.info(LOG_SOURCE, "coleta sem janela iniciada, versão $CURRENT_APP_VERSION, pid ${ProcessHandle.current().pid()}")
        print(labels.started(CURRENT_APP_VERSION, AppDataDirectory.root.path))
        stopped.await()
        return 0
    }

    /**
     * Sem instalador e desligada: o serviço só anuncia versão nova (o bot avisa), e quem
     * atualiza é o gerenciador de pacotes.
     */
    private fun headlessAutoUpdate(): AutoUpdateController =
        AutoUpdateController(
            installer = null,
            platform = currentUpdatePlatform(),
            enabled = MutableStateFlow(false),
            lastReceipt = null,
            feedUrlOverride = null,
            persist = {}
        )
}
