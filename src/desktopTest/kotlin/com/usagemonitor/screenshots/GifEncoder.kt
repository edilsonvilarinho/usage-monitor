package com.usagemonitor.screenshots

import org.jetbrains.skia.EncodedImageFormat
import java.awt.image.BufferedImage
import java.awt.image.DataBufferByte
import java.awt.image.IndexColorModel
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageTypeSpecifier
import javax.imageio.metadata.IIOMetadataNode
import javax.imageio.stream.MemoryCacheImageOutputStream
import org.jetbrains.skia.Image as SkiaImage

/**
 * Um quadro do GIF e quanto tempo ele fica na tela.
 *
 * O tempo é por quadro justamente para as telas paradas custarem **um** quadro
 * com espera longa, em vez de quinze quadros idênticos — é o que segura o
 * tamanho do arquivo num tour de vinte segundos.
 */
internal class GifFrame(val image: BufferedImage, val delayMillis: Int)

/**
 * Escrita de GIF animado sem dependência nova.
 *
 * `ffmpeg` não é opção: não existe na máquina de quem gera as imagens do README,
 * e exigi-lo transformaria "regerar o GIF" numa instalação. O `ImageWriter` de
 * GIF do JDK dá conta, mas do jeito ingênuo — um `BufferedImage` RGB por quadro —
 * ele quantiza cada quadro isolado e grava a tela inteira toda vez: 186 quadros
 * do tour dessa forma deram **7 MB**, com o cartaz do README recarregando isso a
 * cada visita.
 *
 * Por isso aqui há duas otimizações que a API não faz sozinha:
 *
 * 1. **Paleta global** por median-cut sobre uma amostra de todos os quadros. Uma
 *    paleta só para o arquivo inteiro também mata o piscar de cor entre quadros.
 * 2. **Quadros delta**: o que não mudou vira índice transparente com
 *    `disposalMethod = doNotDispose`, e o quadro é recortado no retângulo que
 *    mudou. Uma pausa longa custa alguns bytes em vez de um quadro inteiro.
 */
internal object GifEncoder {

    private const val METADATA_FORMAT = "javax_imageio_gif_image_1.0"

    /** O writer conta a espera em centésimos de segundo. */
    private const val MILLIS_PER_CENTISECOND = 10

    /** 255 cores mais o índice reservado à transparência do delta. */
    private const val PALETTE_COLORS = 255
    private const val TRANSPARENT_INDEX = PALETTE_COLORS

    fun write(file: File, frames: List<GifFrame>) {
        require(frames.isNotEmpty()) { "GIF sem quadros." }

        val width = frames.first().image.width
        val height = frames.first().image.height
        val palette = buildPalette(frames)
        val lookup = PaletteLookup(palette)
        val colorModel = indexColorModel(palette)

        val writer = ImageIO.getImageWritersByFormatName("gif").next()
            ?: error("Nenhum ImageWriter de GIF registrado nesta JVM.")
        val params = writer.defaultWriteParam

        val pixels = IntArray(width * height)
        var previousIndices: ByteArray? = null

        // `FileImageOutputStream` grava por cima sem truncar: com o delta o
        // arquivo novo é bem menor que o antigo, e a cauda do anterior ficaria
        // depois do trailer — 1,5 MB de GIF válido dentro de um arquivo de 7 MB.
        FileOutputStream(file).use { stream ->
            val output = MemoryCacheImageOutputStream(stream)
            writer.output = output
            writer.prepareWriteSequence(null)

            frames.forEachIndexed { index, frame ->
                require(frame.image.width == width && frame.image.height == height) {
                    "Quadro $index tem tamanho diferente do primeiro."
                }
                frame.image.getRGB(0, 0, width, height, pixels, 0, width)

                val indices = ByteArray(width * height)
                for (position in pixels.indices) {
                    indices[position] = lookup.indexOf(pixels[position]).toByte()
                }

                val region = changedRegion(indices, previousIndices, width, height)
                val tile = buildTile(indices, previousIndices, width, region, colorModel)
                previousIndices = indices

                val metadata = writer.getDefaultImageMetadata(ImageTypeSpecifier(tile), params)
                val root = metadata.getAsTree(METADATA_FORMAT) as IIOMetadataNode

                val control = root.childNamed("GraphicControlExtension")
                control.setAttribute("disposalMethod", "doNotDispose")
                control.setAttribute("userInputFlag", "FALSE")
                control.setAttribute("transparentColorFlag", "TRUE")
                control.setAttribute("transparentColorIndex", TRANSPARENT_INDEX.toString())
                control.setAttribute(
                    "delayTime",
                    (frame.delayMillis / MILLIS_PER_CENTISECOND).coerceAtLeast(1).toString()
                )

                val descriptor = root.childNamed("ImageDescriptor")
                descriptor.setAttribute("imageLeftPosition", region.left.toString())
                descriptor.setAttribute("imageTopPosition", region.top.toString())
                descriptor.setAttribute("imageWidth", region.width.toString())
                descriptor.setAttribute("imageHeight", region.height.toString())
                descriptor.setAttribute("interlaceFlag", "FALSE")

                // O laço infinito é uma extensão da Netscape e vale para o arquivo
                // todo, mas só pode ser declarada no primeiro quadro.
                if (index == 0) {
                    val extensions = root.childNamed("ApplicationExtensions")
                    val netscape = IIOMetadataNode("ApplicationExtension")
                    netscape.setAttribute("applicationID", "NETSCAPE")
                    netscape.setAttribute("authenticationCode", "2.0")
                    netscape.userObject = byteArrayOf(0x1, 0x0, 0x0)
                    extensions.appendChild(netscape)
                }

                metadata.setFromTree(METADATA_FORMAT, root)
                writer.writeToSequence(IIOImage(tile, null, metadata), params)
            }

            writer.endWriteSequence()
            output.flush()
            output.close()
        }

        writer.dispose()
    }

