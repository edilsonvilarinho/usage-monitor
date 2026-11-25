// Loop do piloto de IA de CI (#653, A02). Puro: cliente e driver entram por injeção; nada aqui toca rede, disco ou ambiente.
// Orçamento falha fechado: uso sem custo bloqueia o piloto inteiro, nunca é estimado.

export const LIMITS = Object.freeze({
    maxSteps: 60,
    maxPromptTokens: 100000,
    promptMargin: 4000,
    maxScriptUsd: 0.2,
    maxPilotUsd: 5,
    reserveUsd: 0.02,
    maxOutputTokens: 2048,
    maxWaitMs: 5000,
    maxTypeChars: 200,
    maxActions: 150,
    maxActionsPerTurn: 20,
    maxRepeatStreak: 2,
    maxRepeatRefusals: 6,
    maxTotalWaitMs: 120000,
})

// Tolerância para soma de decimais em dólar: exatamente no limite continua permitido.
const EPSILON = 1e-9
// Regras do produto numeradas de 1 a 9 (pilot-rules.md).
const RULE_COUNT = 9
// Recusas de done antes de aceitar o roteiro incompleto.
const MAX_DONE_REFUSALS = 5
const VERDICTS = ['ok', 'violated', 'not_seen']

const ENDPOINT = 'https://openrouter.ai/api/v1/chat/completions'

// Único modelo que o piloto aceita: Haiku 5.5 via OpenRouter (#653).
export const MODEL = 'anthropic/claude-haiku-5.5'

const KEYS = ['Escape', 'Enter', 'Tab', 'Space', 'Up', 'Down', 'Left', 'Right', 'Home', 'End', 'PageUp', 'PageDown']

const INT = { type: 'integer' }
const TEXT = { type: 'string' }
const RULE = { type: 'integer', minimum: 1, maximum: RULE_COUNT }

// Schema de ferramenta no formato de function calling da OpenAI.
function tool(name, description, properties = {}, required = []) {
    return { type: 'function', function: { name, description, parameters: { type: 'object', properties, required } } }
}

export const TOOLS = [
    tool('screenshot', 'Captura a tela atual; a captura ganha o próximo número.'),
    tool('zoom', 'Amplia um retângulo (x, y, w, h inteiros) para ler texto pequeno; vira captura numerada.', { x: INT, y: INT, w: INT, h: INT }, ['x', 'y', 'w', 'h']),
    tool('click', 'Clique esquerdo na coordenada (0-based).', { x: INT, y: INT }, ['x', 'y']),
    tool('drag', 'Arrasta da origem ao destino.', { x1: INT, y1: INT, x2: INT, y2: INT }, ['x1', 'y1', 'x2', 'y2']),
    tool('move', 'Move o ponteiro (0-based).', { x: INT, y: INT }, ['x', 'y']),
    tool('type', 'Digita texto.', { text: TEXT }, ['text']),
    tool('key', 'Pressiona uma tecla da lista permitida.', { name: { type: 'string', enum: KEYS } }, ['name']),
    tool('wait', 'Espera em milissegundos.', { ms: INT }, ['ms']),
    tool('check', 'Veredito de uma regra. seen é o texto literal da captura; not_seen dispensa shot.', { rule: RULE, shot: INT, seen: TEXT, verdict: { type: 'string', enum: VERDICTS } }, ['rule', 'seen', 'verdict']),
    tool('cover', 'Marca um item de cobertura com duas capturas inteiras, before < after, e a ação entre elas: click para minimize e restore; move, click ou drag para os demais. possible false marca impossível: exige as mesmas capturas e a mesma ação, e aceita imagens iguais.', { item: TEXT, before: INT, after: INT, seen: TEXT, possible: { type: 'boolean' } }, ['item', 'seen']),
    tool('report', 'Registra um achado de regra com check violated.', { title: TEXT, rule: RULE, shot: INT, seen: TEXT, expected: TEXT }, ['title', 'rule', 'shot', 'seen', 'expected']),
    tool('done', 'Encerra o roteiro com um resumo.', { summary: TEXT }, ['summary']),
]

