package com.usagemonitor.presentation.terminal

import com.usagemonitor.presentation.ui.TelegramBotMessages
import com.usagemonitor.presentation.ui.formatMicrosUsdShort
import kotlin.time.Instant

/**
 * Aba 4, Time (3.4): quem está trabalhando, com o app aberto ou ausente, e o consumo do dia
 * de cada pessoa na conta escolhida, pelo mesmo `GET` da janela de time do app. O servidor não
 * guarda cota por pessoa, e a tela não as deriva: as cotas da conta estão na aba 1. Ordem da
 * presença do app (`toTeamPresence`), total e sem carimbo de tempo no comparador.
 */
internal object TuiTeamView {
    /** `▸ ` da seleção e a marca de presença: a seleção não esconde o estado. */
    private const val MARK_WIDTH = 4
    private const val NOW_WIDTH = 34
    private const val NOW_MIN_WIDTH = 30
    private const val ALIAS_MAX_WIDTH = 26
    private const val NUMBER_WIDTH = 9
    private const val COST_WIDTH = 11

    fun render(team: TuiTeam?, state: TuiTeamState, size: TuiSize, labels: TuiTeamLabels, terminalLabels: TerminalLabels, profile: TerminalProfile): TuiTabView {
        val shortcuts = buildList {
            add(TuiShortcut("↑↓", labels.keyPerson))
            if ((team?.accounts?.size ?: 0) > 1) {
                add(TuiShortcut("t", labels.keySwitch))
            }
        }
        team ?: return TuiTabView(emptyList(), shortcuts)
        val glyphs = profile.glyphs
        val body = mutableListOf(header(team, size.bodyWidth, labels))
        body += TerminalLine.EMPTY
        team.failure?.let { failure ->
            body += terminalLine(TerminalSpan(glyphs.warning + " ", TerminalColor.Warn), TerminalSpan(labels.failed(if (team.unreachable) labels.unreachable else failure)))
            body += terminalLine(TerminalSpan("  " + labels.showing(team.readAt?.let(TelegramBotMessages::clock)), TerminalColor.Muted))
            body += TerminalLine.EMPTY
        }
        if (team.members.isEmpty()) {
            if (team.readAt != null) {
                body += terminalLine(TerminalSpan(labels.noMembers, TerminalColor.Muted))
            }
            return TuiTabView(body, shortcuts)
        }
        // A coluna AGORA guarda o que a frase mais longa pede ("app aberto · turno há 40 min");
        // o resto vai para o apelido, que em 80 colunas ficaria com 9 células se fosse o contrário.
        val free = size.bodyWidth - 1 - MARK_WIDTH - 2 * NUMBER_WIDTH - COST_WIDTH
        val aliasWidth = (free - NOW_MIN_WIDTH).coerceIn(8, ALIAS_MAX_WIDTH)
        val nowWidth = (free - aliasWidth).coerceIn(12, NOW_WIDTH)
        body += TuiFrame.header(
            " ".repeat(MARK_WIDTH) + labels.headerPerson.padEnd(aliasWidth) + labels.headerNow.padEnd(nowWidth) +
                labels.headerSessions.padStart(NUMBER_WIDTH) + labels.headerTokens.padStart(NUMBER_WIDTH) + labels.headerToday.padStart(COST_WIDTH)
        )
        // O cabeçalho já está no corpo; sobram as duas linhas da legenda e a em branco antes dela.
        val visible = (size.bodyHeight - body.size - 3).coerceAtLeast(1)
        val selected = state.selected.coerceIn(0, team.members.lastIndex)
        val start = TuiFrame.scrollStart(selected, team.members.size, visible)
        team.members.subList(start, (start + visible).coerceAtMost(team.members.size)).forEachIndexed { offset, member ->
            body += row(member, team.referenceNow, start + offset == selected, aliasWidth, nowWidth, size.bodyWidth, labels, terminalLabels, glyphs)
        }
        body += TerminalLine.EMPTY
        body += terminalLine(TerminalSpan(labels.legend(glyphs), TerminalColor.Muted))
        body += terminalLine(TerminalSpan(labels.privacy, TerminalColor.Muted))
        return TuiTabView(body, shortcuts)
    }

    /**
     * O rótulo da conta em negrito e, à direita, quantas pessoas e quantas trabalhando agora.
     * Sem leitura nenhuma ainda, a contagem não aparece: seria "0 pessoas" sem ter medido.
     */
    private fun header(team: TuiTeam, width: Int, labels: TuiTeamLabels): TerminalLine {
        val left = terminalLine(TerminalSpan(team.accounts.getOrNull(team.accountIndex).orEmpty(), bold = true))
        if (team.readAt == null) {
            return left
        }
        val counts = labels.counts(team.members.size, team.members.count { member -> member.workingNow })
        val gap = width - 1 - left.width - counts.length
        return if (gap >= 2) left + terminalLine(TerminalSpan(" ".repeat(gap) + counts, TerminalColor.Muted)) else left
    }

    private fun row(
        member: TuiTeamMember,
        now: Instant,
        selected: Boolean,
        aliasWidth: Int,
        nowWidth: Int,
        width: Int,
        labels: TuiTeamLabels,
        terminalLabels: TerminalLabels,
        glyphs: TerminalGlyphs
    ): TerminalLine {
        val (mark, markColor) = when {
            member.workingNow -> glyphs.presenceWorking to TerminalColor.Ok
            member.online -> glyphs.presenceOnline to TerminalColor.Ok
            else -> glyphs.presenceAway to TerminalColor.Muted
        }
        val state = when {
            member.workingNow -> labels.working(member.activeSessions)
            member.online -> labels.appOpen(member.lastActivityAt?.let { at -> terminalLabels.ago(now - at) })
            else -> labels.away((member.lastSeenAt ?: member.lastActivityAt)?.let { at -> terminalLabels.ago(now - at) })
        }
        val cost = formatMicrosUsdShort(member.costMicros) + if (member.costIncomplete) "+" else ""
        val content = terminalLine(
            TerminalSpan(member.alias.take(aliasWidth - 1).padEnd(aliasWidth), bold = true),
            TerminalSpan(state.take(nowWidth - 1).padEnd(nowWidth), TerminalColor.Muted),
            TerminalSpan(member.sessions.toString().padStart(NUMBER_WIDTH)),
            TerminalSpan(TuiText.tokens(member.tokens, terminalLabels.pt).padStart(NUMBER_WIDTH)),
            TerminalSpan(cost.padStart(COST_WIDTH), bold = true)
        )
        val pointer = if (selected) TerminalSpan(glyphs.selection + " ", TerminalColor.Gold) else TerminalSpan("  ")
        val line = terminalLine(pointer, TerminalSpan("$mark ", markColor)) + content
        return if (selected) line.fit(width - 1).selected() else line
    }
}
