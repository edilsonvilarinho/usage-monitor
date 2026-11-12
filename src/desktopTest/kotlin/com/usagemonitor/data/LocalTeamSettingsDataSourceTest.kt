package com.usagemonitor.data

import com.usagemonitor.data.datasource.LocalTeamSettingsDataSource
import com.usagemonitor.domain.entity.TeamIntegrationSettings
import com.usagemonitor.domain.entity.TeamIntegrations
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * O `deviceId` é o que separa uma máquina da outra no servidor de time.
 *
 * Gerar um novo onde já havia um faz a mesma máquina aparecer duas vezes na tela
 * do time, e a linha antiga fica lá até a retenção do servidor recolhê-la.
 */
class LocalTeamSettingsDataSourceTest {

    @Test
    fun `mantem o mesmo deviceId entre leituras`() {
        withSettingsFile { file ->
            val first = LocalTeamSettingsDataSource(file).load()
            val second = LocalTeamSettingsDataSource(file).load()

            assertTrue(first.deviceId.isNotBlank())
            assertEquals(first.deviceId, second.deviceId)
        }
    }

    @Test
    fun `preserva o deviceId ao gravar sem informa-lo`() {
        withSettingsFile { file ->
            val dataSource = LocalTeamSettingsDataSource(file)
            val original = dataSource.load()

            dataSource.save(original.copy(alias = "edilson", deviceId = ""))

            assertEquals(original.deviceId, dataSource.load().deviceId)
            assertEquals("edilson", dataSource.load().alias)
        }
    }

    @Test
    fun `trocar o apelido nao troca o deviceId`() {
        withSettingsFile { file ->
            val dataSource = LocalTeamSettingsDataSource(file)
            val original = dataSource.load()

            dataSource.save(original.copy(alias = "edilson"))
            dataSource.save(dataSource.load().copy(alias = "SUETONIO"))

            val reloaded = dataSource.load()
            assertEquals("SUETONIO", reloaded.alias)
            assertEquals(original.deviceId, reloaded.deviceId)
        }
    }

    @Test
    fun `guarda o token de administracao`() {
        withSettingsFile { file ->
            val dataSource = LocalTeamSettingsDataSource(file)

            dataSource.save(dataSource.load().copy(adminToken = "  token-de-admin  "))

            val reloaded = dataSource.load()
            assertEquals("token-de-admin", reloaded.adminToken)
            // Administrar não exige participar de time: o token sozinho não pode
            // fazer a integração parecer configurada.
            assertEquals(false, reloaded.isConfigured)
        }
    }

    @Test
    fun `arquivo sem o campo de admin continua legivel`() {
        withSettingsFile { file ->
            file.writeText(
                """{"enabled":true,"serverUrl":"https://time.empresa.com",""" +
                    """"apiKey":"k","alias":"edilson","deviceId":"device-1"}"""
            )

            val loaded = LocalTeamSettingsDataSource(file).load()

            assertEquals("device-1", loaded.deviceId)
            assertEquals("", loaded.adminToken)
        }
    }

    @Test
    fun `arquivo ilegivel vai para corrupt em vez de ser sobrescrito`() {
        withSettingsFile { file ->
            val previousDeviceId = LocalTeamSettingsDataSource(file).load().deviceId
            file.writeText("{ isto nao e json")

            val recovered = LocalTeamSettingsDataSource(file).load()

            // O conteúdo antigo continua recuperável em vez de ser destruído
            // pelos defaults — é o único vestígio de qual era o deviceId.
            val corruptFile = File(file.parentFile, "${file.name}.corrupt")
            assertTrue(corruptFile.isFile)
            assertEquals("{ isto nao e json", corruptFile.readText())
            assertNotEquals(previousDeviceId, recovered.deviceId)
            assertTrue(recovered.deviceId.isNotBlank())
        }
    }

