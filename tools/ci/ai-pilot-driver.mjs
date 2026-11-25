// Driver de tela do piloto de IA de CI (#653, A03a). Fala com o processo de tela por linhas JSON.
// O processo entra por injeção: este módulo não abre processo, não lê disco nem variável de ambiente.
import { StringDecoder } from 'node:string_decoder'

const isSize = (value) => Number.isInteger(value) && value > 0

export function createScreenDriver({ child, timeoutMs = 30000 }) {
    const decoder = new StringDecoder('utf8')
    const queue = []
    const readyWaiters = []
    const exitWaiters = []
    let buffer = ''
    let nextId = 1
    let pending = null
    let readyInfo = null
    let readyTimer = null
    let closeTimer = null
    let failure = null
    let closed = false
    let exited = false

    // Falha terminal: rejeita pendente, fila e espera de ready. Timeout mata o filho; saída não.
    function fail(message, { kill }) {
        if (!failure) failure = message
        const error = new Error(failure)
        if (pending) {
            clearTimeout(pending.timer)
            pending.job.reject(error)
            pending = null
        }
        for (const job of queue.splice(0)) job.reject(error)
        for (const waiter of readyWaiters.splice(0)) waiter.reject(error)
        clearTimeout(readyTimer)
        if (kill) child.kill()
    }

    // Envia o próximo da fila somente quando não há pedido pendente: um por vez.
    function pump() {
        if (pending || failure || queue.length === 0) return
        const job = queue.shift()
        const id = nextId++
        const timer = setTimeout(() => fail('tempo esgotado aguardando resposta do filho', { kill: true }), timeoutMs)
        pending = { id, job, timer }
        child.stdin.write(`${JSON.stringify({ ...job.message, id })}\n`)
    }

    function request(message) {
        if (failure) return Promise.reject(new Error(failure))
        if (closed) return Promise.reject(new Error('driver fechado'))
        return new Promise((resolve, reject) => {
            queue.push({ message, resolve, reject })
            pump()
        })
    }

    function handleMessage(msg) {
        if (msg.ready === true) {
            if (!isSize(msg.width) || !isSize(msg.height)) return
            readyInfo = { width: msg.width, height: msg.height }
            clearTimeout(readyTimer)
            for (const waiter of readyWaiters.splice(0)) waiter.resolve({ ...readyInfo })
            return
        }
        if (msg.ready === false) {
            fail(`filho não ficou pronto: ${msg.error ?? 'erro desconhecido'}`, { kill: false })
            return
        }
        if (!pending || msg.id !== pending.id) return
        const { job, timer } = pending
        clearTimeout(timer)
        pending = null
        if (msg.ok === true) job.resolve(msg)
        else job.reject(new Error(typeof msg.error === 'string' ? msg.error : 'erro desconhecido do filho'))
        pump()
    }

    // Linha que não é JSON (aviso do pwsh, por exemplo) é ignorada.
    function handleLine(line) {
        if (line.trim() === '') return
        let msg
        try {
            msg = JSON.parse(line)
        } catch {
            return
        }
        if (msg && typeof msg === 'object') handleMessage(msg)
    }

    child.stdout.on('data', (chunk) => {
        buffer += typeof chunk === 'string' ? chunk : decoder.write(chunk)
        let newline = buffer.indexOf('\n')
        while (newline !== -1) {
            handleLine(buffer.slice(0, newline))
            buffer = buffer.slice(newline + 1)
            newline = buffer.indexOf('\n')
        }
    })

    child.on('exit', (code) => {
        exited = true
        fail(`filho saiu (código ${code}) antes de terminar`, { kill: false })
        clearTimeout(closeTimer)
        for (const resolve of exitWaiters.splice(0)) resolve()
    })

    function ready() {
        if (readyInfo) return Promise.resolve({ ...readyInfo })
        if (failure) return Promise.reject(new Error(failure))
        return new Promise((resolve, reject) => {
            readyWaiters.push({ resolve, reject })
            if (!readyTimer) readyTimer = setTimeout(() => fail('tempo esgotado aguardando ready do filho', { kill: true }), timeoutMs)
        })
    }

    async function screenshot() {
        const msg = await request({ tool: 'screenshot' })
        if (typeof msg.image !== 'string' || msg.image.length === 0) throw new Error('resposta de screenshot sem imagem')
        return msg.image
    }

    async function perform(action) {
        await request(action)
    }

    // Fecha o stdin e espera a saída; mata o filho só no teto.
    function close() {
        if (!closed) {
            closed = true
            child.stdin.end()
        }
        if (exited) return Promise.resolve()
        return new Promise((resolve) => {
            exitWaiters.push(resolve)
            if (!closeTimer) {
                closeTimer = setTimeout(() => {
                    child.kill()
                    resolve()
                }, timeoutMs)
            }
        })
    }

    async function zoom({ x, y, w, h }) {
        const msg = await request({ tool: 'zoom', x, y, w, h });
        if (typeof msg.image !== 'string' || msg.image.length === 0) throw new Error('resposta de zoom sem imagem')
        return msg.image
    }

    return { ready, screenshot, perform, close, zoom }
}
