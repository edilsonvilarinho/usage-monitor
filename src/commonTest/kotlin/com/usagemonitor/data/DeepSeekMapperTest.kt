package com.usagemonitor.data

import com.usagemonitor.data.dto.DeepSeekBalanceInfoDto
import com.usagemonitor.data.dto.DeepSeekBalanceResponse
import com.usagemonitor.data.mapper.DeepSeekMapper
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.DeepSeekQuotaLabels
import com.usagemonitor.domain.entity.UsageUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DeepSeekMapperTest {

    @Test
    fun `maps topped_up and granted balances to two quotas`() {
        val response = DeepSeekBalanceResponse(
            isAvailable = true,
            balanceInfos = listOf(
                DeepSeekBalanceInfoDto(
                    currency = "USD",
                    totalBalance = "12.50",
                    grantedBalance = "2.50",
                    toppedUpBalance = "10.00"
                )
            )
        )

        val stats = DeepSeekMapper.toUsageStats(response)

        assertEquals(ApiSource.DEEPSEEK, stats.source)
        assertEquals("DeepSeek", stats.apiName)
        assertEquals(2, stats.quotas.size)

        val saldo = stats.quotas[0]
        assertEquals(DeepSeekQuotaLabels.BALANCE,saldo.label)
        assertEquals(1000L, saldo.total)
        assertEquals(0L, saldo.used)
        assertEquals(UsageUnit.CURRENCY_USD, saldo.unit)

        val gratuito = stats.quotas[1]
        assertEquals(DeepSeekQuotaLabels.GRANTED,gratuito.label)
        assertEquals(250L, gratuito.total)
        assertEquals(0L, gratuito.used)
        assertEquals(UsageUnit.CURRENCY_USD, gratuito.unit)

        // Conta em dólar continua em dólar: regressão do default de QuotaInfo.
        assertEquals("USD", saldo.currencyCode)
        assertEquals("USD", gratuito.currencyCode)
    }

    /**
     * Issue #195: o mapper lia `currency` só para escolher o item e o descartava,
     * então o default "USD" prevalecia e uma conta em yuan aparecia com cifrão de
     * dólar — o número certo com o símbolo errado, por um fator de ~7.
     */
    @Test
    fun `carries the account currency into both quotas`() {
        val response = DeepSeekBalanceResponse(
            isAvailable = true,
            balanceInfos = listOf(
                DeepSeekBalanceInfoDto(
                    currency = "CNY",
                    totalBalance = "120.00",
                    grantedBalance = "20.00",
                    toppedUpBalance = "100.00"
                )
            )
        )

        val stats = DeepSeekMapper.toUsageStats(response)

        assertEquals(2, stats.quotas.size)
        assertEquals("CNY", stats.quotas[0].currencyCode)
        assertEquals("CNY", stats.quotas[1].currencyCode)
    }

    @Test
    fun `omits granted quota when granted balance is zero`() {
        val response = DeepSeekBalanceResponse(
            isAvailable = true,
            balanceInfos = listOf(
                DeepSeekBalanceInfoDto(
                    currency = "USD",
                    totalBalance = "8.00",
                    grantedBalance = "0.00",
                    toppedUpBalance = "8.00"
                )
            )
        )

        val stats = DeepSeekMapper.toUsageStats(response)

        assertEquals(1, stats.quotas.size)
        assertEquals(DeepSeekQuotaLabels.BALANCE,stats.quotas[0].label)
        assertEquals(800L, stats.quotas[0].total)
    }

    @Test
    fun `throws when account is unavailable`() {
        val response = DeepSeekBalanceResponse(
            isAvailable = false,
            balanceInfos = emptyList()
        )

        assertFailsWith<IllegalStateException> {
            DeepSeekMapper.toUsageStats(response)
        }
    }

    @Test
    fun `throws when balance_infos is empty`() {
        val response = DeepSeekBalanceResponse(
            isAvailable = true,
            balanceInfos = emptyList()
        )

        assertFailsWith<IllegalStateException> {
            DeepSeekMapper.toUsageStats(response)
        }
    }

    @Test
    fun `prefers USD currency when multiple currencies present`() {
        val response = DeepSeekBalanceResponse(
            isAvailable = true,
            balanceInfos = listOf(
                DeepSeekBalanceInfoDto("CNY", "100.00", "0.00", "100.00"),
                DeepSeekBalanceInfoDto("USD", "5.00", "0.00", "5.00")
            )
        )

        val stats = DeepSeekMapper.toUsageStats(response)

        assertEquals(500L, stats.quotas[0].total)
        // A moeda exibida é a do item escolhido, nunca a do descartado: com "CNY"
        // aqui, o valor em dólar apareceria com símbolo de yuan.
        assertEquals("USD", stats.quotas[0].currencyCode)
    }

    @Test
    fun `zero topped_up balance maps to zero cents`() {
        val response = DeepSeekBalanceResponse(
            isAvailable = true,
            balanceInfos = listOf(
                DeepSeekBalanceInfoDto("USD", "0.00", "0.00", "0.00")
            )
        )

        val stats = DeepSeekMapper.toUsageStats(response)

        assertEquals(1, stats.quotas.size)
        assertEquals(0L, stats.quotas[0].total)
    }
}
