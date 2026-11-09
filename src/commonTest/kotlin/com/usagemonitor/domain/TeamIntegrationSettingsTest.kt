package com.usagemonitor.domain

import com.usagemonitor.domain.entity.TeamIntegrationSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TeamIntegrationSettingsTest {

    /** Time completo e configurado, exceto pela chave, que cada teste escolhe. */
    private fun team(
        apiKey: String = "chave-time",
        participatingProfileIds: Set<String> = emptySet(),
        accountApiKeys: Map<String, String> = emptyMap()
    ) = TeamIntegrationSettings(
        enabled = true,
        serverUrl = "https://time.example",
        apiKey = apiKey,
        alias = "ana",
        deviceId = "dev-1",
        id = "a",
        name = "Time a",
        participatingProfileIds = participatingProfileIds,
        accountApiKeys = accountApiKeys
    )

    @Test
    fun `conta com chave propria usa a dela`() {
        val settings = team(accountApiKeys = mapOf("conta-1" to "chave-conta-1"))

        assertEquals("chave-conta-1", settings.apiKeyFor("conta-1"))
    }

    @Test
    fun `conta sem chave ou com chave em branco usa a do time`() {
        val settings = team(accountApiKeys = mapOf("conta-2" to "   "))

        assertEquals("chave-time", settings.apiKeyFor("conta-2"))
        assertEquals("chave-time", settings.apiKeyFor("conta-3"))
    }

    @Test
    fun `profileId nulo usa a chave do time`() {
        val settings = team(accountApiKeys = mapOf("conta-1" to "chave-conta-1"))

        assertEquals("chave-time", settings.apiKeyFor(null))
    }

    @Test
    fun `chave da conta sai sem espacos nas bordas`() {
        val settings = team(accountApiKeys = mapOf("conta-1" to "  chave-conta-1  "))

        assertEquals("chave-conta-1", settings.apiKeyFor("conta-1"))
    }

    @Test
    fun `time sem chave e todas as participantes com chave propria esta configurado`() {
        val settings = team(
            apiKey = "",
            participatingProfileIds = setOf("conta-1", "conta-2"),
            accountApiKeys = mapOf("conta-1" to "k1", "conta-2" to "k2")
        )

        assertTrue(settings.isConfigured)
    }

    @Test
    fun `time sem chave e uma participante sem chave propria nao esta configurado`() {
        val settings = team(
            apiKey = "",
            participatingProfileIds = setOf("conta-1", "conta-2"),
            accountApiKeys = mapOf("conta-1" to "k1", "conta-2" to "  ")
        )

        assertFalse(settings.isConfigured)
    }

    @Test
    fun `time sem chave e sem participantes nao esta configurado`() {
        val settings = team(apiKey = "", accountApiKeys = mapOf("conta-1" to "k1"))

        assertFalse(settings.isConfigured)
    }

    @Test
    fun `toString nao expoe a chave da conta`() {
        val printed = team(accountApiKeys = mapOf("conta-1" to "segredo-conta-789")).toString()

        assertFalse(printed.contains("segredo-conta-789"), printed)
    }
}
