package com.usagemonitor.domain.usecase

import com.usagemonitor.domain.entity.BugReportEnvelope
import com.usagemonitor.domain.entity.BugReportMachineInfo
import com.usagemonitor.domain.repository.BreadcrumbRecorder
import kotlinx.datetime.Clock

/**
 * Monta o pacote de diagnóstico: descrição do usuário + máquina + trilha lida.
 *
 * **Não grava nada e não abre nada.** Ele devolve um [BugReportEnvelope], que é
 * texto; quem escreve em disco e quem abre o navegador são camadas de fora, e
 * quem publica é o usuário. Essa separação é o que faz "nada sai da máquina
 * sozinho" ser uma propriedade do desenho, e não uma promessa.
 */
class GenerateBugReportUseCase(
    private val breadcrumbs: BreadcrumbRecorder,
    /**
     * Informação de máquina lida **na hora**, e não capturada na construção.
     *
     * Idioma e escala da interface mudam nas Configurações enquanto o app roda;
     * congelá-los no arranque faria o relatório descrever um estado que já não é
     * o do momento da falha. Mesmo motivo do idioma injetado no
     * `DesktopUsageExportWriter`.
     */
    private val machineInfo: () -> BugReportMachineInfo,
    /**
     * Quantos passos ler da trilha.
     *
     * Sem default de propósito: quem conhece o tamanho do arquivo é quem o
     * escreve, e um número aqui seria um terceiro dono do corte que
     * `StartupDiagnostics` já possui e `LocalBreadcrumbRecorder` já reusa.
     */
    private val breadcrumbLimit: Int,
    private val clock: Clock = Clock.System
) {

    operator fun invoke(description: String): BugReportEnvelope {
        return BugReportEnvelope(
            // O texto vai como o usuário escreveu, só sem o espaço das bordas: a
            // descrição é a parte do relatório que ninguém mais pode reescrever.
            description = description.trim(),
            machineInfo = machineInfo(),
            capturedAt = clock.now(),
            breadcrumbs = breadcrumbs.read(breadcrumbLimit)
        )
    }
}
