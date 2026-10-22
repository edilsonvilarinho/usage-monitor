// Desenho do relatorio de testes, no design system de `docs/design-system/`.
//
// As primitivas aqui sao a versao HTML das publicadas em `components/`:
// AppPanel (+Header/Body), AppMetric, AppDataTable com AppColumnHeader,
// AppStatusIndicator (ponto E palavra), AppProgressTrack, AppBanner e
// AppSegmentedControl. Nenhum valor novo: cor, espaco, raio e tipo vem dos
// tokens, que o chamador le do repositorio e passa inline.

const LEVEL_WORD = { ok: 'Saudável', warn: 'Atenção', crit: 'Crítico', off: 'Não medido' }
const GATE_WORD = { ok: 'Aprovado', warn: 'Atenção', crit: 'Bloqueia' }

export function renderHtml(report, tokens) {
    const verdict = overallVerdict(report.gates)
    const sections = [
        ['prontidao', 'Prontidão', renderGates(report)],
        ['tipos', 'Tipos de teste', renderKinds(report)],
        ['areas', 'Áreas', renderAreas(report)],
        ['cobertura', 'Cobertura por camada', renderCoverageLayers(report)],
        ['lacunas', 'Arquivos descobertos', renderCoverageGaps(report)],
        ['falhas', 'Falhas e ignorados', renderFailures(report)],
        ['lentas', 'Suítes mais lentas', renderSlowest(report)],
        ['fora', 'Fora do alcance', renderBlindSpots(report)],
        ['suites', 'Todas as suítes', renderAllSuites(report)],
    ]
    return `<!doctype html>
<html lang="pt-BR">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Relatório de testes</title>
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=IBM+Plex+Mono:wght@400;500;600&family=IBM+Plex+Sans:wght@400;500;600&display=swap">
<style>
${tokens}
${STYLES}
</style>
</head>
<body>
<div class="page">
  <header class="masthead">
    <div>
      <div class="eyebrow">Usage Monitor · relatório de testes</div>
      <h1>Relatório de testes</h1>
      <p class="meta">${escape(metaLine(report))}</p>
    </div>
    <div class="segmented" role="radiogroup" aria-label="Tema">
      <button type="button" role="radio" data-theme-choice="system">Sistema</button>
      <button type="button" role="radio" data-theme-choice="dark">Escuro</button>
      <button type="button" role="radio" data-theme-choice="light">Claro</button>
    </div>
  </header>

  ${renderBanner(verdict, report)}
  ${renderMetrics(report)}

  <nav class="index" aria-label="Seções">
    ${sections.map(([id, label]) => `<a href="#${id}">${escape(label)}</a>`).join('')}
  </nav>

  ${sections.map(([id, , html]) => `<section id="${id}">${html}</section>`).join('\n')}

  <footer class="footnote">
    ${renderHowTo()}
  </footer>
</div>
<script>${SCRIPT}</script>
</body>
</html>
`
}

// ── Cabecalho, veredito e metricas ─────────────────────────────────────────

function metaLine(report) {
    const git = report.git
    const when = formatDateTime(report.generatedAt)
    const parts = [
        git.sha ? `commit ${git.sha}` : null,
        git.branch ? `branch ${git.branch}` : null,
        git.dirty ? 'com alterações locais' : null,
        `gerado em ${when} BRT`,
        `plataforma ${PLATFORM_NAME[report.platform] ?? report.platform}`,
    ]
    return parts.filter(Boolean).join(' · ')
}

const PLATFORM_NAME = { win32: 'Windows', linux: 'Linux', darwin: 'macOS' }

function overallVerdict(gates) {
    if (gates.some((gate) => gate.level === 'crit')) return 'crit'
    if (gates.some((gate) => gate.level === 'warn')) return 'warn'
    return 'ok'
}

