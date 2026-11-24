import { parseArgs, parseRemovedRanges, parseBlame, renderReport } from '../escaped-defects.mjs'
import { test } from 'node:test'
import assert from 'node:assert/strict'
import { areaOf, candidatesOf, isCorrectionTitle, isProductPath, primaryArea, summarize } from '../escaped-defects.mjs'

const DAY = 86_400_000
const at = (day) => new Date(Date.parse('2026-11-01T00:00:00Z') + day * DAY).toISOString()
const SCREEN = 'src/commonMain/kotlin/com/usagemonitor/presentation/DashboardViewModel.kt'
const HUD = 'src/desktopMain/kotlin/com/usagemonitor/HudWindow.kt'

const pr = (number, { title = 'feat: x', body = '', day = 0, closes = [], commits = [], files = [{ path: SCREEN, added: 1, removed: 0 }] } = {}) =>
    ({ number, title, body, mergedAt: at(day), closes, commits, files })

test('titulo de correcao: fix ou revert, com escopo opcional', () => {
    for (const title of ['fix: x', 'fix(hud): x', 'fix!: x', 'revert: x', 'revert(cursor): x']) {
        assert.equal(isCorrectionTitle(title), true, title)
    }
    for (const title of ['feat: fix x', 'fix x', 'docs(ci): x', 'refactor: x']) {
        assert.equal(isCorrectionTitle(title), false, title)
    }
})

test('codigo de produto: fontes, instalador, servidor e build raiz', () => {
    for (const path of ['src/commonMain/kotlin/A.kt', 'src/desktopMain/kotlin/A.kt', 'src/installer/setup.nsi', 'server/src/index.ts', 'build.gradle.kts']) {
        assert.equal(isProductPath(path), true, path)
    }
    for (const path of ['docs/planos/x.md', 'tools/ci/x.mjs', 'src/commonTest/kotlin/A.kt', 'src/desktopTest/kotlin/A.kt', 'server/src/index.test.ts']) {
        assert.equal(isProductPath(path), false, path)
    }
})

test('area pelo caminho: hud-window, screen, data, server, other', () => {
    const cases = {
        'src/desktopMain/kotlin/com/usagemonitor/HudWindow.kt': 'hud-window',
        'src/desktopMain/kotlin/com/usagemonitor/DesktopMainFrame.kt': 'hud-window',
        'src/desktopMain/kotlin/com/usagemonitor/AutoStartManager.kt': 'data',
        [SCREEN]: 'screen',
        'src/commonMain/kotlin/com/usagemonitor/domain/Usage.kt': 'data',
        'src/commonMain/kotlin/com/usagemonitor/data/Repo.kt': 'data',
        'server/src/index.ts': 'server',
        'src/commonMain/kotlin/com/usagemonitor/ui/Chart.kt': 'other',
        'build.gradle.kts': 'other',
    }
    for (const [path, area] of Object.entries(cases)) assert.equal(areaOf(path), area, path)
})

test('area da correcao: a de mais linhas de codigo de produto', () => {
    assert.equal(primaryArea([
        { path: SCREEN, added: 3, removed: 1 },
        { path: HUD, added: 10, removed: 2 },
    ]), 'hud-window')
    assert.equal(primaryArea([
        { path: HUD, added: 1, removed: 0 },
        { path: 'docs/planos/x.md', added: 500, removed: 0 },
        { path: 'src/desktopTest/kotlin/A.kt', added: 300, removed: 0 },
    ]), 'hud-window')
    assert.equal(primaryArea([{ path: 'docs/x.md', added: 5, removed: 0 }]), 'other')
})

test('area da correcao: empate vai para a area de ordem anterior', () => {
    assert.equal(primaryArea([
        { path: 'server/src/a.ts', added: 4, removed: 0 },
        { path: SCREEN, added: 2, removed: 2 },
    ]), 'screen')
})

test('correcao que cita o PR de origem dentro de 14 dias acha a origem', () => {
    const prs = [
        pr(628, { title: 'feat(hud): mostrar flutuante', day: 8, commits: ['c628'] }),
        pr(630, { title: 'revert: desfaz #628', body: 'Reverte o #628.', day: 10, files: [{ path: HUD, added: 4, removed: 6 }] }),
    ]
    assert.deepEqual(candidatesOf({ prs, issueBodies: {}, blamedCommits: {} }), [
        { fix: 630, area: 'hud-window', origins: [{ pr: 628, via: ['reference'] }] },
    ])
})

