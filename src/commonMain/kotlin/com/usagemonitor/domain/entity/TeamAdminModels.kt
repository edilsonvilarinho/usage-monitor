package com.usagemonitor.domain.entity

import kotlinx.datetime.Instant

/**
 * Chave de time como o painel de administração a mostra.
 *
 * [label] é texto livre de quem administra — normalmente o e-mail da pessoa. O
 * servidor **não** o verifica: quem prova a quem a chave pertence é [accounts],
 * preenchida no primeiro envio que a chave faz. Um rótulo errado produz uma
 * lista bonita e mentirosa, e é por isso que a tela mostra os dois lado a lado.
 *
 * [key] vem crua na resposta de propósito: o painel é a lista de "quem tem qual
 * chave", e mostrá-la só na criação obrigaria o admin a guardá-la fora do
 * sistema ou a regerar a cada consulta.
 */
data class TeamKeyEntry(
    val id: String,
    val label: String,
    val key: String,
    val keyPrefix: String,
    val maxAccounts: Int,
    /** `accountUuid` já vinculados. Vazia enquanto a chave nunca foi usada. */
    val accounts: List<String> = emptyList(),
    /**
     * As mesmas contas de [accounts], com e-mail e veredito do rótulo.
     *
     * Vazia contra servidor anterior à 0.11.0, e por isso a tela lê por
     * [accountEntries], que cai em [accounts] quando ela não vem — mostrar o
     * UUID cru é o que a tela sempre fez, e vale mais que uma lista em branco.
     */
    val accountDetails: List<TeamKeyAccount> = emptyList(),
    val createdAt: Instant? = null,
    val revokedAt: Instant? = null,
    val lastUsedAt: Instant? = null
) {
    /** Um item por conta vinculada, com ou sem os detalhes que o servidor manda. */
    val accountEntries: List<TeamKeyAccount>
        get() = accountDetails.ifEmpty {
            accounts.map { accountKey -> TeamKeyAccount(accountKey = accountKey) }
        }

    /** Alguma conta vinculada está fora da relação declarada no rótulo. */
    val hasUnauthorizedAccount: Boolean
        get() = accountDetails.any { account -> !account.authorized }

    val isRevoked: Boolean
        get() = revokedAt != null

    /** Ainda não foi usada por ninguém — o vínculo nasce no primeiro envio. */
    val isUnclaimed: Boolean
        get() = accounts.isEmpty()

    val hasRoomForAnotherAccount: Boolean
        get() = accounts.size < maxAccounts
}

/**
 * Conta vinculada a uma chave.
 *
 * [accountEmail] é o e-mail que a máquina reportou — o servidor nunca o inventa.
 * Sem ele a tela mostrava só o `accountUuid`, e a conta pessoal que entrou no
 * time era indistinguível das legítimas.
 */
data class TeamKeyAccount(
    val accountKey: String,
    val accountEmail: String? = null,
    /** `false` quando o e-mail não está na relação declarada no rótulo da chave. */
    val authorized: Boolean = true
)

/**
 * Conta que o administrador declarou fora do time.
 *
 * Os dados dela foram apagados junto, então [accountEmail] é o retrato do
 * momento do bloqueio: é ele que identifica a linha para quem vai decidir se
 * devolve a conta ao time.
 */
data class TeamBlockedAccount(
    val accountKey: String,
    val accountEmail: String? = null,
    val reason: String? = null,
    val blockedAt: Instant? = null
)

/**
 * Resposta de `GET /v1/verify`: esta chave serve para esta conta?
 *
 * [claimed] separa "já é sua" de "ainda pode ser sua". A segunda é o estado
 * normal de quem acabou de colar a chave e ainda não sincronizou nada.
 */
data class TeamKeyVerification(
    val authorized: Boolean,
    val claimed: Boolean,
    val label: String? = null,
    val maxAccounts: Int = 0,
    val claimedAccounts: Int = 0
)

/**
 * O que a remoção de uma conta apagou.
 *
 * [unlinkedKeys] é 0 quando a conta não pertencia a chave nenhuma — o caso da
 * conta que entrou pela chave legada. Não é erro: ela existia nos dados e era
 * exatamente isso que a tornava visível na visão global.
 */
data class TeamAccountDeletion(
    val deletedTurns: Int,
    val deletedSessions: Int,
    val deletedMembers: Int,
    val unlinkedKeys: Int
)

/** Uma conta dentro da visão global do administrador. */
data class TeamAccountUsage(
    val accountKey: String,
    /** Rótulo da chave dona; `null` para conta sem chave emitida. */
    val label: String?,
    val accountEmail: String? = null,
    val emailSource: TeamAccountEmailSource? = null,
    val snapshot: TeamUsageSnapshot
)
