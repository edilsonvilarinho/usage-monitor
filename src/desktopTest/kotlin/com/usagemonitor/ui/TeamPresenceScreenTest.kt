package com.usagemonitor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.CliSessionSummary
import com.usagemonitor.domain.entity.TeamMemberPresence
import com.usagemonitor.domain.entity.TeamMemberUsage
import com.usagemonitor.presentation.ui.PRESENCE_ACCOUNT_DELETE_TAG_PREFIX
import com.usagemonitor.presentation.ui.PRESENCE_ACCOUNT_GROUP_TAG_PREFIX
import com.usagemonitor.presentation.ui.PRESENCE_ACTION_ERROR_TAG
import com.usagemonitor.presentation.ui.PRESENCE_CLOCK_SKEW_TAG
import com.usagemonitor.presentation.ui.PRESENCE_COLUMN_HEADER_TAG
import com.usagemonitor.presentation.ui.PRESENCE_DELETE_ACCOUNT_CONFIRM_TAG
import com.usagemonitor.presentation.ui.PRESENCE_LIST_SCROLLBAR_TAG
import com.usagemonitor.presentation.ui.PRESENCE_LOCAL_BADGE_TAG_PREFIX
import com.usagemonitor.presentation.ui.PRESENCE_MEMBER_REMOVE_TAG_PREFIX
import com.usagemonitor.presentation.ui.PRESENCE_ONLY_ONLINE_TAG
import com.usagemonitor.presentation.ui.PRESENCE_REMOVE_CONFIRM_TAG
import com.usagemonitor.TEAM_PRESENCE_MIN_WINDOW_WIDTH_DP
import com.usagemonitor.presentation.ui.PRESENCE_FILTER_TAG
import com.usagemonitor.presentation.ui.PRESENCE_LAST_SEEN_TAG_PREFIX
import com.usagemonitor.presentation.ui.PRESENCE_ROW_TAG_PREFIX
import com.usagemonitor.presentation.ui.PRESENCE_STATE_TAG_PREFIX
import com.usagemonitor.presentation.ui.PRESENCE_WORKING_TAG_PREFIX
import com.usagemonitor.presentation.ui.TeamPresenceContent
import com.usagemonitor.presentation.ui.theme.AppTheme
import com.usagemonitor.presentation.viewmodel.TeamPresenceUiState
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val NOW = Instant.parse("2026-08-11T12:00:00Z")

private fun session(id: String = "s1"): CliSessionSummary {
    return CliSessionSummary(sessionId = id, filePath = "", firstTs = NOW, lastTs = NOW)
}

private fun entry(
    deviceId: String = "device-1",
    alias: String = "edilson",
    hostName: String? = "DESKTOP-A1",
    isOnline: Boolean = true,
    isWorkingNow: Boolean = false,
    activeSessionCount: Int = 0,
    accountKey: String? = null,
    accountLabel: String? = null,
    lastSeenAt: Instant? = NOW
): TeamMemberPresence {
    return TeamMemberPresence(
        member = TeamMemberUsage(
            deviceId = deviceId,
            alias = alias,
            hostName = hostName,
            lastSeenAt = lastSeenAt,
            sessions = if (activeSessionCount > 0) List(activeSessionCount) { session("s$it") } else emptyList(),
            accountKey = accountKey,
            accountLabel = accountLabel
        ),
        isOnline = isOnline,
        isWorkingNow = isWorkingNow,
        activeSessionCount = activeSessionCount
    )
}

@OptIn(ExperimentalTestApi::class)
class TeamPresenceScreenTest {

    @Test
    fun `o cabecalho resume trabalhando conectados e conhecidos`() = runDesktopComposeUiTest {
        renderSuccess(
            TeamPresenceUiState.Success(
                entries = listOf(
                    entry(deviceId = "device-1", isWorkingNow = true, activeSessionCount = 2),
                    entry(deviceId = "device-2", alias = "maria"),
                    entry(deviceId = "device-3", alias = "joao", isOnline = false)
                )
            )
        )

        onNodeWithText("1 trabalhando").assertIsDisplayed()
        onNodeWithText("2 conectados").assertIsDisplayed()
        onNodeWithText("3 conhecidos").assertIsDisplayed()
    }

