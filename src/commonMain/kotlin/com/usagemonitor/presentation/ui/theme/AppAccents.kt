package com.usagemonitor.presentation.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Paleta de acentos da aplicação, com uma variante por tema.
 *
 * Existe porque a paleta antes morava como `internal val` dentro de
 * `CliSessionsScreen.kt` — um arquivo de tela — e as telas de time importavam
 * constantes de uma tela irmã. Dois efeitos disso:
 *
 * 1. Não havia nome para "trabalhando agora", então a tela de presença reusou o
 *    verde de *cache read* para dizer outra coisa.
 * 2. As cores eram **fixas nos dois temas**, e várias são usadas como cor de
 *    texto. Contra a `surface` clara de então (`#F8F9FA`) todas reprovavam o
 *    mínimo de 4,5:1 da WCAG AA — `#4CAF50` dava 2,64:1. `AppAccentsContrastTest`
 *    fecha a porta: qualquer valor novo aqui é medido contra as duas superfícies.
 *
 * Os valores escuros são os que a app já usava, com **uma** exceção deliberada:
 * [saturated] era `#E05252`, que dava 4,06:1 contra a `surface` escura de então
 * (`#242424`) e também reprovava. É a cor do veredito "saturada" — o status mais
 * importante da tela de sessões —, então foi clareada para o tom aprovado mais
 * próximo.
 *
 * A refatoração visual trocou as superfícies (`#1B1818` / `#FFFCFC`) e **não**
 * tocou nestes treze valores: as novas são respectivamente mais escura e mais
 * clara que as antigas, então cada acento ganhou contraste. O que mudou foi a
 * área que eles ocupam — de fundo de card inteiro para marcador de 2dp, linha de
 * gráfico e texto de valor.
 */
@Immutable
data class AppAccents(
    // ── Tipos de token / significados de leitura ──────────────────────────────
    /** Tokens de entrada e custo. */
    val input: Color,
    /** Tokens de saída. */
    val output: Color,
    /** Cache lido; também o acento de "trabalhando agora" na tela de presença. */
    val cacheRead: Color,
    /** Cache gravado. */
    val cacheWrite: Color,
    /** Economia obtida com cache. */
    val savings: Color,
    /** Veredito de contexto saturado. */
    val saturated: Color,
    /** Ausência de atividade: sem cor porque não há o que destacar. */
    val neutral: Color,

    // ── Identidade por fonte de dados ─────────────────────────────────────────
    val anthropic: Color,
    val minimax: Color,
    val codex: Color,
    val deepseek: Color,
    val opencode: Color,
    val kilo: Color,
    /**
     * Identidade própria — OpenRouter não é o mesmo fornecedor de nenhuma das
     * outras seis, então reusar um acento existente associaria visualmente
     * duas empresas sem relação (diferente de [opencode], dividido de
     * propósito entre Zen Free e Go). Matiz ~335° preenche o único vão vazio
     * da roda entre [deepseek] (270°) e [minimax] (24°) — validado em
     * `AppAccentsContrastTest` como as demais.
     */
    val openrouter: Color
) {
    companion object {
        /** Acentos do tema em vigor. Ler daqui, nunca das constantes cruas. */
        val current: AppAccents
            @Composable
            @ReadOnlyComposable
            get() = LocalAppAccents.current
    }
}

/**
 * Variante escura.
 *
 * Referência de contraste: `#1B1818` (`surface` escura), e não `#131010`
 * (`background`) — o texto acentuado desta app cai sobre um painel, não sobre a
 * moldura da janela.
 */
val darkAppAccents = AppAccents(
    input      = Color(0xFF4C8DFF),
    output     = Color(0xFFB07CFF),
    cacheRead  = Color(0xFF4CAF50),
    cacheWrite = Color(0xFFFFA726),
    savings    = Color(0xFF26C6DA),
    saturated  = Color(0xFFE86A6A),
    neutral    = Color(0xFF7C8CA5),
    anthropic  = Color(0xFF4F8CFF),
    minimax    = Color(0xFFFF8A3D),
    codex      = Color(0xFF27BFA3),
    deepseek   = Color(0xFFC084FC),
    opencode   = Color(0xFF7BD389),
    kilo       = Color(0xFFE6D84E),
    openrouter = Color(0xFFFF6FA8)
)

/**
 * Variante clara.
 *
 * Não é a paleta escura com alpha: acento claro sobre fundo claro não tem como
 * chegar a 4,5:1 por transparência. Cada tom foi rebaixado até passar contra a
 * `surface` clara mantendo a matiz, para que a codificação de cor continue a
 * mesma ao trocar de tema.
 */
val lightAppAccents = AppAccents(
    input      = Color(0xFF1565C0),
    output     = Color(0xFF6A3FB5),
    cacheRead  = Color(0xFF2E7D32),
    cacheWrite = Color(0xFF8A5000),
    savings    = Color(0xFF00697A),
    saturated  = Color(0xFFB3261E),
    neutral    = Color(0xFF4A5568),
    anthropic  = Color(0xFF1565C0),
    minimax    = Color(0xFF9A4A00),
    codex      = Color(0xFF00695C),
    deepseek   = Color(0xFF7B3FC4),
    opencode   = Color(0xFF2E7D32),
    kilo       = Color(0xFF5F5500),
    openrouter = Color(0xFFB23368)
)

/**
 * `staticCompositionLocalOf` e não `compositionLocalOf`: a paleta só muda quando
 * o tema inteiro troca, e aí a recomposição total já acontece de qualquer forma.
 */
val LocalAppAccents = staticCompositionLocalOf { darkAppAccents }