test('PR de feature com commits de revert internos nao vira candidato', () => {
    const prs = [
        pr(619, { title: 'feat(hud): base', day: 0, commits: ['c619'] }),
        pr(622, { title: 'feat(hud): janela flutuante', body: 'Parte de #619.', day: 2, commits: ['c622', 'r1', 'r2'], files: [{ path: HUD, added: 80, removed: 3 }] }),
    ]
    assert.deepEqual(candidatesOf({ prs, issueBodies: {}, blamedCommits: {} }), [])
})

test('correcao sem citacao acha a origem so pelo blame da linha removida', () => {
    const prs = [
        pr(622, { title: 'feat(hud): janela flutuante', day: 3, commits: ['a622'], files: [{ path: HUD, added: 80, removed: 3 }] }),
        pr(624, { title: 'fix(hud): modo flutuante', body: 'Corrige o modo flutuante.', day: 5, commits: ['a624'], files: [{ path: HUD, added: 4, removed: 2 }] }),
    ]
    assert.deepEqual(candidatesOf({ prs, issueBodies: {}, blamedCommits: { 624: ['a622'] } }), [
        { fix: 624, area: 'hud-window', origins: [{ pr: 622, via: ['blame'] }] },
    ])
})

test('a propria issue da correcao e a #342 sozinhas nao fazem origem', () => {
    const prs = [
        pr(660, { title: 'feat: x', day: 1, closes: [650], commits: ['a660'] }),
        pr(661, { title: 'feat: y', day: 2, closes: [342], commits: ['a661'] }),
        pr(700, { title: 'fix: z', body: 'Fecha #650. Parte de #342.', day: 4, closes: [650], commits: ['a700'] }),
    ]
    assert.deepEqual(candidatesOf({ prs, issueBodies: { 650: 'Bug. Ver #342.' }, blamedCommits: {} }), [])
})

test('janela de 14 dias: origem com 15 dias fica de fora, com 14 entra', () => {
    const prs = [
        pr(501, { title: 'feat: a', day: 5 }),
        pr(502, { title: 'feat: b', day: 6 }),
        pr(520, { title: 'fix: c', body: 'Reverte #501 e #502.', day: 20 }),
    ]
    assert.deepEqual(candidatesOf({ prs, issueBodies: {}, blamedCommits: {} }), [
        { fix: 520, area: 'screen', origins: [{ pr: 502, via: ['reference'] }] },
    ])
})

test('fix so de documentacao ou CI nao e correcao', () => {
    const prs = [
        pr(628, { title: 'feat: x', day: 8, commits: ['a628'] }),
        pr(631, {
            title: 'fix(ci): ajuste de docs', body: 'Corrige o #628.', day: 10,
            files: [{ path: 'docs/planos/x.md', added: 9, removed: 1 }, { path: 'tools/ci/x.mjs', added: 4, removed: 0 }],
        }),
    ]
    assert.deepEqual(candidatesOf({ prs, issueBodies: {}, blamedCommits: { 631: ['a628'] } }), [])
})

test('ordem deterministica: por numero do PR, independe da ordem de entrada', () => {
    const prs = [
        pr(640, { title: 'fix: a', body: 'Reverte #626.', day: 12 }),
        pr(628, { day: 8 }),
        pr(630, { title: 'fix: b', body: 'Reverte #628 e #626.', day: 10 }),
        pr(626, { day: 7 }),
    ]
    assert.deepEqual(candidatesOf({ prs, issueBodies: {}, blamedCommits: {} }), [
        { fix: 630, area: 'screen', origins: [{ pr: 626, via: ['reference'] }, { pr: 628, via: ['reference'] }] },
        { fix: 640, area: 'screen', origins: [{ pr: 626, via: ['reference'] }] },
    ])
})

test('taxa: origens confirmadas distintas sobre PRs de produto; pendente e recusado fora da taxa', () => {
    const prs = [
        pr(626, { day: 7 }),
        pr(628, { day: 8 }),
        pr(630, { title: 'revert: x', body: 'Reverte #628.', day: 10 }),
        pr(631, { title: 'fix: y', body: 'Corrige #628.', day: 11 }),
        pr(632, { title: 'fix: z', body: 'Corrige #626.', day: 12 }),
        pr(634, { title: 'fix: w', body: 'Corrige #626.', day: 13 }),
        pr(633, { title: 'docs: v', day: 13, files: [{ path: 'docs/x.md', added: 2, removed: 0 }] }),
    ]
    const candidates = candidatesOf({ prs, issueBodies: {}, blamedCommits: {} })
    const verdicts = [
        { fix: 630, escaped: true, origin: 628, reason: 'revert do #628' },
        { fix: 631, escaped: true, origin: 628, reason: 'correcao do mesmo #628' },
        { fix: 634, escaped: false, origin: 626, reason: 'mudanca planejada' },
    ]
    const summary = summarize({ prs, candidates, verdicts })
    assert.equal(summary.productPrs, 6)
    assert.equal(summary.confirmedOrigins, 1)
    assert.equal(summary.pending, 1)
    assert.equal(summary.rejected, 1)
    assert.equal(summary.rate, 1 / 6)
    assert.equal(summary.byArea.screen.confirmedOrigins, 1)
    assert.equal(summary.byArea.other.rate, null)
})

