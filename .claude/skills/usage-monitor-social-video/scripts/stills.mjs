#!/usr/bin/env node
// Quadros de QA da cena final do vídeo social (#542):
//   node scripts/stills.mjs --work <dir> --variant <v> [--format 9:16] [--times 3.8,11.2]
// Grava <dir>/qa/<variante>-<9x16|16x9>-<t>.jpg em escala 1 (tamanho lógico) e imprime o custo médio de renderAt.
// Sem --times: 8 quadros espalhados pela duração. Erro ou aviso de console, ou exceção, sai com código 1.
import { existsSync, mkdirSync, statSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { parseArgs } from 'node:util'
import { FORMATS, isFatal, launch, openPage, pageUrl } from './browser.mjs'

const { values } = parseArgs({
  options: {
    work: { type: 'string' },
    variant: { type: 'string' },
    format: { type: 'string' },
    times: { type: 'string' },
  },
})

function fail(message) {
  console.error(message)
  process.exit(2)
}

if (!values.work) fail('uso: --work <dir> --variant <v> [--format 16:9|9:16] [--times t1,t2]')
const work = resolve(values.work)
if (!existsSync(work) || !statSync(work).isDirectory()) fail(`--work não é uma pasta: ${work}`)

const variant = values.variant ?? ''
if (!/^[a-z0-9][a-z0-9-]*$/.test(variant)) fail('uso: --variant <v> (ex.: short-pt)')
const dataFile = join(work, `${variant}.js`)
if (!existsSync(dataFile)) fail(`roteiro ausente: ${dataFile}`)

const formats = values.format ? [values.format] : Object.keys(FORMATS)
if (formats.some((f) => !FORMATS[f])) fail('--format 16:9|9:16')

const qaDir = join(work, 'qa')
mkdirSync(qaDir, { recursive: true })

const browser = await launch()
let failed = false
try {
  for (const format of formats) {
    const { w, h } = FORMATS[format]
    const slug = format.replace(':', 'x')
    const problems = []
    try {
      const page = await openPage(browser, { width: w, height: h }, problems)
      try {
        await page.goto(pageUrl({ data: dataFile, format }), { waitUntil: 'load' })
        await page.waitForFunction('window.__ready === true || window.__error !== undefined', { timeout: 30000 })
        const error = await page.evaluate(() => window.__error)
        if (error) throw new Error(`carga: ${error}`)
        const duration = await page.evaluate(() => WF.duration)
        const times = values.times
          ? values.times.split(',').map(Number)
          : Array.from({ length: 8 }, (_, k) => Math.round(((duration * (k + 0.5)) / 8) * 10) / 10)
        if (times.some((t) => !Number.isFinite(t) || t < 0 || t > duration)) throw new Error(`--times: valores de 0 a ${duration}`)
        const costs = []
        for (const t of times) {
          const cost = await page.evaluate((time) => {
            const start = performance.now()
            window.renderAt(time)
            return performance.now() - start
          }, t)
          costs.push(cost)
          await page.screenshot({ path: join(qaDir, `${variant}-${slug}-${t}.jpg`), type: 'jpeg', quality: 90 })
        }
        const mean = costs.reduce((a, b) => a + b, 0) / costs.length
        console.log(`${variant} ${format} ${w}x${h}: ${times.length} quadros em ${qaDir} (${times.join(', ')}), renderAt médio ${mean.toFixed(2)} ms, máximo ${Math.max(...costs).toFixed(2)} ms`)
      } finally {
        await page.close()
      }
    } catch (err) {
      problems.push(`harness: ${err.message}`)
    }
    for (const p of problems) console.error(`${format} ${p}`)
    if (problems.some((p) => isFatal(p) || p.startsWith('harness'))) failed = true
  }
} finally {
  await browser.close()
}
process.exitCode = failed ? 1 : 0
