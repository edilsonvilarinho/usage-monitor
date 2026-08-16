package com.usagemonitor.presentation.viewmodel

import com.usagemonitor.presentation.ui.UsageExportRequest

/**
 * Grava o conteúdo exportado onde o usuário escolher.
 *
 * Injetável pelo mesmo motivo de `rememberClipboardWriter`: o teste de
 * componente não pode abrir um diálogo de arquivo nem escrever no disco de quem
 * roda a suíte. A implementação real vive em `desktopMain`.
 */
fun interface UsageExportWriter {

    /**
     * Devolve o caminho gravado, ou `null` quando o usuário cancelou.
     *
     * Cancelar não é falha: o estado não publica resultado nenhum nesse caso.
     */
    suspend fun write(request: UsageExportRequest): String?
}