test('area: presentation em desktopMain fora dos padroes de HUD vira screen', () => {
    assert.equal(areaOf('src/desktopMain/kotlin/com/usagemonitor/presentation/TrayMenu.kt'), 'screen')
    assert.equal(areaOf('src/desktopMain/kotlin/com/usagemonitor/presentation/HudWindow.kt'), 'hud-window')
    assert.equal(areaOf('src/desktopMain/kotlin/com/usagemonitor/AutoStartManager.kt'), 'data')
})

test('taxa por area: origem confirmada conta na area principal do PR de origem', () => {
    const prs = [
        pr(628, { title: 'feat(hud): flutuante', day: 8, files: [{ path: HUD, added: 80, removed: 0 }] }),
        pr(630, { title: 'fix: x', body: 'Corrige #628.', day: 10 }),
    ]
    const summary = summarize({
        prs,
        candidates: candidatesOf({ prs, issueBodies: {}, blamedCommits: {} }),
        verdicts: [{ fix: 630, escaped: true, origin: 628, reason: 'x' }],
    })
    assert.equal(summary.byArea['hud-window'].confirmedOrigins, 1)
    assert.equal(summary.byArea['hud-window'].rate, 1)
    assert.equal(summary.byArea.screen.confirmedOrigins, 0)
    assert.equal(summary.byArea.screen.productPrs, 1)
})

test('summarize recusa verdict com origem que nao e produto ou fix que nao e correcao', () => {
    const prs = [
        pr(628, { title: 'feat: x', day: 8 }),
        pr(630, { title: 'fix: y', body: 'Corrige #628.', day: 10 }),
        pr(640, { title: 'feat: z', day: 11 }),
        pr(641, { title: 'docs: d', files: [{ path: 'docs/x.md', added: 1, removed: 0 }] }),
    ]
    const candidates = candidatesOf({ prs, issueBodies: {}, blamedCommits: {} })
    assert.throws(() => summarize({ prs, candidates, verdicts: [{ fix: 630, escaped: true, origin: 999, reason: 'x' }] }), /999/)
    assert.throws(() => summarize({ prs, candidates, verdicts: [{ fix: 630, escaped: true, origin: 641, reason: 'x' }] }), /641/)
    assert.throws(() => summarize({ prs, candidates, verdicts: [{ fix: 640, escaped: true, origin: 628, reason: 'x' }] }), /640/)
})

test('verdict escaped de correcao sem candidatura entra na taxa e em missedByScript; recusado sem candidatura nao conta', () => {
    const prs = [
        pr(628, { title: 'feat: x', day: 8 }),
        pr(635, { title: 'fix: q', body: 'Sem vinculo.', day: 12 }),
        pr(636, { title: 'fix: r', body: 'Sem vinculo.', day: 13 }),
    ]
    const summary = summarize({
        prs,
        candidates: [],
        verdicts: [
            { fix: 635, escaped: true, origin: 628, reason: 'achado manual' },
            { fix: 636, escaped: false, origin: 628, reason: 'mudanca planejada' },
        ],
    })
    assert.equal(summary.missedByScript, 1)
    assert.equal(summary.confirmedOrigins, 1)
    assert.equal(summary.rejected, 0)
    assert.equal(summary.pending, 0)
    assert.equal(summary.rate, 1 / 3)
})


test('parseArgs: --since obrigatorio em AAAA-MM-DD, --json opcional', () => {
    assert.deepEqual(parseArgs(['--since', '2026-10-22']), { since: '2026-10-22', json: false })
    assert.deepEqual(parseArgs(['--since', '2026-10-22', '--json']), { since: '2026-10-22', json: true })
    assert.throws(() => parseArgs([]), /--since/)
    assert.throws(() => parseArgs(['--since', '22/10/2026']), /AAAA-MM-DD/)
})

