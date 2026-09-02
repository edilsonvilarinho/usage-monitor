package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.QuotaInfo
import com.usagemonitor.domain.entity.UsageAccountKey
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.domain.entity.UsageUnit
import com.usagemonitor.presentation.ui.components.hudQuotaShortLabel
import com.usagemonitor.presentation.ui.components.hudQuotaChipText
import com.usagemonitor.presentation.ui.orderedByCardOrder
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A linha que a barra HUD mostra parada: uma fonte, com o percentual de todas as
 * cotas dela, na ordem que o usuário arrastou no dashboard.
 */
class HudTopLineFormattingTest {

    private val now = Instant.parse("2026-09-01T18:00:00Z")

    private fun quota(label: String, used: Long) = QuotaInfo(
        label = label,
        used = used,
        total = 100L,
        periodEndAt = now,
        periodType = PeriodType.INTERVAL,
        unit = UsageUnit.PERCENTAGE
    )

    // ------------------------------------------------------------- rótulo curto

    @Test
    fun `o rotulo curto e a ultima palavra`() {
        assertEquals("5h", hudQuotaShortLabel("Claude 5h"))
        assertEquals("7d", hudQuotaShortLabel("Claude 7d"))
        assertEquals("semanal", hudQuotaShortLabel("Go semanal"))
        assertEquals("mensal", hudQuotaShortLabel("Codex mensal"))
    }

    /** Rótulo de uma palavra só continua inteiro — não sobra nada para cortar. */
    @Test
    fun `rotulo de uma palavra passa intacto`() {
        assertEquals("Saldo", hudQuotaShortLabel("Saldo"))
        assertEquals("Créditos", hudQuotaShortLabel("Créditos"))
    }

    // -------------------------------------------------------------------- chip

    /**
     * Com um número só, quem olha não sabe qual janela está vendo — foi o que
     * faltava na linha da conta.
     */
    @Test
    fun `o chip junta rotulo curto e percentual`() {
        assertEquals("5h 88%", hudQuotaChipText(quota("Claude 5h", 88)))
        assertEquals("7d 9%", hudQuotaChipText(quota("Claude 7d", 9)))
    }

    @Test
    fun `o chip usa o percentual do card`() {
        // Truncado, nunca arredondado — a mesma regra do resto do app.
        assertEquals("mensal 75%", hudQuotaChipText(quota("Codex mensal", 75)))
    }

    // ------------------------------------------------------------------- ordem

    private fun target(source: ApiSource, profile: String? = null) = if (profile == null) {
        UsageTargetKey.forSource(source)
    } else {
        UsageTargetKey(source, profile)
    }

    private data class Row(val target: UsageTargetKey, val name: String)

    /** "Deve respeitar a ordem que ele escolher." */
    @Test
    fun `a ordem de cards manda`() {
        val anthropic = target(ApiSource.ANTHROPIC, "padrao")
        val codex = target(ApiSource.CODEX)
        val rows = listOf(Row(codex, "codex"), Row(anthropic, "anthropic"))

        val ordered = orderedByCardOrder(rows, listOf(anthropic, codex)) { row -> row.target }

        assertEquals(listOf("anthropic", "codex"), ordered.map { it.name })
    }

    /**
     * Uma fonte tem várias cotas, e todas têm de sair juntas e na ordem em que
     * chegaram: é `groupBy`, não `associateBy`.
     */
    @Test
    fun `varias linhas do mesmo alvo saem juntas e na ordem`() {
        val anthropic = target(ApiSource.ANTHROPIC, "padrao")
        val codex = target(ApiSource.CODEX)
        val rows = listOf(
            Row(codex, "codex 5h"),
            Row(anthropic, "anthropic 5h"),
            Row(anthropic, "anthropic 7d")
        )

        val ordered = orderedByCardOrder(rows, listOf(anthropic, codex)) { row -> row.target }

        assertEquals(listOf("anthropic 5h", "anthropic 7d", "codex 5h"), ordered.map { it.name })
    }

    /**
     * Fonte recém-habilitada ainda não tem posição escolhida. Escondê-la seria
     * pior que pô-la por último.
     */
    @Test
    fun `alvo sem posicao vai para o fim`() {
        val anthropic = target(ApiSource.ANTHROPIC, "padrao")
        val codex = target(ApiSource.CODEX)
        val rows = listOf(Row(codex, "codex"), Row(anthropic, "anthropic"))

        val ordered = orderedByCardOrder(rows, listOf(anthropic)) { row -> row.target }

        assertEquals(listOf("anthropic", "codex"), ordered.map { it.name })
    }

    @Test
    fun `sem ordem escolhida nada e reordenado`() {
        val anthropic = target(ApiSource.ANTHROPIC, "padrao")
        val codex = target(ApiSource.CODEX)
        val rows = listOf(Row(codex, "codex"), Row(anthropic, "anthropic"))

        val ordered = orderedByCardOrder(rows, emptyList()) { row -> row.target }

        assertEquals(listOf("codex", "anthropic"), ordered.map { it.name })
    }
}
