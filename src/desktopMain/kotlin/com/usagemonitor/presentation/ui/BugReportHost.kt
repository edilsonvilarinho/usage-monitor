package com.usagemonitor.presentation.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.usagemonitor.BugReportIssueOpener
import com.usagemonitor.WindowScreenshotCapturer
import com.usagemonitor.bugReportIssueUrl
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.BugReportEnvelope
import com.usagemonitor.domain.entity.breadcrumbFailureReasonOf
import com.usagemonitor.domain.usecase.GenerateBugReportUseCase
import com.usagemonitor.domain.repository.BreadcrumbRecorder
import com.usagemonitor.domain.repository.NoOpBreadcrumbRecorder
import com.usagemonitor.presentation.ui.components.BugReportDialog
import com.usagemonitor.presentation.viewmodel.BugReportSaveRequest
import com.usagemonitor.presentation.viewmodel.BugReportWriter
import com.usagemonitor.presentation.viewmodel.bugReportFileName
import com.usagemonitor.presentation.viewmodel.recordFailure
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.launch

/**
 * Estado e ações do relatório de bug em volta do [BugReportDialog] stateless.
 *
 * Existe **fora** do `main()` de propósito: o diálogo tem seis pedaços de estado e
 * duas ações suspensas, e todos eles dentro daquele composable seriam mais mil
 * linhas no método que já estourou o backend da JVM uma vez. `main()` fica com
 * dois `remember` e uma chamada.
 *
 * Fica em `desktopMain` porque conhece writer, capturer e abridor de navegador —
 * três coisas que o `commonMain` não pode importar.
 */