test('parseRemovedRanges: so linhas removidas de produto, pelo caminho antigo', () => {
    const diff = [
        'diff --git a/src/commonMain/a.kt b/src/commonMain/a.kt',
        '--- a/src/commonMain/a.kt',
        '+++ b/src/commonMain/a.kt',
        '@@ -10,2 +10,3 @@ fun x',
        '-old1',
        '-old2',
        '+new1',
        '+new2',
        '+new3',
        '@@ -40 +41,2 @@',
        '-single',
        '+a',
        '+b',
        '@@ -50,0 +51 @@',
        '+added only',
        'diff --git a/docs/x.md b/docs/x.md',
        '--- a/docs/x.md',
        '+++ b/docs/x.md',
        '@@ -1,3 +1,0 @@',
        '-gone',
        '-gone',
        '-gone',
        'diff --git a/src/commonMain/new.kt b/src/commonMain/new.kt',
        '--- /dev/null',
        '+++ b/src/commonMain/new.kt',
        '@@ -0,0 +1,2 @@',
        '+x',
        '+y',
        'diff --git a/src/commonMain/gone.kt b/src/commonMain/gone.kt',
        '--- a/src/commonMain/gone.kt',
        '+++ /dev/null',
        '@@ -1,2 +0,0 @@',
        '-g1',
        '-g2',
    ].join('\n')
    assert.deepEqual(parseRemovedRanges(diff), [
        { path: 'src/commonMain/a.kt', from: 10, to: 11 },
        { path: 'src/commonMain/a.kt', from: 40, to: 40 },
        { path: 'src/commonMain/gone.kt', from: 1, to: 2 },
    ])
})

test('parseBlame: shas unicos na ordem da primeira aparicao', () => {
    const A = 'a'.repeat(40)
    const B = 'b'.repeat(40)
    const porcelain = [
        `${A} 10 10 2`, 'author x', '\tlinha 1',
        `${A} 11 11`, '\tlinha 2',
        `${B} 3 12 1`, 'author y', '\tlinha 3', '',
    ].join('\n')
    assert.deepEqual(parseBlame(porcelain), [A, B])
})

test('renderReport: taxa, contagens, area e uma linha por correcao ou sem origem', () => {
    const summary = {
        productPrs: 3,
        confirmedOrigins: 1,
        pending: 1,
        rejected: 0,
        missedByScript: 0,
        rate: 1 / 3,
        ceiling: 2 / 3,
        byArea: {
            'hud-window': { productPrs: 1, confirmedOrigins: 1, rate: 1 },
            screen: { productPrs: 2, confirmedOrigins: 0, rate: 0 },
            data: { productPrs: 0, confirmedOrigins: 0, rate: null },
            server: { productPrs: 0, confirmedOrigins: 0, rate: null },
            other: { productPrs: 0, confirmedOrigins: 0, rate: null },
        },
    }
    const lines = renderReport({
        since: '2026-10-22',
        summary,
        rows: [
            { fix: 630, title: 't', area: 'hud-window', status: 'confirmado', origins: [{ pr: 628, via: ['reference'] }] },
            { fix: 622, title: 't', area: 'other', status: null, origins: [] },
        ],
    }).split('\n')
    assert.ok(lines.includes('pendentes: 1'))
    assert.ok(lines.includes('recusados: 0'))
    assert.ok(lines.includes('missedByScript: 0'))
    assert.ok(lines.some((line) => line.includes('taxa confirmada: 33.3%')))
    assert.ok(lines.some((line) => /hud-window.*taxa 100.0%/.test(line)))
    assert.ok(lines.some((line) => line.includes('#630') && line.includes('#628') && line.includes('confirmado')))
    assert.ok(lines.some((line) => line.includes('#622') && line.includes('sem origem')))
})

test('summarize: known cobre origem fora do periodo; contagem de produto so do periodo', () => {
    const known = [
        pr(628, { title: 'feat(hud): flutuante', day: 8, files: [{ path: HUD, added: 80, removed: 0 }] }),
        pr(630, { title: 'fix: x', body: 'Corrige #628.', day: 10 }),
    ]
    const period = [pr(630, { title: 'fix: x', body: 'Corrige #628.', day: 10 })]
    const summary = summarize({
        prs: period,
        known,
        candidates: [],
        verdicts: [{ fix: 630, escaped: true, origin: 628, reason: 'x' }],
    })
    assert.equal(summary.productPrs, 1)
    assert.equal(summary.confirmedOrigins, 1)
    assert.equal(summary.byArea['hud-window'].confirmedOrigins, 1)
})