function renderBanner(verdict, report) {
    const blocking = report.gates.filter((gate) => gate.level === 'crit')
    const attention = report.gates.filter((gate) => gate.level === 'warn')
    const title = {
        ok: 'Suíte verde e cobertura medida',
        warn: 'Suíte verde, com pontos de atenção',
        crit: 'Suíte bloqueada',
    }[verdict]
    const detail =
        verdict === 'ok'
            ? 'Todos os portões passaram. O resultado vale para o código descrito no cabeçalho.'
            : [...blocking, ...attention].map((gate) => `${gate.label}: ${gate.detail}`).join(' ')
    return `<div class="banner banner-${verdict}" role="status">
    ${indicator(verdict, GATE_WORD[verdict] === 'Aprovado' ? 'Pronto' : GATE_WORD[verdict])}
    <div><div class="banner-title">${escape(title)}</div><div class="banner-text">${escape(detail)}</div></div>
  </div>`
}

function renderMetrics(report) {
    const totals = report.totals
    const passRate = totals.tests > 0 ? ratio(totals.passed, totals.tests) : null
    const metrics = [
        ['Testes executados', formatInt(totals.tests), `${formatInt(report.kotlin.tests)} Kotlin · ${formatInt(report.server.tests)} servidor`],
        ['Aprovação', passRate === null ? '—' : formatPercent(passRate), `${formatInt(totals.failed)} falhas · ${formatInt(totals.skipped)} ignorados`],
        [
            'Cobertura de linhas',
            totals.line.total > 0 ? formatPercent(ratio(totals.line.covered, totals.line.total)) : '—',
            totals.line.total > 0
                ? `${formatInt(totals.line.covered)} de ${formatInt(totals.line.total)} linhas · ${totals.lineSources.join(' + ')}`
                : 'Não medida',
        ],
        ['Tempo somado', formatDuration(totals.time), 'Soma das suítes; com forks o relógio anda menos'],
        coverageMetric('Kotlin · linhas', report.kover?.totals.line),
        coverageMetric('Kotlin · ramos', report.kover?.totals.branch),
        coverageMetric('Servidor · linhas', report.serverCoverage?.totals.line),
        coverageMetric('Servidor · ramos', report.serverCoverage?.totals.branch),
    ]
    return `<div class="metrics">${metrics
        .map(
            ([label, value, hint]) =>
                `<div class="metric"><div class="metric-label">${escape(label)}</div><div class="metric-value">${escape(value)}</div><div class="metric-hint">${escape(hint)}</div></div>`,
        )
        .join('')}</div>`
}

function coverageMetric(label, counter) {
    if (!counter || counter.total === 0) return [label, '—', 'Não medida']
    return [label, formatPercent(ratio(counter.covered, counter.total)), `${formatInt(counter.covered)} de ${formatInt(counter.total)}`]
}

// ── Secoes ─────────────────────────────────────────────────────────────────

function renderGates(report) {
    const rows = report.gates.map(
        (gate) => `<tr><td class="strong">${escape(gate.label)}</td><td>${indicator(gate.level, GATE_WORD[gate.level])}</td><td class="prose">${escape(gate.detail)}</td></tr>`,
    )
    return panel(
        'Prontidão para automação',
        'O que precisa estar verde antes de abrir PR, aguardar o CI e fazer merge sem revisão humana.',
        table(['Verificação', 'Estado', 'Detalhe'], rows, ['', '', '']),
    )
}

function renderKinds(report) {
    const all = [
        ...report.kotlin.kinds.map((kind) => ({ ...kind, scope: 'Kotlin' })),
        ...report.server.kinds.map((kind) => ({ ...kind, scope: 'Servidor' })),
    ]
    const total = report.totals.tests
    const rows = all.map(
        (kind) => `<tr>
      <td class="strong">${escape(kind.label)}</td>
      <td>${escape(kind.scope)}</td>
      <td class="num">${formatInt(kind.files)}</td>
      <td class="num">${formatInt(kind.tests)}</td>
      <td class="bar-cell">${shareBar(kind.tests, total)}</td>
      <td>${failureIndicator(kind)}</td>
    </tr>`,
    )
    return panel(
        'Testes por tipo',
        'Tipo pelo conteúdo do arquivo: Compose em runDesktopComposeUiTest, HTTP simulado em MockEngine, integração local em arquivo temporário ou SQLite, servidor em supertest. O resto é unidade.',
        table(['Tipo', 'Escopo', 'Suítes', 'Testes', 'Parcela', 'Estado'], rows, ['', '', 'num', 'num', '', '']),
    )
}