@Composable
fun BugReportHost(
    generateBugReport: GenerateBugReportUseCase,
    writer: BugReportWriter,
    issueOpener: BugReportIssueOpener,
    screenshots: WindowScreenshotCapturer,
    onDismiss: () -> Unit,
    breadcrumbs: BreadcrumbRecorder = NoOpBreadcrumbRecorder,
    language: AppLanguage = AppLanguage.PT,
    /**
     * Descrição sugerida quando o arranque veio depois de uma queda. `null` é a
     * abertura normal, pelas Configurações.
     */
    crashPrefill: String? = null,
    /** Imagem gravada pela queda, quando houve. */
    crashScreenshotPng: ByteArray? = null
) {
    val isPt = language == AppLanguage.PT
    var description by remember { mutableStateOf(crashPrefill.orEmpty()) }
    var previewExpanded by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var statusIsError by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // A captura acontece **uma vez, na abertura**, e não no clique de salvar: no
    // clique o diálogo já está pintado e o `Robot`, que lê o conteúdo do monitor,
    // devolveria uma imagem do próprio formulário por cima do app. Aqui a
    // composição ainda não virou frame.
    //
    // A imagem da queda tem prioridade: ela mostra a tela do defeito, e a de
    // agora mostraria o app funcionando.
    val screenshotResult = remember(crashScreenshotPng, screenshots) {
        runCatching { crashScreenshotPng ?: screenshots.capture() }
    }
    val capturedPng = screenshotResult.getOrNull()
    val screenshotFailure = screenshotResult.exceptionOrNull()
    var includeScreenshot by remember { mutableStateOf(capturedPng != null) }

    LaunchedEffect(screenshotFailure) {
        if (screenshotFailure != null) {
            breadcrumbs.recordFailure("capturar tela para relatório de bug", screenshotFailure)
            statusIsError = true
            statusMessage = if (isPt) {
                "Não foi possível capturar a tela: ${breadcrumbFailureReasonOf(screenshotFailure)}"
            } else {
                "Could not capture the screenshot: ${breadcrumbFailureReasonOf(screenshotFailure)}"
            }
        }
    }

    // Só com a prévia aberta é que a trilha é lida, e o arquivo tem no máximo
    // 201 linhas curtas: relê-la a cada tecla com a prévia fechada seria I/O sem
    // ninguém para ver o resultado.
    val previewResult = remember(description, previewExpanded) {
        if (previewExpanded) {
            runCatching { generateBugReport(description).toGithubIssueBody() }
        } else {
            Result.success("")
        }
    }
    val previewFailure = previewResult.exceptionOrNull()
    LaunchedEffect(description, previewExpanded, previewFailure) {
        if (previewFailure != null) {
            breadcrumbs.recordFailure("gerar prévia do relatório de bug", previewFailure)
            statusIsError = true
            statusMessage = if (isPt) {
                "Não foi possível gerar a prévia: ${breadcrumbFailureReasonOf(previewFailure)}"
            } else {
                "Could not generate the preview: ${breadcrumbFailureReasonOf(previewFailure)}"
            }
        }
    }
    val previewText = previewResult.getOrElse { "" }

    BugReportDialog(
        description = description,
        onDescriptionChange = { value ->
            description = value
            // O resultado anterior descrevia outro texto; mantê-lo na tela faria
            // "Arquivo salvo" parecer valer para o que está sendo digitado agora.
            statusMessage = null
        },
        previewText = previewText,
        previewExpanded = previewExpanded,
        onTogglePreview = { previewExpanded = !previewExpanded },
        includeScreenshot = includeScreenshot,
        onIncludeScreenshotChange = { value -> includeScreenshot = value },
        screenshotSupported = capturedPng != null,
        language = language,
        statusMessage = statusMessage,
        statusIsError = statusIsError,
        onSaveFile = {
            scope.launch {
                val result = runCatching {
                    val envelope = generateBugReport(description)
                    writer.write(
                        BugReportSaveRequest(
                            suggestedFileName = bugReportFileName(envelope.capturedAt),
                            json = envelope.toJson(),
                            screenshotPng = capturedPng.takeIf { includeScreenshot }
                        )
                    )
                }
                result
                    .onSuccess { saved ->
                        // Cancelar devolve `null` e **não** publica resultado: não
                        // é sucesso nem erro, é a ausência da ação.
                        if (saved != null) {
                            statusIsError = false
                            statusMessage = savedMessage(saved.jsonPath, saved.screenshotPath, isPt)
                        }
                    }
                    .onFailure { error ->
                        breadcrumbs.recordFailure("salvar relatório de bug", error)
                        statusIsError = true
                        statusMessage = if (isPt) {
                            "Não foi possível salvar o arquivo: ${breadcrumbFailureReasonOf(error)}"
                        } else {
                            "Could not save the file: ${breadcrumbFailureReasonOf(error)}"
                        }
                    }
            }
        },
        onOpenIssue = {
            val result = runCatching {
                val envelope: BugReportEnvelope = generateBugReport(description)
                issueOpener.open(bugReportIssueUrl(envelope)).getOrThrow()
            }
            result
                .onSuccess {
                    onDismiss()
                }
                .onFailure { error ->
                    breadcrumbs.recordFailure("abrir issue de relatório de bug", error)
                    statusIsError = true
                    statusMessage = if (isPt) {
                        "Não foi possível abrir a issue: ${breadcrumbFailureReasonOf(error)}. Corrija o problema e tente novamente."
                    } else {
                        "Could not open the issue: ${breadcrumbFailureReasonOf(error)}. Fix the problem and try again."
                    }
                }
        },
        onDismiss = onDismiss
    )
}

/**
 * Descrição sugerida para o arranque que veio depois de uma queda.
 *
 * Duas linhas: a primeira é o que o usuário reconhece e vira o título da issue, a
 * segunda é o que a máquina sabe. Ele pode reescrever as duas.
 */
internal fun crashPrefillDescription(exception: String, thread: String, isPt: Boolean): String {
    return if (isPt) {
        "O app fechou inesperadamente.\n\nFalha registrada: $exception na thread $thread."
    } else {
        "The app closed unexpectedly.\n\nRecorded failure: $exception on thread $thread."
    }
}

private fun savedMessage(jsonPath: String, screenshotPath: String?, isPt: Boolean): String {
    val base = if (isPt) "Arquivo salvo em $jsonPath" else "File saved to $jsonPath"
    if (screenshotPath == null) {
        return base
    }
    return if (isPt) "$base (imagem em $screenshotPath)" else "$base (image at $screenshotPath)"
}
