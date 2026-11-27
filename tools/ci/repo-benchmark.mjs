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
    ['--html', 'html'],
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

// ---- HTML (A03): pagina autocontida com os tokens de docs/design-system/tokens. ----

const escapeHtml = (value) =>
    String(value)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;')

const AXIS_LABEL = {
    scorecard: 'OpenSSF Scorecard',
    releases: 'Releases em 90 dias',
    medianMergeHours: 'Mediana até o merge',
    revertRate: 'Taxa de revert',
}
const AXIS_NOTE = {
    scorecard: 'OpenSSF Scorecard: nota de 0 a 10 de boas práticas de segurança; check inconclusivo não entra.',
    releases: 'Releases publicadas na janela, sem rascunhos.',
    medianMergeHours: 'Mediana, em horas, de criação até merge dos PRs mesclados na janela.',
    revertRate: 'Parcela dos PRs mesclados na janela cujo título começa com revert.',
}
const FORMAT = {
    scorecard: (value) => decimal(value, 1),
    releases: (value) => String(value),
    mergedPrs: (value) => String(value),
    medianMergeHours: (value) => decimal(value, 1),
    revertRate: (value) => `${decimal(value * 100, 1)}%`,
}
const TABLE_HEAD = ['Repositório', 'Grupo', 'Scorecard', 'Releases 90d', 'PRs mesclados 90d', 'Mediana merge (h)', 'Taxa de revert']
const GROUP_PILL = {
    target: ['info', 'alvo'],
    'same-goal': ['off', 'mesmo objetivo'],
    'large-stack': ['off', 'grande, mesma stack'],
}
const LEVEL_WORD = { ok: 'alto', warn: 'médio', crit: 'baixo' }

const levelOf = (percentile) => {
    if (percentile === null) return 'off'
    if (percentile >= 67) return 'ok'
    if (percentile >= 34) return 'warn'
    return 'crit'
}

const pill = (tone, text) =>
    `<span class="pill pill-${tone}"><span class="dot" aria-hidden="true"></span>${escapeHtml(text)}</span>`

function renderCard(axis, position, targetRow) {
    const level = levelOf(position.percentile)
    const raw = targetRow[axis.key]
    const value = isMissing(raw) ? '—' : `${FORMAT[axis.key](raw)}${axis.key === 'medianMergeHours' ? ' h' : ''}`
    const meta = position.position === null ? '—' : `${position.position}º de ${position.of}`
    const status = level === 'off' ? 'não medido' : `${LEVEL_WORD[level]} · percentil ${position.percentile}`
    return `<section class="card" data-axis="${axis.key}" data-level="${level}">
<p class="eyebrow">${escapeHtml(AXIS_LABEL[axis.key])}</p>
<p class="value">${value}</p>
<p class="meta">${meta}</p>
${pill(level, status)}
</section>`
}

function renderBars(axis, rows, target) {
    const valued = rows.filter((row) => !isMissing(row[axis.key]))
    const missing = rows.length - valued.length
    const max = Math.max(0, ...valued.map((row) => row[axis.key]))
    const bars = valued
        .map((row) => {
            const width = max === 0 ? 0 : (row[axis.key] / max) * 100
            const tone = row.repo === target ? 'bar target' : 'bar'
            return `<div class="bar-row"><span class="bar-name">${escapeHtml(row.repo)}</span><div class="track"><div class="${tone}" style="width:${width.toFixed(1)}%"></div></div><span class="bar-value">${FORMAT[axis.key](row[axis.key])}</span></div>`
        })
        .join('\n')
    const count = missing === 1 ? '1 não medido' : `${missing} não medidos`
    return `<div class="bars" data-axis="${axis.key}">
<p class="eyebrow">${escapeHtml(AXIS_LABEL[axis.key])} · ${count}</p>
${bars}
</div>`
}

