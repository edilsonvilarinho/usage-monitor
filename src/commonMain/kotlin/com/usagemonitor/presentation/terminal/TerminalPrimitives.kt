package com.usagemonitor.presentation.terminal

import com.usagemonitor.domain.entity.QuotaZone
import kotlin.time.Duration

/**
 * As primitivas do design system do modo terminal (`cli/readme.md`, "Primitivas")
 * que os comandos de leitura usam. Funções puras de dados para trechos com estilo.
 */
internal object TerminalPrimitives {

    /**
     * Barra de cota: células cheias e uma parcial em oitavos, na cor da fonte, e o
     * resto vazio em `Dim`, sempre visível. Pelo **piso**: a barra nunca passa do
     * número escrito ao lado. No ASCII, `[####----]` com a mesma largura total,
     * para a coluna seguinte não andar entre os dois modos.
     */
    fun quotaBar(percent: Int, width: Int, color: TerminalColor, glyphs: TerminalGlyphs): List<TerminalSpan> {
        val value = percent.coerceIn(0, 100)
        if (glyphs.barEighths.isEmpty()) {
            val inner = (width - glyphs.barOpen.length - glyphs.barClose.length).coerceAtLeast(1)
            val filled = value * inner / 100
            return listOf(
                TerminalSpan(glyphs.barOpen, TerminalColor.Muted),
                TerminalSpan(glyphs.barFull.toString().repeat(filled), color),
                TerminalSpan(glyphs.barEmpty.toString().repeat(inner - filled), TerminalColor.Dim),
                TerminalSpan(glyphs.barClose, TerminalColor.Muted)
            ).filter { span -> span.text.isNotEmpty() }
        }
        val eighths = value * width * 8 / 100
        val full = eighths / 8
        val partial = eighths % 8
        val filledText = glyphs.barFull.toString().repeat(full) + if (partial > 0) glyphs.barEighths[partial - 1].toString() else ""
        return listOf(
            TerminalSpan(filledText, color),
            TerminalSpan(glyphs.barEmpty.toString().repeat(width - filledText.length), TerminalColor.Dim)
        ).filter { span -> span.text.isNotEmpty() }
    }

    fun zoneColor(zone: QuotaZone): TerminalColor =
        when (zone) {
            QuotaZone.ON_TRACK -> TerminalColor.Ok
            QuotaZone.ATTENTION -> TerminalColor.Warn
            QuotaZone.CRITICAL -> TerminalColor.Crit
        }

    /**
     * A palavra de estado na cor dela. Sem cor, o crítico vai em maiúsculas: é o único
     * estado que precisa saltar aos olhos sem a ajuda do vermelho.
     */
    fun zoneWord(zone: QuotaZone, labels: TerminalLabels, profile: TerminalProfile): TerminalSpan {
        val word = labels.zone(zone)
        val text = if (zone == QuotaZone.CRITICAL && profile.palette == TerminalPalette.NONE) word.uppercase() else word
        return TerminalSpan(text, zoneColor(zone))
    }

    /**
     * Marca da fonte no começo da linha de cota: `●` na cor da fonte. No ASCII não há
     * marca, exceto `*` na linha em estado crítico.
     */
    fun sourceMark(color: TerminalColor, zone: QuotaZone?, glyphs: TerminalGlyphs): TerminalSpan {
        val mark = if (zone == QuotaZone.CRITICAL) glyphs.criticalMark else glyphs.sourceMark
        return TerminalSpan(mark, color)
    }

    /**
     * Rodapé de leitura: de onde e de quando é a leitura, e a contagem por estado, cada
     * estado na sua cor. Estado com contagem zero não aparece.
     */
    fun readingFooter(age: Duration, zones: List<QuotaZone?>, labels: TerminalLabels): TerminalLine {
        val counted = zones.filterNotNull()
        val spans = mutableListOf(
            TerminalSpan(labels.readByApp(age), TerminalColor.Muted),
            TerminalSpan(" · ${labels.quotaCount(zones.size)}", TerminalColor.Muted)
        )
        val parts = listOf(QuotaZone.CRITICAL, QuotaZone.ATTENTION, QuotaZone.ON_TRACK)
            .map { zone -> zone to counted.count { item -> item == zone } }
            .filter { (_, count) -> count > 0 }
        parts.forEachIndexed { index, (zone, count) ->
            spans += TerminalSpan(if (index == 0) ": " else ", ", TerminalColor.Muted)
            spans += TerminalSpan(labels.zoneCount(zone, count), zoneColor(zone))
        }
        return TerminalLine(spans)
    }
}
