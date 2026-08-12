package com.usagemonitor.domain.repository

import com.usagemonitor.domain.entity.TeamAccountUsage
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
     * Consumo de todas as contas do servidor.
     *
     * [cutoffMillis] recorta os turnos, como em [TeamUsageRepository.fetch].
     */
    suspend fun fetchOverview(cutoffMillis: Long? = null): Result<List<TeamAccountUsage>>

    /**
     * Pergunta se a chave de time configurada serve para [accountKey].
     *
     * Fica aqui, e não em [TeamUsageRepository], por simetria com o resto da
     * verificação de credencial — mas usa a chave de time, não o token de admin.
     */
    suspend fun verifyKeyForAccount(accountKey: String): Result<TeamKeyVerification>
}
