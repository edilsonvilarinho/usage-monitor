// Kit do cliente do portal do escritório. ES module, sem dependências.
// Contrato: .claude/skills/orquestrar/referencias/contrato-portal.md
//
//   conectar(onEstado)  SSE com reconexão; sem servidor, reproduz exemplo.json em laço
//                       com o selo visível "DADOS DE EXEMPLO". onEstado(estado, { exemplo }).
//   detalhe(id)         Promise do detalhe do agente.
//   tema()              alterna Sistema -> Claro -> Escuro e persiste.

const URL_EXEMPLO = new URL('./exemplo.json', import.meta.url);
const CHAVE_TEMA = 'portal:tema';
const MODOS_TEMA = ['sistema', 'claro', 'escuro'];
const ATRIBUTO_TEMA = { claro: 'light', escuro: 'dark' };
const REPETE_MS = 3000;

// Último recurso quando nem o exemplo.json carrega (file:// em alguns navegadores).
const EXEMPLO_MINIMO = {
  intervaloMs: 3500,
  quadros: [{
    sessao: 'exemplo', atualizadoEm: null, modo: 'ao-vivo', tarefas: [], falas: [],
    agentes: [{
      id: 'am', tipo: 'am', tipoOriginal: 'am', modelo: null, descricao: '(exemplo) Orquestradora', estado: 'ocioso',
      atividade: 'sem atividade', inicio: null, fim: null, tarefa: null, ferramentas: 0, erros: 0, tokens: { entrada: 0, saida: 0, cache: 0 },
    }],
    totais: { tokensEntrada: 0, tokensSaida: 0, tokensCache: 0, agentesAtivos: 0 },
  }],
  detalhes: {},
};

let exemploAtivo = false;
let exemploCache = null;

// ---------------------------------------------------------------- tema

function lerTema() {
  try {
    const v = localStorage.getItem(CHAVE_TEMA);
    return MODOS_TEMA.includes(v) ? v : 'sistema';
  } catch { return 'sistema'; }
}
function aplicarTema(modo) {
  const raiz = document.documentElement;
  const attr = ATRIBUTO_TEMA[modo];
  if (attr) raiz.setAttribute('data-theme', attr); else raiz.removeAttribute('data-theme');
  raiz.setAttribute('data-portal-tema', modo);
}

/** Alterna Sistema -> Claro -> Escuro -> Sistema. Devolve o modo novo. */
export function tema() {
  const atual = lerTema();
  const proximo = MODOS_TEMA[(MODOS_TEMA.indexOf(atual) + 1) % MODOS_TEMA.length];
  try { localStorage.setItem(CHAVE_TEMA, proximo); } catch { /* sem armazenamento */ }
  aplicarTema(proximo);
  return proximo;
}
/** Modo de tema atual sem alterar: 'sistema' | 'claro' | 'escuro'. */
export function temaAtual() {
  return lerTema();
}
if (typeof document !== 'undefined') aplicarTema(lerTema());

// ---------------------------------------------------------------- selo

function mostrarSelo(visivel) {
  if (typeof document === 'undefined') return;
  let selo = document.querySelector('[data-portal-selo]');
  if (!visivel) { if (selo) selo.remove(); document.documentElement.removeAttribute('data-portal-dados'); return; }
  document.documentElement.setAttribute('data-portal-dados', 'exemplo');
  if (selo) return;
  if (!document.querySelector('style[data-portal-kit]')) {
    const css = document.createElement('style');
    css.setAttribute('data-portal-kit', '');
    css.textContent = [
      '.portal-selo{position:fixed;right:12px;bottom:12px;z-index:2147483000;padding:5px 10px;',
      'font:600 11px/1 var(--mono);letter-spacing:.08em;text-transform:uppercase;',
      'color:var(--bg);background:var(--fg);border-radius:6px;pointer-events:none}',
    ].join('');
    document.head.appendChild(css);
  }
  selo = document.createElement('div');
  selo.className = 'portal-selo';
  selo.setAttribute('data-portal-selo', '');
  selo.setAttribute('role', 'status');
  selo.textContent = 'DADOS DE EXEMPLO';
  document.body.appendChild(selo);
}

// ---------------------------------------------------------------- exemplo

async function carregarExemplo() {
  if (exemploCache) return exemploCache;
  try {
    const r = await fetch(URL_EXEMPLO);
    if (!r.ok) throw new Error(String(r.status));
    exemploCache = await r.json();
  } catch {
    exemploCache = EXEMPLO_MINIMO;
  }
  return exemploCache;
}

function iniciarExemplo(onEstado) {
  let parado = false;
  let timer = null;
  let i = 0;
  mostrarSelo(true);
  exemploAtivo = true;
  carregarExemplo().then((ex) => {
    if (parado) return;
    const passo = () => {
      if (parado) return;
      const quadro = ex.quadros[i % ex.quadros.length];
      i++;
      onEstado({ ...quadro, atualizadoEm: new Date().toISOString() }, { exemplo: true });
      timer = setTimeout(passo, ex.intervaloMs || 3500);
    };
    passo();
  });
  return () => {
    parado = true;
    clearTimeout(timer);
    exemploAtivo = false;
    mostrarSelo(false);
  };
}

// ---------------------------------------------------------------- conexão

/**
 * Conecta ao servidor do portal. Sem servidor (file:// ou erro), reproduz o exemplo em laço.
 * Devolve { fechar() }.
 */
export function conectar(onEstado) {
  let fechado = false;
  let es = null;
  let pararExemplo = null;
  let retentar = null;

  const parar = () => { if (pararExemplo) { pararExemplo(); pararExemplo = null; } };
  const cair = () => { if (!pararExemplo && !fechado) pararExemplo = iniciarExemplo(onEstado); };

  function abrir() {
    if (fechado) return;
    try {
      es = new EventSource('/api/eventos');
    } catch { cair(); retentar = setTimeout(abrir, REPETE_MS); return; }
    es.addEventListener('estado', (ev) => {
      let estado;
      try { estado = JSON.parse(ev.data); } catch { return; }
      parar();
      onEstado(estado, { exemplo: false });
    });
    es.onerror = () => {
      cair();
      // CLOSED (ex.: 404 de um servidor que não é o portal): o navegador não reconecta sozinho.
      if (es && es.readyState === 2) {
        es.close();
        clearTimeout(retentar);
        retentar = setTimeout(abrir, REPETE_MS);
      }
    };
  }

  if (typeof location !== 'undefined' && location.protocol === 'file:') cair();
  else if (typeof EventSource === 'undefined') cair();
  else abrir();

  return {
    fechar() {
      fechado = true;
      clearTimeout(retentar);
      if (es) es.close();
      parar();
    },
  };
}

/** Detalhe do agente: prompt integral, passos e retorno. */
export async function detalhe(id) {
  if (exemploAtivo) {
    const ex = await carregarExemplo();
    const d = ex.detalhes?.[id];
    if (!d) throw new Error(`sem detalhe de exemplo para ${id}`);
    return d;
  }
  const r = await fetch(`/api/agente/${encodeURIComponent(id)}`);
  if (!r.ok) throw new Error(`detalhe indisponível (${r.status})`);
  return r.json();
}
