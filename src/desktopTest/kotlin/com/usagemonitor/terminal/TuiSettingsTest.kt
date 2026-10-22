package com.usagemonitor.terminal

import com.usagemonitor.AnthropicProfileRegistry
import com.usagemonitor.data.datasource.LocalTeamSettingsDataSource
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.TeamIntegrationSettings
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.CollectorKind
import com.usagemonitor.presentation.terminal.TerminalGlyphs
import com.usagemonitor.presentation.terminal.TerminalPalette
import com.usagemonitor.presentation.terminal.TerminalProfile
import com.usagemonitor.presentation.terminal.TuiSettingAction
import com.usagemonitor.presentation.terminal.TuiSettingField
import com.usagemonitor.presentation.terminal.TuiSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A aba 5 do modo interativo (#441, A08) de ponta a ponta: lê pelas leituras dos comandos e
 * muda pelos comandos da 4.3, com o coletor vivo (o harness atende os pedidos) ou sem ele.
 */
class TuiSettingsTest {

    private val profile = TerminalProfile(TerminalPalette.NONE, unicode = true)
    private val key = "sk-minimax-0123456789abcdef"

    private fun TerminalConfigHarness.settings(): TuiSettings = TuiSettingsReader(settingsInputs()).load(AppLanguage.PT, TerminalGlyphs.UNICODE)

    private fun TerminalConfigHarness.apply(action: TuiSettingAction) =
        TuiSettingsCommands.apply(action, { secret -> runner(secret) }, AppLanguage.PT, profile)

    private fun TuiSettings.field(section: String, label: String): TuiSettingField =
        sections.single { item -> item.title == section }.fields.single { field -> field.label == label }

    @Test
    fun `as secoes sao as das Configuracoes, com os rotulos do app`() = TerminalConfigHarness().use { harness ->
        val settings = harness.settings()
        assertEquals(
            listOf("Fontes e chaves", "Contas", "Alertas", "Telegram", "Time", "Rede e proxy", "Acesso web", "Sistema"),
            settings.sections.map { section -> section.title }
        )
        assertTrue(settings.field("Alertas", "Avisar quando a quota cruzar um limiar") is TuiSettingField.Toggle)
        assertTrue(settings.sections.single { section -> section.title == "Rede e proxy" }.restartNeeded)
        // Sem token do bot, o interruptor não existe: só o estado e o que falta.
        assertTrue(settings.field("Telegram", "Bot do Telegram") is TuiSettingField.Info)
    }

    @Test
    fun `espaco num interruptor roda config set, e o servico ja usa`() = TerminalConfigHarness(collector = CollectorKind.SERVICE).use { harness ->
        val toggle = harness.settings().field("Alertas", "Avisar quando a cota reiniciar") as TuiSettingField.Toggle
        val notice = harness.apply(toggle.action)!!
        assertTrue(notice.ok)
        assertEquals("alerts.reset.enabled = ${!toggle.on} · o serviço já está usando", notice.text)
        assertEquals(!toggle.on, (harness.settings().field("Alertas", "Avisar quando a cota reiniciar") as TuiSettingField.Toggle).on)
    }

    @Test
    fun `as setas de um valor vao ao vizinho, e o valor fora da lista fica entre eles`() = TerminalConfigHarness().use { harness ->
        val reminder = harness.settings().field("Alertas", "Lembrar antes de a cota reiniciar") as TuiSettingField.Stepper
        assertEquals("15 min antes", reminder.shown)
        assertTrue(harness.apply(reminder.next!!)!!.ok)
        assertEquals("30 min antes", (harness.settings().field("Alertas", "Lembrar antes de a cota reiniciar") as TuiSettingField.Stepper).shown)
        harness.run("config", "set", "alerts.reminder.minutes", "off")
        val off = harness.settings().field("Alertas", "Lembrar antes de a cota reiniciar") as TuiSettingField.Stepper
        assertEquals("desligado", off.shown)
        assertNull(off.previous)
        // O `/avise 20` do bot grava 20, que a lista não tem.
        harness.run("config", "set", "alerts.reminder.minutes", "20")
        val odd = harness.settings().field("Alertas", "Lembrar antes de a cota reiniciar") as TuiSettingField.Stepper
        assertEquals(TuiSettingsCommands.set("alerts.reminder.minutes", "15"), odd.previous)
        assertEquals(TuiSettingsCommands.set("alerts.reminder.minutes", "30"), odd.next)
    }

    @Test
    fun `a chave digitada passa pelo teste do keys set e nunca aparece`() = TerminalConfigHarness().use { harness ->
        val field = harness.settings().field("Fontes e chaves", "Chave do MiniMax") as TuiSettingField.Text
        assertNull(field.shown)
        val action = TuiSettingAction(field.edit.id, key, secret = true)
        val notice = harness.apply(action)!!
        assertTrue(notice.ok, notice.toString())
        assertEquals("chave do MiniMax salva e a fonte ligada · vale quando o app ou o serviço abrir", notice.text)
        assertFalse(notice.toString().contains(key))
        assertFalse(action.toString().contains(key))
        val settings = harness.settings()
        assertEquals("••••", (settings.field("Fontes e chaves", "Chave do MiniMax") as TuiSettingField.Text).shown)
        assertTrue((settings.field("Fontes e chaves", "MiniMax") as TuiSettingField.Toggle).on)
        assertTrue(settings.field("Fontes e chaves", "Remover chave do MiniMax").let { remove -> remove is TuiSettingField.Action && remove.confirm })
    }

    @Test
    fun `chave vazia nao muda nada, como no keys set`() = TerminalConfigHarness().use { harness ->
        val notice = harness.apply(TuiSettingAction(TuiSettingsCommands.key(ApiSource.MINIMAX), "", secret = true))!!
        assertFalse(notice.ok)
        assertEquals("Nada foi digitado, e nada mudou.", notice.text)
    }

    @Test
    fun `parear sem coletor diz o que rodar, sem esperar`() = TerminalConfigHarness().use { harness ->
        val notice = harness.apply(TuiSettingAction(TuiSettingsCommands.TELEGRAM_PAIR))!!
        assertFalse(notice.ok)
        assertEquals(listOf("usage-monitor service run"), notice.detail)
    }

    @Test
    fun `sem servidor e apelido gravados, a chave do time fica para o team join`() = TerminalConfigHarness().use { harness ->
        val team = harness.settings().sections.single { section -> section.title == "Time" }
        assertTrue(team.fields.none { field -> field.label == "Chave do time" })
        assertTrue(team.hint!!.contains("usage-monitor team join"))
    }

    @Test
    fun `id desconhecido nao vira comando`() {
        assertNull(TuiSettingsCommands.commandOf(TuiSettingAction("nada")))
        assertEquals(SettingCommand.Set("alerts.quota.thresholds", "75,95"), TuiSettingsCommands.commandOf(TuiSettingsCommands.set("alerts.quota.thresholds", "75,95"))?.first)
        assertEquals(TeamCommand.Join() to "chave", TuiSettingsCommands.commandOf(TuiSettingAction(TuiSettingsCommands.teamKey(emptySet()), "chave", secret = true)))
    }

    /** Um diretório de configuração do Claude Code já logado, como o do `accounts add`. */
    private fun TerminalConfigHarness.claudeDirectory(name: String): java.io.File =
        file(name).apply {
            mkdirs()
            java.io.File(this, ".credentials.json").writeText("""{"claudeAiOauth":{"accessToken":"a"}}""")
            java.io.File(this, ".claude.json").writeText("""{"oauthAccount":{"accountUuid":"u-$name","emailAddress":"$name@example.com"}}""")
        }

    @Test
    fun `redigitar a chave do time mantem as contas que ja participavam`() = TerminalConfigHarness().use { harness ->
        harness.run("accounts", "add", "anthropic", harness.claudeDirectory("claude-work").path, "--label", "trabalho")
        harness.run("accounts", "add", "anthropic", harness.claudeDirectory("claude-home").path, "--label", "pessoal")
        val work = AnthropicProfileRegistry.readStored(harness.node).single { record -> record.label == "trabalho" }.id
        val team = LocalTeamSettingsDataSource(harness.file("team.json"))
        team.save(TeamIntegrationSettings(enabled = true, serverUrl = "https://time.exemplo.com", apiKey = "antiga", alias = "edi", deviceId = "d-1", participatingProfileIds = setOf(work)))

        val field = harness.settings().field("Time", "Chave do time") as TuiSettingField.Text
        val notice = harness.apply(TuiSettingAction(field.edit.id, "chave-nova", secret = true))!!

        assertTrue(notice.ok, notice.toString())
        // Antes da A10 o `team join` sem contas punha as duas monitoradas no time.
        assertEquals(setOf(work), team.peek()!!.participatingProfileIds)
        assertEquals("chave-nova", team.peek()!!.apiKey)
    }
}