// Ferramenta única do veredito do relatório (confirmReport).
const VERDICT_TOOL = tool('verdict', 'Veredito sobre o achado.', { confirmed: { type: 'boolean' }, why: TEXT }, ['confirmed', 'why'])
const VERDICT_CHOICE = Object.freeze({ type: 'function', function: { name: 'verdict' } })

const fail = (reason) => ({ ok: false, reason })

// Uso gasto em dólar: número finito >= 0.
const isSpentUsd = (value) => typeof value === 'number' && Number.isFinite(value) && value >= 0

// Confere a resposta de GET /api/v1/key. Ordem fixa das razões.
export function checkKey(body) {
    const data = body && typeof body === 'object' ? body.data : undefined
    if (!data || typeof data !== 'object') return fail('invalid-response')
    const limit = data.limit
    if (limit === undefined || (limit !== null && (typeof limit !== 'number' || !Number.isFinite(limit)))) {
        return fail('invalid-response')
    }
    if (!isSpentUsd(data.usage)) return fail('invalid-response')
    if (limit !== null) {
        if (!Number.isFinite(data.limit_remaining)) return fail('invalid-response')
        if (data.limit_remaining < LIMITS.maxScriptUsd) return fail('remaining-too-low')
    }
    if (data.usage + LIMITS.reserveUsd > LIMITS.maxPilotUsd + EPSILON) return fail('pilot-budget')
    return { ok: true, usage: data.usage }
}

function isValidUsage(usage) {
    if (!usage || typeof usage !== 'object') return false
    return [usage.cost, usage.prompt_tokens, usage.completion_tokens].every((value) => Number.isFinite(value) && value >= 0)
}

// Orçamento do roteiro (scriptUsd, zerado por roteiro) e do piloto (pilotUsd, acumulado).
export function createBudget({ pilotSpentUsd = 0 } = {}) {
    const budget = {
        scriptUsd: 0,
        pilotUsd: pilotSpentUsd,
        blocked: false,
        lastPromptTokens: 0,
        lastCompletionTokens: 0,
        canCall() {
            if (budget.blocked) return 'cost-unknown'
            if (budget.scriptUsd + LIMITS.reserveUsd > LIMITS.maxScriptUsd + EPSILON) return 'script-budget'
            if (budget.pilotUsd + LIMITS.reserveUsd > LIMITS.maxPilotUsd + EPSILON) return 'pilot-budget'
            const footprint = budget.lastPromptTokens + budget.lastCompletionTokens + LIMITS.promptMargin
            if (footprint > LIMITS.maxPromptTokens) return 'prompt-limit'
            return null
        },
        charge(usage) {
            if (!isValidUsage(usage)) {
                budget.blocked = true
                return
            }
            budget.scriptUsd += usage.cost
            budget.pilotUsd += usage.cost
            budget.lastPromptTokens = usage.prompt_tokens
            budget.lastCompletionTokens = usage.completion_tokens
        },
        // Início de roteiro: zera o custo do roteiro e o tamanho do prompt.
        startScript() {
            budget.scriptUsd = 0
            budget.startConversation()
        },
        // Nova conversa (confirmação): zera só o tamanho do prompt; o custo continua no roteiro.
        startConversation() {
            budget.lastPromptTokens = 0
            budget.lastCompletionTokens = 0
        },
    }
    return budget
}

const ok = (action) => ({ ok: true, action })
const bad = (error) => ({ ok: false, error })

function inScreen(x, y, screen) {
    return Number.isInteger(x) && Number.isInteger(y) && x >= 0 && y >= 0 && x < screen.width && y < screen.height
}

function textOf(value) {
    return typeof value === 'string' ? value.trim() : ''
}

const isRule = (value) => Number.isInteger(value) && value >= 1 && value <= RULE_COUNT
const isShot = (value) => Number.isInteger(value) && value >= 1

// Retângulo inteiro dentro da tela, com lados de 16 px até metade da tela.
function zoomArea({ x, y, w, h }, screen) {
    if (![x, y, w, h].every(Number.isInteger)) return false
    if (w < 16 || h < 16 || w > screen.width / 2 || h > screen.height / 2) return false
    return x >= 0 && y >= 0 && x + w <= screen.width && y + h <= screen.height
}

