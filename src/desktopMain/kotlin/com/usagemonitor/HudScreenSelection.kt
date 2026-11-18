package com.usagemonitor

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.russhwolf.settings.PreferencesSettings

/**
 * O monitor do notch: a área útil em que ele encaixa e o id que decide a
 * `GraphicsConfiguration` com que a janela nasce.
 *
 * Saiu de `HudWindowHost` quando a adoção do monitor do compositor (#430) o
 * levou a 301 linhas: as três origens da troca de monitor — o hover, o arrasto
 * e a adoção — passam a morar juntas, e o host só as chama.
 *
 * **O monitor (issue #273).** Era sempre o padrão: o arrasto era preso a ele e
 * o encaixe usava as bordas dele, e o notch não saía do primário. Agora é o
 * monitor gravado, resolvido de novo a cada abertura por hover — um monitor
 * reconectado volta a receber o notch sem reiniciar o app.
 *
 * **O id (#426).** Ele muda quando a barra é solta noutro monitor, e aí a
 * janela **renasce** ali: remendar a janela aberta devolve o alfa mas para o
 * desenho do Compose.
 */
@Stable
internal class HudScreenSelection(private val settings: PreferencesSettings, fallbackArea: ScreenWorkArea) {
    var area by mutableStateOf(resolveHudScreen(settings)?.workArea ?: fallbackArea)
        private set

    var id by mutableStateOf(resolveHudScreen(settings)?.id)
        private set

    /** O hover relê o monitor **gravado**; sem gravação ou com ele desligado, o padrão. */
    fun reload(fallbackArea: ScreenWorkArea) {
        val resolved = resolveHudScreen(settings)
        area = resolved?.workArea ?: fallbackArea
        id = resolved?.id ?: id
    }

    /**
     * A HUD passa para [screen], e ele é gravado: é o que o hover relê.
     *
     * Vale para o arrasto solto noutro monitor e para a adoção do COSMIC (#430).
     * Na adoção, gravar é o que impede o laço: sem isso o hover seguinte
     * devolveria a barra ao monitor antigo, e o compositor a jogaria de volta.
     */
    fun moveTo(screen: ScreenInfo) {
        area = screen.workArea
        id = screen.id
        persistHudScreen(settings, screen)
    }
}

/**
 * A área útil do monitor gravado para o notch; sem gravação, ou com o monitor
 * desligado, a do padrão. O que está gravado **não é apagado** quando o monitor
 * some: ele pode voltar.
 *
 * Área **útil**, e não a tela inteira (issue #288). A #256 deixou o notch ocupar
 * a faixa da barra de tarefas, mas no Windows ela também é *topmost* e volta
 * para cima de toda janela *topmost* a cada clique, hover ou notificação: o
 * `alwaysOnTop` perde essa disputa, e o notch de baixo ficava meio coberto. O
 * monitor continua identificado pelos limites **inteiros** (`persistHudScreen`),
 * que não mudam quando a barra é movida.
 */
private fun resolveHudScreen(settings: PreferencesSettings): ScreenInfo? {
    val saved = readPersistedHudScreen(settings)
    val screens = availableScreens()
    return resolveScreen(screens, saved.id, saved.bounds) ?: screens.firstOrNull()
}
