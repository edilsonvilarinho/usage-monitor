package com.usagemonitor.update

import com.usagemonitor.APP_ICON_RESOURCE_PATH
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

private const val ICON_LINE_PREFIX = "Icon="

/** 12 hex = 48 bits: basta para distinguir as poucas versões do ícone que já existiram. */
private const val ICON_HASH_HEX_LENGTH = 12

private val CONTENT_ADDRESSED_ICON_NAME = Regex(
    "${Regex.escape(LINUX_MENU_ICON_FILE_PREFIX)}[0-9a-f]{$ICON_HASH_HEX_LENGTH}${Regex.escape(LINUX_MENU_ICON_FILE_SUFFIX)}"
)

/**
 * Nome da cópia estável para estes bytes: `icon-<12 hex do SHA-256>.png`.
 *
 * Endereçar por conteúdo é o que faz o ícone novo chegar ao menu (issue
 * #432): bytes novos dão caminho novo, o `Icon=` deixa de bater e
 * [linuxMenuIconNeedsRepair] reescreve a linha. Regravar os bytes no mesmo
 * caminho não basta — o KDE guarda o pixmap pelo caminho e só o relê quando
 * a entrada de menu muda.
 */
internal fun linuxMenuIconFileName(iconBytes: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(iconBytes)
    val hex = digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
    return "$LINUX_MENU_ICON_FILE_PREFIX${hex.take(ICON_HASH_HEX_LENGTH)}$LINUX_MENU_ICON_FILE_SUFFIX"
}

/**
 * Cópias antigas do ícone que podem ser apagadas da raiz de dados.
 *
 * Só nomes que o próprio app criou entram: o `icon.png` fixo da #133 e
 * `icon-<hash>.png` com outro hash. Qualquer outro arquivo da raiz
 * (`current`, o marcador, um `.png` que alguém pôs à mão) fica. Ordenado para
 * a remoção ser determinística.
 */
internal fun staleLinuxMenuIconFileNames(existingFileNames: Collection<String>, currentFileName: String): List<String> {
    return existingFileNames
        .filter { name -> name != currentFileName }
        .filter { name -> name == LINUX_LEGACY_MENU_ICON_FILE_NAME || CONTENT_ADDRESSED_ICON_NAME.matches(name) }
        .sorted()
}

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
 * instala a partir de agora (issue #133, mesmo princípio da #120). Pelo
 * mesmo caminho chega o ícone novo quando a marca muda (issue #432): a cópia
 * tem nome por conteúdo, o `Icon=` é reescrito para ela e as cópias antigas
 * saem da raiz.
 *
 * Nunca lê da árvore versionada, que é exatamente o caminho que o
 * `linux-updater.sh` está prestes a podar: o app já carrega os próprios
 * bytes do ícone no classpath: o de 512 px, com o nome escrito, que é o
 * tamanho em que os menus de aplicativos o mostram.
 *
 * Nunca lança: falha aqui é cosmética — o pior caso é o ícone do menu
 * continuar errado — e não pode derrubar o arranque do app. A leitura da
 * máquina (origem, raiz, bytes do classpath, `user.home`) fica aqui; o reparo
 * sobre os arquivos é [repairLinuxMenuIcon], testado numa pasta temporária. A
 * prova nos caminhos reais continua sendo a verificação ao vivo na Bazzite
 * (planos das issues #133 e #432).
 */
internal fun ensureLinuxMenuIconCurrent() {
    runCatching {
        if (LinuxInstallOriginResolver.current() != LinuxInstallOrigin.MANAGED_XDG) {
            return@runCatching
        }

        val layout = resolveLinuxInstallLayout() ?: return@runCatching
        val iconBytes = readBundledIconBytes() ?: return@runCatching
        repairLinuxMenuIcon(layout, iconBytes, resolveLinuxMenuDesktopFilePath())
    }
}

/**
 * Grava a cópia estável do ícone, aponta o `.desktop` de menu para ela e só
 * então apaga as cópias antigas da raiz.
 */
internal fun repairLinuxMenuIcon(layout: LinuxInstallLayout, iconBytes: ByteArray, menuDesktopFilePath: String?) {
    val iconFileName = linuxMenuIconFileName(iconBytes)
    val iconPath = layout.iconPath(iconFileName)
    if (!ensureStableIconFile(File(iconPath), iconBytes)) {
        return
    }

    val menuDesktopFile = menuDesktopFilePath?.let(::File) ?: return
    // Entrada ausente não repara -- criá-la continua sendo trabalho
    // exclusivo do instalador `.sh`.
    if (!menuDesktopFile.isFile) {
        return
    }

    val currentText = runCatching { menuDesktopFile.readText() }.getOrNull() ?: return
    if (linuxMenuIconNeedsRepair(currentText, iconPath)) {
        val rewritten = rewriteLinuxMenuIconLine(currentText, iconPath)
        if (!writeAtomically(menuDesktopFile, rewritten)) {
            // A cópia antiga ainda é a que o menu mostra: apagá-la agora
            // deixaria o menu sem ícone nenhum.
            return
        }
    }

    deleteStaleIconFiles(layout.root, iconFileName)
}

private fun readBundledIconBytes(): ByteArray? {
    val resourceStream = object {}.javaClass.getResourceAsStream(APP_ICON_RESOURCE_PATH) ?: return null
    return resourceStream.use { input -> runCatching { input.readBytes() }.getOrNull() }
}

/**
 * Grava a cópia estável, se ela ainda não existir. Já existente não é
 * reescrita: o nome carrega o hash do conteúdo, então arquivo presente é
 * arquivo certo — e não mexer no `mtime` a cada abertura evita reconstruir o
 * cache de ícones do desktop à toa.
 */
private fun ensureStableIconFile(iconFile: File, iconBytes: ByteArray): Boolean {
    if (iconFile.isFile) {
        return true
    }

    return runCatching {
        iconFile.parentFile?.mkdirs()
        val tempFile = File(iconFile.parentFile, "${iconFile.name}.next")
        tempFile.writeBytes(iconBytes)
        tempFile.renameTo(iconFile)
    }.getOrDefault(false)
}

/** Só depois que o `.desktop` aponta para a cópia nova; falha em apagar é só lixo. */
private fun deleteStaleIconFiles(root: File, currentFileName: String) {
    val existingFileNames = root.list()?.toList() ?: return
    staleLinuxMenuIconFileNames(existingFileNames, currentFileName).forEach { name ->
        runCatching { File(root, name).delete() }
    }
}

/**
 * Escreve por arquivo temporário + rename, para nunca deixar o `.desktop` pela metade.
 *
 * `Files.move` com `ATOMIC_MOVE`, como os outros arquivos do app: no Linux é o
 * mesmo `rename(2)` de `File.renameTo`, mas também substitui o destino no
 * Windows, onde `renameTo` recusa destino existente — e é lá que a suíte roda.
 */
private fun writeAtomically(target: File, content: String): Boolean {
    return runCatching {
        val tempFile = File(target.parentFile, "${target.name}.next")
        tempFile.writeText(content)
        Files.move(tempFile.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        true
    }.getOrDefault(false)
}
