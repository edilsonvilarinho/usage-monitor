package com.usagemonitor.domain.entity

import kotlinx.datetime.Instant

/**
 * O que mudou na versão que acabou de ser instalada.
 *
 * Existe porque a atualização automática é **silenciosa por construção**: o app
 * fecha, o instalador roda sem tela e o app volta com outro número no rodapé.
 * Sem esta janela, a única pista de que algo mudou é uma linha em
 * Configurações → Geral, que ninguém abre.
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
 * Esta abertura é a primeira depois de uma atualização que deu certo?
 *
 * As três condições são independentes e nenhuma é dispensável:
 *
 * - **Sucesso.** Recibo de falha descreve uma atualização que não aconteceu.
 * - **Recibo da versão em execução.** Recibo da 39 com o app em 37 é prova de
 *   que a troca não se completou, e anunciar as novidades da 39 ali seria mentir
 *   sobre o binário que está rodando.
 * - **Ainda não vista.** O recibo é sobrescrito só na atualização seguinte, ou
 *   seja, sobrevive a todas as aberturas até lá; sem esta marca a janela abriria
 *   toda vez.
 */
fun shouldShowReleaseNotes(
    receipt: AppUpdateReceipt?,
    currentVersion: String,
    seenVersion: String?
): Boolean {
    if (receipt == null || receipt.status != AppUpdateReceiptStatus.SUCCESS) {
        return false
    }
    if (receipt.version != currentVersion) {
        return false
    }
    return seenVersion != currentVersion
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
