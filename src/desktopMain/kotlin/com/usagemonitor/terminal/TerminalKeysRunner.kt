package com.usagemonitor.terminal

import com.usagemonitor.ConfigChange
import com.usagemonitor.ConfigRefusals
import com.usagemonitor.buildHttpClient
import com.usagemonitor.data.datasource.RemoteApiDataSource
import com.usagemonitor.data.repository.resolveEffectiveProxy
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ProxySettings
import com.usagemonitor.domain.entity.displayName
import com.usagemonitor.presentation.terminal.TerminalConfigLabels
import com.usagemonitor.presentation.terminal.TerminalConfigView
import com.usagemonitor.presentation.terminal.TerminalOutput
import com.usagemonitor.presentation.terminal.TerminalProfile
import com.usagemonitor.presentation.ui.components.ApiKeyCheckStatus
import com.usagemonitor.presentation.ui.components.AppTone
import com.usagemonitor.presentation.ui.components.apiKeyCheckResult
import com.usagemonitor.testApiKeyUsage
import kotlinx.coroutines.runBlocking

/**
 * `keys set|test|remove` (#440, aba APIs). O teste é a coleta real da fonte com a chave
 * candidata (`testApiKeyUsage`), e o veredito é o mesmo das Configurações
 * (`apiKeyCheckResult`): chave recusada não é gravada; teste sem veredito (429, sem
 * assinatura) grava com a ressalva.
 */
internal class TerminalKeysRunner(
    private val client: TerminalConfigClient,
    private val secretInput: TerminalSecretInput,
    /** A chave salva hoje, para o `test`. */
    private val savedKey: (ApiSource) -> String?,
    /** A falha da coleta com a chave, ou `null` quando ela leu o uso. */
    private val probe: (ApiSource, String) -> Throwable?
) {

    fun run(command: KeysCommand, labels: TerminalConfigLabels, profile: TerminalProfile): TerminalOutput {
        val glyphs = profile.glyphs
        val name = command.source.displayName()
        val setCommand = "usage-monitor keys set ${command.source.terminalId()}"
        return when (command) {
            is KeysCommand.Set -> {
                val apiKey = secretInput.read(labels.keyPrompt(name))?.trim()?.takeIf { key -> key.isNotEmpty() }
                    ?: return TerminalOutput(TerminalConfigView.warning(labels.noSecretGiven, null, glyphs), TerminalConfigView.EXIT_REFUSED)
                val verdict = if (command.skipTest) null else apiKeyCheckResult(command.source, probe(command.source, apiKey), labels.language)
                if (verdict != null && verdict.status != ApiKeyCheckStatus.OK && verdict.tone == AppTone.CRITICAL) {
                    val lines = TerminalConfigView.warning(labels.keyRejected(verdict.message.orEmpty()), null, glyphs) +
                        TerminalConfigView.warning(labels.skipTestHint, "$setCommand --skip-test", glyphs)
                    return TerminalOutput(lines, TerminalConfigView.EXIT_REFUSED)
                }
                val saved = delivered(client.apply(ConfigChange.SetApiKey(command.source.name, apiKey)), labels, profile, labels.keySaved(name)) { null }
                val note = when {
                    verdict == null -> listOf(TerminalConfigView.note(labels.keyNotTested))
                    verdict.status == ApiKeyCheckStatus.OK -> listOf(TerminalConfigView.note(verdict.message.orEmpty()))
                    else -> TerminalConfigView.warning(labels.keyTestedWithWarning(verdict.message.orEmpty()), null, glyphs)
                }
                if (saved.exitCode == TerminalConfigView.EXIT_OK) TerminalOutput(saved.lines + note, saved.exitCode) else saved
            }
            is KeysCommand.Test -> {
                val apiKey = savedKey(command.source)
                    ?: return TerminalOutput(TerminalConfigView.warning(labels.noSavedKey(name), setCommand, glyphs), TerminalConfigView.EXIT_REFUSED)
                val verdict = apiKeyCheckResult(command.source, probe(command.source, apiKey), labels.language)
                if (verdict.status == ApiKeyCheckStatus.OK) {
                    TerminalOutput(listOf(TerminalConfigView.done(verdict.message.orEmpty(), name, glyphs)), TerminalConfigView.EXIT_OK)
                } else {
                    TerminalOutput(TerminalConfigView.warning(verdict.message.orEmpty(), null, glyphs), TerminalConfigView.EXIT_REFUSED)
                }
            }
            is KeysCommand.Remove ->
                delivered(client.apply(ConfigChange.RemoveApiKey(command.source.name)), labels, profile, labels.keyRemoved(name)) { refused ->
                    if (refused.code == ConfigRefusals.UNKNOWN_SOURCE) TerminalConfigView.warning(labels.failed(refused.detail), null, glyphs) else null
                }
        }
    }

    companion object {
        /**
         * A coleta real com a chave candidata, num client efêmero com o proxy que a coleta
         * usaria, como o "Testar chave" das Configurações. A chave não é gravada aqui.
         */
        fun realProbe(proxy: () -> ProxySettings): (ApiSource, String) -> Throwable? = { source, apiKey ->
            runBlocking {
                runCatching {
                    val httpClient = buildHttpClient(resolveEffectiveProxy(proxy()))
                    try {
                        testApiKeyUsage(source, RemoteApiDataSource(httpClient = httpClient)) { apiKey }.exceptionOrNull()
                    } finally {
                        httpClient.close()
                    }
                }.getOrElse { error -> error }
            }
        }
    }
}
