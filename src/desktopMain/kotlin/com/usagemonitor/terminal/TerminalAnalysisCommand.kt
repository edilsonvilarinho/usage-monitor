package com.usagemonitor.terminal

import com.usagemonitor.domain.entity.AnalysisWindowView
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.SessionAnalysis
import com.usagemonitor.presentation.terminal.TerminalAccount
import com.usagemonitor.presentation.terminal.TerminalAnalysisLabels
import com.usagemonitor.presentation.terminal.TerminalAnalysisView
import com.usagemonitor.presentation.terminal.TerminalAnsi
import com.usagemonitor.presentation.terminal.TerminalLabels
import com.usagemonitor.presentation.terminal.TerminalQuotaTime
import com.usagemonitor.presentation.terminal.TerminalRace
import com.usagemonitor.presentation.terminal.TerminalStatusView
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.round
import kotlin.time.Instant

/**
 * `usage-monitor analysis [conta] [--json]` (#463, AI3): lê a última coleta e os bancos em somente
 * leitura, como a TUI, e imprime a corrida de cada conta com análise. Nenhuma requisição.
 */
internal object TerminalAnalysisCommand {

    fun run(command: TerminalCommand.Analysis, languageOverride: AppLanguage?, reader: TerminalReader, console: TerminalConsole, stores: () -> TuiStores = ::TuiStores): Int {
        val reading = reader.read(languageOverride)
        val accounts = TerminalAnalysisReader.eligible(reading.accounts).filter { account -> matches(account, command.filter) }
        val items = if (accounts.isEmpty()) emptyList() else stores().use { found -> accounts.map { account -> account to TerminalAnalysisReader.read(account, found, reading.now) } }
        if (command.json) {
            console.print(TerminalAnalysisJson.encode(items, reading.now, asciiOnly = !console.profile.unicode))
            return if (items.isEmpty()) TerminalStatusView.EXIT_NO_READING else TerminalStatusView.EXIT_OK
        }
        val age = reading.lastCollectedAt?.let { at -> reading.now - at }
        val output = TerminalAnalysisView.render(
            items, reading.now, reading.collector, age,
            TerminalAnalysisLabels(reading.language), TerminalLabels(reading.language), console.profile, command.filter
        )
        console.print(TerminalAnsi.render(output.lines, console.profile))
        return output.exitCode
    }

    /** `claude` (ou `anthropic`) e `codex` escolhem a fonte; outro texto, o apelido ou o nome da conta. */
    fun matches(account: TerminalAccount, filter: String?): Boolean {
        val wanted = filter?.trim()?.lowercase()?.takeIf { text -> text.isNotEmpty() } ?: return true
        val source = when (wanted) {
            "claude", "anthropic" -> ApiSource.ANTHROPIC
            "codex" -> ApiSource.CODEX
            else -> null
        }
        if (source != null) return account.source == source
        return account.profile?.lowercase() == wanted || account.name.lowercase() == wanted
    }
}

/**
 * O `analysis --json` (#463). **Contrato**, como o do `status --json`: campo novo pode entrar,
 * campo existente não muda sem subir [VERSION]. Inglês e `snake_case`; `null` é não medido.
 */
internal object TerminalAnalysisJson {
    const val VERSION = 1

    private val json = Json {
        encodeDefaults = true
        prettyPrint = true
        explicitNulls = true
    }

    fun encode(items: List<Pair<TerminalAccount, SessionAnalysis?>>, now: Instant, asciiOnly: Boolean = false): String {
        val dto = AnalysisDto(
            version = VERSION,
            generatedAt = now.toString(),
            accounts = items.map { (account, analysis) ->
                AccountDto(
                    source = account.source.name.lowercase(),
                    sourceName = account.name,
                    account = account.profile,
                    windows = listOfNotNull(analysis?.fiveHour, analysis?.weekly).map { window -> window(window, now) }
                )
            }
        )
        val text = json.encodeToString(AnalysisDto.serializer(), dto)
        return if (asciiOnly) {
            buildString(text.length) { for (char in text) if (char.code < 128) append(char) else append("\\u").append(char.code.toString(16).padStart(4, '0')) }
        } else {
            text
        }
    }

    private fun window(window: AnalysisWindowView, now: Instant): WindowDto {
        val race = TerminalRace.of(window, now)
        val projected = window.projectedAtReset
        return WindowDto(
            label = window.quotaLabel,
            period = window.periodType.name.lowercase(),
            percent = window.percent,
            startsAt = window.startsAt?.toString(),
            resetsAt = window.resetsAt?.toString(),
            timeElapsedPercent = TerminalQuotaTime.between(window.startsAt, window.resetsAt, now)?.elapsedPercent,
            paceLastHour = window.paceNow?.let(::rounded),
            paceAverage = window.paceAverage?.let(::rounded),
            safePace = window.safePace?.let(::rounded),
            projectedAtReset = projected,
            verdict = projected?.let { value -> if (value >= 100) "runs_out" else "fits" },
            exhaustsAtLastHourPace = race.lastHour?.exhaustIn?.let { value -> (now + value).toString() },
            exhaustsAtAveragePace = race.average?.exhaustIn?.let { value -> (now + value).toString() }
        )
    }

    /** Pontos por hora com três casas: o ritmo não precisa de 13,333333333. */
    private fun rounded(value: Double): Double = round(value * 1000.0) / 1000.0

    @Serializable
    private data class AnalysisDto(
        @SerialName("version") val version: Int,
        @SerialName("generated_at") val generatedAt: String,
        @SerialName("accounts") val accounts: List<AccountDto>
    )

    @Serializable
    private data class AccountDto(
        @SerialName("source") val source: String,
        @SerialName("source_name") val sourceName: String,
        @SerialName("account") val account: String?,
        @SerialName("windows") val windows: List<WindowDto>
    )

    @Serializable
    private data class WindowDto(
        @SerialName("label") val label: String,
        @SerialName("period") val period: String,
        @SerialName("percent") val percent: Int,
        @SerialName("starts_at") val startsAt: String?,
        @SerialName("resets_at") val resetsAt: String?,
        @SerialName("time_elapsed_percent") val timeElapsedPercent: Int?,
        @SerialName("pace_last_hour") val paceLastHour: Double?,
        @SerialName("pace_average") val paceAverage: Double?,
        @SerialName("safe_pace") val safePace: Double?,
        @SerialName("projected_at_reset") val projectedAtReset: Int?,
        /** `fits` ou `runs_out`, o veredito da janela "Análise" do app; `null` sem projeção. */
        @SerialName("verdict") val verdict: String?,
        /** Quando a cota acaba em cada ritmo, mesmo depois do reinício; `null` sem ritmo ou parado. */
        @SerialName("exhausts_at_last_hour_pace") val exhaustsAtLastHourPace: String?,
        @SerialName("exhausts_at_average_pace") val exhaustsAtAveragePace: String?
    )
}
