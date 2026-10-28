package com.usagemonitor.domain.entity

import kotlin.time.Instant

/**
 * Etapas da conversa com o servidor de time, na ordem em que acontecem (issue #494).
 *
 * A tela mostra onde a conexão parou, e não a mensagem da exceção: quem lê
 * "(certificate_expired) PKIX path validation failed" não sabe que o problema é
 * do servidor, nem que a chave dele nem chegou a ser testada.
 */
enum class TeamConnectionStage { ADDRESS, CONNECTION, CERTIFICATE, SERVER, CREDENTIAL }

/** Por que o certificado do servidor foi recusado, quando o tipo da causa diz. */
enum class TeamCertificateProblem { EXPIRED, NOT_YET_VALID, UNTRUSTED, HOSTNAME_MISMATCH, OTHER }

enum class TeamStageStatus { PASSED, FAILED, NOT_REACHED, UNKNOWN }

/**
 * Falha classificada pela etapa em que parou.
 *
 * [certificateNotAfter] vem do certificado recusado, nunca do texto da exceção;
 * nulo quando a cadeia de causas não o traz. [earlierStagesProven] é falso quando
 * a falha não prova que as etapas anteriores passaram (tempo esgotado no socket
 * não diz se o nome resolveu) — a tela não marca ✓ no que não foi medido.
 */
data class TeamConnectionFailure(
    val stage: TeamConnectionStage,
    val certificateProblem: TeamCertificateProblem? = null,
    val certificateNotAfter: Instant? = null,
    val httpStatus: Int? = null,
    val timedOut: Boolean = false,
    val earlierStagesProven: Boolean = true
)

/** Situação de cada etapa, na ordem de [TeamConnectionStage]. */
fun TeamConnectionFailure.stageStatuses(): List<Pair<TeamConnectionStage, TeamStageStatus>> {
    return TeamConnectionStage.entries.map { candidate ->
        val status = when {
            candidate == stage -> TeamStageStatus.FAILED
            candidate.ordinal > stage.ordinal -> TeamStageStatus.NOT_REACHED
            earlierStagesProven -> TeamStageStatus.PASSED
            else -> TeamStageStatus.UNKNOWN
        }
        candidate to status
    }
}

/**
 * Falha que carrega o status HTTP da resposta.
 *
 * Porta para a apresentação classificar 401/403/5xx sem importar a exceção da
 * camada `data` (`TeamServerException`), que é quem a implementa.
 */
interface HttpStatusFailure {
    val statusCode: Int
}
