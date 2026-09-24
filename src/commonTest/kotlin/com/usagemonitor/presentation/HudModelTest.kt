package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.AntigravityQuotaLabels
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.QuotaInfo
import com.usagemonitor.domain.entity.QuotaRiskSummary
import com.usagemonitor.domain.entity.UsageRiskLevel
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.domain.entity.UsageUnit
import com.usagemonitor.presentation.ui.MAX_HUD_RINGS
import com.usagemonitor.presentation.ui.TRAY_TOOLTIP_MAX_CHARS
import com.usagemonitor.presentation.ui.buildHudAccounts
import com.usagemonitor.presentation.ui.hudSourceOrigin
import com.usagemonitor.presentation.ui.hudTraySummary
import com.usagemonitor.presentation.ui.hudUsedLeftText
import com.usagemonitor.presentation.ui.components.AppTone
import com.usagemonitor.presentation.viewmodel.HudQuotaEntry
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

private val HUD_NOW = Instant.parse("2026-09-24T12:00:00Z")
private val PADRAO = UsageTargetKey(ApiSource.ANTHROPIC, "padrao")
private val SANDBOX = UsageTargetKey(ApiSource.ANTHROPIC, "sandbox")
private val CODEX = UsageTargetKey.forSource(ApiSource.CODEX)

class HudModelTest {

    @Test
    fun `uma conta por alvo, na ordem dos cards e nao na do risco`() {
        val entries = listOf(
            entry(CODEX, "Codex 5h", used = 90, risk = UsageRiskLevel.WILL_EXCEED),
            entry(PADRAO, "Sessão 5h", used = 10, risk = UsageRiskLevel.ON_TRACK),
            entry(PADRAO, "Sessão 7d", used = 5, risk = UsageRiskLevel.ON_TRACK)
        )

        val accounts = buildHudAccounts(entries, cardOrder = listOf(PADRAO, CODEX), language = AppLanguage.PT, now = HUD_NOW)

        assertEquals(listOf(PADRAO, CODEX), accounts.map { account -> account.targetKey })
        assertEquals(listOf("5h", "7d"), accounts.first().quotas.map { quota -> quota.shortLabel })
    }

    @Test
    fun `a palavra e o tom saem da pior cota da conta`() {
        val entries = listOf(
            entry(PADRAO, "Sessão 5h", used = 12, risk = UsageRiskLevel.ON_TRACK),
            entry(PADRAO, "Sessão 7d", used = 88, risk = UsageRiskLevel.WILL_EXCEED)
        )

        val account = buildHudAccounts(entries, emptyList(), AppLanguage.PT, HUD_NOW).single()

        assertEquals(AppTone.CRITICAL, account.tone)
        assertEquals("Crítico", account.statusLabel)
        assertTrue(account.needsAttention)
    }

    /** O percentual do notch é o da cota de pior risco, não o da primeira. */
    @Test
    fun `o foco e a cota de pior risco e no empate a de maior percentual`() {
        val worstSecond = listOf(
            entry(PADRAO, "Sessão 5h", used = 70, risk = UsageRiskLevel.ON_TRACK),
            entry(PADRAO, "Sessão 7d", used = 40, risk = UsageRiskLevel.AT_RISK)
        )
        assertEquals("40%", buildHudAccounts(worstSecond, emptyList(), AppLanguage.PT, HUD_NOW).single().focus?.percentText)

        val tie = listOf(
            entry(PADRAO, "Sessão 5h", used = 20, risk = UsageRiskLevel.ON_TRACK),
            entry(PADRAO, "Sessão 7d", used = 60, risk = UsageRiskLevel.ON_TRACK)
        )
        assertEquals("60%", buildHudAccounts(tie, emptyList(), AppLanguage.PT, HUD_NOW).single().focus?.percentText)
    }

    @Test
    fun `sem projecao a conta diz isso e a cota nao finge estado`() {
        val entries = listOf(entry(CODEX, "Codex 5h", used = 30, risk = null))

        val account = buildHudAccounts(entries, emptyList(), AppLanguage.PT, HUD_NOW).single()

        assertEquals("Sem projeção", account.statusLabel)
        assertEquals(AppTone.NEUTRAL, account.tone)
        assertFalse(account.quotas.single().hasForecast)
        assertFalse(account.needsAttention)
    }

