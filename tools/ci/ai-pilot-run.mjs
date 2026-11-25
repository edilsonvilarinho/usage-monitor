// Runner do piloto de IA (#653, A03c): lê argv, semeia os dados, roda o modo dry (só captura) ou paid (roteiro com OpenRouter) e grava a evidência.
// Funções de lógica recebem dependências por parâmetro; só `main` toca em processo, disco e rede.
import { execFile, spawn } from 'node:child_process'
import { readFile, writeFile, mkdir } from 'node:fs/promises'
import { join, resolve } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'
import { checkKey, createBudget, createOpenRouterClient, confirmReport, runScript, validateAction, LIMITS, MODEL } from './ai-pilot.mjs'
import { createScreenDriver } from './ai-pilot-driver.mjs'

const REPO = resolve(fileURLToPath(new URL('../..', import.meta.url)))
const CASES_FILE = join(REPO, 'tools', 'real-app', 'pilot-cases.json')
const RULES_FILE = join(REPO, 'tools', 'real-app', 'pilot-rules.md')
const SEED_SCRIPT = join(REPO, 'tools', 'real-app', 'seed.ps1')
const DRIVER_SCRIPT = join(REPO, 'tools', 'real-app', 'screen-driver.ps1')
const KEY_URL = 'https://openrouter.ai/api/v1/key'
const DRIVER_TIMEOUT_MS = 120000
const MAX_CONFIRMATIONS = 3
const MODES = ['dry', 'paid']
const SIDES = ['bad', 'good']
const FLAGS = { '--mode': 'mode', '--case': 'caseId', '--side': 'side', '--exe': 'exe', '--data-dir': 'dataDir', '--out': 'out' }
const EXIT_OK = 0
const EXIT_ERROR = 1
const EXIT_USAGE = 2
const EXIT_REFUSED = 3
const EXIT_UNMEASURED = 4
const REFUSED_STOPS = ['key-refused', 'script-budget', 'pilot-budget', 'prompt-limit', 'cost-unknown']
const ERROR_STOPS = ['driver-error', 'client-error']
const LEDGER_FILE = join(REPO, 'tools', 'real-app', 'pilot-ledger.json')
// Chaves de topo do pilot-cases.json que não são casos executáveis.
const NON_CASE_KEYS = ['script', 'coverage', 'coverageLabels']
const EPSILON = 1e-9

// Pior caso do lote: gasto já no ledger + jobs x teto do roteiro. Recusa se passa do teto do piloto.
export function planBudget({ ledgerUsd, jobs, maxScriptUsd, maxPilotUsd }) {
    const worstCaseUsd = ledgerUsd + jobs * maxScriptUsd
    return { ok: worstCaseUsd <= maxPilotUsd + EPSILON, worstCaseUsd }
}

// Decide caso, lado, versão e chave antes de qualquer I/O. Recusa sai 2, sem rede nem driver.
export function planRun({ caseId, side, mode }, cases, env) {
    const item = cases[caseId]
    if (NON_CASE_KEYS.includes(caseId) || !item) return { ok: false, error: `caso desconhecido: ${caseId}`, exit: EXIT_USAGE }
    if (mode === 'paid' && !env.OPENROUTER_API_KEY) return { ok: false, error: 'OPENROUTER_API_KEY ausente', exit: EXIT_USAGE }
    return { ok: true, item, version: side === 'bad' ? item.badTag : item.goodTag }
}

// Valor de `--jobs`: inteiro maior que 0; ausente, zero, fracionário ou texto vira null.
export function parseJobs(argv) {
    const index = argv.indexOf('--jobs')
    if (index === -1) return null
    const jobs = Number(argv[index + 1])
    return Number.isInteger(jobs) && jobs >= 1 ? jobs : null
}

// Argumentos da linha de comando. Opção ausente, valor fora da lista ou flag desconhecida é erro.
export function parseArgs(argv) {
    const options = {}
    for (let k = 0; k < argv.length; k += 2) {
        const flag = argv[k]
        const field = FLAGS[flag]
        if (!field) return { ok: false, error: `opção desconhecida: ${flag}` }
        if (k + 1 >= argv.length) return { ok: false, error: `valor ausente para ${flag}` }
        options[field] = argv[k + 1]
    }
    for (const flag of Object.keys(FLAGS)) {
        if (!(FLAGS[flag] in options)) return { ok: false, error: `opção obrigatória ausente: ${flag}` }
    }
    if (!MODES.includes(options.mode)) return { ok: false, error: '--mode deve ser dry ou paid' }
    if (!SIDES.includes(options.side)) return { ok: false, error: '--side deve ser bad ou good' }
    return { ok: true, options }
}

