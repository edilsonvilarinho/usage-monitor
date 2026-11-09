package com.usagemonitor.ui

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
        onAccountKeyChange: (String, String) -> Unit = { _, _ -> }
    ) {
        ScreenTestTheme(isDark = true) {
            TeamIntegrationSection(
                settings = settings,
                language = AppLanguage.PT,
                profiles = listOf(contaTrabalho),
                connection = TeamConnectionUiState(),
                onEnabledChange = {},
                onServerUrlChange = {},
                onApiKeyChange = {},
                onAliasChange = {},
                onProfileParticipationChange = { _, _ -> },
                onTestConnection = {},
                teams = teams,
                selectedTeamId = settings.id,
                teamConnections = mapOf("pessoal" to TeamConnectionUiState(status = TeamConnectionUiStatus.OK)),
                onSelectTeam = onSelectTeam,
                onAddTeam = onAddTeam,
                onRemoveTeam = onRemoveTeam,
                onAccountApiKeyChange = onAccountKeyChange
            )
        }
    }

    @Test
    fun `lista mostra os dois times com status escrito`() = runDesktopComposeUiTest {
        setContent { Section(settings = pessoal, teams = listOf(pessoal, empresa)) }

        onNode(
            hasTestTag("${TEAM_LIST_ROW_TAG_PREFIX}pessoal") and hasAnyDescendant(hasText("Pessoal")),
            useUnmergedTree = true
        ).assertIsDisplayed()
        onNodeWithText("Empresa").assertIsDisplayed()
        onNodeWithText("Conectado").assertIsDisplayed()
        onNodeWithText("Não testado").assertIsDisplayed()
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
        onNodeWithTag(prefixoCampoContaTrabalho).performTextInput(chaveContaTrabalho)
        mainClock.advanceTimeBy(DEFAULT_COMMIT_DEBOUNCE_MILLIS + 100L)
        waitForIdle()

        assertEquals(listOf("trabalho" to chaveContaTrabalho), gravados)
    }
}
