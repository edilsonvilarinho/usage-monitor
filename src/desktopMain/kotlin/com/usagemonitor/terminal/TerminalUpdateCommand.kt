package com.usagemonitor.terminal

import com.russhwolf.settings.PreferencesSettings
import com.usagemonitor.CURRENT_APP_VERSION
import com.usagemonitor.buildHttpClient
import com.usagemonitor.data.AppDataDirectory
import com.usagemonitor.data.datasource.LocalProxySettingsDataSource
import com.usagemonitor.data.datasource.RemoteApiDataSource
import com.usagemonitor.data.repository.AppUpdateRepositoryImpl
import com.usagemonitor.data.repository.resolveEffectiveProxy
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.AppUpdateInfo
import com.usagemonitor.domain.entity.CollectorKind
import com.usagemonitor.domain.usecase.CheckForAppUpdateUseCase
import com.usagemonitor.presentation.terminal.TerminalAnsi
import com.usagemonitor.presentation.terminal.TerminalConfigView
import com.usagemonitor.presentation.terminal.TerminalLine
import com.usagemonitor.presentation.terminal.TerminalSetupLabels
import com.usagemonitor.readPersistedReceiveBetaUpdates
import java.io.File
import java.util.prefs.Preferences
import kotlinx.coroutines.runBlocking

/**
 * `usage-monitor update` (4.4, aba Sistema): confere a versão nova, com o canal beta das
 * preferências (`updates.beta`), e diz como atualizar esta instalação. Não instala: no
 * servidor quem atualiza é o pacote ou o script, e o app aberto já se atualiza sozinho.
 */
internal class TerminalUpdateCommand(
    private val check: (includeBeta: Boolean) -> Result<AppUpdateInfo?> = { includeBeta ->
        runBlocking {
            val httpClient = buildHttpClient(resolveEffectiveProxy(LocalProxySettingsDataSource().load()))
            try {
                CheckForAppUpdateUseCase(AppUpdateRepositoryImpl(RemoteApiDataSource(httpClient)))(CURRENT_APP_VERSION, includeBeta)
            } finally {
                httpClient.close()
            }
        }
    },
    private val preferences: Preferences = Preferences.userRoot().node(AppDataDirectory.preferencesNodeName),
    private val reader: TerminalReader = TerminalReader(),
    private val appPath: String? = System.getProperty("jpackage.app-path")
) {

    fun run(languageOverride: AppLanguage?, console: TerminalConsole): Int {
        val labels = TerminalSetupLabels(reader.language(languageOverride))
        val glyphs = console.profile.glyphs
        fun print(lines: List<TerminalLine>) = console.print(TerminalAnsi.render(lines, console.profile))
        val update = check(readPersistedReceiveBetaUpdates(PreferencesSettings(preferences))).getOrElse { error ->
            print(TerminalConfigView.warning(labels.checkFailed(error.message ?: error::class.simpleName.orEmpty()), null, glyphs))
            return 1
        }
        if (update == null) {
            print(listOf(TerminalConfigView.done(labels.upToDate(CURRENT_APP_VERSION), null, glyphs)))
            return 0
        }
        val lines = TerminalConfigView.warning(labels.available(update.version, CURRENT_APP_VERSION), null, glyphs).toMutableList()
        val canonical = appPath?.let(::portablePath)
        lines += when {
            canonical?.contains("/opt/usage-monitor/") == true -> listOf(TerminalConfigView.note(labels.updatePackage), TerminalConfigView.commandLine(update.releasePageUrl))
            canonical?.contains("/usage-monitor/versions/") == true -> listOf(
                TerminalConfigView.note(labels.updateScript),
                TerminalConfigView.commandLine("curl -fsSL $RELEASES/v${update.version}/install-usage-monitor_${update.version}_linux_x64.sh | sh")
            )
            else -> listOf(TerminalConfigView.note(labels.updateApp), TerminalConfigView.commandLine(update.releasePageUrl))
        }
        if (reader.collector() == CollectorKind.SERVICE) {
            lines += TerminalConfigView.note(labels.restartAfter)
            lines += TerminalConfigView.commandLine("usage-monitor service restart")
        }
        print(lines)
        return 0
    }

    private companion object {
        const val RELEASES = "https://github.com/edilsonvilarinho/usage-monitor/releases/download"
    }
}
