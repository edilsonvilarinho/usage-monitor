package com.usagemonitor

/**
 * A disposição gravada de uma janela modal (Histórico, Sessões CLI, Time,
 * Presença). Nasceu com a janela principal, que saiu do app quando a barra HUD
 * virou o único modo de visualização; o formato das chaves das demais não mudou.
 */
internal enum class PersistedWindowPlacement {
    FLOATING,
    MAXIMIZED
}
