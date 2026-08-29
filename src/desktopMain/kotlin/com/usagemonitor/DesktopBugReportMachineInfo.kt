package com.usagemonitor

import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.BugReportMachineInfo
import java.awt.GraphicsEnvironment
import java.awt.Toolkit
import java.util.TimeZone

/**
 * O que esta máquina é, lido da JVM.
 *
 * **Nada aqui identifica a pessoa**: não há `user.name`, não há `user.home`, não
 * há hostname. Ao acrescentar uma leitura, a pergunta é a de
 * [BugReportMachineInfo] — isto explica um defeito ou identifica quem o reportou?
 *
 * Toda leitura opcional passa por `runCatching`: ambiente headless não tem tela e
 * uma JVM restrita pode recusar `TimeZone.getDefault()`. Campo que falha vira
 * nulo, que é "não medido"; o relatório inteiro não pode cair por causa da
 * resolução do monitor.
 */
internal fun desktopBugReportMachineInfo(
    language: AppLanguage,
    uiScalePercent: Int,
    appVersion: String = CURRENT_APP_VERSION
): BugReportMachineInfo {
    return BugReportMachineInfo(
        osName = System.getProperty("os.name").orEmpty(),
        osVersion = System.getProperty("os.version").orEmpty(),
        osArch = System.getProperty("os.arch").orEmpty(),
        javaVersion = System.getProperty("java.version").orEmpty(),
        appVersion = appVersion,
        language = language,
        uiScalePercent = uiScalePercent,
        screenResolution = screenResolution(),
        timeZoneId = runCatching { TimeZone.getDefault().id }.getOrNull()
    )
}

private fun screenResolution(): String? {
    return runCatching {
        if (GraphicsEnvironment.isHeadless()) {
            return null
        }
        val size = Toolkit.getDefaultToolkit().screenSize
        "${size.width}x${size.height}"
    }.getOrNull()
}
