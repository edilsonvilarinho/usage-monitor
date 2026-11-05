// Grava eventos da AM para o portal: tarefas, falas e vereditos.
//   emit.mjs tarefa   --id <ID> --titulo <t> --dono <agente> --estado fila|andamento|validacao|aprovada|devolvida|bloqueada
//   emit.mjs fala     --de <a> --para <b> [--tarefa <ID>] [--tipo <tipo>] --texto <t>
//   emit.mjs veredito --tarefa <ID> --resultado aprovada|devolvida|bloqueada --texto <t>
//   emit.mjs escalada --tarefa <ID>
// Veredito "devolvida" imprime a contagem e a próxima ação; o log em disco é a fonte, não a memória da AM.
// A sessão é lida de .claude/escritorio/portal.json (ou --sessao).

import fs from 'node:fs';
import path from 'node:path';
import { ESTADOS_TAREFA, RESULTADOS_VEREDITO, TIPOS_FALA, dirEscritorio, parseArgs, raizRepo, sessaoValida } from './collector.mjs';

const args = parseArgs(process.argv.slice(2));
const comando = args._[0];

// reprovações que cada modelo aguenta antes de escalar (haiku) ou parar (sonnet)
const LIMITE_REPROVACOES = { haiku: 3, sonnet: 3 };

function falha(msg) {
  console.error(`emit: ${msg}`);
  process.exit(1);
}
function exigir(...nomes) {
  for (const n of nomes) if (typeof args[n] !== 'string' || !args[n].trim()) falha(`falta --${n}`);
}

const dir = dirEscritorio(raizRepo());

function sessaoAtual() {
  if (args.sessao !== undefined) {
    if (!sessaoValida(args.sessao)) falha('--sessao inválida');
    return args.sessao;
  }
  try {
    const o = JSON.parse(fs.readFileSync(path.join(dir, 'portal.json'), 'utf8'));
    if (sessaoValida(o.sessao)) return o.sessao;
  } catch { /* sem portal.json */ }
  return falha('sem portal.json: suba o portal com start.mjs ou passe --sessao');
}

function eventosDaSessao() {
  let txt;
  try { txt = fs.readFileSync(path.join(dir, `${sessaoAtual()}.jsonl`), 'utf8'); } catch { return []; }
  return txt.split(/\r?\n/).flatMap((l) => {
    try { return [JSON.parse(l)]; } catch { return []; }
  });
}

function tarefaRegistrada(id) {
  return eventosDaSessao().some((o) => o.tipo === 'tarefa' && o.id === id && o.titulo && o.dono);
}

// Modelo em uso e reprovações desde a última escalada, com a mesma regra de contagem do coletor.
function rodada(id) {
  let modelo = 'haiku';
  let reprovacoes = 0;
  let estado = null;
  for (const e of eventosDaSessao()) {
    if (e.tipo === 'escalada' && e.tarefa === id) {
      modelo = e.modelo;
      reprovacoes = 0;
      estado = null;
      continue;
    }
    const novo = e.tipo === 'tarefa' && e.id === id ? e.estado : e.tipo === 'veredito' && e.tarefa === id ? e.resultado : null;
    if (!novo) continue;
    if (novo === 'devolvida' && estado !== 'devolvida') reprovacoes++;
    estado = novo;
  }
  return { modelo, reprovacoes, limite: LIMITE_REPROVACOES[modelo] };
}

function gravar(evento) {
  const sessao = sessaoAtual();
  fs.mkdirSync(dir, { recursive: true });
  fs.appendFileSync(path.join(dir, `${sessao}.jsonl`), JSON.stringify({ ts: new Date().toISOString(), ...evento }) + '\n');
}

switch (comando) {
  case 'tarefa': {
    exigir('id', 'estado');
    if (!ESTADOS_TAREFA.includes(args.estado)) falha(`--estado deve ser ${ESTADOS_TAREFA.join('|')}`);
    // --titulo e --dono só são obrigatórios na primeira vez; depois a atualização é parcial
    if (!tarefaRegistrada(args.id)) exigir('titulo', 'dono');
    const ev = { tipo: 'tarefa', id: args.id, estado: args.estado };
    if (typeof args.titulo === 'string' && args.titulo.trim()) ev.titulo = args.titulo;
    if (typeof args.dono === 'string' && args.dono.trim()) ev.dono = args.dono;
    gravar(ev);
    break;
  }
  case 'fala':
    exigir('de', 'para', 'texto');
    if (args.tipo !== undefined && !TIPOS_FALA.includes(args.tipo)) falha(`--tipo deve ser ${TIPOS_FALA.join('|')}`);
    gravar({ tipo: 'fala', de: args.de, para: args.para, tarefa: typeof args.tarefa === 'string' ? args.tarefa : null, tipoFala: args.tipo ?? 'fala', texto: args.texto });
    break;
  case 'veredito':
    exigir('tarefa', 'resultado');
    if (!RESULTADOS_VEREDITO.includes(args.resultado)) falha(`--resultado deve ser ${RESULTADOS_VEREDITO.join('|')}`);
    gravar({ tipo: 'veredito', tarefa: args.tarefa, resultado: args.resultado, texto: typeof args.texto === 'string' ? args.texto : '' });
    if (args.resultado === 'devolvida') {
      const { modelo, reprovacoes, limite } = rodada(args.tarefa);
      const noLimite = reprovacoes >= limite;
      // sem voltar para andamento, a reprovação seguinte não contaria
      if (!noLimite && tarefaRegistrada(args.tarefa)) gravar({ tipo: 'tarefa', id: args.tarefa, estado: 'andamento' });
      const acao = !noLimite ? 'devolver ao mesmo agente' : modelo === 'haiku' ? 'escalar para sonnet high' : 'parar e reportar ao dono do produto';
      console.log(`${args.tarefa}: reprovação ${reprovacoes}/${limite} no ${modelo}. Ação: ${acao}.`);
    }
    break;
  case 'escalada': {
    exigir('tarefa');
    const { modelo, reprovacoes, limite } = rodada(args.tarefa);
    if (modelo !== 'haiku') falha(`${args.tarefa} já está no ${modelo}`);
    if (reprovacoes < limite) falha(`${args.tarefa} tem ${reprovacoes}/${limite} reprovações: escalada só no limite`);
    gravar({ tipo: 'escalada', tarefa: args.tarefa, modelo: 'sonnet' });
    gravar({ tipo: 'tarefa', id: args.tarefa, estado: 'andamento' });
    break;
  }
  default:
    falha('comando deve ser tarefa, fala, veredito ou escalada');
}
