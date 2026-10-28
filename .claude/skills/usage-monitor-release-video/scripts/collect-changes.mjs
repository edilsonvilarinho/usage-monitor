#!/usr/bin/env node
// Coleta o que mudou entre duas versões estáveis, agrupado por issue, para o roteiro do vídeo.
// Só feat e fix entram: docs, testes, CI e as skills do repositório não são novidade para quem usa o app.
// Uso: node collect-changes.mjs [vX.Y.Z]   (sem argumento: a última estável)
import { execFileSync } from 'node:child_process'
import { pathToFileURL } from 'node:url'

const USER_FACING_TYPES = new Set(['feat', 'fix'])
const INTERNAL_SCOPES = new Set(['skills', 'ci'])
const CONVENTIONAL = /^(\w+)(?:\(([^)]+)\))?!?: (.+)$/

export function parseCommitLine(line) {
    const [sha, ...rest] = line.split('\t')
    const message = rest.join('\t').trim()
    const match = CONVENTIONAL.exec(message)
    if (!match) return { sha, type: '', scope: '', subject: message, issues: [] }
    const issues = [...match[3].matchAll(/\(#(\d+)\)/g)].map((found) => Number(found[1]))
    const subject = match[3].replace(/\s*\(#\d+\)/g, '').trim()
    return { sha, type: match[1], scope: match[2] ?? '', subject, issues }
}

export function groupChanges(commits) {
    const byIssue = new Map()
    const loose = []
    for (const commit of commits) {
        if (!USER_FACING_TYPES.has(commit.type) || INTERNAL_SCOPES.has(commit.scope)) continue
        if (commit.issues.length === 0) {
            loose.push(commit)
            continue
        }
        const issue = commit.issues[0]
        if (!byIssue.has(issue)) byIssue.set(issue, { issue, scopes: [], feats: [], fixes: [] })
        const group = byIssue.get(issue)
        if (commit.scope && !group.scopes.includes(commit.scope)) group.scopes.push(commit.scope)
        if (commit.type === 'feat') group.feats.push(commit)
        else group.fixes.push(commit)
    }
    const issues = [...byIssue.values()].sort((left, right) => left.issue - right.issue)
    return { issues, loose, counted: commits.length }
}

const STABLE = /^v(\d+)\.(\d+)\.(\d+)$/

function compareStable(left, right) {
    const a = STABLE.exec(left).slice(1).map(Number)
    const b = STABLE.exec(right).slice(1).map(Number)
    for (let index = 0; index < 3; index += 1) {
        if (a[index] !== b[index]) return a[index] - b[index]
    }
    return 0
}

export function releaseRange(tags, requested) {
    const stable = tags.filter((tag) => STABLE.test(tag)).sort(compareStable)
    const to = requested ?? stable[stable.length - 1]
    const position = stable.indexOf(to)
    if (position < 1) throw new Error(`Sem versão estável anterior a ${to}`)
    return { from: stable[position - 1], to }
}

function main() {
    const tags = execFileSync('git', ['tag', '--list', 'v*'], { encoding: 'utf8' }).split(/\r?\n/).filter(Boolean)
    const range = releaseRange(tags, process.argv[2])
    const log = execFileSync('git', ['log', '--no-merges', '--format=%h%x09%s', `${range.from}..${range.to}`], {
        encoding: 'utf8',
    })
    const commits = log.split(/\r?\n/).filter(Boolean).map(parseCommitLine)
    process.stdout.write(`${JSON.stringify({ ...range, ...groupChanges(commits) }, null, 2)}\n`)
}

if (import.meta.url === pathToFileURL(process.argv[1]).href) main()