// Modo dry: valida o probe inteiro antes de executar; captura antes e depois de cada ação.
export async function runDry({ driver, probe }) {
    const screen = await driver.ready()
    const checked = probe.map((action) => {
        const { tool, ...args } = action ?? {}
        const verdict = validateAction(tool, args, screen)
        if (!verdict.ok) throw new Error(`ação inválida no probe: ${verdict.error}`)
        return verdict.action
    })
    const shots = [{ step: 0, action: 'screenshot', image: await driver.screenshot() }]
    for (const action of checked) {
        if (action.tool !== 'screenshot') await driver.perform(action)
        shots.push({ step: shots.length, action: action.tool, image: await driver.screenshot() })
    }
    return shots
}

// Modo paid: a chave é checada antes de qualquer cliente; cada relatório tem seu orçamento de roteiro e no máximo três veredictos.
export async function runPaid({ fetchKey, makeClient, driver, script, rules, screen, coverage = [], markConfirming = () => {} }) {
    let keyBody
    try {
        keyBody = await fetchKey()
    } catch {
        return { stop: 'key-refused', reason: 'key-unreadable' }
    }
    const check = checkKey(keyBody)
    if (!check.ok) return { stop: 'key-refused', reason: check.reason }
    const client = makeClient()
    const budget = createBudget({ pilotSpentUsd: check.usage })
    const run = await runScript({ client, driver, script, rules, screen, budget, coverage })
    const reports = []
    let confirmations = 0
    for (const [index, item] of run.reports.entries()) {
        const shot = `evidence/${String(index + 1).padStart(3, '0')}.jpg`
        if (confirmations >= MAX_CONFIRMATIONS) {
            reports.push({ ...item, shot, confirmed: null, why: 'confirm-cap' })
            continue
        }
        confirmations += 1
        budget.startConversation()
        markConfirming(true)
        const verdict = await confirmReport({ client, report: item, rules, budget })
        markConfirming(false)
        reports.push({ ...item, shot, confirmed: verdict.confirmed, why: verdict.why })
    }
    const result = {
        stop: run.stop,
        steps: run.steps,
        costUsd: budget.pilotUsd - check.usage,
        keyUsageBefore: check.usage,
        keyUsageAfter: usageOf(await readKeyAfter(fetchKey)),
        checks: run.checks,
        coverage: run.coverage,
        missing: run.missing,
        reports,
    }
    if (run.detail !== undefined) result.detail = run.detail
    return result
}

async function readKeyAfter(fetchKey) {
    try {
        return await fetchKey()
    } catch {
        return null
    }
}

// Gasto da chave lido do corpo; null se não veio número.
function usageOf(body) {
    const usage = body?.data?.usage
    return typeof usage === 'number' && Number.isFinite(usage) ? usage : null
}

// Separa a evidência (base64) do JSON: o resultado fica sem imagem e cada captura vira um arquivo.
export function planEvidence(result) {
    const files = []
    const reports = (result.reports ?? []).map(({ screenshot, ...rest }) => {
        if (typeof screenshot === 'string') files.push({ rel: rest.shot, base64: screenshot })
        return rest
    })
    return { files, result: { ...result, reports } }
}

const cell = (text) => String(text).split('|').join('\\|').replace(/\r?\n/g, ' ')
const money = (value) => (typeof value === 'number' ? String(value) : 'não medido')
const verdictLabel = (item) => {
    if (item.confirmed === true) return 'confirmado'
    if (item.confirmed === false) return 'não confirmado'
    return `não medido: ${item.why}`
}

// Cobertura em três estados: coberto com as capturas A e B, impossível com as capturas A e B e o motivo, ou não coberto.
const coverageLabel = (cover) => {
    if (!cover) return 'não coberto'
    if (cover.possible === false) return `impossível (capturas ${cover.before} → ${cover.after}): ${cell(cover.seen)}`
    return `coberto (capturas ${cover.before} → ${cover.after})`
}

