// Renderiza o cartão de código (code-card.mjs) num PNG pelo navegador de browser.mjs. Usado no staging de make.mjs.
import { writeFileSync } from 'node:fs'
import { pathToFileURL } from 'node:url'
import { isFatal, openPage } from './browser.mjs'
import { CODE_CARD, codeCardHtml } from './code-card.mjs'

// data: { head, numbers, lines } (codeCardData). Devolve o tamanho de fonte usado (26 px até 18 px).
export async function renderCodeCard(browser, { fonts, data, htmlFile, out }) {
  writeFileSync(htmlFile, codeCardHtml(fonts, data), 'utf8')
  const problems = []
  const page = await openPage(browser, { width: CODE_CARD.width, height: 1200 }, problems)
  try {
    await page.goto(pathToFileURL(htmlFile).href, { waitUntil: 'load' })
    await page.waitForFunction('window.__codeReady === true', { timeout: 15000 })
    const font = await page.evaluate(() => window.__codeFont)
    const card = await page.$('#card')
    await card.screenshot({ path: out, omitBackground: true })
    const fatal = problems.filter(isFatal)
    if (fatal.length) throw new Error(`cartão de código: ${fatal.join('; ')}`)
    return { font }
  } finally {
    await page.close()
  }
}
