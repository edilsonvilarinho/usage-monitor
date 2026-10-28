package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.TeamCertificateProblem
import com.usagemonitor.domain.entity.TeamConnectionFailure
import com.usagemonitor.domain.entity.TeamConnectionStage
import com.usagemonitor.domain.entity.TeamStageStatus
import com.usagemonitor.presentation.ui.TeamConnectionLabels
import com.usagemonitor.presentation.ui.TeamCredentialKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class TeamConnectionLabelsTest {

    private val expired = TeamConnectionFailure(
        stage = TeamConnectionStage.CERTIFICATE,
        certificateProblem = TeamCertificateProblem.EXPIRED,
        // 15:21 UTC = 12:21 BRT: o caso real do #494.
        certificateNotAfter = Instant.parse("2026-10-27T15:21:43Z")
    )

    @Test
    fun `headline names the stage and the expiry date in BRT`() {
        assertEquals(
            "Parou em Certificado — venceu em 27/10 12:21 BRT",
            TeamConnectionLabels.headline(expired, TeamCredentialKind.ACCOUNT_KEY, AppLanguage.PT)
        )
        assertEquals(
            "Stopped at Certificate — expired Oct 27, 12:21 BRT",
            TeamConnectionLabels.headline(expired, TeamCredentialKind.ACCOUNT_KEY, AppLanguage.EN)
        )
    }

    @Test
    fun `expired certificate without a date still says expired`() {
        assertEquals(
            "Stopped at Certificate — expired",
            TeamConnectionLabels.headline(expired.copy(certificateNotAfter = null), TeamCredentialKind.ACCOUNT_KEY, AppLanguage.EN)
        )
    }

    @Test
    fun `advice says whose problem it is and that the credential was not tested`() {
        assertEquals(
            "The server's certificate expired: whoever runs the server has to renew it. Your key was not tested.",
            TeamConnectionLabels.advice(expired, TeamCredentialKind.ACCOUNT_KEY, AppLanguage.EN)
        )
        assertEquals(
            "O certificado do servidor venceu: quem administra o servidor precisa renová-lo. O token admin não chegou a ser testado.",
            TeamConnectionLabels.advice(expired, TeamCredentialKind.ADMIN_TOKEN, AppLanguage.PT)
        )
    }

    @Test
    fun `credential stage is named by the credential kind`() {
        assertEquals("Key", TeamConnectionLabels.stage(TeamConnectionStage.CREDENTIAL, TeamCredentialKind.ACCOUNT_KEY, AppLanguage.EN))
        assertEquals("Token admin", TeamConnectionLabels.stage(TeamConnectionStage.CREDENTIAL, TeamCredentialKind.ADMIN_TOKEN, AppLanguage.PT))
    }

    @Test
    fun `refused credential advice does not claim an untested credential`() {
        val refused = TeamConnectionFailure(stage = TeamConnectionStage.CREDENTIAL, httpStatus = 401)

        assertEquals("Stopped at Key — refused (HTTP 401)", TeamConnectionLabels.headline(refused, TeamCredentialKind.ACCOUNT_KEY, AppLanguage.EN))
        assertEquals(
            "The server refused the key. Check the account key in Settings › Team.",
            TeamConnectionLabels.advice(refused, TeamCredentialKind.ACCOUNT_KEY, AppLanguage.EN)
        )
    }

    @Test
    fun `stage detail describes every status of the full path`() {
        val language = AppLanguage.EN
        val kind = TeamCredentialKind.ACCOUNT_KEY

        assertEquals("ok", TeamConnectionLabels.stageDetail(expired, TeamStageStatus.PASSED, kind, language))
        assertEquals("expired Oct 27, 12:21 BRT", TeamConnectionLabels.stageDetail(expired, TeamStageStatus.FAILED, kind, language))
        assertEquals("not reached", TeamConnectionLabels.stageDetail(expired, TeamStageStatus.NOT_REACHED, kind, language))
        assertEquals("not measured", TeamConnectionLabels.stageDetail(expired, TeamStageStatus.UNKNOWN, kind, language))
    }

    @Test
    fun `each failure kind has its own reason`() {
        val en = AppLanguage.EN
        val kind = TeamCredentialKind.ACCOUNT_KEY
        fun reason(failure: TeamConnectionFailure) = TeamConnectionLabels.stageDetail(failure, TeamStageStatus.FAILED, kind, en)

        assertEquals("name does not resolve", reason(TeamConnectionFailure(TeamConnectionStage.ADDRESS)))
        assertEquals("nothing answers", reason(TeamConnectionFailure(TeamConnectionStage.CONNECTION)))
        assertEquals("timed out", reason(TeamConnectionFailure(TeamConnectionStage.CONNECTION, timedOut = true)))
        assertEquals("not yet valid", reason(expired.copy(certificateProblem = TeamCertificateProblem.NOT_YET_VALID)))
        assertEquals("not trusted", reason(expired.copy(certificateProblem = TeamCertificateProblem.UNTRUSTED)))
        assertEquals("does not cover this address", reason(expired.copy(certificateProblem = TeamCertificateProblem.HOSTNAME_MISMATCH)))
        assertEquals("secure connection failed", reason(expired.copy(certificateProblem = TeamCertificateProblem.OTHER)))
        assertEquals("error (HTTP 502)", reason(TeamConnectionFailure(TeamConnectionStage.SERVER, httpStatus = 502)))
    }
}
