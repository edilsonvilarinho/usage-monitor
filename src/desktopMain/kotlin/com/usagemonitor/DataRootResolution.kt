package com.usagemonitor

import com.usagemonitor.data.defaultAppDataRoot
import java.io.File
import java.io.IOException

/** O que `--user-data-dir` vira no disco (issue #419). */
internal sealed interface DataRootResolution {
    /** Sem a opção, ou a pasta pedida é a própria `~/.usage-monitor`: nada muda. */
    data object Default : DataRootResolution

    /** Pasta canônica e já criada. */
    data class Custom(val root: File) : DataRootResolution

    data class Error(val message: String) : DataRootResolution
}

/**
 * Caminho relativo resolve contra [workingDirectory]; o resultado é canônico,
 * porque o nó de preferências sai do hash do caminho, e `dados` e `.\dados\`
 * não podem virar dois perfis.
 *
 * Cria a pasta quando falta: abrir o app numa raiz nova é o uso normal. O que
 * não cria é motivo de saída 2, antes de qualquer arquivo ser aberto.
 */
internal fun resolveDataRoot(
    value: String?,
    workingDirectory: File,
    // `defaultAppDataRoot`, e não `AppDataDirectory.root`: ler a raiz do processo
    // aqui a travaria antes do `configure`.
    defaultRoot: () -> File = ::defaultAppDataRoot
): DataRootResolution {
    if (value == null) {
        return DataRootResolution.Default
    }
    val requested = File(value).let { file -> if (file.isAbsolute) file else File(workingDirectory, value) }
    val canonical = try {
        requested.canonicalFile
    } catch (error: IOException) {
        return DataRootResolution.Error("Error: '$value' is not a valid folder for ${CommandLineOptions.USER_DATA_DIR}.")
    }
    if (canonical.exists() && !canonical.isDirectory) {
        return DataRootResolution.Error(
            "Error: '${canonical.path}' is a file. ${CommandLineOptions.USER_DATA_DIR} needs a folder."
        )
    }
    if (!canonical.isDirectory && !canonical.mkdirs()) {
        return DataRootResolution.Error(
            "Error: could not create '${canonical.path}' for ${CommandLineOptions.USER_DATA_DIR}."
        )
    }
    val default = runCatching { defaultRoot().canonicalFile }.getOrNull()
    return if (canonical == default) DataRootResolution.Default else DataRootResolution.Custom(canonical)
}
