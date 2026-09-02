package com.usagemonitor.presentation.ui

import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.UsageForecast
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Issue #140: `deepSeekForecastText` escondia o texto (`null`) em 3 dos 4 estados de
 * [UsageForecast] e só produzia frase em `EstimatedExhaustionAt` — por isso a previsão do DeepSeek
 * "aparecia e sumia" quando o saldo parava de cair entre duas coletas. Este teste prova que os 4
 * estados sempre têm texto, e que os 3 que não são de saldo concordam literalmente com
 * `forecastLabel` (o mesmo rótulo que o card genérico já usa).
 */
class HistoryScreenFormattingTest {

    @Test
    fun `todo estado de forecast produz texto nao vazio`() {
        val forecasts = listOf(
            UsageForecast.InsufficientData,
            UsageForecast.NoGrowth,
            UsageForecast.ResetsBeforeExhaustion,
            UsageForecast.EstimatedExhaustionAt(Instant.parse("2026-05-01T00:00:00Z"))
        )

        forecasts.forEach { forecast ->
            assertTrue(
                deepSeekForecastText(forecast, AppLanguage.PT).isNotBlank(),
                "forecast $forecast não deveria produzir texto vazio"
            )
        }
    }

    @Test
    fun `insufficient data e no growth concordam com o card generico`() {
        assertEquals(
            forecastLabel(UsageForecast.InsufficientData, AppLanguage.PT),
            deepSeekForecastText(UsageForecast.InsufficientData, AppLanguage.PT)
        )
        assertEquals(
            forecastLabel(UsageForecast.NoGrowth, AppLanguage.PT),
            deepSeekForecastText(UsageForecast.NoGrowth, AppLanguage.PT)
        )
        assertEquals(
            forecastLabel(UsageForecast.ResetsBeforeExhaustion, AppLanguage.PT),
            deepSeekForecastText(UsageForecast.ResetsBeforeExhaustion, AppLanguage.PT)
        )
    }

    @Test
    fun `estimated exhaustion mantem frase propria de saldo`() {
        val forecast = UsageForecast.EstimatedExhaustionAt(Instant.parse("2026-05-01T00:00:00Z"))

        val text = deepSeekForecastText(forecast, AppLanguage.PT)

        assertTrue(text.contains("saldo"), "texto deveria falar de saldo, veio: $text")
    }

    /**
     * A contagem de dias entra no rótulo porque ela é a régua: `4,0×` sem dizer
     * acima de quê não permite julgar se o número merece atenção.
     */
    @Test
    fun `o rotulo da mediana diaria traz o fator e a contagem de dias`() {
        assertEquals("4,0× (3 dias)", dailyBaselineLabel(4.0, 3, AppLanguage.PT))
        assertEquals("4.0× (3 days)", dailyBaselineLabel(4.0, 3, AppLanguage.EN))
        assertEquals("1,2× (6 dias)", dailyBaselineLabel(1.24, 6, AppLanguage.PT))
    }

    /** O separador decimal vem do idioma, nunca do `Locale` da máquina que roda. */
    @Test
    fun `o fator nao depende do locale da jvm`() {
        assertEquals("2,6", formatSpikeFactor(2.55, AppLanguage.PT))
        assertEquals("2.6", formatSpikeFactor(2.55, AppLanguage.EN))
        assertEquals("10,0", formatSpikeFactor(10.0, AppLanguage.PT))
        assertEquals("0,0", formatSpikeFactor(-1.0, AppLanguage.PT))
    }
}
