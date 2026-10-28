package com.usagemonitor

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.ui.HudUpdateIndicator
import com.usagemonitor.presentation.ui.updateBannerContent
import com.usagemonitor.presentation.viewmodel.AppUpdateUiState

/*
 * As decisões da janela da HUD, fora de `HudWindow.kt`. Aquele arquivo é cola de
 * janela (host `@Composable` e chamada AWT) e sai do Kover por nome (onda 6.5 do
 * plano `cobertura-95-execucao.md`); o que decide alguma coisa mora aqui e segue
 * contado e testado.
 */

/**
 * Se a HUD escolhe a `GraphicsConfiguration` da própria janela, em vez de
 * deixar o AWT escolher (issue #426).
 *
 * **Só no Linux**, que é onde a perda de alfa ao cruzar de monitor foi medida
 * (Bazzite 44, KDE Plasma em Wayland com XWayland, dois monitores). No Windows
 * e no macOS a janela segue pelo `Window` de alto nível do Compose, que é o
 * caminho em produção. Comportamento nativo medido numa plataforma nasce
 * restrito a ela (regra do #340).
 */
internal fun hudPicksGraphicsConfiguration(platform: AutoStartManager.Platform): Boolean =
    platform == AutoStartManager.Platform.LINUX

internal fun hudUpdateIndicatorOf(state: AppUpdateUiState, language: AppLanguage): HudUpdateIndicator {
    val content = updateBannerContent(state = state, language = language)
    return HudUpdateIndicator(
        tone = content.tone,
        description = content.title,
        actionLabel = content.actionLabel,
        headline = content.headline,
        detail = content.detail
    )
}

/** `F1` abre a ajuda, a tecla que o sistema reserva para ela. */
internal fun handleHudWindowKey(event: KeyEvent, onOpenHelp: () -> Unit): Boolean {
    val help = event.type == KeyEventType.KeyDown && event.key == Key.F1
    if (help) {
        onOpenHelp()
    }
    return help
}

/** A engrenagem abre as ações do app; é o que ela diz ao leitor de tela. */
internal fun hudGearDescription(language: AppLanguage): String =
    if (language == AppLanguage.PT) "Ações do Usage Monitor" else "Usage Monitor actions"

/**
 * A área da tela na escala da composição. A geometria trabalha em dp de
 * composição; a janela, em dp do sistema. A área desce à escala da composição e o
 * resultado da geometria volta multiplicado.
 */
internal fun ScreenWorkArea.inCompositionDp(scale: Float): ScreenWorkArea = ScreenWorkArea(
    x = x / scale,
    y = y / scale,
    size = DpSize(size.width / scale, size.height / scale)
)

/**
 * A opacidade que a HUD usa, a partir da preferência do usuário.
 *
 * No Windows a janela transparente do Compose só recebe o mouse onde o fundo tem
 * alfa 1/255 (`JLayeredPaneWithTransparencyHack`), e o sistema multiplica esse alfa
 * pela opacidade da janela: em 50% dá 1 × 127/255, que arredonda para zero, e o
 * ponteiro atravessa a HUD — o hover some. Medido no Windows 11 com uma janela
 * transparente igual à da HUD: 50% nunca recebe o mouse; 55%, 60%… 99% recebem.
 * Por isso o piso de [HUD_WINDOWS_MIN_OPACITY_PERCENT] só no Windows; fora dele o
 * fundo de alfa 1/255 não existe e a preferência vale inteira (issue #340).
 */
internal fun hudWindowOpacityPercent(percent: Int, platform: AutoStartManager.Platform): Int {
    val clamped = clampWindowOpacityPercent(percent)
    if (platform != AutoStartManager.Platform.WINDOWS) {
        return clamped
    }
    return maxOf(clamped, HUD_WINDOWS_MIN_OPACITY_PERCENT)
}

/**
 * Se a HUD precisa ser repintada depois de mudar a opacidade. No Windows, mudar a
 * opacidade refaz a camada da janela sem o fundo de alfa 1/255 e o mouse passa a
 * atravessá-la até a próxima pintura AWT: medido, 100 → 50 → 100 sem repintar fica
 * sem hover; repintando, volta. Era o hover que só voltava saindo e entrando no
 * modo HUD.
 */
internal fun hudRepaintsAfterOpacityChange(platform: AutoStartManager.Platform): Boolean =
    platform == AutoStartManager.Platform.WINDOWS

/** O menor valor medido em que a HUD transparente ainda recebe o mouse no Windows. */
internal const val HUD_WINDOWS_MIN_OPACITY_PERCENT = 55