function renderAreas(report) {
    const total = report.totals.tests
    const kotlinRows = report.kotlin.areas.map((area) => areaRow(area, total))
    const serverRow = report.server.ran
        ? [areaRow({ label: 'Servidor de time', files: report.server.classes.length, tests: report.server.tests, failed: report.server.failed, skipped: report.server.skipped, time: report.server.time }, total)]
        : []
    const sourceSets = report.kotlin.sourceSets
        .map((set) => `${set.id}: ${formatInt(set.files)} arquivos, ${formatInt(set.declared)} @Test`)
        .join(' · ')
    return panel(
        'Testes por área',
        `Área pelo pacote da classe de teste. ${sourceSets}${report.server.ran ? ` · server/test: ${formatInt(report.server.testFiles)} arquivos` : ''}.`,
        table(['Área', 'Suítes', 'Testes', 'Parcela', 'Tempo', 'Estado'], [...kotlinRows, ...serverRow], ['', 'num', 'num', '', 'num', '']),
    )
}

function areaRow(area, total) {
    return `<tr>
      <td class="strong">${escape(area.label)}</td>
      <td class="num">${formatInt(area.files)}</td>
      <td class="num">${formatInt(area.tests)}</td>
      <td class="bar-cell">${shareBar(area.tests, total)}</td>
      <td class="num">${formatDuration(area.time)}</td>
      <td>${failureIndicator(area)}</td>
    </tr>`
}

function renderCoverageLayers(report) {
    const parts = []
    if (report.kover) {
        parts.push(
            panel(
                'Cobertura Kotlin por camada',
                'Kover sobre commonMain e desktopMain, medido durante o desktopTest. Main.kt fica fora por configuração.',
                coverageTable(report.kover.layers, report.kover.totals),
            ),
        )
    } else {
        parts.push(panel('Cobertura Kotlin por camada', null, emptyState('Cobertura Kotlin não medida.', 'Rode a suíte com -Pcoverage e depois koverXmlReport.')))
    }
    if (report.serverCoverage) {
        parts.push(
            panel(
                'Cobertura do servidor por pasta',
                'v8 sobre server/src, medido pelo vitest.',
                coverageTable(report.serverCoverage.layers, report.serverCoverage.totals),
            ),
        )
    } else {
        parts.push(panel('Cobertura do servidor por pasta', null, emptyState('Cobertura do servidor não medida.', 'Rode npm run test:coverage em server/.')))
    }
    parts.push(`<p class="legend">Faixas de leitura, não piso: ${indicator('ok', LEVEL_WORD.ok)} 80% ou mais · ${indicator('warn', LEVEL_WORD.warn)} de 60% a 80% · ${indicator('crit', LEVEL_WORD.crit)} abaixo de 60%. Nenhuma build falha por elas.</p>`)
    return parts.join('\n')
}

