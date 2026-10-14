package com.usagemonitor.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.russhwolf.settings.PreferencesSettings
import com.usagemonitor.CURRENT_APP_VERSION
import com.usagemonitor.domain.entity.AppUpdateReceipt
import com.usagemonitor.domain.entity.StartupGuideDecision
import com.usagemonitor.domain.entity.nextStartupTipIndex
import com.usagemonitor.domain.entity.startupGuideDecision
import com.usagemonitor.domain.entity.startupTipIndex
import com.usagemonitor.persistOnboardingCompletedVersion
import com.usagemonitor.persistStartupTipLastShownDate
import com.usagemonitor.persistStartupTipNextIndex
import com.usagemonitor.persistStartupTipsEnabled
import com.usagemonitor.readOnboardingCompletedVersion
import com.usagemonitor.readPersistedReleaseNotesSeenVersion
import com.usagemonitor.readStartupTipLastShownDate
import com.usagemonitor.readStartupTipNextIndex
import com.usagemonitor.readStartupTipsEnabled
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/**
 * Guia de introdução e dica do dia (#411): o que está aberto e o que foi lembrado.
 *
 * Classe própria, como [com.usagemonitor.update.ReleaseNotesController]: a raiz
 * de composição recebe uma chamada, não estado novo. As marcas são gravadas
 * **ao abrir**, nunca ao fechar — um encerramento anormal perderia a marca, e o
 * guia voltaria numa abertura em que já foi visto.
 */
@Stable
internal class StartupGuideController(
    private val settings: PreferencesSettings,
    private val currentVersion: String,
    private val tipCount: Int,
    val today: () -> LocalDate
) {
    var isGuideOpen by mutableStateOf(false)
        private set

    /** Sobe a cada abertura: é o que reativa a janela já visível (`AppDialogWindow`). */
    var guideOpenGeneration by mutableStateOf(0)
        private set

    /** Dica na tela; `null` com a janela fechada. */
    var tipIndex by mutableStateOf<Int?>(null)
        private set

    var tipsEnabled by mutableStateOf(readStartupTipsEnabled(settings))
        private set

    fun apply(decision: StartupGuideDecision) {
        // Sem `else`: valor novo no enum vira erro de compilação aqui.
        when (decision) {
            StartupGuideDecision.NOTHING -> Unit
            StartupGuideDecision.GUIDE -> openGuide()
            StartupGuideDecision.TIP -> showTip()
        }
    }

    /** Também pela Ajuda e pela bandeja ("Rever guia"). */
    fun openGuide() {
        persistOnboardingCompletedVersion(settings, currentVersion)
        isGuideOpen = true
        guideOpenGeneration++
    }

    fun closeGuide() {
        isGuideOpen = false
    }

    private fun showTip() {
        val index = startupTipIndex(readStartupTipNextIndex(settings), tipCount)
        persistStartupTipLastShownDate(settings, today())
        persistStartupTipNextIndex(settings, nextStartupTipIndex(index, tipCount))
        tipIndex = index
    }

    /** "Próxima dica": anda na roda e guarda a seguinte para amanhã. */
    fun nextTip() {
        val current = tipIndex ?: return
        val next = nextStartupTipIndex(current, tipCount)
        persistStartupTipNextIndex(settings, nextStartupTipIndex(next, tipCount))
        tipIndex = next
    }

    fun closeTip() {
        tipIndex = null
    }

    /** O interruptor do modal e o de Configurações › Sistema são o mesmo valor. */
    fun updateTipsEnabled(enabled: Boolean) {
        persistStartupTipsEnabled(settings, enabled)
        tipsEnabled = enabled
    }
}

@Composable
internal fun rememberStartupGuideController(
    settings: PreferencesSettings,
    /** Recibo do instalador, lido uma vez na abertura; só prova que já houve atualização. */
    receipt: AppUpdateReceipt?,
    /** A HUD já está na tela: o modal nunca abre antes da janela âncora do app. */
    ready: Boolean,
    tipCount: Int,
    currentVersion: String = CURRENT_APP_VERSION
): StartupGuideController {
    val controller = remember(currentVersion) {
        StartupGuideController(settings, currentVersion, tipCount) {
            // Mesmo fuso de apresentação do resto do app: o "dia" da dica é o de BRT.
            Clock.System.todayIn(TimeZone.of("America/Sao_Paulo"))
        }
    }
    // Lida na composição, antes de qualquer efeito: o controlador das Novidades
    // grava a marca da versão corrente no primeiro efeito dele, e lida depois
    // toda instalação nova pareceria uma abertura comum.
    val seenVersionAtStart = remember { readPersistedReleaseNotesSeenVersion(settings) }

    LaunchedEffect(ready) {
        if (!ready) {
            return@LaunchedEffect
        }
        val decision = startupGuideDecision(
            currentVersion = currentVersion,
            releaseNotesSeenVersion = seenVersionAtStart,
            onboardingCompletedVersion = readOnboardingCompletedVersion(settings),
            hasUpdateReceipt = receipt != null,
            tipsEnabled = controller.tipsEnabled,
            lastTipDate = readStartupTipLastShownDate(settings),
            today = controller.today()
        )
        controller.apply(decision)
    }
    return controller
}
