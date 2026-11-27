// Benchmark de repositorios (#687, A02): parte pura, sem I/O. Ranqueia o alvo contra pares
// em Scorecard e entrega (releases, mediana de merge, taxa de revert) e gera markdown.
// A casca (gh, API do Scorecard) so roda como script principal.

import { execFileSync } from 'node:child_process'
import { existsSync, readFileSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

const DAY_MS = 86_400_000
const HOUR_MS = 3_600_000
const PEERS_FILE = fileURLToPath(new URL('./repo-benchmark-peers.json', import.meta.url))
const SCORECARD_API = 'https://api.securityscorecards.dev/projects/github.com'
const REVERT_TITLE = /^revert\b/i
const FLAGS = new Map([
    ['--peers', 'peers'],
    ['--scorecard-dir', 'scorecardDir'],
    ['--window-days', 'windowDays'],
    ['--out', 'out'],
    ['--md', 'md'],
])

export const AXES = [
    { key: 'scorecard', direction: 'higher' },
    { key: 'releases', direction: 'higher' },
    { key: 'medianMergeHours', direction: 'lower' },
    { key: 'revertRate', direction: 'lower' },
]

const isNumber = (value) => typeof value === 'number' && Number.isFinite(value)
const isMissing = (value) => value === null || value === undefined
const round = (value, digits) => Math.round(value * 10 ** digits) / 10 ** digits

function median(sorted) {
    const middle = Math.floor(sorted.length / 2)
    return sorted.length % 2 === 1 ? sorted[middle] : (sorted[middle - 1] + sorted[middle]) / 2
}

// Converte o JSON do Scorecard (saida do CLI ou resposta da API). Check inconclusivo (score -1)
// vira null: nao medido, nunca zero.
export function scorecardFromJson(json) {
    const checks = Array.isArray(json?.checks) ? json.checks : []
    return {
        score: isNumber(json?.score) ? json.score : null,
        date: json?.date ?? null,
        checks: checks.map((check) => ({ name: check.name, score: check.score === -1 ? null : check.score })),
    }
}

// Entrega na janela [now - windowDays, now]. Sem PR mesclado na janela, mediana e taxa de revert
// sao null (nao medido), nunca zero. Revert: titulo que casa /^revert\b/i.
export function deliveryMetrics({ prs, releases, now, windowDays = 90 }) {
    const end = now.getTime()
    const start = end - windowDays * DAY_MS
    const inWindow = (iso) => {
        const time = Date.parse(iso)
        return time >= start && time <= end
    }
    const merged = prs.filter((pr) => inWindow(pr.mergedAt))
    const published = releases.filter((release) => !release.isDraft && inWindow(release.publishedAt))
    const hours = merged
        .map((pr) => (Date.parse(pr.mergedAt) - Date.parse(pr.createdAt)) / HOUR_MS)
        .sort((a, b) => a - b)
    const reverts = merged.filter((pr) => REVERT_TITLE.test(pr.title)).length
    return {
        releases: published.length,
        mergedPrs: merged.length,
        medianMergeHours: hours.length === 0 ? null : round(median(hours), 1),
        revertRate: merged.length === 0 ? null : round(reverts / merged.length, 3),
    }
}

// Posicao (1 = melhor) e percentil de cada repositorio num eixo. Empate divide a posicao; null
// fica fora do ranking. Percentil = piso de 100 x piores / (n - 1); sozinho no eixo, 100.
export function rankAxis(rows, key, direction) {
    const valued = rows.map((row) => row[key]).filter((value) => !isMissing(value))
    const of = valued.length
    const isBetter = direction === 'higher' ? (a, b) => a > b : (a, b) => a < b
    const isWorse = direction === 'higher' ? (a, b) => a < b : (a, b) => a > b
    const ranked = new Map()
    for (const row of rows) {
        const value = row[key]
        if (isMissing(value)) {
            ranked.set(row.repo, { position: null, of, percentile: null })
            continue
        }
        const ahead = valued.filter((other) => isBetter(other, value)).length
        const behind = valued.filter((other) => isWorse(other, value)).length
        const percentile = of === 1 ? 100 : Math.floor((100 * behind) / (of - 1))
        ranked.set(row.repo, { position: ahead + 1, of, percentile })
    }
    return ranked
}

// Alvo primeiro, depois cada grupo na ordem do arquivo de pares. A posicao do alvo e calculada
// contra todas as linhas (alvo e pares) em cada eixo.
export function buildReport({ target, groups, data }) {
    const members = [{ repo: target, group: 'target' }]
    for (const group of Object.keys(groups)) {
        for (const repo of groups[group]) members.push({ repo, group })
    }
    const rows = members.map(({ repo, group }) => {
        const metrics = data.get(repo) ?? {}
        return {
            repo,
            group,
            scorecard: metrics.scorecard ?? null,
            releases: metrics.releases ?? null,
            mergedPrs: metrics.mergedPrs ?? null,
            medianMergeHours: metrics.medianMergeHours ?? null,
            revertRate: metrics.revertRate ?? null,
        }
    })
    const targetPositions = {}
    for (const axis of AXES) {
        targetPositions[axis.key] = rankAxis(rows, axis.key, axis.direction).get(target)
    }
    return { target, rows, targetPositions }
}

const decimal = (value, digits) => value.toFixed(digits).replace('.', ',')
const cell = (value, format = String) => (isMissing(value) ? '—' : format(value))
const formatPosition = ({ position, of, percentile }) =>
    position === null ? 'não medido' : `${position}º de ${of} (percentil ${percentile})`

export function renderMarkdown(report) {
    const lines = [
        '| Repositório | Grupo | Scorecard | Releases 90d | PRs mesclados 90d | Mediana merge (h) | Taxa de revert |',
        '|---|---|---|---:|---:|---:|---:|',
    ]
    for (const row of report.rows) {
        const cells = [
            row.repo,
            row.group,
            cell(row.scorecard, (score) => decimal(score, 1)),
            cell(row.releases),
            cell(row.mergedPrs),
            cell(row.medianMergeHours, (hours) => decimal(hours, 1)),
            cell(row.revertRate, (rate) => `${decimal(rate * 100, 1)}%`),
        ]
        lines.push(`| ${cells.join(' | ')} |`)
    }
    lines.push('', '### Posição do alvo', '')
    for (const axis of AXES) {
        lines.push(`- ${axis.key}: ${formatPosition(report.targetPositions[axis.key])}`)
    }
    return `${lines.join('\n')}\n`
}

// ---- Casca: gh e API do Scorecard. Comandos externos por execFileSync, nunca shell. ----

function parseArgs(argv) {
    const options = { peers: PEERS_FILE, scorecardDir: null, windowDays: 90, out: null, md: null }
    for (let i = 0; i < argv.length; i += 2) {
        const key = FLAGS.get(argv[i])
        if (!key || argv[i + 1] === undefined) throw new Error(`argumento invalido: ${argv[i]}`)
        options[key] = key === 'windowDays' ? Number(argv[i + 1]) : argv[i + 1]
    }
    if (!Number.isInteger(options.windowDays) || options.windowDays <= 0) {
        throw new Error('--window-days deve ser inteiro positivo')
    }
    return options
}

function gh(args) {
    return execFileSync('gh', args, { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] })
}

