package com.usagemonitor

import java.io.File

object AutoStartManager {

    private const val WINDOWS_RUN_KEY = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run"
    private const val WINDOWS_VALUE_NAME = "UsageMonitor"
    private const val LINUX_AUTOSTART_FILE = "usage-monitor.desktop"
    private const val APP_DISPLAY_NAME = "Usage Monitor"

    fun isAutoStartSupported(): Boolean {
        return currentPlatform() != Platform.OTHER
    }

    fun isAutoStartEnabled(): Boolean {
        return when (currentPlatform()) {
            Platform.WINDOWS -> isWindowsAutoStartEnabled()
            Platform.LINUX -> linuxAutostartFile().exists()
            Platform.OTHER -> false
        }
    }

    fun setAutoStart(enabled: Boolean): Boolean {
        return when (currentPlatform()) {
            Platform.WINDOWS -> setWindowsAutoStart(enabled)
            Platform.LINUX -> setLinuxAutoStart(enabled)
            Platform.OTHER -> false
        }
    }

    fun syncFromPreference(enabled: Boolean): Boolean {
        return setAutoStart(enabled)
    }

    private fun currentPlatform(): Platform {
        val osName = System.getProperty("os.name").lowercase()
        return when {
            osName.contains("win") -> Platform.WINDOWS
            osName.contains("linux") -> Platform.LINUX
            else -> Platform.OTHER
        }
    }

    private fun isWindowsAutoStartEnabled(): Boolean {
        val result = runCommand(
            listOf("reg", "query", WINDOWS_RUN_KEY, "/v", WINDOWS_VALUE_NAME)
        )
        return result.exitCode == 0
    }

    private fun setWindowsAutoStart(enabled: Boolean): Boolean {
        if (enabled) {
            val executablePath = resolveExecutablePath() ?: return false
            val command = "\"$executablePath\""
            val result = runCommand(
                listOf(
                    "reg",
                    "add",
                    WINDOWS_RUN_KEY,
                    "/v",
                    WINDOWS_VALUE_NAME,
                    "/t",
                    "REG_SZ",
                    "/d",
                    command,
                    "/f"
                )
            )
            return result.exitCode == 0
        }

        val result = runCommand(
            listOf("reg", "delete", WINDOWS_RUN_KEY, "/v", WINDOWS_VALUE_NAME, "/f")
        )
        return result.exitCode == 0 || !isWindowsAutoStartEnabled()
    }

    private fun setLinuxAutoStart(enabled: Boolean): Boolean {
        val autostartFile = linuxAutostartFile()

        if (!enabled) {
            return !autostartFile.exists() || autostartFile.delete()
        }

        val executablePath = resolveExecutablePath() ?: return false
        val parentDir = File(executablePath).parentFile?.absolutePath ?: return false
        val desktopEntry = buildString {
            appendLine("[Desktop Entry]")
            appendLine("Type=Application")
            appendLine("Version=1.0")
            appendLine("Name=$APP_DISPLAY_NAME")
            appendLine("Exec=${quoteDesktopValue(executablePath)}")
            appendLine("Path=${quoteDesktopValue(parentDir)}")
            appendLine("Terminal=false")
            appendLine("X-GNOME-Autostart-enabled=true")
        }

        return runCatching {
            autostartFile.parentFile.mkdirs()
            autostartFile.writeText(desktopEntry)
            true
        }.getOrDefault(false)
    }

    private fun linuxAutostartFile(): File {
        val configHome = System.getenv("XDG_CONFIG_HOME")
            ?.takeIf { it.isNotBlank() }
            ?: File(System.getProperty("user.home"), ".config").absolutePath
        return File(configHome, "autostart/$LINUX_AUTOSTART_FILE")
    }

    private fun resolveExecutablePath(): String? {
        val userDir = System.getProperty("user.dir")
        val candidates = buildList {
            System.getProperty("jpackage.app-path")?.let(::add)
            ProcessHandle.current().info().command().orElse(null)?.let(::add)

            when (currentPlatform()) {
                Platform.WINDOWS -> {
                    add("$userDir\\Usage Monitor.exe")
                    add("$userDir\\UsageMonitor.exe")
                }

                Platform.LINUX -> {
                    add("$userDir/Usage Monitor")
                    add("$userDir/usage-monitor")
                    add("$userDir/bin/Usage Monitor")
                    add("$userDir/bin/usage-monitor")
                }

                Platform.OTHER -> Unit
            }
        }

        return candidates
            .map(::File)
            .firstOrNull { file ->
                file.exists() && file.isFile && !isJavaLauncher(file.name)
            }
            ?.absolutePath
    }

    private fun isJavaLauncher(fileName: String): Boolean {
        val normalized = fileName.lowercase()
        return normalized == "java" ||
            normalized == "java.exe" ||
            normalized == "javaw.exe"
    }

    private fun quoteDesktopValue(value: String): String {
        return "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""
    }

    private fun runCommand(command: List<String>): CommandResult {
        return runCatching {
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            CommandResult(
                exitCode = process.waitFor(),
                output = output
            )
        }.getOrElse {
            CommandResult(exitCode = -1, output = it.message.orEmpty())
        }
    }

    private enum class Platform {
        WINDOWS,
        LINUX,
        OTHER
    }

    private data class CommandResult(
        val exitCode: Int,
        val output: String
    )
}
