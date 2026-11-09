package com.usagemonitor.domain.entity

/**
 * Configuração da integração com o servidor de time.
 *
 * O time é definido pela **conta Anthropic**, não por um identificador digitado:
 * o agrupamento usa o `accountUuid` que o app já lê de `~/.claude.json`. O que o
 * usuário informa aqui é só como ele quer ser identificado ([alias]) e onde fica
 * o servidor da empresa.
 *
 * [participatingProfileIds] existe porque a mesma máquina costuma estar logada em
 * várias contas ao mesmo tempo, e nem toda conta é da empresa: só os perfis
 * marcados aqui empurram dados e exibem o botão de time no card.
 */
data class TeamIntegrationSettings(
    val enabled: Boolean = false,
    val serverUrl: String = "",
    val apiKey: String = "",
    val alias: String = "",
    /** UUID gerado uma vez por instalação; separa duas máquinas do mesmo alias. */
    val deviceId: String = "",
    val participatingProfileIds: Set<String> = emptySet(),
    /**
     * Token de administração do servidor, opcional.
     *
     * Quem administra não é necessariamente integrante de nenhuma conta: o token
     * dá acesso de leitura a todas elas e à emissão de chaves, e por isso vive
     * fora de [isConfigured] — exigir apelido e chave de time para administrar
     * obrigaria o admin a se inscrever num time do qual ele não participa.
     */
    val adminToken: String = "",
    /** Identificador estável do time, gerado uma vez; sobrevive a renomear. */
    val id: String = "",
    /** Nome do time exibido ao usuário. */
    val name: String = "",
    /** Chave própria de cada conta participante (profileId → chave); conta sem entrada usa [apiKey]. */
    val accountApiKeys: Map<String, String> = emptyMap()
) {

    /** Segredo nunca no `toString` (log, relatório de bug, mensagem de exceção). */
    override fun toString(): String =
        "TeamIntegrationSettings(enabled=$enabled, serverUrl=$serverUrl, apiKey=${redactedSecret(apiKey)}, alias=$alias, " +
            "deviceId=$deviceId, participatingProfileIds=$participatingProfileIds, adminToken=${redactedSecret(adminToken)}, " +
            "id=$id, name=$name, accountApiKeys=${redactedAccountKeys()})"

    /** Sem a barra final, para concatenar o caminho sem gerar `//`. */
    val normalizedServerUrl: String
        get() = serverUrl.trim().trimEnd('/')

    /**
     * Configurada para a chamada de rede: tem a chave do time, ou toda conta participante tem chave própria.
     *
     * Sem chave de time, o time só funciona se nenhuma conta depender dele: cada participante precisa da sua.
     */
    val isConfigured: Boolean
        get() = normalizedServerUrl.isNotEmpty() &&
            alias.isNotBlank() &&
            deviceId.isNotBlank() &&
            (apiKey.isNotBlank() || allParticipantsHaveOwnKey())

    /** Chave que a conta usa no servidor: a própria, aparada; sem chave própria, a do time. `null` usa a do time. */
    fun apiKeyFor(profileId: String?): String {
        if (profileId == null) {
            return apiKey
        }
        val own = ownKeyOf(profileId)
        return if (own.isNotEmpty()) own else apiKey
    }

    /** Chave própria da conta já aparada; vazio quando não há. */
    private fun ownKeyOf(profileId: String): String = accountApiKeys[profileId]?.trim().orEmpty()

    /** `true` só com ao menos uma participante e todas com chave própria não vazia. */
    private fun allParticipantsHaveOwnKey(): Boolean =
        participatingProfileIds.isNotEmpty() && participatingProfileIds.all { ownKeyOf(it).isNotEmpty() }

    /** Mapa com cada chave mascarada; o `profileId` (chave do mapa) continua visível, o valor não. */
    private fun redactedAccountKeys(): Map<String, String> = accountApiKeys.mapValues { redactedSecret(it.value) }

    /** Ligada e com tudo o que a chamada de rede precisa. */
    val isActive: Boolean
        get() = enabled && isConfigured

    /**
     * Ligada e com token de administração informado.
     *
     * Deliberadamente independente de [isActive]: administrar e sincronizar são
     * caminhos separados, e a mesma instalação pode fazer só um dos dois.
     */
    val isAdminMode: Boolean
        get() = enabled && normalizedServerUrl.isNotEmpty() && adminToken.isNotBlank()

    /** `true` quando o perfil participa do time e a integração está utilizável. */
    fun participates(profileId: String?): Boolean {
        if (!isActive || profileId == null) {
            return false
        }
        return profileId in participatingProfileIds
    }
}
