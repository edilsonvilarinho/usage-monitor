import { AXES, PR_LIMIT, buildReport, deliveryMetrics, parseArgs, rankAxis, renderHtml, renderMarkdown, scorecardFromJson } from '../repo-benchmark.mjs'
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
    assert.deepEqual(metrics, { releases: 0, mergedPrs: 0, medianMergeHours: null, revertRate: null, mergedPrsTruncated: false })
})

test('truncado: 1000 PRs recebidos marca mergedPrsTruncated; 999 nao', () => {
    const full = Array.from({ length: 1000 }, () => pr('feat: a'))
    assert.equal(deliveryMetrics({ prs: full, releases: [], now: NOW }).mergedPrsTruncated, true)
    assert.equal(deliveryMetrics({ prs: full.slice(0, 999), releases: [], now: NOW }).mergedPrsTruncated, false)
    assert.equal(deliveryMetrics({ prs: [pr('feat: a'), pr('feat: b')], releases: [], now: NOW, prLimit: 2 }).mergedPrsTruncated, true)
    assert.equal(PR_LIMIT, 1000)
})

test('truncado: buildReport copia mergedPrsTruncated; ausente vira false', () => {
    const groups = { 'same-goal': ['A'] }
    const data = new Map([['T', { mergedPrs: 1000, mergedPrsTruncated: true }], ['A', { mergedPrs: 3 }]])
    const rows = buildReport({ target: 'T', groups, data }).rows
    assert.equal(rows[0].mergedPrsTruncated, true)
    assert.equal(rows[1].mergedPrsTruncated, false)
})

test('truncado: markdown mostra ≥1000 na coluna PRs mesclados', () => {
    const report = { target: 'T', rows: [{ repo: 'T', group: 'target', scorecard: null, releases: 2, mergedPrs: 1000, mergedPrsTruncated: true, medianMergeHours: 12.5, revertRate: 0.009 }], targetPositions: Object.fromEntries(AXES.map((axis) => [axis.key, { position: null, of: 1, percentile: null }])) }
    const lines = renderMarkdown(report).split('\n')
    assert.deepEqual(lines.filter((line) => line.startsWith('| T |')), ['| T | target | — | 2 | ≥1000 | 12,5 | 0,9% |'])
})

