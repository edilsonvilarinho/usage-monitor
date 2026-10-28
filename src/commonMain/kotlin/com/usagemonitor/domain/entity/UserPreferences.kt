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

/**
 * Identificador de cada API suportada.
 *
 * A ordem aqui é a ordem padrão dos cards no dashboard e das linhas em
 * Configurações → APIs; nada persiste o ordinal (as preferências gravam
 * [ApiSource.name]), então inserir um valor no meio não invalida nenhuma
 * gravação anterior.
 *
 * [OPENCODE_GO] é a assinatura paga do OpenCode e fica **ao lado** de
 * [OPENCODE], que é o plano gratuito do Zen. São duas fontes e não uma: a
 * gratuita é lida do SQLite local em requisições observadas, a paga vem de
 * `GET /zen/go/v1/usage` em percentual de três janelas. Reaproveitar
 * [OPENCODE] faria `isObservedActivitySource()` desviar o card inteiro para o
 * resumo sem barras, e uma máquina pode ter uma das duas sem a outra.
 */
enum class ApiSource { ANTHROPIC, MINIMAX, CODEX, DEEPSEEK, OPENCODE, OPENCODE_GO, KILO, OPENROUTER, GEMINI, CURSOR, ANTIGRAVITY }

/**
 * Faixa de opacidade da janela principal, em pontos percentuais (100 = totalmente opaco).
 * O piso evita que a janela fique inutilizável.
 */
const val MIN_WINDOW_OPACITY_PERCENT = 50
const val MAX_WINDOW_OPACITY_PERCENT = 100

/**
 * Escala global da interface, em pontos percentuais.
 *
 * O padrão é 100: a escala do app **multiplica** a do sistema, e só 100 deixa o
 * app do tamanho dos outros (#477). Foi 115 — o conjunto lia pequeno a 100–125% no
 * Windows —, mas com o Windows em 225% a densidade efetiva chegava a ~259% e
 * tudo saía grande. Quem quer maior sobe o slider; o sistema já escala a tela.
 *
 * O passo existe para o slider não produzir 71 valores distintos: a diferença
 * entre 113% e 114% não é perceptível e só multiplicaria as gravações.
 */
const val MIN_UI_SCALE_PERCENT = 80
const val MAX_UI_SCALE_PERCENT = 150
const val DEFAULT_UI_SCALE_PERCENT = 100

/** O padrão anterior, que a migração única de [DEFAULT_UI_SCALE_PERCENT] reconhece no registro (#477). */
const val LEGACY_DEFAULT_UI_SCALE_PERCENT = 115
const val UI_SCALE_STEP_PERCENT = 5
