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
 * A ordem é total, determinística e **imune a carimbo de tempo**: conta primeiro,
 * depois trabalhando, depois online parado, e o alias como desempate dentro de
 * cada faixa.
 *
 * **A conta é a chave primária**, pela mesma razão de
 * `TeamUsageViewModel.flattenAccounts`: a tela agrupa por ordem de primeira
 * aparição, então quem ordena os integrantes ordena as faixas de conta. Com o
 * estado no topo, a faixa de uma conta ia parar onde o integrante que bateu o
 * heartbeat mais tarde a levasse, e as três contas trocavam de lugar entre dois
 * tiques do laço de 5s. O rótulo é o e-mail que o administrador digitou ao emitir
 * a chave e não muda sozinho — é ele que dá uma posição estável; conta sem rótulo
 * vai depois de todas as identificadas, por um degrau próprio do comparador e não
 * por sentinela de texto, com o `accountKey` separando duas sem rótulo.
 *
 * **Nenhum carimbo de tempo entra na ordenação.** `lastSeenAt` é o heartbeat de
 * 30s do `TeamSyncService`: entre dois integrantes online ele muda a cada batida e
 * não informa nada — os dois estão dentro dos mesmos [PRESENCE_ONLINE_WINDOW_MILLIS].
 * `lastActivityAt` anda a cada turno de quem está trabalhando. Qualquer um dos
 * dois como critério reordena a lista sem nada ter mudado de fato. A informação
 * não se perde: as duas horas continuam impressas nas colunas Estado e
 * "Trabalhando agora".
 *
 * `isWorkingNow` e `isOnline` continuam antes do alias porque são a pergunta que
 * esta tela responde, e mudam de estado — não de posição relativa a cada tique.
 *
 * Na janela de **uma** conta o bloco da conta é no-op (todos os integrantes têm o
 * mesmo `accountKey`), então não existem dois caminhos de ordenação para manter em
 * acordo.
 *
 * Nada disso é estética: é requisito anti-flicker. Duas leituras iguais têm de
 * produzir listas iguais, ou o `StateFlow` reemite e a tela recompõe sozinha a
 * cada 5 segundos.
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
        compareBy<TeamMemberPresence> { entry -> if (entry.accountLabel == null) 1 else 0 }
            .thenBy { entry -> entry.accountLabel?.lowercase().orEmpty() }
            .thenBy { entry -> entry.accountKey.orEmpty() }
            .thenByDescending { entry -> entry.isWorkingNow }
            .thenByDescending { entry -> entry.isOnline }
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
