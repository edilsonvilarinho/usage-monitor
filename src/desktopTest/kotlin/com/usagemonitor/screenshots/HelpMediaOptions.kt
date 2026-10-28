package com.usagemonitor.screenshots

import com.usagemonitor.domain.entity.AppLanguage
import java.io.File
import javax.imageio.ImageIO

/**
 * O que o gerador de demos grava. O padrão é o da janela de ajuda: GIF em português de todos os tópicos.
 *
 * [HelpMediaFormat.FRAMES] é o do tour em vídeo (#505): o GIF passa por uma paleta de 255 cores e por uma redução à
 * metade, o que num quadro de 1920×1080 vira texto borrado. A sequência de PNG guarda cada quadro a 2×, sem paleta, e
 * a duração de cada um num `frames.json` ao lado.
 */
internal enum class HelpMediaFormat { GIF, FRAMES }

internal class HelpMediaOptions(
    val outputDir: File,
    val language: AppLanguage,
    val format: HelpMediaFormat,
    /** `null` grava todos; o filtro usa o `mediaId` de [com.usagemonitor.help.HelpCatalog]. */
    val topics: Set<String>?
) {
    /** Os dois idiomas convivem na mesma pasta de saída: `<saída>/<idioma>/<mediaId>/`. */
    fun framesDir(mediaId: String): File = File(File(outputDir, language.name.lowercase()), mediaId)
}

internal fun parseHelpMediaArgs(args: Array<String>): HelpMediaOptions {
    val outputDir = File(args.firstOrNull { arg -> !arg.startsWith("--") } ?: "src/desktopMain/resources/help")
    val languageCode = args.firstOrNull { arg -> arg.startsWith("--language=") }?.removePrefix("--language=") ?: "pt"
    val language = AppLanguage.entries.firstOrNull { entry -> entry.name.equals(languageCode, ignoreCase = true) }
        ?: throw IllegalArgumentException("Idioma desconhecido: $languageCode (use pt ou en)")
    val topics = if ("--history" in args) {
        setOf("history")
    } else {
        args.firstOrNull { arg -> arg.startsWith("--topic=") }?.removePrefix("--topic=")?.split(",")?.toSet()
    }
    val format = if ("--frames" in args) HelpMediaFormat.FRAMES else HelpMediaFormat.GIF
    return HelpMediaOptions(outputDir, language, format, topics)
}

/** Grava `NNN.png` e `frames.json` (`[{"file","delayMillis"}]`); uma tomada nova apaga os quadros da anterior. */
internal object FrameSequenceWriter {

    fun write(dir: File, frames: List<GifFrame>) {
        require(frames.isNotEmpty()) { "Sequência sem quadros." }
        dir.mkdirs()
        dir.listFiles()?.filter { file -> file.name.endsWith(".png") || file.name == MANIFEST }?.forEach { it.delete() }
        val entries = frames.mapIndexed { index, frame ->
            val name = "%03d.png".format(index)
            ImageIO.write(frame.image, "png", File(dir, name))
            """{"file":"$name","delayMillis":${frame.delayMillis}}"""
        }
        File(dir, MANIFEST).writeText(entries.joinToString(prefix = "[", postfix = "]", separator = ","))
    }

    private const val MANIFEST = "frames.json"
}