// Valida a ação vinda do modelo. Campos extras são descartados; o driver só recebe o que passou aqui.
export function validateAction(name, args, screen) {
    const input = args && typeof args === 'object' ? args : {}
    switch (name) {
        case 'screenshot':
            return ok({ tool: 'screenshot' })
        case 'zoom':
            if (!zoomArea(input, screen)) return bad('campo inválido em zoom: x, y, w e h inteiros; retângulo dentro da tela; 16 <= w,h <= metade da tela')
            return ok({ tool: 'zoom', x: input.x, y: input.y, w: input.w, h: input.h })
        case 'click':
        case 'move':
            if (!inScreen(input.x, input.y, screen)) return bad(`campo inválido em ${name}: x e y inteiros dentro da tela`)
            return ok({ tool: name, x: input.x, y: input.y })
        case 'drag':
            if (!inScreen(input.x1, input.y1, screen) || !inScreen(input.x2, input.y2, screen)) {
                return bad('campo inválido em drag: coordenadas inteiras dentro da tela')
            }
            return ok({ tool: 'drag', x1: input.x1, y1: input.y1, x2: input.x2, y2: input.y2 })
        case 'wait':
            if (!Number.isInteger(input.ms) || input.ms < 0 || input.ms > LIMITS.maxWaitMs) {
                return bad(`campo inválido em wait: ms inteiro entre 0 e ${LIMITS.maxWaitMs}`)
            }
            return ok({ tool: 'wait', ms: input.ms })
        case 'type':
            if (typeof input.text !== 'string' || input.text.length === 0 || input.text.length > LIMITS.maxTypeChars) {
                return bad(`campo inválido em type: texto de 1 a ${LIMITS.maxTypeChars} caracteres`)
            }
            return ok({ tool: 'type', text: input.text })
        case 'key':
            if (!KEYS.includes(input.name)) return bad(`tecla fora da lista permitida: ${String(input.name)}`)
            return ok({ tool: 'key', name: input.name })
        case 'check': {
            const seen = textOf(input.seen)
            if (!isRule(input.rule) || !VERDICTS.includes(input.verdict) || !seen) {
                return bad('campo inválido em check: rule de 1 a 9, verdict ok|violated|not_seen, seen não vazio')
            }
            if (input.verdict !== 'not_seen' && !isShot(input.shot)) return bad('check exige shot (número da captura) fora de not_seen')
            return ok({ tool: 'check', rule: input.rule, shot: isShot(input.shot) ? input.shot : undefined, seen, verdict: input.verdict })
        }
        case 'cover': {
            const item = textOf(input.item)
            const seen = textOf(input.seen)
            if (!item || !seen) return bad('campo inválido em cover: item e seen não vazios')
            const impossible = input.possible === false
            const tail = impossible ? IMPOSSIBLE_TAIL : ''
            if (!isShot(input.before) || !isShot(input.after) || input.before >= input.after) {
                return bad(`cover exige before e after (números de captura) com before < after${tail}`)
            }
            const action = { tool: 'cover', item, before: input.before, after: input.after, seen }
            if (impossible) action.possible = false
            return ok(action)
        }
        case 'report': {
            const title = textOf(input.title)
            const seen = textOf(input.seen)
            const expected = textOf(input.expected)
            if (!title || !seen || !expected || !isRule(input.rule) || !isShot(input.shot)) {
                return bad('campo inválido em report: title, seen e expected não vazios; rule de 1 a 9; shot número da captura')
            }
            return ok({ tool: 'report', title, rule: input.rule, shot: input.shot, seen, expected })
        }
        case 'done': {
            const summary = textOf(input.summary)
            if (!summary) return bad('campo inválido em done: summary não vazio')
            return ok({ tool: 'done', summary })
        }
        default:
            return bad(`ferramenta desconhecida: ${String(name)}`)
    }
}

function messageOf(error) {
    return error instanceof Error ? error.message : String(error)
}

function imagePart(base64) {
    return { type: 'image_url', image_url: { url: `data:image/jpeg;base64,${base64}` } }
}

