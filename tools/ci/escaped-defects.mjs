// Defeitos que escapam de cada PR (#645, A02): parte pura, sem I/O. Levanta candidatos
// (correcao com PR de produto de origem ate 14 dias antes); o veredito e de gente ou agente.
// A casca (gh, git blame) e do A03; este modulo nao roda sozinho.

import { execFileSync } from 'node:child_process'
import { existsSync, readFileSync } from 'node:fs'
import { fileURLToPath, pathToFileURL } from 'node:url'

const DAY_MS = 86_400_000
const VERDICTS_FILE = fileURLToPath(new URL('./escaped-defects-verdicts.json', import.meta.url))
const PR_LIMIT = 500
const PR_FIELDS = 'number,title,body,mergedAt,closingIssuesReferences,mergeCommit'
export const AREAS = ['hud-window', 'screen', 'data', 'server', 'other']
// Issue citada por quase todo PR: citar so ela nao vincula a origem (#645).
const UMBRELLA_ISSUE = 342

const CORRECTION_TITLE = /^(fix|revert)(\([^()]*\))?!?:/
const PRODUCT_PREFIXES = ['src/commonMain/', 'src/desktopMain/', 'src/installer/', 'server/src/']
const PRODUCT_FILES = new Set(['build.gradle.kts'])
const TEST_FILE = /\.(test|spec)\.[cm]?[jt]sx?$/
const HUD_FILE_NAMES = [/^Hud.*\.kt$/, /Window.*\.kt$/, /^Desktop.*Frame\.kt$/]

export function isCorrectionTitle(title) {
    return CORRECTION_TITLE.test(title)
}

export function isProductPath(path) {
    if (TEST_FILE.test(path)) return false
    return PRODUCT_FILES.has(path) || PRODUCT_PREFIXES.some((prefix) => path.startsWith(prefix))
}

