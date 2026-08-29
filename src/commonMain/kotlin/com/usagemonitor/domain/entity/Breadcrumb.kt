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
 * A [message] passa por [sanitizeBreadcrumbMessage] — redação de identidade
 * primeiro, corte depois. O pacote vira o corpo de uma issue **pública** no
 * GitHub, e texto livre é justamente onde cabe um caminho de arquivo com o nome
 * da pessoa, um e-mail de conta, um fragmento de prompt ou o eco de uma
 * credencial.
 *
 * **A fábrica é a única defesa, não a última.** A primeira versão disto confiava
 * no que cada ponto de chamada escolhia escrever, e foi exatamente assim que
 * `missingCredentialsMessage` — a falha mais rotineira do app — passou a levar
 * `C:\Users\<nome>\.claude\.credentials.json` e o e-mail do perfil para dentro da
 * trilha. Ponto de chamada esquecido é como o defeito nasce; por isso a redação
 * mora aqui, e não lá.
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
         * Cria um passo com a mensagem já redigida e normalizada.
         *
         * **Todo caminho de escrita passa por aqui**, inclusive o do recorder de
         * disco: com dois caminhos, a redação deixaria de ser uma defesa e
         * passaria a ser duas, que é uma a mais para alguém esquecer.
         *
         * Fábrica e não `init`: o construtor da `data class` continua sendo o
         * caminho de quem **lê** o arquivo de volta, e reescrever ali o que já
         * está gravado esconderia uma linha adulterada em vez de mostrá-la.
         */
        fun of(at: Instant, category: BreadcrumbCategory, message: String): Breadcrumb {
            return Breadcrumb(
                at = at,
                category = category,
                message = sanitizeBreadcrumbMessage(message)
            )
        }
    }
}

/**
 * Redige a identidade e depois normaliza. **Esta é a ordem, e ela importa.**
 *
 * Cortar antes de redigir não protegeria nada: `C:\Users\<nome>` são os primeiros
 * caracteres de um caminho absoluto, e a mensagem inteira cabe folgada nos 200 —
 * o teto nunca chegaria perto da parte que identifica a pessoa.
 */
fun sanitizeBreadcrumbMessage(message: String): String {
    return normalizeBreadcrumbMessage(redactBreadcrumbIdentity(message))
}

/**
 * Tira da mensagem o que identifica **quem** rodou o app.
 *
 * **A trilha tem redação própria e não herda `sanitizeUiErrorMessage`**, porque os
 * dois têm modelos de ameaça diferentes. Aquele protege a **tela** do usuário, que
 * é privada à máquina dele, e por isso só redige segredo — `Bearer`, `cap_sid`,
 * `access_token`, `refresh_token`. Este protege o **corpo de uma issue pública**,
 * onde o critério é o mesmo de [BugReportMachineInfo]: isto explica um defeito ou
 * identifica quem o reportou? Um dado pode ser aceitável na tela e inaceitável no
 * GitHub — foi por isso que hostname e usuário do sistema já ficaram de fora.
 *
 * Dois padrões, os dois medidos num caso real e não hipotético.
 * `AnthropicCredentialStore.missingCredentialsMessage` monta
 * `"Credenciais não encontradas para o perfil '<e-mail>': C:\Users\<nome>\.claude\.credentials.json."`
 * — ela é a falha mais rotineira do app, é justamente a que faz alguém abrir um
 * relatório, e sozinha carrega os dois.
 *
 * 1. **Caminho absoluto** (`C:\…`, `\\servidor\…`, `/home/…`, `/Users/…`,
 *    `/root/…`) vira `<caminho>/<nome do arquivo>`: o nome do arquivo é o que
 *    explica o defeito, o diretório é o que carrega a pessoa. As raízes POSIX são
 *    **enumeradas** de propósito — redigir toda barra apagaria `/api/oauth/usage`,
 *    que é rota e não identidade.
 * 2. **E-mail** vira `<e-mail>`. O apelido do perfil é texto digitado pelo usuário
 *    e na prática é o e-mail da conta; isso já estava escrito quando os passos de
 *    navegação foram feitos sem ele, e a mensagem de credencial o trazia de volta
 *    pela porta dos fundos.
 */
fun redactBreadcrumbIdentity(message: String): String {
    val withoutPaths = ABSOLUTE_PATH.replace(message) { match ->
        // A pontuação final da frase não faz parte do caminho: sem separá-la, o
        // ponto que fecha a mensagem sumiria junto com o diretório.
        val trailing = match.value.takeLastWhile { character -> character in SENTENCE_PUNCTUATION }
        val path = match.value.dropLast(trailing.length)
        val fileName = path.substringAfterLast('\\').substringAfterLast('/')
        val replacement = if (fileName.isEmpty()) PATH_MARKER else "$PATH_MARKER/$fileName"
        replacement + trailing
    }
    return EMAIL.replace(withoutPaths, EMAIL_MARKER)
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

/**
 * Motivo curto de uma falha interna, para entrar num passo da trilha.
 *
 * **Só o nome da classe da exceção, nunca a `message`.** A mensagem de uma falha
 * de I/O ou de SQLite carrega o caminho absoluto do arquivo, e caminho absoluto
 * no Windows começa com `C:\Users\<nome da pessoa>`. A classe responde "que tipo
 * de falha foi" sem responder "de quem é a máquina".
 *
 * As falhas de **coleta** não passam por aqui porque a mensagem delas é o próprio
 * diagnóstico — "401", "chave não configurada", "sessão inválida" — e trocá-la
 * pela classe da exceção esvaziaria o passo. Elas dependem de
 * [redactBreadcrumbIdentity], que é quem tira o caminho e o e-mail.
 *
 * **Não confunda com `sanitizeUiErrorMessage`.** Aquele protege a tela, que é
 * privada; ele redige segredo e **não toca em caminho de arquivo** — o que se
 * verifica lendo as quatro expressões dele. Confiar naquele filtro aqui foi
 * exatamente o defeito que deixou `C:\Users\<nome>\.claude\.credentials.json`
 * entrar na trilha.
 */
fun breadcrumbReasonOf(error: Throwable): String {
    return error::class.simpleName ?: "falha desconhecida"
}

/** ASCII puro: o texto atravessa uma URL e um arquivo, e não vale um caractere a explicar. */
private const val TRUNCATION_MARKER = "..."

private const val PATH_MARKER = "<caminho>"
private const val EMAIL_MARKER = "<e-mail>"

private val WHITESPACE_RUN = Regex("\\s+")

/**
 * Caminho absoluto: unidade do Windows, UNC, ou uma das raízes POSIX que contêm
 * diretório de pessoa.
 *
 * A classe final aceita tudo que não é espaço nem delimitador de citação, porque
 * caminho de verdade tem espaço, acento, ponto e til. O que ela **não** aceita é
 * aspas e `<`/`>`, senão o casamento atravessaria a mensagem inteira quando o
 * caminho vier entre aspas.
 */
private val ABSOLUTE_PATH = Regex(
    """(?:[A-Za-z]:[\\/]|\\\\|/(?:home|Users|users|root)/)[^\s"'<>|]*"""
)

/** Pontuação que termina a frase e não pertence ao caminho. */
private const val SENTENCE_PUNCTUATION = ".,;:!?)"

private val EMAIL = Regex("""[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}""")
