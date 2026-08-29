package com.usagemonitor.domain.entity

import kotlinx.datetime.Instant

/**
 * Natureza de um passo da trilha.
 *
 * Cinco categorias e não um nível de log: a trilha responde "o que o usuário
 * estava fazendo", não "quão grave isto é". Severidade já está em [ERROR] e
 * [CRASH], que são os dois únicos valores que descrevem falha.
 *
 * O [wireValue] existe pelo mesmo motivo do de `StartupOutcome`: o arquivo
 * `breadcrumbs.jsonl` sobrevive a upgrades do app, e renomear uma constante do
 * enum não pode mudar o que já está gravado no disco.
 */
enum class BreadcrumbCategory {
    /** Abertura de uma tela ou de um modal. */
    NAVIGATION,

    /** Início e resultado de uma chamada de caso de uso. */
    USE_CASE,

    /** Ida a uma API externa. Só a rota e o desfecho — nunca corpo de resposta. */
    API_CALL,

    /** Falha capturada e tratada. */
    ERROR,

    /** Exceção não tratada que derrubou uma thread. */
    CRASH;

    val wireValue: String
        get() = when (this) {
            NAVIGATION -> "navigation"
            USE_CASE -> "use-case"
            API_CALL -> "api-call"
            ERROR -> "error"
            CRASH -> "crash"
        }

    companion object {
        /** Volta ao enum a partir do arquivo; valor irreconhecível devolve nulo. */
        fun fromWireValue(value: String): BreadcrumbCategory? {
            return entries.firstOrNull { category -> category.wireValue == value }
        }
    }
}

/**
 * Um passo da trilha de eventos.
 *
 * A [message] é curta de propósito e passa por [normalizeBreadcrumbMessage]: o
 * pacote vira o corpo de uma issue **pública** no GitHub, e um texto livre longo
 * é justamente onde caberia um caminho de arquivo com o nome da pessoa, um
 * fragmento de prompt ou o eco de uma credencial. O corte aqui é a última linha
 * de defesa; a primeira é o que cada ponto de chamada escolhe escrever.
 */
data class Breadcrumb(
    val at: Instant,
    val category: BreadcrumbCategory,
    val message: String
) {
    companion object {
        /**
         * Teto do texto de um passo.
         *
         * Duzentos caracteres cobrem "abriu a janela de sessões do time" e
         * "Anthropic: falhou com 401" com folga, e não cobrem um stack trace nem
         * um caminho absoluto inteiro.
         */
        const val MAX_MESSAGE_LENGTH = 200

        /**
         * Cria um passo com a mensagem já normalizada.
         *
         * Fábrica e não `init`: o construtor da `data class` continua sendo o
         * caminho de quem **lê** o arquivo de volta, e reescrever ali o que já
         * está gravado esconderia uma linha adulterada em vez de mostrá-la.
         */
        fun of(at: Instant, category: BreadcrumbCategory, message: String): Breadcrumb {
            return Breadcrumb(
                at = at,
                category = category,
                message = normalizeBreadcrumbMessage(message)
            )
        }
    }
}

/**
 * Colapsa espaço em branco e corta no teto.
 *
 * A quebra de linha some antes de qualquer serialização: o arquivo é JSONL, uma
 * linha por passo, e depender do escape de `\n` para manter esse contrato deixa
 * o formato à mercê de quem escrever o próximo serializador.
 */
fun normalizeBreadcrumbMessage(message: String): String {
    val collapsed = message.replace(WHITESPACE_RUN, " ").trim()
    if (collapsed.length <= Breadcrumb.MAX_MESSAGE_LENGTH) {
        return collapsed
    }
    // O reticências entra DENTRO do teto: o resultado tem no máximo
    // MAX_MESSAGE_LENGTH caracteres, senão o corte não seria um teto.
    return collapsed.take(Breadcrumb.MAX_MESSAGE_LENGTH - TRUNCATION_MARKER.length) + TRUNCATION_MARKER
}

/** ASCII puro: o texto atravessa uma URL e um arquivo, e não vale um caractere a explicar. */
private const val TRUNCATION_MARKER = "..."

private val WHITESPACE_RUN = Regex("\\s+")
