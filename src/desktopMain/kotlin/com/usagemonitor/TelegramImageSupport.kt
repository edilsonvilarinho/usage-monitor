package com.usagemonitor

import org.jetbrains.skia.impl.Library

/**
 * Se este computador desenha as imagens do bot (#440). O Skia exige `libGL`, `libX11` e
 * `fontconfig` (medição da A01): num servidor sem elas, a biblioteca nativa não carrega,
 * e o bot responde em texto no lugar da imagem em vez de deixar o comando sem resposta.
 * Tentado uma vez, na primeira imagem pedida.
 */
internal object TelegramImageSupport {
    val available: Boolean by lazy { runCatching { Library.staticLoad() }.isSuccess }
}