    /** Retângulo que mudou desde o quadro anterior. */
    private class Region(val left: Int, val top: Int, val width: Int, val height: Int)

    private fun changedRegion(
        indices: ByteArray,
        previous: ByteArray?,
        width: Int,
        height: Int
    ): Region {
        if (previous == null) {
            return Region(0, 0, width, height)
        }

        var left = width
        var top = height
        var right = -1
        var bottom = -1

        for (y in 0 until height) {
            val rowStart = y * width
            for (x in 0 until width) {
                if (indices[rowStart + x] != previous[rowStart + x]) {
                    if (x < left) left = x
                    if (x > right) right = x
                    if (y < top) top = y
                    if (y > bottom) bottom = y
                }
            }
        }

        // Nada mudou: o GIF não aceita quadro vazio, então vai um pixel só.
        if (right < 0) {
            return Region(0, 0, 1, 1)
        }
        return Region(left, top, right - left + 1, bottom - top + 1)
    }

    private fun buildTile(
        indices: ByteArray,
        previous: ByteArray?,
        width: Int,
        region: Region,
        colorModel: IndexColorModel
    ): BufferedImage {
        val tile = BufferedImage(region.width, region.height, BufferedImage.TYPE_BYTE_INDEXED, colorModel)
        val target = (tile.raster.dataBuffer as DataBufferByte).data

        for (y in 0 until region.height) {
            val sourceRow = (region.top + y) * width + region.left
            val targetRow = y * region.width
            for (x in 0 until region.width) {
                val value = indices[sourceRow + x]
                target[targetRow + x] = when {
                    previous != null && previous[sourceRow + x] == value -> TRANSPARENT_INDEX.toByte()
                    else -> value
                }
            }
        }

        return tile
    }

    private fun indexColorModel(palette: IntArray): IndexColorModel {
        val reds = ByteArray(256)
        val greens = ByteArray(256)
        val blues = ByteArray(256)
        palette.forEachIndexed { index, color ->
            reds[index] = (color shr 16 and 0xFF).toByte()
            greens[index] = (color shr 8 and 0xFF).toByte()
            blues[index] = (color and 0xFF).toByte()
        }
        return IndexColorModel(8, 256, reds, greens, blues, TRANSPARENT_INDEX)
    }

    /** Nó filho pelo nome, criando-o quando o writer não o trouxe por default. */
    private fun IIOMetadataNode.childNamed(name: String): IIOMetadataNode {
        for (index in 0 until length) {
            val child = item(index)
            if (child.nodeName.equals(name, ignoreCase = true)) {
                return child as IIOMetadataNode
            }
        }
        val created = IIOMetadataNode(name)
        appendChild(created)
        return created
    }
}

// --- Paleta ------------------------------------------------------------------

