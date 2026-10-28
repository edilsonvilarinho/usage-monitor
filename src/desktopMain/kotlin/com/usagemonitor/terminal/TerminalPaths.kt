package com.usagemonitor.terminal

import java.io.File

/**
 * [path] com a pasta pessoal trocada por `~`, para a saída do terminal. Canônicos dos
 * dois lados: no Windows um pode vir com o nome curto 8.3. A própria pasta pessoal, e o
 * que não mora dentro dela, saem como vieram.
 */
internal fun homeRelativePath(path: String, home: File): String {
    val homePath = runCatching { home.canonicalPath }.getOrDefault(home.path).trimEnd(File.separatorChar)
    val canonical = runCatching { File(path).canonicalPath }.getOrDefault(path)
    return if (homePath.isNotEmpty() && canonical.startsWith(homePath + File.separator)) "~" + canonical.removePrefix(homePath) else path
}
