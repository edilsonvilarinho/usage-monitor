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
