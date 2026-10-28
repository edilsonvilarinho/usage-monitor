#!/usr/bin/env node
// Cobertura das linhas que um PR adiciona ou altera (#496). A catraca global
// (`koverVerify`, `thresholds.lines` do vitest) mede o produto inteiro e, a 95%,
// tem folga de dezenas de linhas: um PR pode gastar a folga toda sem nenhum
// sinal. Este script mede so o que o diff toca, contra um piso proprio.
//
// Entra a linha de producao que o diff adiciona ou altera E que o relatorio
// instrumenta. Linha fora do relatorio (comentario, declaracao, classe excluida
// por nome no Kover) nao entra; sem nenhuma linha instrumentada o PR passa.
// Linha coberta e a com instrucao executada (`ci > 0` no Kover, contagem > 0 no
// lcov) -- o mesmo criterio do contador `LINE` que a catraca global usa.
//
// Uso:
//   node tools/ci/diff-coverage.mjs --base <rev> (--kover <report.xml> | --lcov <lcov.info>)
//        [--head <rev>] [--min <pct>] [--title <titulo>]
//
// Saida 0 no piso ou acima, 1 abaixo, 2 em erro de uso, de git ou de relatorio.
import { execFileSync } from 'node:child_process'
import { appendFileSync, readFileSync } from 'node:fs'
import { pathToFileURL } from 'node:url'

export const DEFAULT_MIN_PERCENT = 90

// Fonte de producao de cada formato; teste e script nao contam.
const KOTLIN_SOURCE = /^src\/[A-Za-z]+Main\/kotlin\/(.+\.kt)$/
const SERVER_SOURCE = /^server\/src\/.+\.ts$/

// `git diff --unified=0`: cada `@@ -a,b +c,d @@` traz as linhas c..c+d-1 do
// lado novo; `d` ausente vale 1 e `d = 0` e remocao pura.
export function parseDiff(text) {
    const changed = new Map()
    let current = null
    for (const line of text.split('\n')) {
        if (line.startsWith('+++ ')) {
            const target = line.slice(4).trim()
            current = target === '/dev/null' ? null : target.replace(/^b\//, '')
            if (current && !changed.has(current)) changed.set(current, new Set())
            continue
        }
        const hunk = /^@@ -\d+(?:,\d+)? \+(\d+)(?:,(\d+))? @@/.exec(line)
        if (hunk && current) {
            const start = Number(hunk[1])
            const count = hunk[2] === undefined ? 1 : Number(hunk[2])
            for (let offset = 0; offset < count; offset++) changed.get(current).add(start + offset)
        }
    }
    return changed
}

// Formato JaCoCo do Kover: `<package name="com/x">` > `<sourcefile name="F.kt">`
// > `<line nr mi ci .../>`. Chave: `com/x/F.kt`, o caminho depois de `kotlin/`.
export function parseKover(xml) {
    const files = new Map()
    for (const pkg of xml.matchAll(/<package\s+name="([^"]*)"[^>]*>([\s\S]*?)<\/package>/g)) {
        for (const source of pkg[2].matchAll(/<sourcefile\s+name="([^"]*)"[^>]*>([\s\S]*?)<\/sourcefile>/g)) {
            const lines = new Map()
            for (const entry of source[2].matchAll(/<line\s+nr="(\d+)"\s+mi="(\d+)"\s+ci="(\d+)"/g)) {
                lines.set(Number(entry[1]), Number(entry[3]) > 0)
            }
            files.set(`${pkg[1]}/${source[1]}`, lines)
        }
    }
    return files
}

export function parseLcov(text) {
    const files = new Map()
    let lines = null
    for (const raw of text.split('\n')) {
        const line = raw.trim()
        if (line.startsWith('SF:')) {
            lines = new Map()
            files.set(line.slice(3).replaceAll('\\', '/'), lines)
        } else if (line.startsWith('DA:') && lines) {
            const [number, hits] = line.slice(3).split(',')
            lines.set(Number(number), Number(hits) > 0)
        } else if (line === 'end_of_record') {
            lines = null
        }
    }
    return files
}

// O caminho do diff e relativo a raiz do repositorio; o do relatorio e o que a
// ferramenta escreveu. Kover: o caminho do pacote; lcov: absoluto ou relativo a
// `server/`, conforme a versao do vitest -- por isso o casamento por sufixo.
// `owns` separa o arquivo do outro formato (Kotlin na medicao do servidor) do
// arquivo deste formato que o relatorio nao traz.
export function koverResolver(report) {
    return {
        owns: (path) => KOTLIN_SOURCE.test(path),
        lookup: (path) => report.get(KOTLIN_SOURCE.exec(path)[1]),
    }
}