    @Test
    fun `arquivo plano da versao atual vira um time legado sem acao do usuario`() {
        withSettingsFile { file ->
            file.writeText(legacyFlatJson(serverUrl = "https://time.empresa.com:8443", profiles = """["p2","p1"]"""))

            val loaded = LocalTeamSettingsDataSource(file).loadAll()

            assertEquals(1, loaded.teams.size)
            val team = loaded.teams.single()
            assertEquals("team-legacy", team.id)
            assertEquals("time.empresa.com", team.name)
            assertEquals("https://time.empresa.com:8443", team.serverUrl)
            assertEquals(setOf("p1", "p2"), team.participatingProfileIds)
            assertEquals("device-1", loaded.deviceId)
            assertEquals("device-1", team.deviceId)
            // A leitura regrava no formato v2, fixando a migração.
            assertTrue(jsonOf(file).containsKey("teams"))
        }
    }

    @Test
    fun `time legado sem servidor recebe o nome Time`() {
        withSettingsFile { file ->
            file.writeText("""{"enabled":true,"adminToken":"tok","deviceId":"device-1"}""")

            val loaded = LocalTeamSettingsDataSource(file).loadAll()

            assertEquals("Time", loaded.teams.single().name)
            assertEquals("tok", loaded.teams.single().adminToken)
        }
    }

    @Test
    fun `arquivo plano sem nenhum valor vira lista vazia`() {
        withSettingsFile { file ->
            file.writeText("""{"enabled":false,"deviceId":"device-1"}""")

            val loaded = LocalTeamSettingsDataSource(file).loadAll()

            assertTrue(loaded.teams.isEmpty())
            assertEquals("device-1", loaded.deviceId)
        }
    }

    @Test
    fun `id do time migrado fica estavel entre duas leituras sem gravar`() {
        withSettingsFile { file ->
            file.writeText(legacyFlatJson(serverUrl = "https://time.empresa.com"))
            val dataSource = LocalTeamSettingsDataSource(file)

            val first = dataSource.peekAll()
            val second = dataSource.peekAll()

            assertNotNull(first)
            assertNotNull(second)
            assertEquals("team-legacy", first.teams.single().id)
            assertEquals(first.teams.single().id, second.teams.single().id)
        }
    }

    @Test
    fun `ida e volta de dois times preserva ordem e campos`() {
        withSettingsFile { file ->
            val dataSource = LocalTeamSettingsDataSource(file)
            val aggregate = TeamIntegrations(
                deviceId = "device-1",
                enabled = true,
                teams = listOf(
                    team(id = "team-a", name = "Empresa", serverUrl = "https://a.empresa.com", profiles = setOf("p1")),
                    team(id = "team-b", name = "Freela", serverUrl = "https://b.cliente.com", profiles = setOf("p2"))
                )
            )

            dataSource.saveAll(aggregate)

            assertEquals(aggregate, dataSource.loadAll())
        }
    }

    @Test
    fun `espelha o primeiro time nos campos planos`() {
        withSettingsFile { file ->
            val dataSource = LocalTeamSettingsDataSource(file)

            dataSource.saveAll(
                TeamIntegrations(
                    deviceId = "device-1",
                    enabled = true,
                    teams = listOf(
                        team(id = "team-a", serverUrl = "https://a.empresa.com", alias = "edilson", profiles = setOf("p2", "p1")),
                        team(id = "team-b", serverUrl = "https://b.cliente.com", alias = "outro", profiles = setOf("p3"))
                    )
                )
            )

            val flat = jsonOf(file)
            assertEquals("https://a.empresa.com", flat.string("serverUrl"))
            assertEquals("edilson", flat.string("alias"))
            assertEquals(listOf("p1", "p2"), flat.stringList("participatingProfileIds"))
            assertEquals("device-1", flat.string("deviceId"))
        }
    }

    @Test
    fun `lista vazia zera os campos planos`() {
        withSettingsFile { file ->
            val dataSource = LocalTeamSettingsDataSource(file)
            dataSource.saveAll(
                TeamIntegrations(
                    deviceId = "device-1",
                    enabled = true,
                    teams = listOf(team(id = "team-a", serverUrl = "https://a.empresa.com", profiles = setOf("p1")))
                )
            )

            dataSource.saveAll(TeamIntegrations(deviceId = "device-1", enabled = true, teams = emptyList()))

            val flat = jsonOf(file)
            assertEquals("", flat.string("serverUrl"))
            assertEquals(emptyList(), flat.stringList("participatingProfileIds"))
            assertTrue(dataSource.loadAll().teams.isEmpty())
        }
    }

