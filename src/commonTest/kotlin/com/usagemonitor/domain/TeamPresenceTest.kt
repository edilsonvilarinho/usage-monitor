package com.usagemonitor.domain

import com.usagemonitor.domain.entity.ACTIVE_SESSION_WINDOW_MILLIS
import com.usagemonitor.domain.entity.CliSessionSummary
import com.usagemonitor.domain.entity.PRESENCE_MAX_CLOCK_OFFSET_MILLIS
import com.usagemonitor.domain.entity.PRESENCE_ONLINE_WINDOW_MILLIS
import com.usagemonitor.domain.entity.TeamMemberUsage
import com.usagemonitor.domain.entity.hasSuspectClockSkew
import com.usagemonitor.domain.entity.toTeamPresence
import com.usagemonitor.domain.repository.InMemoryTeamServerClockOffset
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private val NOW = Instant.fromEpochMilliseconds(1_800_000_000_000)

private fun session(
    sessionId: String = "session-1",
    lastTsMillis: Long = NOW.toEpochMilliseconds()
): CliSessionSummary {
    val lastTs = Instant.fromEpochMilliseconds(lastTsMillis)
    return CliSessionSummary(
        sessionId = sessionId,
        filePath = "",
        firstTs = lastTs,
        lastTs = lastTs
    )
}

private fun member(
    deviceId: String = "device-1",
    alias: String = "edilson",
    lastSeenAtMillis: Long? = NOW.toEpochMilliseconds(),
    sessions: List<CliSessionSummary> = emptyList(),
    accountKey: String? = null,
    accountLabel: String? = null
): TeamMemberUsage {
    return TeamMemberUsage(
        deviceId = deviceId,
        alias = alias,
        lastSeenAt = lastSeenAtMillis?.let { millis -> Instant.fromEpochMilliseconds(millis) },
        sessions = sessions,
        accountKey = accountKey,
        accountLabel = accountLabel
    )
}

class TeamPresenceTest {

    @Test
    fun `a batida exatamente na fronteira da janela ainda conta como online`() {
        val onBoundary = member(
            lastSeenAtMillis = NOW.toEpochMilliseconds() - PRESENCE_ONLINE_WINDOW_MILLIS
        )

        val entry = listOf(onBoundary).toTeamPresence(NOW).single()

        assertTrue(entry.isOnline)
    }

    @Test
    fun `um milissegundo alem da janela ja e offline`() {
        val expired = member(
            lastSeenAtMillis = NOW.toEpochMilliseconds() - PRESENCE_ONLINE_WINDOW_MILLIS - 1
        )

        val entry = listOf(expired).toTeamPresence(NOW).single()

        assertFalse(entry.isOnline)
    }

    @Test
    fun `quem nunca reportou fica offline em vez de quebrar`() {
        val entry = listOf(member(lastSeenAtMillis = null)).toTeamPresence(NOW).single()

        assertFalse(entry.isOnline)
    }

    @Test
    fun `so conta como trabalhando quem tem turno dentro da janela ativa`() {
        val working = member(
            deviceId = "device-working",
            sessions = listOf(session(lastTsMillis = NOW.toEpochMilliseconds() - 60_000))
        )
        val idle = member(
            deviceId = "device-idle",
            sessions = listOf(
                session(lastTsMillis = NOW.toEpochMilliseconds() - ACTIVE_SESSION_WINDOW_MILLIS - 1)
            )
        )

        val entries = listOf(working, idle).toTeamPresence(NOW).associateBy { it.deviceId }

        assertTrue(entries.getValue("device-working").isWorkingNow)
        assertFalse(entries.getValue("device-idle").isWorkingNow)
    }

    @Test
    fun `activeSessionCount conta so as sessoes dentro da janela`() {
        val mixed = member(
            sessions = listOf(
                session(sessionId = "recente-1", lastTsMillis = NOW.toEpochMilliseconds() - 1_000),
                session(sessionId = "recente-2", lastTsMillis = NOW.toEpochMilliseconds() - 2_000),
                session(
                    sessionId = "antiga",
                    lastTsMillis = NOW.toEpochMilliseconds() - ACTIVE_SESSION_WINDOW_MILLIS - 5_000
                )
            )
        )

        val entry = listOf(mixed).toTeamPresence(NOW).single()

        assertEquals(2, entry.activeSessionCount)
    }