    @Test
    fun `a linha distingue conectado de desconectado`() = runDesktopComposeUiTest {
        renderSuccess(
            TeamPresenceUiState.Success(
                entries = listOf(
                    entry(deviceId = "device-on"),
                    entry(deviceId = "device-off", alias = "maria", isOnline = false)
                )
            )
        )

        onNodeWithTag("$PRESENCE_STATE_TAG_PREFIX" + "device-on")
            .assertTextContains("Conectado")
        onNodeWithTag("$PRESENCE_STATE_TAG_PREFIX" + "device-off")
            .assertTextContains("Desconectado")
    }

    @Test
    fun `quem nunca reportou aparece sem carimbo de sinal`() = runDesktopComposeUiTest {
        renderSuccess(
            TeamPresenceUiState.Success(
                entries = listOf(entry(isOnline = false, lastSeenAt = null))
            )
        )

        // O carimbo saiu da célula de estado e virou coluna própria; sem sinal
        // nenhum ela mostra o travessão. O estado continua dizendo a palavra.
        onNodeWithTag("$PRESENCE_STATE_TAG_PREFIX" + "device-1")
            .assertTextContains("Desconectado")
        onNodeWithTag("$PRESENCE_LAST_SEEN_TAG_PREFIX" + "device-1")
            .assertTextContains("—")
    }

    @Test
    fun `o selo de trabalhando agora conta as sessoes ativas`() = runDesktopComposeUiTest {
        renderSuccess(
            TeamPresenceUiState.Success(
                entries = listOf(
                    entry(deviceId = "device-working", isWorkingNow = true, activeSessionCount = 2),
                    entry(deviceId = "device-idle", alias = "maria")
                )
            )
        )

        // A coluna passou a ser "Sessões ativas" e carrega só o número: a palavra
        // "Parado" que existia ali virou o estado da coluna Estado, e a contagem
        // deixou de repetir a legenda em cada linha (issue #81).
        onNodeWithTag("$PRESENCE_WORKING_TAG_PREFIX" + "device-working")
            .assertTextContains("2")
        onNodeWithTag("$PRESENCE_WORKING_TAG_PREFIX" + "device-idle")
            .assertTextContains("0")
        onNodeWithTag("$PRESENCE_STATE_TAG_PREFIX" + "device-working")
            .assertTextContains("Trabalhando agora")
    }

    @Test
    fun `a maquina local ganha o selo`() = runDesktopComposeUiTest {
        renderSuccess(
            TeamPresenceUiState.Success(
                entries = listOf(
                    entry(deviceId = "device-1"),
                    entry(deviceId = "device-2", alias = "maria")
                )
            ),
            localDeviceId = "device-1"
        )

        onNodeWithTag("$PRESENCE_LOCAL_BADGE_TAG_PREFIX" + "device-1", useUnmergedTree = true)
            .assertIsDisplayed()
        onAllNodesWithTag("$PRESENCE_LOCAL_BADGE_TAG_PREFIX" + "device-2", useUnmergedTree = true)
            .assertCountEquals(0)
    }

    @Test
    fun `o filtro esconde quem esta desconectado`() = runDesktopComposeUiTest {
        renderSuccess(
            TeamPresenceUiState.Success(
                entries = listOf(
                    entry(deviceId = "device-on"),
                    entry(deviceId = "device-off", alias = "maria", isOnline = false)
                ),
                onlyOnline = true
            )
        )

        onNodeWithTag("$PRESENCE_ROW_TAG_PREFIX" + "device-on").assertIsDisplayed()
        onAllNodesWithTag("$PRESENCE_ROW_TAG_PREFIX" + "device-off").assertCountEquals(0)
    }

    @Test
    fun `o chip do filtro reflete o estado e emite o evento`() = runDesktopComposeUiTest {
        var requested: Boolean? = null
        renderSuccess(
            TeamPresenceUiState.Success(entries = listOf(entry()), onlyOnline = true),
            onSetOnlyOnline = { value -> requested = value }
        )

        onNodeWithTag(PRESENCE_ONLY_ONLINE_TAG).assertIsSelected()
        onNodeWithTag(PRESENCE_ONLY_ONLINE_TAG).performClick()

        assertEquals(false, requested)
    }

    @Test
    fun `filtro que esconde todo mundo tem mensagem propria`() = runDesktopComposeUiTest {
        renderSuccess(
            TeamPresenceUiState.Success(
                entries = listOf(entry(isOnline = false)),
                onlyOnline = true
            )
        )

        onNodeWithText("Ninguém conectado agora. Desligue o filtro para ver o time inteiro.")
            .assertIsDisplayed()
    }

