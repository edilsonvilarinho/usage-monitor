package com.usagemonitor.presentation.terminal

import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.CollectorKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Ramos dos textos do serviço sem tela e do `config` que os comandos não exercitam. */
class TerminalServiceAndConfigLabelsTest {

    private val servicePt = TerminalServiceLabels(AppLanguage.PT)
    private val serviceEn = TerminalServiceLabels(AppLanguage.EN)
    private val configPt = TerminalConfigLabels(AppLanguage.PT)
    private val configEn = TerminalConfigLabels(AppLanguage.EN)

    @Test
    fun `service says who is already collecting`() {
        assertTrue(serviceEn.alreadyCollecting(CollectorKind.APP).startsWith("The app is already collecting"))
        assertTrue(servicePt.alreadyCollecting(CollectorKind.APP).endsWith("feche o app para rodar o serviço."))
        assertEquals("The service is already collecting in this data folder.", serviceEn.alreadyCollecting(CollectorKind.SERVICE))
        assertEquals("O serviço já está coletando nesta pasta de dados.", servicePt.alreadyCollecting(CollectorKind.SERVICE))
    }

    @Test
    fun `service lifecycle messages`() {
        assertEquals(
            "Usage Monitor 42.0.0 collecting without a window · data in /home/a/.usage-monitor · stop with Ctrl+C or SIGTERM",
            serviceEn.started("42.0.0", "/home/a/.usage-monitor")
        )
        assertEquals("Collection stopped.", serviceEn.stopped)
        assertEquals("Coleta encerrada.", servicePt.stopped)
        assertTrue(serviceEn.noDisplay.contains("usage-monitor service run"))
        assertTrue(servicePt.noDisplay.startsWith("Este computador não tem tela"))
        assertEquals("The app is open and collecting: the service takes over when it closes.", serviceEn.appCollecting)
        assertEquals("service removed", serviceEn.removed)
        assertEquals("serviço removido", servicePt.removed)
        assertEquals("service started", serviceEn.actionDone("start"))
        assertEquals("serviço parado", servicePt.actionDone("stop"))
        assertEquals("service restarted", serviceEn.actionDone("restart"))
        assertEquals("systemd refused: boom", serviceEn.actionFailed("boom"))
    }

    @Test
    fun `systemd failure keeps the detail only when there is one`() {
        assertEquals(
            "The user systemd did not answer (systemctl --user: no bus). In a session without a graphical login, it needs linger:",
            serviceEn.noSystemd("no bus")
        )
        assertEquals(
            "O systemd do usuário não respondeu (systemctl --user). Numa sessão sem login gráfico, ele precisa do linger:",
            servicePt.noSystemd("  ")
        )
    }

    @Test
    fun `config says where a change applies`() {
        assertEquals("the app is already using it", configEn.appliedBy(CollectorKind.APP))
        assertEquals("o app já está usando", configPt.appliedBy(CollectorKind.APP))
        assertEquals("applies after restarting the app", configEn.restartNeeded(CollectorKind.APP))
        assertEquals("vale depois de reiniciar o app", configPt.restartNeeded(CollectorKind.APP))
        assertEquals("Could not save: disco cheio", configEn.failed("disco cheio"))
        assertEquals("Não foi possível gravar.", configPt.failed(null))
        assertEquals("there is no source 'x'", configEn.unknownSource("x"))
        assertEquals("saved", configEn.keySaved)
        assertEquals("(empty)", configEn.empty)
        assertEquals("(vazio)", configPt.empty)
        assertEquals("no setting starts with 'hud.'", configEn.noSettingIn("hud."))
        assertEquals("by the source", configEn.bySource)
        assertEquals(
            "'work' is the label of more than one account. Use the id: a, b",
            configEn.ambiguousAccount("work", "a, b")
        )
        assertEquals(
            "'work' é o apelido de mais de uma conta. Use o id: a, b",
            configPt.ambiguousAccount("work", "a, b")
        )
    }

    @Test
    fun `every value type has its own expected text in both languages`() {
        val types = listOf(
            "BOOLEAN", "PERCENTS", "MINUTES", "MINUTES_OR_OFF", "FACTOR", "QUIET_HOURS",
            "HOUR_OR_OFF", "USD_OR_OFF", "LANGUAGE", "PORT", "CLEAR_ONLY", "TEXT"
        )
        for (labels in listOf(configPt, configEn)) {
            val texts = types.map(labels::expected)
            assertEquals(types.size, texts.toSet().size, "textos repetidos em ${labels.language}")
        }
        assertEquals("pt or en", configEn.expected("LANGUAGE"))
        assertEquals("a port from 1 to 65535, or off", configEn.expected("PORT"))
        assertEquals("um texto", configPt.expected("QUALQUER"))
        assertEquals("'lang' does not take that value. Use pt or en.", configEn.invalidValue("lang", configEn.expected("LANGUAGE")))
    }
}