    @Test
    fun `a ordem coloca trabalhando antes de online parado e offline por ultimo`() {
        val offline = member(
            deviceId = "device-offline",
            alias = "aaa-offline",
            lastSeenAtMillis = NOW.toEpochMilliseconds() - PRESENCE_ONLINE_WINDOW_MILLIS - 10_000
        )
        val onlineIdle = member(deviceId = "device-idle", alias = "bbb-idle")
        val working = member(
            deviceId = "device-working",
            alias = "zzz-working",
            sessions = listOf(session(lastTsMillis = NOW.toEpochMilliseconds() - 1_000))
        )

        val order = listOf(offline, onlineIdle, working)
            .toTeamPresence(NOW)
            .map { entry -> entry.deviceId }

        assertEquals(listOf("device-working", "device-idle", "device-offline"), order)
    }

    @Test
    fun `a ordem e estavel entre leituras iguais`() {
        // Requisito anti-flicker: duas classificacoes da mesma entrada produzem
        // listas iguais, senao o StateFlow reemitiria a cada tique.
        val members = listOf(
            member(deviceId = "device-b", alias = "mesmo"),
            member(deviceId = "device-a", alias = "mesmo"),
            member(deviceId = "device-c", alias = "mesmo")
        )

        assertEquals(members.toTeamPresence(NOW), members.toTeamPresence(NOW))
    }

    @Test
    fun `o alias desempata quem tem a mesma atividade`() {
        val zeta = member(deviceId = "device-z", alias = "Zeta")
        val alfa = member(deviceId = "device-a", alias = "alfa")

        val order = listOf(zeta, alfa).toTeamPresence(NOW).map { entry -> entry.alias }

        assertEquals(listOf("alfa", "Zeta"), order)
    }

    @Test
    fun `carimbo no futuro alem da tolerancia denuncia desvio de relogio`() {
        val ahead = member(
            lastSeenAtMillis = NOW.toEpochMilliseconds() + PRESENCE_ONLINE_WINDOW_MILLIS + 1
        )

        assertTrue(listOf(ahead).hasSuspectClockSkew(NOW))
    }

    @Test
    fun `carimbo dentro da tolerancia nao denuncia desvio`() {
        val slightlyAhead = member(
            lastSeenAtMillis = NOW.toEpochMilliseconds() + PRESENCE_ONLINE_WINDOW_MILLIS
        )

        assertFalse(listOf(slightlyAhead).hasSuspectClockSkew(NOW))
    }

    @Test
    fun `lista sem carimbo nenhum nao denuncia desvio`() {
        assertFalse(listOf(member(lastSeenAtMillis = null)).hasSuspectClockSkew(NOW))
    }

    @Test
    fun `a referencia corrigida salva a classificacao de um cliente adiantado`() {
        // Servidor 10 min atras do cliente: sem correcao, todo carimbo cairia
        // fora da janela de 90s e o time inteiro apareceria offline.
        val serverNow = Instant.fromEpochMilliseconds(NOW.toEpochMilliseconds() - 10L * 60 * 1_000)
        val offset = InMemoryTeamServerClockOffset()
        offset.record(serverNow = serverNow, localNow = NOW)

        val stampedByServer = member(lastSeenAtMillis = serverNow.toEpochMilliseconds())
        val referenceNow = Instant.fromEpochMilliseconds(
            NOW.toEpochMilliseconds() + offset.offsetMillis
        )

        assertFalse(listOf(stampedByServer).toTeamPresence(NOW).single().isOnline)
        assertTrue(listOf(stampedByServer).toTeamPresence(referenceNow).single().isOnline)
    }

    @Test
    fun `desvio implausivel e descartado em vez de aplicado`() {
        val offset = InMemoryTeamServerClockOffset()
        val absurd = Instant.fromEpochMilliseconds(
            NOW.toEpochMilliseconds() + PRESENCE_MAX_CLOCK_OFFSET_MILLIS + 1
        )

        offset.record(serverNow = absurd, localNow = NOW)

        assertEquals(0L, offset.offsetMillis)
    }

