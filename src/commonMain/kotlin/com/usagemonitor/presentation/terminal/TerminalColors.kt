package com.usagemonitor.presentation.terminal

import com.usagemonitor.domain.entity.ApiSource

/**
 * As cores do modo terminal por papel de token, nas paletas de
 * `docs/design-system/tokens/terminal.css`. Um dono só para os hexes, que repetem os
 * acentos de `colors.css`: o renderizador ANSI e o do tmux leem daqui.
 */
internal object TerminalColors {
    const val SELECTION_DARK = 0x2A2626
    const val SELECTION_LIGHT = 0xEFEAEA

    fun hex(color: TerminalColor, light: Boolean): Int = if (light) lightHex(color) else darkHex(color)

    /** O mapa de 16 cores comentado em `terminal.css`. */
    fun ansi16(color: TerminalColor): Int =
        when (color) {
            TerminalColor.Muted, TerminalColor.Dim, TerminalColor.Border -> 90
            TerminalColor.Gold, TerminalColor.Warn, TerminalColor.CacheWrite -> 33
            TerminalColor.Ok, TerminalColor.CacheRead -> 32
            TerminalColor.Crit -> 31
            TerminalColor.Input -> 34
            TerminalColor.Output -> 35
            is TerminalColor.Source -> when (color.source) {
                ApiSource.ANTHROPIC -> 34
                ApiSource.CODEX -> 36
                ApiSource.OPENCODE, ApiSource.OPENCODE_GO -> 32
                ApiSource.MINIMAX -> 33
                ApiSource.DEEPSEEK, ApiSource.OPENROUTER, ApiSource.GEMINI, ApiSource.ANTIGRAVITY -> 35
                ApiSource.CURSOR -> 92
                ApiSource.KILO -> 93
            }
        }

    private fun darkHex(color: TerminalColor): Int =
        when (color) {
            TerminalColor.Muted -> 0xB8B2B2
            TerminalColor.Dim -> 0x4A4444
            TerminalColor.Border -> 0x5A5353
            TerminalColor.Gold -> 0xE8AE63
            TerminalColor.Ok -> 0x4CAF50
            TerminalColor.Warn -> 0xFFA726
            TerminalColor.Crit -> 0xE86A6A
            TerminalColor.Input -> 0x4C8DFF
            TerminalColor.Output -> 0xB07CFF
            TerminalColor.CacheRead -> 0x4CAF50
            TerminalColor.CacheWrite -> 0xFFA726
            is TerminalColor.Source -> when (color.source) {
                ApiSource.ANTHROPIC -> 0x4F8CFF
                ApiSource.CODEX -> 0x27BFA3
                ApiSource.OPENCODE, ApiSource.OPENCODE_GO -> 0x7BD389
                ApiSource.MINIMAX -> 0xFF8A3D
                ApiSource.DEEPSEEK -> 0xC084FC
                ApiSource.KILO -> 0xE6D84E
                ApiSource.OPENROUTER -> 0xFF6FA8
                ApiSource.GEMINI, ApiSource.ANTIGRAVITY -> 0xE2A5E9
                ApiSource.CURSOR -> 0xC7E9A5
            }
        }

    private fun lightHex(color: TerminalColor): Int =
        when (color) {
            TerminalColor.Muted -> 0x686060
            TerminalColor.Dim -> 0xD7D0D0
            TerminalColor.Border -> 0xB9B1B1
            TerminalColor.Gold -> 0x8C643F
            TerminalColor.Ok -> 0x2E7D32
            TerminalColor.Warn -> 0x8A5000
            TerminalColor.Crit -> 0xB3261E
            TerminalColor.Input -> 0x1565C0
            TerminalColor.Output -> 0x6A3FB5
            TerminalColor.CacheRead -> 0x2E7D32
            TerminalColor.CacheWrite -> 0x8A5000
            is TerminalColor.Source -> when (color.source) {
                ApiSource.ANTHROPIC -> 0x1565C0
                ApiSource.CODEX -> 0x00695C
                ApiSource.OPENCODE, ApiSource.OPENCODE_GO -> 0x2E7D32
                ApiSource.MINIMAX -> 0x9A4A00
                ApiSource.DEEPSEEK -> 0x7B3FC4
                ApiSource.KILO -> 0x5F5500
                ApiSource.OPENROUTER -> 0xB23368
                ApiSource.GEMINI, ApiSource.ANTIGRAVITY -> 0x7C1D87
                ApiSource.CURSOR -> 0x477915
            }
        }
}
