#!/usr/bin/env node
// Guarda de paridade entre a tabela de precos do domain Kotlin e a que o
// servidor publica em `/api/v1/pricing`.
//
// Ha duas copias da mesma tabela de proposito: o domain Kotlin nao pode importar
// serializacao nem IO, e o `Dockerfile.dokploy` copia so `server/tsconfig.json` e
// `server/src` -- um JSON compartilhado na raiz nao chegaria na imagem. O preco
// dessa escolha e a divergencia, e este script e quem a cobra.
//
// A leitura e por REGEX sobre os dois arquivos, e por isso o formato das duas
// listas e contrato, dito no comentario de cada uma. Reformatar quebra o script:
// e o comportamento desejado -- falha ruidosa vale mais que preco errado
// publicado em silencio.
//
// Sem dependencia externa, no molde de `tools/ci/test-summary.mjs`: o job do
// desktop nao roda `npm ci`, e uma guarda que precisa de instalacao para existir
// e uma guarda que some no primeiro erro de rede.
import { readFileSync } from 'node:fs'

const KOTLIN_TABLE = 'src/commonMain/kotlin/com/usagemonitor/domain/entity/ModelPricingTable.kt'
const KOTLIN_PRICING = 'src/commonMain/kotlin/com/usagemonitor/domain/entity/ModelPricing.kt'
const SERVER_TABLE = 'server/src/domain/modelPricing.ts'
const SYNTHETIC = '<synthetic>'

const problems = []

const kotlinTableSource = read(KOTLIN_TABLE)
const kotlinPricingSource = read(KOTLIN_PRICING)
const serverSource = read(SERVER_TABLE)

const kotlinDefaultDivisor = readKotlinDefaultDivisor(kotlinPricingSource)
const kotlinEntries = readKotlinEntries(kotlinTableSource, kotlinDefaultDivisor)
const kotlinMultipliers = readKotlinMultipliers(kotlinPricingSource, kotlinDefaultDivisor)
const serverMultipliers = readServerMultipliers(serverSource)
const serverEntries = readServerEntries(serverSource, serverMultipliers.read?.denominator ?? kotlinDefaultDivisor)
compareEntries(kotlinEntries, serverEntries)
compareMultipliers(kotlinMultipliers, serverMultipliers)
compareThreshold(readKotlinThreshold(kotlinPricingSource), readServerThreshold(serverSource))

// `<synthetic>` e preco zero CONHECIDO: some de um lado so e o consumidor passa a
// marcar sessoes inteiras como custo incompleto, sem nenhum erro aparecer.
requireSynthetic(kotlinEntries, KOTLIN_TABLE)
requireSynthetic(serverEntries, SERVER_TABLE)

if (problems.length > 0) {
    console.error(`[pricing-parity] ${problems.length} divergencia(s) entre ${KOTLIN_TABLE} e ${SERVER_TABLE}:`)
    for (const problem of problems) console.error(`  - ${problem}`)
    process.exit(1)
}

console.log(
    `[pricing-parity] OK: ${kotlinEntries.length} modelo(s) e ${Object.keys(kotlinMultipliers).length} multiplicador(es) de cache identicos nos dois lados.`
)

function read(path) {
    try {
        return readFileSync(path, 'utf8')
    } catch (error) {
        console.error(`[pricing-parity] nao foi possivel ler "${path}": ${error.message}`)
        console.error('[pricing-parity] rode a partir da raiz do repositorio.')
        process.exit(2)
    }
}

