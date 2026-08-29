package com.usagemonitor

import com.usagemonitor.data.datasource.restrictToOwnerReadWrite
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Origem provavel do arranque. Ela vem de um argumento na linha de comando
 * ([AUTO_START_ARGUMENT]), porque o processo lancado pela chave `Run` e o lancado
 * pelo atalho tem o mesmo pai (o Explorer) e sao indistinguiveis sem isso.
 */
internal enum class StartupOrigin {
    AUTOSTART,
    MANUAL;

    val wireValue: String
        get() = when (this) {
            AUTOSTART -> "autostart"
            MANUAL -> "manual"
        }

    companion object {
        const val AUTO_START_ARGUMENT = "--autostart"

        fun from(arguments: Array<String>): StartupOrigin {
            val isAutoStart = arguments.any { it.trim() == AUTO_START_ARGUMENT }
            return if (isAutoStart) AUTOSTART else MANUAL
        }
    }
}

internal enum class StartupOutcome {
    STARTED,
    SECOND_INSTANCE_EXIT,

    /**
     * A instancia viva atendeu o pedido de foco deixado por outra. Sem esta linha
     * nao ha como separar "o pedido nunca foi lido" de "foi lido e a janela nao
     * veio para a frente" -- que sao defeitos em lugares diferentes.
     */
    FOCUS_REQUEST_SERVED,

    /**
     * A janela principal foi mapeada e ja pode ser lida de volta.
     *
     * `STARTED` e gravado antes de existir janela, e por isso nao consegue
     * responder o que o sistema fez com o pedido de "sempre visivel" -- ele so
     * pode ser lido depois do mapeamento (issue #120). Separar o segundo
     * registro por desfecho, e nao por campo, e o que permite achar as duas
     * linhas do mesmo arranque e comparar o que foi pedido com o que ficou.
     *
     * Valor novo em enum existente e **excecao declarada** a regra do
     * `CLAUDE.md`, pelo mesmo motivo de `FOCUS_REQUEST_SERVED`: ha um `when`
     * exaustivo sobre `wireValue`, e o erro de compilacao garante que o valor de
     * fio existe.
     */
    WINDOW_SHOWN;

    val wireValue: String
        get() = when (this) {
            STARTED -> "started"
            SECOND_INSTANCE_EXIT -> "second-instance-exit"
            FOCUS_REQUEST_SERVED -> "focus-request-served"
            WINDOW_SHOWN -> "window-shown"
        }
}

/**
 * Ambiente grafico em que o processo subiu, lido das variaveis XDG.
 *
 * Existe para separar as hipoteses do "sempre visivel" ignorado (issue #120):
 * uma sessao X11, uma sessao Wayland e uma janela XWayland dentro do Wayland
 * respondem de forma diferente a `_NET_WM_STATE_ABOVE`, e o compositor que
 * decide (KWin, Mutter) muda com o desktop. Sem saber em qual combinacao o app
 * subiu, a medicao na maquina real nao e interpretavel.
 */
internal data class LinuxGraphicsEnvironment(
    val sessionType: String?,
    val desktop: String?
)

/**
 * Funcao pura com o ambiente **injetado** porque a suite roda no Windows, onde
 * `XDG_SESSION_TYPE` nao existe: ler `System.getenv` la dentro tornaria a
 * leitura nao testavel.
 *
 * O tipo de sessao e normalizado para minusculas -- `X11` e `x11` sao a mesma
 * resposta, e duas grafias no arquivo dariam duas linhas para o mesmo caso. O
 * `XDG_CURRENT_DESKTOP` vai **verbatim**: ele e uma lista separada por dois
 * pontos (`ubuntu:GNOME`), e recortar so o primeiro item perderia justamente o
 * que distingue uma sessao derivada da original.
 */
internal fun linuxGraphicsEnvironment(
    environment: (String) -> String? = System::getenv
): LinuxGraphicsEnvironment {
    return LinuxGraphicsEnvironment(
        sessionType = readEnvValue(environment, "XDG_SESSION_TYPE")?.lowercase(),
        desktop = readEnvValue(environment, "XDG_CURRENT_DESKTOP")
    )
}

