package com.usagemonitor.presentation.ui

import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.TeamCertificateProblem
import com.usagemonitor.domain.entity.TeamConnectionFailure
import com.usagemonitor.domain.entity.TeamConnectionStage
import com.usagemonitor.domain.entity.TeamStageStatus
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** Qual credencial a última etapa testa: a chave da conta ou o token do admin. */
enum class TeamCredentialKind { ACCOUNT_KEY, ADMIN_TOKEN }

private val MONTHS_EN = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/**
 * Frases do caminho da conexão com o servidor de time (issue #494).
 *
 * Dono único do texto: as janelas de time, as Configurações, o bot e o terminal
 * dizem a mesma coisa da mesma falha. Diz **de quem** é o problema e o que não
 * chegou a ser testado — o que a mensagem da exceção nunca disse.
 */
object TeamConnectionLabels {

    fun stage(stage: TeamConnectionStage, kind: TeamCredentialKind, language: AppLanguage): String {
        return when (stage) {
            TeamConnectionStage.ADDRESS -> localized(language, "Endereço", "Address")
            TeamConnectionStage.CONNECTION -> localized(language, "Conexão", "Connection")
            TeamConnectionStage.CERTIFICATE -> localized(language, "Certificado", "Certificate")
            TeamConnectionStage.SERVER -> localized(language, "Servidor", "Server")
            TeamConnectionStage.CREDENTIAL -> when (kind) {
                TeamCredentialKind.ACCOUNT_KEY -> localized(language, "Chave", "Key")
                TeamCredentialKind.ADMIN_TOKEN -> localized(language, "Token admin", "Admin token")
            }
        }
    }

    /** "Parou em Certificado — venceu em 27/10 12:21 BRT". */
    fun headline(failure: TeamConnectionFailure, kind: TeamCredentialKind, language: AppLanguage): String {
        val stageName = stage(failure.stage, kind, language)
        return localized(language, "Parou em $stageName", "Stopped at $stageName") + " — " + reason(failure, language)
    }

    /** O que aconteceu, de quem é o conserto e, se for o caso, o que não foi testado. */
    fun advice(failure: TeamConnectionFailure, kind: TeamCredentialKind, language: AppLanguage): String {
        val cause = cause(failure, kind, language)
        if (failure.stage == TeamConnectionStage.CREDENTIAL) {
            return cause
        }
        val untested = when (kind) {
            TeamCredentialKind.ACCOUNT_KEY -> localized(language, "A chave não chegou a ser testada.", "Your key was not tested.")
            TeamCredentialKind.ADMIN_TOKEN -> localized(language, "O token admin não chegou a ser testado.", "The admin token was not tested.")
        }
        return "$cause $untested"
    }

    /** Texto à direita de cada etapa no caminho completo. */
    fun stageDetail(
        failure: TeamConnectionFailure,
        status: TeamStageStatus,
        kind: TeamCredentialKind,
        language: AppLanguage
    ): String {
        return when (status) {
            TeamStageStatus.PASSED -> "ok"
            TeamStageStatus.FAILED -> reason(failure, language)
            TeamStageStatus.NOT_REACHED -> localized(language, "não alcançado", "not reached")
            TeamStageStatus.UNKNOWN -> localized(language, "não medido", "not measured")
        }
    }

    fun retry(language: AppLanguage): String = localized(language, "Tentar de novo", "Try again")

    fun copyDetail(language: AppLanguage): String = localized(language, "Copiar detalhe técnico", "Copy technical detail")

    private fun reason(failure: TeamConnectionFailure, language: AppLanguage): String {
        return when (failure.stage) {
            TeamConnectionStage.ADDRESS -> localized(language, "o nome não resolve", "name does not resolve")
            TeamConnectionStage.CONNECTION -> if (failure.timedOut) {
                localized(language, "tempo esgotado", "timed out")
            } else {
                localized(language, "nada responde", "nothing answers")
            }
            TeamConnectionStage.CERTIFICATE -> certificateReason(failure, language)
            TeamConnectionStage.SERVER -> localized(language, "erro", "error") + " (HTTP ${failure.httpStatus})"
            TeamConnectionStage.CREDENTIAL -> localized(language, "recusada", "refused") + " (HTTP ${failure.httpStatus})"
        }
    }

