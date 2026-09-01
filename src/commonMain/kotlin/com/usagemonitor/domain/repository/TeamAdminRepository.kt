package com.usagemonitor.domain.repository

import com.usagemonitor.domain.entity.CliSessionDetail
import com.usagemonitor.domain.entity.TeamAccountDeletion
import com.usagemonitor.domain.entity.TeamAccountUsage
import com.usagemonitor.domain.entity.TeamBlockedAccount
import com.usagemonitor.domain.entity.TeamKeyEntry
import com.usagemonitor.domain.entity.TeamKeyVerification

/**
 * Administração do servidor de time.
 *
 * Separado de [TeamUsageRepository] porque as duas credenciais e os dois papéis
 * são diferentes: lá a chave de time de **uma** conta, aqui o token de
 * administração, que não pertence a conta nenhuma e vale para todas.
 */
interface TeamAdminRepository {

    /** Confere o token contra o servidor, sem efeito colateral. */
    suspend fun validateToken(): Result<Unit>

    suspend fun listKeys(): Result<List<TeamKeyEntry>>

    suspend fun createKey(label: String, maxAccounts: Int): Result<TeamKeyEntry>

    suspend fun updateKey(id: String, label: String?, maxAccounts: Int?): Result<TeamKeyEntry>

    /** Troca a chave crua mantendo os vínculos. A antiga para de valer na hora. */
    suspend fun regenerateKey(id: String): Result<TeamKeyEntry>

    /** Revoga o acesso. **Não** apaga o histórico já enviado. */
    suspend fun revokeKey(id: String): Result<TeamKeyEntry>

    /** Desfaz um vínculo criado no envio errado, liberando a conta. */
    suspend fun unclaimAccount(id: String, accountKey: String): Result<TeamKeyEntry>

    /**
     * Apaga um integrante e os dados enviados por ele usando a autoridade de
     * administração, independentemente da chave de time dona da conta.
     */
    suspend fun removeMember(accountKey: String, deviceId: String): Result<Unit>

    /**
     * Apaga uma sessão e os turnos dela usando exclusivamente a autoridade de
     * administração. O integrante e as demais sessões permanecem.
     */
    suspend fun removeSession(
        accountKey: String,
        deviceId: String,
        sessionId: String
    ): Result<Unit>

    /**
     * Apaga uma conta inteira do servidor: integrantes, sessões, turnos e o
     * vínculo com a chave. **Irreversível.**
     *
     * Diferente de [unclaimAccount], que só solta o vínculo: a conta desvinculada
     * continua na visão global, agora sem rótulo, porque a agregação parte dos
     * dados de uso e não da tabela de vínculos.
     *
     * A partir do servidor 0.11.0 a remoção também **declara a conta fora do
     * time**: sem isso a máquina que ainda participa dela a recriava na batida
     * seguinte, e apagar era gesto sem efeito. Para devolvê-la existe
     * [unblockAccount].
     */
    suspend fun deleteAccount(accountKey: String): Result<TeamAccountDeletion>

    /**
     * Contas que o administrador declarou fora do time.
     *
     * Lista vazia contra servidor anterior à 0.11.0 — sem a tabela não há conta
     * bloqueada naquele deploy, e um erro mandaria o admin atrás de um problema
     * que não existe.
     */
    suspend fun fetchBlockedAccounts(): Result<List<TeamBlockedAccount>>

    /**
     * Devolve a conta ao time e responde com a lista restante.
     *
     * **Não restaura dado nenhum**: o histórico foi apagado junto do bloqueio e a
     * máquina daquela conta já marcou os turnos como enviados. O que volta é a
     * possibilidade de a conta se vincular de novo — e daí em diante ela passa
     * pelo rótulo da chave como qualquer outra.
     */
    suspend fun unblockAccount(accountKey: String): Result<List<TeamBlockedAccount>>

    /**
     * Consumo de todas as contas do servidor.
     *
     * [cutoffMillis] recorta os turnos, como em [TeamUsageRepository.fetch].
     */
    suspend fun fetchOverview(cutoffMillis: Long? = null): Result<List<TeamAccountUsage>>

    /**
     * Lê o detalhe de uma sessão com o token de administração.
     *
     * É um contrato separado de [TeamUsageRepository.fetchSessionDetail]: a
     * visão global precisa atravessar contas pertencentes a outras chaves de
     * time, mesmo quando esta instalação também participa de um time.
     */
    suspend fun fetchSessionDetail(
        accountKey: String,
        deviceId: String,
        sessionId: String
    ): Result<CliSessionDetail?>

    /**
     * Pergunta se a chave de time configurada serve para [accountKey].
     *
     * Fica aqui, e não em [TeamUsageRepository], por simetria com o resto da
     * verificação de credencial — mas usa a chave de time, não o token de admin.
     *
     * [accountEmail] é o e-mail que a conta reporta. O servidor confere a conta
     * contra o rótulo da chave (0.11.0+), e sem ele responderia pelo que aquela
     * conta já gravou — que numa máquina que nunca enviou nada é nada, e a
     * resposta aprovaria uma conta que o envio seguinte recusa.
     */
    suspend fun verifyKeyForAccount(
        accountKey: String,
        accountEmail: String? = null
    ): Result<TeamKeyVerification>

    /**
     * Amarra a conta à chave de time configurada. Idempotente.
     *
     * Diferente de [verifyKeyForAccount], que só informa: esta é a ação que
     * resolve. O vínculo antes só nascia dentro de um envio de turnos, e uma
     * máquina sem turno pendente nunca o criava.
     */
    suspend fun claimKeyForAccount(
        accountKey: String,
        accountEmail: String? = null
    ): Result<TeamKeyVerification>
}