function toolMessage(toolCallId, content) {
    return { role: 'tool', tool_call_id: toolCallId, content }
}

function parseArguments(toolCall) {
    const raw = toolCall?.function?.arguments
    if (typeof raw !== 'string') return { ok: false, error: 'argumentos ausentes ou não textuais' }
    try {
        return { ok: true, args: JSON.parse(raw) }
    } catch {
        return { ok: false, error: 'JSON inválido nos argumentos' }
    }
}

// Pendências para aceitar done: check de cada regra, cover de cada item, report das regras violadas.
function missingOf(state) {
    const missing = []
    for (let rule = 1; rule <= RULE_COUNT; rule += 1) {
        const check = state.checks.get(rule)
        if (!check) missing.push(`check da regra ${rule}`)
        else if (check.verdict === 'violated' && !state.reportedRules.has(rule)) missing.push(`report da regra ${rule} (violated)`)
    }
    for (const item of state.coverageItems) {
        if (!state.covers.has(item)) missing.push(`cover de ${item}`)
    }
    return missing
}

function finish(stop, state, budget, detail) {
    const result = {
        stop,
        steps: state.steps,
        costUsd: budget.scriptUsd,
        reports: state.reports,
        checks: [...state.checks.values()].sort((a, b) => a.rule - b.rule),
        coverage: state.coverageItems.filter((item) => state.covers.has(item)).map((item) => state.covers.get(item)),
        missing: missingOf(state),
    }
    if (detail !== undefined) result.detail = detail
    return result
}

// Guarda a captura com número novo, na linha do tempo; a primeira, antes do laço, é a 1.
function recordCapture(state, image) {
    state.nextShot += 1
    state.images.set(state.nextShot, image)
    state.timeline.push({ kind: 'shot', shot: state.nextShot })
    return state.nextShot
}

// Resultado da ferramenta de captura: o texto leva o número que o modelo usa em check, cover e report. Zoom é recorte.
function captureResult(state, image, zoom = false) {
    const shot = recordCapture(state, image)
    if (!zoom) return { text: `captura ${shot}`, shot }
    state.zoomShots.add(shot)
    return { text: `captura ${shot} (recorte: a borda do zoom corta o que fica fora dele; não é prova)`, shot }
}

// Recorte de zoom não é prova: achado e veredito violated exigem captura inteira.
const zoomRefusal = (shot) => `erro: captura ${shot} é um recorte de zoom; cite a captura inteira`

function recordCheck(state, action) {
    if (action.shot !== undefined && !state.images.has(action.shot)) return { text: `erro: captura ${action.shot} não existe` }
    if (action.verdict === 'violated' && state.zoomShots.has(action.shot)) return { text: zoomRefusal(action.shot) }
    state.checks.set(action.rule, { rule: action.rule, verdict: action.verdict, seen: action.seen, shot: action.shot })
    return { text: 'ok' }
}

// Ações que contam entre as capturas de um cover. minimize e restore pedem clique no botão do balão.
const CLICK_ONLY_ITEMS = ['minimize', 'restore']
// Acrescentado às recusas de cover impossível: a tentativa é obrigatória também para dizer que não dá.
const IMPOSSIBLE_TAIL = ' (impossível também exige a tentativa)'
const POINTER_TOOLS = ['move', 'click', 'drag']
const REPEAT_TEXT = 'erro: ação repetida; faça outra coisa'
const TURN_TEXT = 'ignorado: limite por turno'
const ZOOM_REPEAT_TEXT = 'erro: zoom repetido sem ação do ponteiro; mova, clique ou arraste antes de recortar de novo'
const WAIT_TOTAL_TEXT = `erro: espera passa do teto de ${LIMITS.maxTotalWaitMs} ms no roteiro`

// Sequência de chamadas iguais (nome + argumentos JSON), atravessando turnos. Recusa conta para o teto e não zera a sequência.
function repeatRefusal(state, tool, args) {
    const key = JSON.stringify([tool, args])
    state.streak = key === state.streakKey ? state.streak + 1 : 1
    state.streakKey = key
    if (state.streak <= LIMITS.maxRepeatStreak) return null
    state.repeatRefusals += 1
    return { text: REPEAT_TEXT, stuck: state.repeatRefusals >= LIMITS.maxRepeatRefusals }
}