export function areaOf(path) {
    const name = path.slice(path.lastIndexOf('/') + 1)
    if (path.startsWith('src/desktopMain/')) {
        if (HUD_FILE_NAMES.some((pattern) => pattern.test(name))) return 'hud-window'
        return path.includes('/presentation/') ? 'screen' : 'data'
    }
    if (path.startsWith('src/commonMain/')) {
        if (path.includes('/presentation/')) return 'screen'
        if (/\/(domain|data)\//.test(path)) return 'data'
        return 'other'
    }
    if (path.startsWith('server/src/')) return 'server'
    return 'other'
}

// Empate vai para a area que vem antes em AREAS.
export function primaryArea(files) {
    const linesByArea = new Map()
    for (const file of files) {
        if (!isProductPath(file.path)) continue
        const area = areaOf(file.path)
        linesByArea.set(area, (linesByArea.get(area) ?? 0) + file.added + file.removed)
    }
    let best = 'other'
    let bestLines = 0
    for (const area of AREAS) {
        const lines = linesByArea.get(area) ?? 0
        if (lines > bestLines) {
            best = area
            bestLines = lines
        }
    }
    return best
}

const isProductPr = (pr) => pr.files.some((file) => isProductPath(file.path))
const isCorrection = (pr) => isCorrectionTitle(pr.title) && isProductPr(pr)
const byNumber = (left, right) => left.number - right.number

// Numeros citados no corpo da correcao e no das issues que ela fecha, sem as issues dela e a #342.
function citedNumbers(fix, issueBodies) {
    const texts = [fix.body ?? '', ...fix.closes.map((issue) => issueBodies[issue] ?? '')]
    const numbers = new Set()
    for (const text of texts) {
        for (const match of text.matchAll(/#(\d+)/g)) numbers.add(Number(match[1]))
    }
    for (const issue of [...fix.closes, fix.number, UMBRELLA_ISSUE]) numbers.delete(issue)
    return numbers
}

export function candidatesOf({ prs, issueBodies = {}, blamedCommits = {}, windowDays = 14 }) {
    const windowMs = windowDays * DAY_MS
    const products = prs.filter(isProductPr).sort(byNumber)
    const ownerOfCommit = new Map()
    for (const pr of products) {
        for (const sha of pr.commits) ownerOfCommit.set(sha, pr.number)
    }

    const candidates = []
    for (const fix of prs.filter(isCorrection).sort(byNumber)) {
        const cited = citedNumbers(fix, issueBodies)
        const blamed = new Set((blamedCommits[fix.number] ?? []).map((sha) => ownerOfCommit.get(sha)))
        const origins = []
        for (const origin of products) {
            const age = Date.parse(fix.mergedAt) - Date.parse(origin.mergedAt)
            if (origin.number === fix.number || age < 0 || age > windowMs) continue
            const via = []
            if (cited.has(origin.number) || origin.closes.some((issue) => cited.has(issue))) via.push('reference')
            if (blamed.has(origin.number)) via.push('blame')
            if (via.length) origins.push({ pr: origin.number, via })
        }
        if (origins.length) candidates.push({ fix: fix.number, area: primaryArea(fix.files), origins })
    }
    return candidates
}

// Verdict: { fix, escaped, origin, reason }. Candidato sem verdict fica pendente; escaped sem candidatura conta em missedByScript.
// Taxa por area: origem confirmada conta na area principal do PR de origem. Teto: confirmadas mais a primeira origem de cada pendente.
export function summarize({ prs, candidates, verdicts = [], known = prs }) {
    const productPrs = prs.filter(isProductPr)
    const prByNumber = new Map(known.map((pr) => [pr.number, pr]))
    for (const verdict of verdicts) checkVerdict(verdict, prByNumber)
    const verdictByFix = new Map(verdicts.map((verdict) => [verdict.fix, verdict]))
    const candidateFixes = new Set(candidates.map((candidate) => candidate.fix))
    const confirmed = new Set()
    const ceilingOrigins = new Set()
    let pending = 0
    let rejected = 0
    let missedByScript = 0
    for (const candidate of candidates) {
        const verdict = verdictByFix.get(candidate.fix)
        if (!verdict) {
            pending++
            ceilingOrigins.add(candidate.origins[0].pr)
        } else if (!verdict.escaped) rejected++
        else confirmed.add(verdict.origin)
    }
    for (const verdict of verdicts) {
        if (!verdict.escaped || candidateFixes.has(verdict.fix)) continue
        missedByScript++
        confirmed.add(verdict.origin)
    }
    for (const origin of confirmed) ceilingOrigins.add(origin)
    const confirmedByArea = new Map(AREAS.map((area) => [area, new Set()]))
    for (const origin of confirmed) confirmedByArea.get(primaryArea(prByNumber.get(origin).files)).add(origin)

    const byArea = {}
    for (const area of AREAS) {
        const inArea = productPrs.filter((pr) => primaryArea(pr.files) === area).length
        const confirmedOrigins = confirmedByArea.get(area).size
        byArea[area] = { productPrs: inArea, confirmedOrigins, rate: inArea ? confirmedOrigins / inArea : null }
    }
    const total = productPrs.length
    return {
        productPrs: total,
        confirmedOrigins: confirmed.size,
        pending,
        rejected,
        missedByScript,
        rate: total ? confirmed.size / total : null,
        ceiling: total ? ceilingOrigins.size / total : null,
        byArea,
    }
}

function checkVerdict(verdict, prByNumber) {
    const fix = prByNumber.get(verdict.fix)
    if (!fix || !isCorrection(fix)) throw new Error(`veredito invalido: #${verdict.fix} nao e correcao de produto`)
    const origin = prByNumber.get(verdict.origin)
    if (!origin || !isProductPr(origin)) throw new Error(`veredito invalido: origem #${verdict.origin} nao e PR de produto`)
}

export function parseArgs(argv) {
    const since = argv[argv.indexOf('--since') + 1]
    if (argv.indexOf('--since') < 0 || !since) throw new Error('uso: --since AAAA-MM-DD [--json]')
    if (!/^\d{4}-\d{2}-\d{2}$/.test(since)) throw new Error(`data invalida: ${since}; use AAAA-MM-DD`)
    return { since, json: argv.includes('--json') }
}

// Linhas removidas (-U0, --no-renames) de cada arquivo de produto, pelo caminho antigo.
export function parseRemovedRanges(diff) {
    const ranges = []
    let path = null
    for (const line of diff.split('\n')) {
        const header = /^diff --git a\/(.*) b\/.*$/.exec(line)
        if (header) path = header[1]
        const hunk = /^@@ -(\d+)(?:,(\d+))? /.exec(line)
        if (!hunk || !isProductPath(path)) continue
        const from = Number(hunk[1])
        const count = hunk[2] === undefined ? 1 : Number(hunk[2])
        if (count > 0) ranges.push({ path, from, to: from + count - 1 })
    }
    return ranges
}

// Cada linha do blame --porcelain comeca com o sha do commit que a introduziu.
export function parseBlame(porcelain) {
    const shas = []
    for (const line of porcelain.split('\n')) {
        const match = /^([0-9a-f]{40}) /.exec(line)
        if (match && !shas.includes(match[1])) shas.push(match[1])
    }
    return shas
}

// Uma linha por correcao: #fix area "titulo" <- todas as origens com via, e status.
export function buildRows({ corrections, candidates, verdicts = [] }) {
    const candidateByFix = new Map(candidates.map((candidate) => [candidate.fix, candidate]))
    const verdictByFix = new Map(verdicts.map((verdict) => [verdict.fix, verdict]))
    return corrections.map((fix) => {
        const candidate = candidateByFix.get(fix.number)
        const verdict = verdictByFix.get(fix.number)
        const origins = (candidate?.origins ?? []).map((origin) => ({ pr: origin.pr, via: [...origin.via] }))
        if (verdict && !origins.some((origin) => origin.pr === verdict.origin)) origins.push({ pr: verdict.origin, via: [] })
        const status = verdict ? (verdict.escaped ? 'confirmado' : 'recusado') : candidate ? 'pendente' : null
        return { fix: fix.number, title: fix.title, area: primaryArea(fix.files), status, origins }
    })
}

const pct = (rate) => (rate === null ? 'n/a' : `${(rate * 100).toFixed(1)}%`)
const originText = (origin) => (origin.via.length ? `#${origin.pr}(${origin.via.join('+')})` : `#${origin.pr}`)

export function renderReport({ since, summary, rows }) {
    const lines = [
        `defeitos que escapam desde ${since} (janela de 14 dias)`,
        `PRs de produto: ${summary.productPrs}`,
        `origens confirmadas: ${summary.confirmedOrigins}`,
        `taxa confirmada: ${pct(summary.rate)}`,
    ]
    if (summary.pending > 0) lines.push(`teto se todos os pendentes forem defeito: ${pct(summary.ceiling)}`)
    lines.push(
        `pendentes: ${summary.pending}`,
        `recusados: ${summary.rejected}`,
        `missedByScript: ${summary.missedByScript}`,
        'por area:',
    )
    for (const area of AREAS) {
        const { productPrs, confirmedOrigins, rate } = summary.byArea[area]
        lines.push(`  ${area}: produto ${productPrs}, origens ${confirmedOrigins}, taxa ${pct(rate)}`)
    }
    lines.push('correcoes:')
    for (const row of rows) {
        const head = `#${row.fix} ${row.area} "${row.title}" <- `
        if (!row.origins.length) lines.push(`${head}sem origem`)
        else lines.push(`${head}${row.origins.map(originText).join(', ')}${row.status ? ` [${row.status}]` : ''}`)
    }
    return lines.join('\n')
}

export function renderJson({ since, summary, rows }) {
    return JSON.stringify({ since, summary, rows }, null, 2)
}

// Casca: gh e git com argumentos em lista, sem shell.
const run = (command, args) => execFileSync(command, args, { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 })

function parseNumstat(text) {
    return text.split('\n').filter(Boolean).map((line) => {
        const [added, removed, ...rest] = line.split('\t')
        return { path: rest.join('\t'), added: Number(added) || 0, removed: Number(removed) || 0 }
    })
}

function fetchPrs(since) {
    const from = new Date(Date.parse(since) - 14 * DAY_MS).toISOString().slice(0, 10)
    const list = JSON.parse(run('gh', ['pr', 'list', '--state', 'merged', '--limit', String(PR_LIMIT), '--search', `merged:>=${from}`, '--json', PR_FIELDS]))
    if (list.length >= PR_LIMIT) throw new Error(`gh pr list atingiu o limite de ${PR_LIMIT} PRs; contagem incompleta`)
    return list.map((pr) => {
        const oid = pr.mergeCommit.oid
        return {
            number: pr.number,
            title: pr.title,
            body: pr.body ?? '',
            mergedAt: pr.mergedAt,
            closes: pr.closingIssuesReferences.map((issue) => issue.number),
            commits: run('git', ['rev-list', `${oid}^1..${oid}`]).split('\n').filter(Boolean),
            files: parseNumstat(run('git', ['diff', '--numstat', '--no-renames', `${oid}^1`, oid])),
            mergeOid: oid,
        }
    })
}

function issueBodiesOf(fixes) {
    const bodies = {}
    for (const fix of fixes) {
        for (const issue of fix.closes) {
            if (issue in bodies) continue
            bodies[issue] = JSON.parse(run('gh', ['issue', 'view', String(issue), '--json', 'body'])).body ?? ''
        }
    }
    return bodies
}

function blamedCommitsOf(fix) {
    const parent = `${fix.mergeOid}^1`
    const shas = new Set()
    for (const range of parseRemovedRanges(run('git', ['diff', '-U0', '--no-renames', parent, fix.mergeOid]))) {
        const porcelain = run('git', ['blame', '--porcelain', '-L', `${range.from},${range.to}`, parent, '--', range.path])
        for (const sha of parseBlame(porcelain)) shas.add(sha)
    }
    return [...shas]
}

export function main(argv = process.argv.slice(2)) {
    const { since, json } = parseArgs(argv)
    const prs = fetchPrs(since)
    const periodPrs = prs.filter((pr) => Date.parse(pr.mergedAt) >= Date.parse(since)).sort(byNumber)
    const periodNumbers = new Set(periodPrs.map((pr) => pr.number))
    const corrections = periodPrs.filter(isCorrection)
    const issueBodies = issueBodiesOf(corrections)
    const blamedCommits = {}
    for (const fix of corrections) blamedCommits[fix.number] = blamedCommitsOf(fix)
    const verdicts = existsSync(VERDICTS_FILE) ? JSON.parse(readFileSync(VERDICTS_FILE, 'utf8')) : []
    const candidates = candidatesOf({ prs, issueBodies, blamedCommits }).filter((candidate) => periodNumbers.has(candidate.fix))
    const summary = summarize({ prs: periodPrs, known: prs, candidates, verdicts })
    const rows = buildRows({ corrections, candidates, verdicts })
    console.log(json ? renderJson({ since, summary, rows }) : renderReport({ since, summary, rows }))
}

if (import.meta.url === pathToFileURL(process.argv[1] ?? '').href) {
    try {
        main()
    } catch (error) {
        console.error(error.message)
        process.exitCode = 2
    }
}
