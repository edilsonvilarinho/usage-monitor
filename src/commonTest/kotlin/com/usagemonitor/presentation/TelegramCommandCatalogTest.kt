package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.BotCommand
import com.usagemonitor.domain.entity.parseBotCommand
import com.usagemonitor.presentation.ui.TelegramCommandCatalog
import com.usagemonitor.presentation.ui.TelegramCommandCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** O menu do bot por categoria: um dono só para o `setMyCommands` e o `/ajuda`. */
class TelegramCommandCatalogTest {

    @Test
    fun `every catalog name is a command the bot understands in both languages`() {
        for (entry in TelegramCommandCatalog.entries) {
            for (name in listOf(entry.namePt, entry.nameEn)) {
                val parsed = parseBotCommand("/$name")
                assertFalse(parsed == null || parsed == BotCommand.Invalid("/ajuda"), name)
            }
        }
    }

    /** Rodada AE, AE4: o botão Menu leva só três comandos, cada um com ícone próprio. */
    @Test
    fun `menu keeps only status, keyboard and help, each with its own icon`() {
        assertEquals(listOf("status", "teclado", "ajuda"), TelegramCommandCatalog.menu(AppLanguage.PT).map { (name, _) -> name })
        assertEquals(listOf("status", "keyboard", "help"), TelegramCommandCatalog.menu(AppLanguage.EN).map { (name, _) -> name })
        for (language in AppLanguage.entries) {
            val menu = TelegramCommandCatalog.menu(language)
            val icons = menu.map { (_, description) -> description.substringBefore(' ') }
            assertEquals(icons.size, icons.toSet().size, menu.toString())
            assertTrue(menu.all { (name, _) -> name.matches(Regex("[a-z0-9_]{1,32}")) }, "o Telegram só aceita a-z, 0-9 e _")
        }
        assertEquals("📊 Cotas de todas as contas", TelegramCommandCatalog.menu(AppLanguage.PT).first().second)
    }

    @Test
    fun `help has one heading per category in the language asked`() {
        val pt = TelegramCommandCatalog.help(AppLanguage.PT)
        val en = TelegramCommandCatalog.help(AppLanguage.EN)
        for (category in TelegramCommandCategory.entries) {
            assertTrue("<b>${category.emoji} ${category.title(AppLanguage.PT)}</b>" in pt)
            assertTrue("<b>${category.emoji} ${category.title(AppLanguage.EN)}</b>" in en)
        }
        assertTrue("<code>/analise" !in en && "<code>/reinicios</code>" !in en)
        assertTrue(pt.indexOf("📐 Análises") < pt.indexOf("<code>/vazao</code>"))
    }
}