function warn(repo, axis, error) {
    process.stderr.write(`[repo-benchmark] ${repo} (${axis}): ${error?.message ?? error}\n`)
}

// Arquivo local tem precedencia sobre a API; resposta nao-200 ou nao-JSON vira null.
async function loadScorecard(repo, scorecardDir) {
    const [owner, name] = repo.split('/')
    const local = scorecardDir ? join(scorecardDir, `${owner}__${name}.json`) : null
    if (local && existsSync(local)) return scorecardFromJson(JSON.parse(readFileSync(local, 'utf8')))
    const response = await fetch(`${SCORECARD_API}/${owner}/${name}`)
    if (!response.ok) return null
    try {
        return scorecardFromJson(await response.json())
    } catch {
        return null
    }
}

// Cada eixo falha sozinho: erro vira null naquele eixo e a mensagem vai para stderr.
async function collect(repo, { scorecardDir, windowDays, now }) {
    const since = new Date(now.getTime() - windowDays * DAY_MS).toISOString().slice(0, 10)
    const metrics = { scorecard: null, releases: null, mergedPrs: null, medianMergeHours: null, revertRate: null }
    try {
        metrics.scorecard = (await loadScorecard(repo, scorecardDir))?.score ?? null
    } catch (error) {
        warn(repo, 'scorecard', error)
    }
    let prs = null
    try {
        const search = `merged:>=${since}`
        prs = JSON.parse(gh(['pr', 'list', '-R', repo, '--state', 'merged', '--search', search, '--limit', '1000', '--json', 'title,createdAt,mergedAt']))
    } catch (error) {
        warn(repo, 'PRs', error)
    }
    let releases = null
    try {
        releases = JSON.parse(gh(['release', 'list', '-R', repo, '--limit', '100', '--json', 'publishedAt,isDraft']))
    } catch (error) {
        warn(repo, 'releases', error)
    }
    const delivery = deliveryMetrics({ prs: prs ?? [], releases: releases ?? [], now, windowDays })
    if (releases !== null) metrics.releases = delivery.releases
    if (prs !== null) {
        metrics.mergedPrs = delivery.mergedPrs
        metrics.medianMergeHours = delivery.medianMergeHours
        metrics.revertRate = delivery.revertRate
    }
    return metrics
}

export async function main(argv = process.argv.slice(2)) {
    const options = parseArgs(argv)
    const { target, groups } = JSON.parse(readFileSync(options.peers, 'utf8'))
    const now = new Date()
    const data = new Map()
    for (const repo of [target, ...Object.values(groups).flat()]) {
        data.set(repo, await collect(repo, { ...options, now }))
    }
    const report = buildReport({ target, groups, data })
    const markdown = renderMarkdown(report)
    if (options.out) writeFileSync(options.out, `${JSON.stringify(report, null, 4)}\n`)
    if (options.md) writeFileSync(options.md, markdown)
    process.stdout.write(markdown)
}

if (import.meta.url === pathToFileURL(process.argv[1] ?? '').href) {
    main().catch((error) => {
        process.stderr.write(`${error.message}\n`)
        process.exitCode = 1
    })
}