function coverageTable(layers, totals) {
    const rows = layers.map(
        (layer) => `<tr>
      <td class="strong">${escape(layer.label)}</td>
      <td class="num">${formatInt(layer.files)}</td>
      <td class="num">${formatInt(layer.line.covered)} / ${formatInt(layer.line.total)}</td>
      <td class="bar-cell">${coverageTrack(layer.line)}</td>
      <td class="num">${percentOf(layer.branch)}</td>
      <td class="num">${percentOf(layer.method)}</td>
    </tr>`,
    )
    rows.push(`<tr class="total-row">
      <td class="strong">Total</td>
      <td class="num">${formatInt(layers.reduce((sum, layer) => sum + layer.files, 0))}</td>
      <td class="num">${formatInt(totals.line.covered)} / ${formatInt(totals.line.total)}</td>
      <td class="bar-cell">${coverageTrack(totals.line)}</td>
      <td class="num">${percentOf(totals.branch)}</td>
      <td class="num">${percentOf(totals.method)}</td>
    </tr>`)
    return table(['Camada', 'Arquivos', 'Linhas', 'Cobertura de linhas', 'Ramos', 'Métodos'], rows, ['', 'num', 'num', '', 'num', 'num'])
}

function renderCoverageGaps(report) {
    const files = [...(report.kover?.files ?? []), ...(report.serverCoverage?.files ?? [])]
    if (files.length === 0) return panel('Arquivos com mais linhas descobertas', null, emptyState('Sem cobertura medida.', 'Nada a listar até a cobertura rodar.'))
    const worst = [...files].sort((left, right) => right.line.missed - left.line.missed || left.path.localeCompare(right.path)).slice(0, 25)
    const rows = worst.map(
        (file) => `<tr>
      <td class="path">${escape(file.path)}</td>
      <td class="num">${formatInt(file.line.missed)}</td>
      <td class="num">${formatInt(file.line.total)}</td>
      <td class="bar-cell">${coverageTrack(file.line)}</td>
    </tr>`,
    )
    const uncovered = files.filter((file) => file.line.covered === 0 && file.line.total > 0).sort((left, right) => left.path.localeCompare(right.path))
    const uncoveredHtml =
        uncovered.length === 0
            ? emptyState('Nenhum arquivo com cobertura zero.', 'Todo arquivo medido tem ao menos uma linha exercitada.')
            : `<details class="disclosure"><summary>${formatInt(uncovered.length)} arquivos com cobertura zero · ${formatInt(uncovered.reduce((sum, file) => sum + file.line.total, 0))} linhas</summary>
        ${table(['Arquivo', 'Linhas'], uncovered.map((file) => `<tr><td class="path">${escape(file.path)}</td><td class="num">${formatInt(file.line.total)}</td></tr>`), ['', 'num'])}
      </details>`
    return [
        panel('Arquivos com mais linhas descobertas', 'Os 25 primeiros por linhas não exercitadas: é onde um teste novo rende mais.', table(['Arquivo', 'Descobertas', 'Linhas', 'Cobertura'], rows, ['', 'num', 'num', ''])),
        panel('Arquivos sem cobertura', 'Nenhuma linha exercitada pela suíte.', uncoveredHtml),
    ].join('\n')
}

function renderFailures(report) {
    const failures = [...report.kotlin.classes, ...report.server.classes].flatMap((entry) =>
        entry.failures.map((testcase) => ({ ...testcase, suite: entry.simpleName })),
    )
    if (failures.length === 0) return panel('Falhas e ignorados', null, emptyState('Nenhuma falha e nenhum teste ignorado nesta execução.', 'Todos os casos executados passaram.'))
    const rows = failures.map(
        (testcase) => `<tr>
      <td>${indicator(testcase.status === 'failed' ? 'crit' : 'off', testcase.status === 'failed' ? 'Falhou' : 'Ignorado')}</td>
      <td class="path">${escape(testcase.suite)}</td>
      <td class="prose">${escape(testcase.name)}${testcase.message ? `<div class="hint">${escape(testcase.message)}</div>` : ''}</td>
    </tr>`,
    )
    return panel('Falhas e ignorados', `${formatInt(failures.length)} casos.`, table(['Estado', 'Suíte', 'Caso'], rows, ['', '', '']))
}

