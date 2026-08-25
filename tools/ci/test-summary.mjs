#!/usr/bin/env node
// Resumo de suite de teste no formato JUnit XML, escrito no `$GITHUB_STEP_SUMMARY`.
//
// Um parser so para os dois jobs: o Gradle ja escreve JUnit XML em
// `build/test-results/**` e o vitest passa a escrever o mesmo formato. Duas
// implementacoes divergiriam justamente no que interessa aqui -- a contagem.
//
// Sem dependencia externa de proposito: no job do desktop nao existe `npm ci`,
// e um resumo que precisa de instalacao para existir e um resumo que some no
// primeiro erro de rede.
import { readFileSync, readdirSync, statSync, appendFileSync } from 'node:fs'
import { join, sep } from 'node:path'

const args = parseArgs(process.argv.slice(2))

if (args.skipped) {
    write(renderSkipped(args.title, args.skipped, args.evaluated))
    process.exit(0)
}

const files = collect(args.glob)
const suites = files.flatMap((file) => parseSuites(readFileSync(file, 'utf8')))
const totals = sum(suites)

write(renderReport(args.title, totals, suites, files.length))

// Suite que devia rodar e nao produziu resultado nenhum e o defeito que a issue
// #93 expoe: verde indistinguivel de verde. Com `--require` ele vira vermelho.
if (args.require && totals.tests === 0) {
    console.error(`[test-summary] nenhum resultado em "${args.glob}" -- a suite nao produziu XML.`)
    process.exit(1)
}

function parseArgs(argv) {
    const parsed = { title: 'Testes', glob: null, skipped: null, evaluated: null, require: false, out: null }
    for (let index = 0; index < argv.length; index += 1) {
        const flag = argv[index]
        if (flag === '--require') parsed.require = true
        else if (flag.startsWith('--')) parsed[flag.slice(2)] = argv[++index]
    }
    if (!parsed.glob && !parsed.skipped) {
        console.error('uso: test-summary.mjs --title <t> (--glob <padrao> [--require] | --skipped <motivo> [--evaluated <n>])')
        process.exit(2)
    }
    return parsed
}

