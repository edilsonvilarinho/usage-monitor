package com.usagemonitor.domain.entity

/**
 * Formato de saída da exportação de texto.
 *
 * Mora no domain porque a tela escolhe o formato e o `data` o produz: com o enum
 * em `data`, a camada de apresentação importava `data` para nomear um botão.
 */
enum class UsageExportFormat(val extension: String) {
    CSV("csv"),
    JSON("json")
}