function renderSlowest(report) {
    const all = [...report.kotlin.classes, ...report.server.classes].sort((left, right) => right.time - left.time || left.name.localeCompare(right.name)).slice(0, 15)
    const rows = all.map(
        (entry) => `<tr>
      <td class="path">${escape(entry.simpleName)}</td>
      <td>${escape(kindLabel(report, entry))}</td>
      <td class="num">${formatInt(entry.tests)}</td>
      <td class="num">${formatDuration(entry.time)}</td>
    </tr>`,
    )
    return panel('Suítes mais lentas', 'As 15 classes que mais pesam no tempo somado.', table(['Suíte', 'Tipo', 'Testes', 'Tempo'], rows, ['', '', 'num', 'num']))
}

function renderBlindSpots(report) {
    const rows = report.blindSpots.map(
        (spot) => `<tr><td class="strong">${escape(spot.title)}</td><td class="prose">${escape(spot.detail)}<div class="hint">Fonte: ${escape(spot.source)}</div></td></tr>`,
    )
    return panel(
        'O que a suíte não cobre',
        'Verde aqui não diz nada sobre estes pontos. Mudança que os toca precisa de verificação à mão, registrada no PR.',
        table(['Ponto', 'Por quê'], rows, ['', '']),
    )
}

function renderAllSuites(report) {
    const all = [...report.kotlin.classes, ...report.server.classes]
    const rows = all.map(
        (entry) => `<tr data-filter="${escape(`${entry.name} ${entry.file ?? ''} ${kindLabel(report, entry)}`.toLowerCase())}">
      <td class="path" title="${escape(entry.file ?? entry.name)}">${escape(entry.simpleName)}</td>
      <td>${escape(entry.sourceSet)}</td>
      <td>${escape(kindLabel(report, entry))}</td>
      <td class="num">${formatInt(entry.tests)}</td>
      <td class="num">${formatDuration(entry.time)}</td>
      <td>${failureIndicator(entry)}</td>
    </tr>`,
    )
    return panel(
        'Todas as suítes',
        `${formatInt(all.length)} suítes. O filtro procura no nome, no caminho e no tipo.`,
        `<div class="filter"><input type="search" id="suite-filter" placeholder="Filtrar suítes" aria-label="Filtrar suítes"><span id="suite-count" class="hint"></span></div>
    <div class="scroll">${table(['Suíte', 'Source set', 'Tipo', 'Testes', 'Tempo', 'Estado'], rows, ['', '', '', 'num', 'num', ''], 'suite-table')}</div>`,
    )
}

function renderHowTo() {
    return panel(
        'Como gerar de novo',
        'A skill usage-monitor-test-report roda estes passos. No Windows, troque bash ./gradlew por gradlew.bat.',
        `<pre class="code">bash ./gradlew desktopTest --rerun -Pcoverage -PtestForks=4
bash ./gradlew koverXmlReport -Pcoverage
(cd server &amp;&amp; npm ci &amp;&amp; npm run test:coverage)
node .claude/skills/usage-monitor-test-report/scripts/build-test-report.mjs</pre>`,
    )
}

// ── Primitivas ─────────────────────────────────────────────────────────────

function panel(title, subtitle, body) {
    return `<div class="panel">
    <div class="panel-header"><div class="panel-title">${escape(title)}</div>${subtitle ? `<div class="panel-subtitle">${escape(subtitle)}</div>` : ''}</div>
    <div class="panel-body">${body}</div>
  </div>`
}

function table(headers, rows, alignments, id) {
    const head = headers.map((header, index) => `<th class="${alignments[index] ?? ''}" scope="col">${escape(header)}</th>`).join('')
    return `<table class="data"${id ? ` id="${id}"` : ''}><thead><tr>${head}</tr></thead><tbody>${rows.join('')}</tbody></table>`
}

function indicator(level, word) {
    return `<span class="status status-${level}"><span class="dot" aria-hidden="true"></span>${escape(word)}</span>`
}

function failureIndicator(group) {
    if (group.failed > 0) return indicator('crit', `${formatInt(group.failed)} falhas`)
    if (group.skipped > 0) return indicator('warn', `${formatInt(group.skipped)} ignorados`)
    return indicator('ok', 'Passou')
}

