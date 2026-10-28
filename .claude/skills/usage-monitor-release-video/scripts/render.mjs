// Captura o renderizador (assets/video.html, ou assets/tour.html no tour 16:9) quadro a quadro no Chrome sem tela e entrega os JPEG ao
// ffmpeg pela entrada padrão. O tempo de cada quadro é frame / FPS: nenhum relógio de verdade
// entra, então dois renders do mesmo roteiro dão o mesmo vídeo.
import { spawn } from 'node:child_process'
import { createRequire } from 'node:module'
import { join } from 'node:path'
import { pathToFileURL } from 'node:url'
import { chromePath } from './setup-tools.mjs'

export const WIDTH = 1080
export const HEIGHT = 1920
export const FPS = 30
// Vídeo de release vertical; o tour (#505) passa { width: 1920, height: 1080, page: 'tour.html' }.
const VERTICAL = { width: WIDTH, height: HEIGHT, page: 'video.html' }

async function openPage(nodeModules, stageDir, data, frame = VERTICAL) {
    const require = createRequire(join(nodeModules, 'noop.js'))
    const puppeteer = require('puppeteer-core')
    const browser = await puppeteer.launch({
        executablePath: chromePath(),
        headless: true,
        args: ['--allow-file-access-from-files', '--hide-scrollbars', '--force-color-profile=srgb'],
    })
    const page = await browser.newPage()
    await page.setViewport({ width: frame.width, height: frame.height, deviceScaleFactor: 1 })
    await page.goto(pathToFileURL(join(stageDir, frame.page)).href, { waitUntil: 'load' })
    await page.evaluate((payload) => window.setup(payload), data)
    return { browser, page }
}

function capture(page, t) {
    return page
        .evaluate((time) => window.renderFrame(time), t)
        .then(() => page.screenshot({ type: 'jpeg', quality: 92, optimizeForSpeed: true }))
}

export async function renderVideo({ nodeModules, stageDir, data, output, onProgress, frame }) {
    const { browser, page } = await openPage(nodeModules, stageDir, data, frame)
    const encoder = spawn('ffmpeg', [
        '-v', 'error', '-y', '-f', 'image2pipe', '-framerate', String(FPS), '-c:v', 'mjpeg', '-i', '-',
        '-c:v', 'libx264', '-preset', 'medium', '-crf', '18', '-pix_fmt', 'yuv420p', '-r', String(FPS), output,
    ], { stdio: ['pipe', 'inherit', 'inherit'] })
    const finished = new Promise((resolve, reject) => {
        encoder.on('close', (code) => (code === 0 ? resolve() : reject(new Error(`ffmpeg saiu com ${code}`))))
    })
    const frames = Math.ceil(data.timeline.total * FPS)
    try {
        for (let frame = 0; frame < frames; frame += 1) {
            const image = await capture(page, frame / FPS)
            if (!encoder.stdin.write(image)) await new Promise((resolve) => encoder.stdin.once('drain', resolve))
            if (onProgress && frame % (FPS * 10) === 0) onProgress(frame, frames)
        }
    } finally {
        encoder.stdin.end()
        await browser.close()
    }
    await finished
}

// Quadros soltos para conferir cenas sem gerar o vídeo inteiro.
export async function renderStills({ nodeModules, stageDir, data, times, outputFor, frame }) {
    const { browser, page } = await openPage(nodeModules, stageDir, data, frame)
    const { writeFileSync } = await import('node:fs')
    try {
        for (const t of times) {
            await page.evaluate((time) => window.renderFrame(time), t)
            writeFileSync(outputFor(t), await page.screenshot({ type: 'png' }))
        }
    } finally {
        await browser.close()
    }
}
