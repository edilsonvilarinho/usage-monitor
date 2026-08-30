package com.usagemonitor.update

import com.usagemonitor.APP_ICON_RESOURCE_PATH
import java.io.File

private const val ICON_LINE_PREFIX = "Icon="

/**
 * Se a entrada de menu precisa ser reparada para apontar para o ícone
 * estável.
 *
 * Entrada **ausente não repara** — `desktopEntryText == null` devolve
 * `false`, mesma regra já estabelecida para o autostart
 * ([com.usagemonitor.AutoStartManager.linuxAutoStartNeedsRepair]): criar o
 * arquivo continua sendo trabalho exclusivo do instalador `.sh`, nunca do
 * app em execução.
 *
 * Ausência da própria linha `Icon=` também conta como "precisa reparar" —
 * cobre a instalação onde o `.png` não existia no momento em que o
 * instalador rodou, e que hoje nunca ganharia ícone algum.
 */
internal fun linuxMenuIconNeedsRepair(desktopEntryText: String?, stableIconPath: String): Boolean {
    val text = desktopEntryText ?: return false
    val currentIconLine = text.lineSequence().firstOrNull { line -> line.startsWith(ICON_LINE_PREFIX) }
    return currentIconLine != "$ICON_LINE_PREFIX$stableIconPath"
}

/**
 * Reescreve só a linha `Icon=`, preservando todas as outras na ordem
 * original.
 *
 * Um `.desktop` pode ter sido editado à mão (comentário, `MimeType=`,
 * `Keywords=`); regenerar o arquivo inteiro apagaria isso em silêncio. Aqui
 * a única linha que muda é a que este reparo entende.
 */
internal fun rewriteLinuxMenuIconLine(desktopEntryText: String, stableIconPath: String): String {
    // `split` e não `lineSequence` propositalmente: um texto terminado em
    // `\n` (o formato normal de um `.desktop`) produz um último elemento
    // vazio, que precisa ser descartado antes de reconstruir -- senão o
    // `joinToString` abaixo deixa uma linha em branco antes do `Icon=` novo.
    val lines = desktopEntryText.split("\n")
    val withoutTrailingBlank = if (lines.isNotEmpty() && lines.last().isEmpty()) {
        lines.dropLast(1)
    } else {
        lines
    }

    val keptLines = withoutTrailingBlank
        .filterNot { line -> line.startsWith(ICON_LINE_PREFIX) }
        .toMutableList()
    keptLines += "$ICON_LINE_PREFIX$stableIconPath"
    return keptLines.joinToString(separator = "\n", postfix = "\n")
}

/**
 * Garante que a cópia estável do ícone existe e que a entrada de **menu**
 * (se houver) aponta para ela.
 *
 * Roda a cada abertura do app, no mesmo `LaunchedEffect` que já chama
 * [com.usagemonitor.AutoStartManager.ensureAutoStartCommandCurrent] — é o
 * que alcança quem já está com o `.desktop` de menu quebrado, e não só quem
 * instala a partir de agora (issue #133, mesmo princípio da #120).
 *
 * Nunca lê da árvore versionada, que é exatamente o caminho que o
 * `linux-updater.sh` está prestes a podar: o app já carrega os próprios
 * bytes do ícone no classpath, os mesmos do ícone da janela
 * ([com.usagemonitor.loadWindowIcon]).
 *
 * Nunca lança: falha aqui é cosmética — o pior caso é o ícone do menu
 * continuar errado — e não pode derrubar o arranque do app. Pela mesma razão
 * de [com.usagemonitor.AutoStartManager.ensureAutoStartCommandCurrent], não
 * é unit-testada isoladamente: é IO de ponta a ponta sobre caminhos reais do
 * Linux, e a suíte roda no Windows. A prova é a verificação ao vivo na
 * Bazzite (plano da issue #133), como já é o caso para a migração irmã do
 * autostart.
 */
internal fun ensureLinuxMenuIconCurrent() {
    runCatching {
        if (LinuxInstallOriginResolver.current() != LinuxInstallOrigin.MANAGED_XDG) {
            return@runCatching
        }

        val layout = resolveLinuxInstallLayout() ?: return@runCatching
        if (!ensureStableIconFile(layout.iconFile)) {
            return@runCatching
        }

        val menuDesktopFile = resolveLinuxMenuDesktopFilePath()?.let(::File) ?: return@runCatching
        // Entrada ausente não repara -- criá-la continua sendo trabalho
        // exclusivo do instalador `.sh`.
        if (!menuDesktopFile.isFile) {
            return@runCatching
        }

        val currentText = runCatching { menuDesktopFile.readText() }.getOrNull() ?: return@runCatching
        if (!linuxMenuIconNeedsRepair(currentText, layout.iconPath)) {
            return@runCatching
        }

        val rewritten = rewriteLinuxMenuIconLine(currentText, layout.iconPath)
        writeAtomically(menuDesktopFile, rewritten)
    }
}

/**
 * Copia o ícone do classpath para o caminho estável, se ele ainda não
 * existir. Já existente não é reescrito — evita mexer no `mtime` a cada
 * abertura do app, o que rebuildaria o cache de ícones do desktop à toa.
 */
private fun ensureStableIconFile(iconFile: File): Boolean {
    if (iconFile.isFile) {
        return true
    }

    val resourceStream = object {}.javaClass.getResourceAsStream(APP_ICON_RESOURCE_PATH)
        ?: return false
    return resourceStream.use { input ->
        runCatching {
            iconFile.parentFile?.mkdirs()
            val tempFile = File(iconFile.parentFile, "${iconFile.name}.next")
            tempFile.outputStream().use { output -> input.copyTo(output) }
            tempFile.renameTo(iconFile)
        }.getOrDefault(false)
    }
}

/** Escreve por arquivo temporário + rename, para nunca deixar o `.desktop` pela metade. */
private fun writeAtomically(target: File, content: String): Boolean {
    return runCatching {
        val tempFile = File(target.parentFile, "${target.name}.next")
        tempFile.writeText(content)
        tempFile.renameTo(target)
    }.getOrDefault(false)
}
