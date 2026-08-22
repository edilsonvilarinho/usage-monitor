package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.AppUpdateInfo
import com.usagemonitor.domain.repository.AnthropicRepository
import com.usagemonitor.domain.repository.AppUpdateInstaller
import com.usagemonitor.domain.repository.AppUpdatePreparation
import com.usagemonitor.domain.repository.AppUpdateSupport
import com.usagemonitor.domain.repository.CodexRepository
import com.usagemonitor.domain.repository.DeepSeekRepository
import com.usagemonitor.domain.repository.MiniMaxRepository
import com.usagemonitor.domain.usecase.GetAnthropicUsageUseCase
import com.usagemonitor.domain.usecase.GetCodexUsageUseCase
import com.usagemonitor.domain.usecase.GetDeepSeekUsageUseCase
import com.usagemonitor.domain.usecase.GetMiniMaxUsageUseCase
import com.usagemonitor.presentation.viewmodel.AppUpdateFailureReason
import com.usagemonitor.presentation.viewmodel.AppUpdateUiState
import com.usagemonitor.presentation.viewmodel.DashboardViewModel
import com.usagemonitor.presentation.viewmodel.DashboardViewModelConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelAutoUpdateTest : DashboardViewModelTestSupport() {

    // --- interruptor desligado ---------------------------------------------

    @Test
    fun `switch off never touches the installer`() = runTest {
        val installer = FakeInstaller()
        val viewModel = autoUpdateViewModel(installer = installer, enabled = false)

        try {
            runCurrent()

            assertEquals(0, installer.prepareCalls)
            assertIs<AppUpdateUiState.Available>(viewModel.appUpdateState.value)
        } finally {
            viewModel.onDestroy()
        }
    }

    @Test
    fun `unavailable installer keeps the manual banner`() = runTest {
        val viewModel = autoUpdateViewModel(installer = null, enabled = true)

        try {
            runCurrent()

            assertIs<AppUpdateUiState.Available>(viewModel.appUpdateState.value)
        } finally {
            viewModel.onDestroy()
        }
    }

    @Test
    fun `unsupported install origin keeps the manual banner`() = runTest {
        val installer = FakeInstaller(support = AppUpdateSupport.UNSUPPORTED_INSTALL_ORIGIN)
        val viewModel = autoUpdateViewModel(installer = installer, enabled = true)

        try {
            runCurrent()

            assertEquals(0, installer.prepareCalls)
            assertIs<AppUpdateUiState.Available>(viewModel.appUpdateState.value)
        } finally {
            viewModel.onDestroy()
        }
    }

    // --- caminho feliz -------------------------------------------------------

    @Test
    fun `switch on downloads and reaches the ready state`() = runTest {
        val installer = FakeInstaller()
        val viewModel = autoUpdateViewModel(installer = installer, enabled = true)

        try {
            runCurrent()

            assertEquals(1, installer.prepareCalls)
            val state = viewModel.appUpdateState.value
            assertIs<AppUpdateUiState.Ready>(state)
            assertEquals("7.1.0", state.update.version)
        } finally {
            viewModel.onDestroy()
        }
    }

    @Test
    fun `progress is published as whole percent while downloading`() = runTest {
        val installer = FakeInstaller(gate = CompletableDeferred())
        val viewModel = autoUpdateViewModel(installer = installer, enabled = true)

        try {
            runCurrent()
            assertIs<AppUpdateUiState.Downloading>(viewModel.appUpdateState.value)

            installer.emitProgress(downloaded = 50, total = 200)
            runCurrent()

            val state = viewModel.appUpdateState.value
            assertIs<AppUpdateUiState.Downloading>(state)
            assertEquals(25, state.percent)
        } finally {
            installer.release()
            viewModel.onDestroy()
        }
    }

    @Test
    fun `progress without a known total keeps the percentage null`() = runTest {
        val installer = FakeInstaller(gate = CompletableDeferred())
        val viewModel = autoUpdateViewModel(installer = installer, enabled = true)

        try {
            runCurrent()
            installer.emitProgress(downloaded = 50, total = null)
            runCurrent()

            val state = viewModel.appUpdateState.value
            assertIs<AppUpdateUiState.Downloading>(state)
            assertEquals(null, state.percent)
        } finally {
            installer.release()
            viewModel.onDestroy()
        }
    }

    // --- dedup ---------------------------------------------------------------

    /**
     * A guarda que faltava: durante o download `preparedUpdate` é nulo, e
     * comparar só por ele fazia o poll de 10 min cancelar e reiniciar o download
     * do zero — um download de 120 MB que levasse mais que o ciclo nunca
     * terminaria.
     */
    @Test
    fun `a second check for the same version does not restart the download`() = runTest {
        val installer = FakeInstaller(gate = CompletableDeferred())
        val viewModel = autoUpdateViewModel(installer = installer, enabled = true)

        try {
            runCurrent()
            assertEquals(1, installer.prepareCalls)

            viewModel.refresh()
            runCurrent()

            assertEquals(1, installer.prepareCalls)
            assertIs<AppUpdateUiState.Downloading>(viewModel.appUpdateState.value)
        } finally {
            installer.release()
            viewModel.onDestroy()
        }
    }

    @Test
    fun `a check for an already prepared version does not download again`() = runTest {
        val installer = FakeInstaller()
        val viewModel = autoUpdateViewModel(installer = installer, enabled = true)

        try {
            runCurrent()
            assertEquals(1, installer.prepareCalls)

            viewModel.refresh()
            runCurrent()

            assertEquals(1, installer.prepareCalls)
            assertIs<AppUpdateUiState.Ready>(viewModel.appUpdateState.value)
        } finally {
            viewModel.onDestroy()
        }
    }

    // --- backoff -------------------------------------------------------------

    @Test
    fun `failure enters backoff and does not retry on the next check`() = runTest {
        val clock = MutableClock(NOW)
        val installer = FakeInstaller(failWith = IllegalStateException("network down"))
        val viewModel = autoUpdateViewModel(installer = installer, enabled = true, clock = clock)

        try {
            runCurrent()
            assertEquals(1, installer.prepareCalls)
            val failed = viewModel.appUpdateState.value
            assertIs<AppUpdateUiState.Failed>(failed)
            assertEquals(AppUpdateFailureReason.DOWNLOAD, failed.reason)

            // Dentro da janela de espera: nada de rebaixar 120 MB.
            clock.advance(29.minutes)
            viewModel.refresh()
            runCurrent()
            assertEquals(1, installer.prepareCalls)

            // Passada a janela, tenta de novo.
            clock.advance(2.minutes)
            viewModel.refresh()
            runCurrent()
            assertEquals(2, installer.prepareCalls)
        } finally {
            viewModel.onDestroy()
        }
    }

    @Test
    fun `the backoff list is also the attempt ceiling`() = runTest {
        val clock = MutableClock(NOW)
        val installer = FakeInstaller(failWith = IllegalStateException("network down"))
        val viewModel = autoUpdateViewModel(
            installer = installer,
            enabled = true,
            clock = clock,
            backoff = listOf(1.minutes, 2.minutes)
        )

        try {
            runCurrent()
            assertEquals(1, installer.prepareCalls)

            clock.advance(5.minutes)
            viewModel.refresh()
            runCurrent()
            assertEquals(2, installer.prepareCalls)

            // Esgotadas as duas tentativas, a versão para de ser tentada.
            clock.advance(5.hours)
            viewModel.refresh()
            runCurrent()
            assertEquals(2, installer.prepareCalls)
            assertIs<AppUpdateUiState.Failed>(viewModel.appUpdateState.value)
        } finally {
            viewModel.onDestroy()
        }
    }

    @Test
    fun `a newer announced version clears the backoff`() = runTest {
        val clock = MutableClock(NOW)
        val installer = FakeInstaller(failWith = IllegalStateException("network down"))
        var announced = "7.1.0"
        val viewModel = autoUpdateViewModel(
            installer = installer,
            enabled = true,
            clock = clock,
            announcedVersion = { announced }
        )

        try {
            runCurrent()
            assertEquals(1, installer.prepareCalls)

            // Release nova é uma tentativa nova, mesmo dentro da espera anterior.
            announced = "7.2.0"
            installer.failWith = null
            viewModel.refresh()
            runCurrent()

            assertEquals(2, installer.prepareCalls)
            assertIs<AppUpdateUiState.Ready>(viewModel.appUpdateState.value)
        } finally {
            viewModel.onDestroy()
        }
    }

    // --- interruptor desligado no meio --------------------------------------

    @Test
    fun `turning the switch off mid download cancels it and drops back to available`() = runTest {
        val installer = FakeInstaller(gate = CompletableDeferred())
        val enabled = MutableStateFlow(true)
        val viewModel = autoUpdateViewModel(installer = installer, enabledFlow = enabled)

        try {
            runCurrent()
            assertIs<AppUpdateUiState.Downloading>(viewModel.appUpdateState.value)

            enabled.value = false
            runCurrent()

            assertIs<AppUpdateUiState.Available>(viewModel.appUpdateState.value)
        } finally {
            installer.release()
            viewModel.onDestroy()
        }
    }

    /**
     * Um artefato preparado seria aplicado no encerramento — exatamente o que o
     * usuário acabou de recusar ao desligar o interruptor.
     */
    @Test
    fun `turning the switch off discards an already prepared update`() = runTest {
        val installer = FakeInstaller()
        val enabled = MutableStateFlow(true)
        val viewModel = autoUpdateViewModel(installer = installer, enabledFlow = enabled)

        try {
            runCurrent()
            assertIs<AppUpdateUiState.Ready>(viewModel.appUpdateState.value)

            enabled.value = false
            runCurrent()
            viewModel.scheduleUpdateOnExit()

            assertIs<AppUpdateUiState.Available>(viewModel.appUpdateState.value)
            assertEquals(0, installer.scheduleCalls)
        } finally {
            viewModel.onDestroy()
        }
    }

    // --- encerramento --------------------------------------------------------

    @Test
    fun `exit schedules the prepared update exactly once`() = runTest {
        val installer = FakeInstaller()
        val viewModel = autoUpdateViewModel(installer = installer, enabled = true)

        try {
            runCurrent()

            viewModel.scheduleUpdateOnExit()

            assertEquals(1, installer.scheduleCalls)
            assertEquals("7.1.0", installer.scheduledVersions.single())
        } finally {
            viewModel.onDestroy()
        }
    }

    @Test
    fun `exit without a prepared update schedules nothing`() = runTest {
        val installer = FakeInstaller(gate = CompletableDeferred())
        val viewModel = autoUpdateViewModel(installer = installer, enabled = true)

        try {
            runCurrent()

            viewModel.scheduleUpdateOnExit()

            assertEquals(0, installer.scheduleCalls)
        } finally {
            installer.release()
            viewModel.onDestroy()
        }
    }

    @Test
    fun `restart now only fires with a prepared update`() = runTest {
        val installer = FakeInstaller(gate = CompletableDeferred())
        var restarts = 0
        val viewModel = autoUpdateViewModel(
            installer = installer,
            enabled = true,
            onRestart = { restarts += 1 }
        )

        try {
            runCurrent()
            viewModel.restartAndUpdateNow()
            assertEquals(0, restarts)

            installer.release()
            runCurrent()
            viewModel.restartAndUpdateNow()
            assertEquals(1, restarts)
        } finally {
            viewModel.onDestroy()
        }
    }

    // --- infraestrutura ------------------------------------------------------

    private class FakeInstaller(
        private val support: AppUpdateSupport = AppUpdateSupport.SUPPORTED,
        var failWith: Throwable? = null,
        private val gate: CompletableDeferred<Unit>? = null
    ) : AppUpdateInstaller {

        var prepareCalls = 0
            private set
        var scheduleCalls = 0
            private set
        val scheduledVersions = mutableListOf<String>()

        private var progressSink: ((Long, Long?) -> Unit)? = null

        override fun support(): AppUpdateSupport = support

        override suspend fun prepare(
            update: AppUpdateInfo,
            onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit
        ): Result<AppUpdatePreparation> {
            prepareCalls += 1
            progressSink = onProgress
            gate?.await()
            failWith?.let { return Result.failure(it) }
            return Result.success(
                AppUpdatePreparation(
                    version = update.version,
                    assetName = "UsageMonitor-Setup-${update.version}.exe",
                    sizeBytes = 200L
                )
            )
        }

        override fun schedule(preparation: AppUpdatePreparation): Result<Unit> {
            scheduleCalls += 1
            scheduledVersions += preparation.version
            return Result.success(Unit)
        }

        fun emitProgress(downloaded: Long, total: Long?) {
            progressSink?.invoke(downloaded, total)
        }

        fun release() {
            gate?.complete(Unit)
        }
    }

    private class MutableClock(private var current: Instant) : Clock {
        override fun now(): Instant = current
        fun advance(by: Duration) {
            current += by
        }
    }

    /**
     * Extensão de `TestScope` para o `testScheduler` do `runTest` chegar até a
     * configuração sem ser repassado em toda chamada.
     */
    private fun kotlinx.coroutines.test.TestScope.autoUpdateViewModel(
        installer: AppUpdateInstaller?,
        enabled: Boolean = true,
        enabledFlow: MutableStateFlow<Boolean> = MutableStateFlow(enabled),
        clock: Clock = MutableClock(NOW),
        backoff: List<Duration> = listOf(30.minutes, 2.hours, 6.hours),
        announcedVersion: () -> String = { "7.1.0" },
        onRestart: () -> Unit = {}
    ): DashboardViewModel {
        val failing = Result.failure<ApiUsageStats>(Exception("Não deve ser chamado"))
        return DashboardViewModel(
            GetAnthropicUsageUseCase(object : AnthropicRepository {
                override suspend fun getUsage() = failing
            }),
            GetMiniMaxUsageUseCase(object : MiniMaxRepository {
                override suspend fun getUsage() = failing
            }),
            GetCodexUsageUseCase(object : CodexRepository {
                override suspend fun getUsage() = failing
            }),
            GetDeepSeekUsageUseCase(object : DeepSeekRepository {
                override suspend fun getUsage() = failing
            }),
            MutableStateFlow(emptySet()),
            historyUseCase(mutableListOf()),
            checkForAppUpdate = updateUseCase {
                val version = announcedVersion()
                Result.success(
                    AppUpdateInfo(
                        version = version,
                        releasePageUrl = "https://example.com/releases/tag/v$version"
                    )
                )
            },
            appUpdateInstaller = installer,
            autoUpdateEnabled = enabledFlow,
            onRestartAndUpdateRequested = onRestart,
            currentAppVersion = "7.0.0",
            clock = clock,
            config = autoUpdateConfig(testScheduler, backoff)
        ).also { it.cancelCountdown() }
    }

    private fun autoUpdateConfig(
        scheduler: TestCoroutineScheduler,
        backoff: List<Duration>
    ) = DashboardViewModelConfig(
        workerDispatcher = StandardTestDispatcher(scheduler),
        // O laço periódico fica fora do caminho: aqui quem dispara a segunda
        // verificação é `refresh()`, para o teste controlar quando ela acontece.
        updateCheckIntervalWhileRunning = 365.days,
        autoStartInitialFetch = false,
        autoStartCountdown = false,
        updateRetryBackoff = backoff
    )

    private companion object {
        val NOW: Instant = Instant.parse("2026-08-22T12:00:00Z")
    }
}
