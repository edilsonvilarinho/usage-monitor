package com.usagemonitor.terminal

import com.russhwolf.settings.PreferencesSettings
import com.usagemonitor.LanguageSelection
import com.usagemonitor.SingleInstanceGuard
import com.usagemonitor.data.AppDataDirectory
import com.usagemonitor.data.datasource.LocalDashboardCacheDataSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.terminal.TerminalAccount
import com.usagemonitor.presentation.terminal.terminalAccounts
import com.usagemonitor.readPersistedAlertSettings
import java.io.File
import java.util.prefs.Preferences
import kotlinx.coroutines.runBlocking
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * O que os comandos de leitura do modo terminal mostram (#439): a última leitura de
 * cada conta, o idioma e se há coletor vivo.
 */
internal data class TerminalReading(
    val accounts: List<TerminalAccount>,
    val collectorRunning: Boolean,
    val language: AppLanguage,
    val now: Instant
) {
    /** A coleta mais recente entre as contas; `null` sem leitura nenhuma. */
    val lastCollectedAt: Instant?
        get() = accounts.mapNotNull { account -> account.fetchedAt }.maxOrNull()
}

/**
 * Lê sem subir nada do app: nem `HttpClient`, nem view model, nem Compose. Imprimir uma
 * linha pagando a interface inteira tornaria a linha de status lenta.
 *
 * A leitura pronta é o `dashboard-cache.json` que o app grava a cada coleta (decisão D2
 * do plano da #439), lido pelo mesmo data source e pelo mesmo mapper que reidratam a
 * HUD. Os limiares e o idioma vêm das mesmas preferências do app. Nada aqui faz
 * requisição, renova login ou segura a trava de instância.
 */
internal class TerminalReader(
    private val cacheFile: File? = null,
    private val lockFile: File? = null,
    private val settings: PreferencesSettings = PreferencesSettings(Preferences.userRoot().node(AppDataDirectory.preferencesNodeName)),
    private val clock: Clock = Clock.System
) {
    fun read(languageOverride: AppLanguage?): TerminalReading {
        val dataSource = cacheFile?.let(::LocalDashboardCacheDataSource) ?: LocalDashboardCacheDataSource()
        val stats = runBlocking { dataSource.load() }
        val now = clock.now()
        return TerminalReading(
            accounts = terminalAccounts(stats, readPersistedAlertSettings(settings), now),
            collectorRunning = lockFile?.let(SingleInstanceGuard::isHeld) ?: SingleInstanceGuard.isHeld(),
            language = LanguageSelection(settings, languageOverride).current(),
            now = now
        )
    }
}