/**
 * Variavel ausente e variavel presente em branco sao a mesma resposta: "nao
 * informado". Guardar `""` no arquivo faria um campo vazio parecer um valor
 * medido.
 */
private fun readEnvValue(environment: (String) -> String?, name: String): String? {
    return runCatching { environment(name) }
        .getOrNull()
        ?.trim()
        ?.takeIf { it.isNotBlank() }
}

@Serializable
internal data class StartupDiagnosticsEntry(
    val ts: String,
    val pid: Long,
    val version: String,
    val origin: String,
    val outcome: String,
    // Instante em que o processo nasceu, contra o `ts` acima. O delta entre os
    // dois e o custo do arranque do proprio app; o delta contra o boot do SO fica
    // para a analise, porque a JVM nao expoe o instante de boot de forma portatil
    // e um processo externo so para medi-lo custaria mais do que informa.
    val processStartedAt: String? = null,
    val startupLatencyMillis: Long? = null,
    // Contexto da maquina. Campo novo com default e retrocompativel -- as linhas
    // ja gravadas continuam desserializando --, ao contrario de valor novo em
    // enum. `null` significa "nao medido", nunca "medido e falso": nesta versao
    // so o Linux mede o ambiente grafico e a entrada de autostart, e num arquivo
    // do Windows um `false` afirmaria uma medida que ninguem fez.
    val osName: String? = null,
    val osVersion: String? = null,
    val sessionType: String? = null,
    val desktop: String? = null,
    val alwaysOnTopSupported: Boolean? = null,
    val autostartEntryPresent: Boolean? = null,
    val autostartEntryValid: Boolean? = null
)

/**
 * Contexto da maquina no instante do arranque, resolvido uma vez e reusado
 * pelos tres pontos que gravam.
 *
 * Objeto proprio em vez de sete parametros em [StartupDiagnostics.record]: os
 * sete sao a mesma medida e viajam juntos, e a resolucao deles toca disco
 * (le a entrada de autostart) -- fazer isso a cada chamada repetiria I/O que a
 * resposta nao muda.
 */
internal data class StartupMachineContext(
    val osName: String? = null,
    val osVersion: String? = null,
    val sessionType: String? = null,
    val desktop: String? = null,
    val alwaysOnTopSupported: Boolean? = null,
    val autostartEntryPresent: Boolean? = null,
    val autostartEntryValid: Boolean? = null
) {
    internal companion object {
        /**
         * Nada medido. E o default de [StartupDiagnostics.record] para a suite
         * nao tocar disco nem AWT ao exercitar o formato do arquivo -- quem mede
         * de verdade e o `main()`, que passa [current] explicitamente.
         */
        val EMPTY = StartupMachineContext()

        fun current(): StartupMachineContext {
            val isLinux = AutoStartManager.currentPlatform() == AutoStartManager.Platform.LINUX

            // So o Linux mede ambiente grafico e entrada de autostart. Nas outras
            // plataformas o campo fica `null` = "nao medido"; `false` ali
            // afirmaria uma medida que ninguem fez.
            val graphics = if (isLinux) linuxGraphicsEnvironment() else null
            val autostartEntry = if (isLinux) {
                runCatching { AutoStartManager.inspectLinuxAutostartEntry() }.getOrNull()
            } else {
                null
            }

            return StartupMachineContext(
                osName = System.getProperty("os.name"),
                osVersion = System.getProperty("os.version"),
                sessionType = graphics?.sessionType,
                desktop = graphics?.desktop,
                // A pergunta e do toolkit e nao da janela: ela responde se o
                // ambiente **aceita** o pedido, e separa "o app nao pediu" de "o
                // sistema nao suporta". A janela em si so pode ser lida depois de
                // mapeada, e isso e o segundo registro.
                alwaysOnTopSupported = runCatching {
                    java.awt.Toolkit.getDefaultToolkit().isAlwaysOnTopSupported
                }.getOrNull(),
                autostartEntryPresent = autostartEntry?.present,
                autostartEntryValid = autostartEntry?.valid
            )
        }
    }
}