// `private val NOME = ModelPricing.ofUsdPerMillion(input = 5.00, output = 25.00, ...)`.
// Os argumentos sao lidos por nome, nao por posicao: a faixa de prompt longo e o
// divisor do cache so existem em alguns modelos. Os valores em USD viram micros
// aqui, que e a unidade em que o servidor publica.
function readKotlinPrices(source) {
    const prices = new Map()
    for (const match of source.matchAll(/private val (\w+) = ModelPricing\.ofUsdPerMillion\(([^)]*)\)/g)) {
        const args = match[2]
        const input = namedArgument(args, 'input')
        const output = namedArgument(args, 'output')
        if (input === null || output === null) {
            fail(`${KOTLIN_TABLE}: \`${match[1]}\` sem \`input\` ou \`output\` no formato esperado.`)
            continue
        }
        const divisor = namedArgument(args, 'cacheReadDivisor')
        const longInput = namedArgument(args, 'longPromptInput')
        const longOutput = namedArgument(args, 'longPromptOutput')
        prices.set(match[1], {
            input: toMicros(input),
            output: toMicros(output),
            divisor: divisor === null ? undefined : Number(divisor.replace(/L$/, '')),
            longInput: longInput === null ? undefined : toMicros(longInput),
            longOutput: longOutput === null ? undefined : toMicros(longOutput)
        })
    }
    return prices
}

function namedArgument(args, name) {
    const match = args.match(new RegExp(`\\b${name} = ([\\w.]+)`))
    return match ? match[1] : null
}

// `const val DEFAULT_CACHE_READ_DIVISOR = 10L`: divisor de quem nao declara o seu.
function readKotlinDefaultDivisor(source) {
    const match = source.match(/const val DEFAULT_CACHE_READ_DIVISOR = (\d+)L/)
    if (!match) {
        fail(`${KOTLIN_PRICING}: \`DEFAULT_CACHE_READ_DIVISOR\` nao encontrado.`)
        return null
    }
    return Number(match[1])
}

// Seguido da lista `listOf("prefixo" to NOME, ...)`. Os prefixos sem aspas vem
// de `const val`.
function readKotlinEntries(source, defaultDivisor) {
    const prices = readKotlinPrices(source)

    // O prefixo do marcador nao e literal na lista: vem de `const val`.
    const constants = new Map()
    for (const match of source.matchAll(/const val (\w+) = "([^"]*)"/g)) constants.set(match[1], match[2])

    const block = source.match(/private val entries[^=]*= listOf\(([\s\S]*?)\)\s*\.sortedByDescending/)
    if (!block) {
        fail(`${KOTLIN_TABLE}: nao encontrei a lista \`entries ... listOf(...)\`. O formato mudou?`)
        return []
    }

    const entries = []
    for (const rawLine of block[1].split('\n')) {
        const line = rawLine.trim()
        if (line === '' || line.startsWith('//')) continue
        const parsed = line.match(/^(?:"([^"]*)"|([A-Za-z_]\w*))\s+to\s+([A-Za-z_]\w*),?$/)
        if (!parsed) {
            fail(`${KOTLIN_TABLE}: linha da lista fora do formato esperado: \`${line}\``)
            continue
        }
        const prefix = parsed[1] ?? constants.get(parsed[2])
        if (prefix === undefined) {
            fail(`${KOTLIN_TABLE}: prefixo \`${parsed[2]}\` nao resolve para nenhum \`const val\`.`)
            continue
        }
        const price = prices.get(parsed[3])
        if (price === undefined) {
            fail(`${KOTLIN_TABLE}: preco \`${parsed[3]}\` nao resolve para nenhum \`private val\`.`)
            continue
        }
        if ((price.longInput === undefined) !== (price.longOutput === undefined)) {
            fail(`${KOTLIN_TABLE}: faixa de prompt longo de \`${parsed[3]}\` declarada so de um lado.`)
        }
        entries.push({
            prefix,
            input: price.input,
            output: price.output,
            divisor: price.divisor ?? defaultDivisor,
            longInput: price.longInput,
            longOutput: price.longOutput
        })
    }
    return entries
}