function coverageTrack(counter) {
    if (!counter || counter.total === 0) return `<span class="hint">sem linhas</span>`
    const value = ratio(counter.covered, counter.total)
    const level = coverageLevel(value)
    return `<div class="track-row"><div class="track" role="img" aria-label="${escape(`${formatPercent(value)} · ${LEVEL_WORD[level]}`)}"><div class="track-fill track-${level}" style="width:${(value * 100).toFixed(2)}%"></div></div><span class="track-value">${formatPercent(value)}</span>${indicator(level, LEVEL_WORD[level])}</div>`
}

function shareBar(part, total) {
    const value = total > 0 ? part / total : 0
    return `<div class="track-row"><div class="track" role="img" aria-label="${escape(formatPercent(value))} do total"><div class="track-fill track-info" style="width:${(value * 100).toFixed(2)}%"></div></div><span class="track-value">${formatPercent(value)}</span></div>`
}

function emptyState(title, text) {
    return `<div class="empty"><span class="empty-glyph" aria-hidden="true">·</span><div><div class="strong">${escape(title)}</div><div class="hint">${escape(text)}</div></div></div>`
}

function coverageLevel(value) {
    if (value >= 0.8) return 'ok'
    if (value >= 0.6) return 'warn'
    return 'crit'
}

function kindLabel(report, entry) {
    const kinds = entry.area === 'server' ? report.server.kinds : report.kotlin.kinds
    return kinds.find((kind) => kind.id === entry.kind)?.label ?? entry.kind
}

// ── Formato pt-BR ──────────────────────────────────────────────────────────

function ratio(part, total) {
    return total > 0 ? part / total : 0
}

function percentOf(counter) {
    return counter && counter.total > 0 ? formatPercent(ratio(counter.covered, counter.total)) : '—'
}

// Milhar com espaco fino e decimal com virgula, como pede o readme do design system.
function formatInt(value) {
    return Math.round(value).toLocaleString('pt-BR').replaceAll('.', ' ')
}

function formatPercent(value) {
    return `${(value * 100).toFixed(1).replace('.', ',')}%`
}

function formatDuration(seconds) {
    if (seconds < 1) return `${Math.round(seconds * 1000)} ms`
    if (seconds < 60) return `${seconds.toFixed(1).replace('.', ',')} s`
    const minutes = Math.floor(seconds / 60)
    return `${minutes} min ${Math.round(seconds - minutes * 60)} s`
}

function formatDateTime(iso) {
    return new Date(iso).toLocaleString('pt-BR', { timeZone: 'America/Sao_Paulo', dateStyle: 'short', timeStyle: 'short' })
}

function escape(text) {
    return String(text).replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;').replaceAll('"', '&quot;')
}

// ── Estilo e comportamento ─────────────────────────────────────────────────