// Zoom do mesmo retângulo só volta a valer depois de ação do ponteiro.
const zoomKeyOf = (action) => [action.x, action.y, action.w, action.h].join(',')

// Motivo da recusa do cover entre as capturas A e B; null quando aceita. A linha do tempo só tem ações que o driver executou.
// Impossível aceita imagens iguais: a tentativa é o que prova a impossibilidade.
function coverRefusal(state, { item, before, after, possible }) {
    if (!state.images.has(before)) return `erro: captura ${before} não existe`
    if (!state.images.has(after)) return `erro: captura ${after} não existe`
    if (state.zoomShots.has(before)) return zoomRefusal(before)
    if (state.zoomShots.has(after)) return zoomRefusal(after)
    if (possible !== false && state.images.get(before) === state.images.get(after)) return `erro: cover recusado: capturas ${before} e ${after} têm imagens iguais; a ação não mudou a tela`
    const clickOnly = CLICK_ONLY_ITEMS.includes(item)
    const allowed = clickOnly ? ['click'] : POINTER_TOOLS
    const start = state.timeline.findIndex((entry) => entry.kind === 'shot' && entry.shot === before)
    const end = state.timeline.findIndex((entry) => entry.kind === 'shot' && entry.shot === after)
    const between = state.timeline.slice(start + 1, end).filter((entry) => entry.kind === 'action')
    if (between.some((entry) => allowed.includes(entry.tool))) return null
    const hint = clickOnly ? 'clique no botão do balão' : 'mover o ponteiro, clicar ou arrastar'
    return `erro: cover recusado: falta ação entre as capturas ${before} e ${after}; use ${hint}`
}

function recordCover(state, action) {
    if (!state.coverageItems.includes(action.item)) return { text: `erro: item fora da cobertura: ${action.item}` }
    const refusal = coverRefusal(state, action)
    if (refusal) return { text: action.possible === false ? refusal + IMPOSSIBLE_TAIL : refusal }
    const cover = { item: action.item, before: action.before, after: action.after, seen: action.seen }
    if (action.possible === false) cover.possible = false
    state.covers.set(action.item, cover)
    return { text: 'ok' }
}

// Achado só vale com check violated da mesma regra; duplicata = mesma regra + mesmo título.
function recordReport(state, action) {
    if (state.checks.get(action.rule)?.verdict !== 'violated') return { text: `erro: report da regra ${action.rule} sem check violated` }
    if (!state.images.has(action.shot)) return { text: `erro: captura ${action.shot} não existe` }
    if (state.zoomShots.has(action.shot)) return { text: zoomRefusal(action.shot) }
    const key = [action.rule, action.title].join('\u0000')
    if (!state.reportKeys.has(key)) {
        state.reportKeys.add(key)
        state.reports.push({
            step: state.steps,
            title: action.title,
            rule: action.rule,
            shotNumber: action.shot,
            seen: action.seen,
            expected: action.expected,
            screenshot: state.images.get(action.shot),
        })
    }
    state.reportedRules.add(action.rule)
    return { text: 'ok' }
}

// Executa uma ação validada. Devolve o texto da resposta e, se for captura, o número dela.
async function executeTool(action, state, driver) {
    switch (action.tool) {
        case 'screenshot':
            return captureResult(state, await driver.screenshot())
        case 'zoom':
            if (state.zoomKey === zoomKeyOf(action)) return { text: ZOOM_REPEAT_TEXT }
            state.zoomKey = zoomKeyOf(action)
            return captureResult(state, await driver.zoom(action), true)
        case 'check':
            return recordCheck(state, action)
        case 'cover':
            return recordCover(state, action)
        case 'report':
            return recordReport(state, action)
        default:
            if (action.tool === 'wait' && state.waitedMs + action.ms > LIMITS.maxTotalWaitMs) return { text: WAIT_TOTAL_TEXT }
            await driver.perform(action)
            // Só entra na linha do tempo depois do driver aceitar: ação rejeitada não conta.
            state.timeline.push({ kind: 'action', tool: action.tool })
            if (action.tool === 'wait') state.waitedMs += action.ms
            if (POINTER_TOOLS.includes(action.tool)) state.zoomKey = null
            return { text: 'ok' }
    }
}

