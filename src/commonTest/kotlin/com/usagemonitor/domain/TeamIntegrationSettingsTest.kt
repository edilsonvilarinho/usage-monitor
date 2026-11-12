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
    fun `modo legado sem mapa, conta sem chave ou com chave em branco usa a do time`() {
        val settings = team(accountApiKeys = emptyMap())

        assertEquals("chave-time", settings.apiKeyFor("conta-2"))
        assertEquals("chave-time", settings.apiKeyFor("conta-3"))
    }

    @Test
    fun `com mapa de chaves, conta sem chave propria nao herda a do time`() {
        val settings = team(accountApiKeys = mapOf("conta-1" to "chave-conta-1"))

        assertEquals("", settings.apiKeyFor("conta-2"), "conta sem entrada no mapa deveria ficar sem chave")
    }

    @Test
    fun `com mapa de chaves, conta com chave em branco nao herda a do time`() {
        val settings = team(accountApiKeys = mapOf("conta-1" to "chave-conta-1", "conta-2" to "   "))

        assertEquals("", settings.apiKeyFor("conta-2"), "chave em branco no mapa deveria virar vazio")
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
    fun `time sem chave com ao menos uma participante com chave propria esta configurado`() {
        val settings = team(
            apiKey = "",
            participatingProfileIds = setOf("conta-1", "conta-2"),
            accountApiKeys = mapOf("conta-1" to "k1", "conta-2" to "  ")
        )

        assertTrue(settings.isConfigured, "uma conta com chave basta; a sem chave fica de fora do envio")
    }

    @Test
    fun `time sem chave e nenhuma participante com chave propria nao esta configurado`() {
        val settings = team(
            apiKey = "",
            participatingProfileIds = setOf("conta-1", "conta-2"),
            accountApiKeys = mapOf("conta-3" to "k3")
        )

        assertFalse(settings.isConfigured)
    }

    @Test
    fun `time sem chave e sem participantes nao esta configurado`() {
        val settings = team(apiKey = "", accountApiKeys = mapOf("conta-1" to "k1"))

        assertFalse(settings.isConfigured)
    }

    @Test
    fun `modo legado com chave de time configura mesmo sem participantes`() {
        val settings = team(apiKey = "chave-time", participatingProfileIds = emptySet(), accountApiKeys = emptyMap())

        assertTrue(settings.isConfigured)
    }

    @Test
    fun `missingKeyProfileIds lista participantes sem chave propria quando ha mapa`() {
        val settings = team(
            apiKey = "chave-time",
            participatingProfileIds = setOf("conta-1", "conta-2", "conta-3"),
            accountApiKeys = mapOf("conta-1" to "k1", "conta-2" to "  ")
        )

        assertEquals(setOf("conta-2", "conta-3"), settings.missingKeyProfileIds())
    }

    @Test
    fun `missingKeyProfileIds no modo legado com chave de time esta vazio`() {
        val settings = team(apiKey = "chave-time", participatingProfileIds = setOf("conta-1", "conta-2"))

        assertEquals(emptySet(), settings.missingKeyProfileIds())
    }

    @Test
    fun `missingKeyProfileIds no modo legado sem chave de time lista todas as participantes`() {
        val settings = team(apiKey = "", participatingProfileIds = setOf("conta-1", "conta-2"))

        assertEquals(setOf("conta-1", "conta-2"), settings.missingKeyProfileIds())
    }

    @Test
    fun `withOwnKeysMaterialized copia a chave do time para participante sem chave propria`() {
        val settings = team(
            apiKey = "chave-time",
            participatingProfileIds = setOf("conta-1", "conta-2", "conta-3"),
            accountApiKeys = mapOf("conta-1" to "k1")
        )

        val materialized = settings.withOwnKeysMaterialized()

        assertEquals(
            mapOf("conta-1" to "k1", "conta-2" to "chave-time", "conta-3" to "chave-time"),
            materialized.accountApiKeys
        )
        assertEquals("chave-time", materialized.apiKey)
    }

    @Test
    fun `withOwnKeysMaterialized troca chave em branco e preserva chave propria`() {
        val settings = team(
            apiKey = "chave-time",
            participatingProfileIds = setOf("conta-1", "conta-2"),
            accountApiKeys = mapOf("conta-1" to "k1", "conta-2" to "   ")
        )

        val materialized = settings.withOwnKeysMaterialized()

        assertEquals("k1", materialized.accountApiKeys["conta-1"])
        assertEquals("chave-time", materialized.accountApiKeys["conta-2"])
    }

    @Test
    fun `withOwnKeysMaterialized e idempotente`() {
        val once = team(
            apiKey = "chave-time",
            participatingProfileIds = setOf("conta-1", "conta-2"),
            accountApiKeys = mapOf("conta-1" to "k1")
        ).withOwnKeysMaterialized()

        assertEquals(once, once.withOwnKeysMaterialized())
    }

    @Test
    fun `withOwnKeysMaterialized sem chave de time devolve igual`() {
        val settings = team(
            apiKey = "",
            participatingProfileIds = setOf("conta-1", "conta-2"),
            accountApiKeys = mapOf("conta-1" to "k1")
        )

        assertEquals(settings, settings.withOwnKeysMaterialized())
    }

    @Test
    fun `toString nao expoe a chave da conta`() {
        val printed = team(accountApiKeys = mapOf("conta-1" to "segredo-conta-789")).toString()

        assertFalse(printed.contains("segredo-conta-789"), printed)
    }
}
