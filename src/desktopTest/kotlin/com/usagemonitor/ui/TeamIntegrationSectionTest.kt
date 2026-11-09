package com.usagemonitor.ui

import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.TeamIntegrationSettings
import com.usagemonitor.presentation.ui.components.AnthropicProfileUiModel
import com.usagemonitor.presentation.ui.components.AnthropicProfileUiStatus
import com.usagemonitor.presentation.ui.components.DEFAULT_COMMIT_DEBOUNCE_MILLIS
import com.usagemonitor.presentation.ui.components.TEAM_LIST_ROW_TAG_PREFIX
import com.usagemonitor.presentation.ui.components.TEAM_NAME_FIELD_TEST_TAG
import com.usagemonitor.presentation.ui.components.TEAM_REMOVE_TEST_TAG
import com.usagemonitor.presentation.ui.components.TEAM_STEP_TAG_PREFIX
import com.usagemonitor.presentation.ui.components.TeamConnectionUiState
import com.usagemonitor.presentation.ui.components.TeamConnectionUiStatus
import com.usagemonitor.presentation.ui.components.TeamIntegrationSection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Mestre-detalhe de Configurações › Time (A08 do briefing #547, direção B). */
@OptIn(ExperimentalTestApi::class)
class TeamIntegrationSectionTest {

    private val pessoal = TeamIntegrationSettings(
        enabled = true,
        serverUrl = "https://time.exemplo.com",
        apiKey = "chave-pessoal-com-tamanho-suficiente",
        alias = "EDILSON-WINDOWS",
        deviceId = "device-1",
        participatingProfileIds = setOf("padrao"),
        id = "pessoal",
        name = "Pessoal"
    )

    private val empresa = TeamIntegrationSettings(
        enabled = false,
        serverUrl = "https://usage.empresa.com.br",
        apiKey = "chave-empresa-com-tamanho-suficiente",
        alias = "EDILSON",
        deviceId = "device-1",
        participatingProfileIds = setOf("trabalho"),
        id = "empresa",
        name = "Empresa"
    )

    private val contaTrabalho = AnthropicProfileUiModel(
        id = "trabalho",
        label = "TRABALHO",
        path = "/home/test/.claude-trabalho",
        enabled = false,
        removable = true,
        identityLabel = "dev@empresa.com.br",
        status = AnthropicProfileUiStatus.READY
    )

    @Composable
    private fun Section(
        settings: TeamIntegrationSettings,
        teams: List<TeamIntegrationSettings>,
        onSelectTeam: (String) -> Unit = {},
        onAddTeam: () -> Unit = {},
        onRemoveTeam: (String) -> Unit = {},
        onAccountKeyChange: (String, String) -> Unit = { _, _ -> },
        profiles: List<AnthropicProfileUiModel> = listOf(contaTrabalho),
        connection: TeamConnectionUiState = TeamConnectionUiState(),
        teamConnections: Map<String, TeamConnectionUiState> =
            mapOf("pessoal" to TeamConnectionUiState(status = TeamConnectionUiStatus.OK))
    ) {
        ScreenTestTheme(isDark = true) {
            TeamIntegrationSection(
                settings = settings,
                language = AppLanguage.PT,
                profiles = profiles,
                connection = connection,
                onEnabledChange = {},
                onServerUrlChange = {},
                onAliasChange = {},
                onProfileParticipationChange = { _, _ -> },
                onTestConnection = {},
                teams = teams,
                selectedTeamId = settings.id,
                teamConnections = teamConnections,
                onSelectTeam = onSelectTeam,
                onAddTeam = onAddTeam,
                onRemoveTeam = onRemoveTeam,
                onAccountApiKeyChange = onAccountKeyChange
            )
        }
    }

    @Test
    fun `nome do time fica dentro do bloco Conexao`() = runDesktopComposeUiTest {
        val time = TeamIntegrationSettings(
            id = "pessoal", name = "Pessoal", enabled = true,
            serverUrl = "https://time.local", alias = "edilson", apiKey = "chave"
        )
        setContent { Section(settings = time, teams = listOf(time)) }

        // Bloco 2 abre por padrão (servidor preenchido): o campo do nome não pode aparecer fora dos blocos.
        onAllNodesWithTag(TEAM_NAME_FIELD_TEST_TAG).assertCountEquals(0)
        onNodeWithTag("${TEAM_STEP_TAG_PREFIX}1").performClick()
        onNodeWithTag(TEAM_NAME_FIELD_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun `resumo do bloco Conexao usa o mesmo status da lista`() = runDesktopComposeUiTest {
        // deviceId preenchido: sem ele isConfigured é falso e o status vira "Incompleto".
        val time = TeamIntegrationSettings(
            id = "pessoal", name = "Pessoal", enabled = true,
            serverUrl = "https://time.local", alias = "edilson", deviceId = "dispositivo", apiKey = "chave"
        )
        setContent {
            Section(
                settings = time,
                teams = listOf(time),
                connection = TeamConnectionUiState(status = TeamConnectionUiStatus.OK),
                teamConnections = emptyMap()
            )
        }

        // Sem resultado por time a lista diz "Não testado"; o resumo do bloco 1 segue a mesma função.
        onAllNodesWithText("Conectado").assertCountEquals(0)
        onAllNodesWithText("Não testado").assertCountEquals(2)
    }

    @Test
    fun `com o time desligado os tres blocos e Remover time continuam na tela`() = runDesktopComposeUiTest {
        val time = TeamIntegrationSettings(
            id = "pessoal", name = "Pessoal", enabled = false,
            serverUrl = "https://time.local", alias = "edilson", apiKey = "chave"
        )
        setContent { Section(settings = time, teams = listOf(time)) }

        onNodeWithTag("${TEAM_STEP_TAG_PREFIX}1").assertIsDisplayed()
        onNodeWithTag("${TEAM_STEP_TAG_PREFIX}2").assertIsDisplayed()
        onNodeWithTag("${TEAM_STEP_TAG_PREFIX}3").assertIsDisplayed()
        onNodeWithTag(TEAM_REMOVE_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun `lista mostra os dois times com status escrito`() = runDesktopComposeUiTest {
        setContent { Section(settings = pessoal, teams = listOf(pessoal, empresa)) }

        onNode(
            hasTestTag("${TEAM_LIST_ROW_TAG_PREFIX}pessoal") and hasAnyDescendant(hasText("Pessoal")),
            useUnmergedTree = true
        ).assertIsDisplayed()
        onNodeWithText("Empresa").assertIsDisplayed()
        onNode(
            hasTestTag("${TEAM_LIST_ROW_TAG_PREFIX}pessoal") and hasAnyDescendant(hasText("Conectado")),
            useUnmergedTree = true
        ).assertIsDisplayed()
        onNode(
            hasTestTag("${TEAM_LIST_ROW_TAG_PREFIX}empresa") and hasAnyDescendant(hasText("Não testado")),
            useUnmergedTree = true
        ).assertIsDisplayed()
    }

    @Test
    fun `clicar num time chama onSelectTeam com o id`() = runDesktopComposeUiTest {
        var selected: String? = null
        setContent {
            Section(settings = pessoal, teams = listOf(pessoal, empresa), onSelectTeam = { selected = it })
        }

        onNodeWithTag("teamIntegrationRow:empresa").performClick()

        assertEquals("empresa", selected)
    }

    @Test
    fun `Adicionar time chama onAddTeam`() = runDesktopComposeUiTest {
        var added = false
        setContent {
            Section(settings = pessoal, teams = listOf(pessoal, empresa), onAddTeam = { added = true })
        }

        onNodeWithText("Adicionar time").performClick()

        assertEquals(true, added)
    }

    @Test
    fun `conta de outro time mostra o texto de mover`() = runDesktopComposeUiTest {
        setContent { Section(settings = pessoal, teams = listOf(pessoal, empresa)) }

        onNodeWithText("No time Empresa — ligar aqui move para este time").assertIsDisplayed()
    }

    @Test
    fun `Remover time abre a confirmacao e so confirmar chama onRemoveTeam`() = runDesktopComposeUiTest {
        var removed: String? = null
        setContent {
            Section(settings = pessoal, teams = listOf(pessoal, empresa), onRemoveTeam = { removed = it })
        }

        onNodeWithText("Remover time").performClick()
        onNodeWithText("Remover o time Pessoal?").assertIsDisplayed()
        assertNull(removed)

        onNodeWithTag("teamIntegrationRemoveConfirm").performClick()

        assertEquals("pessoal", removed)
    }

    @Test
    fun `sem times mostra o estado vazio nomeando o recorte`() = runDesktopComposeUiTest {
        setContent { Section(settings = pessoal, teams = emptyList()) }

        onNodeWithText("Nenhum time configurado").assertIsDisplayed()
    }

    private val prefixoCampoContaTrabalho = "teamIntegrationAccountKey:trabalho"
    private val chaveContaTrabalho = "chave-conta-trabalho-com-tamanho-suficiente"

    @Test
    fun `conta ligada mostra o campo Chave da conta com o valor gravado`() = runDesktopComposeUiTest {
        val comContaPropria = empresa.copy(
            enabled = true,
            participatingProfileIds = setOf("trabalho"),
            accountApiKeys = mapOf("trabalho" to chaveContaTrabalho)
        )
        setContent { Section(settings = comContaPropria, teams = listOf(comContaPropria)) }

        onNodeWithText("Chave da conta").assertIsDisplayed()
        onNodeWithTag(prefixoCampoContaTrabalho).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.InputText, AnnotatedString(chaveContaTrabalho))
        )
    }

    @Test
    fun `conta desligada nao mostra o campo Chave da conta`() = runDesktopComposeUiTest {
        setContent { Section(settings = pessoal, teams = listOf(pessoal)) }

        onNodeWithText("TRABALHO").assertIsDisplayed()
        onAllNodesWithTag(prefixoCampoContaTrabalho).assertCountEquals(0)
    }

    @Test
    fun `digitar na chave da conta chama o callback com profileId e valor`() = runDesktopComposeUiTest {
        val gravados = mutableListOf<Pair<String, String>>()
        val comContaLigada = empresa.copy(enabled = true, participatingProfileIds = setOf("trabalho"))
        setContent {
            Section(
                settings = comContaLigada,
                teams = listOf(comContaLigada),
                onAccountKeyChange = { profileId, valor -> gravados += profileId to valor }
            )
        }

        mainClock.autoAdvance = false
        onNodeWithTag(prefixoCampoContaTrabalho).performTextReplacement(chaveContaTrabalho)
        mainClock.advanceTimeBy(DEFAULT_COMMIT_DEBOUNCE_MILLIS + 100L)
        waitForIdle()

        assertEquals(listOf("trabalho" to chaveContaTrabalho), gravados)
    }

    @Test
    fun `sem campo Chave do time na tela`() = runDesktopComposeUiTest {
        setContent { Section(settings = pessoal, teams = listOf(pessoal)) }

        onAllNodesWithText("Chave do time").assertCountEquals(0)
    }

    @Test
    fun `conta ligada sem chave mostra falta a chave`() = runDesktopComposeUiTest {
        val semChave = empresa.copy(
            enabled = true,
            apiKey = "",
            participatingProfileIds = setOf("trabalho"),
            accountApiKeys = emptyMap()
        )
        setContent { Section(settings = semChave, teams = listOf(semChave)) }

        onNodeWithText("falta a chave").assertIsDisplayed()
    }

    @Test
    fun `legado mostra a chave antiga no campo da conta`() = runDesktopComposeUiTest {
        val legado = empresa.copy(
            enabled = true,
            participatingProfileIds = setOf("trabalho"),
            accountApiKeys = emptyMap()
        )
        setContent { Section(settings = legado, teams = listOf(legado)) }

        onNodeWithTag(prefixoCampoContaTrabalho).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.InputText, AnnotatedString(legado.apiKey))
        )
    }

    @Test
    fun `detalhe mostra os blocos Conexao Contas e Administracao`() = runDesktopComposeUiTest {
        setContent { Section(settings = pessoal, teams = listOf(pessoal)) }

        onNodeWithText("Conexão").assertIsDisplayed()
        onNodeWithText("Contas").assertIsDisplayed()
        onNodeWithText("Administração").assertIsDisplayed()
    }

    @Test
    fun `sem servidor abre Conexao e nao Contas`() = runDesktopComposeUiTest {
        val semServidor = pessoal.copy(serverUrl = "")
        setContent { Section(settings = semServidor, teams = listOf(semServidor)) }

        onNodeWithText("Testar conexão").assertIsDisplayed()
        onAllNodesWithText("TRABALHO").assertCountEquals(0)
    }

    @Test
    fun `com servidor abre Contas e nao Conexao`() = runDesktopComposeUiTest {
        setContent { Section(settings = pessoal, teams = listOf(pessoal)) }

        onNodeWithText("TRABALHO").assertIsDisplayed()
        onAllNodesWithText("Testar conexão").assertCountEquals(0)
    }

    @Test
    fun `falha de conexao abre Conexao mesmo com servidor preenchido`() = runDesktopComposeUiTest {
        val falhou = TeamConnectionUiState(
            status = TeamConnectionUiStatus.FAILED,
            message = "Falha de conexão"
        )
        setContent { Section(settings = pessoal, teams = listOf(pessoal), connection = falhou) }

        onNodeWithText("Testar conexão").assertIsDisplayed()
        onAllNodesWithText("TRABALHO").assertCountEquals(0)
    }

    @Test
    fun `com token de admin abre Contas`() = runDesktopComposeUiTest {
        val comAdmin = pessoal.copy(adminToken = "token-admin-com-tamanho-suficiente")
        setContent { Section(settings = comAdmin, teams = listOf(comAdmin)) }

        onNodeWithText("TRABALHO").assertIsDisplayed()
        onAllNodesWithText("Testar conexão").assertCountEquals(0)
    }

    @Test
    fun `bloco Contas fechado resume N de M ligadas`() = runDesktopComposeUiTest {
        val quatroContas = listOf(
            contaTrabalho,
            contaTrabalho.copy(id = "conta-2", label = "PESSOAL-2"),
            contaTrabalho.copy(id = "conta-3", label = "CLIENTE"),
            contaTrabalho.copy(id = "conta-4", label = "LAB")
        )
        val semServidor = empresa.copy(
            enabled = true,
            serverUrl = "",
            participatingProfileIds = setOf("trabalho", "conta-2")
        )
        setContent { Section(settings = semServidor, teams = listOf(semServidor), profiles = quatroContas) }

        onNodeWithText("2 de 4 ligadas").assertIsDisplayed()
    }

    @Test
    fun `bloco Administracao fechado mostra Desligado`() = runDesktopComposeUiTest {
        setContent { Section(settings = pessoal, teams = listOf(pessoal)) }

        onNodeWithText("Desligado").assertIsDisplayed()
    }
}
