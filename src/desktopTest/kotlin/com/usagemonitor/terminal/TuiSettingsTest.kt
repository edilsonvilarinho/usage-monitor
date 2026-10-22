package com.usagemonitor.terminal

import com.usagemonitor.AnthropicProfileRegistry
import com.usagemonitor.data.datasource.LocalApiKeyDataSource
import com.usagemonitor.data.datasource.LocalTeamSettingsDataSource
import com.usagemonitor.data.datasource.LocalTelegramSettingsDataSource
import com.usagemonitor.data.datasource.LocalWebAccessSettingsDataSource
import com.usagemonitor.domain.entity.TelegramBotSettings
import com.usagemonitor.domain.entity.TelegramChat
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

    @Test
    fun `cada id da aba 5 vira o comando da 4_3 que a mesma acao rodaria`() {
        fun command(action: TuiSettingAction) = TuiSettingsCommands.commandOf(action)
        val expected = listOf(
            TuiSettingsCommands.set("alerts.quota.enabled", "false") to (SettingCommand.Set("alerts.quota.enabled", "false") to null),
            TuiSettingsCommands.source(ApiSource.CODEX, true) to (SourcesCommand.Switch(ApiSource.CODEX, true) to null),
            TuiSettingAction(TuiSettingsCommands.key(ApiSource.MINIMAX), "segredo", secret = true) to (KeysCommand.Set(ApiSource.MINIMAX) to "segredo"),
            TuiSettingAction(TuiSettingsCommands.keyRemove(ApiSource.MINIMAX)) to (KeysCommand.Remove(ApiSource.MINIMAX) to null),
            TuiSettingAction(TuiSettingsCommands.account("4220139c-17de"), "false") to (AccountsCommand.Switch("4220139c-17de", false) to null),
            TuiSettingAction(TuiSettingsCommands.accountLabel("4220139c-17de"), "trabalho") to (AccountsCommand.Rename("4220139c-17de", "trabalho") to null),
            TuiSettingAction(TuiSettingsCommands.ACCOUNTS_RESCAN) to (AccountsCommand.Rescan to null),
            TuiSettingAction(TuiSettingsCommands.TELEGRAM, "true") to (TelegramCommand.Switch(true) to null),
            TuiSettingAction(TuiSettingsCommands.TELEGRAM_TOKEN, "token", secret = true) to (TelegramCommand.Setup to "token"),
            TuiSettingAction(TuiSettingsCommands.TELEGRAM_PAIR) to (TelegramCommand.Pair() to null),
            TuiSettingAction(TuiSettingsCommands.TELEGRAM_TEST) to (TelegramCommand.Test to null),
            TuiSettingAction(TuiSettingsCommands.chatReadOnly(42), "true") to (TelegramCommand.ReadOnly("42", true) to null),
            TuiSettingAction(TuiSettingsCommands.chatRemove(42)) to (TelegramCommand.Remove("42") to null),
            TuiSettingAction(TuiSettingsCommands.teamKey(setOf("b", "a")), "chave", secret = true) to (TeamCommand.Join(accounts = listOf("a", "b")) to "chave"),
            TuiSettingAction(TuiSettingsCommands.TEAM_LEAVE) to (TeamCommand.Leave to null),
            TuiSettingAction(TuiSettingsCommands.WEB, "true") to (WebCommand.Switch(true) to null),
            TuiSettingAction(TuiSettingsCommands.WEB_TOKEN) to (WebCommand.RegenerateToken to null)
        )
        for ((action, result) in expected) {
            assertEquals(result, command(action), action.id)
        }
        assertNull(command(TuiSettingAction("source:NADA", "true")))
    }

    @Test
    fun `contas ligam, desligam e renomeiam pela aba`() = TerminalConfigHarness().use { harness ->
        harness.run("accounts", "add", "anthropic", harness.claudeDirectory("claude-work").path, "--label", "trabalho")
        fun record() = AnthropicProfileRegistry.readStored(harness.node).single { stored -> stored.configDirectory.endsWith("claude-work") }
        val toggle = harness.settings().field("Contas", "Anthropic · trabalho") as TuiSettingField.Toggle
        assertTrue(toggle.on)
        assertTrue(harness.apply(toggle.action)!!.ok)
        assertFalse(record().enabled)
        val notice = harness.apply(TuiSettingAction(toggle.rename!!.id, "cliente"))!!
        assertTrue(notice.ok, notice.toString())
        assertEquals("cliente", record().label)
        assertTrue(harness.apply(TuiSettingAction(TuiSettingsCommands.ACCOUNTS_RESCAN))!!.ok)
    }

    @Test
    fun `fonte e chave saem pela aba, como no keys remove`() = TerminalConfigHarness().use { harness ->
        harness.run("keys", "set", "minimax", secret = key)
        val remove = harness.settings().field("Fontes e chaves", "Remover chave do MiniMax") as TuiSettingField.Action
        assertTrue(remove.confirm)
        val notice = harness.apply(remove.action)!!
        assertTrue(notice.ok, notice.toString())
        assertNull(LocalApiKeyDataSource(harness.file("api-keys.json")).load().forSource(ApiSource.MINIMAX))
        val settings = harness.settings()
        assertFalse((settings.field("Fontes e chaves", "MiniMax") as TuiSettingField.Toggle).on)
        assertTrue(settings.sections.single { section -> section.title == "Fontes e chaves" }.fields.none { field -> field.label == "Remover chave do MiniMax" })
    }

    @Test
    fun `conversas do Telegram ficam so leitura e saem pela aba`() = TerminalConfigHarness().use { harness ->
        val telegram = LocalTelegramSettingsDataSource(harness.file("telegram.json"))
        telegram.save(TelegramBotSettings(botToken = "123:abc", authorizedChats = listOf(TelegramChat(42, "@ana"))))
        val bot = harness.settings().field("Telegram", "Bot do Telegram") as TuiSettingField.Toggle
        assertTrue(harness.apply(bot.action)!!.ok)
        assertTrue(telegram.load().enabled)
        val readOnly = harness.settings().field("Telegram", "@ana · só leitura") as TuiSettingField.Toggle
        assertFalse(readOnly.on)
        assertTrue(harness.apply(readOnly.action)!!.ok)
        assertTrue(telegram.load().authorizedChats.single().readOnly)
        val remove = harness.settings().field("Telegram", "Remover @ana") as TuiSettingField.Action
        assertTrue(remove.confirm)
        assertTrue(harness.apply(remove.action)!!.ok)
        assertEquals(emptyList(), telegram.load().authorizedChats)
    }

    @Test
    fun `acesso web liga pela aba e o endereco novo troca o token`() = TerminalConfigHarness().use { harness ->
        val web = LocalWebAccessSettingsDataSource(harness.file("web-access.json"))
        val toggle = harness.settings().field("Acesso web", "Acesso pela rede local") as TuiSettingField.Toggle
        assertTrue(harness.apply(toggle.action)!!.ok)
        val token = web.load().token
        assertTrue(web.load().enabled && token.isNotBlank())
        val address = harness.settings().field("Acesso web", "Gerar novo endereço") as TuiSettingField.Action
        assertTrue(address.confirm)
        val notice = harness.apply(address.action)!!
        assertTrue(notice.ok, notice.toString())
        assertTrue(web.load().token.let { renewed -> renewed.isNotBlank() && renewed != token })
        assertFalse(notice.toString().contains(web.load().token))
    }

    @Test
    fun `sair do time pela aba apaga a chave e guarda servidor e apelido`() = TerminalConfigHarness().use { harness ->
        val team = LocalTeamSettingsDataSource(harness.file("team.json"))
        team.save(TeamIntegrationSettings(enabled = true, serverUrl = "https://time.exemplo.com", apiKey = "chave", alias = "edi", deviceId = "d-1", participatingProfileIds = setOf("default")))
        val leave = harness.settings().field("Time", "Sair do time") as TuiSettingField.Action
        assertTrue(leave.confirm)
        assertTrue(harness.apply(leave.action)!!.ok)
        val after = team.peek()!!
        assertFalse(after.isActive)
        assertEquals("", after.apiKey)
        assertEquals("edi", after.alias)
        assertTrue(harness.settings().sections.single { section -> section.title == "Time" }.fields.none { field -> field.label == "Sair do time" })
    }
}