test('truncado: html mostra ≥1000 e a nota de rodape; sem truncamento nao aparece', () => {
    const report = htmlReport()
    report.rows[0] = { ...report.rows[0], mergedPrs: 1000, mergedPrsTruncated: true }
    const html = renderHtml(report, HTML_OPTIONS)
    assert.ok(html.includes('≥1000'))
    assert.ok(html.includes('<p>≥: a busca do GitHub devolve no máximo 1000 PRs; mediana e taxa de revert usam os mais recentes.</p>'))
    assert.ok(!renderHtml(htmlReport(), HTML_OPTIONS).includes('≥'))
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
    assert.deepEqual(report.rows[0], { repo: 'T', group: 'target', ...metrics(6), mergedPrsTruncated: false })
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

// ---- HTML (#687, A03): renderHtml sobre um relatorio falso, sem rede ----

const htmlReport = () => ({
    target: 'T/usage-monitor',
    rows: [
        { repo: 'T/usage-monitor', group: 'target', scorecard: 6.4, releases: 2, mergedPrs: 10, medianMergeHours: 12.5, revertRate: 0.009 },
        { repo: 'A/one', group: 'same-goal', scorecard: 7.5, releases: null, mergedPrs: 3, medianMergeHours: 1, revertRate: 0 },
        { repo: 'B/<two>', group: 'large-stack', scorecard: 3, releases: 5, mergedPrs: 4, medianMergeHours: 30, revertRate: 0.1 },
    ],
    targetPositions: {
        scorecard: { position: 1, of: 5, percentile: 80 },
        releases: { position: 3, of: 5, percentile: 50 },
        medianMergeHours: { position: 5, of: 5, percentile: 10 },
        revertRate: { position: null, of: 3, percentile: null },
    },
})
const HTML_OPTIONS = { generatedAt: '2026-11-27T00:00:00.000Z', windowDays: 90 }

test('html: documento comeca com doctype, lang pt-BR, charset e viewport', () => {
    const html = renderHtml(htmlReport(), HTML_OPTIONS)
    assert.equal(html.slice(0, 15), '<!doctype html>')
    assert.match(html, /<html lang="pt-BR">/)
    assert.match(html, /<meta charset="utf-8">/i)
    assert.match(html, /<meta name="viewport"/)
})

test('html: cabecalho traz eyebrow, titulo, data de geracao e janela', () => {
    const html = renderHtml(htmlReport(), HTML_OPTIONS)
    assert.ok(html.includes('usage-monitor · benchmark de repositório'))
    assert.ok(html.includes('Posição no grupo'))
    assert.ok(html.includes('gerado em 2026-11-27T00:00:00.000Z'))
    assert.ok(html.includes('janela de 90 dias'))
})

test('html: contem os quatro rotulos de eixo', () => {
    const html = renderHtml(htmlReport(), HTML_OPTIONS)
    for (const label of ['OpenSSF Scorecard', 'Releases em 90 dias', 'Mediana até o merge', 'Taxa de revert']) {
        assert.ok(html.includes(label), label)
    }
})

test('html: autocontido, sem script e sem recurso externo alem do Google Fonts', () => {
    const html = renderHtml(htmlReport(), HTML_OPTIONS)
    assert.ok(!html.includes('<script'), 'nao deve haver <script>')
    const urls = html.match(/https?:\/\/[^"') ]+/g) ?? []
    assert.equal(urls.length, 1, urls.join(' '))
    assert.ok(urls[0].startsWith('https://fonts.googleapis.com/'), urls[0])
})

test('html: alvo com position null gera nao medido e nivel off', () => {
    const html = renderHtml(htmlReport(), HTML_OPTIONS)
    assert.ok(html.includes('data-axis="revertRate" data-level="off"'))
    assert.ok(html.includes('não medido'))
})

test('html: percentil 80 e ok, 50 e warn, 10 e crit', () => {
    const html = renderHtml(htmlReport(), HTML_OPTIONS)
    assert.ok(html.includes('data-axis="scorecard" data-level="ok"'))
    assert.ok(html.includes('data-axis="releases" data-level="warn"'))
    assert.ok(html.includes('data-axis="medianMergeHours" data-level="crit"'))
})

test('html: nome de repositorio com < sai escapado', () => {
    const html = renderHtml(htmlReport(), HTML_OPTIONS)
    assert.ok(html.includes('B/&lt;two&gt;'))
    assert.ok(!html.includes('B/<two>'))
    const quoted = renderHtml({ ...htmlReport(), rows: [{ ...htmlReport().rows[0], repo: `a&"'<b>` }] }, HTML_OPTIONS)
    assert.ok(quoted.includes('a&amp;&quot;&#39;&lt;b&gt;'))
})

test('html: tabela tem traco para nulo e taxa com virgula; alvo destacado', () => {
    const html = renderHtml(htmlReport(), HTML_OPTIONS)
    assert.ok(html.includes('—'))
    assert.ok(html.includes('0,9%'))
    assert.ok(html.includes('<tr class="target">'))
})

test('html: eixo com nulo diz quantos nao medidos', () => {
    const html = renderHtml(htmlReport(), HTML_OPTIONS)
    assert.ok(html.includes('1 não medido'))
})

test('html: CSS traz os tokens de fundo escuro e claro', () => {
    const html = renderHtml(htmlReport(), HTML_OPTIONS)
    assert.ok(html.includes('#131010'))
    assert.ok(html.includes('#F6F3F3'))
})

test('args: --html grava o caminho; ausente vira null', () => {
    assert.equal(parseArgs(['--html', 'out.html']).html, 'out.html')
    assert.equal(parseArgs([]).html, null)
})

test('html: tabela dentro de contêiner com overflow-x auto; a pagina nao rola', () => {
    const html = renderHtml(htmlReport(), HTML_OPTIONS)
    assert.match(html, /<div class="scroll">\s*<table>/)
    assert.match(html, /<\/table>\s*<\/div>/)
    assert.match(html, /\.scroll \{[^}]*overflow-x: auto/)
})

test('html: tela estreita tem coluna unica nos cartoes e barras sem estouro', () => {
    const html = renderHtml(htmlReport(), HTML_OPTIONS)
    assert.match(html, /@media \(max-width: 600px\) \{[^@]*\.cards \{ grid-template-columns: 1fr/)
    assert.match(html, /@media \(max-width: 600px\) \{[^@]*\.bar-name \{ grid-column: 1 \/ -1/)
    assert.match(html, /\.bar-name \{ min-width: 0/)
})
