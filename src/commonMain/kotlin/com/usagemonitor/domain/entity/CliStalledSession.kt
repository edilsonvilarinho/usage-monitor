package com.usagemonitor.domain.entity

import kotlinx.datetime.Instant

/**
 * O que a cauda do transcript diz sobre o último pedido de uma sessão.
 *
 * O índice sozinho não responde a isto. `cli_turns` só guarda turnos do
 * assistente, e uma sessão encerrada normalmente é indistinguível de uma travada
 * pelo `last_ts`: as duas simplesmente param de produzir turno. Medido sobre os
 * 323 transcripts de três contas reais, a regra "sem turno novo há X" marcaria as
 * 323 sessões.
 *
 * O que separa as duas é o marcador de fim de turno que o próprio CLI escreve no
 * transcript (`{"type":"system","subtype":"turn_duration"}`). Havendo pedido do
 * usuário **depois** do último marcador, o turno foi aberto e nunca fechou.
 */
enum class CliSessionTailOutcome {

    /** Há pedido sem marcador de fim depois dele: o turno não fechou. */
    PENDING_REQUEST,

    /** O último marcador de fim é posterior ao último pedido: turno fechado. */
    TURN_COMPLETED,

    /**
     * Não dá para afirmar nada.
     *
     * Cauda sem nenhum marcador (sessão de subagente, sessão conduzida por harness
     * de agente, transcript curto), arquivo ausente ou ilegível. Marcar aqui seria
     * afirmar o que não se sabe — a mesma recusa de [withKnownWindow].
     */
    NOT_EVALUATED
}

/**
 * Fato bruto lido da cauda de um transcript, sem veredito.
 *
 * Quem decide se isso é uma sessão travada é [detectStalledSessions], que precisa
 * do relógio e do limiar; guardar o veredito aqui daria dois donos da regra.
 */
data class CliSessionTail(
    val sessionId: String,
    val outcome: CliSessionTailOutcome,
    /** Carimbo do último pedido sem resposta; só preenchido em [CliSessionTailOutcome.PENDING_REQUEST]. */
    val lastRequestAt: Instant? = null,
    /** Carimbo do último marcador de fim de turno encontrado na janela lida. */
    val lastTurnEndAt: Instant? = null
)

/** Duas horas: tempo em que nenhuma resposta chega sem que algo esteja errado. */
const val DEFAULT_STALL_THRESHOLD_MILLIS = 2L * 60 * 60 * 1_000

/**
 * Piso do limiar oferecido na tela.
 *
 * Abaixo de meia hora o aviso alcançaria turno longo legítimo — uma resposta com
 * muitas ferramentas e um subagente demora minutos, não segundos.
 */
const val MIN_STALL_THRESHOLD_MILLIS = 30L * 60 * 1_000

/**
 * Teto: acima disto a sessão está abandonada, não travada.
 *
 * Não é estética. Terminal fechado no meio de um turno deixa a cauda pendente
 * **para sempre** — as quatro sessões pendentes encontradas nas contas reais têm
 * mais de 500 horas. Sem o teto elas virariam alerta a cada arranque do app, já
 * que a deduplicação de [UsageAlertState] vive em memória. E processo que não
 * existe há um dia não está queimando cota nenhuma, que é o que a detecção existe
 * para flagrar.
 */
const val STALLED_SESSION_MAX_AGE_MILLIS = 24L * 60 * 60 * 1_000

/**
 * Quanto da cauda do transcript é lido.
 *
 * Medido: 256 KB reproduzem o veredito do arquivo inteiro nos 323 transcripts
 * reais. Com 64 KB um caso degrada para [CliSessionTailOutcome.NOT_EVALUATED] —
 * subdetecção, nunca alarme falso, que é a direção segura para errar.
 */
const val SESSION_TAIL_WINDOW_BYTES = 256 * 1024

/**
 * Sessão cujo último pedido não recebeu resposta dentro do limiar.
 *
 * O nome diz "stalled" e não "hung" de propósito: a evidência é ausência de
 * resposta no transcript, nunca a morte de um processo — o app não olha o sistema
 * operacional. A frase que chega ao usuário carrega a mesma reserva.
 */
data class StalledCliSession(
    val sessionId: String,
    val projectName: String?,
    val profileId: String?,
    /** Instante do pedido que ficou sem resposta. */
    val pendingSince: Instant,
    /** Há quanto tempo ele está sem resposta, no instante da avaliação. */
    val pendingMillis: Long
)

/**
 * Decide quais sessões estão sem resposta há tempo demais.
 *
 * Função pura, no mesmo desenho de [toSessionPulse]: entra o que foi lido, sai o
 * veredito, sem relógio próprio nem I/O.
 *
 * [thresholdMillis] **não é normalizado aqui**. Quem o normaliza é
 * [UsageAlertSettings], que é o dono do valor; um segundo saneamento nesta função
 * esconderia configuração inválida em vez de corrigi-la num lugar só. Limiar
 * acima de [STALLED_SESSION_MAX_AGE_MILLIS] não detecta nada, e isso é a
 * consequência honesta de pedir um limiar maior que o teto.
 *
 * A ordem é total e determinística — mais antigo primeiro, `sessionId` desempata.
 * Não é estética: duas leituras iguais têm de produzir listas iguais, ou o
 * `StateFlow` reemite e a tela recompõe a cada tique do laço.
 */
fun detectStalledSessions(
    sessions: Iterable<CliSessionSummary>,
    tails: Map<String, CliSessionTail>,
    now: Instant,
    thresholdMillis: Long = DEFAULT_STALL_THRESHOLD_MILLIS
): List<StalledCliSession> {
    val nowMillis = now.toEpochMilliseconds()

    return sessions
        .mapNotNull { session ->
            val tail = tails[session.sessionId] ?: return@mapNotNull null
            if (tail.outcome != CliSessionTailOutcome.PENDING_REQUEST) {
                return@mapNotNull null
            }
            val pendingSince = tail.lastRequestAt ?: return@mapNotNull null

            val pendingMillis = nowMillis - pendingSince.toEpochMilliseconds()
            if (pendingMillis < thresholdMillis || pendingMillis > STALLED_SESSION_MAX_AGE_MILLIS) {
                return@mapNotNull null
            }

            StalledCliSession(
                sessionId = session.sessionId,
                projectName = session.projectName,
                profileId = session.profileId,
                pendingSince = pendingSince,
                pendingMillis = pendingMillis
            )
        }
        .sortedWith(
            compareByDescending<StalledCliSession> { stalled -> stalled.pendingMillis }
                .thenBy { stalled -> stalled.sessionId }
        )
}
