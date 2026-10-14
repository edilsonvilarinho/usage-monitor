package com.usagemonitor.domain

import com.usagemonitor.domain.entity.ProxySettings
import com.usagemonitor.domain.entity.TeamIntegrationSettings
import com.usagemonitor.domain.entity.redactedSecret
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SecretRedactionTest {

    @Test
    fun `segredo vira asteriscos e ausencia continua visivel`() {
        assertEquals("***", redactedSecret("sk-live-123"))
        assertEquals("", redactedSecret(""))
        assertEquals("null", redactedSecret(null))
    }

    @Test
    fun `toString das configuracoes nao expoe chave nem senha`() {
        val team = TeamIntegrationSettings(enabled = true, apiKey = "team-key-123", adminToken = "admin-456")
        val proxy = ProxySettings(host = "proxy.local", port = 8080, username = "ana", password = "s3nha")

        val printed = "$team $proxy"

        assertFalse(printed.contains("team-key-123"))
        assertFalse(printed.contains("admin-456"))
        assertFalse(printed.contains("s3nha"))
        assertTrue(printed.contains("proxy.local"))
    }

    @Test
    fun `igualdade e copia continuam de data class`() {
        val team = TeamIntegrationSettings(apiKey = "a")

        assertEquals(team, team.copy())
        assertEquals("b", team.copy(apiKey = "b").apiKey)
    }
}