// `{ prefix: 'claude-opus-5', inputMicrosPerMillion: 5_000_000, outputMicrosPerMillion: 25_000_000 },`
// com `cacheReadDenominator: N` e `longPrompt: { inputMicrosPerMillion, outputMicrosPerMillion }`
// opcionais. Uma entrada por linha: linha que nao casa com a forma e ignorada aqui e
// acusada pela contagem de modelos.
function readServerEntries(source, defaultDivisor) {
    const entries = []
    for (const line of source.split('\n')) {
        const head = line.match(
            /^\s*\{\s*prefix:\s*'([^']*)',\s*inputMicrosPerMillion:\s*([\d_]+),\s*outputMicrosPerMillion:\s*([\d_]+)/
        )
        if (!head) continue
        const denominator = line.match(/cacheReadDenominator:\s*(\d+)/)
        const tier = line.match(
            /longPrompt:\s*\{\s*inputMicrosPerMillion:\s*([\d_]+),\s*outputMicrosPerMillion:\s*([\d_]+)/
        )
        entries.push({
            prefix: head[1],
            input: digits(head[2]),
            output: digits(head[3]),
            divisor: denominator ? Number(denominator[1]) : defaultDivisor,
            longInput: tier ? digits(tier[1]) : undefined,
            longOutput: tier ? digits(tier[2]) : undefined
        })
    }
    if (entries.length === 0) fail(`${SERVER_TABLE}: nenhuma entrada de \`MODEL_PRICING\` reconhecida. O formato mudou?`)
    return entries
}

// A ordem e comparada posicao a posicao de proposito: as duas listas declaram a
// mesma tabela, e uma reordenacao que ninguem consegue justificar e o primeiro
// sintoma de edicao feita num lado so.
function compareEntries(kotlin, server) {
    if (kotlin.length !== server.length) {
        fail(`contagem de modelos: ${kotlin.length} no Kotlin contra ${server.length} no servidor.`)
    }
    const total = Math.max(kotlin.length, server.length)
    for (let index = 0; index < total; index += 1) {
        const left = kotlin[index]
        const right = server[index]
        if (!left) {
            fail(`modelo so no servidor, na posicao ${index + 1}: \`${right.prefix}\`.`)
            continue
        }
        if (!right) {
            fail(`modelo so no Kotlin, na posicao ${index + 1}: \`${left.prefix}\`.`)
            continue
        }
        if (left.prefix !== right.prefix) {
            fail(`posicao ${index + 1}: Kotlin diz \`${left.prefix}\`, servidor diz \`${right.prefix}\`.`)
            continue
        }
        if (left.input !== right.input) {
            fail(`\`${left.prefix}\`: input ${left.input} no Kotlin contra ${right.input} no servidor.`)
        }
        if (left.output !== right.output) {
            fail(`\`${left.prefix}\`: output ${left.output} no Kotlin contra ${right.output} no servidor.`)
        }
        if (left.divisor !== right.divisor) {
            fail(`\`${left.prefix}\`: divisor de cache read ${left.divisor} no Kotlin contra ${right.divisor} no servidor.`)
        }
        if (!sameTier(left, right)) {
            fail(`\`${left.prefix}\`: faixa de prompt longo ${describeTier(left)} no Kotlin contra ${describeTier(right)} no servidor.`)
        }
    }
}

// Faixa de prompt longo ausente nos dois lados e igual; faixa so de um lado, ou com
// valores diferentes, diverge.
function tierOf(entry) {
    if (entry.longInput === undefined || entry.longOutput === undefined) return null
    return { input: entry.longInput, output: entry.longOutput }
}

function sameTier(a, b) {
    const left = tierOf(a)
    const right = tierOf(b)
    if (left === null || right === null) return left === right
    return left.input === right.input && left.output === right.output
}

function describeTier(entry) {
    const tier = tierOf(entry)
    return tier === null ? 'ausente' : `${tier.input}/${tier.output} micros`
}

// `const val LONG_PROMPT_THRESHOLD_TOKENS = 100_000L` contra
// `export const LONG_PROMPT_THRESHOLD_TOKENS = 100_000`.
function readKotlinThreshold(source) {
    const match = source.match(/const val LONG_PROMPT_THRESHOLD_TOKENS = ([\d_]+)L?/)
    if (!match) {
        fail(`${KOTLIN_PRICING}: \`LONG_PROMPT_THRESHOLD_TOKENS\` nao encontrado.`)
        return null
    }
    return digits(match[1])
}

function readServerThreshold(source) {
    const match = source.match(/export const LONG_PROMPT_THRESHOLD_TOKENS = ([\d_]+)/)
    if (!match) {
        fail(`${SERVER_TABLE}: \`LONG_PROMPT_THRESHOLD_TOKENS\` nao encontrado.`)
        return null
    }
    return digits(match[1])
}

