package com.usagemonitor.domain

import com.usagemonitor.domain.entity.AppUpdateReceipt
import com.usagemonitor.domain.entity.AppUpdateReceiptStatus
import com.usagemonitor.domain.entity.ReleaseNotesDecision
import com.usagemonitor.domain.entity.parseReleaseNoteItems
import com.usagemonitor.domain.entity.releaseNotesDecision
import com.usagemonitor.domain.entity.releaseNotesPreviousVersion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReleaseNotesTest {

    /** O corpo real que `.github/workflows/release-linux.yml` gera. */
    private val generatedBody = """
        ## Changes

        - Compare: [v38.0.0...v39.0.0](https://github.com/edilsonvilarinho/usage-monitor/compare/v38.0.0...v39.0.0)

        - feat(update): prune the applied artifact (`a1b2c3d`)
        - fix(installer): stop killing every JVM on the machine (`e4f5a6b`)
        - chore: bump version to v39.0.0 (`0123456`)
        - docs(plan): record the A21 status row (`789abcd`)
        - ci: run the installer scenarios on windows (`fedcba9`)
    """.trimIndent()

    @Test
    fun `only user-facing commits survive, without the type prefix or the hash`() {
        val items = parseReleaseNoteItems(generatedBody)

        assertEquals(
            listOf(
                "prune the applied artifact",
                "stop killing every JVM on the machine"
            ),
            items
        )
    }

    @Test
    fun `the compare line is not a change`() {
        // Ela é um item de lista como qualquer outro e passaria pelo filtro de
        // bullet livre se não fosse descartada por nome.
        val items = parseReleaseNoteItems(
            "- Compare: [v1...v2](https://example.invalid/compare)\n- feat: algo novo (`abcdef1`)"
        )

        assertEquals(listOf("algo novo"), items)
    }

    @Test
    fun `a breaking change marker does not hide the item`() {
        val items = parseReleaseNoteItems("- feat(api)!: drop the legacy endpoint (`abcdef1`)")

        assertEquals(listOf("drop the legacy endpoint"), items)
    }

    @Test
    fun `a hand written bullet passes through untouched`() {
        // Release editada à mão: descartar o que não casa Conventional faria uma
        // nota escrita para o usuário virar tela vazia.
        val items = parseReleaseNoteItems("- Agora o app volta sozinho depois de atualizar\n* Correções de tema escuro")

        assertEquals(
            listOf("Agora o app volta sozinho depois de atualizar", "Correções de tema escuro"),
            items
        )
    }

    @Test
    fun `a release with nothing user-facing yields no items`() {
        val items = parseReleaseNoteItems(
            "## Changes\n\n- chore: bump version to v40.0.0 (`abcdef1`)\n- docs: fix a typo (`1234567`)"
        )

        assertTrue(items.isEmpty())
    }

    @Test
    fun `identical subjects collapse into one line`() {
        val items = parseReleaseNoteItems(
            "- fix(ui): align the column header (`aaaaaaa`)\n- fix(ui): align the column header (`bbbbbbb`)"
        )

        assertEquals(listOf("align the column header"), items)
    }

    @Test
    fun `headings and prose are not items`() {
        val items = parseReleaseNoteItems("## Changes\n\nAlgum texto solto.\n\n- feat: item (`abcdef1`)")

        assertEquals(listOf("item"), items)
    }

    @Test
    fun `an absent or blank body yields no items`() {
        assertTrue(parseReleaseNoteItems(null).isEmpty())
        assertTrue(parseReleaseNoteItems("   \n  ").isEmpty())
    }

    @Test
    fun `a fresh install marks the version and stays quiet`() {
        // Sem marca e sem recibo: ninguém atualizou nada. "Novidades" para quem
        // não tem versão anterior não descreve mudança nenhuma.
        assertEquals(
            ReleaseNotesDecision.MARK_SEEN_ONLY,
            releaseNotesDecision(currentVersion = "39.0.0", seenVersion = null, hasUpdateReceipt = false)
        )
    }

    @Test
    fun `a missing mark with a receipt on disk still opens the notes`() {
        // O estado de quem foi atingido pela issue #127: a janela nunca abriu, e
        // por isso nada foi marcado. O recibo prova que a máquina já atualizou
        // alguma vez, então não é instalação nova.
        assertEquals(
            ReleaseNotesDecision.SHOW,
            releaseNotesDecision(currentVersion = "39.0.0", seenVersion = null, hasUpdateReceipt = true)
        )
    }

    @Test
    fun `a version already seen does not open again`() {
        assertEquals(
            ReleaseNotesDecision.SKIP,
            releaseNotesDecision(currentVersion = "39.0.0", seenVersion = "39.0.0", hasUpdateReceipt = true)
        )
    }

    @Test
    fun `a mark from an older version does not block the new one`() {
        assertEquals(
            ReleaseNotesDecision.SHOW,
            releaseNotesDecision(currentVersion = "39.0.0", seenVersion = "38.0.0", hasUpdateReceipt = false)
        )
    }

    @Test
    fun `the decision does not depend on a receipt`() {
        // A correção da #127 em uma linha: sem recibo nenhum — instalação
        // manual, macOS, ou o Linux antes de o script gravar o arquivo — a
        // janela abre igual.
        assertEquals(
            releaseNotesDecision(currentVersion = "39.0.0", seenVersion = "38.0.0", hasUpdateReceipt = true),
            releaseNotesDecision(currentVersion = "39.0.0", seenVersion = "38.0.0", hasUpdateReceipt = false)
        )
    }

    @Test
    fun `a rollback re-marks instead of announcing a version that is not running`() {
        // O `health-timeout` do linux-updater.sh: o app novo chega a abrir a
        // janela e a marcar a versão, e o script então restaura a anterior.
        // Anunciar a 38.0.0 vindo da 39.0.0 seria falso, e sem reescrever a
        // marca o usuário perderia as novidades da 39 para sempre.
        assertEquals(
            ReleaseNotesDecision.MARK_SEEN_ONLY,
            releaseNotesDecision(currentVersion = "38.0.0", seenVersion = "39.0.0", hasUpdateReceipt = true)
        )
    }

    @Test
    fun `a mark in another spelling of the same version is re-normalized, not announced`() {
        // Strings diferentes, versão igual: a igualdade textual não pega, e
        // anunciar seria repetir a mesma release.
        assertEquals(
            ReleaseNotesDecision.MARK_SEEN_ONLY,
            releaseNotesDecision(currentVersion = "38.0.2", seenVersion = "38.0.02", hasUpdateReceipt = true)
        )
    }

    @Test
    fun `an unreadable version never opens the notes`() {
        // A comparação falha fechado, e o pior desfecho é silêncio com marca.
        assertEquals(
            ReleaseNotesDecision.MARK_SEEN_ONLY,
            releaseNotesDecision(currentVersion = "sem-numero", seenVersion = "38.0.2", hasUpdateReceipt = true)
        )
    }

    @Test
    fun `the receipt names the previous version on the windows path`() {
        // Recibo da atualização que trouxe este binário: é a fonte exata, porque
        // o instalador leu a versão anterior do registro antes de sobrescrevê-la.
        assertEquals(
            "37.0.0",
            releaseNotesPreviousVersion(receipt("39.0.0"), currentVersion = "39.0.0", seenVersion = "38.0.0")
        )
    }

    @Test
    fun `a stale receipt from the previous update does not name the previous version`() {
        // O estado real do Linux na primeira abertura: o arquivo ainda descreve
        // a atualização anterior, porque o script só o grava depois do ACK.
        assertEquals(
            "38.0.0",
            releaseNotesPreviousVersion(receipt("38.0.0"), currentVersion = "39.0.0", seenVersion = "38.0.0")
        )
    }

    @Test
    fun `a failed receipt does not name the previous version`() {
        val failed = receipt("39.0.0", status = AppUpdateReceiptStatus.FAILED)

        assertEquals(
            "38.0.0",
            releaseNotesPreviousVersion(failed, currentVersion = "39.0.0", seenVersion = "38.0.0")
        )
    }

    @Test
    fun `without a receipt the mark says where we came from`() {
        assertEquals(
            "38.0.0",
            releaseNotesPreviousVersion(receipt = null, currentVersion = "39.0.0", seenVersion = "38.0.0")
        )
    }

    @Test
    fun `a receipt without a previous version falls back to the mark`() {
        // O instalador nem sempre consegue lê-la, e descartar a marca aqui
        // apagaria o subtítulo em vez de completá-lo.
        val withoutPrevious = AppUpdateReceipt(
            version = "39.0.0",
            previousVersion = null,
            status = AppUpdateReceiptStatus.SUCCESS,
            reason = null
        )

        assertEquals(
            "38.0.0",
            releaseNotesPreviousVersion(withoutPrevious, currentVersion = "39.0.0", seenVersion = "38.0.0")
        )
    }

    @Test
    fun `with neither receipt nor mark there is no subtitle`() {
        assertNull(releaseNotesPreviousVersion(receipt = null, currentVersion = "39.0.0", seenVersion = null))
    }

    private fun receipt(
        version: String,
        status: AppUpdateReceiptStatus = AppUpdateReceiptStatus.SUCCESS
    ) = AppUpdateReceipt(
        version = version,
        previousVersion = "37.0.0",
        status = status,
        reason = null
    )
}
