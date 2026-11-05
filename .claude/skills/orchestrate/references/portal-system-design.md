# System design do portal

Escopo: **somente** o portal de acompanhamento (`portal/web/*`). Este documento não rege projetos do usuário, protótipos entregues a ele nem o código que os agentes escrevem. O contrato de dados continua em `portal-contract.md` e não muda por causa do visual.

Direção escolhida: **Circuito** (protótipo P10): mapa isométrico em estilo metrô. A orquestradora é o hub, um tronco sai dela e cada tipo de agente é uma estação num ramal.

## 1. Princípios

1. **Limpo.** Superfícies planas, uma cor de acento por significado, sem textura, papel ou ornamento.
2. **Isométrico sem sobreposição.** Nenhum objeto, rótulo ou etiqueta cobre outro. A regra é verificável (seção 5), não estética.
3. **Claro e escuro com paridade.** Mesmos tokens, mesma hierarquia; só os valores mudam.
4. **Zero dependência e offline.** Fontes do sistema, SVG inline, nenhum CDN. Funciona por `file://` com o kit local de exemplo.
5. **Dado real manda.** Campo desconhecido aparece como `—`. Nada é inventado para preencher a cena.
6. **Só o portal.** Arquivos tocados: `portal/web/index.html`, `portal/web/tokens.css` e o trecho visual do selo em `portal/web/portal-dados.js`. Servidor, coletor, `emit.mjs` e API ficam intactos.

## 2. Tokens (`portal/web/tokens.css`)

Claro em `:root`; escuro por `prefers-color-scheme` (quando não há `data-theme="light"`) e por `:root[data-theme="dark"]`. `.force-light` e `.force-dark` forçam um tema num subtrecho.

| Token | Claro | Escuro | Uso |
|---|---|---|---|
| `--bg` | `#f3f5f8` | `#0c0f14` | fundo da página |
| `--surface` | `#ffffff` | `#141920` | cartões, cabeçalho, cena |
| `--fg` / `--muted` | `#12161c` / `#5a6575` | `#e8ecf2` / `#8b96a6` | texto |
| `--line` | `#dde2e9` | `#252c37` | bordas, trilhos tracejados |
| `--accent` | `#2f6df6` | `#6ea0ff` | seleção, foco, fluxo ativo |
| `--plate` / `--plate-2` | `#e7ebf1` / `#d9dfe8` | `#1b232d` / `#27313e` | volumes neutros e trilhos |
| `--edge` | `rgba(18,22,28,.26)` | `rgba(0,0,0,.55)` | contorno das faces |
| `--sh-l` / `--sh-r` | `11%` / `24%` | `24%` / `42%` | escurecimento das faces esquerda e direita |

Cores de estado (agente) `--c-ocioso`, `--c-trabalhando`, `--c-aguardando`, `--c-concluido`, `--c-erro`; (tarefa) `--c-fila`, `--c-andamento`, `--c-validacao`, `--c-aprovada`, `--c-devolvida`, `--c-bloqueada`. Os valores estão em `tokens.css`; o escuro usa tons mais claros. Contraste dos tons não foi medido formalmente.

Cor nunca é o único sinal de estado: todo estado também aparece como texto (rótulo da estação, chip, legenda).

Tipografia: `--sans` = `ui-sans-serif, system-ui, "Segoe UI", Roboto, sans-serif`; `--mono` = `ui-monospace, "Cascadia Mono", Consolas, monospace` (horários, ids, números). Raio dos cartões `--r: 14px`, controles `8px`.

## 3. Geometria isométrica

- Projeção 2:1. Cena do portal: `tw = 80`, `th = 40` (largura e altura de uma célula).
- `P(x, y, z) = [ox + (x − y)·tw/2, oy + (x + y)·th/2 − z]`. `x` cresce para baixo-direita, `y` para baixo-esquerda, `z` (px) para cima.
- `S(sx, sy)` é a inversa no chão (`z = 0`). Objetos são **posicionados em coordenadas de tela** e convertidos por `S`; assim o espaçamento é controlado em pixels, não em células.
- Caixa = 3 polígonos: topo `t`, face esquerda `l` (em `y + d`), face direita `r` (em `x + w`). Cor-base vem de `--fc`; `l` e `r` usam `color-mix(in srgb, var(--fc), #000 var(--sh-l|--sh-r))`.
- Ordem de pintura: ordem do desenho = ordem de profundidade. Trilhos primeiro, depois estações por `x + y` crescente, depois etiquetas.

## 4. Composição da página

