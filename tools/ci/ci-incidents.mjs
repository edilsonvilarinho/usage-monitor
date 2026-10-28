#!/usr/bin/env node
// Contador de incidencias do passo 5 do "Fluxo de trabalho" do CLAUDE.md (#496):
// cada run vermelho do `ci.yml` na branch, ou rodada de testes local falhando
// depois de aberto o PR, e uma incidencia; na 4a o agente para.
//
// Contado, nao lembrado: a sessao do agente pode ser resumida ou recomecar, e a
// contagem de cabeca se perde junto. Os runs vem do `gh run list`; as falhas
// locais ficam num arquivo no diretorio COMUM do git, o mesmo para a branch em
// qualquer worktree, e fora do que se commita.
//
// Uso:
//   node tools/ci/ci-incidents.mjs [--branch <b>] [--limit <n>]
//   node tools/ci/ci-incidents.mjs --local "<o que falhou>"   registra e conta
//
// Saida 0 abaixo do limite (padrao 4), 3 ao atingi-lo, 2 em erro de uso ou de gh.
import { execFileSync } from 'node:child_process'
import { appendFileSync, existsSync, mkdirSync, readFileSync } from 'node:fs'
import { isAbsolute, join } from 'node:path'
import { pathToFileURL } from 'node:url'

export const DEFAULT_LIMIT = 4
export const LIMIT_REACHED_EXIT = 3

// `cancelled` nao conta: e o `concurrency` do `ci.yml` trocando um run pelo do
// push seguinte, nao uma falha.
const RED_CONCLUSIONS = new Set(['failure', 'timed_out', 'startup_failure'])

export function isRed(conclusion) {
    return RED_CONCLUSIONS.has(conclusion)
}

// `runs`: saida do `gh run list --json databaseId,attempt,conclusion,url,createdAt`.
// `earlierAttempts`: Map de databaseId -> conclusoes das tentativas anteriores a
// atual. O `gh run list` so devolve a ultima tentativa, e um run vermelho que
// passou no `rerun` sumiria da conta.
export function collectIncidents(runs, earlierAttempts, localEntries) {
    const incidents = []
    for (const run of runs) {
        const earlier = earlierAttempts.get(run.databaseId) ?? []
        earlier.forEach((conclusion, index) => {
            if (isRed(conclusion)) {
                incidents.push({ source: 'ci', at: run.createdAt, detail: `${run.url} (tentativa ${index + 1}: ${conclusion})` })
            }
        })
        if (isRed(run.conclusion)) {
            incidents.push({ source: 'ci', at: run.createdAt, detail: `${run.url} (tentativa ${run.attempt}: ${run.conclusion})` })
        }
    }
    for (const entry of localEntries) {
        incidents.push({ source: 'local', at: entry.at, detail: entry.detail })
    }
    return incidents.sort((left, right) => left.at.localeCompare(right.at) || left.detail.localeCompare(right.detail))
}

export function parseLocalLog(text) {
    return text
        .split('\n')
        .filter((line) => line.trim() !== '')
        .map((line) => {
            const tab = line.indexOf('\t')
            return tab === -1 ? { at: '', detail: line } : { at: line.slice(0, tab), detail: line.slice(tab + 1) }
        })
}

// Nome de branch tem `/`; o arquivo nao pode virar subdiretorio.
export function localLogName(branch) {
    return `${branch.replace(/[^A-Za-z0-9._-]/g, '_')}.log`
}

export function parseArgs(argv) {
    const options = { branch: null, limit: DEFAULT_LIMIT, local: null }
    for (let index = 0; index < argv.length; index++) {
        const flag = argv[index]
        const value = argv[index + 1]
        if (flag === '--branch' && value) options.branch = value
        else if (flag === '--limit' && value) options.limit = Number(value)
        else if (flag === '--local' && value) options.local = value
        else throw new Error(`argumento desconhecido ou sem valor: ${flag}`)
        index++
    }
    if (!Number.isInteger(options.limit) || options.limit < 1) throw new Error('--limit precisa ser inteiro >= 1')
    return options
}

function run(command, args) {
    return execFileSync(command, args, { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] }).trim()
}

function localLogPath(branch) {
    const common = run('git', ['rev-parse', '--git-common-dir'])
    const root = isAbsolute(common) ? common : join(run('git', ['rev-parse', '--show-toplevel']), common)
    return join(root, 'ci-incidents', localLogName(branch))
}

function earlierAttemptConclusions(runs) {
    const repo = run('gh', ['repo', 'view', '--json', 'nameWithOwner', '--jq', '.nameWithOwner'])
    const found = new Map()
    for (const entry of runs.filter((candidate) => candidate.attempt > 1)) {
        const conclusions = []
        for (let attempt = 1; attempt < entry.attempt; attempt++) {
            conclusions.push(run('gh', ['api', `repos/${repo}/actions/runs/${entry.databaseId}/attempts/${attempt}`, '--jq', '.conclusion']))
        }
        found.set(entry.databaseId, conclusions)
    }
    return found
}

function main() {
    const options = parseArgs(process.argv.slice(2))
    const branch = options.branch ?? run('git', ['branch', '--show-current'])
    if (!branch) throw new Error('sem branch atual (HEAD destacado); passe --branch')

    const logPath = localLogPath(branch)
    if (options.local) {
        mkdirSync(join(logPath, '..'), { recursive: true })
        appendFileSync(logPath, `${new Date().toISOString()}\t${options.local.replace(/\s+/g, ' ')}\n`)
    }

    const runs = JSON.parse(run('gh', [
        'run', 'list', '--branch', branch, '--workflow', 'ci.yml', '--limit', '200',
        '--json', 'databaseId,attempt,conclusion,url,createdAt',
    ]))
    const localEntries = existsSync(logPath) ? parseLocalLog(readFileSync(logPath, 'utf8')) : []
    const incidents = collectIncidents(runs, earlierAttemptConclusions(runs), localEntries)

    console.log(`Incidencias em ${branch}: ${incidents.length} de ${options.limit}`)
    incidents.forEach((incident, index) => console.log(`  ${index + 1}. [${incident.source}] ${incident.at} ${incident.detail}`))
    if (incidents.length >= options.limit) {
        console.log('Limite atingido: parar, desligar o auto-merge (gh pr merge --disable-auto) e devolver ao usuario.')
        process.exitCode = LIMIT_REACHED_EXIT
    }
}

if (import.meta.url === pathToFileURL(process.argv[1] ?? '').href) {
    try {
        main()
    } catch (error) {
        console.error(error.message)
        process.exitCode = 2
    }
}
