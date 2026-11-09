package com.usagemonitor.data

import com.usagemonitor.data.datasource.LocalTeamSettingsDataSource
import com.usagemonitor.domain.entity.TeamIntegrationSettings
import com.usagemonitor.domain.entity.TeamIntegrations
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

private const val TEAM_KEY = "tk_chave-do-time-000111"
private const val ACCOUNT_KEY_SECRET = "chave-da-conta-p1-999"

/** Segredo de conta e de time nunca aparece no texto do time lido do arquivo. */
class LocalTeamSettingsRedactionTest {

    @Test
    fun `toString do time lido do arquivo nao expoe a chave da conta nem a do time`() {
        val dir = createTempDirectory("team-redaction").toFile()
        try {
            val dataSource = LocalTeamSettingsDataSource(File(dir, "team.json"))
            dataSource.saveAll(
                TeamIntegrations(
                    deviceId = "device-1",
                    enabled = true,
                    teams = listOf(
                        TeamIntegrationSettings(
                            id = "team-a",
                            name = "Empresa",
                            serverUrl = "https://a.empresa.com",
                            apiKey = TEAM_KEY,
                            alias = "Ana",
                            deviceId = "device-1",
                            participatingProfileIds = setOf("p1"),
                            accountApiKeys = mapOf("p1" to ACCOUNT_KEY_SECRET)
                        )
                    )
                )
            )

            val team = dataSource.loadAll().teams.single()

            // A chave segue no arquivo (o cliente a usa); o texto de log nunca a carrega.
            assertEquals(ACCOUNT_KEY_SECRET, team.accountApiKeys["p1"])
            assertFalse(team.toString().contains(ACCOUNT_KEY_SECRET))
            assertFalse(team.toString().contains(TEAM_KEY))
        } finally {
            dir.deleteRecursively()
        }
    }
}
