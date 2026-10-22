package com.usagemonitor.terminal

import java.io.File
import java.util.concurrent.TimeUnit

/** O resultado de um comando do sistema: código e a saída junta (`stdout` + `stderr`). */
internal data class ProcessResult(val exitCode: Int, val output: String)

/** Roda um comando do sistema; o teste troca por um de mentira. */
internal fun interface ProcessRunner {
    fun run(command: List<String>): ProcessResult

    companion object {
        /** Sem shell: argumento por lista, como o Antigravity (nada de interpolação). */
        val SYSTEM = ProcessRunner { command ->
            runCatching {
                val process = ProcessBuilder(command).redirectErrorStream(true).start()
                val output = process.inputStream.bufferedReader().readText()
                if (!process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    process.destroyForcibly()
                    ProcessResult(-1, output)
                } else {
                    ProcessResult(process.exitValue(), output)
                }
            }.getOrElse { error -> ProcessResult(-1, error.message.orEmpty()) }
        }

        private const val PROCESS_TIMEOUT_SECONDS = 60L
    }
}

/**
 * O serviço do usuário no systemd (#440, decisão D3: só no Linux). Usuário, e não
 * sistema: as credenciais dos CLIs moram na pasta pessoal, e nada aqui pede `sudo` —
 * exceto o `loginctl enable-linger`, que só o administrador roda, e que a saída mostra.
 */
internal class SystemdUserService(
    private val runner: ProcessRunner,
    /** `XDG_CONFIG_HOME`, ou `~/.config`. */
    private val configHome: File
) {
    val unitFile: File get() = File(configHome, "systemd/user/$UNIT")

    /** `null` quando o systemd do usuário responde; senão, o que ele (ou o sistema) disse. */
    fun unavailableReason(): String? =
        systemctl("show-environment").takeIf { result -> result.exitCode != 0 }?.let { result -> result.output.trim().lines().firstOrNull().orEmpty() }

    fun writeUnit(executable: String) {
        unitFile.parentFile.mkdirs()
        unitFile.writeText(unitText(executable))
    }

    fun systemctl(vararg arguments: String): ProcessResult = runner.run(listOf("systemctl", "--user") + arguments)

    /** As propriedades do `systemctl show`, como mapa (`ActiveState`, `MainPID`, `MemoryCurrent`…). */
    fun properties(): Map<String, String> =
        systemctl("show", UNIT, "--property=LoadState,ActiveState,SubState,MainPID,MemoryCurrent").output.lines()
            .mapNotNull { line -> line.split('=', limit = 2).takeIf { parts -> parts.size == 2 }?.let { parts -> parts[0] to parts[1] } }
            .toMap()

    /** `true`/`false` pelo `loginctl`; `null` quando ele não responde. */
    fun lingers(user: String): Boolean? =
        runner.run(listOf("loginctl", "show-user", user, "--property=Linger", "--value")).takeIf { result -> result.exitCode == 0 }
            ?.output?.trim()?.let { value -> value == "yes" }

    companion object {
        const val UNIT = "usage-monitor.service"

        /**
         * A unit, em função pura. `Restart=on-failure` com 30 s: com o app aberto, o
         * serviço sai com 1 (a trava é do app) e tenta de novo, e assume quando o app
         * fechar. `SuccessExitStatus=143`: a JVM sai com 143 no `SIGTERM`, que é o `stop`.
         */
        fun unitText(executable: String): String =
            """
            |[Unit]
            |Description=Usage Monitor (coleta sem janela)
            |After=network-online.target
            |Wants=network-online.target
            |
            |[Service]
            |Type=simple
            |ExecStart=${quoted(executable)} service run
            |Restart=on-failure
            |RestartSec=30
            |SuccessExitStatus=143
            |
            |[Install]
            |WantedBy=default.target
            |""".trimMargin()

        /** Aspas do systemd: o caminho do pacote tem espaço (`/opt/usage-monitor/bin/Usage Monitor`). */
        fun quoted(path: String): String = "\"" + path.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

        /**
         * O executável que a unit chama. Pela árvore do script do Linux, o launcher estável
         * (`~/.local/bin/usage-monitor`), que segue a versão ativa; pelo pacote, o binário
         * dele. Sem `jpackage.app-path` (o app não foi instalado), nada.
         */
        fun executable(appPath: String?, home: File): String? {
            val path = appPath?.takeIf { value -> value.isNotBlank() } ?: return null
            val launcher = File(home, ".local/bin/usage-monitor")
            val managed = runCatching { File(path).canonicalPath }.getOrDefault(path).contains("/usage-monitor/versions/")
            return if (managed && launcher.canExecute()) launcher.path else path
        }
    }
}
