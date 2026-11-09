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
     * Configurada para a chamada de rede: servidor, apelido e deviceId, mais uma chave utilizável.
     *
     * Modo legado (sem mapa de chaves próprias): a chave do time basta. Com mapa, a chave do time não é
     * herdada: basta ao menos uma participante com chave própria; as sem chave ficam de fora do envio
     * (ver [missingKeyProfileIds]).
     */
    val isConfigured: Boolean
        get() = normalizedServerUrl.isNotEmpty() &&
            alias.isNotBlank() &&
            deviceId.isNotBlank() &&
            hasUsableKey()

    /**
     * Chave que a conta usa no servidor. `null` usa a do time.
     *
     * Modo legado (mapa vazio): a do time. Com mapa: a própria, aparada, sem herdar a do time;
     * conta sem entrada no mapa fica com vazio.
     */
    fun apiKeyFor(profileId: String?): String {
        if (profileId == null || accountApiKeys.isEmpty()) {
            return apiKey
        }
        return ownKeyOf(profileId)
    }

    /** Participantes sem chave utilizável: `apiKeyFor` vazio. Não entram em presença, identidade nem envio. */
    fun missingKeyProfileIds(): Set<String> = participatingProfileIds.filterTo(mutableSetOf()) { apiKeyFor(it).isBlank() }

    /**
     * Copia a chave do time para cada participante sem chave própria, para sair do modo legado sem
     * perder a chave de ninguém. Não altera [apiKey]; sem chave do time, ou sem nada a copiar, devolve esta instância.
     */
    fun withOwnKeysMaterialized(): TeamIntegrationSettings {
        if (apiKey.isBlank()) {
            return this
        }
        val missing = participatingProfileIds.filter { ownKeyOf(it).isEmpty() }
        if (missing.isEmpty()) {
            return this
        }
        return copy(accountApiKeys = accountApiKeys + missing.associateWith { apiKey })
    }

    /** Chave própria da conta já aparada; vazio quando não há. */
    private fun ownKeyOf(profileId: String): String = accountApiKeys[profileId]?.trim().orEmpty()

    /** Modo legado: a chave do time. Com mapa: ao menos uma participante com chave própria. */
    private fun hasUsableKey(): Boolean =
        if (accountApiKeys.isEmpty()) {
            apiKey.isNotBlank()
        } else {
            participatingProfileIds.any { ownKeyOf(it).isNotEmpty() }
        }

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
