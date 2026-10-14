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

    @Test
    fun `menu groups each category together and opens each description with its emoji`() {
        for (language in AppLanguage.entries) {
            val menu = TelegramCommandCatalog.menu(language)
            val categories = menu.map { (_, description) -> TelegramCommandCategory.entries.first { category -> description.startsWith(category.emoji) } }
            assertEquals(categories.sortedBy { category -> category.ordinal }, categories)
            assertEquals(menu.size, menu.map { (name, _) -> name }.toSet().size)
            assertTrue(menu.all { (name, _) -> name.matches(Regex("[a-z0-9_]{1,32}")) }, "o Telegram só aceita a-z, 0-9 e _")
        }
        assertEquals("status", TelegramCommandCatalog.menu(AppLanguage.PT).first().first)
        assertEquals("account", TelegramCommandCatalog.menu(AppLanguage.EN)[1].first)
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
