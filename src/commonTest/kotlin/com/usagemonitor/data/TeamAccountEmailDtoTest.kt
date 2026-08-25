package com.usagemonitor.data

import com.usagemonitor.data.dto.TeamOverviewDto
import com.usagemonitor.data.mapper.toDomain
import com.usagemonitor.domain.entity.TeamAccountEmailSource
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TeamAccountEmailDtoTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `overview de servidor antigo continua desserializando sem campos de e-mail`() {
        val overview = json.decodeFromString<TeamOverviewDto>(
            """{"accounts":[{"accountKey":"account-a","label":"Pessoa","members":[],"rows":[]}]}"""
        )

        val account = overview.accounts.single()
        assertEquals("account-a", account.accountKey)
        assertNull(account.accountEmail)
        assertNull(account.emailSource)
    }

    @Test
    fun `overview novo leva e-mail e origem ao dominio`() {
        val overview = json.decodeFromString<TeamOverviewDto>(
            """{"accounts":[{"accountKey":"account-a","label":"Pessoa","accountEmail":"pessoa@empresa.com","emailSource":"reported","members":[],"rows":[]}]}"""
        )

        val account = overview.toDomain().single()
        assertEquals("pessoa@empresa.com", account.accountEmail)
        assertEquals(TeamAccountEmailSource.REPORTED, account.emailSource)
    }
}