export function lcovResolver(report) {
    const entries = [...report.entries()]
    return {
        owns: (path) => SERVER_SOURCE.test(path),
        lookup: (path) => entries.find(([source]) => source === path || source.endsWith(`/${path}`) || path.endsWith(`/${source}`))?.[1],
    }
}

export function measure(changed, resolver) {
    const files = []
    let outsideReport = 0
    for (const [path, lines] of [...changed.entries()].sort(([left], [right]) => left.localeCompare(right))) {
        if (!resolver.owns(path)) continue
        const coverage = resolver.lookup(path)
        if (!coverage) {
            outsideReport += lines.size
            continue
        }
        const instrumented = [...lines].filter((line) => coverage.has(line)).sort((left, right) => left - right)
        if (instrumented.length === 0) continue
        const uncovered = instrumented.filter((line) => !coverage.get(line))
        files.push({ path, total: instrumented.length, covered: instrumented.length - uncovered.length, uncovered })
    }
    const total = files.reduce((sum, file) => sum + file.total, 0)
    const covered = files.reduce((sum, file) => sum + file.covered, 0)
    return { files, total, covered, outsideReport }
}

export function passes(result, minPercent) {
    return result.total === 0 || (result.covered * 100) / result.total >= minPercent
}

// `[3,4,5,9]` -> `3-5, 9`: a lista de linhas descobertas cabe numa celula.
export function compactRanges(numbers) {
    const ranges = []
    for (const number of numbers) {
        const last = ranges.at(-1)
        if (last && number === last[1] + 1) last[1] = number
        else ranges.push([number, number])
    }
    return ranges.map(([start, end]) => (start === end ? `${start}` : `${start}-${end}`)).join(', ')
}

function percentText(covered, total) {
    return `${((covered * 100) / total).toFixed(1).replace('.', ',')}%`
}

export function render(result, minPercent, title) {
    const ok = passes(result, minPercent)
    const lines = [`## ${title} -- ${ok ? 'passou' : 'REPROVOU'}`, '']
    if (result.total === 0) {
        lines.push('Nenhuma linha instrumentada no diff.')
    } else {
        lines.push(`**${result.covered} de ${result.total}** linhas alteradas cobertas (${percentText(result.covered, result.total)}); piso ${minPercent}%.`)
        lines.push('', '| Arquivo | Cobertas | Linhas descobertas |', '|---|---|---|')
        for (const file of result.files) {
            lines.push(`| \`${file.path}\` | ${file.covered}/${file.total} | ${compactRanges(file.uncovered) || '--'} |`)
        }
    }
    if (result.outsideReport > 0) {
        lines.push('', `${result.outsideReport} linha(s) de producao em arquivos fora do relatorio (excluidos por nome) nao entram na conta.`)
    }
    return `${lines.join('\n')}\n`
}

export function parseArgs(argv) {
    const options = { base: null, head: 'HEAD', kover: null, lcov: null, min: DEFAULT_MIN_PERCENT, title: 'Cobertura do diff' }
    for (let index = 0; index < argv.length; index += 2) {
        const flag = argv[index]
        const value = argv[index + 1]
        const key = flag?.replace(/^--/, '')
        if (!flag?.startsWith('--') || !(key in options) || value === undefined) throw new Error(`argumento desconhecido ou sem valor: ${flag}`)
        options[key] = key === 'min' ? Number(value) : value
    }
    if (!options.base) throw new Error('--base e obrigatorio')
    if (Boolean(options.kover) === Boolean(options.lcov)) throw new Error('passe exatamente um de --kover e --lcov')
    if (!Number.isFinite(options.min) || options.min < 0 || options.min > 100) throw new Error('--min precisa estar entre 0 e 100')
    return options
}

function main() {
    const options = parseArgs(process.argv.slice(2))
    const diff = execFileSync('git', ['diff', '--unified=0', '--no-color', '--no-ext-diff', '-M', options.base, options.head], {
        encoding: 'utf8',
        maxBuffer: 256 * 1024 * 1024,
    })
    const resolver = options.kover
        ? koverResolver(parseKover(readFileSync(options.kover, 'utf8')))
        : lcovResolver(parseLcov(readFileSync(options.lcov, 'utf8')))
    const result = measure(parseDiff(diff), resolver)
    const text = render(result, options.min, options.title)
    process.stdout.write(text)
    if (process.env.GITHUB_STEP_SUMMARY) appendFileSync(process.env.GITHUB_STEP_SUMMARY, `${text}\n`)
    if (!passes(result, options.min)) process.exitCode = 1
}

if (import.meta.url === pathToFileURL(process.argv[1] ?? '').href) {
    try {
        main()
    } catch (error) {
        console.error(error.message)
        process.exitCode = 2
    }
}
