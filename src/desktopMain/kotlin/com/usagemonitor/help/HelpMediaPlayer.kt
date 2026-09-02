package com.usagemonitor.help

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.usagemonitor.presentation.ui.help.HelpMediaState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data
import org.jetbrains.skia.Image

/**
 * Reprodução das demos da ajuda.
 *
 * O Compose não anima GIF — `painterResource` desenha o primeiro quadro e para.
 * Quem sabe decodificar GIF animado é o `Codec` do Skia, que já está no
 * classpath: ele dá a contagem de quadros, a duração de cada um e compõe o
 * quadro delta em cima do anterior. Sem ele, seria preciso ler os deslocamentos
 * do `ImageDescriptor` do arquivo e compor os quadros na mão.
 *
 * O laço de quadros mora **aqui**, em `desktopMain`, e não no composable de
 * conteúdo: animação infinita trava o `waitForIdle` dos testes de componente, e
 * é essa separação que deixa `HelpContent` exercitável.
 */
private const val HELP_MEDIA_RESOURCE_PREFIX = "/help/"

/**
 * Duração mínima de um quadro.
 *
 * O GIF conta a espera em centésimos de segundo e o valor zero é legal — vários
 * codificadores o usam para dizer "o mais rápido possível". Sem o piso, um
 * arquivo assim viraria um laço sem espera nenhuma, queimando CPU e piscando.
 */
private const val MIN_FRAME_DELAY_MILLIS = 20L

/**
 * Uma demo decodificada, com o bitmap de trabalho retido.
 *
 * O bitmap é retido de propósito: `readPixels` aceita o índice do quadro que já
 * está nele (`priorFrame`) e, com isso, decodifica só o delta. Sem esse
 * argumento o codec refaz a cadeia de quadros requeridos a cada tique — num GIF
 * delta de cinquenta quadros, isso é trabalho quadrático por passada.
 *
 * O quadro publicado é **cópia imutável**: `Bitmap.asComposeImageBitmap()`
 * embrulha o mesmo bitmap, e escrever o quadro seguinte por cima dele mutaria a
 * imagem que já está na tela sem invalidar nada.
 */
internal class HelpMediaClip(
    private val codec: Codec,
    private val working: Bitmap
) : AutoCloseable {

    val frameCount: Int = codec.frameCount

    private var decodedIndex = -1

    /** Quanto tempo o quadro fica na tela, com o piso aplicado. */
    fun frameDelayMillis(index: Int): Long {
        return codec.getFrameInfo(index).duration.toLong().coerceAtLeast(MIN_FRAME_DELAY_MILLIS)
    }

    fun frameAt(index: Int): ImageBitmap {
        // O atalho do delta só vale quando o bitmap carrega exatamente o quadro
        // anterior. Ao voltar ao início do laço ele carrega o último, então o
        // codec refaz a partir do quadro independente — que é o quadro zero.
        val priorFrame = if (decodedIndex == index - 1) decodedIndex else -1
        codec.readPixels(working, index, priorFrame)
        decodedIndex = index
        return Image.makeFromBitmap(working).toComposeImageBitmap()
    }

    override fun close() {
        codec.close()
        working.close()
    }
}

/**
 * Decodifica os bytes de um GIF. `null` quando não são um GIF legível.
 *
 * Arquivo ausente ou corrompido não pode derrubar a janela de ajuda: a demo
 * ilustra o tópico, e sem ela a descrição e os passos continuam servindo.
 */
internal fun decodeHelpMedia(bytes: ByteArray): HelpMediaClip? {
    return runCatching {
        val codec = Codec.makeFromData(Data.makeFromBytes(bytes))
        val working = Bitmap()
        if (!working.allocPixels(codec.imageInfo)) {
            codec.close()
            working.close()
            return null
        }
        HelpMediaClip(codec, working)
    }.getOrNull()
}

/** Lê a demo do classpath. `null` quando o recurso não veio nesta instalação. */
internal fun readHelpMediaBytes(mediaId: String): ByteArray? {
    val path = "$HELP_MEDIA_RESOURCE_PREFIX$mediaId.gif"
    val stream = object {}.javaClass.getResourceAsStream(path) ?: return null
    return stream.use { input -> runCatching { input.readBytes() }.getOrNull() }
}

/**
 * Estado da demo do tópico, avançando os quadros enquanto ele está na tela.
 *
 * Trocar de tópico cancela o laço anterior e fecha o codec dele: só uma demo
 * toca por vez, e cada quadro publicado é um bitmap novo — reter os quadros
 * todos de uma gravação de cinquenta quadros custaria dezenas de megabytes de
 * memória para mostrar um de cada vez.
 */
@Composable
internal fun rememberHelpMedia(mediaId: String): HelpMediaState {
    var state by remember(mediaId) { mutableStateOf<HelpMediaState>(HelpMediaState.Loading) }

    LaunchedEffect(mediaId) {
        val clip = withContext(Dispatchers.IO) {
            readHelpMediaBytes(mediaId)?.let { bytes -> decodeHelpMedia(bytes) }
        }
        if (clip == null || clip.frameCount <= 0) {
            clip?.close()
            state = HelpMediaState.Unavailable
            return@LaunchedEffect
        }

        try {
            var index = 0
            while (true) {
                val frame = withContext(Dispatchers.Default) {
                    runCatching { clip.frameAt(index) }.getOrNull()
                }
                if (frame == null) {
                    // Quadro que não decodifica no meio da gravação é arquivo
                    // truncado: parar aqui deixaria a tela congelada num quadro
                    // sem dizer por quê.
                    state = HelpMediaState.Unavailable
                    return@LaunchedEffect
                }
                state = HelpMediaState.Frame(frame)

                if (clip.frameCount == 1) {
                    // Imagem parada não tem laço para rodar, mas o codec só pode
                    // ser fechado quando o tópico sair da tela.
                    awaitCancellation()
                }
                delay(clip.frameDelayMillis(index))
                index = (index + 1) % clip.frameCount
            }
        } finally {
            clip.close()
        }
    }

    return state
}
