// Navegador de render e QA do vídeo social (#542): Chrome por puppeteer-core, ferramentas de setup-tools.mjs.
// A página é assets/index.html?data=<caminho de <variante>.js>&format=<16:9|9:16>; expõe window.__ready,
// window.__error e window.renderAt(t). Erros de console, de página e de rede voltam em `problems`.
import { existsSync } from 'node:fs'
import { createRequire } from 'node:module'
import { homedir } from 'node:os'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'
import { chromePath, toolPaths } from '../../usage-monitor-release-video/scripts/setup-tools.mjs'

const SKILL = resolve(dirname(fileURLToPath(import.meta.url)), '..')

export const FORMATS = {
  '16:9': { w: 1920, h: 1080 },
  '9:16': { w: 1080, h: 1920 },
}

// data: caminho absoluto de <dir>/<variante>.js; format: 16:9 ou 9:16.
export function pageUrl({ data, format }) {
  const url = pathToFileURL(join(SKILL, 'assets', 'index.html'))
  url.search = new URLSearchParams({ data, format }).toString()
  return url.href
}

function findChrome() {
  try {
    return chromePath()
  } catch (err) {
    const fallback = join(homedir(), 'AppData', 'Local', 'ms-playwright', 'chromium-1248', 'chrome-win64', 'chrome.exe')
    if (existsSync(fallback)) return fallback
    throw err
  }
}

// Abre o navegador sem tela.
export function launch() {
  const require = createRequire(join(toolPaths().nodeModules, 'noop.js'))
  const puppeteer = require('puppeteer-core')
  return puppeteer.launch({
    executablePath: findChrome(),
    headless: true,
    args: [
      '--allow-file-access-from-files',
      '--hide-scrollbars',
      '--force-color-profile=srgb',
      '--autoplay-policy=no-user-gesture-required',
    ],
  })
}

// Página nova na resolução do formato; os problemas da página entram em `problems`.
export async function openPage(browser, { width, height }, problems) {
  const page = await browser.newPage()
  try {
    await page.setViewport({ width, height, deviceScaleFactor: 1 })
  } catch (err) {
    await page.close()
    throw err
  }
  page.on('console', (msg) => {
    if (msg.type() === 'error' || msg.type() === 'warning') problems.push(`console.${msg.type()}: ${msg.text()}`)
  })
  page.on('pageerror', (err) => problems.push(`pageerror: ${err.message}`))
  page.on('requestfailed', (req) => problems.push(`requestfailed: ${req.url()} ${req.failure()?.errorText ?? ''}`))
  return page
}

export function isFatal(problem) {
  return /^(console\.error|pageerror|requestfailed)/.test(problem)
}
