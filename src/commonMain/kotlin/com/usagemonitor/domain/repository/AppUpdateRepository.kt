package com.usagemonitor.domain.repository

import com.usagemonitor.domain.entity.AppUpdateInfo
import com.usagemonitor.domain.entity.ReleaseNotes

interface AppUpdateRepository {
    suspend fun getLatestAvailableUpdate(currentVersion: String): Result<AppUpdateInfo?>

    /**
     * Notas da release de [version], já filtradas para o que o usuário percebe.
     *
     * Sucesso com `null` é **release sem nada a mostrar** e sucesso com lista
     * vazia não existe: os dois casos colapsam em `null`, e quem chama trata um
     * caso só. Falha é falha de rede, e ela importa — quem falha tenta de novo
     * na abertura seguinte, em vez de marcar a versão como já vista.
     */
    suspend fun getReleaseNotes(version: String, previousVersion: String?): Result<ReleaseNotes?>
}
