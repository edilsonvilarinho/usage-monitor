package com.usagemonitor.update

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.russhwolf.settings.PreferencesSettings
import com.usagemonitor.domain.entity.AppUpdateInfo
import com.usagemonitor.domain.entity.AppUpdateReceipt
import com.usagemonitor.domain.entity.AppUpdateReceiptStatus
import com.usagemonitor.domain.entity.ReleaseNotes
import com.usagemonitor.domain.repository.AppUpdateRepository
import com.usagemonitor.domain.usecase.GetReleaseNotesUseCase
import com.usagemonitor.persistReleaseNotesSeenVersion
import com.usagemonitor.readPersistedReleaseNotesSeenVersion
import java.util.UUID
import java.util.prefs.Preferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * A costura entre a decisão, a busca e a marca.
 *
 * Existe porque a issue #127 foi um defeito **daqui**, e não das funções puras:
 * elas estavam certas para o contrato que descreviam, e o que estava errado era
 * o sinal que o controlador lia. Um caso a mais de decisão não teria pego isso.
 *
 * Os casos que não vão à rede afirmam **zero chamadas** ao repositório: é o
 * contador, e não o estado da janela, que prova que o ramo silencioso não gasta
 * requisição.
 */
@OptIn(ExperimentalTestApi::class)
class ReleaseNotesControllerTest {

    @Test
    fun `it opens without any receipt`() {
        // A regressão da issue #127: instalação manual, macOS, ou o Linux antes
        // de o linux-updater.sh gravar o arquivo.
        val repository = FakeAppUpdateRepository(notesWithItems())

        withController(seenVersion = "38.0.1", receipt = null, repository = repository) { controller, settings ->
            assertNotNull(controller.notes)
            assertEquals("38.0.2", readPersistedReleaseNotesSeenVersion(settings))
            assertEquals(1, repository.calls)
        }
    }

    @Test
    fun `a stale receipt does not suppress the window`() {
        // O estado real do Linux na primeira abertura depois da troca: o recibo
        // ainda descreve a atualização anterior, porque o script só o grava
        // depois do ACK que este processo escreve.
        val repository = FakeAppUpdateRepository(notesWithItems())
        val stale = receipt(version = "38.0.1", previousVersion = "38.0.0")

        withController(seenVersion = "38.0.1", receipt = stale, repository = repository) { controller, _ ->
            assertNotNull(controller.notes)
        }
    }

    @Test
    fun `a fresh install marks without asking the network`() {
        val repository = FakeAppUpdateRepository(notesWithItems())

        withController(seenVersion = null, receipt = null, repository = repository) { controller, settings ->
            assertNull(controller.notes)
            assertEquals("38.0.2", readPersistedReleaseNotesSeenVersion(settings))
            assertEquals(0, repository.calls)
        }
    }

    @Test
    fun `a missing mark with a receipt on disk still opens`() {
        val repository = FakeAppUpdateRepository(notesWithItems())
        val anyReceipt = receipt(version = "38.0.1", previousVersion = "38.0.0")

        withController(seenVersion = null, receipt = anyReceipt, repository = repository) { controller, _ ->
            assertNotNull(controller.notes)
        }
    }

    @Test
    fun `a rollback re-marks without asking the network`() {
        val repository = FakeAppUpdateRepository(notesWithItems())

        withController(seenVersion = "39.0.0", receipt = null, repository = repository) { controller, settings ->
            assertNull(controller.notes)
            assertEquals("38.0.2", readPersistedReleaseNotesSeenVersion(settings))
            assertEquals(0, repository.calls)
        }
    }

    @Test
    fun `the same version asks nothing and writes nothing`() {
        val repository = FakeAppUpdateRepository(notesWithItems())

        withController(seenVersion = "38.0.2", receipt = null, repository = repository) { controller, settings ->
            assertNull(controller.notes)
            assertEquals("38.0.2", readPersistedReleaseNotesSeenVersion(settings))
            assertEquals(0, repository.calls)
        }
    }

    @Test
    fun `a release with nothing to show still marks`() {
        // Retentar a cada abertura seria requisição perpétua por uma resposta
        // que não vai mudar.
        val repository = FakeAppUpdateRepository(Result.success(null))

        withController(seenVersion = "38.0.1", receipt = null, repository = repository) { controller, settings ->
            assertNull(controller.notes)
            assertEquals("38.0.2", readPersistedReleaseNotesSeenVersion(settings))
            assertEquals(1, repository.calls)
        }
    }