// Executa um turno do modelo. done recusado não executa o resto do turno.
async function runTurn({ toolCalls, messages, state, driver, screen }) {
    const images = []
    for (let index = 0; index < toolCalls.length; index += 1) {
        const toolCall = toolCalls[index]
        // Toda chamada conta para o teto total, inclusive as ignoradas por limite de turno.
        state.actions += 1
        if (state.actions > LIMITS.maxActions) return { done: true, stop: 'max-actions', images }
        if (index >= LIMITS.maxActionsPerTurn) {
            messages.push(toolMessage(toolCall.id, TURN_TEXT))
            continue
        }
        const parsed = parseArguments(toolCall)
        const checked = parsed.ok ? validateAction(toolCall?.function?.name, parsed.args, screen) : bad(parsed.error)
        if (!checked.ok) {
            messages.push(toolMessage(toolCall.id, `erro: ${checked.error}`))
            continue
        }
        if (checked.action.tool === 'done') {
            const turn = finishRequest(state, checked.action)
            messages.push(toolMessage(toolCall.id, turn.text))
            if (turn.done) return { done: true, stop: turn.stop, images }
            for (const rest of toolCalls.slice(index + 1)) messages.push(toolMessage(rest.id, 'ignorado: done recusado'))
            return { done: false, images }
        }
        const repeat = repeatRefusal(state, checked.action.tool, parsed.args)
        if (repeat) {
            messages.push(toolMessage(toolCall.id, repeat.text))
            if (repeat.stuck) return { done: true, stop: 'stuck', images }
            continue
        }
        const result = await executeTool(checked.action, state, driver)
        messages.push(toolMessage(toolCall.id, result.text))
        if (result.shot !== undefined) {
            images.push({ role: 'user', content: [{ type: 'text', text: `captura ${result.shot}` }, imagePart(state.images.get(result.shot))] })
        }
    }
    return { done: false, images }
}

// done aceito com pendências só depois de MAX_DONE_REFUSALS recusas; aí o stop vira done-incomplete.
function finishRequest(state, action) {
    const missing = missingOf(state)
    if (missing.length === 0) return { done: true, stop: 'done', text: 'ok' }
    if (state.doneRefusals >= MAX_DONE_REFUSALS) return { done: true, stop: 'done-incomplete', text: 'ok' }
    state.doneRefusals += 1
    return { done: false, text: `erro: done recusado (${action.summary.length} caracteres). Falta: ${missing.join('; ')}` }
}

// Roteiro completo. Erro nunca rejeita: vira stop + detail.
export async function runScript({ client, driver, script, rules, screen, budget, coverage = [] }) {
    budget.startScript()
    const state = {
        steps: 0,
        reports: [],
        reportKeys: new Set(),
        images: new Map(),
        nextShot: 0,
        checks: new Map(),
        covers: new Map(),
        reportedRules: new Set(),
        coverageItems: [...coverage],
        doneRefusals: 0,
        zoomShots: new Set(),
        timeline: [],
        actions: 0,
        streak: 0,
        streakKey: null,
        repeatRefusals: 0,
        waitedMs: 0,
        zoomKey: null,
    }
    let first
    try {
        first = recordCapture(state, await driver.screenshot())
    } catch (error) {
        return finish('driver-error', state, budget, messageOf(error))
    }
    const messages = [
        { role: 'system', content: rules },
        { role: 'user', content: [{ type: 'text', text: script }, imagePart(state.images.get(first))] },
    ]
    while (true) {
        if (state.steps >= LIMITS.maxSteps) return finish('max-steps', state, budget)
        const blocked = budget.canCall()
        if (blocked) return finish(blocked, state, budget)
        state.steps += 1
        let response
        try {
            response = await client.complete({ messages, tools: TOOLS, maxTokens: LIMITS.maxOutputTokens })
        } catch (error) {
            return finish('client-error', state, budget, messageOf(error))
        }
        budget.charge(response?.usage)
        const afterCharge = budget.canCall()
        if (afterCharge === 'cost-unknown') return finish(afterCharge, state, budget)
        const message = response?.message
        const toolCalls = Array.isArray(message?.tool_calls) ? message.tool_calls : []
        if (toolCalls.length === 0) return finish('no-tool-call', state, budget)
        messages.push({ role: 'assistant', content: message.content ?? null, tool_calls: toolCalls })
        try {
            const turn = await runTurn({ toolCalls, messages, state, driver, screen })
            if (turn.done) return finish(turn.stop, state, budget)
            messages.push(...turn.images)
        } catch (error) {
            return finish('driver-error', state, budget, messageOf(error))
        }
    }
}

