package com.usagemonitor.presentation.ui.theme

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font

/**
 * Carga da IBM Plex a partir do classpath.
 *
 * A sobrecarga usada é `Font(resource: String, weight, style)` de
 * `androidx.compose.ui.text.platform` — confirmada por inspeção do
 * `ui-text-desktop-1.7.1.jar`, onde ela convive com a variante que recebe um
 * `java.io.File`. A de recurso é a correta aqui: o app empacotado não expõe os
 * TTFs como arquivos soltos no disco, e um caminho absoluto não sobreviveria ao
 * jpackage.
 *
 * Só os três pesos que a escala usa — 400, 500 e 600 — e nenhum itálico: peso
 * que a `Typography` não pede é um TTF de ~180 KB a mais no instalador sem
 * nenhum pixel de diferença.
 *
 * `by lazy` e não inicialização direta: a leitura dos seis arquivos custa I/O, e
 * quem só executa teste de domínio não tem por que pagá-la.
 */
private val families: AppFontFamilies by lazy {
    AppFontFamilies(
        mono = FontFamily(
            plexFont("fonts/IBMPlexMono-Regular.ttf", FontWeight.Normal),
            plexFont("fonts/IBMPlexMono-Medium.ttf", FontWeight.Medium),
            plexFont("fonts/IBMPlexMono-SemiBold.ttf", FontWeight.SemiBold)
        ),
        sans = FontFamily(
            plexFont("fonts/IBMPlexSans-Regular.ttf", FontWeight.Normal),
            plexFont("fonts/IBMPlexSans-Medium.ttf", FontWeight.Medium),
            plexFont("fonts/IBMPlexSans-SemiBold.ttf", FontWeight.SemiBold)
        )
    )
}

private fun plexFont(resource: String, weight: FontWeight) =
    Font(resource = resource, weight = weight, style = FontStyle.Normal)

actual val appFontFamilies: AppFontFamilies
    get() = families
