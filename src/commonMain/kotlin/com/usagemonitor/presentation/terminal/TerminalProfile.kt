package com.usagemonitor.presentation.terminal

/**
 * As quatro paletas do design system do modo terminal. Quem decide é o terminal
 * de quem lê, nunca o tema do app.
 */
enum class TerminalPalette { TRUECOLOR_DARK, TRUECOLOR_LIGHT, ANSI16, NONE }

/**
 * Como escrever neste terminal: a paleta e se os glifos podem sair em Unicode.
 * São duas decisões separadas: um console do Windows sem UTF-8 pode ter cor e
 * ainda assim precisar dos glifos ASCII.
 */
data class TerminalProfile(val palette: TerminalPalette, val unicode: Boolean) {
    val glyphs: TerminalGlyphs
        get() = if (unicode) TerminalGlyphs.UNICODE else TerminalGlyphs.ASCII
}

/**
 * Lê o perfil do ambiente do processo. Função pura: o `main` passa o ambiente,
 * se a saída é um terminal e a codificação dela, e o teste passa o que quiser.
 *
 * - Sem cor com `NO_COLOR` não vazio (no-color.org), `TERM=dumb` ou saída que não
 *   é terminal. `FORCE_COLOR` (diferente de `0`) liga a cor mesmo num pipe, e a
 *   linha de status passa [outputIsTerminal] verdadeiro porque o Claude Code lê a
 *   saída dela por um pipe e mostra as cores.
 * - Truecolor com `COLORTERM` `truecolor`/`24bit` ou dentro do Windows Terminal
 *   (`WT_SESSION`); senão, 16 cores, e o terminal escolhe o tom.
 * - Fundo claro pelo último campo de `COLORFGBG` (7 e de 9 a 15 são cores claras).
 *   Sem a variável, escuro, que é o caso comum e o padrão do design system.
 * - Glifos Unicode só com saída em UTF-8.
 */
fun detectTerminalProfile(
    environment: Map<String, String>,
    outputIsTerminal: Boolean,
    outputEncoding: String?
): TerminalProfile {
    val noColor = !environment["NO_COLOR"].isNullOrEmpty()
    val dumb = environment["TERM"].equals("dumb", ignoreCase = true)
    val forced = environment["FORCE_COLOR"].let { value -> !value.isNullOrEmpty() && value != "0" }
    val palette = when {
        noColor || dumb -> TerminalPalette.NONE
        !outputIsTerminal && !forced -> TerminalPalette.NONE
        !supportsTruecolor(environment) -> TerminalPalette.ANSI16
        hasLightBackground(environment["COLORFGBG"]) -> TerminalPalette.TRUECOLOR_LIGHT
        else -> TerminalPalette.TRUECOLOR_DARK
    }
    return TerminalProfile(palette = palette, unicode = isUtf8(outputEncoding))
}

private fun supportsTruecolor(environment: Map<String, String>): Boolean {
    val colorTerm = environment["COLORTERM"].orEmpty().lowercase()
    return colorTerm == "truecolor" || colorTerm == "24bit" || !environment["WT_SESSION"].isNullOrEmpty()
}

private fun hasLightBackground(colorFgBg: String?): Boolean {
    val background = colorFgBg?.substringAfterLast(';')?.trim()?.toIntOrNull() ?: return false
    return background == 7 || background in 9..15
}

private fun isUtf8(encoding: String?): Boolean =
    encoding.orEmpty().lowercase().replace("-", "").replace("_", "") == "utf8"
