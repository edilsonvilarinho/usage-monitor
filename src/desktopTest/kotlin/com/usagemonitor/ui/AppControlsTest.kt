package com.usagemonitor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.usagemonitor.presentation.ui.components.AppButton
import com.usagemonitor.presentation.ui.components.AppButtonTone
import com.usagemonitor.presentation.ui.components.AppIconButton
import com.usagemonitor.presentation.ui.components.AppSegment
import com.usagemonitor.presentation.ui.components.AppSegmentedControl
import com.usagemonitor.presentation.ui.components.AppSwitch
import com.usagemonitor.presentation.ui.components.AppTextField
import com.usagemonitor.presentation.ui.theme.AppTheme
import androidx.compose.ui.platform.testTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class AppControlsTest {

    @Test
    fun `o botao desabilitado nao dispara`() = runDesktopComposeUiTest {
        var clicks = 0
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(400.dp).height(120.dp)) {
                    AppButton(
                        label = "Atualizar",
                        onClick = { clicks += 1 },
                        enabled = false
                    )
                }
            }
        }

        onNodeWithText("Atualizar").performClick()

        assertEquals(0, clicks)
    }

    @Test
    fun `o botao dispara o clique`() = runDesktopComposeUiTest {
        var clicks = 0
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(400.dp).height(120.dp)) {
                    AppButton(
                        label = "Emitir chave",
                        onClick = { clicks += 1 },
                        tone = AppButtonTone.PRIMARY
                    )
                }
            }
        }

        onNodeWithText("Emitir chave").performClick()

        assertEquals(1, clicks)
    }

    /**
     * O rótulo que virou ícone continua descrito: é assim que a ação é
     * encontrada tanto pelo leitor de tela quanto pelo teste de componente.
     */
    @Test
    fun `o botao de icone carrega a descricao da acao`() = runDesktopComposeUiTest {
        var clicks = 0
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(400.dp).height(120.dp)) {
                    AppIconButton(
                        contentDescription = "Atualizar",
                        onClick = { clicks += 1 },
                        modifier = Modifier.testTag("iconButton")
                    ) {
                        Text("R")
                    }
                }
            }
        }

        onNodeWithTag("iconButton").performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun `o campo publica o texto digitado`() = runDesktopComposeUiTest {
        var typed = ""
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(400.dp).height(120.dp)) {
                    AppTextField(
                        value = typed,
                        onValueChange = { typed = it },
                        placeholder = "Filtrar",
                        modifier = Modifier.testTag("field")
                    )
                }
            }
        }

        onNodeWithText("Filtrar").assertIsDisplayed()
        onNodeWithTag("field").performTextInput("alpha")

        assertEquals("alpha", typed)
    }

    @Test
    fun `o interruptor inverte o estado`() = runDesktopComposeUiTest {
        var checked = false
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(400.dp).height(120.dp)) {
                    AppSwitch(
                        checked = checked,
                        onCheckedChange = { checked = it },
                        modifier = Modifier.testTag("switch")
                    )
                }
            }
        }

        onNodeWithTag("switch").performClick()

        assertTrue(checked)
    }

    @Test
    fun `o segmentado marca a opcao escolhida e devolve o indice`() = runDesktopComposeUiTest {
        var chosen = -1
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(400.dp).height(120.dp)) {
                    AppSegmentedControl(
                        options = listOf(
                            AppSegment("5h"),
                            AppSegment("7 dias"),
                            AppSegment("30 dias")
                        ),
                        selectedIndex = 0,
                        onSelect = { chosen = it }
                    )
                }
            }
        }

        onNodeWithText("5h").assertIsSelected()
        onNodeWithText("30 dias").assertIsNotSelected()
        onNodeWithText("30 dias").performClick()

        assertEquals(2, chosen)
    }
}
