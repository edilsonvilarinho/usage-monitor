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
import com.usagemonitor.presentation.ui.hudDefaultShouldSwitch
import com.usagemonitor.presentation.ui.hudFallbackLabel
import com.usagemonitor.presentation.ui.hudRingDescription
import com.usagemonitor.presentation.ui.hudRingPositionLabel
import com.usagemonitor.presentation.ui.hudSourceOrigin
import com.usagemonitor.presentation.ui.hudTraySummary
import com.usagemonitor.presentation.ui.hudUsedLeftText
import com.usagemonitor.presentation.ui.components.AppTone
import com.usagemonitor.presentation.ui.theme.AccountAccent
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

    // ------------------------------------------------------------ ordem dos anéis (#278)

    /** O anel maior é o período maior: a semanal por fora, a 5h por dentro. */
    @Test
    fun `a semanal fica por fora e a 5h por dentro`() {
        val entries = listOf(
            entry(PADRAO, "Sessão 5h", used = 28, risk = UsageRiskLevel.ON_TRACK, period = PeriodType.INTERVAL),
            entry(PADRAO, "Sessão 7d", used = 9, risk = UsageRiskLevel.ON_TRACK, period = PeriodType.WEEKLY)
        )

        val account = buildHudAccounts(entries, emptyList(), AppLanguage.PT, HUD_NOW).single()

        assertEquals(listOf("7d", "5h"), account.rings.map { ring -> ring.shortLabel })
        // O balão e o card continuam na ordem da API.
        assertEquals(listOf("5h", "7d"), account.quotas.map { quota -> quota.shortLabel })
    }

    @Test
    fun `mensal por fora de semanal e de 5h, creditos por dentro`() {
        val entries = listOf(
            entry(PADRAO, "Rolling 5h", used = 10, risk = null, period = PeriodType.INTERVAL),
            entry(PADRAO, "Créditos", used = 20, risk = null, period = PeriodType.REPORTED),
            entry(PADRAO, "Mensal 30d", used = 30, risk = null, period = PeriodType.MONTHLY)
        )

        val account = buildHudAccounts(entries, emptyList(), AppLanguage.PT, HUD_NOW).single()

        assertEquals(listOf("30d", "5h", "Créditos"), account.rings.map { ring -> ring.shortLabel })
    }

    /** Dois grupos semanais (Antigravity) mantêm a ordem do card entre si. */
    @Test
    fun `janelas iguais mantem a ordem do card`() {
        val entries = listOf(
            entry(SANDBOX, "Gemini 7d", used = 10, risk = null, period = PeriodType.WEEKLY),
            entry(SANDBOX, "Claude 7d", used = 20, risk = null, period = PeriodType.WEEKLY)
        )

        val account = buildHudAccounts(entries, emptyList(), AppLanguage.PT, HUD_NOW).single()

        assertEquals(listOf("10%", "20%"), account.rings.map { ring -> ring.percentText })
    }

    /** Com a semanal por fora, pulsar o índice 0 pulsaria a semanal com a 5h crítica. */
    @Test
    fun `o anel que pulsa e o da cota em foco`() {
        val entries = listOf(
            entry(PADRAO, "Sessão 5h", used = 90, risk = UsageRiskLevel.WILL_EXCEED, period = PeriodType.INTERVAL),
            entry(PADRAO, "Sessão 7d", used = 30, risk = UsageRiskLevel.ON_TRACK, period = PeriodType.WEEKLY)
        )

        val account = buildHudAccounts(entries, emptyList(), AppLanguage.PT, HUD_NOW).single()

        assertEquals("5h", account.rings[account.attentionRingIndex].shortLabel)
        assertEquals(1, account.attentionRingIndex)
    }

    @Test
    fun `a descricao do anel diz a posicao de cada cota`() {
        val entries = listOf(
            entry(PADRAO, "Sessão 5h", used = 28, risk = UsageRiskLevel.WILL_EXCEED, period = PeriodType.INTERVAL, profileLabel = "Padrão"),
            entry(PADRAO, "Sessão 7d", used = 9, risk = UsageRiskLevel.ON_TRACK, period = PeriodType.WEEKLY, profileLabel = "Padrão")
        )
        val pt = buildHudAccounts(entries, emptyList(), AppLanguage.PT, HUD_NOW).single()
        val en = buildHudAccounts(entries, emptyList(), AppLanguage.EN, HUD_NOW).single()

        assertEquals(
            "ANTHROPIC — Padrão · Crítico · anel externo 7d 9% · anel interno 5h 28%",
            hudRingDescription(pt, AppLanguage.PT)
        )
        assertEquals(
            "ANTHROPIC — Padrão · Critical · outer ring 7d 9% · inner ring 5h 28%",
            hudRingDescription(en, AppLanguage.EN)
        )
    }

    @Test
    fun `um anel so nao tem posicao e o do meio so existe com tres`() {
        assertEquals(null, hudRingPositionLabel(0, 1, AppLanguage.PT))
        assertEquals("anel do meio", hudRingPositionLabel(1, 3, AppLanguage.PT))
        assertEquals("middle ring", hudRingPositionLabel(1, 3, AppLanguage.EN))
        assertEquals("anel interno", hudRingPositionLabel(2, 3, AppLanguage.PT))
    }

    /** A cor da conta (issue #275) chega à HUD por `profileId`; sem escolha, nenhuma. */
    @Test
    fun `a cor escolhida para a conta chega a hud e so a dela`() {
        val entries = listOf(
            entry(PADRAO, "Sessão 5h", used = 10, risk = null),
            entry(SANDBOX, "Sessão 5h", used = 10, risk = null),
            entry(CODEX, "Codex 5h", used = 10, risk = null)
        )

        val accounts = buildHudAccounts(
            entries,
            listOf(PADRAO, SANDBOX, CODEX),
            AppLanguage.PT,
            HUD_NOW,
            accountColors = mapOf("sandbox" to AccountAccent.VIOLET)
        )

        assertEquals(listOf(null, AccountAccent.VIOLET, null), accounts.map { account -> account.accountAccent })
    }

    /** Os sinais de sessão (#265) chegam por alvo e entram na descrição do anel. */
    @Test
    fun `os sinais de sessao da conta chegam a hud e a descricao`() {
        val now = HUD_NOW
        val entries = listOf(
            entry(SANDBOX, "Sessão 5h", used = 10, risk = null, profileLabel = "Sandbox"),
            entry(CODEX, "Codex 5h", used = 10, risk = null)
        )
        val pulses = mapOf(
            SANDBOX to com.usagemonitor.domain.entity.SessionPulse(
                listOf(com.usagemonitor.domain.entity.ActiveSessionAlert("s", com.usagemonitor.domain.entity.CliSessionHealth.SATURATED, now))
            )
        )

        val accounts = buildHudAccounts(entries, listOf(SANDBOX, CODEX), AppLanguage.PT, now, sessionPulses = pulses)

        assertEquals(listOf("Contexto saturado · 1 sessão"), accounts.first().sessionSignals.map { signal -> signal.text })
        assertTrue(accounts.last().sessionSignals.isEmpty())
        assertTrue(hudRingDescription(accounts.first(), AppLanguage.PT).endsWith("· Contexto saturado · 1 sessão"))
    }

    /** Instalação nova (#277): troca só com conta para mostrar e sem modal aberto. */
    @Test
    fun `a troca para a hud espera conta e nenhuma janela modal`() {
        assertTrue(hudDefaultShouldSwitch(pending = true, hasHudAccounts = true, modalOpen = false))
        assertFalse(hudDefaultShouldSwitch(pending = true, hasHudAccounts = false, modalOpen = false))
        assertFalse(hudDefaultShouldSwitch(pending = true, hasHudAccounts = true, modalOpen = true))
        assertFalse(hudDefaultShouldSwitch(pending = false, hasHudAccounts = true, modalOpen = false))
    }

    /** Sem API habilitada o notch não pode prometer "Carregando" para sempre. */
    @Test
    fun `sem api habilitada o notch diz isso em vez de carregando`() {
        assertEquals("Nenhuma API", hudFallbackLabel(noApisEnabled = true, language = AppLanguage.PT))
        assertEquals("No APIs", hudFallbackLabel(noApisEnabled = true, language = AppLanguage.EN))
        assertEquals("Carregando", hudFallbackLabel(noApisEnabled = false, language = AppLanguage.PT))
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

    /** Com duas janelas a bandeja diz de qual é o número (#286). */
    @Test
    fun `o resumo da bandeja diz a janela da cota em foco`() {
        val entries = listOf(
            entry(PADRAO, "Sessão 5h", used = 45, risk = UsageRiskLevel.AT_RISK, profileLabel = "Padrão"),
            entry(PADRAO, "Sessão 7d", used = 72, risk = UsageRiskLevel.WILL_EXCEED, period = PeriodType.WEEKLY)
        )
        val accounts = buildHudAccounts(entries, emptyList(), AppLanguage.PT, HUD_NOW)

        assertEquals("Usage Monitor — ANTHROPIC — Padrão 7d 72%", hudTraySummary("Usage Monitor", accounts))
    }

    // ------------------------------------------------------------ linhas do notch (#286)

    /** Uma linha por anel, na ordem dos anéis, e não o número da cota em foco. */
    @Test
    fun `o notch mostra cada janela com o rotulo, na ordem dos aneis`() {
        val entries = listOf(
            entry(PADRAO, "Sessão 5h", used = 45, risk = UsageRiskLevel.AT_RISK, period = PeriodType.INTERVAL),
            entry(PADRAO, "Sessão 7d", used = 72, risk = UsageRiskLevel.WILL_EXCEED, period = PeriodType.WEEKLY)
        )

        val account = buildHudAccounts(entries, emptyList(), AppLanguage.PT, HUD_NOW).single()

        assertEquals(listOf("7d 72%", "5h 45%"), account.stripLines.map { line -> line.text })
        assertEquals("7d 72%", account.focusLine.text)
    }

    /** O foco pode mudar de janela; as linhas não mudam de lugar. */
    @Test
    fun `as linhas nao trocam de lugar quando o foco muda de janela`() {
        val calm = listOf(
            entry(PADRAO, "Sessão 5h", used = 80, risk = UsageRiskLevel.AT_RISK, period = PeriodType.INTERVAL),
            entry(PADRAO, "Sessão 7d", used = 30, risk = UsageRiskLevel.ON_TRACK, period = PeriodType.WEEKLY)
        )
        val hot = listOf(
            entry(PADRAO, "Sessão 5h", used = 10, risk = UsageRiskLevel.ON_TRACK, period = PeriodType.INTERVAL),
            entry(PADRAO, "Sessão 7d", used = 90, risk = UsageRiskLevel.WILL_EXCEED, period = PeriodType.WEEKLY)
        )

        val before = buildHudAccounts(calm, emptyList(), AppLanguage.PT, HUD_NOW).single()
        val after = buildHudAccounts(hot, emptyList(), AppLanguage.PT, HUD_NOW).single()

        assertEquals("5h", before.focusLine.label)
        assertEquals("7d", after.focusLine.label)
        assertEquals(listOf("7d", "5h"), before.stripLines.map { line -> line.label })
        assertEquals(listOf("7d", "5h"), after.stripLines.map { line -> line.label })
    }

    /** Sem janela a distinguir, o notch continua como antes: só o número. */
    @Test
    fun `conta de cota unica fica sem rotulo`() {
        val account = buildHudAccounts(listOf(entry(CODEX, "Codex 5h", used = 30, risk = null)), emptyList(), AppLanguage.PT, HUD_NOW).single()

        assertEquals(listOf("30%"), account.stripLines.map { line -> line.text })
        assertEquals(null, account.focusLine.label)
    }

    @Test
    fun `tres janelas dao tres linhas e a quarta cota fica so no balao`() {
        val entries = listOf(
            entry(SANDBOX, "Rolling 5h", used = 10, risk = null, period = PeriodType.INTERVAL),
            entry(SANDBOX, "Semanal 7d", used = 20, risk = null, period = PeriodType.WEEKLY),
            entry(SANDBOX, "Mensal 30d", used = 30, risk = null, period = PeriodType.MONTHLY),
            entry(SANDBOX, "Extra 1d", used = 40, risk = null, period = PeriodType.INTERVAL)
        )

        val account = buildHudAccounts(entries, emptyList(), AppLanguage.PT, HUD_NOW).single()

        assertEquals(listOf("30d 30%", "7d 20%", "5h 10%"), account.stripLines.map { line -> line.text })
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
        plan: String? = null,
        period: PeriodType = PeriodType.INTERVAL
    ): HudQuotaEntry {
        val quota = QuotaInfo(
            label = label,
            used = used.toLong(),
            total = 100L,
            periodEndAt = HUD_NOW + 2.hours,
            periodType = period,
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
