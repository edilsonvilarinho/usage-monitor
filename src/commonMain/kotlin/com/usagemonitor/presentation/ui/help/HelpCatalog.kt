package com.usagemonitor.presentation.ui.help

import com.usagemonitor.domain.entity.AppLanguage

/**
 * Tópicos da janela de ajuda.
 *
 * Um por bloco de funcionalidade, e não um por controle: a lista existe para
 * responder "o que este app faz e como ligo isso", e vinte e cinco entradas
 * granulares respondem à pergunta errada — vira índice de Configurações, que já
 * tem navegação própria.
 */
enum class HelpTopic {
    DASHBOARD,
    HISTORY,
    CLI_SESSIONS,
    BREAKDOWN,
    BUDGET,
    ALERTS,
    EXPORT,
    TEAM,
    PRESENCE,
    WINDOW_MODES,
    APPEARANCE,
    UPDATES
}

/**
 * O que a ajuda mostra de um tópico.
 *
 * [steps] é o "como ativar" e é o motivo de esta tela existir: descrição sem
 * caminho de ativação descreve algo que o leitor não consegue alcançar. Os
 * passos citam o rótulo **real** do controle — trocar o rótulo na tela sem
 * trocar aqui manda o usuário procurar um botão que não existe.
 *
 * [mediaId] nomeia o recurso `help/<mediaId>.gif`. Não é anulável: todo tópico
 * tem demo, e quem trata ausência é o tocador, que degrada para texto.
 */
data class HelpEntry(
    val title: String,
    val summary: String,
    val description: String,
    val steps: List<String>,
    val mediaId: String
)

/**
 * Catálogo bilíngue das funcionalidades.
 *
 * Mesmo desenho de `CliSessionsGlossary`: enum de tópicos, [readingOrder] e uma
 * função que devolve a entrada no idioma. Aqui não são rótulos — são definições
 * e caminhos de ativação, e o `when` exaustivo do Kotlin só pega a entrada
 * faltando, nunca a entrada vazia ou não traduzida; por isso os testes.
 *
 * O texto sai do README, que é a documentação do produto, e os passos saem do
 * código: nome real da aba de Configurações, rótulo real do interruptor, atalho
 * real do `onKeyEvent`.
 */
object HelpCatalog {

    /**
     * Ordem de leitura: do que se vê ao abrir o app para o que se liga depois.
     *
     * Dashboard primeiro porque é a tela que já está na frente de quem procura
     * ajuda; os modos de janela e a aparência no fim porque são preferências, e
     * quem chega aqui pela primeira vez procura o que o app mede.
     */
    val readingOrder: List<HelpTopic> = listOf(
        HelpTopic.DASHBOARD,
        HelpTopic.HISTORY,
        HelpTopic.CLI_SESSIONS,
        HelpTopic.BREAKDOWN,
        HelpTopic.BUDGET,
        HelpTopic.ALERTS,
        HelpTopic.EXPORT,
        HelpTopic.TEAM,
        HelpTopic.PRESENCE,
        HelpTopic.WINDOW_MODES,
        HelpTopic.APPEARANCE,
        HelpTopic.UPDATES
    )

    fun entry(topic: HelpTopic, language: AppLanguage): HelpEntry {
        return if (language == AppLanguage.PT) portugueseHelpEntry(topic) else englishHelpEntry(topic)
    }

    /** Recurso da demo, sem depender do idioma: as demos são geradas só em PT. */
    fun mediaId(topic: HelpTopic): String = portugueseHelpEntry(topic).mediaId
}
