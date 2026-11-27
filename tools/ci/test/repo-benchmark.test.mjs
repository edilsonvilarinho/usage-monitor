import { AXES, buildReport, deliveryMetrics, rankAxis, renderMarkdown, scorecardFromJson } from '../repo-benchmark.mjs'
import { test } from 'node:test'
import assert from 'node:assert/strict'

const DAY = 86_400_000
const HOUR = 3_600_000
const NOW = new Date('2026-11-27T00:00:00Z')
// Instante `days` dias antes de NOW, deslocado `hours` horas (positivo = depois)
const at = (days, hours = 0) => new Date(NOW.getTime() - days * DAY + hours * HOUR).toISOString()
// PR criado `days` dias atras e mesclado `hours` horas depois (ou aberto, se merged for false)
const pr = (title, { days = 5, hours = 2, merged = true } = {}) =>
    ({ title, createdAt: at(days), mergedAt: merged ? at(days, hours) : null })

test('scorecard: check inconclusivo (-1) vira null', () => {
    const json = { date: '2026-11-01', score: 6.4, checks: [{ name: 'Code-Review', score: -1 }, { name: 'Maintained', score: 10 }] }
    assert.deepEqual(scorecardFromJson(json), {
        score: 6.4,
        date: '2026-11-01',
        checks: [{ name: 'Code-Review', score: null }, { name: 'Maintained', score: 10 }],
    })
})

test('scorecard: json nulo ou score nao numerico vira nulos e lista vazia', () => {
    assert.deepEqual(scorecardFromJson(null), { score: null, date: null, checks: [] })
    assert.deepEqual(scorecardFromJson({ score: 'alto' }), { score: null, date: null, checks: [] })
})

test('entrega: PR fora da janela ou sem merge nao conta; rascunho nao e release', () => {
    const prs = [
        pr('feat: a', { days: 5 }),
        pr('feat: fora da janela', { days: 120 }),
        pr('feat: aberto', { days: 3, merged: false }),
    ]
    const releases = [
        { publishedAt: at(3), isDraft: false },
        { publishedAt: at(4), isDraft: true },
        { publishedAt: at(100), isDraft: false },
    ]
    const metrics = deliveryMetrics({ prs, releases, now: NOW })
    assert.equal(metrics.mergedPrs, 1)
    assert.equal(metrics.releases, 1)
})

test('mediana: contagem impar pega o do meio; par faz media dos dois do meio', () => {
    const odd = [pr('feat: a', { hours: 2 }), pr('feat: b', { hours: 4 }), pr('feat: c', { hours: 7 })]
    assert.equal(deliveryMetrics({ prs: odd, releases: [], now: NOW }).medianMergeHours, 4)
    const even = [pr('feat: a', { hours: 1 }), pr('feat: b', { hours: 2 }), pr('feat: c', { hours: 3 }), pr('feat: d', { hours: 6 })]
    assert.equal(deliveryMetrics({ prs: even, releases: [], now: NOW }).medianMergeHours, 2.5)
})

test('sem PR na janela: mediana e taxa de revert sao null, nunca zero', () => {
    const metrics = deliveryMetrics({ prs: [pr('feat: a', { merged: false })], releases: [], now: NOW })
    assert.deepEqual(metrics, { releases: 0, mergedPrs: 0, medianMergeHours: null, revertRate: null })
})

test('revert: casa Revert "x" e revert: y; nao casa Reverting z nem fix: revert flag', () => {
    for (const [title, expected] of [['Revert "x"', 1], ['revert: y', 1], ['Reverting z', 0], ['fix: revert flag', 0]]) {
        assert.equal(deliveryMetrics({ prs: [pr(title)], releases: [], now: NOW }).revertRate, expected, title)
    }
})

test('ranking: empate divide a posicao (1, 1, 3); percentil e piso de 100 x piores / (n - 1)', () => {
    const ranked = rankAxis([{ repo: 'A', v: 10 }, { repo: 'B', v: 10 }, { repo: 'C', v: 5 }], 'v', 'higher')
    assert.deepEqual(ranked.get('A'), { position: 1, of: 3, percentile: 50 })
    assert.deepEqual(ranked.get('B'), { position: 1, of: 3, percentile: 50 })
    assert.deepEqual(ranked.get('C'), { position: 3, of: 3, percentile: 0 })
})