    @Test
    fun `lista vazia explica que cada maquina reporta ao abrir o app`() = runDesktopComposeUiTest {
        renderSuccess(TeamPresenceUiState.Success(entries = emptyList()))

        onNodeWithText("Ninguém apareceu ainda. Cada máquina reporta ao abrir o app.")
            .assertIsDisplayed()
    }

    @Test
    fun `o erro nomeia a falha do servidor`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(820.dp).height(640.dp)) {
                    TeamPresenceContent(
                        state = TeamPresenceUiState.Error("chave invalida"),
                        language = AppLanguage.PT
                    )
                }
            }
        }

        onNodeWithText("Não foi possível ler a presença do time: chave invalida")
            .assertIsDisplayed()
    }

    @Test
    fun `o aviso de relogio so aparece quando ha suspeita`() = runDesktopComposeUiTest {
        renderSuccess(
            TeamPresenceUiState.Success(
                entries = listOf(entry()),
                clockSkewSuspected = true,
                clockSkewMinutes = 12L
            )
        )

        onNodeWithTag(PRESENCE_CLOCK_SKEW_TAG).assertTextContains(
            "Os relógios desta máquina e do servidor divergem em cerca de 12 min. " +
                "A coluna Estado pode estar errada."
        )
    }

    @Test
    fun `sem suspeita o aviso de relogio nao existe`() = runDesktopComposeUiTest {
        renderSuccess(TeamPresenceUiState.Success(entries = listOf(entry())))

        onAllNodesWithTag(PRESENCE_CLOCK_SKEW_TAG).assertCountEquals(0)
    }

    @Test
    fun `a visao global agrupa por conta e nasce recolhida`() = runDesktopComposeUiTest {
        renderSuccess(
            TeamPresenceUiState.Success(
                entries = listOf(
                    entry(deviceId = "device-1", accountKey = "acc-a", accountLabel = "time-a"),
                    entry(
                        deviceId = "device-2",
                        alias = "maria",
                        accountKey = "acc-b",
                        accountLabel = "time-b"
                    )
                ),
                isAdminOverview = true
            )
        )

        onNodeWithTag("${PRESENCE_ACCOUNT_GROUP_TAG_PREFIX}acc-a").assertIsDisplayed()
        onNodeWithTag("${PRESENCE_ACCOUNT_GROUP_TAG_PREFIX}acc-b").assertIsDisplayed()
        // Recolhida por padrão: a faixa é a lista inteira até alguém abrir.
        onAllNodesWithTag("${PRESENCE_ROW_TAG_PREFIX}acc-a/device-1").assertCountEquals(0)
    }

    @Test
    fun `o filtro de texto estreita a lista por apelido e por maquina`() = runDesktopComposeUiTest {
        var typed: String? = null
        renderSuccess(
            TeamPresenceUiState.Success(
                entries = listOf(
                    entry(deviceId = "device-1", alias = "edilson"),
                    entry(deviceId = "device-2", alias = "maria")
                )
            ),
            onQueryChange = { value -> typed = value }
        )

        onNodeWithTag(PRESENCE_FILTER_TAG).performTextInput("mar")

        assertEquals("mar", typed)
    }

    @Test
    fun `o filtro aplicado esconde quem nao casa`() = runDesktopComposeUiTest {
        renderSuccess(
            TeamPresenceUiState.Success(
                entries = listOf(
                    entry(deviceId = "device-1", alias = "edilson"),
                    entry(deviceId = "device-2", alias = "maria")
                ),
                // O texto casa contra a máquina também: quem digita "desktop"
                // está atrás dela, e filtrar só pelo apelido não acharia nada —
                // nenhum dos dois apelidos contém a palavra.
                query = "desktop"
            )
        )

        onNodeWithTag("$PRESENCE_ROW_TAG_PREFIX" + "device-1").assertIsDisplayed()
        onNodeWithTag("$PRESENCE_ROW_TAG_PREFIX" + "device-2").assertIsDisplayed()
    }

    @Test
    fun `filtro sem resultado explica o texto digitado`() = runDesktopComposeUiTest {
        renderSuccess(
            TeamPresenceUiState.Success(
                entries = listOf(entry(deviceId = "device-1", alias = "edilson")),
                query = "zzz"
            )
        )

        // Mensagem diferente da do chip "somente conectados": "desligue o filtro
        // para ver o time inteiro" não ajuda quem digitou um nome que não existe.
        onNodeWithText("Ninguém com \"zzz\" no apelido ou na máquina.").assertIsDisplayed()
    }

    @Test
    fun `a faixa da conta mostra quantos dela estao conectados`() = runDesktopComposeUiTest {
        renderSuccess(
            TeamPresenceUiState.Success(
                entries = listOf(
                    entry(deviceId = "device-1", accountKey = "acc-a", accountLabel = "time-a"),
                    entry(
                        deviceId = "device-2",
                        alias = "maria",
                        isOnline = false,
                        accountKey = "acc-a",
                        accountLabel = "time-a"
                    )
                ),
                isAdminOverview = true
            )
        )

        onNodeWithTag("${PRESENCE_ACCOUNT_GROUP_TAG_PREFIX}acc-a")
            .assertTextContains("1 de 2 conectados", substring = true)
    }

    @Test
    fun `conta provisoria nao exibe indicador na presenca`() =
        runDesktopComposeUiTest {
            renderSuccess(
                TeamPresenceUiState.Success(
                    entries = listOf(
                        entry(
                            deviceId = "device-1",
                            accountKey = "acc-a",
                            accountLabel = "fulano@empresa.com"
                        ),
                        entry(
                            deviceId = "device-2",
                            alias = "maria",
                            accountKey = "acc-b"
                        )
                    ),
                    isAdminOverview = true
                )
            )

            onAllNodesWithText("rótulo provisório").assertCountEquals(0)

            val provisionalBand = onNodeWithTag("${PRESENCE_ACCOUNT_GROUP_TAG_PREFIX}acc-a")
                .getUnclippedBoundsInRoot()
            val regularBand = onNodeWithTag("${PRESENCE_ACCOUNT_GROUP_TAG_PREFIX}acc-b")
                .getUnclippedBoundsInRoot()

            assertTrue(
                provisionalBand.bottom - provisionalBand.top == regularBand.bottom - regularBand.top,
                "remover o indicador não deveria aumentar a altura da faixa"
            )
            assertEquals(
                regularBand.right,
                provisionalBand.right,
                "as faixas devem preservar a mesma largura e as colunas"
            )
        }

    @Test
    fun `abrir a conta emite o evento com a chave dela`() = runDesktopComposeUiTest {
        var toggled: String? = null
        renderSuccess(
            TeamPresenceUiState.Success(
                entries = listOf(
                    entry(deviceId = "device-1", accountKey = "acc-a", accountLabel = "time-a")
                ),
                isAdminOverview = true
            ),
            onToggleAccount = { key -> toggled = key }
        )

        onNodeWithTag("${PRESENCE_ACCOUNT_GROUP_TAG_PREFIX}acc-a").performClick()

        assertEquals("acc-a", toggled)
    }

    @Test
    fun `conta expandida mostra os integrantes dela`() = runDesktopComposeUiTest {
        renderSuccess(
            TeamPresenceUiState.Success(
                entries = listOf(
                    entry(deviceId = "device-1", accountKey = "acc-a", accountLabel = "time-a")
                ),
                isAdminOverview = true,
                expandedAccountKeys = setOf("acc-a")
            )
        )

        onNodeWithTag("${PRESENCE_ROW_TAG_PREFIX}acc-a/device-1").assertIsDisplayed()
    }

    @Test
    fun `sem modo admin nenhum botao destrutivo aparece`() = runDesktopComposeUiTest {
        renderSuccess(
            TeamPresenceUiState.Success(
                entries = listOf(
                    entry(deviceId = "device-1", accountKey = "acc-a", accountLabel = "time-a")
                ),
                isAdminOverview = true,
                expandedAccountKeys = setOf("acc-a")
            ),
            localDeviceId = "device-outro"
        )

        onAllNodesWithTag("${PRESENCE_MEMBER_REMOVE_TAG_PREFIX}acc-a/device-1").assertCountEquals(0)
        onAllNodesWithTag("${PRESENCE_ACCOUNT_DELETE_TAG_PREFIX}acc-a").assertCountEquals(0)
    }

    @Test
    fun `esta maquina e a conta dela nao ganham botao`() = runDesktopComposeUiTest {
        renderSuccess(
            TeamPresenceUiState.Success(
                entries = listOf(
                    entry(deviceId = "device-1", accountKey = "acc-a", accountLabel = "time-a")
                ),
                isAdminOverview = true,
                expandedAccountKeys = setOf("acc-a")
            ),
            localDeviceId = "device-1",
            canManage = true
        )

        // O próximo envio recriaria as duas: o botão entregaria uma remoção que
        // se desfaz sozinha e apagaria o histórico local no caminho.
        onAllNodesWithTag("${PRESENCE_MEMBER_REMOVE_TAG_PREFIX}acc-a/device-1").assertCountEquals(0)
        onAllNodesWithTag("${PRESENCE_ACCOUNT_DELETE_TAG_PREFIX}acc-a").assertCountEquals(0)
    }

    @Test
    fun `remover integrante so age depois de confirmar`() = runDesktopComposeUiTest {
        var removed: String? = null
        renderSuccess(
            TeamPresenceUiState.Success(
                entries = listOf(
                    entry(deviceId = "device-1", accountKey = "acc-a", accountLabel = "time-a")
                ),
                isAdminOverview = true,
                expandedAccountKeys = setOf("acc-a")
            ),
            localDeviceId = "device-outro",
            canManage = true,
            onRemoveMember = { key -> removed = key }
        )

        onNodeWithTag("${PRESENCE_MEMBER_REMOVE_TAG_PREFIX}acc-a/device-1").performClick()
        assertEquals(null, removed)

        onNodeWithTag(PRESENCE_REMOVE_CONFIRM_TAG).performClick()
        assertEquals("acc-a/device-1", removed)
    }

    @Test
    fun `apagar conta so age depois de confirmar`() = runDesktopComposeUiTest {
        var deleted: String? = null
        renderSuccess(
            TeamPresenceUiState.Success(
                entries = listOf(
                    entry(deviceId = "device-1", accountKey = "acc-a", accountLabel = "time-a")
                ),
                isAdminOverview = true,
                expandedAccountKeys = setOf("acc-a")
            ),
            localDeviceId = "device-outro",
            canManage = true,
            onDeleteAccount = { key -> deleted = key }
        )

        onNodeWithTag("${PRESENCE_ACCOUNT_DELETE_TAG_PREFIX}acc-a").performClick()
        assertEquals(null, deleted)

        onNodeWithTag(PRESENCE_DELETE_ACCOUNT_CONFIRM_TAG).performClick()
        assertEquals("acc-a", deleted)
    }

    @Test
    fun `a falha da remocao aparece na tela`() = runDesktopComposeUiTest {
        renderSuccess(
            TeamPresenceUiState.Success(entries = listOf(entry())),
            actionError = "servidor fora do ar"
        )

        onNodeWithTag(PRESENCE_ACTION_ERROR_TAG)
            .assertTextContains("Não foi possível concluir a remoção: servidor fora do ar")
    }

    /**
     * As legendas de coluna vivem numa faixa só, e não dentro de cada linha.
     *
     * Antes cada integrante reimprimia "Máquina", "Estado" e "Trabalhando agora"
     * ao lado do próprio valor: o texto da lista dobrava e o ruído crescia com o
     * tamanho do time. A contagem exata é o que impede a legenda de voltar para
     * dentro da linha sem alguém perceber.
     */
    @Test
    fun `as legendas de coluna aparecem uma vez para a lista inteira`() = runDesktopComposeUiTest {
        renderSuccess(
            TeamPresenceUiState.Success(
                entries = listOf(
                    entry(deviceId = "device-1", isWorkingNow = true, activeSessionCount = 1),
                    entry(deviceId = "device-2", alias = "maria"),
                    entry(deviceId = "device-3", alias = "joao", isOnline = false)
                )
            )
        )

        onNodeWithTag(PRESENCE_COLUMN_HEADER_TAG).assertIsDisplayed()
        for (label in listOf("Integrante", "Máquina", "Estado", "Trabalhando agora")) {
            onAllNodesWithText(label, useUnmergedTree = true).assertCountEquals(1)
        }
    }

    /**
     * A coluna de status some do cabeçalho quando ninguém tem veredito — do
     * contrário a faixa prometeria uma coluna que nenhuma linha preenche.
     */
    @Test
    fun `sem veredito a coluna de status nao entra no cabecalho`() = runDesktopComposeUiTest {
        renderSuccess(
            TeamPresenceUiState.Success(entries = listOf(entry(activeSessionCount = 0)))
        )

        onAllNodesWithText("Status", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun `a lista tem barra de rolagem`() = runDesktopComposeUiTest {
        renderSuccess(TeamPresenceUiState.Success(entries = listOf(entry())))

        onNodeWithTag(PRESENCE_LIST_SCROLLBAR_TAG).assertIsDisplayed()
    }

    /**
     * Prova de que a tela não tem animação infinita.
     *
     * O `waitForIdle` só retorna quando não há mais trabalho pendente de
     * composição — uma transição sem fim numa linha de lista o travaria, e o
     * sintoma seria a suíte inteira pendurando em vez de falhar.
     */
    @Test
    fun `a tela alcanca o estado ocioso`() = runDesktopComposeUiTest {
        renderSuccess(
            TeamPresenceUiState.Success(
                entries = listOf(
                    entry(deviceId = "device-1", isWorkingNow = true, activeSessionCount = 1),
                    entry(deviceId = "device-2", alias = "maria", isOnline = false)
                )
            )
        )

        waitForIdle()
    }

    @Test
    fun `a faixa da conta diz que aquele nivel e uma conta`() = runDesktopComposeUiTest {
        renderSuccess(
            TeamPresenceUiState.Success(
                entries = listOf(
                    entry(deviceId = "device-1", accountKey = "acc-a", accountLabel = "time-a")
                ),
                isAdminOverview = true
            )
        )

        // Sem a palavra, a faixa entregava um e-mail e um uuid — exatamente o que
        // a linha do integrante também tem — e os dois níveis liam igual.
        onNodeWithTag("${PRESENCE_ACCOUNT_GROUP_TAG_PREFIX}acc-a").assertTextContains("Conta")
    }

    @Test
    fun `em janela estreita o botao de apagar continua na coluna de acao`() = runDesktopComposeUiTest {
        renderSuccess(
            TeamPresenceUiState.Success(
                entries = listOf(
                    entry(deviceId = "device-1", accountKey = "acc-a", accountLabel = "time-a")
                ),
                isAdminOverview = true,
                expandedAccountKeys = setOf("acc-a")
            ),
            localDeviceId = "device-outro",
            canManage = true,
            // Largura das capturas do defeito: abaixo do orçamento das colunas, que
            // é justamente onde o `FlowRow` quebra.
            widthDp = 800
        )

        // Dentro do `FlowRow` a ação era o último item e o primeiro a quebrar: o
        // botão descia para uma linha própria e aparecia colado à esquerda, um ícone
        // vermelho solto abaixo do e-mail. Fora dele a coluna de ação é fixa e o
        // botão encosta na borda direita da faixa, quebrem as colunas ou não.
        val button = onNodeWithTag("${PRESENCE_ACCOUNT_DELETE_TAG_PREFIX}acc-a")
            .getUnclippedBoundsInRoot()
        val band = onNodeWithTag("${PRESENCE_ACCOUNT_GROUP_TAG_PREFIX}acc-a")
            .getUnclippedBoundsInRoot()

        assertTrue(
            (band.right - button.right).value < 20f,
            "botão termina em ${button.right}, faixa em ${band.right}"
        )
    }

    private fun ComposeUiTest.renderSuccess(
        state: TeamPresenceUiState.Success,
        localDeviceId: String? = null,
        canManage: Boolean = false,
        actionError: String? = null,
        /** A quebra de coluna só se mede na largura em que a janela real para. */
        widthDp: Int = TEAM_PRESENCE_MIN_WINDOW_WIDTH_DP,
        onToggleAccount: (String) -> Unit = {},
        onSetOnlyOnline: (Boolean) -> Unit = {},
        onQueryChange: (String) -> Unit = {},
        onRemoveMember: (String) -> Unit = {},
        onDeleteAccount: (String) -> Unit = {}
    ) {
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(widthDp.dp).height(700.dp)) {
                    TeamPresenceContent(
                        state = state,
                        language = AppLanguage.PT,
                        localDeviceId = localDeviceId,
                        canManage = canManage,
                        actionError = actionError,
                        onToggleAccount = onToggleAccount,
                        onSetOnlyOnline = onSetOnlyOnline,
                        onQueryChange = onQueryChange,
                        onRemoveMember = onRemoveMember,
                        onDeleteAccount = onDeleteAccount
                    )
                }
            }
        }
    }
}
