#!/usr/bin/env node
// Relatorio de testes e cobertura do repositorio inteiro, em HTML e JSON.
//
// So LE o que a suite ja escreveu: JUnit XML do Gradle e do vitest, o XML do
// Kover e o `coverage-summary.json` do v8. Rodar a suite e passo da skill, nao
// deste script -- assim o mesmo gerador serve ao Windows (`gradlew.bat`), ao
// Linux e a um artefato baixado do CI.
//
// Sem dependencia externa, pelo mesmo motivo de `tools/ci/test-summary.mjs`:
// um relatorio que precisa de `npm install` para existir some no primeiro erro
// de rede.
import { readFileSync, readdirSync, writeFileSync, mkdirSync, existsSync, statSync } from 'node:fs'
import { join, relative, sep } from 'node:path'
import { execFileSync } from 'node:child_process'
import { platform } from 'node:os'
import { renderHtml } from './report-html.mjs'

function parseArgs(argv) {
    // `--platform` existe para o CI: o relatorio e montado num runner Linux a
    // partir da suite que rodou no Windows, e o cabecalho deve dizer Windows.
    const parsed = { root: process.cwd(), out: 'build/test-report', platform: platform(), ci: false }
    for (let index = 0; index < argv.length; index += 1) {
        const flag = argv[index]
        if (flag === '--root') parsed.root = argv[++index]
        else if (flag === '--out') parsed.out = argv[++index]
        else if (flag === '--platform') parsed.platform = argv[++index]
        else if (flag === '--ci') parsed.ci = true
        else {
            console.error('uso: build-test-report.mjs [--root <repo>] [--out <pasta relativa>] [--platform win32|linux|darwin] [--ci]')
            process.exit(2)
        }
    }
    return parsed
}

// ── Inventario estatico ────────────────────────────────────────────────────

// Areas por pacote. A primeira que casa vence; a ordem importa porque
// `presentation.ui` tambem casa `presentation`.
// Meta de cobertura de linhas, por suite (docs/planos/cobertura-95-execucao.md).
const COVERAGE_GOAL_PERCENT = 95

const KOTLIN_AREAS = [
    { id: 'architecture', label: 'Regras de arquitetura', test: (fqn) => fqn.includes('.architecture.') },
    { id: 'ui', label: 'Telas e componentes Compose', test: (fqn) => /\.(ui|help|onboarding|screenshots)\./.test(fqn) },
    { id: 'terminal', label: 'Modo terminal (CLI e TUI)', test: (fqn) => fqn.includes('.terminal.') },
    { id: 'viewmodel', label: 'View models e apresentação', test: (fqn) => fqn.includes('.presentation.') },
    { id: 'domain', label: 'Domínio', test: (fqn) => fqn.includes('.domain.') },
    { id: 'data', label: 'Dados (DTO, mapper, repositório, fonte)', test: (fqn) => fqn.includes('.data.') },
    { id: 'update', label: 'Atualização automática', test: (fqn) => fqn.includes('.update.') },
    { id: 'desktop', label: 'Desktop (janelas, bandeja, preferências, serviços)', test: () => true },
]

// Tipo pelo CONTEUDO do arquivo, nao pelo nome: e o que diz o que o teste de
// fato exercita. A primeira regra que casa vence.
const KOTLIN_KINDS = [
    { id: 'architecture', label: 'Arquitetura', test: (file) => file.fqnPackage.includes('.architecture') },
    { id: 'compose', label: 'Componente de UI (Compose)', test: (file) => /run(Desktop)?ComposeUiTest/.test(file.text) },
    { id: 'http', label: 'Contrato HTTP simulado', test: (file) => file.text.includes('MockEngine') },
    {
        id: 'local',
        label: 'Integração local (arquivo, SQLite)',
        test: (file) => /createTempDirectory|createTempFile|Files\.createTemp|SqliteConnectionManager|JdbcSqliteDriver/.test(file.text),
    },
    { id: 'unit', label: 'Unidade', test: () => true },
]