/**
 * Uma linha por arranque em `~/.usage-monitor/diagnostics/startup.jsonl`.
 *
 * **Sempre ligado**, ao contrario do recorder de creditos da Anthropic e do
 * recorder do Codex, que exigem variavel de ambiente: aqueles gravam corpo de
 * resposta a cada coleta, este grava uma linha por arranque. Diagnostico que
 * depende de variavel configurada ANTES do fato nao serve para investigar o boot
 * que ja passou -- que foi exatamente o que faltou quando o autostart foi dado
 * como quebrado sem que nada no disco pudesse confirmar ou desmentir.
 *
 * Metadado de arranque apenas: nada de sessao, prompt, resposta ou credencial.
 */
internal class StartupDiagnostics(
    private val diagnosticsFile: File = defaultDiagnosticsFile(),
    private val json: Json = Json { encodeDefaults = true }
) {

    private val lock = Any()

    fun record(
        origin: StartupOrigin,
        outcome: StartupOutcome,
        version: String = CURRENT_APP_VERSION,
        pid: Long = ProcessHandle.current().pid(),
        processStartedAtMillis: Long? = currentProcessStartMillis(),
        nowMillis: Long = Clock.System.now().toEpochMilliseconds(),
        machineContext: StartupMachineContext = StartupMachineContext.EMPTY
    ) {
        val entry = StartupDiagnosticsEntry(
            ts = isoOf(nowMillis),
            pid = pid,
            version = version,
            origin = origin.wireValue,
            outcome = outcome.wireValue,
            processStartedAt = processStartedAtMillis?.let(::isoOf),
            startupLatencyMillis = processStartedAtMillis?.let { nowMillis - it },
            osName = machineContext.osName,
            osVersion = machineContext.osVersion,
            sessionType = machineContext.sessionType,
            desktop = machineContext.desktop,
            alwaysOnTopSupported = machineContext.alwaysOnTopSupported,
            autostartEntryPresent = machineContext.autostartEntryPresent,
            autostartEntryValid = machineContext.autostartEntryValid
        )

        // Falha aqui nao pode derrubar o arranque: o registro existe para explicar
        // o app, nao para impedi-lo de subir.
        runCatching { appendLine(json.encodeToString(entry)) }
    }

    private fun appendLine(line: String) {
        synchronized(lock) {
            diagnosticsFile.parentFile?.mkdirs()
            trimIfNeeded()
            diagnosticsFile.appendText("$line\n")
            restrictToOwnerReadWrite(diagnosticsFile.toPath())
        }
    }

    // O app sobe a cada logon; sem corte o arquivo cresceria para sempre. O corte
    // acontece ANTES do append, entao o limite superior real e MAX_LINES + 1.
    private fun trimIfNeeded() {
        if (!diagnosticsFile.exists()) {
            return
        }

        val lines = diagnosticsFile.readLines()
        if (lines.size <= MAX_LINES) {
            return
        }

        val kept = lines.takeLast(KEPT_LINES)
        diagnosticsFile.writeText(kept.joinToString(separator = "\n", postfix = "\n"))
    }

    internal companion object {
        const val MAX_LINES = 200
        const val KEPT_LINES = 100

        private fun isoOf(epochMillis: Long): String {
            return kotlinx.datetime.Instant.fromEpochMilliseconds(epochMillis).toString()
        }

        private fun currentProcessStartMillis(): Long? {
            return runCatching {
                ProcessHandle.current().info().startInstant().orElse(null)?.toEpochMilli()
            }.getOrNull()
        }

        fun defaultDiagnosticsFile(): File {
            val homeDir = System.getProperty("user.home")
                ?: throw IllegalStateException("Propriedade 'user.home' não disponível")

            return File(homeDir, ".usage-monitor/diagnostics/startup.jsonl")
        }
    }
}