function renderTableRows(report) {
    return report.rows
        .map((row) => {
            const isTarget = row.repo === report.target
            const [tone, text] = GROUP_PILL[row.group] ?? ['off', row.group]
            const numbers = [
                ['scorecard', row.scorecard],
                ['releases', row.releases],
                ['mergedPrs', row.mergedPrs],
                ['medianMergeHours', row.medianMergeHours],
                ['revertRate', row.revertRate],
            ].map(([key, value]) => `<td class="num">${isMissing(value) ? '—' : FORMAT[key](value)}</td>`)
            return `<tr${isTarget ? ' class="target"' : ''}><td>${escapeHtml(row.repo)}</td><td>${pill(tone, text)}</td>${numbers.join('')}</tr>`
        })
        .join('\n')
}

const CSS = `@import url("https://fonts.googleapis.com/css2?family=IBM+Plex+Mono:wght@400;500;600&family=IBM+Plex+Sans:wght@400;500;600&display=swap");
:root {
  --bg: #131010; --surface: #1B1818; --raised: #211E1E; --border: #3D3838;
  --fg: #F2EDED; --muted: #B8B2B2;
  --ok: #4CAF50; --warn: #FFA726; --crit: #E86A6A; --info: #4C8DFF;
  --mono: "IBM Plex Mono", ui-monospace, "Cascadia Mono", Consolas, monospace;
  --sans: "IBM Plex Sans", ui-sans-serif, "Segoe UI", system-ui, sans-serif;
  --t10: 10px; --t12: 12px; --t14: 14px; --t20: 20px; --t28: 28px;
  --s1: 4px; --s2: 8px; --s3: 12px; --s4: 16px; --s6: 24px;
  --r1: 4px; --r3: 8px;
}
@media (prefers-color-scheme: light) {
  :root {
    --bg: #F6F3F3; --surface: #FFFCFC; --raised: #EFEAEA; --border: #D7D0D0;
    --fg: #171414; --muted: #686060;
    --ok: #2E7D32; --warn: #8A5000; --crit: #B3261E; --info: #1565C0;
  }
}
* { box-sizing: border-box; }
body { margin: 0; padding: var(--s6) var(--s4); background: var(--bg); color: var(--fg); font-family: var(--sans); font-size: var(--t14); line-height: 1.55; }
main { max-width: 960px; margin: 0 auto; }
.eyebrow { margin: 0; font-family: var(--mono); font-size: var(--t10); font-weight: 500; text-transform: uppercase; letter-spacing: .07em; color: var(--muted); }
h1 { margin: var(--s1) 0 var(--s2); font-family: var(--mono); font-size: var(--t28); font-weight: 600; letter-spacing: -.01em; line-height: 1.2; }
.stamp { margin: 0 0 var(--s6); font-family: var(--mono); font-size: var(--t12); color: var(--muted); }
.cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(200px, 100%), 1fr)); gap: var(--s3); margin-bottom: var(--s6); }
.card { display: flex; flex-direction: column; gap: var(--s1); padding: var(--s3); background: var(--surface); border: 1px solid var(--border); border-radius: var(--r3); }
.card .value { margin: 0; font-family: var(--mono); font-size: var(--t20); font-weight: 600; font-variant-numeric: tabular-nums; }
.card .meta { margin: 0; font-family: var(--mono); font-size: var(--t12); color: var(--muted); }
.pill { display: inline-flex; align-items: center; gap: var(--s1); align-self: flex-start; padding: 2px 6px; border-radius: var(--r1); font-family: var(--mono); font-size: var(--t10); letter-spacing: .06em; white-space: nowrap; }
.dot { width: 6px; height: 6px; border-radius: 50%; background: currentColor; }
.pill-ok { color: var(--ok); background: color-mix(in srgb, var(--ok) 8%, transparent); }
.pill-warn { color: var(--warn); background: color-mix(in srgb, var(--warn) 8%, transparent); }
.pill-crit { color: var(--crit); background: color-mix(in srgb, var(--crit) 8%, transparent); }
.pill-info { color: var(--info); background: color-mix(in srgb, var(--info) 8%, transparent); }
.pill-off { color: var(--muted); background: color-mix(in srgb, var(--muted) 8%, transparent); }
.panel { margin-bottom: var(--s6); padding: var(--s3); background: var(--surface); border: 1px solid var(--border); border-radius: var(--r3); }
.bars { margin-bottom: var(--s3); }
.bar-row { display: grid; grid-template-columns: minmax(140px, 220px) 1fr auto; gap: var(--s2); align-items: center; margin: var(--s1) 0; font-family: var(--mono); font-size: var(--t12); }
.bar-name { min-width: 0; overflow-wrap: anywhere; }
.bar-value { font-variant-numeric: tabular-nums; }
.track { min-width: 0; height: 4px; background: var(--raised); border-radius: var(--r1); }
.bar { height: 4px; background: var(--muted); border-radius: var(--r1); }
.bar.target { background: var(--info); }
.scroll { overflow-x: auto; }
@media (max-width: 600px) {
  .cards { grid-template-columns: 1fr; }
  .bar-row { grid-template-columns: minmax(0, 1fr) auto; }
  .bar-name { grid-column: 1 / -1; }
  .track { grid-column: 1; }
}
table { width: 100%; border-collapse: collapse; font-family: var(--mono); font-size: var(--t12); }
th { padding: var(--s2) var(--s3); font-weight: 500; font-size: var(--t10); letter-spacing: .07em; text-transform: uppercase; text-align: left; color: var(--muted); border-bottom: 1px solid var(--border); vertical-align: bottom; }
td { padding: 7px var(--s3); border-bottom: 1px solid var(--border); overflow-wrap: anywhere; }
td.num { white-space: nowrap; }
tr:last-child td { border-bottom: none; }
th.num, td.num { text-align: right; font-variant-numeric: tabular-nums; }
tr.target td { background: var(--raised); }
tbody tr:hover td { background: var(--raised); }
footer { margin-top: var(--s6); font-size: var(--t12); color: var(--muted); }
footer p { margin: var(--s1) 0; }`