const STYLES = `
html { background: var(--bg); }
body { min-height: 100vh; }
.page { max-width: 1120px; margin: 0 auto; padding: var(--s8) var(--s4); display: flex; flex-direction: column; gap: var(--s4); }
.masthead { display: flex; justify-content: space-between; align-items: flex-start; gap: var(--s4); flex-wrap: wrap; }
.eyebrow, th, .metric-label { font-family: var(--mono); font-size: var(--t10); text-transform: uppercase; letter-spacing: var(--ls-eyebrow); color: var(--muted); font-weight: var(--w-medium); }
h1 { font-family: var(--mono); font-size: var(--t28); font-weight: var(--w-semibold); letter-spacing: var(--ls-display); line-height: var(--lh-tight); margin: var(--s1) 0 var(--s2); }
.meta { font-family: var(--mono); font-size: var(--t12); color: var(--muted); margin: 0; }

.segmented { display: inline-flex; border: var(--border-w) solid var(--border); border-radius: var(--r2); background: var(--raised); padding: 2px; gap: 2px; }
.segmented button { font-family: var(--mono); font-size: var(--t12); height: 24px; padding: 0 var(--s3); border: 0; border-radius: var(--r1); background: transparent; color: var(--muted); cursor: pointer; transition: color 120ms ease, background-color 180ms ease; }
.segmented button:hover { color: var(--fg); background: var(--hover-layer); }
.segmented button[aria-checked="true"] { background: var(--surface); color: var(--fg); box-shadow: inset 0 1px 0 var(--highlight), var(--shadow-2); }

.banner { display: flex; gap: var(--s3); align-items: flex-start; padding: var(--s3) var(--s4); border: var(--border-w) solid var(--border); border-radius: var(--r2); background: var(--surface); }
.banner-ok { border-left: var(--mark-w) solid var(--ok); }
.banner-warn { border-left: var(--mark-w) solid var(--warn); }
.banner-crit { border-left: var(--mark-w) solid var(--crit); }
.banner-title { font-family: var(--mono); font-size: var(--t14); font-weight: var(--w-semibold); }
.banner-text { font-size: var(--t12); color: var(--muted); }

.metrics { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: var(--s3); }
.metric { border: var(--border-w) solid var(--border); border-radius: var(--r2); background: var(--raised); padding: var(--s3); min-width: 0; }
.metric-value { font-family: var(--mono); font-size: var(--t20); font-weight: var(--w-semibold); font-variant-numeric: tabular-nums; margin: var(--s1) 0; }
.metric-hint { font-size: var(--t12); color: var(--muted); }

.index { display: flex; flex-wrap: wrap; gap: var(--s1) var(--s4); font-family: var(--mono); font-size: var(--t12); padding: var(--s2) 0; border-bottom: var(--border-w) solid var(--border); }
.index a { color: var(--muted); }
.index a:hover { color: var(--fg); }

section { display: flex; flex-direction: column; gap: var(--s4); scroll-margin-top: var(--s4); }
.panel { background: linear-gradient(var(--sheen), transparent 56px), var(--surface); border: var(--border-w) solid var(--border-bottom); border-top-color: var(--border-top); border-radius: var(--r3); box-shadow: inset 0 1px 0 var(--highlight), var(--shadow-card); overflow: hidden; }
.panel-header { padding: var(--s3) var(--s4); border-bottom: var(--border-w) solid var(--border); }
.panel-title { font-family: var(--mono); font-size: var(--t12); font-weight: var(--w-semibold); color: var(--fg); }
.panel-subtitle { font-size: var(--t12); color: var(--muted); margin-top: 2px; }
.panel-body { padding: 0; }

.scroll { overflow-x: auto; }
table.data { width: 100%; border-collapse: collapse; font-family: var(--mono); font-size: var(--t12); }
table.data th { text-align: left; padding: var(--s2) var(--s4); border-bottom: var(--border-w) solid var(--border); white-space: nowrap; }
table.data td { padding: var(--s2) var(--s4); border-bottom: var(--border-w) solid var(--border); vertical-align: top; }
table.data tbody tr:last-child td { border-bottom: 0; }
table.data tbody tr:hover td { background: var(--hover-layer); }
.num { text-align: right; font-variant-numeric: tabular-nums; white-space: nowrap; }
th.num { text-align: right; }
.strong { font-weight: var(--w-medium); color: var(--fg); }
.prose { font-family: var(--sans); color: var(--muted); }
.path { word-break: break-all; }
.hint { font-family: var(--sans); font-size: var(--t12); color: var(--muted); }
.total-row td { border-top: var(--border-w) solid var(--border); }

.status { display: inline-flex; align-items: center; gap: var(--s1); font-family: var(--mono); font-size: var(--t12); white-space: nowrap; }
.status .dot { width: 6px; height: 6px; border-radius: 50%; background: currentColor; flex: none; }
.status-ok { color: var(--ok); }
.status-warn { color: var(--warn); }
.status-crit { color: var(--crit); }
.status-off { color: var(--muted); }

.bar-cell { min-width: 220px; }
.track-row { display: flex; align-items: center; gap: var(--s2); }
.track { flex: 1; min-width: 60px; height: var(--track-h); border-radius: 999px; background: var(--raised); overflow: hidden; }
.track-fill { height: 100%; border-radius: 999px; }
.track-ok { background: var(--ok); }
.track-warn { background: var(--warn); }
.track-crit { background: var(--crit); }
.track-info { background: var(--info); }
.track-value { font-variant-numeric: tabular-nums; min-width: 48px; text-align: right; }

.legend { font-size: var(--t12); color: var(--muted); margin: 0; display: flex; flex-wrap: wrap; gap: var(--s1) var(--s2); align-items: center; }
.empty { display: flex; gap: var(--s3); align-items: center; padding: var(--s4); font-family: var(--mono); font-size: var(--t12); }
.empty-glyph { font-size: var(--t20); color: var(--muted); }
.disclosure summary { cursor: pointer; padding: var(--s3) var(--s4); font-family: var(--mono); font-size: var(--t12); color: var(--muted); }
.disclosure summary:hover { color: var(--fg); }
.disclosure[open] summary { border-bottom: var(--border-w) solid var(--border); }

.filter { display: flex; align-items: center; gap: var(--s3); padding: var(--s3) var(--s4); border-bottom: var(--border-w) solid var(--border); }
.filter input { font-family: var(--mono); font-size: var(--t12); height: var(--h-control); width: min(320px, 100%); padding: 0 var(--s2); color: var(--fg); background: var(--bg); border: var(--border-w) solid var(--border); border-radius: var(--r2); transition: border-color 120ms ease; }
.filter input:focus-visible { outline: 2px solid var(--info); outline-offset: -1px; }

.footnote { margin-top: var(--s4); }
pre.code { margin: 0; padding: var(--s3) var(--s4); font-family: var(--mono); font-size: var(--t12); color: var(--fg); background: var(--raised); overflow-x: auto; }

@media (max-width: 760px) {
  .page { padding: var(--s6) var(--s4); }
  .metrics { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  table.data th, table.data td { padding: var(--s2); }
  .bar-cell { min-width: 140px; }
  .panel-body { overflow-x: auto; }
}
`

