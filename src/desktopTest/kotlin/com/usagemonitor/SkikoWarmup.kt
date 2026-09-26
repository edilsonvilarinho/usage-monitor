package com.usagemonitor

import org.jetbrains.skiko.Library

/**
 * Extrai a biblioteca nativa do Skiko para `~/.skiko` num processo só, antes
 * dos forks da suíte (issue #295).
 *
 * `Library.unpackIfNeeded` move a DLL para o destino com `Files.move`, e no
 * Windows o move falha com `AccessDeniedException` quando outro processo já
 * abriu o arquivo. Num runner limpo todo fork tentava extrair ao mesmo tempo e
 * a suíte caía com `ExceptionInInitializerError`. Com a extração feita aqui,
 * cada fork encontra o cache quente e só carrega.
 */
fun main() {
    Library.load()
}