    @Test
    fun `save com id existente troca so esse time e preserva a posicao`() {
        withSettingsFile { file ->
            val dataSource = LocalTeamSettingsDataSource(file)
            dataSource.saveAll(
                TeamIntegrations(
                    deviceId = "device-1",
                    enabled = true,
                    teams = listOf(
                        team(id = "team-a", alias = "a"),
                        team(id = "team-b", alias = "b")
                    )
                )
            )

            dataSource.save(dataSource.load().copy(id = "team-a", alias = "a2"))

            val reloaded = dataSource.loadAll().teams
            assertEquals(listOf("team-a", "team-b"), reloaded.map { it.id })
            assertEquals("a2", reloaded[0].alias)
            assertEquals("b", reloaded[1].alias)
        }
    }

    @Test
    fun `save sem id grava no primeiro time`() {
        withSettingsFile { file ->
            val dataSource = LocalTeamSettingsDataSource(file)
            dataSource.saveAll(
                TeamIntegrations(
                    deviceId = "device-1",
                    enabled = true,
                    teams = listOf(team(id = "team-a", alias = "a"))
                )
            )

            dataSource.save(TeamIntegrationSettings(alias = "novo", serverUrl = "https://a.empresa.com"))

            val reloaded = dataSource.loadAll().teams
            assertEquals(1, reloaded.size)
            assertEquals("team-a", reloaded.single().id)
            assertEquals("novo", reloaded.single().alias)
        }
    }

    @Test
    fun `save em arquivo vazio cria o time legado`() {
        withSettingsFile { file ->
            val dataSource = LocalTeamSettingsDataSource(file)

            dataSource.save(TeamIntegrationSettings(alias = "edilson", serverUrl = "https://a.empresa.com", apiKey = "k"))

            val reloaded = dataSource.loadAll().teams
            assertEquals(listOf("team-legacy"), reloaded.map { it.id })
            assertEquals("edilson", reloaded.single().alias)
        }
    }

    @Test
    fun `load devolve o primeiro time com o deviceId e o enabled do agregado`() {
        withSettingsFile { file ->
            val dataSource = LocalTeamSettingsDataSource(file)
            dataSource.saveAll(
                TeamIntegrations(
                    deviceId = "device-1",
                    enabled = true,
                    teams = listOf(team(id = "team-a", alias = "a"), team(id = "team-b", alias = "b"))
                )
            )

            val loaded = dataSource.load()

            assertEquals("team-a", loaded.id)
            assertEquals("device-1", loaded.deviceId)
            assertTrue(loaded.enabled)
        }
    }

    @Test
    fun `peekAll nao grava nem cria deviceId`() {
        withSettingsFile { file ->
            val content = """{"enabled":true,"serverUrl":"https://time.empresa.com","alias":"edilson"}"""
            file.writeText(content)

            val peeked = LocalTeamSettingsDataSource(file).peekAll()

            assertNotNull(peeked)
            assertEquals("", peeked.deviceId)
            assertEquals(content, file.readText())
        }
    }

    @Test
    fun `peekAll sem arquivo devolve null e nao o cria`() {
        withSettingsFile { file ->
            assertNull(LocalTeamSettingsDataSource(file).peekAll())
            assertFalse(file.exists())
        }
    }

    @Test
    fun `usuario ja configurado no formato plano segue ativo apos migracao e reabertura`() {
        withSettingsFile { file ->
            file.writeText(legacyFlatJson(serverUrl = "https://time.empresa.com:8443", profiles = """["p2","p1"]"""))

            val migrated = LocalTeamSettingsDataSource(file).loadAll()

            assertTrue(migrated.enabled)
            val team = assertNotNull(migrated.teamFor("p1"))
            assertTrue(team.isActive)
            assertEquals("k", team.apiKey)

            val reopened = LocalTeamSettingsDataSource(file).loadAll()
            assertEquals(migrated, reopened)

            val flat = jsonOf(file)
            assertEquals("true", flat.string("enabled"))
            assertEquals("https://time.empresa.com:8443", flat.string("serverUrl"))
            assertEquals("k", flat.string("apiKey"))
            assertEquals("edilson", flat.string("alias"))
            assertEquals(listOf("p1", "p2"), flat.stringList("participatingProfileIds"))
        }
    }