// Tema: "Sistema" tira o atributo e devolve a decisao ao prefers-color-scheme;
// os outros dois fixam `data-app-theme`, o mesmo escopo que os tokens leem.
const SCRIPT = `
(function () {
  var root = document.documentElement;
  var stored = null;
  try { stored = localStorage.getItem('test-report-theme'); } catch (e) {}
  function apply(choice) {
    if (choice === 'dark' || choice === 'light') root.setAttribute('data-app-theme', choice);
    else root.removeAttribute('data-app-theme');
    document.querySelectorAll('[data-theme-choice]').forEach(function (button) {
      button.setAttribute('aria-checked', String(button.getAttribute('data-theme-choice') === (choice || 'system')));
    });
  }
  apply(stored);
  document.querySelectorAll('[data-theme-choice]').forEach(function (button) {
    button.addEventListener('click', function () {
      var choice = button.getAttribute('data-theme-choice');
      try { localStorage.setItem('test-report-theme', choice); } catch (e) {}
      apply(choice);
    });
  });

  var input = document.getElementById('suite-filter');
  var count = document.getElementById('suite-count');
  var rows = Array.prototype.slice.call(document.querySelectorAll('#suite-table tbody tr'));
  function filter() {
    var query = input.value.trim().toLowerCase();
    var shown = 0;
    rows.forEach(function (row) {
      var match = !query || row.getAttribute('data-filter').indexOf(query) !== -1;
      row.hidden = !match;
      if (match) shown += 1;
    });
    count.textContent = shown + ' de ' + rows.length;
  }
  if (input) { input.addEventListener('input', filter); filter(); }
})();
`
