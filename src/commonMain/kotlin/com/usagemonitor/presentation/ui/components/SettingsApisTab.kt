package com.usagemonitor.presentation.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.displayName
import com.usagemonitor.presentation.ui.theme.AppSpacing

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MonitoredApisTab(
    currentLanguage: AppLanguage,
    enabledApis: Set<ApiSource>,
    configuredApiKeys: Set<ApiSource>,
    onApiToggle: (ApiSource, Boolean) -> Unit,
    onApiKeySave: (ApiSource, String) -> Boolean,
    onApiKeyRemove: (ApiSource) -> Boolean,
    apiKeyCheck: ApiKeyCheckUiState,
    onApiKeyTest: (ApiSource, String) -> Unit,
    onApiKeyCheckReset: () -> Unit
) {
    var pendingApiKeySource by remember { mutableStateOf<ApiSource?>(null) }

    AppDataSurfaceFlush(
        header = {
            AppSectionHeader(
                title = if (currentLanguage == AppLanguage.PT) "APIs monitoradas" else "Monitored APIs"
            )
        }
    ) {
        ApiSelector(
            enabledApis = enabledApis,
            configuredApiKeys = configuredApiKeys,
            // `requiresApiKey` continua sendo o dono da resposta, e não um
            // literal novo: o conjunto das fontes que dependem de chave já tem
            // dois donos — este e o filtro de arranque —, e um terceiro seria
            // onde a fonte seguinte ficaria esquecida.
            editableApiKeys = EDITABLE_API_KEY_SOURCES,
            language = currentLanguage,
            onToggle = { api, checked ->
                if (checked && api.requiresApiKey() && api !in configuredApiKeys) {
                    onApiKeyCheckReset()
                    pendingApiKeySource = api
                } else {
                    onApiToggle(api, checked)
                }
            },
            // Pelo lápis o diálogo abre com a fonte já configurada, que é o
            // caminho que não existia: até aqui a chave só era pedida ao
            // **ligar** uma fonte sem chave, e depois disso era definitiva.
            onEditApiKey = { api ->
                // O veredito que sobrou da fonte anterior descreveria outra chave.
                onApiKeyCheckReset()
                pendingApiKeySource = api
            }
        )
    }

    val source = pendingApiKeySource
    if (source != null) {
        ApiKeyDialog(
            source = source,
            language = currentLanguage,
            onSave = { apiKey ->
                if (onApiKeySave(source, apiKey)) {
                    // Fonte já ligada não é religada. Reafirmar o mesmo conjunto
                    // regravaria a preferência, dispararia uma segunda coleta e
                    // trocaria o aviso de "chave de API salva" pelo de "APIs
                    // monitoradas" — que não é o que quem trocou a chave fez.
                    // Ligar continua acontecendo no caminho original, o de
                    // configurar uma fonte que estava desligada.
                    if (source !in enabledApis) {
                        onApiToggle(source, true)
                    }
                    pendingApiKeySource = null
                }
            },
            // Sem chave guardada não há o que remover: o diálogo também abre no
            // caminho de ligar uma fonte que nunca foi configurada.
            onRemove = if (source in configuredApiKeys) {
                {
                    if (onApiKeyRemove(source)) {
                        pendingApiKeySource = null
                    }
                }
            } else {
                null
            },
            hasStoredKey = source in configuredApiKeys,
            checkState = apiKeyCheck,
            onTest = { candidateKey -> onApiKeyTest(source, candidateKey) },
            onCheckReset = onApiKeyCheckReset,
            onDismiss = {
                onApiKeyCheckReset()
                pendingApiKeySource = null
            }
        )
    }
}

/**
 * Fontes cuja linha desta tela ganha o lápis de gerenciar chave.
 *
 * **Não é um segundo dono do conjunto**, e o nome diz isso: a resposta continua
 * saindo de [requiresApiKey], e este val é só a projeção dela em `Set` para o
 * [ApiSelector]. O nome `API_KEY_DEPENDENT_SOURCES` está tomado pelo literal do
 * `AppPreferenceKeys.kt`, que é o filtro de arranque; dois símbolos com o mesmo nome fariam
 * quem lê os dois concluir que são a mesma constante duplicada, quando um é
 * literal e o outro é derivado.
 */
private val EDITABLE_API_KEY_SOURCES: Set<ApiSource> =
    ApiSource.entries.filter { source -> source.requiresApiKey() }.toSet()

private fun ApiSource.requiresApiKey(): Boolean {
    return this == ApiSource.MINIMAX ||
        this == ApiSource.DEEPSEEK ||
        this == ApiSource.OPENCODE_GO ||
        this == ApiSource.OPENROUTER
}