function compareThreshold(kotlin, server) {
    if (kotlin === null || server === null) return
    if (kotlin !== server) {
        fail(`limite de prompt longo: ${kotlin} tokens no Kotlin contra ${server} no servidor.`)
    }
}

// Os tres getters de `ModelPricing`: `/ cacheReadDivisor`, `* 5L / 4L` e `* 2L` sobre a
// tarifa de input. O cache read usa o divisor padrao; o divisor de cada modelo e
// comparado na lista de entradas. Viram a razao inteira que o servidor publica.
function readKotlinMultipliers(source, defaultDivisor) {
    const names = {
        cacheReadMicrosPerMillion: 'read',
        cacheWrite5mMicrosPerMillion: 'write5m',
        cacheWrite1hMicrosPerMillion: 'write1h'
    }
    const found = {}
    for (const match of source.matchAll(/val (\w+): Long\s*\n\s*get\(\) = inputMicrosPerMillion([^\n]*)/g)) {
        const key = names[match[1]]
        if (!key) continue
        const expression = match[2].trim()
        const ratio =
            expression === '/ cacheReadDivisor'
                ? defaultDivisor === null
                    ? null
                    : { numerator: 1, denominator: defaultDivisor }
                : parseRatio(expression)
        if (!ratio) {
            fail(`${KOTLIN_PRICING}: expressao de \`${match[1]}\` fora do formato esperado: \`${expression}\`.`)
            continue
        }
        found[key] = ratio
    }
    for (const key of Object.values(names)) {
        if (!found[key]) fail(`${KOTLIN_PRICING}: multiplicador \`${key}\` nao encontrado.`)
    }
    return found
}

// Aceita `* N L`, `/ N L` e `* N L / M L` -- as tres formas que os getters usam.
function parseRatio(expression) {
    const both = expression.match(/^\*\s*(\d+)L\s*\/\s*(\d+)L$/)
    if (both) return { numerator: Number(both[1]), denominator: Number(both[2]) }
    const divide = expression.match(/^\/\s*(\d+)L$/)
    if (divide) return { numerator: 1, denominator: Number(divide[1]) }
    const multiply = expression.match(/^\*\s*(\d+)L$/)
    if (multiply) return { numerator: Number(multiply[1]), denominator: 1 }
    return null
}

// `read: { numerator: 1, denominator: 10 },`
function readServerMultipliers(source) {
    const found = {}
    for (const match of source.matchAll(
        /(read|write5m|write1h):\s*\{\s*numerator:\s*(\d+),\s*denominator:\s*(\d+),?\s*\}/g
    )) {
        found[match[1]] = { numerator: Number(match[2]), denominator: Number(match[3]) }
    }
    for (const key of ['read', 'write5m', 'write1h']) {
        if (!found[key]) fail(`${SERVER_TABLE}: multiplicador \`${key}\` nao encontrado em \`CACHE_MULTIPLIERS\`.`)
    }
    return found
}

// Razoes equivalentes (2/1 contra 4/2) nao passam: as duas listas descrevem a
// mesma constante, e um lado "simplificado" e edicao que o outro nao recebeu.
function compareMultipliers(kotlin, server) {
    for (const key of ['read', 'write5m', 'write1h']) {
        const left = kotlin[key]
        const right = server[key]
        if (!left || !right) continue
        if (left.numerator !== right.numerator || left.denominator !== right.denominator) {
            fail(
                `multiplicador \`${key}\`: ${left.numerator}/${left.denominator} no Kotlin contra ` +
                    `${right.numerator}/${right.denominator} no servidor.`
            )
        }
    }
}

function requireSynthetic(entries, path) {
    if (!entries.some((entry) => entry.prefix === SYNTHETIC)) {
        fail(`${path}: \`${SYNTHETIC}\` ausente. Ele e preco zero conhecido, nao preco desconhecido.`)
    }
}

function toMicros(usd) {
    return Math.round(Number(usd) * 1_000_000)
}

function digits(value) {
    return Number(value.replaceAll('_', ''))
}

function fail(message) {
    problems.push(message)
}
