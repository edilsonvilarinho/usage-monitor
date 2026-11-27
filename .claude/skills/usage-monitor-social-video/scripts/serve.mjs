// Servidor mínimo da prévia (A03c): só GET, só arquivos dentro da raiz, sem .git nem .env, sem listagem de pasta.
import http from 'node:http'
import { existsSync, readFileSync, statSync } from 'node:fs'
import { extname, resolve, sep } from 'node:path'

const TYPES = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.mjs': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.srt': 'text/plain; charset=utf-8',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.ttf': 'font/ttf',
  '.wav': 'audio/wav',
  '.mp4': 'video/mp4',
}

export function createRepoServer(root) {
  const base = resolve(root)
  return http.createServer((req, res) => {
    if (req.method !== 'GET') {
      res.writeHead(405, { Allow: 'GET' })
      res.end()
      return
    }
    let path
    try {
      path = decodeURIComponent(new URL(req.url, 'http://127.0.0.1').pathname)
    } catch {
      res.writeHead(400)
      res.end()
      return
    }
    const file = resolve(base, `.${path}`)
    if (!file.startsWith(base + sep)) {
      res.writeHead(403)
      res.end()
      return
    }
    const hidden = path.split('/').some((part) => part === '.git' || part.startsWith('.env'))
    if (hidden || !existsSync(file) || !statSync(file).isFile()) {
      res.writeHead(404)
      res.end()
      return
    }
    res.writeHead(200, {
      'Content-Type': TYPES[extname(file).toLowerCase()] ?? 'application/octet-stream',
      'Cache-Control': 'no-store',
    })
    res.end(readFileSync(file))
  })
}