@Composable
private fun ApiKeyDialog(
    source: ApiSource,
    language: AppLanguage,
    onSave: (String) -> Unit,
    /**
     * Apaga a chave guardada. `null` quando não há nenhuma — o diálogo também
     * abre no caminho de **ligar** uma fonte que nunca foi configurada, e ali um
     * botão de remover não teria o que remover.
     */
    onRemove: (() -> Unit)?,
    /**
     * Há chave guardada para esta fonte. Só isso torna possível testar com o
     * campo vazio — sem chave nenhuma o botão não teria o que enviar.
     */
    hasStoredKey: Boolean,
    checkState: ApiKeyCheckUiState,
    /** Recebe o texto digitado; vazio significa "use a chave já guardada". */
    onTest: (String) -> Unit,
    onCheckReset: () -> Unit,
    onDismiss: () -> Unit
) {
    val isPt = language == AppLanguage.PT
    var apiKey by remember(source) { mutableStateOf("") }
    var revealed by remember(source) { mutableStateOf(false) }
    var showError by remember(source) { mutableStateOf(false) }
    val sourceName = source.displayName(language)
    val isChecking = checkState.status == ApiKeyCheckStatus.CHECKING
    val hasCandidateKey = apiKey.isNotBlank() || hasStoredKey

    AppDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isPt) "Configurar $sourceName" else "Configure $sourceName",
                style = MaterialTheme.typography.titleSmall
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                Text(
                    text = if (isPt) {
                        "Informe a API key para habilitar esta integração. A chave será armazenada localmente com acesso restrito."
                    } else {
                        "Enter the API key to enable this integration. The key is stored locally with restricted access."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (isPt) "API key" else "API key",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)
                ) {
                    AppTextField(
                        value = apiKey,
                        onValueChange = {
                            apiKey = it
                            showError = false
                            // Mesma regra do `showError`: o veredito descreve o
                            // texto anterior e deixa de valer no primeiro toque.
                            onCheckReset()
                        },
                        visualTransformation = if (revealed) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        modifier = Modifier.weight(1f).testTag(API_KEY_DIALOG_FIELD_TEST_TAG)
                    )
                    AppIconButton(
                        contentDescription = if (revealed) {
                            if (isPt) "Ocultar chave" else "Hide key"
                        } else {
                            if (isPt) "Mostrar chave" else "Show key"
                        },
                        onClick = { revealed = !revealed }
                    ) {
                        Icon(
                            imageVector = if (revealed) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (showError) {
                    Text(
                        text = if (isPt) "Informe uma API key." else "Enter an API key.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                // O veredito fica junto do campo que ele descreve, e não no
                // rodapé junto do botão: a pergunta é sobre o que está digitado
                // ali. Ponto E palavra, como todo estado deste sistema visual.
                val checkMessage = checkState.message
                if (isChecking || checkMessage != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)
                    ) {
                        if (isChecking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        }
                        if (checkMessage != null) {
                            AppStatusIndicator(
                                label = checkMessage,
                                tone = checkState.tone,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag(API_KEY_DIALOG_RESULT_TEST_TAG)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            AppButton(
                label = if (isPt) "Salvar" else "Save",
                tone = AppButtonTone.PRIMARY,
                onClick = {
                    val normalized = apiKey.trim()
                    if (normalized.isBlank()) {
                        showError = true
                    } else {
                        onSave(normalized)
                    }
                }
            )
        },
        dismissButton = {
            // As duas ações secundárias moram no mesmo slot para o `AppDialog`
            // as manter na fileira do rodapé, à esquerda do `PRIMARY`. Remover é
            // `GHOST` e não `DANGER`: `PRIMARY` é uma por tela e o realce forte
            // aqui é do "Salvar", que é o que o diálogo propõe.
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                if (onRemove != null) {
                    AppButton(
                        label = if (isPt) "Remover chave" else "Remove key",
                        tone = AppButtonTone.GHOST,
                        onClick = onRemove,
                        modifier = Modifier.testTag(API_KEY_DIALOG_REMOVE_TEST_TAG)
                    )
                }
                AppButton(
                    label = if (isPt) "Cancelar" else "Cancel",
                    tone = AppButtonTone.GHOST,
                    onClick = onDismiss
                )
                // `GHOST` e não `PRIMARY`: primária é uma por tela e é o
                // "Salvar", que é o que o diálogo propõe. Fica encostado nele
                // porque a sequência natural é testar e então salvar.
                AppButton(
                    label = if (isPt) "Testar chave" else "Test key",
                    tone = AppButtonTone.GHOST,
                    onClick = { onTest(apiKey.trim()) },
                    enabled = hasCandidateKey && !isChecking,
                    modifier = Modifier.testTag(API_KEY_DIALOG_TEST_TEST_TAG)
                )
            }
        }
    )
}
