package com.usagemonitor

import com.usagemonitor.data.datasource.LocalWebAccessSettingsDataSource
import com.usagemonitor.domain.entity.WebAccessSettings
import com.usagemonitor.domain.entity.isValidWebAccessPort
import com.usagemonitor.presentation.ui.components.WebAccessSectionModel
import com.usagemonitor.presentation.ui.components.WebAccessUiStatus
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Ações da seção "Acesso pela rede local" (#388). Arquivo próprio para o
 * `SettingsActions` não crescer: a seção tem estado e regras suas (token, porta).
 */
internal class WebAccessActions(
    private val dataSource: LocalWebAccessSettingsDataSource,
    private val settingsFlow: MutableStateFlow<WebAccessSettings>
) {
    /** Ligar pela primeira vez gera o token: sem ele o servidor não sobe. */
    fun setEnabled(enabled: Boolean) {
        update { current ->
            val token = current.token.ifBlank { LocalWebAccessSettingsDataSource.newToken() }
            current.copy(enabled = enabled, token = token)
        }
    }

    /** Só aceita porta válida; texto incompleto não derruba o servidor no meio da digitação. */
    fun changePort(text: String) {
        val port = text.trim().toIntOrNull() ?: return
        if (!isValidWebAccessPort(port)) {
            return
        }
        update { current -> current.copy(port = port) }
    }

    /** Token novo invalida todo link já copiado — é para isso que o botão existe. */
    fun regenerateToken() {
        update { current -> current.copy(token = LocalWebAccessSettingsDataSource.newToken()) }
    }

    private fun update(transform: (WebAccessSettings) -> WebAccessSettings) {
        val next = transform(settingsFlow.value)
        runCatching { dataSource.save(next) }
        settingsFlow.value = next
    }
}

/** O modelo da seção a partir das configurações gravadas e do estado do servidor. */
internal fun webAccessSectionModel(
    settings: WebAccessSettings,
    status: WebAccessStatus,
    actions: WebAccessActions
): WebAccessSectionModel {
    val running = status as? WebAccessStatus.Running
    return WebAccessSectionModel(
        enabled = settings.enabled,
        portText = settings.port.toString(),
        status = when (status) {
            is WebAccessStatus.Running -> WebAccessUiStatus.RUNNING
            is WebAccessStatus.Failed -> WebAccessUiStatus.FAILED
            WebAccessStatus.Stopped -> WebAccessUiStatus.STOPPED
        },
        urls = running?.addresses?.map { address -> "http://$address:${running.port}/?t=${settings.token}" }.orEmpty(),
        failureMessage = (status as? WebAccessStatus.Failed)?.message,
        onEnabledChange = actions::setEnabled,
        onPortChange = actions::changePort,
        onRegenerateToken = actions::regenerateToken
    )
}
