package com.usagemonitor.update

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