// Texto da regra pelo número, como está em pilot-rules.md; sem linha, cai para o número.
function ruleText(rules, rule) {
    const prefix = `${rule}. `
    const line = String(rules).split('\n').find((item) => item.startsWith(prefix))
    return line ? line.slice(prefix.length) : `regra ${rule}`
}

// Veredito de um relatório com o modelo do piloto. null significa "não medido", nunca falso.
export async function confirmReport({ client, report, rules, budget }) {
    const blocked = budget.canCall()
    if (blocked) return { confirmed: null, why: blocked }
    const messages = [
        { role: 'system', content: rules },
        {
            role: 'user',
            content: [
                { type: 'text', text: `regra ${report.rule}: ${ruleText(rules, report.rule)}\ntítulo: ${report.title}\no que foi lido na captura: ${report.seen}` },
                imagePart(report.screenshot),
            ],
        },
    ]
    let response
    try {
        response = await client.complete({ messages, tools: [VERDICT_TOOL], toolChoice: VERDICT_CHOICE, maxTokens: LIMITS.maxOutputTokens })
    } catch {
        return { confirmed: null, why: 'client-error' }
    }
    budget.charge(response?.usage)
    if (budget.canCall() === 'cost-unknown') return { confirmed: null, why: 'cost-unknown' }
    return readVerdict(response?.message)
}

function readVerdict(message) {
    const verdictCall = Array.isArray(message?.tool_calls)
        ? message.tool_calls.find((item) => item?.function?.name === 'verdict')
        : undefined
    const parsed = verdictCall ? parseArguments(verdictCall) : { ok: false }
    if (!parsed.ok || typeof parsed.args?.confirmed !== 'boolean' || typeof parsed.args?.why !== 'string') {
        return { confirmed: null, why: 'malformed-verdict' }
    }
    return { confirmed: parsed.args.confirmed, why: parsed.args.why }
}

// Cliente OpenRouter. A chave fica no fechamento, fora de propriedades do objeto.
export function createOpenRouterClient({ fetch, apiKey, model }) {
    if (model !== MODEL) throw new Error('modelo não permitido no piloto')
    if (typeof apiKey !== 'string' || apiKey.length === 0) throw new Error('chave de API ausente')
    const redact = (text) => String(text).split(apiKey).join('[redacted]')
    const complete = async ({ messages, tools, maxTokens, toolChoice }) => {
        const body = {
            model,
            messages,
            tools,
            tool_choice: toolChoice ?? 'required',
            max_tokens: maxTokens,
            usage: { include: true },
        }
        const init = {
            method: 'POST',
            headers: { Authorization: `Bearer ${apiKey}`, 'Content-Type': 'application/json' },
            body: JSON.stringify(body),
        }
        let response
        try {
            response = await fetch(ENDPOINT, init)
        } catch (error) {
            throw new Error(redact(`falha de rede: ${messageOf(error)}`))
        }
        if (!response.ok) {
            let text = ''
            try {
                text = await response.text()
            } catch {
                text = ''
            }
            throw new Error(redact(`OpenRouter HTTP ${response.status}: ${String(text)}`).slice(0, 300))
        }
        let data
        try {
            data = await response.json()
        } catch {
            throw new Error('resposta JSON inválida do OpenRouter')
        }
        const message = data?.choices?.[0]?.message
        if (!message || typeof message !== 'object') throw new Error('resposta sem choices[0].message')
        return { message, usage: data.usage }
    }
    return { complete }
}
