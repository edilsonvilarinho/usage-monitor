package com.usagemonitor.presentation.viewmodel

import kotlinx.datetime.Instant

/**
 * O que gravar em disco.
 *
 * Classe comum e não `data class`: ela carrega um `ByteArray`, e a igualdade
 * gerada compararia a imagem por identidade — um `equals` que mente é pior que
 * `equals` nenhum.
 */
class BugReportSaveRequest(
    val suggestedFileName: String,
    val json: String,
    /** PNG da janela, quando o usuário pediu para incluí-lo. */
    val screenshotPng: ByteArray? = null
)

/** Onde o pacote foi parar. `screenshotPath` nulo é "não havia imagem para salvar". */
data class BugReportSaveResult(
    val jsonPath: String,
    val screenshotPath: String? = null
)

/**
 * Grava o pacote de diagnóstico num arquivo escolhido pelo usuário.
 *
 * Injetável pelo mesmo motivo do [UsageExportWriter] e do
 * `rememberClipboardWriter`: teste de componente não abre diálogo de arquivo e
 * não escreve no disco de quem roda a suíte.
 */
interface BugReportWriter {

    /** `null` quando o usuário cancelou — que não é sucesso nem erro. */
    suspend fun write(request: BugReportSaveRequest): BugReportSaveResult?
}

/**
 * Nome sugerido do arquivo.
 *
 * Carimbo de tempo no nome porque o usuário pode gerar mais de um relatório antes
 * de publicar qualquer um, e um nome fixo faria o segundo apagar o primeiro no
 * diálogo de salvar. Só dígitos: `:` não é caractere válido em nome de arquivo no
 * Windows, e o ISO cru daria um nome que o próprio diálogo recusa.
 */
fun bugReportFileName(capturedAt: Instant): String {
    val stamp = capturedAt.toString()
        .takeWhile { character -> character != '.' }
        .filter { character -> character.isDigit() }
    return "usage-monitor-bug-report-$stamp.json"
}
