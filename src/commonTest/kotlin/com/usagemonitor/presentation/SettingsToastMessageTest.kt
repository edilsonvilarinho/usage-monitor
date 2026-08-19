package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.ui.components.SettingsField
import com.usagemonitor.presentation.ui.components.SettingsToast
import com.usagemonitor.presentation.ui.components.settingsToastMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettingsToastMessageTest {

    @Test
    fun `concorda o participio com o genero do campo em portugues`() {
        // Montar "$campo salvo" por concatenação erraria metade dos casos.
        assertEquals(
            "Tema salvo",
            settingsToastMessage(SettingsToast.Saved(SettingsField.THEME), AppLanguage.PT)
        )
        assertEquals(
            "Opacidade salva",
            settingsToastMessage(SettingsToast.Saved(SettingsField.WINDOW_OPACITY), AppLanguage.PT)
        )
        assertEquals(
            "APIs monitoradas salvas",
            settingsToastMessage(SettingsToast.Saved(SettingsField.MONITORED_APIS), AppLanguage.PT)
        )
        assertEquals(
            "Apelido salvo",
            settingsToastMessage(SettingsToast.Saved(SettingsField.TEAM_ALIAS), AppLanguage.PT)
        )
        assertEquals(
            "Tamanho da interface salvo",
            settingsToastMessage(SettingsToast.Saved(SettingsField.UI_SCALE), AppLanguage.PT)
        )
    }

    @Test
    fun `escala da interface tem texto em ingles`() {
        assertEquals(
            "Interface size saved",
            settingsToastMessage(SettingsToast.Saved(SettingsField.UI_SCALE), AppLanguage.EN)
        )
    }

    @Test
    fun `falha de gravacao nao diz que salvou`() {
        val message = settingsToastMessage(
            SettingsToast.SaveFailed(SettingsField.TEAM_ALIAS),
            AppLanguage.PT
        )

        assertEquals("Falha ao salvar: apelido", message)
    }

    @Test
    fun `apelido em branco tem mensagem propria`() {
        assertEquals(
            "O apelido não pode ficar vazio.",
            settingsToastMessage(SettingsToast.TeamAliasRequired, AppLanguage.PT)
        )
        assertEquals(
            "The alias cannot be empty.",
            settingsToastMessage(SettingsToast.TeamAliasRequired, AppLanguage.EN)
        )
    }

    @Test
    fun `todo campo tem texto nos dois idiomas`() {
        for (field in SettingsField.entries) {
            for (language in AppLanguage.entries) {
                val saved = settingsToastMessage(SettingsToast.Saved(field), language)
                val failed = settingsToastMessage(SettingsToast.SaveFailed(field), language)
                assertTrue(saved.isNotBlank(), "Saved sem texto: $field/$language")
                assertTrue(failed.isNotBlank(), "SaveFailed sem texto: $field/$language")
            }
        }
    }
}
