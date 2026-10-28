package com.usagemonitor

import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.ui.localized

/*
 * O texto do menu e do tooltip da bandeja, fora de `AppTrayHost.kt`. Aquele arquivo
 * é cola de janela (o `Tray` do AWT) e sai do Kover por nome (onda 6.5 do plano
 * `cobertura-95-execucao.md`); o texto que o usuário lê mora aqui e segue testado.
 */

/** Os itens do menu da bandeja; "Rever guia de introdução" vem da Ajuda (`helpReplayGuideLabel`). */
internal object TrayMenuLabels {
    fun open(language: AppLanguage): String = localized(language, "Abrir", "Open")

    fun refreshNow(language: AppLanguage): String = localized(language, "Atualizar agora", "Refresh now")

    fun settings(language: AppLanguage): String = localized(language, "Configurações", "Settings")

    fun help(language: AppLanguage): String = localized(language, "Ajuda", "Help")

    fun quit(language: AppLanguage): String = localized(language, "Sair", "Quit")
}

/**
 * Nome no começo do tooltip da bandeja. Com `--user-data-dir` (#419) leva a pasta:
 * duas instâncias abertas dariam dois ícones idênticos. Fica no começo porque o
 * corte de 127 caracteres do tooltip come o fim.
 */
internal fun trayAppName(isolatedDataRootName: String?): String =
    if (isolatedDataRootName == null) "Usage Monitor" else "Usage Monitor ($isolatedDataRootName)"
