package com.usagemonitor.domain

import com.usagemonitor.domain.entity.AppUpdateReceipt
import com.usagemonitor.domain.entity.AppUpdateReceiptStatus
import com.usagemonitor.domain.entity.parseReleaseNoteItems
import com.usagemonitor.domain.entity.shouldShowReleaseNotes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    fun `the notes open once for the version that was just installed`() {
        assertTrue(shouldShowReleaseNotes(receipt("39.0.0"), currentVersion = "39.0.0", seenVersion = null))
    }

    @Test
    fun `a version already seen does not open again`() {
        // O recibo só é sobrescrito na atualização seguinte: sem esta marca a
        // janela abriria em toda inicialização até lá.
        assertFalse(shouldShowReleaseNotes(receipt("39.0.0"), currentVersion = "39.0.0", seenVersion = "39.0.0"))
    }

    @Test
    fun `a mark from an older version does not block the new one`() {
        assertTrue(shouldShowReleaseNotes(receipt("39.0.0"), currentVersion = "39.0.0", seenVersion = "38.0.0"))
    }

    @Test
    fun `a failed update has no news to announce`() {
        val failed = receipt("39.0.0", status = AppUpdateReceiptStatus.FAILED)

        assertFalse(shouldShowReleaseNotes(failed, currentVersion = "37.0.0", seenVersion = null))
    }

    @Test
    fun `a receipt for a version that is not running does not open the notes`() {
        // Recibo da 39 com o app em 37 é prova de que a troca não se completou.
        assertFalse(shouldShowReleaseNotes(receipt("39.0.0"), currentVersion = "37.0.0", seenVersion = null))
    }

    @Test
    fun `no receipt means no notes`() {
        assertFalse(shouldShowReleaseNotes(receipt = null, currentVersion = "37.0.0", seenVersion = null))
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
