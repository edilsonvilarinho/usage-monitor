package com.usagemonitor

internal interface WindowActivationTarget {
    var isVisible: Boolean
    var isAlwaysOnTop: Boolean
    fun toFront()
    fun requestFocus()
    fun setBounds(x: Int, y: Int, width: Int, height: Int)
}

internal fun activateWindow(window: java.awt.Window) {
    activateWindow(
        object : WindowActivationTarget {
            override var isVisible: Boolean
                get() = window.isVisible
                set(value) {
                    window.isVisible = value
                }

            override var isAlwaysOnTop: Boolean
                get() = window.isAlwaysOnTop
                set(value) {
                    // Nem toda janela AWT aceita: sem a permissao o setter lanca
                    // SecurityException, e ficar sem o realce e melhor que nao
                    // ativar a janela.
                    runCatching { window.isAlwaysOnTop = value }
                }

            override fun toFront() {
                window.toFront()
            }

            override fun requestFocus() {
                window.requestFocus()
            }

            override fun setBounds(x: Int, y: Int, width: Int, height: Int) {
                window.setBounds(x, y, width, height)
            }
        }
    )
}

/**
 * `toFront()` sozinho nao traz a janela para a frente quando o processo nao detem
 * o primeiro plano: o Windows apenas pisca o botao na barra de tarefas. E esse
 * exatamente o caso de quem clica no atalho com o app ja rodando -- quem pede a
 * ativacao e o processo que ja esta la, e nao o que acabou de receber o clique.
 *
 * Ligar `alwaysOnTop` por um instante contorna o bloqueio, e o valor anterior e
 * **restaurado**: ele e preferencia do usuario na janela principal, e deixa-la
 * presa no topo seria trocar um defeito por outro.
 */
internal fun activateWindow(window: WindowActivationTarget) {
    window.isVisible = true

    val wasAlwaysOnTop = window.isAlwaysOnTop
    if (!wasAlwaysOnTop) {
        window.isAlwaysOnTop = true
    }

    window.toFront()
    window.requestFocus()

    if (!wasAlwaysOnTop) {
        window.isAlwaysOnTop = false
    }
}