test('ranking: direcao lower (menor e melhor); 2o de 4 com percentil 66', () => {
    const lower = rankAxis([{ repo: 'A', v: 2 }, { repo: 'B', v: 8 }, { repo: 'C', v: 5 }], 'v', 'lower')
    assert.deepEqual(lower.get('A'), { position: 1, of: 3, percentile: 100 })
    assert.deepEqual(lower.get('C'), { position: 2, of: 3, percentile: 50 })
    assert.deepEqual(lower.get('B'), { position: 3, of: 3, percentile: 0 })
    const higher = rankAxis([{ repo: 'T', v: 30 }, { repo: 'X', v: 40 }, { repo: 'Y', v: 20 }, { repo: 'Z', v: 10 }], 'v', 'higher')
    assert.deepEqual(higher.get('T'), { position: 2, of: 4, percentile: 66 })
})

test('ranking: null fica fora do ranking; of 1 da percentil 100', () => {
    const ranked = rankAxis([{ repo: 'A', v: 1 }, { repo: 'B', v: null }, { repo: 'C', v: 3 }], 'v', 'higher')
    assert.deepEqual(ranked.get('B'), { position: null, of: 2, percentile: null })
    assert.deepEqual(ranked.get('C'), { position: 1, of: 2, percentile: 100 })
    const alone = rankAxis([{ repo: 'A', v: 5 }, { repo: 'B', v: null }], 'v', 'lower')
    assert.deepEqual(alone.get('A'), { position: 1, of: 1, percentile: 100 })
})

test('eixos: scorecard e releases maiores sao melhores; mediana e revert menores sao melhores', () => {
    assert.deepEqual(AXES, [
        { key: 'scorecard', direction: 'higher' },
        { key: 'releases', direction: 'higher' },
        { key: 'medianMergeHours', direction: 'lower' },
        { key: 'revertRate', direction: 'lower' },
    ])
})

test('relatorio: alvo primeiro, depois grupos; posicao do alvo contra todos os pares', () => {
    const groups = { 'same-goal': ['A', 'B'], 'large-stack': ['C'] }
    const metrics = (scorecard) => ({ scorecard, releases: 1, mergedPrs: 2, medianMergeHours: 3, revertRate: 0 })
    const data = new Map([['T', metrics(6)], ['A', metrics(9)], ['B', metrics(7)], ['C', metrics(1)]])
    const report = buildReport({ target: 'T', groups, data })
    assert.deepEqual(report.rows.map((row) => [row.repo, row.group]), [['T', 'target'], ['A', 'same-goal'], ['B', 'same-goal'], ['C', 'large-stack']])
    assert.deepEqual(report.rows[0], { repo: 'T', group: 'target', ...metrics(6) })
    assert.deepEqual(report.targetPositions.scorecard, { position: 3, of: 4, percentile: 33 })
})

test('markdown: null vira traco; taxa de revert com virgula; lista de posicao com nao medido', () => {
    const report = {
        target: 'T',
        rows: [
            { repo: 'T', group: 'target', scorecard: null, releases: 2, mergedPrs: 10, medianMergeHours: 12.5, revertRate: 0.009 },
            { repo: 'A', group: 'same-goal', scorecard: 7.5, releases: null, mergedPrs: 3, medianMergeHours: 1, revertRate: 0 },
        ],
        targetPositions: {
            scorecard: { position: null, of: 1, percentile: null },
            releases: { position: 2, of: 4, percentile: 66 },
            medianMergeHours: { position: 1, of: 2, percentile: 100 },
            revertRate: { position: 1, of: 2, percentile: 100 },
        },
    }
    const lines = renderMarkdown(report).split('\n')
    assert.equal(lines[0], '| Repositório | Grupo | Scorecard | Releases 90d | PRs mesclados 90d | Mediana merge (h) | Taxa de revert |')
    assert.deepEqual(lines.filter((line) => line.startsWith('| T |')), ['| T | target | — | 2 | 10 | 12,5 | 0,9% |'])
    assert.deepEqual(lines.filter((line) => line.startsWith('| A |')), ['| A | same-goal | 7,5 | — | 3 | 1,0 | 0,0% |'])
    assert.deepEqual(lines.filter((line) => line.startsWith('- ')), [
        '- scorecard: não medido',
        '- releases: 2º de 4 (percentil 66)',
        '- medianMergeHours: 1º de 2 (percentil 100)',
        '- revertRate: 1º de 2 (percentil 100)',
    ])
})
