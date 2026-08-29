package com.usagemonitor.domain.entity

/**
 * Ordenação de versões do app, com **um** dono.
 *
 * Estava em `data/repository/AppUpdateRepositoryImpl.kt`, onde nasceu para
 * responder "a release publicada é mais nova que a instalada?". A decisão de
 * mostrar as novidades faz a mesma pergunta ao contrário — "a versão em execução
 * é mais nova que a última que o usuário viu?" — e ela mora no domain, que não
 * pode importar de `data`. Duplicar o parser daria dois donos que divergem no
 * primeiro caso de borda: o `v` da tag e o sufixo de pré-lançamento.
 *
 * O domain precisa do **sinal**, não do booleano: é ele que separa atualização
 * de retrocesso, e retrocesso não é "não atualizou".
 */

/**
 * Sinal da comparação, no contrato de [Comparator]: negativo, zero ou positivo.
 *
 * Duas normalizações, ambas herdadas do uso original e nenhuma acidental:
 *
 * - o prefixo `v` sai, porque as tags do projeto o levam e os números do app
 *   não;
 * - o sufixo de pré-lançamento sai (`substringBefore("-")`), então `8.0.1-beta`
 *   e `8.0.1` comparam **iguais**. É o que impede o instalador de oferecer uma
 *   pré-release como se fosse versão nova da mesma numeração.
 *
 * **Falha fechado**: componente que não é número vira `0`, e versão ilegível
 * inteira vira `0`. Quem chama nunca recebe exceção, e o pior desfecho é
 * "iguais" — que nas duas decisões que dependem daqui significa não fazer nada.
 */
internal fun compareAppVersions(left: String, right: String): Int {
    val leftParts = versionParts(left)
    val rightParts = versionParts(right)
    val maxSize = maxOf(leftParts.size, rightParts.size)

    for (index in 0 until maxSize) {
        // Componente ausente é zero e não "menor que tudo": 38.1 e 38.1.0 são a
        // mesma versão escrita de dois jeitos.
        val leftPart = leftParts.getOrElse(index) { 0 }
        val rightPart = rightParts.getOrElse(index) { 0 }

        if (leftPart != rightPart) {
            return leftPart.compareTo(rightPart)
        }
    }

    return 0
}

/**
 * Casca fina sobre [compareAppVersions], mantida porque é o que os cinco
 * chamadores do caminho de atualização já perguntam.
 */
internal fun isVersionNewer(candidateVersion: String, currentVersion: String): Boolean {
    return compareAppVersions(candidateVersion, currentVersion) > 0
}

private fun versionParts(version: String): List<Int> {
    val normalizedVersion = version
        .trim()
        .removePrefix("v")
        .substringBefore("-")

    if (normalizedVersion.isBlank()) {
        return listOf(0)
    }

    return normalizedVersion.split(".").map { token ->
        token.filter(Char::isDigit).toIntOrNull() ?: 0
    }
}
