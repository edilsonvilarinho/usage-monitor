package com.usagemonitor.presentation.terminal

import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.CollectorKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class TuiSettingsViewTest {

    private val now = Instant.parse("2026-10-21T17:23:00Z")
    private val pt = TuiLabels(AppLanguage.PT)
    private val color = TerminalProfile(TerminalPalette.TRUECOLOR_DARK, unicode = true)
    private val ascii = TerminalProfile(TerminalPalette.NONE, unicode = false)

    private val alerts = TuiSettingsSection(
        "Alertas",
        listOf(
            TuiSettingField.Toggle("Avisar quando a quota cruzar um limiar", true, TuiSettingAction("set:alerts.quota.enabled", "false")),
            TuiSettingField.Text("Limiares (%)", "70,90", TuiSettingEdit("set:alerts.quota.thresholds", "70,90")),
            TuiSettingField.Stepper("Lembrar antes de a cota reiniciar", "15 min antes", TuiSettingAction("set:alerts.reminder.minutes", "10"), TuiSettingAction("set:alerts.reminder.minutes", "30")),
            TuiSettingField.Info("Somente leitura", "valor"),
            TuiSettingField.Text("Senha (opcional)", null, TuiSettingEdit("set:proxy.password", secret = true))
        )
    )
    private val network = TuiSettingsSection(
        "Rede e proxy",
        listOf(TuiSettingField.Action("Gerar novo endereço", TuiSettingAction("web-token"), confirm = true)),
        restartNeeded = true,
        hint = "o endereço com o token: usage-monitor web token"
    )
    private val settings = TuiSettings(listOf(alerts, network))
    private val sections = settings.sections

    private fun screen(state: TuiSettingsState = TuiSettingsState(), size: TuiSize = TuiSize(96, 20), profile: TerminalProfile = color, collector: CollectorKind? = CollectorKind.SERVICE) =
        TuiScreen.render(TuiState(tab = TuiTab.SETTINGS, settings = state), TuiData(now, collector, now, settings = settings), size, pt, profile).map { line -> line.plainText }

    private fun reduce(state: TuiSettingsState, vararg keys: TuiKey): Pair<TuiSettingsState, List<TuiEffect>> {
        var current = state
        val effects = mutableListOf<TuiEffect>()
        for (key in keys) {
            val step = TuiReducer.reduce(TuiState(tab = TuiTab.SETTINGS, settings = current), key, TuiContext(false, false, settingsSections = sections))
            current = step.state.settings
            effects += step.effects
        }
        return current to effects
    }

    @Test
    fun `secoes a esquerda, campos a direita e quando a mudanca vale`() {
        val lines = screen(TuiSettingsState(inFields = true))
        assertTrue(lines[3].startsWith("│ SEÇÕES ") && lines[3].contains("│ Alertas ") && lines[3].endsWith("o serviço já está usando │"), lines[3])
        assertTrue(lines[5].contains("▸ Alertas") && lines[5].contains("│ [x] Avisar quando a quota cruzar um limiar"), lines[5])
        assertTrue(lines[6].contains("│     Limiares (%)  [ 70,90 ]"), lines[6])
        assertTrue(lines[7].contains("│     Lembrar antes de a cota reiniciar  ‹ 15 min antes ›"), lines[7])
        assertTrue(lines[9].contains("Senha (opcional)  [ vazio ]"), lines[9])
        assertTrue(lines[lines.size - 2].startsWith("│ ↑↓ campo  espaço liga/desliga  tab seção  esc volta  1–5 aba"), lines[lines.size - 2])
    }

    @Test
    fun `proxy avisa que so vale depois de reiniciar, e a dica diz o comando`() {
        val lines = screen(TuiSettingsState(section = 1, inFields = true), collector = CollectorKind.SERVICE)
        assertTrue(lines[3].endsWith("vale depois de usage-monitor service restart │"), lines[3])
        assertTrue(lines.any { line -> "o endereço com o token: usage-monitor web token" in line })
    }

    @Test
    fun `espaco liga e desliga, e as setas mudam o valor pelo comando`() {
        val (_, toggled) = reduce(TuiSettingsState(), TuiKey.Enter, TuiKey.Space)
        assertEquals(listOf<TuiEffect>(TuiEffect.ApplySetting(TuiSettingAction("set:alerts.quota.enabled", "false"))), toggled)
        val (_, stepped) = reduce(TuiSettingsState(), TuiKey.Enter, TuiKey.Down, TuiKey.Down, TuiKey.Right)
        assertEquals(listOf<TuiEffect>(TuiEffect.ApplySetting(TuiSettingAction("set:alerts.reminder.minutes", "30"))), stepped)
    }

    @Test
    fun `campo aberto recebe q, digitos e interrogacao como texto`() {
        val (open, _) = reduce(TuiSettingsState(), TuiKey.Enter, TuiKey.Down, TuiKey.Enter)
        assertEquals("70,90", open.editing?.text)
        val (typed, effects) = reduce(open, TuiKey.Backspace, TuiKey.Backspace, TuiKey.Text('9'), TuiKey.Text('5'), TuiKey.Text('q'), TuiKey.Text('?'), TuiKey.Text('3'))
        assertEquals("70,95q?3", typed.editing?.text)
        assertEquals(emptyList(), effects)
        val (_, saved) = reduce(typed, TuiKey.Enter)
        assertEquals(listOf<TuiEffect>(TuiEffect.ApplySetting(TuiSettingAction("set:alerts.quota.thresholds", "70,95q?3"))), saved)
        val (gaveUp, nothing) = reduce(typed, TuiKey.Escape)
        assertNull(gaveUp.editing)
        assertEquals(emptyList(), nothing)
    }

    @Test
    fun `o segredo nao ecoa nem o tamanho, e nao sai no toString`() {
        // A informação só de leitura é pulada: do Lembrar o ↓ vai direto à senha.
        val (open, _) = reduce(TuiSettingsState(), TuiKey.Enter, TuiKey.Down, TuiKey.Down, TuiKey.Down, TuiKey.Enter, TuiKey.Text('s'), TuiKey.Text('3'), TuiKey.Text('g'))
        assertEquals("sec".length, open.editing?.text?.length)
        val lines = screen(open)
        assertTrue(lines.any { line -> "Senha (opcional)  [ ▏ ]   não aparece na tela" in line }, lines.joinToString("\n"))
        assertTrue(lines.none { line -> "s3g" in line })
        assertFalse(open.toString().contains("s3g"))
        val (_, effects) = reduce(open, TuiKey.Enter)
        val action = (effects.single() as TuiEffect.ApplySetting).action
        assertEquals("s3g", action.value)
        assertFalse(action.toString().contains("s3g"))
        assertFalse(effects.toString().contains("s3g"))
    }

    @Test
    fun `acao que apaga pede o segundo enter, e outra tecla desiste`() {
        val (asked, none) = reduce(TuiSettingsState(), TuiKey.Down, TuiKey.Enter, TuiKey.Enter)
        assertEquals(TuiSettingAction("web-token"), asked.confirming)
        assertEquals(emptyList(), none)
        assertTrue(screen(asked).any { line -> "Gerar novo endereço   enter de novo confirma" in line })
        assertEquals(listOf<TuiEffect>(TuiEffect.ApplySetting(TuiSettingAction("web-token"))), reduce(asked, TuiKey.Enter).second)
        val (gaveUp, nothing) = reduce(asked, TuiKey.Down)
        assertNull(gaveUp.confirming)
        assertEquals(emptyList(), nothing)
    }

    @Test
    fun `durante a edicao os atalhos comuns somem`() {
        val (open, _) = reduce(TuiSettingsState(), TuiKey.Enter, TuiKey.Down, TuiKey.Enter)
        val footer = screen(open).let { lines -> lines[lines.size - 2] }
        assertTrue(footer.startsWith("│ enter grava  esc desiste "), footer)
        assertFalse("q sair" in footer)
    }

    @Test
    fun `empilhado mostra a lista ou os campos, e sem Unicode os glifos do ASCII`() {
        assertTrue(screen(size = TuiSize(70, 20)).any { line -> line.startsWith("│ ▸ Alertas") })
        assertTrue(screen(TuiSettingsState(inFields = true), size = TuiSize(70, 20)).any { line -> line.startsWith("│ [x] Avisar quando") })
        val plain = screen(TuiSettingsState(inFields = true), profile = ascii)
        assertTrue(plain.any { line -> "< 15 min antes >" in line }, plain.joinToString("\n"))
    }

    @Test
    fun `largura e altura exatas em 80 e 96 colunas`() {
        for (size in listOf(TuiSize(96, 20), TuiSize(80, 20), TuiSize(60, 14))) {
            for (state in listOf(TuiSettingsState(), TuiSettingsState(inFields = true), TuiSettingsState(section = 1, inFields = true, confirming = TuiSettingAction("web-token")))) {
                val lines = screen(state, size)
                assertEquals(size.rows, lines.size)
                assertTrue(lines.all { line -> line.length == size.columns }, "$size $state")
            }
        }
    }

    @Test
    fun `aviso longo quebra por palavra em vez de sumir na borda`() {
        val text = "'alerts.quota.thresholds' não aceita esse valor. Use percentuais de 1 a 100 separados por vírgula, como 70,90."
        val lines = TuiScreen.render(
            TuiState(tab = TuiTab.SETTINGS, notice = TuiNotice(text, ok = false)), TuiData(now, null, now, settings = settings), TuiSize(80, 20), pt, color
        ).map { line -> line.plainText }
        val shown = lines.filter { line -> "▲" in line || line.startsWith("│   ") && "vírgula" in line }
        assertTrue(lines[lines.size - 5].startsWith("│ ▲ 'alerts.quota.thresholds' não aceita esse valor."), lines.joinToString("\n"))
        assertTrue(lines[lines.size - 4].startsWith("│   ") && "como 70,90." in lines[lines.size - 4], lines[lines.size - 4])
        assertEquals(2, shown.size)
    }
}