function indexKotlinTests(rootDir) {
    const byClass = new Map()
    const files = []
    for (const sourceSet of ['commonTest', 'desktopTest']) {
        for (const path of walk(join(rootDir, 'src', sourceSet), '.kt')) {
            const text = readFileSync(path, 'utf8')
            const fqnPackage = (text.match(/^package\s+([\w.]+)/m) ?? [])[1] ?? ''
            const declared = (text.match(/^\s*@Test\b/gm) ?? []).length
            const file = { path: toPosix(relative(rootDir, path)), sourceSet, fqnPackage, text, declared }
            file.kind = KOTLIN_KINDS.find((kind) => kind.test(file)).id
            files.push(file)
            for (const match of text.matchAll(/^(?:[a-z]+\s+)*class\s+(\w+)/gm)) {
                byClass.set(`${fqnPackage}.${match[1]}`, file)
            }
        }
    }
    return { byClass, files }
}

function indexServerTests(rootDir) {
    const files = []
    for (const path of walk(join(rootDir, 'server/test'), '.test.ts')) {
        const text = readFileSync(path, 'utf8')
        const relativePath = toPosix(relative(join(rootDir, 'server'), path))
        const declared = (text.match(/^\s*(it|test)(\.each[^(]*\([^)]*\))?\(/gm) ?? []).length
        files.push({ path: relativePath, declared, kind: relativePath.startsWith('test/api/') ? 'supertest' : 'unit' })
    }
    return files
}

// ── JUnit XML ──────────────────────────────────────────────────────────────

function readJUnitDir(dir) {
    if (!existsSync(dir)) return []
    return readdirSync(dir)
        .filter((name) => name.startsWith('TEST-') && name.endsWith('.xml'))
        .sort()
        .flatMap((name) => readJUnitFile(join(dir, name)))
}

function readJUnitFile(path) {
    if (!existsSync(path)) return []
    const source = readFileSync(path, 'utf8')
    const suites = []
    for (const match of source.matchAll(/<testsuite\b([^>]*)>([\s\S]*?)<\/testsuite>/g)) {
        const attributes = readAttributes(match[1])
        const cases = []
        for (const testcase of match[2].matchAll(/<testcase\b([^>]*?)(?:\/>|>([\s\S]*?)<\/testcase>)/g)) {
            const caseAttributes = readAttributes(testcase[1])
            const body = testcase[2] ?? ''
            const failure = body.match(/<(failure|error)\b([^>]*)>?/)
            cases.push({
                name: stripPlatform(caseAttributes.name ?? ''),
                classname: caseAttributes.classname ?? '',
                time: Number(caseAttributes.time ?? 0),
                status: failure ? 'failed' : /<skipped\b/.test(body) ? 'skipped' : 'passed',
                message: failure ? (readAttributes(failure[2]).message ?? failure[1]).slice(0, 400) : null,
            })
        }
        suites.push({
            name: stripPlatform(attributes.name ?? ''),
            timestamp: attributes.timestamp ?? null,
            time: Number(attributes.time ?? 0),
            cases,
        })
    }
    return suites
}

function readAttributes(source) {
    const attributes = {}
    for (const match of source.matchAll(/([\w:-]+)="([^"]*)"/g)) attributes[match[1]] = decodeXml(match[2])
    return attributes
}

function decodeXml(text) {
    return text
        .replaceAll('&lt;', '<')
        .replaceAll('&gt;', '>')
        .replaceAll('&quot;', '"')
        .replaceAll('&apos;', "'")
        .replaceAll('&#10;', ' ')
        .replaceAll('&amp;', '&')
}

// O Gradle sufixa o alvo KMP (`[desktop]`) em suite e caso; no relatorio so
// ha um alvo, entao o sufixo e ruido.
function stripPlatform(text) {
    return text.replace(/\[desktop\]$/, '')
}

// ── Resumo das suites ──────────────────────────────────────────────────────

function summarizeKotlin(suites, index) {
    const classes = suites.map((suite) => {
        const classname = suite.cases[0]?.classname ?? suite.name
        const outer = classname.split('$')[0]
        const file = index.byClass.get(outer) ?? null
        const fqnPackage = outer.slice(0, outer.lastIndexOf('.'))
        return {
            name: classname,
            simpleName: classname.slice(classname.lastIndexOf('.') + 1),
            file: file?.path ?? null,
            sourceSet: file?.sourceSet ?? '—',
            area: KOTLIN_AREAS.find((area) => area.test(`${fqnPackage}.`)).id,
            kind: file?.kind ?? 'unit',
            ...countCases(suite.cases),
            time: suite.time,
            timestamp: suite.timestamp,
            failures: suite.cases.filter((testcase) => testcase.status !== 'passed'),
        }
    })
    const declared = index.files.reduce((sum, file) => sum + file.declared, 0)
    return {
        ran: classes.length > 0,
        classes,
        declared,
        testFiles: index.files.filter((file) => file.declared > 0).length,
        sourceSets: ['commonTest', 'desktopTest'].map((sourceSet) => ({
            id: sourceSet,
            files: index.files.filter((file) => file.sourceSet === sourceSet && file.declared > 0).length,
            declared: index.files.filter((file) => file.sourceSet === sourceSet).reduce((sum, file) => sum + file.declared, 0),
        })),
        areas: groupBy(classes, 'area', KOTLIN_AREAS),
        kinds: groupBy(classes, 'kind', KOTLIN_KINDS),
        ...countCases(suites.flatMap((suite) => suite.cases)),
        time: classes.reduce((sum, entry) => sum + entry.time, 0),
        oldestTimestamp: oldest(suites.map((suite) => suite.timestamp)),
    }
}

function summarizeServer(suites, files) {
    const kinds = [
        { id: 'supertest', label: 'Integração HTTP (supertest)' },
        { id: 'unit', label: 'Unidade' },
    ]
    const classes = suites.map((suite) => {
        const file = files.find((entry) => entry.path === suite.name)
        return {
            name: suite.name,
            simpleName: suite.name.replace(/^test\//, ''),
            file: `server/${suite.name}`,
            sourceSet: 'server/test',
            area: 'server',
            kind: file?.kind ?? 'unit',
            ...countCases(suite.cases),
            time: suite.time,
            timestamp: suite.timestamp,
            failures: suite.cases.filter((testcase) => testcase.status !== 'passed'),
        }
    })
    return {
        ran: classes.length > 0,
        classes,
        declared: files.reduce((sum, file) => sum + file.declared, 0),
        testFiles: files.length,
        kinds: groupBy(classes, 'kind', kinds),
        ...countCases(suites.flatMap((suite) => suite.cases)),
        time: classes.reduce((sum, entry) => sum + entry.time, 0),
        oldestTimestamp: oldest(suites.map((suite) => suite.timestamp)),
    }
}

function countCases(cases) {
    return {
        tests: cases.length,
        passed: cases.filter((testcase) => testcase.status === 'passed').length,
        failed: cases.filter((testcase) => testcase.status === 'failed').length,
        skipped: cases.filter((testcase) => testcase.status === 'skipped').length,
    }
}

// Ordem fixa pela tabela de definicao, nao pela contagem: duas execucoes
// iguais dao a mesma tabela.
function groupBy(classes, key, definitions) {
    return definitions
        .map((definition) => {
            const members = classes.filter((entry) => entry[key] === definition.id)
            return {
                id: definition.id,
                label: definition.label,
                files: members.length,
                tests: members.reduce((sum, entry) => sum + entry.tests, 0),
                failed: members.reduce((sum, entry) => sum + entry.failed, 0),
                skipped: members.reduce((sum, entry) => sum + entry.skipped, 0),
                time: members.reduce((sum, entry) => sum + entry.time, 0),
            }
        })
        .filter((group) => group.files > 0)
}

function oldest(timestamps) {
    const valid = timestamps.filter(Boolean).sort()
    return valid[0] ?? null
}

// ── Cobertura Kotlin (Kover, formato JaCoCo) ───────────────────────────────

const KOTLIN_LAYERS = [
    { id: 'domain', label: 'Domínio', test: (pkg) => pkg.startsWith('com/usagemonitor/domain') },
    { id: 'data', label: 'Dados', test: (pkg) => pkg.startsWith('com/usagemonitor/data') },
    { id: 'viewmodel', label: 'View models', test: (pkg) => pkg.startsWith('com/usagemonitor/presentation/viewmodel') },
    {
        id: 'terminal',
        label: 'Modo terminal',
        test: (pkg) => pkg.startsWith('com/usagemonitor/presentation/terminal') || pkg.startsWith('com/usagemonitor/terminal'),
    },
    { id: 'ui', label: 'Interface Compose', test: (pkg) => pkg.startsWith('com/usagemonitor/presentation') },
    { id: 'update', label: 'Atualização automática', test: (pkg) => pkg.startsWith('com/usagemonitor/update') },
    { id: 'desktop', label: 'Desktop (hosts, janelas, serviços)', test: () => true },
]

function readKover(path, rootDir) {
    if (!existsSync(path)) return null
    const xml = readFileSync(path, 'utf8')
    const files = []
    for (const match of xml.matchAll(/<package\s+name="([^"]*)">([\s\S]*?)<\/package>/g)) {
        const pkg = match[1]
        // O `<sourcefile>` nao traz contador de METODO; ele so existe por
        // `<class>`, que diz de que arquivo veio.
        const methodsByFile = new Map()
        for (const declared of match[2].matchAll(/<class\s+name="[^"]*"\s+sourcefilename="([^"]*)">([\s\S]*?)<\/class>/g)) {
            const method = readCounters(declared[2]).METHOD ?? empty()
            const sum = methodsByFile.get(declared[1]) ?? empty()
            methodsByFile.set(declared[1], { covered: sum.covered + method.covered, missed: sum.missed + method.missed, total: sum.total + method.total })
        }
        for (const sourcefile of match[2].matchAll(/<sourcefile\s+name="([^"]*)">([\s\S]*?)<\/sourcefile>/g)) {
            const counters = readCounters(sourcefile[2])
            if (!counters.LINE) continue
            files.push({
                path: resolveKotlinSource(rootDir, pkg, sourcefile[1]),
                layer: KOTLIN_LAYERS.find((layer) => layer.test(pkg)).id,
                line: counters.LINE,
                branch: counters.BRANCH ?? empty(),
                method: methodsByFile.get(sourcefile[1]) ?? empty(),
            })
        }
    }
    const lastPackage = xml.lastIndexOf('</package>')
    const totals = readCounters(lastPackage === -1 ? xml : xml.slice(lastPackage))
    return {
        generatedAt: statSync(path).mtime.toISOString(),
        totals: { line: totals.LINE ?? empty(), branch: totals.BRANCH ?? empty(), method: totals.METHOD ?? empty(), class: totals.CLASS ?? empty() },
        layers: KOTLIN_LAYERS.map((layer) => sumLayer(layer, files.filter((file) => file.layer === layer.id))).filter((layer) => layer.files > 0),
        files: files.sort(byMissedThenPath),
    }
}

// Os contadores de um bloco sao os ULTIMOS de cada tipo: os de metodo e de
// linha vem antes e o do proprio elemento fecha o bloco.
function readCounters(source) {
    const counters = {}
    for (const match of source.matchAll(/<counter type="(\w+)" missed="(\d+)" covered="(\d+)"\s*\/>/g)) {
        const missed = Number(match[2])
        const covered = Number(match[3])
        counters[match[1]] = { missed, covered, total: missed + covered }
    }
    return counters
}

function resolveKotlinSource(rootDir, pkg, name) {
    for (const sourceSet of ['commonMain', 'desktopMain']) {
        const candidate = join('src', sourceSet, 'kotlin', pkg, name)
        if (existsSync(join(rootDir, candidate))) return toPosix(candidate)
    }
    return `${pkg}/${name} (gerado)`
}

// ── Cobertura do servidor (v8, json-summary) ───────────────────────────────

function readV8Summary(path, rootDir) {
    if (!existsSync(path)) return null
    const summary = JSON.parse(readFileSync(path, 'utf8'))
    const serverRoot = join(rootDir, 'server')
    const files = Object.entries(summary)
        .filter(([key]) => key !== 'total')
        .map(([key, value]) => {
            const path = toPosix(relative(serverRoot, key))
            return {
                path: `server/${path}`,
                layer: path.split('/').slice(0, 2).join('/'),
                line: fromV8(value.lines),
                branch: fromV8(value.branches),
                method: fromV8(value.functions),
            }
        })
    const layerIds = [...new Set(files.map((file) => file.layer))].sort()
    return {
        generatedAt: statSync(path).mtime.toISOString(),
        totals: { line: fromV8(summary.total.lines), branch: fromV8(summary.total.branches), method: fromV8(summary.total.functions) },
        layers: layerIds.map((id) => sumLayer({ id, label: id }, files.filter((file) => file.layer === id))),
        files: files.sort(byMissedThenPath),
    }
}

function fromV8(entry) {
    return { covered: entry.covered, missed: entry.total - entry.covered, total: entry.total }
}

function sumLayer(layer, files) {
    const sum = (key) =>
        files.reduce(
            (acc, file) => ({ covered: acc.covered + file[key].covered, missed: acc.missed + file[key].missed, total: acc.total + file[key].total }),
            empty(),
        )
    return { id: layer.id, label: layer.label, files: files.length, line: sum('line'), branch: sum('branch'), method: sum('method') }
}

function empty() {
    return { covered: 0, missed: 0, total: 0 }
}

function byMissedThenPath(left, right) {
    return right.line.missed - left.line.missed || left.path.localeCompare(right.path)
}

// ── Totais, portoes e lacunas conhecidas ───────────────────────────────────

function combineTotals(data) {
    const line = empty()
    for (const coverage of [data.kover, data.serverCoverage]) {
        if (!coverage) continue
        line.covered += coverage.totals.line.covered
        line.missed += coverage.totals.line.missed
        line.total += coverage.totals.line.total
    }
    return {
        tests: data.kotlin.tests + data.server.tests,
        passed: data.kotlin.passed + data.server.passed,
        failed: data.kotlin.failed + data.server.failed,
        skipped: data.kotlin.skipped + data.server.skipped,
        time: data.kotlin.time + data.server.time,
        line,
        lineSources: [data.kover && 'Kotlin', data.serverCoverage && 'servidor'].filter(Boolean),
    }
}

function evaluateGates(data) {
    const gates = []
    const add = (id, label, level, detail) => gates.push({ id, label, level, detail })

    if (!data.kotlin.ran) add('kotlin-ran', 'Suíte Kotlin executou', 'crit', 'Nenhum XML em build/test-results/desktopTest. A suíte não rodou.')
    else add('kotlin-ran', 'Suíte Kotlin executou', 'ok', `${formatCount(data.kotlin.tests)} testes em ${formatCount(data.kotlin.classes.length)} classes.`)

    if (data.kotlin.ran) {
        add(
            'kotlin-green',
            'Suíte Kotlin sem falhas',
            data.kotlin.failed > 0 ? 'crit' : 'ok',
            data.kotlin.failed > 0 ? `${data.kotlin.failed} testes falharam.` : 'Nenhuma falha.',
        )
        const architecture = data.kotlin.classes.find((entry) => entry.simpleName === 'ArchitectureRulesTest')
        if (!architecture) add('architecture', 'Regras de arquitetura', 'crit', 'ArchitectureRulesTest não aparece nos resultados.')
        else
            add(
                'architecture',
                'Regras de arquitetura',
                architecture.failed > 0 ? 'crit' : 'ok',
                architecture.failed > 0 ? `${architecture.failed} regras quebradas.` : `${architecture.tests} regras verificadas (camadas, tamanho, segredos, textos).`,
            )
        const gap = data.kotlin.declared - data.kotlin.tests
        add(
            'declared',
            'Testes declarados × executados',
            gap === 0 ? 'ok' : 'warn',
            gap === 0
                ? `Os ${formatCount(data.kotlin.declared)} @Test do código rodaram.`
                : `${formatCount(data.kotlin.declared)} @Test no código e ${formatCount(data.kotlin.tests)} executados (diferença ${gap}). Contagem estática por linha: @Test comentado ou gerado por laço desalinha o número.`,
        )
    }

    if (!data.server.ran) add('server', 'Suíte do servidor de time', 'warn', 'Não medida: sem server/test-results/junit.xml.')
    else
        add(
            'server',
            'Suíte do servidor de time',
            data.server.failed > 0 ? 'crit' : 'ok',
            data.server.failed > 0 ? `${data.server.failed} testes falharam.` : `${formatCount(data.server.tests)} testes, nenhuma falha.`,
        )

    add(
        'coverage',
        'Cobertura medida',
        data.kover && data.serverCoverage ? 'ok' : 'warn',
        [
            data.kover ? 'Kotlin pelo Kover' : 'Kotlin não medido (rode com -Pcoverage e koverXmlReport)',
            data.serverCoverage ? 'servidor pelo v8' : 'servidor não medido (npm run test:coverage)',
        ].join(' · ') + '.',
    )

    // Catraca de linhas (docs/planos/cobertura-95-execucao.md): abaixo do piso o CI
    // reprova (`koverVerify`, `thresholds` do vitest); entre o piso e a meta, atencao.
    for (const [id, name, coverage, floor] of [
        ['coverage-goal-kotlin', 'Kotlin', data.kover, data.coverageFloors.kotlin],
        ['coverage-goal-server', 'servidor', data.serverCoverage, data.coverageFloors.server],
    ]) {
        if (!coverage || coverage.totals.line.total === 0) continue
        const percent = (coverage.totals.line.covered / coverage.totals.line.total) * 100
        const shown = formatOneDecimal(percent)
        const level = floor !== null && percent < floor ? 'crit' : percent < COVERAGE_GOAL_PERCENT ? 'warn' : 'ok'
        const floorText = floor === null ? 'piso não encontrado' : `piso ${floor}%`
        add(
            id,
            `Meta de cobertura do ${name}`,
            level,
            level === 'crit'
                ? `${shown}% de linhas, abaixo do ${floorText}: o CI reprova.`
                : `${shown}% de linhas · ${floorText} · meta ${COVERAGE_GOAL_PERCENT}%.`,
        )
    }

    // Suite vermelha faz o Gradle pular o `koverXmlReport` (e o vitest nao
    // reescreve o resumo): o XML da passada anterior continua no disco e
    // descreveria outro codigo. Relatorio mais velho que a suite e desse tipo.
    const stale = [
        ['Kotlin', data.kover, data.kotlin.oldestTimestamp],
        ['servidor', data.serverCoverage, data.server.oldestTimestamp],
    ].filter(([, coverage, runAt]) => coverage && runAt && Date.parse(coverage.generatedAt) < Date.parse(runAt))
    if (stale.length > 0)
        add(
            'coverage-fresh',
            'Cobertura da mesma execução',
            'warn',
            `Cobertura ${stale.map(([name]) => name).join(' e ')} anterior à última execução da suíte: o número é de outra passada.`,
        )

    // No CI o carimbo nao serve de prova: com entradas identicas o Gradle
    // restaura o `desktopTest` do build cache (FROM-CACHE, medido no #451) e o
    // XML traz a hora da execucao original, anterior ao commit. A chave do
    // cache cobre as entradas, entao o resultado vale para este commit.
    if (data.ci) {
        add(
            'fresh',
            'Resultado do código atual',
            'ok',
            `Run do CI do commit ${data.git.sha}. Suíte restaurada do build cache traz o carimbo da execução original; o cache só reaproveita com entradas idênticas.`,
        )
        return gates
    }
    const commitTime = data.git.commitTime ? Date.parse(data.git.commitTime) : null
    const oldestRun = [data.kotlin.oldestTimestamp, data.server.oldestTimestamp].filter(Boolean).sort()[0]
    if (oldestRun && commitTime && Date.parse(oldestRun) < commitTime)
        add('fresh', 'Resultado do código atual', 'warn', `Há resultado de ${oldestRun}, anterior ao commit ${data.git.sha}. Rode a suíte de novo.`)
    else
        add(
            'fresh',
            'Resultado do código atual',
            data.git.dirty ? 'warn' : 'ok',
            data.git.dirty
                ? 'A árvore tem alterações não commitadas: o resultado vale para elas, não para o commit.'
                : `Execução posterior ao commit ${data.git.sha}.`,
        )
    return gates
}

// Fora do alcance da suite por construcao. Cada linha cita de onde vem a
// afirmacao; muda quando a fonte mudar.
const BLIND_SPOTS = [
    {
        title: 'Sistema de janelas real',
        detail: 'Nenhum teste abre janela de verdade: posição, alfa, recorte e foco são medidos à mão em cada plataforma.',
        source: 'CLAUDE.md, "CI e testes"; CONTRIBUTING.md, "Platform reality" (#342)',
    },
    {
        title: 'Modo /UPDATE do instalador NSIS',
        detail: 'Fora do allTests. Só o job installer-windows do CI o exercita, e só quando o instalador muda.',
        source: '.github/workflows/ci.yml, job installer-windows',
    },
    {
        title: 'Suíte no Linux não bloqueia',
        detail: 'O job desktop-linux roda com continue-on-error: vermelho ali não segura o merge.',
        source: '.github/workflows/ci.yml, job desktop-linux (#342)',
    },
    {
        title: 'APIs externas de verdade',
        detail: 'Os testes de dados respondem com MockEngine; nenhum chama Anthropic, MiniMax, Cursor ou outro fornecedor.',
        source: 'src/commonTest e src/desktopTest, uso de MockEngine',
    },
    {
        title: 'Excluídos do Kover por nome',
        detail: 'Main.kt (arranque); AppGraph e AppViewModels (só montam com credenciais reais e HTTP de verdade); DashboardScreen e a grade responsiva (só testes e capturas os chamam). Nenhum deles conta no percentual.',
        source: 'build.gradle.kts, bloco kover; docs/planos/cobertura-95-execucao.md',
    },
]

// ── Utilitarios ────────────────────────────────────────────────────────────

function readGit(rootDir) {
    const git = (...gitArgs) => {
        try {
            return execFileSync('git', gitArgs, { cwd: rootDir, encoding: 'utf8' }).trim()
        } catch {
            return null
        }
    }
    return {
        sha: git('rev-parse', '--short', 'HEAD'),
        branch: git('rev-parse', '--abbrev-ref', 'HEAD'),
        commitTime: git('log', '-1', '--format=%cI'),
        subject: git('log', '-1', '--format=%s'),
        dirty: Boolean(git('status', '--porcelain', '--', 'src', 'server/src', 'server/test')),
    }
}

// Os tokens entram inline, lidos do design system na hora: o relatorio
// acompanha a paleta sem copia que envelheca. `fonts.css` fica de fora porque
// e um `@import` remoto, que o HTML faz por `<link>`.
function readTokens(rootDir) {
    return ['colors', 'typography', 'spacing', 'shape', 'motion', 'base']
        .map((name) => readFileSync(join(rootDir, 'docs/design-system/tokens', `${name}.css`), 'utf8'))
        .join('\n')
}

function walk(dir, suffix) {
    if (!existsSync(dir)) return []
    const found = []
    for (const entry of readdirSync(dir, { withFileTypes: true })) {
        const path = join(dir, entry.name)
        if (entry.isDirectory()) found.push(...walk(path, suffix))
        else if (entry.name.endsWith(suffix)) found.push(path)
    }
    return found.sort()
}

// Mesmo formato do HTML: milhar com espaco fino.
// Os pisos vivem onde o CI os aplica; o relatorio so os le, para nao haver
// segundo dono do numero.
function readCoverageFloors(rootDir) {
    const readNumber = (path, pattern) => {
        try {
            const match = readFileSync(join(rootDir, path), 'utf8').match(pattern)
            return match ? Number(match[1]) : null
        } catch {
            return null
        }
    }
    return {
        kotlin: readNumber('build.gradle.kts', /val coverageLineFloorPercent = (\d+)/),
        server: readNumber('server/vitest.config.ts', /thresholds:\s*\{\s*lines:\s*(\d+)/),
    }
}

function formatOneDecimal(value) {
    return (Math.floor(value * 10) / 10).toFixed(1).replace('.', ',')
}

function formatCount(value) {
    return value.toLocaleString('pt-BR').replaceAll('.', '\u2009')
}

function toPosix(path) {
    return path.split(sep).join('/')
}

function renderConsole(data, htmlPath) {
    const percent = (counter) => (counter && counter.total > 0 ? `${((100 * counter.covered) / counter.total).toFixed(1)}%` : '--')
    const lines = [
        `Relatório: ${htmlPath}`,
        `Testes: ${data.totals.tests} (${data.totals.passed} passaram, ${data.totals.failed} falharam, ${data.totals.skipped} ignorados)`,
        `  Kotlin: ${data.kotlin.tests} · servidor: ${data.server.tests}`,
        `Cobertura de linhas: total ${percent(data.totals.line)} · Kotlin ${percent(data.kover?.totals.line)} · servidor ${percent(data.serverCoverage?.totals.line)}`,
        'Portões:',
        ...data.gates.map((gate) => `  [${gate.level}] ${gate.label}: ${gate.detail}`),
        '',
    ]
    return lines.join('\n')
}

// Ponto de entrada no FIM do arquivo: as tabelas de area, tipo e camada sao
// `const` de topo, e chama-las antes da declaracao cai na zona morta temporal.
main()

function main() {
    const args = parseArgs(process.argv.slice(2))
    const root = args.root
    const outDir = join(root, args.out)

    const kotlinFiles = indexKotlinTests(root)
    const serverFiles = indexServerTests(root)
    const kotlinSuites = readJUnitDir(join(root, 'build/test-results/desktopTest'))
    const serverSuites = readJUnitFile(join(root, 'server/test-results/junit.xml'))

    const report = {
        generatedAt: new Date().toISOString(),
        git: readGit(root),
        platform: args.platform,
        ci: args.ci,
        kotlin: summarizeKotlin(kotlinSuites, kotlinFiles),
        server: summarizeServer(serverSuites, serverFiles),
        kover: readKover(join(root, 'build/reports/kover/report.xml'), root),
        serverCoverage: readV8Summary(join(root, 'server/coverage/coverage-summary.json'), root),
        coverageFloors: readCoverageFloors(root),
    }
    report.totals = combineTotals(report)
    report.gates = evaluateGates(report)
    report.blindSpots = BLIND_SPOTS

    mkdirSync(outDir, { recursive: true })
    writeFileSync(join(outDir, 'report.json'), JSON.stringify(report, null, 2))
    writeFileSync(join(outDir, 'index.html'), renderHtml(report, readTokens(root)))
    process.stdout.write(renderConsole(report, join(args.out, 'index.html')))

    // Saida 1 quando a suite falhou ou nao rodou: quem chama (a skill, um hook)
    // decide pelo codigo, sem reler o HTML.
    process.exit(report.gates.some((gate) => gate.level === 'crit') ? 1 : 0)
}