    @Test
    fun `os aneis param em tres e o painel continua com todas as cotas`() {
        val entries = (1..4).map { index -> entry(SANDBOX, "Janela w$index", used = index * 10, risk = null) }

        val account = buildHudAccounts(entries, emptyList(), AppLanguage.PT, HUD_NOW).single()

        assertEquals(MAX_HUD_RINGS, account.rings.size)
        assertEquals(4, account.quotas.size)
    }

    @Test
    fun `a fracao do anel e presa a uma volta`() {
        val entries = listOf(entry(CODEX, "Codex 5h", used = 140, risk = UsageRiskLevel.WILL_EXCEED))

        val quota = buildHudAccounts(entries, emptyList(), AppLanguage.PT, HUD_NOW).single().quotas.single()

        assertEquals(1f, quota.fraction)
    }

    @Test
    fun `a sessao ativa vem do conjunto de alvos ativos`() {
        val entries = listOf(
            entry(PADRAO, "Sessão 5h", used = 10, risk = null),
            entry(CODEX, "Codex 5h", used = 10, risk = null)
        )

        val accounts = buildHudAccounts(entries, emptyList(), AppLanguage.PT, HUD_NOW, activeTargets = setOf(PADRAO))

        assertEquals(listOf(true, false), accounts.map { account -> account.sessionActive })
    }

    /** A HUD mostrava só o perfil ("Padrão"); agora o título é o do card. */
    @Test
    fun `o rotulo da conta traz o fornecedor e o plano vem junto`() {
        val entries = listOf(entry(PADRAO, "Sessão 5h", used = 10, risk = null, profileLabel = "Padrão", plan = "Max 20x"))

        val account = buildHudAccounts(entries, emptyList(), AppLanguage.PT, HUD_NOW).single()

        assertEquals("ANTHROPIC — Padrão", account.label)
        assertEquals("Max 20x", account.planLabel)
        assertEquals(ApiSource.ANTHROPIC, account.source)
    }

    @Test
    fun `o resumo da bandeja traz cada conta com o percentual em foco`() {
        val entries = listOf(
            entry(PADRAO, "Sessão 5h", used = 87, risk = UsageRiskLevel.AT_RISK, profileLabel = "Padrão"),
            entry(CODEX, "Codex 5h", used = 0, risk = null)
        )
        val accounts = buildHudAccounts(entries, listOf(PADRAO, CODEX), AppLanguage.PT, HUD_NOW)

        assertEquals("Usage Monitor — ANTHROPIC — Padrão 87% · CODEX 0%", hudTraySummary("Usage Monitor", accounts))
        assertEquals("Usage Monitor", hudTraySummary("Usage Monitor", emptyList()))
    }

    /** O Windows corta o tooltip da bandeja em 127 caracteres; o corte é nosso. */
    @Test
    fun `o resumo da bandeja respeita o limite do windows`() {
        val entries = (1..20).map { index ->
            entry(UsageTargetKey(ApiSource.ANTHROPIC, "perfil$index"), "Sessão 5h", used = 50, risk = null, profileLabel = "Perfil longo $index")
        }
        val summary = hudTraySummary("Usage Monitor", buildHudAccounts(entries, emptyList(), AppLanguage.PT, HUD_NOW))

        assertEquals(TRAY_TOOLTIP_MAX_CHARS, summary.length)
        assertTrue(summary.endsWith("…"))
    }

    // ------------------------------------------------------------ balão (rodada 3)

    @Test
    fun `cada cota diz quanto foi usado e quanto resta, somando cem`() {
        val account = buildHudAccounts(listOf(entry(PADRAO, "Sessão 5h", used = 87, risk = null)), emptyList(), AppLanguage.PT, HUD_NOW).single()

        assertEquals("87% usado · 13% restante", account.quotas.single().usedLeftText)
        val english = buildHudAccounts(listOf(entry(PADRAO, "Sessão 5h", used = 87, risk = null)), emptyList(), AppLanguage.EN, HUD_NOW).single()
        assertEquals("87% used · 13% left", english.quotas.single().usedLeftText)
    }