```
┌ cabeçalho: marca · modo (ao vivo / replay / exemplo) · sessão · atualizado · totais · tema
├ cena Circuito (coluna larga) ──────────────┬ coluna lateral (360 px)
│ hub + tronco + ramais + estações           │ quadro de tarefas (por estado)
│ legenda de estados                         │ registro de comunicação + filtro
└────────────────────────────────────────────┴ (abaixo de 980 px: tudo em coluna única)
diálogo: posto → lista de execuções → detalhe da execução (prompt, passos, retorno)
```

Cena Circuito:

- **Hub** = tipo `am` (orquestradora). Plataforma 2×2, bloco interno na cor do estado.
- **Tronco** sai do hub ao longo do eixo `x`. Cada **ramal** desce do tronco ao longo de `y`, alternando para cima-direita e baixo-esquerda, uma **estação** por posto.
- **Postos** (um por tipo, não por execução): `explorer`, `backend-dev`, `frontend-dev`, `prototype-dev`, `test-runner` e `outro` (só aparece se existir). Várias execuções do mesmo tipo viram **uma** estação com contagem. Isso limita a cena a 1 hub + 6 estações, sem importar quantos subagentes a sessão criou.
- **Rótulo** da estação fica ao lado oposto ao ramal (ramal de cima: à direita; ramal de baixo: à esquerda), nunca sobre o trilho.
- **Etiqueta de tarefa** (pílula com o id) fica acima da estação dona de tarefa não aprovada.
- **Fluxo:** ramal de posto `trabalhando` ganha traço animado em `--accent`. Cada fala nova solta um **pacote** (ponto) que percorre a rota hub ↔ estação.

## 5. Regra de não sobreposição

1. Cada objeto de cena (hub, estação, com rótulo e etiqueta) é um `<g data-obj>` e **não pode** cruzar polígonos de outro `data-obj`.
2. Trilhos, pacotes e anel de fundo não entram na regra (são fundo ou efeito).
3. Nenhuma forma pode sair do `viewBox`.
4. O `viewBox` é calculado a partir da geometria do circuito, com folga de 24 px, e não é fixo.
5. Verificação: `index.html?checar` roda o teste (SAT entre polígonos convexos, tolerância 1 px) para o quadro atual e escreve `{"falhas":[…]}` em `<pre id="resultado-checar">`. Falha em qualquer objeto reprova a alteração visual.

Comando: `chrome --headless=new --virtual-time-budget=4000 --dump-dom "http://127.0.0.1:4599/?checar"` e procurar `resultado-checar`.

## 6. Mapeamento dado → visual

| Dado (`portal-contract.md`) | Visual |
|---|---|
| `agentes[].estado` (maior prioridade do posto: erro > trabalhando > aguardando > concluído > ocioso) | cor do bloco da estação e texto do rótulo |
| número de execuções do posto | linha de contagem no rótulo (`2 em andamento`, `3 concluídas`) |
| `agentes[].ferramentas` | altura do bloco (8 a 38 px) |
| `tarefas[]` não aprovadas, por `dono` | pílula com o id sobre a estação; card no quadro |
| `tarefas[].devolucoes` | contador `devol. n` no card |
| `falas[]` | linha no registro; pacote na rota |
| `modo` (`ao-vivo` / `replay`) e dados de exemplo | chip do cabeçalho; selo `DADOS DE EXEMPLO` quando for exemplo |
| `totais` | cabeçalho |

## 7. Interação e acessibilidade

- Estação e hub são `role="button"`, `tabindex="0"`, com `aria-label` = nome, estado e contagem; Enter e Espaço abrem o diálogo.
- Foco visível: contorno de 2 px em `--accent` com offset de 3 px.
- O diálogo usa `<dialog>`: Esc fecha, o foco volta ao elemento que abriu.
- `prefers-reduced-motion`: sem traço animado e sem pacotes; o estado continua legível pelo texto.
- A lista do registro é `aria-live="polite"`.
- Leitor de tela: a cena é `role="group"` com rótulo; o conteúdo equivalente está nos cards e no registro.

## 8. Atualização

O estado completo chega a cada mudança (SSE, máx. 2 por segundo). A cena é redesenhada por inteiro a cada estado cuja assinatura (posto, estado, contagem, tarefas) mudou; sem mudança, nada é redesenhado. Pacotes novos só nascem para falas ainda não vistas; na primeira carga as falas existentes não animam.

## 9. Fora de escopo e alternativas descartadas

- Não há texto em linguagem de usuário final dentro da cena além de nome, estado e id.
- Os outros 9 protótipos (ladrilhos, torres de tokens, esteira, mesas, andares, órbita, racks, raias, ilhas) estão em `prototipos/portal/` do repositório de desenvolvimento e **não** fazem parte da skill instalada.
- Removido do portal anterior: papel, carimbos, fita de telex, balões, objetos voadores e a faixa de devolução. A devolução continua visível no card (`devol. n`, coluna Devolvida) e no registro (tipo Devolução).
