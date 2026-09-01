package com.usagemonitor.presentation.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.ProxySettings
import com.usagemonitor.presentation.ui.theme.AppSpacing

/** Resultado do "Testar conexão" da seção de proxy — par próprio, não o de Time. */
enum class ProxyConnectionUiStatus { IDLE, CHECKING, OK, FAILED }

data class ProxyConnectionUiState(
    val status: ProxyConnectionUiStatus = ProxyConnectionUiStatus.IDLE,
    val message: String? = null
)

const val NETWORK_SECTION_TEST_TAG = "networkProxySection"
const val NETWORK_USE_ENV_SWITCH_TEST_TAG = "networkProxyUseEnvSwitch"
const val NETWORK_HOST_FIELD_TEST_TAG = "networkProxyHostField"
const val NETWORK_PORT_FIELD_TEST_TAG = "networkProxyPortField"
const val NETWORK_USERNAME_FIELD_TEST_TAG = "networkProxyUsernameField"
const val NETWORK_TEST_CONNECTION_TEST_TAG = "networkProxyTestConnection"

/**
 * Seção "Proxy" da aba Rede (issue #174).
 *
 * Stateless como o resto do diálogo: [settings] chega pronto e os eventos saem
 * pelas lambdas. Diferente da aba Time, não há interruptor "ligar a integração
 * inteira" — [ProxySettings.useEnvironmentProxy] já decide entre autodetectar
 * `HTTP_PROXY`/`HTTPS_PROXY` (default) e usar os campos manuais abaixo.
 *
 * **A configuração só é aplicada ao reiniciar o app.** O `HttpClient`
 * compartilhado é montado uma única vez no arranque, com o proxy já resolvido
 * — recriar o engine em runtime arriscaria `ClosedException` em requisição
 * in-flight de qualquer um dos consumidores que o compartilham (dashboard,
 * sincronização de time, atualização automática). "Testar conexão" usa um
 * `HttpClient` efêmero, montado com os valores do formulário mesmo antes de
 * salvos — é a única forma de dar feedback sem esperar o reinício.
 */
@Composable
fun NetworkSettingsSection(
    settings: ProxySettings,
    language: AppLanguage,
    connection: ProxyConnectionUiState,
    onUseEnvironmentProxyChange: (Boolean) -> Unit,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onTestConnection: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isPt = language == AppLanguage.PT

    AppDataSurfaceFlush(
        modifier = modifier.fillMaxWidth().testTag(NETWORK_SECTION_TEST_TAG),
        header = { AppSectionHeader(title = if (isPt) "Proxy" else "Proxy") }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(AppSpacing.md),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
        ) {
            Text(
                text = if (isPt) {
                    "Necessário em rede corporativa com proxy HTTP obrigatório. As " +
                        "alterações valem só depois de reiniciar o app."
                } else {
                    "Needed on a corporate network with a mandatory HTTP proxy. " +
                        "Changes only take effect after restarting the app."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isPt) {
                        "Usar variável de ambiente do sistema (HTTP_PROXY/HTTPS_PROXY)"
                    } else {
                        "Use the system environment variable (HTTP_PROXY/HTTPS_PROXY)"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                AppSwitch(
                    checked = settings.useEnvironmentProxy,
                    onCheckedChange = onUseEnvironmentProxyChange,
                    modifier = Modifier.testTag(NETWORK_USE_ENV_SWITCH_TEST_TAG)
                )
            }

            if (settings.useEnvironmentProxy) {
                return@Column
            }

            DebouncedTextField(
                value = settings.host,
                label = if (isPt) "Host" else "Host",
                placeholder = "proxy.empresa.com",
                onCommit = onHostChange,
                modifier = Modifier.testTag(NETWORK_HOST_FIELD_TEST_TAG)
            )

            DebouncedTextField(
                value = if (settings.port > 0) settings.port.toString() else "",
                label = if (isPt) "Porta" else "Port",
                placeholder = "8080",
                onCommit = onPortChange,
                validate = { text ->
                    val port = text.toIntOrNull()
                    if (text.isNotBlank() && (port == null || port !in 1..65535)) {
                        if (isPt) "Porta deve ser um número entre 1 e 65535." else "Port must be a number between 1 and 65535."
                    } else {
                        null
                    }
                },
                modifier = Modifier.testTag(NETWORK_PORT_FIELD_TEST_TAG)
            )

            DebouncedTextField(
                value = settings.username,
                label = if (isPt) "Usuário (opcional)" else "Username (optional)",
                onCommit = onUsernameChange,
                modifier = Modifier.testTag(NETWORK_USERNAME_FIELD_TEST_TAG)
            )

            DebouncedSecretField(
                value = settings.password,
                label = if (isPt) "Senha (opcional)" else "Password (optional)",
                revealLabel = if (isPt) "Mostrar senha" else "Show password",
                hideLabel = if (isPt) "Ocultar senha" else "Hide password",
                onCommit = onPasswordChange
            )

            // NTLM e proxy com CA própria não são suportados — só autenticação
            // Basic. Documentado aqui porque é o único lugar em que quem
            // configura o proxy vê essa limitação antes de tentar usá-la.
            Text(
                text = if (isPt) {
                    "Suporta apenas autenticação Basic. NTLM e proxy com CA própria não são suportados."
                } else {
                    "Only Basic authentication is supported. NTLM and proxies with a custom CA are not."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AppButton(
                    label = if (isPt) "Testar conexão" else "Test connection",
                    onClick = onTestConnection,
                    enabled = settings.isManualConfigured && connection.status != ProxyConnectionUiStatus.CHECKING,
                    modifier = Modifier.testTag(NETWORK_TEST_CONNECTION_TEST_TAG)
                )

                if (connection.status == ProxyConnectionUiStatus.CHECKING) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                }

                val statusMessage = connection.message
                if (statusMessage != null) {
                    AppStatusIndicator(
                        label = statusMessage,
                        tone = proxyConnectionTone(connection.status),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/** Enquanto checa não há veredito: neutro. Sem tentativa, também não. */
private fun proxyConnectionTone(status: ProxyConnectionUiStatus): AppTone {
    return when (status) {
        ProxyConnectionUiStatus.OK -> AppTone.OK
        ProxyConnectionUiStatus.FAILED -> AppTone.CRITICAL
        ProxyConnectionUiStatus.CHECKING, ProxyConnectionUiStatus.IDLE -> AppTone.NEUTRAL
    }
}