import { buildRows, renderJson } from '../escaped-defects.mjs'

const emptyAreas = () => Object.fromEntries(['hud-window', 'screen', 'data', 'server', 'other'].map((area) => [area, { productPrs: 0, confirmedOrigins: 0, rate: null }]))

test('renderReport: linha por correcao com area, titulo, todas as origens com via e status', () => {
    const summary = { productPrs: 4, confirmedOrigins: 0, pending: 2, rejected: 0, missedByScript: 0, rate: 0, ceiling: 0.5, byArea: emptyAreas() }
    const lines = renderReport({
        since: '2026-10-22',
        summary,
        rows: [
            { fix: 632, title: 'fix(hud): x', area: 'hud-window', status: 'pendente', origins: [{ pr: 579, via: ['blame'] }, { pr: 622, via: ['blame'] }] },
            { fix: 475, title: 'fix: y', area: 'screen', status: null, origins: [] },
        ],
    }).split('\n')
    assert.ok(lines.includes('#632 hud-window "fix(hud): x" <- #579(blame), #622(blame) [pendente]'))
    assert.ok(lines.includes('#475 screen "fix: y" <- sem origem'))
})

test('renderReport: taxa confirmada e teto so quando ha pendentes', () => {
    const withPending = renderReport({ since: '2026-10-22', summary: { productPrs: 4, confirmedOrigins: 0, pending: 2, rejected: 0, missedByScript: 0, rate: 0, ceiling: 0.5, byArea: emptyAreas() }, rows: [] }).split('\n')
    assert.ok(withPending.includes('taxa confirmada: 0.0%'))
    assert.ok(withPending.includes('teto se todos os pendentes forem defeito: 50.0%'))
    const noPending = renderReport({ since: '2026-10-22', summary: { productPrs: 4, confirmedOrigins: 1, pending: 0, rejected: 0, missedByScript: 0, rate: 0.25, ceiling: 0.25, byArea: emptyAreas() }, rows: [] }).split('\n')
    assert.ok(noPending.includes('taxa confirmada: 25.0%'))
    assert.ok(!noPending.some((line) => line.startsWith('teto se todos')))
})

test('summarize: teto conta a primeira origem de cada pendente, distinta da confirmada', () => {
    const prs = [
        pr(628, { title: 'feat: c', day: 8 }),
        pr(629, { title: 'feat: d', day: 9 }),
        pr(630, { title: 'fix: a', body: 'Corrige #628 e #629.', day: 10 }),
        pr(631, { title: 'fix: b', body: 'Corrige #629.', day: 11 }),
    ]
    const summary = summarize({ prs, candidates: candidatesOf({ prs, issueBodies: {}, blamedCommits: {} }), verdicts: [] })
    assert.equal(summary.pending, 2)
    assert.equal(summary.rate, 0)
    assert.equal(summary.ceiling, 0.5)
})

test('buildRows: titulo, area, todas as origens com via e status', () => {
    const prs = [
        pr(579, { title: 'feat: a', day: 5 }),
        pr(622, { title: 'feat: b', day: 6 }),
        pr(632, { title: 'fix(hud): c', body: 'Corrige #579 e #622.', day: 10, files: [{ path: HUD, added: 5, removed: 1 }] }),
        pr(640, { title: 'fix: z', day: 11 }),
    ]
    const rows = buildRows({
        corrections: prs.filter((item) => item.number === 632 || item.number === 640),
        candidates: candidatesOf({ prs, issueBodies: {}, blamedCommits: {} }),
        verdicts: [],
    })
    assert.deepEqual(rows, [
        { fix: 632, title: 'fix(hud): c', area: 'hud-window', status: 'pendente', origins: [{ pr: 579, via: ['reference'] }, { pr: 622, via: ['reference'] }] },
        { fix: 640, title: 'fix: z', area: 'screen', status: null, origins: [] },
    ])
})

test('renderJson: inclui titulo, area, origens completas e teto', () => {
    const parsed = JSON.parse(renderJson({
        since: '2026-10-22',
        summary: { ceiling: 0.5, rate: 0 },
        rows: [{ fix: 632, title: 't', area: 'hud-window', status: 'pendente', origins: [{ pr: 579, via: ['blame'] }, { pr: 622, via: ['blame'] }] }],
    }))
    assert.equal(parsed.summary.ceiling, 0.5)
    assert.deepEqual(parsed.rows[0], { fix: 632, title: 't', area: 'hud-window', status: 'pendente', origins: [{ pr: 579, via: ['blame'] }, { pr: 622, via: ['blame'] }] })
})
