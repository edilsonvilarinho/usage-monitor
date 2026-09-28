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
 * Normalizações:
 *
 * - o prefixo `v` sai, porque as tags do projeto o levam e os números do app
 *   não;
 * - o sufixo de pré-lançamento (`-beta.N`) segue a precedência do SemVer: com o
 *   núcleo numérico igual, a versão **com** sufixo é menor, e dois sufixos se
 *   comparam identificador a identificador — número contra número pelo valor,
 *   texto contra texto pela ordem léxica, número antes de texto, lista mais
 *   curta antes quando uma é prefixo da outra. Assim
 *   `42.0.0-beta.1 < 42.0.0-beta.2 < 42.0.0` (issue #355). Antes o sufixo era
 *   descartado e as três comparavam iguais — o canal beta nunca oferecia a
 *   `beta.2` a quem estava na `beta.1`, nem a estável a quem estava na beta.
 *
 * O sufixo só conta quando o núcleo antes do `-` é numérico legível: sem isso,
 * `sem-numero` passaria a ser "pré-lançamento de 0" e deixaria de comparar
 * igual a uma versão vazia.
 *
 * **Falha fechado**: componente que não é número vira `0`, e versão ilegível
 * inteira vira `0`. Quem chama nunca recebe exceção, e o pior desfecho é
 * "iguais" — que nas decisões que dependem daqui significa não fazer nada.
 */
internal fun compareAppVersions(left: String, right: String): Int {
    val leftVersion = parseAppVersion(left)
    val rightVersion = parseAppVersion(right)
    val maxSize = maxOf(leftVersion.numbers.size, rightVersion.numbers.size)

    for (index in 0 until maxSize) {
        // Componente ausente é zero e não "menor que tudo": 38.1 e 38.1.0 são a
        // mesma versão escrita de dois jeitos.
        val leftPart = leftVersion.numbers.getOrElse(index) { 0 }
        val rightPart = rightVersion.numbers.getOrElse(index) { 0 }

        if (leftPart != rightPart) {
            return leftPart.compareTo(rightPart)
        }
    }

    return comparePrerelease(leftVersion.prerelease, rightVersion.prerelease)
}

/**
 * Casca fina sobre [compareAppVersions], mantida porque é o que os cinco
 * chamadores do caminho de atualização já perguntam.
 */
internal fun isVersionNewer(candidateVersion: String, currentVersion: String): Boolean {
    return compareAppVersions(candidateVersion, currentVersion) > 0
}

/**
 * A versão é um pré-lançamento (`42.0.0-beta.1`)? É o que as superfícies de
 * atualização, novidades e rodapé perguntam para dizer "beta".
 */
internal fun isPrereleaseVersion(version: String): Boolean {
    return parseAppVersion(version).prerelease.isNotEmpty()
}

private class ParsedAppVersion(
    val numbers: List<Int>,
    /** Identificadores do sufixo, vazio para versão estável. */
    val prerelease: List<String>
)

private val NUMERIC_VERSION_CORE = Regex("""^\d+(\.\d+)*$""")

private fun parseAppVersion(version: String): ParsedAppVersion {
    val normalizedVersion = version
        .trim()
        .removePrefix("v")
        // Metadado de build (`+sha`) não entra na precedência do SemVer.
        .substringBefore("+")
    val core = normalizedVersion.substringBefore("-")
    val suffix = normalizedVersion.substringAfter("-", missingDelimiterValue = "")
    val prerelease = if (suffix.isNotEmpty() && NUMERIC_VERSION_CORE.matches(core)) {
        suffix.split(".")
    } else {
        emptyList()
    }

    return ParsedAppVersion(numbers = versionParts(core), prerelease = prerelease)
}

private fun versionParts(core: String): List<Int> {
    if (core.isBlank()) {
        return listOf(0)
    }

    return core.split(".").map { token ->
        token.filter(Char::isDigit).toIntOrNull() ?: 0
    }
}

private fun comparePrerelease(left: List<String>, right: List<String>): Int {
    // Estável vence pré-lançamento do mesmo núcleo.
    if (left.isEmpty() || right.isEmpty()) {
        return right.size.coerceAtMost(1) - left.size.coerceAtMost(1)
    }

    val sharedSize = minOf(left.size, right.size)
    for (index in 0 until sharedSize) {
        val comparison = comparePrereleaseIdentifier(left[index], right[index])
        if (comparison != 0) {
            return comparison
        }
    }

    return left.size.compareTo(right.size)
}

private fun comparePrereleaseIdentifier(left: String, right: String): Int {
    val leftIsNumeric = left.isNotEmpty() && left.all(Char::isDigit)
    val rightIsNumeric = right.isNotEmpty() && right.all(Char::isDigit)

    return when {
        leftIsNumeric && rightIsNumeric -> compareNumericIdentifiers(left, right)
        leftIsNumeric -> -1
        rightIsNumeric -> 1
        else -> left.compareTo(right)
    }
}

// Por comprimento e depois lexicamente: não estoura `Int` com `beta.99999999999`.
private fun compareNumericIdentifiers(left: String, right: String): Int {
    val leftDigits = left.trimStart('0')
    val rightDigits = right.trimStart('0')
    if (leftDigits.length != rightDigits.length) {
        return leftDigits.length.compareTo(rightDigits.length)
    }
    return leftDigits.compareTo(rightDigits)
}