// Relatório em português; nunca imprime base64. Resultado de chave recusada não tem passos nem custo: vira zero.
export function renderReport(input, meta) {
    const result = { steps: 0, costUsd: 0, reports: [], checks: [], coverage: [], ...input }
    const lines = [
        `# Piloto de IA: ${meta.caseId}`,
        '',
        `- Caso: ${meta.caseId}`,
        `- Lado: ${meta.side}`,
        `- Versão: ${meta.version}`,
        `- Parada: ${result.stop}`,
        `- Passos: ${result.steps}`,
        `- Custo: ${Number(result.costUsd).toFixed(4)} USD`,
        `- Gasto da chave antes: ${money(result.keyUsageBefore)}`,
        `- Gasto da chave depois: ${money(result.keyUsageAfter)}`,
        '',
        '## Regras',
        '',
    ]
    if (result.checks.length === 0) lines.push('Nenhum check registrado.')
    for (const check of result.checks) lines.push(`- Regra ${cell(check.rule)}: ${cell(check.verdict)} (captura ${cell(check.shot)}): ${cell(check.seen)}`)
    lines.push('', '## Cobertura', '')
    const covers = new Map(result.coverage.map((cover) => [cover.item, cover]))
    for (const item of meta.coverage ?? []) lines.push(`- ${cell((meta.coverageLabels ?? {})[item] ?? item)}: ${coverageLabel(covers.get(item))}`)
    lines.push('', '## Achados', '')
    if (result.reports.length === 0) lines.push('Nenhum achado registrado.')
    for (const item of result.reports) {
        lines.push(`### ${cell(item.title)}`, '', `- Regra: ${cell(item.rule)}`, `- Lido: ${cell(item.seen)}`, `- Pedido: ${cell(item.expected)}`, `- Confirmação: ${verdictLabel(item)}`, `- Captura: ${cell(item.shot)}`, `- Captura nº ${cell(item.shotNumber)}`, '')
    }
    return lines.join('\n') + '\n'
}

// Ambiente do processo filho: a chave da OpenRouter nunca chega ao driver nem ao seed.
export function childEnv(env) {
    const copy = { ...env }
    delete copy.OPENROUTER_API_KEY
    return copy
}

// Código de saída: só done é 0; recusa de orçamento ou chave é 3; falha do driver ou do cliente é 1; o resto é 4 (não medido).
export function exitCodeOf(stop) {
    if (stop === 'done') return EXIT_OK
    if (REFUSED_STOPS.includes(stop)) return EXIT_REFUSED
    if (ERROR_STOPS.includes(stop)) return EXIT_ERROR
    // done-incomplete, max-steps, max-actions, no-tool-call e stuck: roteiro sem done aceito, resultado não medido.
    return EXIT_UNMEASURED
}

function seedData({ dataDir, version, scenario, env }) {
    return new Promise((done, fail) => {
        const args = ['-NoProfile', '-File', SEED_SCRIPT, '-DataDir', dataDir, '-Version', version, '-Scenario', scenario]
        execFile('pwsh', args, { env: childEnv(env) }, (error) => {
            if (error) fail(new Error(`seed.ps1 falhou: ${error.message}`))
            else done()
        })
    })
}

function spawnDriver({ exe, dataDir, out, env }) {
    const args = ['-NoProfile', '-File', DRIVER_SCRIPT, '-Exe', exe, '-DataDir', dataDir, '-OutDir', out]
    const child = spawn('pwsh', args, { env: childEnv(env), stdio: ['pipe', 'pipe', 'inherit'] })
    return createScreenDriver({ child, timeoutMs: DRIVER_TIMEOUT_MS })
}

async function writeEvidence(out, files) {
    for (const file of files) {
        const path = join(out, file.rel)
        await mkdir(join(path, '..'), { recursive: true })
        await writeFile(path, Buffer.from(file.base64, 'base64'))
    }
}

async function runMode({ mode, driver, item, env, out, caseId, side, version, cases, rules, io }) {
    if (mode === 'dry') {
        const shots = await runDry({ driver, probe: item.probe })
        await writeEvidence(out, shots.map((shot) => ({ rel: `evidence/${String(shot.step).padStart(3, '0')}.jpg`, base64: shot.image })))
        const summary = dryResult(shots)
        await writeFile(join(out, 'result.json'), JSON.stringify(summary, null, 2))
        await writeFile(join(out, 'report.md'), renderDryReport(summary, { caseId, side, version }))
        return EXIT_OK
    }
    const apiKey = env.OPENROUTER_API_KEY
    const screen = await driver.ready()
    const fetchKey = async () => (await io.fetch(KEY_URL, { headers: { Authorization: `Bearer ${apiKey}` } })).json()
    const turns = []
    const confirming = { on: false }
    const makeClient = () => recordingClient(createOpenRouterClient({ fetch: io.fetch, apiKey, model: MODEL }), (turn) => turns.push({ ...turn, confirmation: confirming.on }))
    const markConfirming = (on) => { confirming.on = on }
    const result = await runPaid({ fetchKey, makeClient, driver, script: cases.script, rules, screen, coverage: cases.coverage ?? [], markConfirming })
    const plan = planEvidence(result)
    await writeEvidence(out, plan.files)
    await writeFile(join(out, 'result.json'), JSON.stringify(plan.result, null, 2))
    await writeFile(join(out, 'transcript.jsonl'), transcriptJsonl(turns))
    await writeFile(join(out, 'report.md'), renderReport(result, { caseId, side, version, coverage: cases.coverage, coverageLabels: cases.coverageLabels }))
    return exitCodeOf(result.stop)
}

