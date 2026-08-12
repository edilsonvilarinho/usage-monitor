package com.usagemonitor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.TeamIntegrationSettings
import com.usagemonitor.domain.entity.TeamKeyEntry
import com.usagemonitor.presentation.ui.TEAM_KEYS_CREATE_BUTTON_TAG
import com.usagemonitor.presentation.ui.TEAM_KEYS_CREATE_FIELD_TAG
import com.usagemonitor.presentation.ui.TEAM_KEYS_ERROR_TAG
import com.usagemonitor.presentation.ui.TEAM_KEYS_ROW_TAG_PREFIX
import com.usagemonitor.presentation.ui.TeamKeysAdminContent
import com.usagemonitor.presentation.ui.components.TEAM_ADMIN_EXIT_TEST_TAG
import com.usagemonitor.presentation.ui.components.TEAM_ADMIN_KEYS_TEST_TAG
import com.usagemonitor.presentation.ui.components.TEAM_ADMIN_SWITCH_TEST_TAG
import com.usagemonitor.presentation.ui.components.TEAM_ADMIN_VALIDATE_TEST_TAG
import com.usagemonitor.presentation.ui.components.TEAM_SYNC_STATUS_TEST_TAG
import com.usagemonitor.presentation.ui.components.TeamConnectionUiState
import com.usagemonitor.presentation.ui.components.TeamIntegrationSection
import com.usagemonitor.presentation.ui.theme.AppTheme
import com.usagemonitor.presentation.viewmodel.TeamKeysUiState
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class TeamAdminSectionTest {

    @Test
    fun `campos de admin so aparecem depois de ligar a chave`() = runDesktopComposeUiTest {
        renderSection(TeamIntegrationSettings(enabled = true, serverUrl = "https://time.local"))

        onAllNodesWithTag(TEAM_ADMIN_VALIDATE_TEST_TAG).assertCountEquals(0)

        onNodeWithTag(TEAM_ADMIN_SWITCH_TEST_TAG).performClick()

        onNodeWithTag(TEAM_ADMIN_VALIDATE_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun `validar e gerenciar exigem servidor e token, nao chave de time`() =
        runDesktopComposeUiTest {
            renderSection(
                TeamIntegrationSettings(
                    enabled = true,
                    serverUrl = "https://time.local",
                    adminToken = "token-de-admin",
                    // Sem chave, sem apelido e sem conta marcada de propósito:
                    // administrar não exige participar de time nenhum.
                    apiKey = "",
                    alias = ""
                )
            )

            onNodeWithTag(TEAM_ADMIN_VALIDATE_TEST_TAG).assertIsEnabled()
            onNodeWithTag(TEAM_ADMIN_KEYS_TEST_TAG).assertIsEnabled()
        }

    @Test
    fun `sem servidor os botoes de admin ficam desabilitados`() = runDesktopComposeUiTest {
        renderSection(
            TeamIntegrationSettings(enabled = true, serverUrl = "", adminToken = "token-de-admin")
        )

        onNodeWithTag(TEAM_ADMIN_VALIDATE_TEST_TAG).assertIsNotEnabled()
        onNodeWithTag(TEAM_ADMIN_KEYS_TEST_TAG).assertIsNotEnabled()
    }

    @Test
    fun `sair do modo admin emite o evento`() = runDesktopComposeUiTest {
        var exits = 0
        renderSection(
            settings = TeamIntegrationSettings(
                enabled = true,
                serverUrl = "https://time.local",
                adminToken = "token-de-admin"
            ),
            onExitAdminMode = { exits += 1 }
        )

        onNodeWithTag(TEAM_ADMIN_EXIT_TEST_TAG).performClick()

        assertEquals(1, exits)
    }

    @Test
    fun `falha do envio em background aparece na secao`() = runDesktopComposeUiTest {
        renderSection(
            settings = TeamIntegrationSettings(
                enabled = true,
                serverUrl = "https://time.local",
                apiKey = "chave",
                alias = "edilson",
                deviceId = "device-1"
            ),
            syncFailureMessage = "Chave de time nao autorizada para esta conta."
        )

        onNodeWithTag(TEAM_SYNC_STATUS_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun `sem falha nao ha aviso de envio`() = runDesktopComposeUiTest {
        renderSection(
            TeamIntegrationSettings(
                enabled = true,
                serverUrl = "https://time.local",
                apiKey = "chave",
                alias = "edilson",
                deviceId = "device-1"
            )
        )

        onAllNodesWithTag(TEAM_SYNC_STATUS_TEST_TAG).assertCountEquals(0)
    }

    private fun ComposeUiTest.renderSection(
        settings: TeamIntegrationSettings,
        onExitAdminMode: () -> Unit = {},
        syncFailureMessage: String? = null
    ) {
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(620.dp).height(900.dp)) {
                    TeamIntegrationSection(
                        settings = settings,
                        language = AppLanguage.PT,
                        profiles = emptyList(),
                        connection = TeamConnectionUiState(),
                        onEnabledChange = {},
                        onServerUrlChange = {},
                        onApiKeyChange = {},
                        onAliasChange = {},
                        onProfileParticipationChange = { _, _ -> },
                        onTestConnection = {},
                        syncFailureMessage = syncFailureMessage,
                        onExitAdminMode = onExitAdminMode
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTestApi::class)
class TeamKeysAdminScreenTest {

    @Test
    fun `lista mostra o rotulo, o prefixo e a conta vinculada`() = runDesktopComposeUiTest {
        renderKeys(
            TeamKeysUiState.Success(
                keys = listOf(
                    entry(
                        id = "key-1",
                        label = "fulano@empresa.com",
                        accounts = listOf("account-uuid-aaa")
                    )
                )
            )
        )

        onNodeWithTag("${TEAM_KEYS_ROW_TAG_PREFIX}key-1").assertIsDisplayed()
        onNodeWithText("fulano@empresa.com").assertIsDisplayed()
        onNodeWithText("account-uuid-aaa").assertIsDisplayed()
    }

    @Test
    fun `chave nasce oculta e o botao revela`() = runDesktopComposeUiTest {
        renderKeys(TeamKeysUiState.Success(keys = listOf(entry("key-1", "fulano@empresa.com"))))

        onAllNodesWithText("raw-key-secreta").assertCountEquals(0)

        onNodeWithText("Mostrar").performClick()

        onNodeWithText("raw-key-secreta").assertIsDisplayed()
    }

    @Test
    fun `chave sem conta diz que o vinculo nasce no primeiro envio`() = runDesktopComposeUiTest {
        renderKeys(TeamKeysUiState.Success(keys = listOf(entry("key-1", "fulano@empresa.com"))))

        onNodeWithText(
            "Sem conta vinculada — o vínculo nasce no primeiro envio."
        ).assertIsDisplayed()
    }

    @Test
    fun `emitir exige rotulo`() = runDesktopComposeUiTest {
        val created = mutableListOf<Pair<String, Int>>()
        renderKeys(TeamKeysUiState.Success(), onCreate = { label, max -> created += label to max })

        onNodeWithTag(TEAM_KEYS_CREATE_BUTTON_TAG).assertIsNotEnabled()

        onNodeWithTag(TEAM_KEYS_CREATE_FIELD_TAG).performTextInput("sicrano@empresa.com")
        onNodeWithTag(TEAM_KEYS_CREATE_BUTTON_TAG).performClick()

        assertEquals(listOf("sicrano@empresa.com" to 1), created)
    }

    @Test
    fun `erro de acao aparece sem apagar a lista`() = runDesktopComposeUiTest {
        renderKeys(
            TeamKeysUiState.Success(
                keys = listOf(entry("key-1", "fulano@empresa.com")),
                actionError = "limite excedido"
            )
        )

        onNodeWithTag(TEAM_KEYS_ERROR_TAG).assertIsDisplayed()
        onNodeWithTag("${TEAM_KEYS_ROW_TAG_PREFIX}key-1").assertIsDisplayed()
    }

    private fun ComposeUiTest.renderKeys(
        state: TeamKeysUiState,
        onCreate: (String, Int) -> Unit = { _, _ -> }
    ) {
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(900.dp).height(700.dp)) {
                    TeamKeysAdminContent(
                        state = state,
                        language = AppLanguage.PT,
                        onCreate = onCreate
                    )
                }
            }
        }
    }

    private fun entry(
        id: String,
        label: String,
        accounts: List<String> = emptyList()
    ): TeamKeyEntry {
        return TeamKeyEntry(
            id = id,
            label = label,
            key = "raw-key-secreta",
            keyPrefix = "raw-key-",
            maxAccounts = 1,
            accounts = accounts
        )
    }
}