// Glob minimo: so `**/` (qualquer profundidade) e `*` (dentro de um segmento).
// E o que os dois chamadores usam; um matcher completo aqui seria codigo sem
// consumidor.
function collect(pattern) {
    const normalized = pattern.replaceAll('\\', '/')
    const segments = normalized.split('/')
    const wildcardAt = segments.findIndex((segment) => segment.includes('*'))
    const base = wildcardAt <= 0 ? '.' : segments.slice(0, wildcardAt).join('/')
    // Sem sentinela: cada trecho entre `**/` e escapado por si, e a juncao
    // devolve o salto de profundidade.
    const matcher = new RegExp(
        '^' +
            normalized
                .split('**/')
                .map((part) => part.replaceAll('.', '\\.').replaceAll('*', '[^/]*'))
                .join('(?:.*/)?') +
            '$'
    )
    const found = []
    walk(base, found)
    return found.filter((file) => matcher.test(file.replaceAll(sep, '/').replace(/^\.\//, '')))
}

function walk(dir, found) {
    let entries
    try {
        entries = readdirSync(dir)
    } catch {
        return
    }
    for (const entry of entries) {
        const path = join(dir, entry)
        if (statSync(path).isDirectory()) walk(path, found)
        else found.push(path)
    }
}

// Os totais saem dos atributos de `<testsuite>`; os nomes dos testes que
// falharam saem dos `<testcase>` que tem `<failure>` ou `<error>` dentro.
function parseSuites(xml) {
    const suites = []
    for (const match of xml.matchAll(/<testsuite\s([^>]*?)\/?>/g)) {
        const attributes = readAttributes(match[1])
        if (!attributes.name) continue
        suites.push({
            name: attributes.name,
            tests: Number(attributes.tests ?? 0),
            failures: Number(attributes.failures ?? 0),
            errors: Number(attributes.errors ?? 0),
            skipped: Number(attributes.skipped ?? 0),
            seconds: Number(attributes.time ?? 0)
        })
    }
    const failed = []
    for (const match of xml.matchAll(/<testcase\s([^>]*?)(\/>|>([\s\S]*?)<\/testcase>)/g)) {
        const body = match[3] ?? ''
        if (!/<(failure|error)[\s>]/.test(body)) continue
        const attributes = readAttributes(match[1])
        failed.push(`${attributes.classname ?? ''} > ${attributes.name ?? ''}`.trim())
    }
    if (failed.length > 0) suites.push({ failedNames: failed })
    return suites
}

function readAttributes(raw) {
    const attributes = {}
    for (const match of raw.matchAll(/([\w:.-]+)="([^"]*)"/g)) attributes[match[1]] = decode(match[2])
    return attributes
}

function decode(value) {
    return value
        .replaceAll('&quot;', '"')
        .replaceAll('&apos;', "'")
        .replaceAll('&lt;', '<')
        .replaceAll('&gt;', '>')
        .replaceAll('&amp;', '&')
}

function sum(suites) {
    const totals = { tests: 0, failures: 0, errors: 0, skipped: 0, seconds: 0, failedNames: [] }
    for (const suite of suites) {
        if (suite.failedNames) {
            totals.failedNames.push(...suite.failedNames)
            continue
        }
        totals.tests += suite.tests
        totals.failures += suite.failures
        totals.errors += suite.errors
        totals.skipped += suite.skipped
        totals.seconds += suite.seconds
    }
    return totals
}

function renderReport(title, totals, suites, fileCount) {
    const classes = suites.filter((suite) => !suite.failedNames).sort((left, right) => right.seconds - left.seconds)
    const broken = totals.failures + totals.errors > 0
    const verdict = broken ? 'FALHOU' : totals.tests === 0 ? 'SEM RESULTADO' : 'passou'
    const lines = [
        `## ${title} -- ${verdict}`,
        '',
        '| Testes | Falhas | Erros | Pulados | Classes | Duracao |',
        '|---:|---:|---:|---:|---:|---:|',
        `| ${totals.tests} | ${totals.failures} | ${totals.errors} | ${totals.skipped} | ${classes.length} | ${seconds(totals.seconds)} |`,
        ''
    ]
    if (totals.tests === 0) {
        lines.push(`> Nenhum resultado encontrado em \`${args.glob}\` (${fileCount} arquivo(s) varrido(s)).`, '')
    }
    if (totals.failedNames.length > 0) {
        lines.push('### Testes que falharam', '')
        for (const name of totals.failedNames.slice(0, 50)) lines.push(`- \`${name}\``)
        if (totals.failedNames.length > 50) lines.push(`- ... e mais ${totals.failedNames.length - 50}`)
        lines.push('')
    }
    if (classes.length > 0) {
        lines.push('<details><summary>Dez classes mais lentas</summary>', '', '| Classe | Testes | Duracao |', '|---|---:|---:|')
        for (const suite of classes.slice(0, 10)) lines.push(`| \`${suite.name}\` | ${suite.tests} | ${seconds(suite.seconds)} |`)
        lines.push('', '</details>', '')
    }
    return lines.join('\n')
}

function renderSkipped(title, reason, evaluated) {
    const detail = evaluated ? ` Arquivos avaliados no diff: ${evaluated}.` : ''
    return [
        `## ${title} -- NAO EXECUTADA`,
        '',
        'Este check esta verde porque nada relevante mudou, e **nao** porque a suite passou.',
        '',
        `Motivo: ${reason}.${detail}`,
        ''
    ].join('\n')
}

function seconds(value) {
    return `${value.toFixed(1).replace('.', ',')} s`
}

function write(markdown) {
    const target = args.out ?? process.env.GITHUB_STEP_SUMMARY
    if (target) appendFileSync(target, markdown + '\n')
    else process.stdout.write(markdown + '\n')
}
