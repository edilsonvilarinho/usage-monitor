package com.usagemonitor

import com.usagemonitor.data.datasource.LocalTeamSyncStateDataSource
import com.usagemonitor.domain.entity.TeamIngestPayload
import com.usagemonitor.domain.entity.TeamIntegrationSettings
import com.usagemonitor.domain.entity.TeamMemberIdentity
import com.usagemonitor.domain.usecase.PushTeamUsageUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/** Conta Anthropic que participa do time, resolvida a partir de um perfil local. */
internal data class TeamSyncTarget(
    val profileId: String,
    /** `accountUuid` da conta — é o que agrupa as máquinas no servidor. */
    val accountKey: String,
    val organizationUuid: String? = null,
    val organizationName: String? = null
)

internal data class TeamSyncReport(
    val pushedTurns: Int = 0,
    val batches: Int = 0,
    val failures: List<Throwable> = emptyList()
) {
    val hasFailure: Boolean
        get() = failures.isNotEmpty()
}

/**
 * Identidade já confirmada pelo servidor para uma conta.
 *
 * Carrega a chave junto do apelido porque as duas definem o que o servidor sabe:
 * o apelido é o que ele grava, e a chave é o que amarra a conta a um time.
 */
internal data class ConfirmedIdentity(
    val alias: String,
    val apiKey: String
)

private fun TeamIntegrationSettings.confirmedIdentity(): ConfirmedIdentity {
    return ConfirmedIdentity(alias = alias, apiKey = apiKey)
}

/**
 * O que a última passada de envio produziu, para a tela poder mostrar.
 *
 * Antes o [TeamSyncReport] era montado e descartado: uma máquina que falhava em
 * todo envio não dava nenhum sinal, e o usuário só percebia pela ausência dos
 * próprios dados na tela dos colegas — sem nada que apontasse a causa.
 */
data class TeamSyncStatus(
    val lastSuccessAt: Instant? = null,
    val lastFailureAt: Instant? = null,
    val lastFailureMessage: String? = null
) {
    /**
     * Há falha pendente, e o aviso deve aparecer.
     *
     * A pendência é apagada no sucesso em vez de deduzida da ordem dos
     * carimbos: duas passadas no mesmo milissegundo — comum quando o envio não
     * tem nada a fazer — empatariam, e o aviso ficaria preso na tela.
     */
    val isFailing: Boolean
        get() = lastFailureMessage != null
}

/**
 * Empurra os turnos indexados desta máquina para o servidor de time.
 *
 * Roda com a janela do time fechada: se o envio só acontecesse com o modal
 * aberto, o consumo de quem nunca abre a tela nunca chegaria ao time.
 *
 * Cada passada começa atualizando o índice por [ensureIndexFresh]. Sem isso a
 * latência real não era o intervalo daqui, e sim o do laço de indexação de
 * background (10min): este serviço só enxerga turno que já está no índice, então
 * uma sessão nova levava mais de dez minutos para aparecer nas outras máquinas.
 * Com a indexação na própria passada, a latência passa a ser o [intervalMillis].
 *
 * A indexação é incremental e barata — compara tamanho e data de cada transcript
 * e só lê o trecho novo. Com a janela de Sessões CLI aberta, o laço ao vivo dela
 * (5s) indexa em paralelo com esta passada: a conexão do índice é `synchronized`,
 * então as duas se serializam e a passada redundante apenas lista os diretórios.
 *
 * Cada passada também confere o apelido antes dos lotes: o servidor só grava o
 * `alias` dentro de um ingest, então sem um envio dedicado quem renomeia e para
 * de usar o Claude Code fica com o nome velho na tela do time indefinidamente.
 *
 * O trabalho pesado é o **backfill inicial**: na primeira execução o índice pode
 * ter dezenas de milhares de turnos. Por isso cada passada envia no máximo
 * [maxBatchesPerPass] lotes — o histórico entra em poucas passadas em vez de
 * numa requisição gigante, e o laço não segura a conexão do índice por muito
 * tempo de uma vez.
 */
