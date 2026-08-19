package com.usagemonitor.domain.entity

/**
 * Preferências do utilizador persistidas entre sessões.
 * Controlam a aparência e quais APIs são monitoradas.
 */
data class UserPreferences(
    val theme: AppTheme = AppTheme.DARK,
    val language: AppLanguage = AppLanguage.PT,
    val enabledApis: Set<ApiSource> = emptySet()
)

/** Tema visual da aplicação. */
enum class AppTheme { DARK, LIGHT }

/** Idioma da interface. */
enum class AppLanguage { PT, EN }

/** Identificador de cada API suportada. */
enum class ApiSource { ANTHROPIC, MINIMAX, CODEX, DEEPSEEK, OPENCODE, KILO }

/**
 * Faixa de opacidade da janela principal, em pontos percentuais (100 = totalmente opaco).
 * O piso evita que a janela fique inutilizável.
 */
const val MIN_WINDOW_OPACITY_PERCENT = 50
const val MAX_WINDOW_OPACITY_PERCENT = 100

/**
 * Escala global da interface, em pontos percentuais.
 *
 * O padrão é 115 e não 100: a escala tipográfica do sistema visual (10 · 12 · 14
 * · 16 · 20 · 28) e os alvos de 26–34dp foram calibrados no protótipo, e em tela
 * real o conjunto lê pequeno. Subir o padrão preserva as proporções aprovadas —
 * o que muda é o tamanho de todas elas ao mesmo tempo.
 *
 * O passo existe para o slider não produzir 71 valores distintos: a diferença
 * entre 113% e 114% não é perceptível e só multiplicaria as gravações.
 */
const val MIN_UI_SCALE_PERCENT = 80
const val MAX_UI_SCALE_PERCENT = 150
const val DEFAULT_UI_SCALE_PERCENT = 115
const val UI_SCALE_STEP_PERCENT = 5
