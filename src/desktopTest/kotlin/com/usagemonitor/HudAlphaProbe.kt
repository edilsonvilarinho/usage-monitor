package com.usagemonitor

import java.awt.Color
import java.awt.GraphicsConfiguration
import java.awt.GraphicsEnvironment
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.SwingUtilities

/**
 * O que nenhum teste da suíte alcança: o sistema de janelas (issue #426).
 *
 * A suíte desenha numa cena fora de tela, sem janela AWT e sem gerenciador de
 * janelas, e o CI roda numa máquina Windows com **uma** tela ("Platform
 * reality", `CONTRIBUTING.md`, #342). O defeito da #426 só existe com dois
 * monitores de verdade: uma janela transparente só guarda a
 * `GraphicsConfiguration` de 32 bits no `GraphicsDevice` em que nasceu, e ao
 * cruzar para outro o AWT a troca pela padrão do destino — 24 bits, sem canal
 * alfa, retângulo preto.
 *
 * Esta sonda é o portão manual que o `CONTRIBUTING.md` descreve como "abra o
 * build no Linux antes do merge", só que medido em vez de olhado. Ela exercita
 * a **função de produção** ([translucencyCapableConfigurationFor]) e responde
 * duas perguntas, por monitor:
 *
 * 1. nascendo na configuração escolhida pelo app, a janela tem 32 bits?
 * 2. depois de cruzar para cada outro monitor e voltar, quantos bits restam?
 *
 * A pergunta 2 **não** é um defeito a corrigir aqui: é o comportamento do AWT
 * que a HUD contorna renascendo. A sonda a mede para que a mudança de um
 * ambiente — driver, compositor, versão do Java — apareça como número.
 *
 * ```
 * ./gradlew probeHudAlpha
 * ```
 *
 * Sai com código 1 quando a pergunta 1 falha em algum monitor, que é a garantia
 * de que a HUD ainda nasce transparente. Com um monitor só, a pergunta 2 é
 * pulada e a sonda ainda vale pela 1.
 */
object HudAlphaProbe {

    private const val WINDOW_WIDTH = 592
    private const val WINDOW_HEIGHT = 436
    private const val SETTLE_MILLIS = 1_500L
    private const val ALPHA_PIXEL_SIZE = 32

    @JvmStatic
    fun main(args: Array<String>) {
        val environment = GraphicsEnvironment.getLocalGraphicsEnvironment()
        val devices = environment.screenDevices
        println("monitores: ${devices.size}")
        devices.forEach { device ->
            val default = device.defaultConfiguration
            println(
                "  ${device.getIDstring()}  padrão=${default.colorModel.pixelSize}bpp  " +
                    "alfaPorPixel=${device.isWindowTranslucencySupported(
                        java.awt.GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSLUCENT
                    )}  limites=${default.bounds}"
            )
        }

        var failures = 0
        for (device in devices) {
            val id = device.getIDstring()
            val chosen = translucencyCapableConfigurationFor(id)
            if (chosen == null) {
                println("FALHA $id: o app não resolveu nenhuma GraphicsConfiguration")
                failures += 1
                continue
            }
            val born = measureBornOn(chosen)
            val ok = born == ALPHA_PIXEL_SIZE
            println("${if (ok) "ok   " else "FALHA"} $id nasce com ${born}bpp (esperado $ALPHA_PIXEL_SIZE)")
            if (!ok) {
                failures += 1
            }
            for (other in devices) {
                if (other.getIDstring() == id) {
                    continue
                }
                val crossed = measureCrossingTo(chosen, other.defaultConfiguration.bounds)
                println("      ao cruzar para ${other.getIDstring()}: ${crossed}bpp (o AWT troca; a HUD renasce)")
            }
        }

        println(if (failures == 0) "RESULTADO: ok" else "RESULTADO: $failures monitor(es) sem alfa no nascimento")
        System.exit(if (failures == 0) 0 else 1)
    }

    /** Bits por pixel de uma janela transparente **nascida** na configuração dada. */
    private fun measureBornOn(configuration: GraphicsConfiguration): Int? =
        withProbeWindow(configuration, configuration.bounds.x + 100, configuration.bounds.y + 100) { frame ->
            frame.graphicsConfiguration?.colorModel?.pixelSize
        }

    /** Bits por pixel depois de a janela ser movida para dentro de outro monitor. */
    private fun measureCrossingTo(configuration: GraphicsConfiguration, target: java.awt.Rectangle): Int? =
        withProbeWindow(configuration, configuration.bounds.x + 100, configuration.bounds.y + 100) { frame ->
            onEdt { frame.setLocation(target.x + 100, target.y + 100) }
            Thread.sleep(SETTLE_MILLIS)
            frame.graphicsConfiguration?.colorModel?.pixelSize
        }

    private fun <T> withProbeWindow(
        configuration: GraphicsConfiguration,
        x: Int,
        y: Int,
        measure: (JFrame) -> T
    ): T {
        lateinit var frame: JFrame
        onEdt {
            frame = JFrame("HudAlphaProbe", configuration).apply {
                isUndecorated = true
                isAlwaysOnTop = true
                setSize(WINDOW_WIDTH, WINDOW_HEIGHT)
                setLocation(x, y)
                background = Color(0, 0, 0, 0)
                contentPane = JPanel().apply { isOpaque = false }
                isVisible = true
            }
        }
        Thread.sleep(SETTLE_MILLIS)
        try {
            return measure(frame)
        } finally {
            onEdt { frame.dispose() }
        }
    }

    private fun onEdt(block: () -> Unit) = SwingUtilities.invokeAndWait(block)
}