/**
 * Median-cut sobre uma amostra dos quadros.
 *
 * Amostrar em vez de ler tudo: um tour tem ~150 quadros de 1100x720, e a paleta
 * de uma interface (poucas cores chapadas mais alguns degradês) já sai estável
 * com uma fração dos pixels.
 */
private fun buildPalette(frames: List<GifFrame>): IntArray {
    val frameStride = (frames.size / 24).coerceAtLeast(1)
    val pixelStride = 11
    val samples = ArrayList<Int>()

    frames.forEachIndexed { index, frame ->
        if (index % frameStride != 0) {
            return@forEachIndexed
        }
        val width = frame.image.width
        val height = frame.image.height
        val row = IntArray(width)
        for (y in 0 until height step 4) {
            frame.image.getRGB(0, y, width, 1, row, 0, width)
            for (x in 0 until width step pixelStride) {
                samples.add(row[x] and 0xFFFFFF)
            }
        }
    }

    return medianCut(samples.toIntArray(), 255)
}

/** Fatia contígua das amostras já ordenada por algum canal, com o volume dela. */
private class ColorBox(val from: Int, val to: Int, val volume: Int, val widestChannel: Int)

private fun medianCut(samples: IntArray, colorCount: Int): IntArray {
    if (samples.isEmpty()) {
        return IntArray(colorCount)
    }

    val boxes = ArrayList<ColorBox>()
    boxes.add(boxOf(samples, 0, samples.size - 1))

    // O volume fica memorizado na caixa: recalculá-lo a cada corte custaria uma
    // passada por todas as amostras vezes as 255 iterações.
    while (boxes.size < colorCount) {
        var targetIndex = -1
        var targetVolume = -1
        boxes.forEachIndexed { index, box ->
            if (box.to > box.from && box.volume > targetVolume) {
                targetIndex = index
                targetVolume = box.volume
            }
        }
        if (targetIndex < 0) {
            break
        }

        val box = boxes.removeAt(targetIndex)
        sortSlice(samples, box.from..box.to, box.widestChannel)

        val middle = (box.from + box.to) / 2
        boxes.add(boxOf(samples, box.from, middle))
        boxes.add(boxOf(samples, middle + 1, box.to))
    }

    return IntArray(colorCount) { index ->
        val box = boxes.getOrNull(index) ?: return@IntArray 0
        averageOf(samples, box.from..box.to)
    }
}

private fun boxOf(samples: IntArray, from: Int, to: Int): ColorBox {
    val ranges = channelRanges(samples, from..to)
    val widest = when {
        ranges[0] >= ranges[1] && ranges[0] >= ranges[2] -> 0
        ranges[1] >= ranges[2] -> 1
        else -> 2
    }
    return ColorBox(from, to, ranges[0] * ranges[1] * ranges[2], widest)
}

/** Amplitude de vermelho, verde e azul dentro da caixa. */
private fun channelRanges(samples: IntArray, box: IntRange): IntArray {
    var minRed = 255; var maxRed = 0
    var minGreen = 255; var maxGreen = 0
    var minBlue = 255; var maxBlue = 0

    for (index in box) {
        val color = samples[index]
        val red = color shr 16 and 0xFF
        val green = color shr 8 and 0xFF
        val blue = color and 0xFF
        if (red < minRed) minRed = red
        if (red > maxRed) maxRed = red
        if (green < minGreen) minGreen = green
        if (green > maxGreen) maxGreen = green
        if (blue < minBlue) minBlue = blue
        if (blue > maxBlue) maxBlue = blue
    }

    return intArrayOf(maxRed - minRed + 1, maxGreen - minGreen + 1, maxBlue - minBlue + 1)
}

/**
 * Ordena a fatia pelo canal escolhido.
 *
 * O par (chave, cor) vai empacotado num `Long` para reusar o sort primitivo do
 * JDK — ordenar `IntArray` por um critério exigiria boxing de centenas de
 * milhares de amostras a cada corte.
 */
private fun sortSlice(samples: IntArray, box: IntRange, channel: Int) {
    val shift = (2 - channel) * 8
    val packed = LongArray(box.last - box.first + 1)
    for (index in packed.indices) {
        val color = samples[box.first + index]
        val key = (color shr shift and 0xFF).toLong()
        packed[index] = (key shl 32) or color.toLong()
    }
    packed.sort()
    for (index in packed.indices) {
        samples[box.first + index] = (packed[index] and 0xFFFFFFFFL).toInt()
    }
}

