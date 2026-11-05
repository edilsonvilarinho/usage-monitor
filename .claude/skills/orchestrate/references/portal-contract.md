# Contrato do portal do escritório

Servidor local em `127.0.0.1`, sem dependências. A página definitiva faz parte da skill: `portal/web/index.html`, com o kit em `portal/web/portal-dados.js`.

## Fontes de dados

1. Transcript principal: `~/.claude/projects/<projeto>/<sessao>.jsonl`. Chamadas `Agent` (`input.description`, `input.prompt`, `input.subagent_type`), resultado com `toolUseResult.agentId` e `SendMessage` (`input.to`, `input.message`).
2. Subagentes: `~/.claude/projects/<projeto>/<sessao>/subagents/agent-<id>.jsonl` e `.meta.json` (`agentType`, `description`, `finished`). Linhas `assistant` trazem `message.model`, `message.usage` e blocos `tool_use`/`text`; linhas `user` trazem `tool_result` com `is_error`.
3. Eventos da AM: `.claude/escritorio/<sessao>.jsonl`, gravados por `emit.mjs`.

`<projeto>` é o caminho do repositório com `:`, `\` e `/` trocados por `-`. O formato do transcript não é API pública: linha desconhecida é ignorada, nunca derruba o portal.

## API

- `GET /api/estado`: estado completo (abaixo).
- `GET /api/eventos`: SSE. Evento `estado` com o estado completo a cada mudança, no máximo 2 por segundo; `: ping` a cada 15 s.
- `GET /api/agente/:id`: detalhe para inspeção: `prompt` integral, `passos[]` (`ts`, `tipo`: `texto|ferramenta|resultado`, `ferramenta`, `resumo`, `erro`), `retorno` final.
- `GET /`: página do portal (`portal/web/index.html`), sem redirecionamento.
- `GET /portal/*`, `GET /prototipos/*`: estáticos, sem sair dessas pastas.

```ts
interface Estado {
  sessao: string; atualizadoEm: string; modo: 'ao-vivo' | 'replay';
  agentes: Agente[]; tarefas: Tarefa[]; falas: Fala[];
  totais: { tokensEntrada: number; tokensSaida: number; tokensCache: number; agentesAtivos: number };
}
interface Agente {
  id: string;            // 'am' para a orquestradora; agentId nos demais
  tipo: string;          // am, backend-dev, frontend-dev, prototype-dev, test-runner, explorer, outro
  modelo: string | null; // valor literal do transcript
  descricao: string;
  estado: 'ocioso' | 'trabalhando' | 'aguardando' | 'concluido' | 'erro';
  atividade: string;     // em português: "lendo tokens.css", "rodando npm test", "editando p1.html"
  inicio: string | null; fim: string | null; tarefa: string | null;
  ferramentas: number; erros: number;
  tokens: { entrada: number; saida: number; cache: number };
}
interface Tarefa {
  id: string; titulo: string; dono: string;
  estado: 'fila' | 'andamento' | 'validacao' | 'aprovada' | 'devolvida' | 'bloqueada';
  devolucoes: number; veredito: string | null; atualizadoEm: string;
}
interface Fala {
  id: string; ts: string; de: string; para: string; tarefa: string | null;
  tipo: 'delegacao' | 'retorno' | 'devolucao' | 'veredito' | 'fala' | 'atividade';
  texto: string; // sempre em português; no máximo 240 caracteres
}
```

Sem transcript da sessão, o estado vem vazio com `agentes: [am]`. Nenhum dado é inventado: campo desconhecido é `null`.

## Linha de comando

- `start.mjs --sessao <id> [--porta 4599] [--abrir nao]`: idempotente. Sobe em processo destacado, grava `.claude/escritorio/portal.json` (`pid`, `porta`, `sessao`), abre o navegador uma vez e imprime só a URL. Com portal ativo em outra sessão, troca a sessão observada.
- `server.mjs --replay <sessao> --velocidade 20`: reproduz uma sessão antiga em tempo acelerado. Dados reais, `modo: 'replay'`.
- `emit.mjs tarefa --id --titulo --dono --estado`, `emit.mjs fala --de --para --tarefa --texto`, `emit.mjs veredito --tarefa --resultado --texto`, `emit.mjs escalada --tarefa`. Sessão lida de `portal.json`.
- `emit.mjs veredito --resultado devolvida` imprime `<ID>: reprovação n/limite no <modelo>. Ação: <ação>.` e, abaixo do limite, devolve a tarefa para `andamento`. Limites: 10 no haiku (ação: escalar) e 3 no sonnet (ação: parar). A contagem vem do log da sessão. `escalada` recusa tarefa abaixo do limite ou já escalada; grava `{ tipo: 'escalada', tarefa, modelo }`, que o coletor ignora.

## Kit do cliente

`portal/web/portal-dados.js` (ES module, sem dependências):

- `conectar(onEstado)`: SSE com reconexão. Sem servidor (`file://` ou erro), carrega `exemplo.json` em laço, com selo visível `DADOS DE EXEMPLO`.
- `detalhe(id)`: `Promise` do detalhe do agente.
- `tema()`: alterna Sistema, Claro e Escuro; persiste em `localStorage` com try/catch.