internal class TeamSyncService(
    private val syncStateDataSource: LocalTeamSyncStateDataSource,
    private val pushTeamUsage: PushTeamUsageUseCase,
    private val settingsProvider: () -> TeamIntegrationSettings,
    private val targetsProvider: () -> List<TeamSyncTarget>,
    /**
     * Atualiza o índice antes de ler o lote. `null` desliga — a passada passa a
     * enviar só o que outro laço já indexou.
     */
    private val ensureIndexFresh: (suspend () -> Unit)? = null,
    private val hostNameProvider: () -> String? = { defaultHostName() },
    private val intervalMillis: Long = DEFAULT_INTERVAL_MILLIS,
    private val batchSize: Int = DEFAULT_BATCH_SIZE,
    private val maxBatchesPerPass: Int = DEFAULT_MAX_BATCHES_PER_PASS,
    private val clock: Clock = Clock.System,
    dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val passMutex = Mutex()
    private var loopJob: Job? = null
    private var immediateJob: Job? = null

    /**
     * Última identidade que o servidor confirmou, por conta.
     *
     * Só em memória: o custo de errar é uma requisição de ~200 bytes por conta a
     * cada início do app, que ainda serve de heartbeat para o `last_seen_at`.
     * Persistir isso ao lado de `team_sync_state` seria estado a mais para
     * economizar esse POST. O acesso é sempre de dentro do [passMutex].
     *
     * A **chave de time entra na comparação** junto do apelido. Guardar só o
     * apelido fazia trocar a chave não gerar requisição nenhuma: numa máquina sem
     * turno pendente — o caso normal de quem já sincronizou tudo — nada era
     * enviado, e o vínculo da chave nova com a conta, que só nasce num ingest,
     * nunca acontecia. O time inteiro ficava sem conseguir ler.
     */
    private val confirmedIdentityByAccount = mutableMapOf<String, ConfirmedIdentity>()

    private val _syncStatus = MutableStateFlow(TeamSyncStatus())

    /** Desfecho da última passada, para as Configurações mostrarem. */
    val syncStatus: StateFlow<TeamSyncStatus> = _syncStatus.asStateFlow()

    /** Idempotente: chamar com o laço já rodando não abre um segundo. */
    fun start() {
        if (loopJob?.isActive == true) {
            return
        }
        loopJob = scope.launch {
            while (true) {
                syncOnce()
                delay(intervalMillis)
            }
        }
    }

    fun stop() {
        loopJob?.cancel()
        loopJob = null
    }

    /**
     * Antecipa uma passada, sem reiniciar o laço periódico.
     *
     * Serve ao momento em que o usuário abre a janela do time: esperar o próximo
     * tique deixaria a tela abrir mostrando um estado até um intervalo atrasado.
     * Chamadas repetidas enquanto a anterior corre devolvem o mesmo job, senão um
     * clique repetido empilharia passadas na fila do [passMutex].
     */
    fun requestImmediateSync(): Job {
        val running = immediateJob
        if (running?.isActive == true) {
            return running
        }
        val job = scope.launch { syncOnce() }
        immediateJob = job
        return job
    }

    fun onDestroy() {
        stop()
        immediateJob = null
        scope.cancel()
    }

    /**
     * Uma passada completa sobre todas as contas participantes.
     *
     * Uma conta que falha não impede as outras: o erro é acumulado no relatório e
     * o marcador dela simplesmente não avança, então o lote sai de novo na
     * próxima passada.
     */
    suspend fun syncOnce(): TeamSyncReport {
        val settings = settingsProvider()
        if (!settings.isActive) {
            return TeamSyncReport()
        }

        return passMutex.withLock {
            var pushedTurns = 0
            var batches = 0
            val failures = mutableListOf<Throwable>()

            // Uma indexação por passada, nunca por conta: o índice é único e
            // varrer os transcritos de novo a cada alvo seria trabalho repetido.
            // Falhar aqui não cancela o envio — o que já está indexado ainda
            // precisa sair, e o erro viaja no relatório.
            val indexRefresh = ensureIndexFresh
            if (indexRefresh != null) {
                runCatching { indexRefresh() }
                    .onFailure { error -> failures += error }
            }

            for (target in targetsProvider()) {
                if (!settings.participates(target.profileId)) {
                    continue
                }

                val result = syncTarget(settings, target)
                pushedTurns += result.pushedTurns
                batches += result.batches
                failures += result.failures
            }

            val report = TeamSyncReport(
                pushedTurns = pushedTurns,
                batches = batches,
                failures = failures
            )
            publishStatus(report)
            report
        }
    }

    /**
     * Publica o desfecho da passada para a tela de Configurações.
     *
     * A primeira falha é a que fica: as seguintes costumam ser a mesma causa, e
     * trocar a mensagem a cada tique de 30s faria o aviso piscar sem informar
     * nada novo.
     */
    private fun publishStatus(report: TeamSyncReport) {
        val now = clock.now()
        val current = _syncStatus.value
        _syncStatus.value = if (report.hasFailure) {
            current.copy(
                lastFailureAt = now,
                lastFailureMessage = report.failures.first().message ?: UNKNOWN_FAILURE_MESSAGE
            )
        } else {
            // Passada limpa apaga a falha: o aviso é sobre o estado atual, e uma
            // mensagem que sobrevive ao conserto manda o usuário atrás de um
            // problema que já não existe.
            TeamSyncStatus(lastSuccessAt = now)
        }
    }

    private suspend fun syncTarget(
        settings: TeamIntegrationSettings,
        target: TeamSyncTarget
    ): TeamSyncReport {
        var pushedTurns = 0
        var batches = 0
        val failures = mutableListOf<Throwable>()

        // Identidade primeiro, e independente de haver turnos: o apelido só vai
        // ao servidor dentro de um ingest, então sem isto uma máquina que trocou
        // de apelido e parou de usar o Claude Code ficaria com o nome velho na
        // tela do time para sempre. Falhar aqui não impede o envio dos lotes.
        val identityFailure = pushIdentityIfChanged(settings, target)
        if (identityFailure != null) {
            failures += identityFailure
        }

        repeat(maxBatchesPerPass) {
            val batch = runCatching {
                syncStateDataSource.readPendingBatch(target.profileId, batchSize)
            }.getOrElse { error ->
                return TeamSyncReport(pushedTurns, batches, failures + error)
            }

            if (batch.isEmpty) {
                return TeamSyncReport(pushedTurns, batches, failures)
            }

            val payload = TeamIngestPayload(
                accountKey = target.accountKey,
                member = TeamMemberIdentity(
                    deviceId = settings.deviceId,
                    alias = settings.alias,
                    // O índice já grava o hostname de quem leu o transcript; o
                    // provider só cobre sessões antigas sem a coluna preenchida.
                    hostName = batch.sessions.firstNotNullOfOrNull { session -> session.hostName }
                        ?: hostNameProvider(),
                    organizationUuid = target.organizationUuid,
                    organizationName = target.organizationName
                ),
                sessions = batch.sessions,
                turns = batch.turns
            )

            val pushResult = pushTeamUsage(payload)
            val failure = pushResult.exceptionOrNull()
            if (failure != null) {
                // Marcador não avança: o mesmo lote volta na próxima passada.
                return TeamSyncReport(pushedTurns, batches, failures + failure)
            }

            // O lote carrega a mesma identidade e vai com a mesma chave, então
            // confirma tanto quanto o envio dedicado — e evita repeti-lo na
            // próxima passada quando o envio de identidade falhou mas este passou.
            confirmedIdentityByAccount[target.accountKey] = settings.confirmedIdentity()

            runCatching { syncStateDataSource.markPushed(batch.turns, clock.now()) }
                .onFailure { error -> return TeamSyncReport(pushedTurns, batches, failures + error) }

            pushedTurns += batch.turns.size
            batches += 1

            // Lote incompleto significa que o índice acabou: nada a fazer agora.
            if (batch.turns.size < batchSize) {
                return TeamSyncReport(pushedTurns, batches, failures)
            }
        }

        return TeamSyncReport(pushedTurns, batches, failures)
    }

    /**
     * Manda só a identidade quando o apelido **ou a chave** mudaram desde a
     * última confirmação.
     *
     * O `POST /v1/ingest` aceita `sessions` e `turns` vazios e faz o upsert do
     * membro antes de qualquer laço, então este envio é barato e não depende de
     * haver turno novo. O marcador só avança no sucesso: falhar aqui faz a
     * próxima passada tentar de novo.
     *
     * A chave entra na condição porque é ela que amarra a conta no servidor: sem
     * isso, quem trocasse de chave sem ter turno pendente não emitia requisição
     * nenhuma e a conta ficava sem dona.
     *
     * Devolve a falha, ou `null` quando não havia o que enviar ou o envio passou.
     */
    private suspend fun pushIdentityIfChanged(
        settings: TeamIntegrationSettings,
        target: TeamSyncTarget
    ): Throwable? {
        if (confirmedIdentityByAccount[target.accountKey] == settings.confirmedIdentity()) {
            return null
        }

        val payload = TeamIngestPayload(
            accountKey = target.accountKey,
            member = TeamMemberIdentity(
                deviceId = settings.deviceId,
                alias = settings.alias,
                // Sem lote não há sessão de onde ler o hostname do índice.
                hostName = hostNameProvider(),
                organizationUuid = target.organizationUuid,
                organizationName = target.organizationName
            ),
            sessions = emptyList(),
            turns = emptyList()
        )

        val failure = pushTeamUsage(payload, force = true).exceptionOrNull()
        if (failure == null) {
            confirmedIdentityByAccount[target.accountKey] = settings.confirmedIdentity()
        }
        return failure
    }

    companion object {
        private const val UNKNOWN_FAILURE_MESSAGE = "falha desconhecida no envio ao time"

        /** Latência com que uma máquina aparece para as outras. */
        const val DEFAULT_INTERVAL_MILLIS = 30_000L

        /** Tem de caber no `TEAM_MAX_TURNS_PER_REQUEST` do servidor (5000). */
        const val DEFAULT_BATCH_SIZE = 2_000

        /** Teto de lotes por passada, para o backfill não monopolizar o índice. */
        const val DEFAULT_MAX_BATCHES_PER_PASS = 5

        fun defaultHostName(): String? {
            val fromEnvironment = System.getenv("COMPUTERNAME")?.takeIf { it.isNotBlank() }
                ?: System.getenv("HOSTNAME")?.takeIf { it.isNotBlank() }
            if (fromEnvironment != null) {
                return fromEnvironment
            }
            return runCatching { java.net.InetAddress.getLocalHost().hostName }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
        }
    }
}
