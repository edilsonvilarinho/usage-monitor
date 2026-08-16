package com.usagemonitor.domain.entity

import kotlinx.datetime.Instant

/**
 * Janela do heartbeat: o app é considerado aberto enquanto a última batida
 * couber aqui.
 *
 * São três batidas do laço de envio (30s). Não é arbitrário nos dois sentidos:
 *  - 60s (duas batidas) piscaria. Uma passada que indexa transcritos grandes, um
 *    soluço de rede ou um notebook que suspendeu por segundos já atrasa uma
 *    batida, e o estado alternaria sem nada ter mudado.
 *  - 150s ou mais manteria na tela, por dois minutos e meio, quem já fechou o
 *    app — que é exatamente a mentira que esta tela existe para não contar.
 *
 * A leitura é de 5 em 5 segundos, então a granularidade percebida é a do laço da
 * tela, não a desta janela.
 */
const val PRESENCE_ONLINE_WINDOW_MILLIS = 90L * 1_000

/**
 * Recorte da consulta de presença.
 *
 * Folgado de propósito sobre [ACTIVE_SESSION_WINDOW_MILLIS]: quem classifica
 * "trabalhando agora" é o cliente, e com um corte justo um desvio de relógio de
 * poucos minutos apagaria do servidor justamente as sessões que decidiriam a
 * classificação. Também é o que permite dizer a que horas foi o último turno de
 * quem está online e parado.
 */
const val PRESENCE_LOOKBACK_MILLIS = 30L * 60 * 1_000

/**
 * Acima disto o desvio medido é implausível e a medida é descartada, não
 * aplicada — corrigir o relógio em mais de um dia esconderia um erro de
 * configuração em vez de revelá-lo.
 */
const val PRESENCE_MAX_CLOCK_OFFSET_MILLIS = 24L * 60 * 60 * 1_000

/**
 * Estado de uma pessoa na tela de presença.
 *
 * Duas camadas distintas, e não uma escala única: **online** é o app aberto
 * (heartbeat), **trabalhando agora** é turno do Claude Code dentro de
 * [ACTIVE_SESSION_WINDOW_MILLIS]. Colapsá-las esconderia justamente o caso que
 * interessa a quem abre esta tela — quem está com o app aberto e parado.
 *
 * Deriva de [TeamMemberUsage] em vez de duplicar identidade: alias, host, conta,
 * rótulo e saúde já vivem lá, e um segundo modelo com os mesmos campos
 * divergiria dele no primeiro campo novo.
 *
 * Não há enum de estado aqui de propósito. Dois booleanos derivados cobrem os
 * quatro casos sem travar a evolução; um enum novo obrigaria `when` exaustivos
 * na tela, que é a classe de quebra que este repositório já paga em
 * `UsageUnit` e `CliSessionRange`.
 */
data class TeamMemberPresence(
    val member: TeamMemberUsage,
    val isOnline: Boolean,
    val isWorkingNow: Boolean,
    /** Sessões com turno dentro da janela ativa. Zero quando não há. */
    val activeSessionCount: Int
) {
    val deviceId: String
        get() = member.deviceId

    val memberKey: String
        get() = member.memberKey

    val alias: String
        get() = member.alias

    val machineLabel: String
        get() = member.machineLabel

    val accountKey: String?
        get() = member.accountKey

    val accountLabel: String?
        get() = member.accountLabel

    /** Última batida de presença conhecida; `null` para quem nunca reportou. */
    val lastSeenAt: Instant?
        get() = member.lastSeenAt

    /** Turno mais recente dentro do recorte consultado. */
    val lastActivityAt: Instant?
        get() = member.lastActivityAt

    val worstHealth: CliSessionHealth?
        get() = member.worstHealth
}

/**
 * Classifica os integrantes contra [referenceNow].
 *
 * [referenceNow] é o agora **do servidor**, já corrigido pelo desvio medido —
 * nunca `Clock.System.now()` cru. `lastSeenAt` vem do relógio do servidor, e
 * comparar dois relógios diferentes é o que faz um time inteiro aparecer online
 * para sempre quando a máquina que lê está atrasada.
 *
 * A ordem é total e determinística — trabalhando primeiro, depois online parado,
 * depois offline; dentro de cada faixa, atividade mais recente primeiro, último
 * sinal como segundo critério e o alias como desempate final. Isso é requisito
 * anti-flicker, não estética: duas leituras iguais têm de produzir listas iguais,
 * ou o `StateFlow` reemite e a tela recompõe sozinha a cada 5 segundos.
 */
fun Iterable<TeamMemberUsage>.toTeamPresence(referenceNow: Instant): List<TeamMemberPresence> {
    val nowMillis = referenceNow.toEpochMilliseconds()
    val onlineCutoff = nowMillis - PRESENCE_ONLINE_WINDOW_MILLIS
    val activeCutoff = nowMillis - ACTIVE_SESSION_WINDOW_MILLIS

    val entries = map { member ->
        val lastSeenMillis = member.lastSeenAt?.toEpochMilliseconds()
        val activeSessions = member.sessions.count { session ->
            session.lastTs.toEpochMilliseconds() >= activeCutoff
        }
        TeamMemberPresence(
            member = member,
            isOnline = lastSeenMillis != null && lastSeenMillis >= onlineCutoff,
            isWorkingNow = activeSessions > 0,
            activeSessionCount = activeSessions
        )
    }

    return entries.sortedWith(
        compareByDescending<TeamMemberPresence> { entry -> entry.isWorkingNow }
            .thenByDescending { entry -> entry.isOnline }
            .thenByDescending { entry -> entry.lastActivityAt?.toEpochMilliseconds() ?: Long.MIN_VALUE }
            .thenByDescending { entry -> entry.lastSeenAt?.toEpochMilliseconds() ?: Long.MIN_VALUE }
            .thenBy { entry -> entry.alias.lowercase() }
            .thenBy { entry -> entry.deviceId }
    )
}

/**
 * `true` quando algum carimbo está no futuro além da tolerância.
 *
 * Só desvio de relógio explica: o servidor nunca carimba adiante do próprio
 * agora, então um `lastSeenAt` à frente da referência corrigida significa que os
 * dois relógios discordam. A tela avisa em vez de mostrar um número errado com
 * cara de certo — é o único caminho para o administrador descobrir um servidor
 * com NTP quebrado.
 */
fun Iterable<TeamMemberUsage>.hasSuspectClockSkew(referenceNow: Instant): Boolean {
    val limit = referenceNow.toEpochMilliseconds() + PRESENCE_ONLINE_WINDOW_MILLIS
    return any { member ->
        val lastSeenMillis = member.lastSeenAt?.toEpochMilliseconds() ?: return@any false
        lastSeenMillis > limit
    }
}
