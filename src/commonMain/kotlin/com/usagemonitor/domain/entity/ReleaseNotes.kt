package com.usagemonitor.domain.entity

import kotlinx.datetime.Instant

/**
 * O que mudou na versão que acabou de ser instalada.
 *
 * Existe porque a atualização automática é **silenciosa por construção**: o app
 * fecha, o instalador roda sem tela e o app volta com outro número no rodapé.
 * Sem esta janela, a única pista de que algo mudou é uma linha em
 * Configurações → Geral, que ninguém abre. Vale igualmente para a instalação
 * manual, que também troca o binário sem dizer o que mudou.
 *
 * [items] pode ser vazio — release só de `chore`/`docs` não tem nada a dizer ao
 * usuário —, e nesse caso **não há janela**. Lista vazia numa tela de novidades
 * afirma que a versão não trouxe nada, quando o certo é não abrir a tela.
 */
data class ReleaseNotes(
    val version: String,
    /** Versão de onde se veio, do recibo. Nula quando o instalador não conseguiu lê-la. */
    val previousVersion: String?,
    /** `published_at` da release. Nulo é "não informado" — a linha de data some. */
    val publishedAt: Instant?,
    val releasePageUrl: String,
    val items: List<String>
)

/**
 * O que fazer nesta abertura a respeito das novidades.
 *
 * Enum próprio porque os três desfechos não são dois: "não abrir" cobre duas
 * situações que exigem escritas diferentes — a que precisa gravar a marca e a
 * que não pode gravá-la.
 */
enum class ReleaseNotesDecision {
    /** Nada a fazer: esta versão já foi anunciada. */
    SKIP,

    /**
     * Marcar a versão como vista **sem** abrir a janela e **sem** ir à rede.
     * Instalação nova e retrocesso caem aqui.
     */
    MARK_SEEN_ONLY,

    /** Buscar as notas e abrir a janela. */
    SHOW
}

/**
 * A versão em execução mudou desde a última que o usuário viu?
 *
 * **O recibo do instalador não decide nada aqui**, e é por isso que ele entra
 * como um booleano de existência em vez de entidade. Ele era a condição
 * principal, e isso escondia a janela em quase todo lugar (issue #127):
 *
 * - no **Linux** o `linux-updater.sh` só grava o recibo **depois** do ACK, que é
 *   escrito pelo app novo já em execução — quando este código roda, o arquivo
 *   ainda descreve a atualização anterior. A ordem lá está certa: antes do ACK
 *   ainda pode haver rollback. Quem perde a corrida é o leitor, sempre;
 * - **instalação manual** (`Setup.exe` sem `/UPDATE`, `.sh`, `.deb`, `.rpm`) não
 *   escreve recibo nenhum;
 * - no **macOS** não existe instalador automático, então nunca houve recibo.
 *
 * Os ramos, na ordem em que são avaliados:
 *
 * - **Sem marca e sem recibo**: instalação nova. Marca em silêncio — "novidades"
 *   para quem não tem versão anterior não descreve mudança nenhuma.
 * - **Sem marca mas com recibo**: a máquina já atualizou alguma vez, então não é
 *   instalação nova. Abre. Sem este ramo, quem foi atingido pela #127 — que por
 *   definição nunca chegou a marcar nada — só veria a janela uma versão depois
 *   de a correção ser publicada.
 * - **Marca igual à versão em execução**: nada a fazer. Igualdade textual,
 *   exata e barata.
 * - **Versão em execução mais nova**: abre.
 * - **Resto**: marca em silêncio. Cobre o retrocesso — anunciar a 38.0.1 vindo
 *   da 38.0.2 seria falso, e é este ramo que reconcilia a marca depois de um
 *   `health-timeout` do updater do Linux, em que o app novo chega a abrir a
 *   janela antes de o script desistir e restaurar a versão anterior. Cobre
 *   também "mesma versão escrita de outro jeito" (`38.0.2` × `38.0.02`), que a
 *   igualdade textual não pega: a marca é reescrita na forma canônica e a
 *   abertura seguinte resolve em [SKIP] sem escrita nenhuma.
 */
fun releaseNotesDecision(
    currentVersion: String,
    seenVersion: String?,
    hasUpdateReceipt: Boolean
): ReleaseNotesDecision {
    if (seenVersion == null) {
        return if (hasUpdateReceipt) ReleaseNotesDecision.SHOW else ReleaseNotesDecision.MARK_SEEN_ONLY
    }
    if (seenVersion == currentVersion) {
        return ReleaseNotesDecision.SKIP
    }
    return if (compareAppVersions(currentVersion, seenVersion) > 0) {
        ReleaseNotesDecision.SHOW
    } else {
        ReleaseNotesDecision.MARK_SEEN_ONLY
    }
}

