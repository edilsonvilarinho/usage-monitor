package com.usagemonitor

import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.awt.Robot
import java.awt.Window
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/**
 * Captura da janela do app, em PNG.
 *
 * Interface e não função concreta porque `java.awt.Robot` **não é utilizável em
 * CI headless** — mesmo motivo do `UsageExportWriter` e do
 * `rememberClipboardWriter`: teste não pode depender de haver uma tela.
 */
fun interface WindowScreenshotCapturer {

    /**
     * Os bytes de um PNG, ou `null`.
     *
     * `null` é o resultado normal em três casos: não há janela, o ambiente não
     * tem tela, ou a captura falhou. Nenhum deles é erro — a captura é
     * *best-effort*, e um relatório sem imagem continua sendo um relatório.
     */
    fun capture(): ByteArray?
}

/** Capturer que nunca captura; default de composição sem tela. */
val NoWindowScreenshotCapturer = WindowScreenshotCapturer { null }

/**
 * Captura **os limites da janela**, nunca a tela inteira.
 *
 * O recorte é a diferença entre um diagnóstico e um vazamento: a tela inteira
 * traria o que mais estivesse aberto — outra janela, um e-mail, um terminal com
 * uma credencial na linha de comando — e o pacote vira o corpo de uma issue
 * pública.
 *
 * O que o `Robot` lê é o **conteúdo do monitor naquele retângulo**: se outra
 * janela estiver por cima da nossa, é ela que aparece. Não há como pedir ao
 * compositor o conteúdo próprio da janela sem passar pelo pipeline de desenho do
 * Swing, que não descreve o que o Skia do Compose desenhou.
 *
 * [window] é uma função e não a janela: a janela principal só existe depois da
 * composição, e capturá-la na construção daria sempre `null` — o mesmo motivo do
 * `DesktopUsageExportWriter`.
 */
class RobotWindowScreenshotCapturer(
    private val window: () -> Window?
) : WindowScreenshotCapturer {

    override fun capture(): ByteArray? {
        return runCatching {
            if (GraphicsEnvironment.isHeadless()) {
                return null
            }
            val target = window() ?: return null
            if (!target.isShowing) {
                return null
            }
            val bounds = target.bounds
            if (bounds.width <= 0 || bounds.height <= 0) {
                return null
            }

            val image = Robot().createScreenCapture(
                Rectangle(bounds.x, bounds.y, bounds.width, bounds.height)
            )
            val output = ByteArrayOutputStream()
            if (!ImageIO.write(image, "png", output)) {
                return null
            }
            output.toByteArray()
        }.getOrNull()
    }
}
