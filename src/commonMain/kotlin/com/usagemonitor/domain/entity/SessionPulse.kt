package com.usagemonitor.domain.entity

import kotlinx.datetime.Instant

/**
 * Janela de "interação recente".
 *
 * Uma sessão só entra no semáforo enquanto tiver turno dentro dela. Não é uma
 * janela de quota nem de agregação: é o tempo em que ainda faz sentido dizer que
 * alguém está trabalhando naquela sessão neste instante.
 */
const val ACTIVE_SESSION_WINDOW_MILLIS = 5L * 60 * 1_000

/**
 * Sessão com interação recente cujo veredito pede atenção.
 *
 * Só existe para sessão laranja ou vermelha: a saudável não vira alerta porque o
 * semáforo não tem nada a dizer sobre ela.
 */
data class ActiveSessionAlert(
    val sessionId: String,
    val health: CliSessionHealth,
    val lastActivityAt: Instant,
    val projectName: String? = null,
    /** Máquina onde a sessão roda; preenchido na visão do time. */
    val machineLabel: String? = null,
    /** Apelido de quem roda a sessão; preenchido na visão do time. */
    val memberAlias: String? = null
)

/**
 * O que o semáforo de um botão do card deve mostrar agora.
 *
 * Vazio significa botão normal — é o estado da esmagadora maioria do tempo.
 */
data class SessionPulse(
    val alerts: List<ActiveSessionAlert> = emptyList()
) {
    /**
     * Severidades presentes, da menor para a maior.
     *
     * É a sequência do pisca: com laranja e vermelho ao mesmo tempo o botão
     * alterna entre as duas cores, em vez de mostrar só a pior e esconder que há
     * mais de um problema.
     */
    val severities: List<CliSessionHealth>
        get() = alerts.map { alert -> alert.health }.distinct().sorted()

    val worstHealth: CliSessionHealth?
        get() = alerts.maxOfOrNull { alert -> alert.health }

    val isPulsing: Boolean
        get() = alerts.isNotEmpty()

    fun countOf(health: CliSessionHealth): Int {
        return alerts.count { alert -> alert.health == health }
    }

    /**
     * Descarta o que saiu da janela de atividade.
     *
     * Existe porque o valor publicado sobrevive a uma leitura que falhou: sem o
     * envelhecimento, um servidor de time fora do ar deixaria o botão piscando
     * indefinidamente com a última sessão que ele conheceu.
     */
    fun prunedAt(now: Instant): SessionPulse {
        val cutoffMillis = now.toEpochMilliseconds() - ACTIVE_SESSION_WINDOW_MILLIS
        val surviving = alerts.filter { alert ->
            alert.lastActivityAt.toEpochMilliseconds() >= cutoffMillis
        }
        if (surviving.size == alerts.size) {
            return this
        }
        return SessionPulse(surviving)
    }

    companion object {
        val EMPTY = SessionPulse()
    }
}

/**
 * Converte sessões em alertas do semáforo.
 *
 * O veredito vem de [CliSessionSummary.contextStatus] — o mesmo que a lista e o
 * detalhe mostram, sem uma segunda regra que pudesse discordar deles. Sessão de
 * janela de contexto desconhecida fica de fora, na mesma regra de [tallyHealth]:
 * sem a janela não há fração, e afirmar saúde ali seria afirmar o que não se sabe.
 *
 * [memberAlias] e [machineLabel] só são preenchidos na visão do time; na visão
 * local a máquina é sempre esta e repeti-la no texto seria ruído.
 */
fun Iterable<CliSessionSummary>.toSessionPulse(
    now: Instant,
    memberAlias: String? = null,
    machineLabel: String? = null
): SessionPulse {
    val cutoffMillis = now.toEpochMilliseconds() - ACTIVE_SESSION_WINDOW_MILLIS

    val alerts = withKnownWindow()
        .filter { session -> session.lastTs.toEpochMilliseconds() >= cutoffMillis }
        .mapNotNull { session ->
            val health = session.contextStatus.health
            if (health == CliSessionHealth.HEALTHY) {
                return@mapNotNull null
            }
            ActiveSessionAlert(
                sessionId = session.sessionId,
                health = health,
                lastActivityAt = session.lastTs,
                projectName = session.projectName,
                machineLabel = machineLabel,
                memberAlias = memberAlias
            )
        }

    return SessionPulse(alerts)
}

/** Junta os pulsos de várias origens — os integrantes de uma conta do time. */
fun Iterable<SessionPulse>.mergeSessionPulses(): SessionPulse {
    val alerts = flatMap { pulse -> pulse.alerts }
    if (alerts.isEmpty()) {
        return SessionPulse.EMPTY
    }
    return SessionPulse(alerts.sortedByDescending { alert -> alert.health })
}