private fun averageOf(samples: IntArray, box: IntRange): Int {
    var red = 0L
    var green = 0L
    var blue = 0L
    for (index in box) {
        val color = samples[index]
        red += (color shr 16 and 0xFF).toLong()
        green += (color shr 8 and 0xFF).toLong()
        blue += (color and 0xFF).toLong()
    }
    val count = (box.last - box.first + 1).toLong()
    return ((red / count).toInt() shl 16) or ((green / count).toInt() shl 8) or (blue / count).toInt()
}

/**
 * Cor → índice da paleta, com cache por RGB555.
 *
 * Sem o cache seriam 255 comparações por pixel, vezes ~120 milhões de pixels no
 * tour inteiro.
 */
private class PaletteLookup(private val palette: IntArray) {

    private val cache = IntArray(1 shl 15) { -1 }

    fun indexOf(color: Int): Int {
        val key = (color shr 9 and 0x7C00) or (color shr 6 and 0x03E0) or (color shr 3 and 0x001F)
        val cached = cache[key]
        if (cached >= 0) {
            return cached
        }

        val red = color shr 16 and 0xFF
        val green = color shr 8 and 0xFF
        val blue = color and 0xFF

        var best = 0
        var bestDistance = Int.MAX_VALUE
        palette.forEachIndexed { index, candidate ->
            val deltaRed = red - (candidate shr 16 and 0xFF)
            val deltaGreen = green - (candidate shr 8 and 0xFF)
            val deltaBlue = blue - (candidate and 0xFF)
            val distance = deltaRed * deltaRed + deltaGreen * deltaGreen + deltaBlue * deltaBlue
            if (distance < bestDistance) {
                bestDistance = distance
                best = index
            }
        }

        cache[key] = best
        return best
    }
}

// --- Conversão de quadros ----------------------------------------------------

/**
 * Skia → AWT pela ponte do PNG.
 *
 * `readPixels` seria mais rápido, mas exigiria tratar formato e ordem de canais
 * na mão; o PNG é o caminho que o gerador de capturas já usa e prova funcionar.
 */
internal fun SkiaImage.toBufferedImage(): BufferedImage {
    val data = encodeToData(EncodedImageFormat.PNG) ?: error("Falha ao codificar o quadro em PNG.")
    return ImageIO.read(ByteArrayInputStream(data.bytes))
        ?: error("Falha ao decodificar o quadro em BufferedImage.")
}

/**
 * Reduz o quadro à metade com média de blocos 2x2.
 *
 * A cena é renderizada em 2x e reduzida aqui: é o mesmo supersampling das
 * capturas PNG, e sem ele o texto do GIF fica com serrilhado. A média de bloco
 * é exata para o fator 2 — `Graphics2D` com interpolação bilinear amostra a
 * vizinhança e borra mais.
 */
internal fun BufferedImage.downsampleByTwo(): BufferedImage {
    val targetWidth = width / 2
    val targetHeight = height / 2
    val target = BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB)

    val sourceRow = IntArray(width * 2)
    val targetRow = IntArray(targetWidth)

    for (y in 0 until targetHeight) {
        getRGB(0, y * 2, width, 2, sourceRow, 0, width)
        for (x in 0 until targetWidth) {
            val topLeft = sourceRow[x * 2]
            val topRight = sourceRow[x * 2 + 1]
            val bottomLeft = sourceRow[width + x * 2]
            val bottomRight = sourceRow[width + x * 2 + 1]
            targetRow[x] = averageOfFour(topLeft, topRight, bottomLeft, bottomRight)
        }
        target.setRGB(0, y, targetWidth, 1, targetRow, 0, targetWidth)
    }

    return target
}

private fun averageOfFour(a: Int, b: Int, c: Int, d: Int): Int {
    val red = ((a shr 16 and 0xFF) + (b shr 16 and 0xFF) + (c shr 16 and 0xFF) + (d shr 16 and 0xFF)) / 4
    val green = ((a shr 8 and 0xFF) + (b shr 8 and 0xFF) + (c shr 8 and 0xFF) + (d shr 8 and 0xFF)) / 4
    val blue = ((a and 0xFF) + (b and 0xFF) + (c and 0xFF) + (d and 0xFF)) / 4
    return (red shl 16) or (green shl 8) or blue
}