/**
 * De onde se veio, para o subtítulo da janela.
 *
 * O recibo é a fonte **exata** quando ele descreve a atualização que trouxe o
 * binário em execução — é o caminho automático do Windows, onde o instalador lê
 * a versão anterior do registro antes de sobrescrevê-la.
 *
 * Fora disso vale a marca, que é a última versão que o usuário viu e portanto a
 * versão de onde ele veio. É o que acontece no Linux, onde o recibo presente na
 * primeira abertura descreve a atualização **anterior** e a guarda de versão o
 * exclui sozinha, e em toda instalação manual, onde não há recibo.
 *
 * O `?:` e não um `if` sobre o recibo inteiro: [AppUpdateReceipt.previousVersion]
 * é anulável — o instalador nem sempre consegue lê-la —, e um recibo válido com
 * a versão anterior ilegível descartaria uma marca conhecida, apagando o
 * subtítulo em vez de completá-lo.
 */
fun releaseNotesPreviousVersion(
    receipt: AppUpdateReceipt?,
    currentVersion: String,
    seenVersion: String?
): String? {
    val fromReceipt = receipt
        ?.takeIf { it.status == AppUpdateReceiptStatus.SUCCESS && it.version == currentVersion }
        ?.previousVersion
    return fromReceipt ?: seenVersion
}

/**
 * Tipos de commit que descrevem mudança que o usuário percebe.
 *
 * O corpo da release é gerado pelo CI a partir dos assuntos de commit, em
 * Conventional Commits. `chore`, `docs`, `ci`, `test`, `build`, `refactor`,
 * `style` e `perf` descrevem trabalho interno: numa tela chamada "Novidades"
 * eles empurram para fora da vista justamente o que ela existe para mostrar.
 */
private val USER_FACING_COMMIT_TYPES = setOf("feat", "fix")

/**
 * Casa o prefixo Conventional Commits: tipo, escopo opcional, `!` opcional.
 *
 * O grupo 1 é o tipo, que é o que decide se a linha entra.
 */
private val CONVENTIONAL_PREFIX = Regex("^([a-z]+)(\\([^)]*\\))?(!)?:\\s+")

/** Sufixo `` (`abc1234`) `` que o gerador acrescenta a cada assunto. */
private val COMMIT_HASH_SUFFIX = Regex("\\s*\\(`[0-9a-f]{4,40}`\\)\\s*$")

/** Linhas de cabeçalho que o gerador do CI produz e que não são mudança nenhuma. */
private val GENERATOR_LINES = listOf("Compare:", "Initial release", "No user-facing commits")

/**
 * Extrai da release do GitHub as linhas que valem uma tela de novidades.
 *
 * O corpo é markdown gerado por `.github/workflows/release-linux.yml`: um
 * `## Changes`, uma linha de comparação e um item por commit no formato
 * `` - <assunto> (`<sha>`) ``.
 *
 * Linha que **não** casa Conventional Commits passa como está, sem filtro: é o
 * caso da release editada à mão, e descartá-la faria uma nota escrita para o
 * usuário virar tela vazia — o oposto do que o filtro existe para fazer.
 */
fun parseReleaseNoteItems(body: String?): List<String> {
    if (body.isNullOrBlank()) {
        return emptyList()
    }

    val items = mutableListOf<String>()
    for (rawLine in body.lineSequence()) {
        val line = rawLine.trim()
        if (!line.startsWith("- ") && !line.startsWith("* ")) {
            continue
        }

        val content = line.drop(2).trim()
        if (content.isEmpty() || GENERATOR_LINES.any { content.startsWith(it) }) {
            continue
        }

        val withoutHash = content.replace(COMMIT_HASH_SUFFIX, "").trim()
        val match = CONVENTIONAL_PREFIX.find(withoutHash)
        val item = when {
            match == null -> withoutHash
            match.groupValues[1] in USER_FACING_COMMIT_TYPES -> withoutHash.removeRange(match.range).trim()
            else -> continue
        }

        // Dedup por conteúdo: dois commits com o mesmo assunto viram duas linhas
        // idênticas na tela, e a segunda não informa nada.
        if (item.isNotEmpty() && item !in items) {
            items += item
        }
    }
    return items
}
