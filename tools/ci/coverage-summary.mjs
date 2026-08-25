#!/usr/bin/env node
// Resumo de cobertura no `$GITHUB_STEP_SUMMARY`, a partir do XML do Kover.
//
// O formato e o do JaCoCo: contadores `<counter type=... missed=... covered=.../>`
// aninhados em `<package>` e `<class>`, e os do FECHAMENTO do documento sao os
// totais. Ler os do topo daria o contador do primeiro pacote, nao o do relatorio.
//
// Script proprio, e nao um modo do `test-summary.mjs`: aquele responde "a suite
// rodou e o que deu"; este responde "quanto do produto foi exercitado". Juntar
// os dois num arquivo so por serem ambos "resumo" nao os torna a mesma pergunta.
import { readFileSync, appendFileSync } from 'node:fs'

const [xmlPath, ...rest] = process.argv.slice(2)
if (!xmlPath) {
    console.error('uso: coverage-summary.mjs <report.xml> [--top <n>]')
    process.exit(2)
}
const topIndex = rest.indexOf('--top')
const top = topIndex === -1 ? 10 : Number(rest[topIndex + 1] ?? 10)

const xml = readFileSync(xmlPath, 'utf8')
const totals = readTotals(xml)
const packages = readPackages(xml)

write(render(totals, packages))

// Os contadores do relatorio inteiro sao os que vem DEPOIS do ultimo
// `</package>`: o Kover os emite no fechamento do documento, nao na abertura.
function readTotals(source) {
    const lastPackage = source.lastIndexOf('</package>')
    const tail = lastPackage === -1 ? source : source.slice(lastPackage)
    return readCounters(tail)
}

function readPackages(source) {
    const found = []
    for (const match of source.matchAll(/<package\s+name="([^"]*)"([\s\S]*?)<\/package>/g)) {
        // Os contadores do pacote sao os ultimos filhos diretos; os das classes
        // vem antes e somam no mesmo total, entao so o ultimo bloco interessa.
        const body = match[2]
        const lastClose = body.lastIndexOf('</class>')
        const tail = lastClose === -1 ? body : body.slice(lastClose)
        const counters = readCounters(tail)
        if (!counters.LINE) continue
        found.push({ name: match[1].replaceAll('/', '.'), ...counters })
    }
    return found
}

function readCounters(source) {
    const counters = {}
    for (const match of source.matchAll(/<counter type="(\w+)" missed="(\d+)" covered="(\d+)"\s*\/>/g)) {
        const missed = Number(match[2])
        const covered = Number(match[3])
        counters[match[1]] = { missed, covered, total: missed + covered }
    }
    return counters
}

function percent(counter) {
    if (!counter || counter.total === 0) return '--'
    return `${((100 * counter.covered) / counter.total).toFixed(1).replace('.', ',')}%`
}

function render(totals, packages) {
    const uncovered = packages
        .filter((entry) => entry.LINE.missed > 0)
        .sort((left, right) => right.LINE.missed - left.LINE.missed)
        .slice(0, top)

    const lines = [
        '## Cobertura (Kover)',
        '',
        '| Linhas | Ramos | Metodos | Classes |',
        '|---:|---:|---:|---:|',
        `| ${percent(totals.LINE)} | ${percent(totals.BRANCH)} | ${percent(totals.METHOD)} | ${percent(totals.CLASS)} |`,
        '',
        `Linhas cobertas: ${totals.LINE?.covered ?? 0} de ${totals.LINE?.total ?? 0}.`,
        'Numero de referencia, sem piso: nenhuma build falha por causa dele.',
        ''
    ]

    if (uncovered.length > 0) {
        lines.push(
            `<details><summary>Pacotes com mais linhas descobertas (${uncovered.length})</summary>`,
            '',
            '| Pacote | Linhas descobertas | Cobertura |',
            '|---|---:|---:|'
        )
        for (const entry of uncovered) {
            lines.push(`| \`${entry.name}\` | ${entry.LINE.missed} | ${percent(entry.LINE)} |`)
        }
        lines.push('', '</details>', '')
    }

    return lines.join('\n')
}

function write(markdown) {
    const target = process.env.GITHUB_STEP_SUMMARY
    if (target) appendFileSync(target, markdown + '\n')
    else process.stdout.write(markdown + '\n')
}
