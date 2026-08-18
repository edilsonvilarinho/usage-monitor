package com.usagemonitor.presentation.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

enum class BannerTone {
    INFO,
    ERROR
}

/**
 * Aviso persistente de uma fonte.
 *
 * Virou casca de [AppBanner]. Antes pintava o retângulo inteiro de
 * `errorContainer` e, com uma conta Anthropic por banner, a metade de cima da
 * janela ficava vermelha antes de qualquer número aparecer. A severidade agora
 * é a barra de 2dp à esquerda, e o "!" desenhado com o tipo saiu: a palavra do
 * título já diz o que a exclamação repetia.
 */
@Composable
fun PersistentApiWarningBanner(
    title: String,
    description: String,
    actionLabel: String?,
    onAction: (() -> Unit)?,
    tone: BannerTone = BannerTone.ERROR,
    modifier: Modifier = Modifier
) {
    AppBanner(
        title = title,
        description = description,
        tone = when (tone) {
            BannerTone.INFO -> AppTone.INFO
            BannerTone.ERROR -> AppTone.CRITICAL
        },
        modifier = modifier,
        action = if (actionLabel != null && onAction != null) {
            { AppButton(label = actionLabel, onClick = onAction) }
        } else {
            null
        }
    )
}
