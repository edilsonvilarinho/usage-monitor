package com.usagemonitor.presentation.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.font.FontFamily

/**
 * As duas famílias tipográficas do sistema visual.
 *
 * **Duas, não uma.** [mono] carrega título, rótulo, número, célula de tabela e
 * cromo — tudo onde o alinhamento de coluna importa e onde o dígito precisa ter
 * largura fixa. [sans] carrega parágrafo descritivo: as explicações das
 * Configurações, o glossário de sessões e as mensagens de estado. Monoespaçada
 * em texto corrido fica ~8% mais larga e mais lenta de ler, e esta app tem
 * bastante texto explicativo.
 */
@Immutable
data class AppFontFamilies(
    val mono: FontFamily,
    val sans: FontFamily
)

/**
 * As famílias carregadas pela plataforma.
 *
 * `expect`/`actual` porque [AppTheme] vive em `commonMain` e a API de fonte por
 * arquivo do Compose é JVM-only — os TTFs saem do classpath, em
 * `desktopMain/resources/fonts/`.
 *
 * **Não passa por `composeResources`**: aquele caminho carrega de forma
 * assíncrona, e o `ImageComposeScene` do `ScreenshotGenerator` renderiza
 * offscreen com relógio manual. Uma captura gerada antes de a fonte chegar sai
 * com a fonte de fallback — falha silenciosa, que só aparece ao comparar
 * imagens.
 */
expect val appFontFamilies: AppFontFamilies