    @Test
    fun `a ultima medida vale sobre a anterior`() {
        val offset = InMemoryTeamServerClockOffset()
        offset.record(
            serverNow = Instant.fromEpochMilliseconds(NOW.toEpochMilliseconds() + 5_000),
            localNow = NOW
        )

        offset.record(
            serverNow = Instant.fromEpochMilliseconds(NOW.toEpochMilliseconds() - 2_000),
            localNow = NOW
        )

        assertEquals(-2_000L, offset.offsetMillis)
    }

    @Test
    fun `a conta do integrante sobrevive a classificacao`() {
        val entry = listOf(member(accountKey = "account-1")).toTeamPresence(NOW).single()

        assertEquals("account-1", entry.accountKey)
        assertEquals("account-1/device-1", entry.memberKey)
    }

    @Test
    fun `a conta ordena antes do estado`() {
        // Caso que a ordem antiga errava: quem estava trabalhando puxava a conta
        // dele para o topo, e a faixa da conta mudava de lugar sozinha.
        val workingOnZeta = member(
            deviceId = "device-zeta",
            alias = "aaa",
            accountKey = "account-zeta",
            accountLabel = "zeta@empresa.com",
            sessions = listOf(session(lastTsMillis = NOW.toEpochMilliseconds() - 1_000))
        )
        val offlineOnAlfa = member(
            deviceId = "device-alfa",
            alias = "zzz",
            accountKey = "account-alfa",
            accountLabel = "alfa@empresa.com",
            lastSeenAtMillis = NOW.toEpochMilliseconds() - PRESENCE_ONLINE_WINDOW_MILLIS - 10_000
        )

        val order = listOf(workingOnZeta, offlineOnAlfa)
            .toTeamPresence(NOW)
            .map { entry -> entry.deviceId }

        assertEquals(listOf("device-alfa", "device-zeta"), order)
    }

    @Test
    fun `conta sem rotulo vai depois das identificadas`() {
        val unlabeled = member(
            deviceId = "device-sem-rotulo",
            accountKey = "aaa-account",
            accountLabel = null
        )
        val labeled = member(
            deviceId = "device-com-rotulo",
            accountKey = "zzz-account",
            accountLabel = "zeta@empresa.com"
        )

        val order = listOf(unlabeled, labeled)
            .toTeamPresence(NOW)
            .map { entry -> entry.deviceId }

        assertEquals(listOf("device-com-rotulo", "device-sem-rotulo"), order)
    }

    @Test
    fun `o heartbeat nao reordena a lista`() {
        // Reproducao direta do defeito: a batida de 30s de um dos online chega
        // depois e nada mais muda. A lista tem de sair identica.
        fun members(romeroLastSeenMillis: Long) = listOf(
            member(
                deviceId = "device-romero",
                alias = "romero",
                accountKey = "account-helio",
                accountLabel = "helio@empresa.com",
                lastSeenAtMillis = romeroLastSeenMillis
            ),
            member(
                deviceId = "device-severino",
                alias = "severino",
                accountKey = "account-severino",
                accountLabel = "severino@empresa.com",
                lastSeenAtMillis = NOW.toEpochMilliseconds() - 60_000
            )
        )

        val before = members(NOW.toEpochMilliseconds() - 60_000).toTeamPresence(NOW)
        val after = members(NOW.toEpochMilliseconds()).toTeamPresence(NOW)

        assertEquals(
            before.map { entry -> entry.deviceId },
            after.map { entry -> entry.deviceId }
        )
    }

    @Test
    fun `o turno mais recente nao reordena quem trabalha`() {
        val recent = member(
            deviceId = "device-zzz",
            alias = "zzz",
            sessions = listOf(session(lastTsMillis = NOW.toEpochMilliseconds()))
        )
        val older = member(
            deviceId = "device-aaa",
            alias = "aaa",
            sessions = listOf(
                session(lastTsMillis = NOW.toEpochMilliseconds() - ACTIVE_SESSION_WINDOW_MILLIS + 1_000)
            )
        )

        val order = listOf(recent, older).toTeamPresence(NOW).map { entry -> entry.alias }

        assertEquals(listOf("aaa", "zzz"), order)
    }
}
