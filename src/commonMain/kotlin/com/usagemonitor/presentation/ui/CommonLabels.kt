package com.usagemonitor.presentation.ui

import com.usagemonitor.domain.entity.AppLanguage

/**
 * O texto do idioma pedido. Dono único da escolha: os objetos de rótulos tinham
 * cada um o seu `pick` privado, cópias da mesma linha.
 */
internal fun localized(language: AppLanguage, pt: String, en: String): String {
    return if (language == AppLanguage.PT) pt else en
}

/**
 * Rótulos com o **mesmo sentido** em mais de uma tela. Estavam repetidos em até
 * quatro arquivos, e cópia de texto diverge na primeira revisão de tradução.
 *
 * Só entra aqui o que significa a mesma coisa em todo lugar: "Janela" (do app ×
 * de tempo) ou "Conta" (de uso × coluna do time) têm sentidos diferentes e ficam
 * no objeto de rótulos da própria tela. Vocabulário das sessões CLI mora em
 * `CliSessionsLabels`; do recorte, em `BreakdownLabels`.
 */
internal object CommonLabels {
    fun cancel(language: AppLanguage): String = localized(language, "Cancelar", "Cancel")

    fun close(language: AppLanguage): String = localized(language, "Fechar", "Close")

    fun back(language: AppLanguage): String = localized(language, "Voltar", "Back")

    fun copy(language: AppLanguage): String = localized(language, "Copiar", "Copy")

    fun reportBug(language: AppLanguage): String = localized(language, "Reportar um bug", "Report a bug")

    fun exportDone(language: AppLanguage): String = localized(language, "Exportação concluída", "Export complete")

    fun exportFailed(language: AppLanguage): String = localized(language, "Falha na exportação", "Export failed")

    /** Cota que a fonte informa só como percentual corrente, sem janela (card, tooltip e histórico). */
    fun currentUsage(language: AppLanguage): String = localized(language, "Uso atual", "Current usage")

    /** Saldo pré-pago (card, tooltip, histórico e PDF). */
    fun currentBalance(language: AppLanguage): String = localized(language, "Saldo atual", "Current balance")

    /** Estado neutro das fontes de atividade observada (HUD e card do histórico). */
    fun localActivity(language: AppLanguage): String = localized(language, "Atividade local", "Local activity")
}
