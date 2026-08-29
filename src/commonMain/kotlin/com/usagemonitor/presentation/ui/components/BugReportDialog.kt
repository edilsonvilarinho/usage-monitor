package com.usagemonitor.presentation.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.ui.theme.AppShapes
import com.usagemonitor.presentation.ui.theme.AppSpacing

const val BUG_REPORT_DESCRIPTION_TEST_TAG = "bugReportDescription"
const val BUG_REPORT_SCREENSHOT_SWITCH_TEST_TAG = "bugReportScreenshotSwitch"
const val BUG_REPORT_PREVIEW_TEST_TAG = "bugReportPreview"
const val BUG_REPORT_STATUS_TEST_TAG = "bugReportStatus"

/**
 * Diálogo de relatório de bug.
 *
 * **Stateless**: descrição, caixa da captura e prévia recolhida chegam por
 * parâmetro e saem por lambda. É isso que permite ao teste de componente digitar,
 * alternar e conferir sem montar meio app em volta.
 *
 * **Nenhuma animação**: a prévia entra e sai da composição, sem transição. Uma
 * animação infinita travaria o `waitForIdle` da suíte, e uma finita não
 * acrescenta nada a um bloco de texto.
 *
 * **`PRIMARY` é "Salvar arquivo", não "Abrir issue".** A ordem do fluxo é salvar
 * e depois anexar: o botão que abre o navegador antes de existir arquivo levaria
 * o usuário a publicar uma issue sem o pacote, que é o caso que o formulário
 * existe para evitar. E `PRIMARY` é uma por tela.
 */
@Composable
fun BugReportDialog(
    description: String,
    onDescriptionChange: (String) -> Unit,
    previewText: String,
    previewExpanded: Boolean,
    onTogglePreview: () -> Unit,
    onSaveFile: () -> Unit,
    onOpenIssue: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    language: AppLanguage = AppLanguage.PT,
    includeScreenshot: Boolean = false,
    onIncludeScreenshotChange: (Boolean) -> Unit = {},
    /**
     * `false` esconde a linha da captura por inteiro.
     *
     * Interruptor que não pode fazer nada é pior que interruptor nenhum: sem
     * tela (ambiente headless, janela ainda não mapeada) a captura devolveria
     * sempre nada, e a caixa marcada prometeria uma imagem que não vem.
     */
    screenshotSupported: Boolean = true,
    /** Resultado da última ação. Fora do estado do formulário: é retorno, não entrada. */
    statusMessage: String? = null,
    statusIsError: Boolean = false
) {
    val isPt = language == AppLanguage.PT
    val canSubmit = description.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = {
            Text(
                text = if (isPt) "Reportar um bug" else "Report a bug",
                style = MaterialTheme.typography.titleSmall
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                Text(
                    text = if (isPt) {
                        "Salve o pacote de diagnóstico e abra a issue no GitHub. " +
                            "Nada é enviado automaticamente: você revisa e publica."
                    } else {
                        "Save the diagnostic package and open the issue on GitHub. " +
                            "Nothing is sent automatically: you review and publish."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = if (isPt) "O que aconteceu" else "What happened",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                AppTextArea(
                    value = description,
                    onValueChange = onDescriptionChange,
                    placeholder = if (isPt) {
                        "Descreva o que você fez e o que aconteceu."
                    } else {
                        "Describe what you did and what happened."
                    },
                    modifier = Modifier.fillMaxWidth().testTag(BUG_REPORT_DESCRIPTION_TEST_TAG)
                )
                if (!canSubmit) {
                    // Botão desabilitado sem motivo é pior que botão desabilitado:
                    // a frase diz o que falta em vez de deixar o usuário adivinhar.
                    Text(
                        text = if (isPt) {
                            "Escreva a descrição para habilitar as ações."
                        } else {
                            "Write the description to enable the actions."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (screenshotSupported) {
                    AppDataRow(showDivider = false, horizontalPadding = 0.dp) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isPt) "Incluir captura da janela" else "Include a window capture",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isPt) {
                                    "A imagem é só da janela do app e é salva ao lado do arquivo."
                                } else {
                                    "The image covers the app window only and is saved beside the file."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        AppSwitch(
                            checked = includeScreenshot,
                            onCheckedChange = onIncludeScreenshotChange,
                            modifier = Modifier.testTag(BUG_REPORT_SCREENSHOT_SWITCH_TEST_TAG)
                        )
                    }
                }

                AppButton(
                    label = if (previewExpanded) {
                        if (isPt) "Ocultar prévia" else "Hide preview"
                    } else {
                        if (isPt) "Ver prévia" else "Show preview"
                    },
                    tone = AppButtonTone.GHOST,
                    onClick = onTogglePreview
                )
                if (previewExpanded) {
                    // Recolhida por padrão: o corpo da issue tem milhares de
                    // caracteres e, aberto de saída, empurraria o campo de
                    // descrição para fora da janela.
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = PREVIEW_MAX_HEIGHT)
                            .verticalScroll(rememberScrollState())
                            .testTag(BUG_REPORT_PREVIEW_TEST_TAG)
                    ) {
                        Text(
                            text = previewText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (statusMessage != null) {
                    AppBanner(
                        title = statusMessage,
                        tone = if (statusIsError) AppTone.CRITICAL else AppTone.OK,
                        modifier = Modifier.testTag(BUG_REPORT_STATUS_TEST_TAG)
                    )
                }
            }
        },
        shape = AppShapes.large,
        containerColor = MaterialTheme.colorScheme.surface,
        confirmButton = {
            AppButton(
                label = if (isPt) "Salvar arquivo" else "Save file",
                tone = AppButtonTone.PRIMARY,
                enabled = canSubmit,
                onClick = onSaveFile
            )
        },
        dismissButton = {
            AppButton(
                label = if (isPt) "Abrir issue no GitHub" else "Open issue on GitHub",
                tone = AppButtonTone.DEFAULT,
                enabled = canSubmit,
                onClick = onOpenIssue
            )
            AppButton(
                label = if (isPt) "Fechar" else "Close",
                tone = AppButtonTone.GHOST,
                onClick = onDismiss
            )
        }
    )
}

/** Teto da prévia: ela informa, mas não pode ser a tela inteira. */
private val PREVIEW_MAX_HEIGHT = 160.dp