    @Test
    fun `a network failure does not mark`() {
        // A espera acabou sem resposta: a abertura seguinte tenta de novo.
        val repository = FakeAppUpdateRepository(Result.failure(IllegalStateException("GitHub HTTP 503")))

        withController(seenVersion = "38.0.1", receipt = null, repository = repository) { controller, settings ->
            assertNull(controller.notes)
            assertEquals("38.0.1", readPersistedReleaseNotesSeenVersion(settings))
        }
    }

    @Test
    fun `the receipt names the previous version on the windows path`() {
        val repository = FakeAppUpdateRepository(notesWithItems())
        val applied = receipt(version = "38.0.2", previousVersion = "36.0.0")

        withController(seenVersion = "38.0.1", receipt = applied, repository = repository) { _, _ ->
            assertEquals("36.0.0", repository.lastPreviousVersion)
        }
    }

    @Test
    fun `the mark names the previous version when the receipt is stale`() {
        val repository = FakeAppUpdateRepository(notesWithItems())
        val stale = receipt(version = "38.0.1", previousVersion = "38.0.0")

        withController(seenVersion = "38.0.1", receipt = stale, repository = repository) { _, _ ->
            assertEquals("38.0.1", repository.lastPreviousVersion)
        }
    }

    /**
     * Monta o controlador numa composição descartável e espera o
     * `LaunchedEffect` terminar.
     *
     * O nó de preferências é aleatório e removido no `finally`, como nos demais
     * testes de preferência do projeto: a suíte não pode sujar o registro de
     * quem a roda.
     */
    private fun withController(
        seenVersion: String?,
        receipt: AppUpdateReceipt?,
        repository: FakeAppUpdateRepository,
        currentVersion: String = "38.0.2",
        assertions: (ReleaseNotesController, PreferencesSettings) -> Unit
    ) {
        val nodeName = "com.usagemonitor.tests.${UUID.randomUUID()}"
        val preferencesNode = Preferences.userRoot().node(nodeName)
        try {
            val settings = PreferencesSettings(preferencesNode)
            if (seenVersion != null) {
                persistReleaseNotesSeenVersion(settings, seenVersion)
            }

            var controller: ReleaseNotesController? = null
            runDesktopComposeUiTest {
                setContent {
                    controller = rememberReleaseNotesController(
                        settings = settings,
                        getReleaseNotes = GetReleaseNotesUseCase(repository),
                        receipt = receipt,
                        currentVersion = currentVersion
                    )
                }
                waitForIdle()
            }

            assertions(assertNotNull(controller), settings)
        } finally {
            runCatching {
                preferencesNode.removeNode()
                preferencesNode.flush()
            }
        }
    }

    private fun notesWithItems() = Result.success(
        ReleaseNotes(
            version = "38.0.2",
            previousVersion = null,
            publishedAt = null,
            releasePageUrl = "https://github.com/edilsonvilarinho/usage-monitor/releases/tag/v38.0.2",
            items = listOf("ship linux auto update after real bazzite acceptance")
        )
    )

    private fun receipt(version: String, previousVersion: String?) = AppUpdateReceipt(
        version = version,
        previousVersion = previousVersion,
        status = AppUpdateReceiptStatus.SUCCESS,
        reason = null
    )

    /**
     * Conta as chamadas e guarda a versão anterior recebida — as duas coisas que
     * os testes afirmam e que nenhum estado da janela revela.
     */
    private class FakeAppUpdateRepository(
        private val notes: Result<ReleaseNotes?>
    ) : AppUpdateRepository {

        var calls = 0
            private set

        var lastPreviousVersion: String? = null
            private set

        override suspend fun getLatestAvailableUpdate(currentVersion: String): Result<AppUpdateInfo?> {
            throw UnsupportedOperationException("Não utilizado neste teste")
        }

        override suspend fun getReleaseNotes(
            version: String,
            previousVersion: String?
        ): Result<ReleaseNotes?> {
            calls += 1
            lastPreviousVersion = previousVersion
            return notes
        }
    }
}