    private fun certificateReason(failure: TeamConnectionFailure, language: AppLanguage): String {
        return when (failure.certificateProblem) {
            TeamCertificateProblem.EXPIRED -> {
                val notAfter = failure.certificateNotAfter
                if (notAfter == null) {
                    localized(language, "venceu", "expired")
                } else {
                    localized(language, "venceu em ", "expired ") + brtDate(notAfter, language) + " BRT"
                }
            }
            TeamCertificateProblem.NOT_YET_VALID -> localized(language, "ainda não vale", "not yet valid")
            TeamCertificateProblem.UNTRUSTED -> localized(language, "não confiável", "not trusted")
            TeamCertificateProblem.HOSTNAME_MISMATCH -> localized(language, "não cobre este endereço", "does not cover this address")
            TeamCertificateProblem.OTHER,
            null -> localized(language, "conexão segura falhou", "secure connection failed")
        }
    }

    private fun cause(failure: TeamConnectionFailure, kind: TeamCredentialKind, language: AppLanguage): String {
        return when (failure.stage) {
            TeamConnectionStage.ADDRESS -> localized(
                language,
                "O nome do servidor não resolve: confira o endereço em Configurações › Time.",
                "The server name does not resolve: check the address in Settings › Team."
            )
            TeamConnectionStage.CONNECTION -> if (failure.timedOut) {
                localized(language, "O servidor não respondeu a tempo: confira a rede e o proxy.", "The server did not answer in time: check the network and the proxy.")
            } else {
                localized(language, "Nada responde no servidor: ele está fora do ar ou um firewall bloqueia.", "Nothing answers at the server: it is down or a firewall blocks it.")
            }
            TeamConnectionStage.CERTIFICATE -> certificateCause(failure, language)
            TeamConnectionStage.SERVER -> localized(
                language,
                "O servidor respondeu com erro (HTTP ${failure.httpStatus}): quem administra o servidor precisa olhar o log dele.",
                "The server answered with an error (HTTP ${failure.httpStatus}): whoever runs the server has to check its log."
            )
            TeamConnectionStage.CREDENTIAL -> when (kind) {
                TeamCredentialKind.ACCOUNT_KEY -> localized(
                    language,
                    "O servidor recusou a chave. Confira a chave da conta em Configurações › Time.",
                    "The server refused the key. Check the account key in Settings › Team."
                )
                TeamCredentialKind.ADMIN_TOKEN -> localized(
                    language,
                    "O servidor recusou o token admin. Confira o token em Configurações › Time.",
                    "The server refused the admin token. Check the token in Settings › Team."
                )
            }
        }
    }

    private fun certificateCause(failure: TeamConnectionFailure, language: AppLanguage): String {
        return when (failure.certificateProblem) {
            TeamCertificateProblem.EXPIRED -> localized(
                language,
                "O certificado do servidor venceu: quem administra o servidor precisa renová-lo.",
                "The server's certificate expired: whoever runs the server has to renew it."
            )
            TeamCertificateProblem.NOT_YET_VALID -> localized(
                language,
                "O certificado do servidor ainda não vale: confira o relógio deste computador.",
                "The server's certificate is not valid yet: check this computer's clock."
            )
            TeamCertificateProblem.UNTRUSTED -> localized(
                language,
                "O certificado do servidor não vem de uma autoridade confiável neste computador.",
                "The server's certificate does not come from an authority this computer trusts."
            )
            TeamCertificateProblem.HOSTNAME_MISMATCH -> localized(
                language,
                "O certificado do servidor não cobre este endereço: confira o endereço em Configurações › Time.",
                "The server's certificate does not cover this address: check the address in Settings › Team."
            )
            TeamCertificateProblem.OTHER,
            null -> localized(language, "A conexão segura com o servidor falhou.", "The secure connection to the server failed.")
        }
    }

    /** PT "27/10 12:21", EN "Oct 27, 12:21", sempre em BRT. */
    private fun brtDate(instant: Instant, language: AppLanguage): String {
        val local = instant.toLocalDateTime(TimeZone.of("America/Sao_Paulo"))
        val clock = "${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}"
        return localized(
            language,
            "${local.day.toString().padStart(2, '0')}/${local.month.ordinal.plus(1).toString().padStart(2, '0')} $clock",
            "${MONTHS_EN[local.month.ordinal]} ${local.day}, $clock"
        )
    }
}
