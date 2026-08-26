package com.usagemonitor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.AppUpdatePlatform
import com.usagemonitor.domain.entity.AppUpdateReceipt
import com.usagemonitor.domain.entity.AppUpdateReceiptStatus
import com.usagemonitor.domain.repository.AppUpdateSupport
import com.usagemonitor.presentation.ui.components.AUTO_UPDATE_FEED_OVERRIDE_TEST_TAG
import com.usagemonitor.presentation.ui.components.AUTO_UPDATE_RECEIPT_TEST_TAG
import com.usagemonitor.presentation.ui.components.AUTO_UPDATE_SWITCH_TEST_TAG
import com.usagemonitor.presentation.ui.components.AUTO_UPDATE_TEXT_BLOCK_TEST_TAG
import com.usagemonitor.presentation.ui.components.AutoUpdateToggle
import com.usagemonitor.presentation.ui.theme.AppTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class AutoUpdateToggleTest {

    @Test
    fun `switch ocupa o slot direito e todos os textos ficam no bloco esquerdo`() = runDesktopComposeUiTest {
        showToggle(
            enabled = true,
            support = AppUpdateSupport.SUPPORTED,
            receipt = AppUpdateReceipt(
                version = "39.0.0",
                previousVersion = "38.0.0",
                status = AppUpdateReceiptStatus.SUCCESS,
                reason = null
            ),
            feedOverride = "http://localhost:8099/release.json"
        )

        val textBlock = onNodeWithTag(AUTO_UPDATE_TEXT_BLOCK_TEST_TAG).getUnclippedBoundsInRoot()
        val switch = onNodeWithTag(AUTO_UPDATE_SWITCH_TEST_TAG).getUnclippedBoundsInRoot()
        assertTrue(switch.left > textBlock.left)
        assertTrue(switch.left >= textBlock.right)

        val titleLeft = onNodeWithText("Atualização automática").getUnclippedBoundsInRoot().left
        val hintLeft = onNodeWithText(
            "Baixa a versão nova em segundo plano (~120 MB) e a aplica ao fechar o app."
        ).getUnclippedBoundsInRoot().left
        val receiptLeft = onNodeWithTag(AUTO_UPDATE_RECEIPT_TEST_TAG).getUnclippedBoundsInRoot().left
        val warningLeft = onNodeWithTag(AUTO_UPDATE_FEED_OVERRIDE_TEST_TAG).getUnclippedBoundsInRoot().left
        assertEquals(titleLeft, hintLeft)
        assertEquals(titleLeft, receiptLeft)
        assertEquals(titleLeft, warningLeft)
    }

    @Test
    fun `supported install says the size and the moment`() = runDesktopComposeUiTest {
        showToggle(enabled = false, support = AppUpdateSupport.SUPPORTED)

        onNodeWithText("Atualização automática").assertIsDisplayed()
        // Os dois números surpreendem, e o interruptor que não os diz liga uma
        // coisa que o usuário não escolheu.
        onNodeWithText(
            "Baixa a versão nova em segundo plano (~120 MB) e a aplica ao fechar o app."
        ).assertIsDisplayed()
        onNodeWithTag(AUTO_UPDATE_SWITCH_TEST_TAG).assertIsEnabled().assertIsOff()
    }

    @Test
    fun `toggling reports the new value`() = runDesktopComposeUiTest {
        var lastValue: Boolean? = null
        showToggle(
            enabled = false,
            support = AppUpdateSupport.SUPPORTED,
            onToggle = { value -> lastValue = value }
        )

        onNodeWithTag(AUTO_UPDATE_SWITCH_TEST_TAG).performClick()

        assertEquals(true, lastValue)
    }

    @Test
    fun `unsupported platform names macOS instead of blaming linux`() = runDesktopComposeUiTest {
        showToggle(
            enabled = false,
            support = AppUpdateSupport.UNSUPPORTED_PLATFORM,
            platform = AppUpdatePlatform.MACOS
        )

        onNodeWithTag(AUTO_UPDATE_SWITCH_TEST_TAG).assertIsNotEnabled()
        onNodeWithText(
            "Não disponível no macOS: o pacote não é assinado e o Gatekeeper exige liberação manual."
        ).assertIsDisplayed()
    }

    /**
     * Plataforma que o app não reconhece não pode nomear instalador nenhum: o
     * texto genérico é o que sobra de verdadeiro.
     */
    @Test
    fun `unknown platform falls back to the generic reason`() = runDesktopComposeUiTest {
        showToggle(
            enabled = false,
            support = AppUpdateSupport.UNSUPPORTED_PLATFORM,
            platform = null
        )

        onNodeWithText(
            "Não disponível nesta plataforma. A atualização automática cobre Windows e Linux em user-space."
        ).assertIsDisplayed()
    }

    @Test
    fun `unsupported install origin explains the second install risk`() = runDesktopComposeUiTest {
        showToggle(
            enabled = false,
            support = AppUpdateSupport.UNSUPPORTED_INSTALL_ORIGIN,
            platform = AppUpdatePlatform.WINDOWS
        )

        onNodeWithTag(AUTO_UPDATE_SWITCH_TEST_TAG).assertIsNotEnabled()
        onNodeWithText(
            "Disponível apenas na instalação feita pelo instalador .exe. Esta cópia veio do MSI " +
                "ou de fora dele, e atualizá-la por aqui criaria uma segunda instalação."
        ).assertIsDisplayed()
    }

    /**
     * O mesmo valor de suporte, outra plataforma, outro texto. É por isto que
     * `autoUpdateHint` passou a receber a plataforma: no Linux o instalador é o
     * `.sh` e o risco é mexer em arquivo do gerenciador de pacotes, não criar
     * uma segunda instalação.
     */
    @Test
    fun `unsupported install origin on linux names the sh installer`() = runDesktopComposeUiTest {
        showToggle(
            enabled = false,
            support = AppUpdateSupport.UNSUPPORTED_INSTALL_ORIGIN,
            platform = AppUpdatePlatform.LINUX
        )

        onNodeWithTag(AUTO_UPDATE_SWITCH_TEST_TAG).assertIsNotEnabled()
        onNodeWithText(
            "Disponível apenas na instalação em user-space feita pelo instalador .sh. Esta cópia " +
                "veio de um pacote .deb/.rpm ou de fora dele, e atualizá-la por aqui mexeria em " +
                "arquivos do gerenciador de pacotes."
        ).assertIsDisplayed()
    }

    /**
     * ARM64 não é "plataforma sem suporte": a plataforma tem suporte e o pacote
     * é que não existe. Sem valor próprio no enum, a tela diria a razão errada.
     */
    @Test
    fun `unsupported architecture says the package is missing, not the platform`() =
        runDesktopComposeUiTest {
            showToggle(
                enabled = false,
                support = AppUpdateSupport.UNSUPPORTED_ARCHITECTURE,
                platform = AppUpdatePlatform.LINUX
            )

            onNodeWithTag(AUTO_UPDATE_SWITCH_TEST_TAG).assertIsNotEnabled()
            onNodeWithText(
                "Não há pacote publicado para a arquitetura desta máquina. A atualização " +
                    "automática cobre apenas x86_64."
            ).assertIsDisplayed()
        }

    @Test
    fun `build without the mechanism says so instead of promising`() = runDesktopComposeUiTest {
        showToggle(enabled = false, support = AppUpdateSupport.UNAVAILABLE)

        onNodeWithTag(AUTO_UPDATE_SWITCH_TEST_TAG).assertIsNotEnabled()
        onNodeWithText("Esta versão do aplicativo ainda não traz a atualização automática.")
            .assertIsDisplayed()
    }

    /**
     * Ligado-mas-inerte seria uma promessa falsa: a preferência guardada pode
     * dizer `true` numa máquina onde a atualização automática não roda.
     */
    @Test
    fun `stored true shows off when the install is not supported`() = runDesktopComposeUiTest {
        showToggle(enabled = true, support = AppUpdateSupport.UNSUPPORTED_INSTALL_ORIGIN)

        onNodeWithTag(AUTO_UPDATE_SWITCH_TEST_TAG).assertIsOff()
    }

    @Test
    fun `stored true shows on when the install is supported`() = runDesktopComposeUiTest {
        showToggle(enabled = true, support = AppUpdateSupport.SUPPORTED)

        onNodeWithTag(AUTO_UPDATE_SWITCH_TEST_TAG).assertIsOn()
    }

    @Test
    fun `no receipt means no receipt line`() = runDesktopComposeUiTest {
        showToggle(enabled = true, support = AppUpdateSupport.SUPPORTED, receipt = null)

        onNodeWithTag(AUTO_UPDATE_RECEIPT_TEST_TAG).assertDoesNotExist()
    }

    @Test
    fun `a successful receipt names both versions`() = runDesktopComposeUiTest {
        showToggle(
            enabled = true,
            support = AppUpdateSupport.SUPPORTED,
            receipt = AppUpdateReceipt(
                version = "38.0.0",
                previousVersion = "37.0.0",
                status = AppUpdateReceiptStatus.SUCCESS,
                reason = null
            )
        )

        onNodeWithText("Última atualização: 37.0.0 → 38.0.0, concluída.").assertIsDisplayed()
    }

    /**
     * Atualização automática que falha é invisível por natureza: o app volta na
     * versão antiga sem nada dizer. Esta linha é o rastro.
     */
    @Test
    fun `a failed receipt says the installed version was left untouched`() = runDesktopComposeUiTest {
        showToggle(
            enabled = true,
            support = AppUpdateSupport.SUPPORTED,
            receipt = AppUpdateReceipt(
                version = "38.0.0",
                previousVersion = "37.0.0",
                status = AppUpdateReceiptStatus.FAILED,
                reason = "locked"
            )
        )

        onNodeWithText(
            "Última atualização: 37.0.0 → 38.0.0 falhou (locked). A versão instalada não foi alterada."
        ).assertIsDisplayed()
    }

    /**
     * O aviso existe para ninguem rodar com o feed trocado sem perceber: o
     * SHA-256 que barra artefato adulterado vem do mesmo feed.
     */
    @Test
    fun `an overridden release feed is announced on screen`() = runDesktopComposeUiTest {
        showToggle(
            enabled = true,
            support = AppUpdateSupport.SUPPORTED,
            feedOverride = "http://localhost:8099/release.json"
        )

        onNodeWithTag(AUTO_UPDATE_FEED_OVERRIDE_TEST_TAG).assertIsDisplayed()
        onNodeWithText(
            "Aviso: o feed de releases está sobrescrito por USAGE_MONITOR_UPDATE_FEED_URL " +
                "(http://localhost:8099/release.json). Só para teste."
        ).assertIsDisplayed()
    }

    @Test
    fun `no override means no warning`() = runDesktopComposeUiTest {
        showToggle(enabled = true, support = AppUpdateSupport.SUPPORTED, feedOverride = null)

        onNodeWithTag(AUTO_UPDATE_FEED_OVERRIDE_TEST_TAG).assertDoesNotExist()
    }

    @Test
    fun `english translates the hint and the receipt`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(560.dp)) {
                    AutoUpdateToggle(
                        enabled = true,
                        support = AppUpdateSupport.SUPPORTED,
                        platform = AppUpdatePlatform.WINDOWS,
                        language = AppLanguage.EN,
                        lastReceipt = AppUpdateReceipt(
                            version = "38.0.0",
                            previousVersion = null,
                            status = AppUpdateReceiptStatus.SUCCESS,
                            reason = null
                        ),
                        onToggle = {}
                    )
                }
            }
        }

        onNodeWithText(
            "Downloads the new version in the background (~120 MB) and applies it on exit."
        ).assertIsDisplayed()
        onNodeWithText("Last update: 38.0.0, completed.").assertIsDisplayed()
    }

    private fun androidx.compose.ui.test.ComposeUiTest.showToggle(
        enabled: Boolean,
        support: AppUpdateSupport,
        platform: AppUpdatePlatform? = AppUpdatePlatform.WINDOWS,
        receipt: AppUpdateReceipt? = null,
        feedOverride: String? = null,
        onToggle: (Boolean) -> Unit = {}
    ) {
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(560.dp)) {
                    AutoUpdateToggle(
                        enabled = enabled,
                        support = support,
                        platform = platform,
                        language = AppLanguage.PT,
                        lastReceipt = receipt,
                        feedUrlOverride = feedOverride,
                        onToggle = onToggle
                    )
                }
            }
        }
    }
}