export function renderHtml(report, { generatedAt = new Date().toISOString(), windowDays = 90 } = {}) {
    const target = report.rows.find((row) => row.repo === report.target) ?? {}
    const cards = AXES.map((axis) => renderCard(axis, report.targetPositions[axis.key], target)).join('\n')
    const bars = AXES.map((axis) => renderBars(axis, report.rows, report.target)).join('\n')
    const head = TABLE_HEAD.map((label, index) => `<th${index >= 2 ? ' class="num"' : ''}>${escapeHtml(label)}</th>`).join('')
    const notes = AXES.map((axis) => `<p>${escapeHtml(AXIS_NOTE[axis.key])}</p>`).join('\n')
    return `<!doctype html>
<html lang="pt-BR">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Posição no grupo · usage-monitor</title>
<style>
${CSS}
</style>
</head>
<body>
<main>
<header>
<p class="eyebrow">usage-monitor · benchmark de repositório</p>
<h1>Posição no grupo</h1>
<p class="stamp">gerado em ${escapeHtml(generatedAt)} · janela de ${escapeHtml(windowDays)} dias</p>
</header>
<div class="cards">
${cards}
</div>
<section class="panel">
${bars}
</section>
<section class="panel">
<div class="scroll">
<table>
<thead><tr>${head}</tr></thead>
<tbody>
${renderTableRows(report)}
</tbody>
</table>
</div>
</section>
<footer>
${notes}
<p>null = não medido, nunca zero.</p>
<p>Fontes: OpenSSF Scorecard API e GitHub.</p>
</footer>
</main>
</body>
</html>
`
}

// ---- Casca: gh e API do Scorecard. Comandos externos por execFileSync, nunca shell. ----

export function parseArgs(argv) {
    const options = { peers: PEERS_FILE, scorecardDir: null, windowDays: 90, out: null, md: null, html: null }
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
    if (options.html) {
        writeFileSync(options.html, renderHtml(report, { generatedAt: now.toISOString(), windowDays: options.windowDays }))
    }
    process.stdout.write(markdown)
}

if (import.meta.url === pathToFileURL(process.argv[1] ?? '').href) {
    main().catch((error) => {
        process.stderr.write(`${error.message}\n`)
        process.exitCode = 1
    })
}
