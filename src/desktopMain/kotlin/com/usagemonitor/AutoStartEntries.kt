package com.usagemonitor

import com.usagemonitor.AutoStartManager.APP_DISPLAY_NAME
import com.usagemonitor.AutoStartManager.MACOS_LAUNCH_AGENT_LABEL

/*
 * Texto das entradas de inicialização do Linux (Desktop Entry) e do macOS (LaunchAgent):
 * montagem e leitura, funções puras. Saíram de `AutoStartManager.kt` (761 linhas) pela
 * regra de tamanho; quem grava e apaga os arquivos continua lá.
 */

/** Valor cru de uma chave do grupo `[Desktop Entry]`, sem interpretar nada. */
internal fun desktopEntryValue(entry: String, key: String): String? {
    return entry.lineSequence()
        .map(String::trim)
        .firstNotNullOfOrNull { line ->
            line.removePrefix("$key=")
                .takeIf { it != line }
                ?.trim()
                ?.takeIf { it.isNotBlank() }
        }
}

/**
 * Primeiro argumento do `Exec=`, que e o programa.
 *
 * Aqui as aspas **sao** da especificacao e precisam ser desfeitas: no `Exec`
 * elas delimitam o argumento e o leitor as remove antes do spawn. E o oposto
 * exato do `Path=`, lido verbatim -- e a razao de as duas chaves nao se
 * escreverem do mesmo jeito.
 */
internal fun firstDesktopExecArgument(exec: String): String? {
    if (!exec.startsWith("\"")) {
        return exec.substringBefore(' ').takeIf { it.isNotBlank() }
    }

    val program = StringBuilder()
    var index = 1
    while (index < exec.length) {
        val character = exec[index]
        when {
            character == '\\' && index + 1 < exec.length -> {
                program.append(exec[index + 1])
                index += 2
            }

            character == '"' -> return program.toString().takeIf { it.isNotBlank() }

            else -> {
                program.append(character)
                index += 1
            }
        }
    }

    // Aspas sem fechamento: entrada corrompida, e nao um caminho.
    return null
}

/**
 * As duas chaves nao se escrevem do mesmo jeito, e a simetria custou o
 * arranque inteiro no Linux.
 *
 * A Desktop Entry Specification define regras de aspas **apenas para a chave
 * `Exec`** (secao "The Exec key"): ali as aspas separam os argumentos e sao
 * removidas pelo leitor. `Path` e do tipo `string` e e lida **verbatim** --
 * a GLib guarda o valor em `info->path` e o passa como `working_directory`
 * do `g_spawn`, e o KIO o passa para `QProcess::setWorkingDirectory`. Um
 * diretorio cujo nome literal comeca com aspas nao existe, o spawn falha no
 * `chdir` e nada aparece na tela: `isAutoStartEnabled()` so testa se o
 * arquivo existe, entao o interruptor continua ligado (issue #120).
 */
internal fun AutoStartManager.buildLinuxDesktopEntry(executablePath: String, parentDir: String): String {
    return buildString {
        appendLine("[Desktop Entry]")
        appendLine("Type=Application")
        appendLine("Version=1.0")
        appendLine("Name=$APP_DISPLAY_NAME")
        appendLine("Exec=${quoteDesktopValue(executablePath)} ${StartupOrigin.AUTO_START_ARGUMENT}")
        appendLine("Path=$parentDir")
        appendLine("Terminal=false")
        appendLine("X-GNOME-Autostart-enabled=true")
    }
}

internal fun AutoStartManager.buildLaunchAgentPlist(executablePath: String): String {
    return buildString {
        appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        appendLine(
            """<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" """ +
                """"http://www.apple.com/DTDs/PropertyList-1.0.dtd">"""
        )
        appendLine("""<plist version="1.0">""")
        appendLine("<dict>")
        appendLine("    <key>Label</key>")
        appendLine("    <string>$MACOS_LAUNCH_AGENT_LABEL</string>")
        appendLine("    <key>ProgramArguments</key>")
        appendLine("    <array>")
        appendLine("        <string>${escapeXml(executablePath)}</string>")
        appendLine("        <string>${StartupOrigin.AUTO_START_ARGUMENT}</string>")
        appendLine("    </array>")
        appendLine("    <key>RunAtLoad</key>")
        appendLine("    <true/>")
        appendLine("</dict>")
        appendLine("</plist>")
    }
}

private fun escapeXml(value: String): String {
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
}

private fun quoteDesktopValue(value: String): String {
    return "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""
}
