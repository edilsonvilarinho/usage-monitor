package com.usagemonitor.presentation.terminal

import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.CollectorKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class TuiTeamViewTest {

    private val now = Instant.parse("2026-10-21T17:23:00Z")
    private val pt = TuiLabels(AppLanguage.PT)
    private val color = TerminalProfile(TerminalPalette.TRUECOLOR_DARK, unicode = true)
    private val ascii = TerminalProfile(TerminalPalette.NONE, unicode = false)

    private fun member(alias: String, working: Boolean, online: Boolean, cost: Long, lastSeen: Instant? = now) =
        TuiTeamMember(alias, online, working, if (working) 1 else 0, now - 3.minutes, lastSeen, sessions = 2, tokens = 4_800_000, costMicros = cost, costIncomplete = false)

    private val team = TuiTeam(
        accounts = listOf("trabalho", "pessoal"),
        accountIndex = 0,
        members = listOf(member("Ana", true, true, 12_400_000), member("Bruno", false, true, 4_950_000), member("Diego", false, false, 800_000, now - 2.hours - 10.minutes)),
        referenceNow = now,
        readAt = now
    )

    private fun screen(data: TuiTeam? = team, state: TuiTeamState = TuiTeamState(), size: TuiSize = TuiSize(96, 20), profile: TerminalProfile = color) =
        TuiScreen.render(TuiState(tab = TuiTab.TEAM, team = state), TuiData(now, CollectorKind.SERVICE, now, teamConfigured = true, team = data), size, pt, profile).map { line -> line.plainText }

    @Test
    fun `conta, pessoas ativas, presenca escrita e o consumo do dia`() {
        val lines = screen()
        assertTrue(lines[1].contains("4 Time"))
        assertTrue(lines[3].startsWith("│ trabalho ") && lines[3].endsWith("3 pessoas · 1 ativa agora │"), lines[3])
        assertTrue(lines[5].startsWith("│     PESSOA "))
        assertTrue(lines[6].startsWith("│ ▸ ● Ana ") && lines[6].contains("trabalhando · 1 sessão") && lines[6].contains("4,8M") && lines[6].contains("\$12.40"), lines[6])
        assertTrue(lines[7].startsWith("│   ◑ Bruno ") && lines[7].contains("app aberto · turno há 3 min"), lines[7])
        assertTrue(lines[8].startsWith("│   ○ Diego ") && lines[8].contains("ausente · há 2 h"), lines[8])
        assertTrue(lines[10].startsWith("│ ● trabalhando · ◑ app aberto · ○ ausente "), lines[10])
        assertTrue(lines[11].startsWith("│ custo calculado no seu computador · nunca prompt nem resposta "), lines[11])
        assertTrue(lines[lines.size - 2].startsWith("│ ↑↓ pessoa  t troca de conta  1–5 aba"))
    }

    @Test
    fun `sem cor e sem Unicode a presenca vira asterisco, mais e traco`() {
        val lines = screen(profile = ascii)
        assertTrue(lines[6].startsWith("| > * Ana "), lines[6])
        assertTrue(lines[7].startsWith("|   + Bruno "))
        assertTrue(lines[8].startsWith("|   - Diego "))
    }

    @Test
    fun `leitura que falhou diz o que houve e de quando sao os numeros`() {
        val lines = screen(team.copy(failure = "servidor fora do ar", readAt = now - 15.minutes))
        assertTrue(lines.any { line -> "│ ▲ Não deu para ler o time: servidor fora do ar" in line })
        assertTrue(lines.any { line -> "Mostrando os números de 14:08; tenta de novo na próxima leitura." in line })
        val offline = screen(team.copy(failure = "Failed to connect to localhost/[0:0:0:0:0:0:0:1]:3917", unreachable = true))
        assertTrue(offline.any { line -> "│ ▲ Não deu para ler o time: o servidor do time não respondeu" in line })
        // Sem leitura nenhuma, nada de "0 pessoas": não foi medido.
        val never = screen(team.copy(members = emptyList(), failure = "x", unreachable = true, readAt = null))
        assertTrue(never[3].startsWith("│ trabalho ") && "pessoas" !in never[3], never[3])
        assertTrue(never.any { line -> "Ainda sem números do time; tenta de novo na próxima leitura." in line })
    }

    @Test
    fun `t troca de conta e pede releitura, e so com mais de uma`() {
        val two = TuiReducer.reduce(TuiState(tab = TuiTab.TEAM), TuiKey.Text('t'), TuiContext(true, false, teamAccountCount = 2))
        assertEquals(1, two.state.team.accountIndex)
        assertEquals(listOf<TuiEffect>(TuiEffect.Refresh), two.effects)
        assertEquals(emptyList(), TuiReducer.reduce(TuiState(tab = TuiTab.TEAM), TuiKey.Text('t'), TuiContext(true, false, teamAccountCount = 1)).effects)
        assertTrue(screen(team.copy(accounts = listOf("trabalho"))).last { line -> "pessoa" in line }.contains("t troca").not())
    }

    @Test
    fun `em 80 colunas o apelido fica com o que sobra da frase de presenca`() {
        val lines = screen(team.copy(members = listOf(member("Ana Carolina", false, true, 0))), size = TuiSize(80, 20))
        assertTrue(lines[6].startsWith("│ ▸ ◑ Ana Carolina app aberto · turno há 3 min "), lines[6])
    }

    @Test
    fun `largura e altura exatas de 80 a 200 colunas`() {
        for (size in listOf(TuiSize(200, 40), TuiSize(96, 20), TuiSize(80, 20), TuiSize(60, 14))) {
            val lines = screen(size = size)
            assertEquals(size.rows, lines.size)
            assertTrue(lines.all { line -> line.length == size.columns }, "$size")
        }
    }
}