    /** Truncar 0,4% daria "0% usado" com consumo real; o Codenotch diz "<1", e aqui também. */
    @Test
    fun `consumo abaixo de um por cento nao vira zero`() {
        val tiny = QuotaInfo("Tokens 7d", used = 4L, total = 1_000L, periodEndAt = HUD_NOW + 2.hours, unit = UsageUnit.TOKENS)
        val almostFull = QuotaInfo("Tokens 7d", used = 996L, total = 1_000L, periodEndAt = HUD_NOW + 2.hours, unit = UsageUnit.TOKENS)

        assertEquals("<1% usado · 100% restante", hudUsedLeftText(tiny, AppLanguage.PT))
        assertEquals("99% usado · <1% restante", hudUsedLeftText(almostFull, AppLanguage.PT))
    }

    /** Saldo não tem teto: "restante" ali seria uma conta sem sentido. */
    @Test
    fun `saldo e atividade observada nao tem usado e restante`() {
        val balance = QuotaInfo("Saldo", used = 0L, total = 227L, periodEndAt = HUD_NOW, unit = UsageUnit.CURRENCY_USD)
        val observed = QuotaInfo("Tokens 5h", used = 5_000L, total = 0L, periodEndAt = HUD_NOW, unit = UsageUnit.TOKENS)

        assertEquals(null, hudUsedLeftText(balance, AppLanguage.PT))
        assertEquals(null, hudUsedLeftText(observed, AppLanguage.PT))
    }

    @Test
    fun `o rodape do balao junta plano e origem`() {
        val codex = buildHudAccounts(listOf(entry(CODEX, "Codex 5h", used = 0, risk = null, plan = "Plus")), emptyList(), AppLanguage.PT, HUD_NOW).single()
        val claude = buildHudAccounts(listOf(entry(PADRAO, "Sessão 5h", used = 0, risk = null)), emptyList(), AppLanguage.PT, HUD_NOW).single()

        assertEquals("Plus · via Codex", codex.detailLine)
        assertEquals("via Claude Code", claude.detailLine)
        assertEquals("via chave de API", hudSourceOrigin(ApiSource.DEEPSEEK, AppLanguage.PT))
        assertEquals("via Antigravity CLI", hudSourceOrigin(ApiSource.ANTIGRAVITY, AppLanguage.EN))
    }

    /** No balão o grupo é o cabeçalho da caixa; repeti-lo no título de cada cota seria eco. */
    @Test
    fun `cota do antigravity leva o grupo a parte e o titulo sem ele`() {
        val target = UsageTargetKey.forSource(ApiSource.ANTIGRAVITY)
        val label = AntigravityQuotaLabels.label("Gemini", "7d")
        val quota = QuotaInfo(label, used = 5L, total = 100L, periodEndAt = HUD_NOW + 2.hours, periodType = PeriodType.WEEKLY, unit = UsageUnit.PERCENTAGE)
        val stats = ApiUsageStats(source = ApiSource.ANTIGRAVITY, targetKey = target, apiName = "Antigravity CLI", quotas = listOf(quota))

        val hudQuota = buildHudAccounts(listOf(HudQuotaEntry(stats, quota, null)), emptyList(), AppLanguage.PT, HUD_NOW).single().quotas.single()

        assertEquals("Gemini", hudQuota.group)
        assertEquals("Semanal", hudQuota.title)
        assertEquals("5% usado · 95% restante", hudQuota.usedLeftText)
    }

    private fun entry(
        target: UsageTargetKey,
        label: String,
        used: Int,
        risk: UsageRiskLevel?,
        profileLabel: String? = null,
        plan: String? = null
    ): HudQuotaEntry {
        val quota = QuotaInfo(
            label = label,
            used = used.toLong(),
            total = 100L,
            periodEndAt = HUD_NOW + 2.hours,
            periodType = PeriodType.INTERVAL,
            unit = UsageUnit.PERCENTAGE
        )
        val stats = ApiUsageStats(
            source = target.source,
            targetKey = target,
            apiName = target.source.name,
            quotas = listOf(quota),
            profileLabel = profileLabel,
            planLabel = plan
        )
        return HudQuotaEntry(stats, quota, risk?.let { level -> QuotaRiskSummary(level, HUD_NOW + 1.hours) })
    }
}