    @Test
    fun `load de arquivo plano legado devolve o time ativo com a chave original`() {
        withSettingsFile { file ->
            file.writeText(legacyFlatJson(serverUrl = "https://time.empresa.com:8443", profiles = """["p1"]"""))

            val settings = LocalTeamSettingsDataSource(file).load()

            assertTrue(settings.isActive)
            assertEquals("k", settings.apiKey)
        }
    }

    @Test
    fun `arquivo v2 sem accountApiKeys sai byte a byte igual ao regravar`() {
        withSettingsFile { file ->
            val dataSource = LocalTeamSettingsDataSource(file)
            dataSource.saveAll(
                TeamIntegrations(
                    deviceId = "device-1",
                    enabled = true,
                    teams = listOf(team(id = "team-a", serverUrl = "https://a.empresa.com", profiles = setOf("p1")))
                )
            )
            val original = file.readText()

            dataSource.saveAll(dataSource.loadAll())

            assertFalse(original.contains("accountApiKeys"), original)
            assertEquals(original, file.readText())
        }
    }

    @Test
    fun `mapa de chaves por conta sobrevive a ida e volta`() {
        withSettingsFile { file ->
            val dataSource = LocalTeamSettingsDataSource(file)
            val aggregate = TeamIntegrations(
                deviceId = "device-1",
                enabled = true,
                teams = listOf(
                    team(id = "team-a", profiles = setOf("p1", "p2")).copy(accountApiKeys = mapOf("p1" to "chave-p1"))
                )
            )

            dataSource.saveAll(aggregate)

            assertEquals(mapOf("p1" to "chave-p1"), dataSource.loadAll().teams.single().accountApiKeys)
            assertEquals(aggregate, dataSource.loadAll())
        }
    }

    @Test
    fun `campo desconhecido de versao futura nao quebra a leitura do time`() {
        withSettingsFile { file ->
            file.writeText(
                """{"enabled":true,"deviceId":"device-1","teams":[{"id":"team-a","name":"Empresa",""" +
                    """"serverUrl":"https://a.empresa.com","apiKey":"k","alias":"edilson",""" +
                    """"participatingProfileIds":["p1"],"adminToken":"","campoFuturo":{"x":1}}],"outroCampo":2}"""
            )

            val loaded = LocalTeamSettingsDataSource(file).loadAll()

            assertEquals(listOf("team-a"), loaded.teams.map { it.id })
            assertEquals(setOf("p1"), loaded.teams.single().participatingProfileIds)
        }
    }

    private fun team(
        id: String,
        name: String = "",
        serverUrl: String = "",
        alias: String = "",
        profiles: Set<String> = emptySet()
    ): TeamIntegrationSettings {
        return TeamIntegrationSettings(
            id = id,
            name = name,
            serverUrl = serverUrl,
            apiKey = "key-$id",
            alias = alias,
            participatingProfileIds = profiles,
            adminToken = "admin-$id"
        )
    }

    private fun legacyFlatJson(serverUrl: String, profiles: String = "[]"): String {
        return """{"enabled":true,"serverUrl":"$serverUrl","apiKey":"k","alias":"edilson",""" +
            """"deviceId":"device-1","participatingProfileIds":$profiles,"adminToken":""}"""
    }

    private fun jsonOf(file: File): JsonObject {
        return Json.parseToJsonElement(file.readText()).jsonObject
    }

    private fun JsonObject.string(key: String): String = getValue(key).jsonPrimitive.content

    private fun JsonObject.stringList(key: String): List<String> {
        return getValue(key).jsonArray.map { it.jsonPrimitive.content }
    }

    private fun withSettingsFile(block: (File) -> Unit) {
        val tempDir = createTempDirectory().toFile()
        try {
            block(File(tempDir, "team.json"))
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
