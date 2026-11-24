// Loop do piloto de IA de CI (#653, A02). Puro: cliente e driver entram por injeção; nada aqui toca rede, disco ou ambiente.
// Orçamento falha fechado: uso sem custo bloqueia o piloto inteiro, nunca é estimado.

export const LIMITS = Object.freeze({
    maxSteps: 60,
    maxPromptTokens: 100000,
    promptMargin: 4000,
    maxScriptUsd: 0.3,
    maxPilotUsd: 5,
    reserveUsd: 0.05,
    maxOutputTokens: 1024,
    maxWaitMs: 5000,
    maxTypeChars: 200,
})

// Tolerância para soma de decimais em dólar: exatamente no limite continua permitido.
const EPSILON = 1e-9

const ENDPOINT = 'https://openrouter.ai/api/v1/chat/completions'

const KEYS = ['Escape', 'Enter', 'Tab', 'Space', 'Up', 'Down', 'Left', 'Right', 'Home', 'End', 'PageUp', 'PageDown']

const INT = { type: 'integer' }
const TEXT = { type: 'string' }

// Schema de ferramenta no formato de function calling da OpenAI.
function tool(name, description, properties = {}, required = []) {
    return { type: 'function', function: { name, description, parameters: { type: 'object', properties, required } } }
}

export const TOOLS = [
    tool('screenshot', 'Captura a tela atual.'),
    tool('click', 'Clique esquerdo na coordenada (0-based).', { x: INT, y: INT }, ['x', 'y']),
    tool('drag', 'Arrasta da origem ao destino.', { x1: INT, y1: INT, x2: INT, y2: INT }, ['x1', 'y1', 'x2', 'y2']),
    tool('move', 'Move o ponteiro (0-based).', { x: INT, y: INT }, ['x', 'y']),
    tool('type', 'Digita texto.', { text: TEXT }, ['text']),
    tool('key', 'Pressiona uma tecla da lista permitida.', { name: { type: 'string', enum: KEYS } }, ['name']),
    tool('wait', 'Espera em milissegundos.', { ms: INT }, ['ms']),
    tool('report', 'Registra um achado: título, regra violada e onde.', { title: TEXT, rule: TEXT, where: TEXT }, ['title', 'rule', 'where']),
    tool('done', 'Encerra o roteiro com um resumo.', { summary: TEXT }, ['summary']),
]

// Ferramenta única do veredito do modelo mais forte (confirmReport).
const VERDICT_TOOL = tool('verdict', 'Veredito sobre o achado.', { confirmed: { type: 'boolean' }, why: TEXT }, ['confirmed', 'why'])
const VERDICT_CHOICE = Object.freeze({ type: 'function', function: { name: 'verdict' } })

const fail = (reason) => ({ ok: false, reason })

// Confere a resposta de GET /api/v1/key. Ordem fixa das razões.
export function checkKey(body) {
    const data = body && typeof body === 'object' ? body.data : undefined
    if (!data || typeof data !== 'object') return fail('invalid-response')
    const limit = data.limit
    if (limit === undefined || (limit !== null && (typeof limit !== 'number' || !Number.isFinite(limit)))) {
        return fail('invalid-response')
    }
    if (limit === null) return fail('no-limit')
    if (!Number.isFinite(data.limit_remaining)) return fail('invalid-response')
    if (limit > 5) return fail('limit-too-high')
    if (data.limit_remaining < LIMITS.maxScriptUsd) return fail('remaining-too-low')
    return { ok: true }
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
        startScript() {
            budget.scriptUsd = 0
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

// Valida a ação vinda do modelo. Campos extras são descartados; o driver só recebe o que passou aqui.
export function validateAction(name, args, screen) {
    const input = args && typeof args === 'object' ? args : {}
    switch (name) {
        case 'screenshot':
            return ok({ tool: 'screenshot' })
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
        case 'report': {
            const title = textOf(input.title)
            const rule = textOf(input.rule)
            const where = textOf(input.where)
            if (!title || !rule || !where) return bad('campo inválido em report: title, rule e where não vazios')
            return ok({ tool: 'report', title, rule, where })
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

function finish(stop, state, budget, detail) {
    const result = { stop, steps: state.steps, costUsd: budget.scriptUsd, reports: state.reports }
    if (detail !== undefined) result.detail = detail
    return result
}

// Relatório repetido (título + regra + onde) fica uma vez só.
function recordReport(state, action) {
    const key = [action.title, action.rule, action.where].join('\u0000')
    if (state.seen.has(key)) return
    state.seen.add(key)
    state.reports.push({
        step: state.steps,
        title: action.title,
        rule: action.rule,
        where: action.where,
        screenshot: state.lastImage,
    })
}

// Executa um turno do modelo. Devolve { stop } quando o roteiro acaba no meio do turno; senão { done, images }.
async function runTurn({ toolCalls, messages, state, driver, screen }) {
    const images = []
    for (const toolCall of toolCalls) {
        const parsed = parseArguments(toolCall)
        const checked = parsed.ok
            ? validateAction(toolCall?.function?.name, parsed.args, screen)
            : bad(parsed.error)
        if (!checked.ok) {
            messages.push(toolMessage(toolCall.id, `erro: ${checked.error}`))
            continue
        }
        const action = checked.action
        if (action.tool === 'done') {
            messages.push(toolMessage(toolCall.id, 'ok'))
            return { done: true, images }
        }
        if (action.tool === 'report') {
            recordReport(state, action)
            messages.push(toolMessage(toolCall.id, 'ok'))
            continue
        }
        if (action.tool === 'screenshot') {
            state.lastImage = await driver.screenshot()
            messages.push(toolMessage(toolCall.id, 'captured'))
            images.push({ role: 'user', content: [{ type: 'text', text: 'captura solicitada' }, imagePart(state.lastImage)] })
            continue
        }
        await driver.perform(action)
        messages.push(toolMessage(toolCall.id, 'ok'))
    }
    return { done: false, images }
}

// Roteiro completo. Erro nunca rejeita: vira stop + detail.
export async function runScript({ client, driver, script, rules, screen, budget }) {
    budget.startScript()
    const state = { steps: 0, reports: [], seen: new Set(), lastImage: null }
    try {
        state.lastImage = await driver.screenshot()
    } catch (error) {
        return finish('driver-error', state, budget, messageOf(error))
    }
    const messages = [
        { role: 'system', content: rules },
        { role: 'user', content: [{ type: 'text', text: script }, imagePart(state.lastImage)] },
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
            if (turn.done) return finish('done', state, budget)
            messages.push(...turn.images)
        } catch (error) {
            return finish('driver-error', state, budget, messageOf(error))
        }
    }
}

// Veredito de um relatório com o modelo mais forte. null significa "não medido", nunca falso.
export async function confirmReport({ client, report, rules, budget }) {
    const blocked = budget.canCall()
    if (blocked) return { confirmed: null, why: blocked }
    const messages = [
        { role: 'system', content: rules },
        {
            role: 'user',
            content: [
                { type: 'text', text: `título: ${report.title}\nregra: ${report.rule}\nonde: ${report.where}` },
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