// `--plan-budget --jobs n`: confere o pior caso do lote contra o teto do piloto, sem rede e sem driver. Sai 3 se passa do teto.
async function planMain(argv, io) {
    const jobs = parseJobs(argv)
    if (jobs === null) {
        io.stderr('uso: --plan-budget --jobs <inteiro maior que 0>\n')
        return EXIT_USAGE
    }
    const ledger = await io.readLedger()
    const plan = planBudget({ ledgerUsd: ledger.spentUsd, jobs, maxScriptUsd: LIMITS.maxScriptUsd, maxPilotUsd: LIMITS.maxPilotUsd })
    io.stdout(`pior caso: ${plan.worstCaseUsd.toFixed(4)} USD de ${LIMITS.maxPilotUsd} USD\n`)
    return plan.ok ? EXIT_OK : EXIT_REFUSED
}

// Efeitos reais por padrão; os testes trocam por fakes via `deps`.
const DEFAULT_DEPS = {
    seed: seedData,
    spawnDriver,
    fetch,
    readCases: async () => JSON.parse(await readFile(CASES_FILE, 'utf8')),
    readRules: () => readFile(RULES_FILE, 'utf8'),
    readLedger: async () => JSON.parse(await readFile(LEDGER_FILE, 'utf8')),
    stdout: (text) => process.stdout.write(text),
    stderr: (text) => process.stderr.write(text),
}

// Devolve o código de saída; não chama `process.exit`. Seed e driver recebem o ambiente sem a chave da OpenRouter.
export async function main(argv, env, deps = {}) {
    const io = { ...DEFAULT_DEPS, ...deps }
    if (argv[0] === '--plan-budget') return planMain(argv.slice(1), io)
    const parsed = parseArgs(argv)
    if (!parsed.ok) {
        io.stderr(`${parsed.error}\n`)
        return EXIT_USAGE
    }
    const { mode, caseId, side, exe, dataDir, out } = parsed.options
    const cases = await io.readCases()
    const plan = planRun({ caseId, side, mode }, cases, env)
    if (!plan.ok) {
        io.stderr(`${plan.error}\n`)
        return plan.exit
    }
    const { item, version } = plan
    const childEnvironment = childEnv(env)
    let driver = null
    try {
        await mkdir(out, { recursive: true })
        await io.seed({ dataDir, version, scenario: item.scenario, env: childEnvironment })
        driver = io.spawnDriver({ exe, dataDir, out, env: childEnvironment })
        const rules = mode === 'paid' ? await io.readRules() : null
        return await runMode({ mode, driver, item, env, out, caseId, side, version, cases, rules, io })
    } catch (error) {
        io.stderr(`${error instanceof Error ? error.message : String(error)}\n`)
        return EXIT_ERROR
    } finally {
        if (driver) await driver.close()
    }
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
    main(process.argv.slice(2), process.env).then((code) => { process.exitCode = code })
}


// Envolve o cliente: cada resposta vai para `onTurn` sem imagem nem chave; a resposta segue intacta.
export function recordingClient(client, onTurn) {
    let turn = 0
    return {
        complete: async (request) => {
            const response = await client.complete(request)
            turn += 1
            const message = response.message ?? {}
            const toolCalls = (message.tool_calls ?? []).map((call) => ({ name: call.function?.name, arguments: call.function?.arguments }))
            onTurn({ turn, text: message.content ?? '', toolCalls, usage: response.usage ?? null })
            return response
        },
    }
}

// Resumo do dry: passos e ações, sem imagem; as capturas são os arquivos gravados em evidence/.
export function dryResult(shots) {
    return {
        stop: 'done',
        steps: shots.length - 1,
        actions: shots.slice(1).map((shot) => shot.action),
        captures: shots.map((shot) => `evidence/${String(shot.step).padStart(3, '0')}.jpg`),
    }
}

export function renderDryReport(result, meta) {
    const lines = [
        `# Piloto de IA (dry): ${meta.caseId}`,
        '',
        `- Caso: ${meta.caseId}`,
        `- Lado: ${meta.side}`,
        `- Versão: ${meta.version}`,
        `- Parada: ${result.stop}`,
        `- Passos: ${result.steps}`,
        '',
        '## Capturas',
        '',
        ...result.captures.map((capture) => `- ${cell(capture)}`),
    ]
    return lines.join('\n') + '\n'
}

// Uma linha JSON por turno; confirmações vêm marcadas para separar do laço.
export function transcriptJsonl(turns) {
    return turns.map((turn) => `${JSON.stringify(turn)}\n`).join('')
}
