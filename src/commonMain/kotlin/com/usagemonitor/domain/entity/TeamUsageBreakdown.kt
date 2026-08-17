package com.usagemonitor.domain.entity

import kotlinx.datetime.Instant

/**
 * O consumo do time da janela recortado por projeto, branch, modelo e integrante.
 *
 * Reusa [toUsageBreakdown] — o mesmo dobrador do resumo da própria máquina — em
 * cima das linhas cruas que o servidor devolveu. É isso que garante que os dois
 * modais precifiquem igual: uma segunda agregação aqui divergiria da local no
 * primeiro ajuste de tarifa.
 *
 * O servidor não manda grade por hora nem ferramenta, então
 * [CliUsageBreakdown.heatmap] e [CliUsageBreakdown.byTool] ficam vazios e a tela
 * pula as duas seções — não é lacuna a preencher com número inventado. O tempo
 * de trabalho por sessão, esse, ele manda a partir da versão 0.7.0; contra
 * servidor anterior o mapa nasce vazio e as horas ficam nulas.
 */
fun TeamUsageSnapshot.toTeamBreakdown(
    window: CliRangeWindow,
    now: Instant
): CliUsageBreakdown {
    val activeTimes = activeTimesOf(members)
    val breakdown = members.flatMap { member -> member.groupRows }.toUsageBreakdown(activeTimes)

    // Integrante sem linha nenhuma fica de fora — uma linha zerada por pessoa que
    // não trabalhou no período é ruído, e a lista de integrantes já a mostra.
    val active = members.filter { member -> member.groupRows.isNotEmpty() }
    val labels = memberLabels(active)

    // O balde do integrante é o `totals` do resumo dele: recontar aqui abriria um
    // segundo caminho de soma que poderia não bater com o total da tela.
    val byMember = active
        .map { member ->
            member.groupRows
                .toUsageBreakdown(activeTimesOf(listOf(member)))
                .totals
                .copy(label = labels[member.memberKey])
        }
        .rankedByCost()

    val burnRate = burnRateOf(
        totals = breakdown.totals,
        windowStart = window.cutoffMillis?.let { millis -> Instant.fromEpochMilliseconds(millis) },
        now = now,
        windowEndsAt = window.endsAt
    )

    return breakdown.copy(byMember = byMember, burnRate = burnRate)
}

/**
 * Tempo de trabalho por sessão, a partir do que o servidor mediu.
 *
 * Sessão sem medida fica **fora** do mapa em vez de entrar com zero: mapa vazio
 * é o sinal de "servidor não mede isto", e um zero ali o apagaria.
 */
private fun activeTimesOf(members: List<TeamMemberUsage>): Map<String, Long> {
    return members
        .flatMap { member -> member.sessions }
        .mapNotNull { session -> session.activeMillis?.let { millis -> session.sessionId to millis } }
        .toMap()
}

/** Quanto do `deviceId` basta para separar dois apelidos iguais na tela. */
private const val SHORT_DEVICE_ID_LENGTH = 8

/**
 * Rótulo de cada integrante no eixo, por `memberKey`.
 *
 * Nos outros três eixos o rótulo **é** a chave da agregação, então ele é único
 * por construção. Aqui não: a agregação é por integrante e o apelido é texto que
 * a pessoa digitou — a mesma máquina que perdeu o `team.json` volta com outro
 * `deviceId` e o mesmo apelido, que é justamente o caso que o botão de remover
 * existe para limpar. Dois baldes com o mesmo rótulo quebrariam a leitura ("qual
 * dos dois SUETONIO?") e, antes disso, a unicidade que a lista da tela exige.
 *
 * O desempate é o começo do `deviceId`, e não a máquina: as duas linhas do caso
 * real têm o mesmo `hostName` também. Só quem repete apelido ganha o sufixo —
 * carimbá-lo em todo mundo poluiria o caso normal para resolver o excepcional.
 */
private fun memberLabels(members: List<TeamMemberUsage>): Map<String, String> {
    val aliasCounts = members.groupingBy { member -> member.alias }.eachCount()

    return members.associate { member ->
        val label = if ((aliasCounts[member.alias] ?: 0) > 1) {
            "${member.alias} (${member.deviceId.take(SHORT_DEVICE_ID_LENGTH)})"
        } else {
            member.alias
        }
        member.memberKey to label
    }
}
