# Refatoração visual completa inspirada no OpenCode

## Estado

- Status: **planejado, não iniciado**.
- Próxima etapa obrigatória: criar e aprovar o protótipo visual completo.
- Nenhuma implementação Compose deve começar antes da aprovação explícita do protótipo.
- Este documento é a fonte de verdade desta iniciativa e amplia o escopo do `UI_REDESIGN_PLAN.md`, que tratava apenas de uma rodada visual parcial.

## Objetivo

Refatorar integralmente a identidade visual do Usage Monitor usando como referência:

- https://opencode.ai/
- https://opencode.ai/docs/pt-br

A aplicação deve ficar muito próxima da linguagem visual do OpenCode: tipografia monoespaçada, superfícies neutras, bordas finas, alta densidade, navegação estrutural e hierarquia clara. A inspiração não autoriza copiar logotipo, ilustrações, assets ou código do OpenCode.

A mudança deve preservar integralmente contratos, funcionalidades, dados e fluxos existentes. O escopo é visual e de organização da apresentação.

## Decisões já aprovadas

- Fidelidade: **quase réplica visual**, adaptada ao Usage Monitor.
- Temas: **escuro e claro**, com o escuro como referência principal.
- Tipografia: **IBM Plex Mono em toda a aplicação**.
- Cores: **superfícies neutras com cores apenas para semântica, gráficos e pequenos acentos**.
- Densidade: **compacta e adaptativa**.
- Janelas: **manter o modelo atual de janelas separadas**.
- Protótipo: **completo e navegável**, cobrindo todas as áreas principais.
- Artefatos: **tudo visual**, incluindo interface, PDFs, marca, ícones, screenshots e GIF do README.
- Isolamento Git: **worktree separado e branch `codex/refatoracao-visual-opencode`**.

## Diagnóstico confirmado

A interface atual já utiliza elevação, gradientes e o componente `DepthSurface`. O defeito não é simplesmente ausência de sombras.

Os principais problemas visuais observados são:

- uso quase uniforme de cards grandes;
- raios de 16 a 28 dp em praticamente todas as superfícies;
- botões de ação circulares e visualmente pesados;
- fundos coloridos extensos para diferenciar integrações e estados;
- excesso de containers aninhados com o mesmo peso visual;
- baixa diferenciação entre estrutura, conteúdo, ações e estados;
- densidade inconsistente entre dashboard, histórico, configurações e telas de sessões;
- profundidade baseada em volume e cor, em vez de camadas, divisórias e hierarquia.

O OpenCode usa principalmente:

- IBM Plex Mono como alternativa aberta na pilha tipográfica;
- fundo escuro quase neutro;
- superfícies próximas entre si, separadas por bordas finas;
- raios pequenos;
- controles compactos;
- poucas sombras;
- cor aplicada com disciplina;
- menus e conteúdos organizados estruturalmente.

## Estado técnico encontrado no levantamento

Revalidar tudo antes de iniciar, pois estes dados podem mudar.

- Stack observada:
  - Kotlin `2.1.0`;
  - Compose Multiplatform `1.7.1`;
  - JDK `17`;
  - Material3, Foundation e Material Icons Extended já disponíveis;
  - recursos multiplataforma do Compose já configurados.
- `origin/main` estava no commit `fada75d` durante o levantamento.
- O checkout principal possuía alterações locais protegidas:
  - `build.gradle.kts`: versão `34.0.0` para `35.0.0`;
  - `src/installer/UsageMonitor.nsi`: versão `34.0.0` para `35.0.0`;
  - `img/presence.png` e `img/presence-light.png` não versionados;
  - diretório `output/` não versionado.
- Essas alterações não pertencem à refatoração visual e não podem ser apagadas, movidas, sobrescritas ou incluídas em commit da iniciativa.

## Estratégia de bibliotecas

### Decisão

Não adotar uma nova suíte de componentes visuais.

Manter Compose Foundation e Material3 como infraestrutura e criar um design system próprio para o Usage Monitor.

### Motivos

- Jewel segue a identidade visual do IntelliJ, não a do OpenCode.
- As versões atuais do Jewel exigiriam uma atualização ampla de Compose, Kotlin e JDK, incompatível com o objetivo de uma refatoração predominantemente visual.
- Compose Unstyled oferece componentes headless úteis, mas exigiria migração extensa dos controles atuais sem resolver diretamente os problemas de hierarquia e composição.
- MaterialKolor produz esquemas Material dinâmicos, enquanto a direção aprovada exige uma paleta editorial neutra e controlada.
- Os gráficos existentes já possuem regras de domínio, tooltips, escalas, binning e testes. Substituí-los por uma biblioteca de gráficos criaria risco funcional sem benefício proporcional.

### Recursos externos permitidos

- Arquivos oficiais da IBM Plex Mono em formato TTF ou OTF.
- Licença OFL distribuída junto da fonte.
- Nenhum asset do OpenCode.

## Fase 1 — protótipo obrigatório

### Regra de bloqueio

Criar primeiro um protótipo visual fora do repositório. Não editar código Compose enquanto o usuário não aprovar explicitamente esse protótipo.

### Formato

- Protótipo navegável e interativo apresentado na conversa.
- Dados baseados em `ScreenshotFixtures`, sem informações reais de contas, máquinas, caminhos ou chaves.
- Alternância entre tema escuro e claro.
- Navegação interna apenas para apresentação; ela não representa consolidação das janelas reais.

### Áreas obrigatórias

1. Dashboard.
2. Histórico.
3. Sessões CLI.
4. Detalhe de sessão.
5. Resumo por eixo.
6. Uso do time.
7. Tendência do time.
8. Presença.
9. Configurações — Geral.
10. Configurações — Alertas.
11. Configurações — APIs.
12. Configurações — Contas.
13. Configurações — Time.
14. Administração de chaves.
15. Amostra de relatório PDF.
16. Prancha da nova marca e ícones.

### Estados obrigatórios no protótipo

- sucesso;
- carregamento;
- sucesso parcial;
- erro total;
- lista vazia;
- atualização em andamento;
- card minimizado;
- quota saudável, em atenção e crítica;
- sessão saudável, em atenção e saturada;
- integração de time ativa e inativa;
- ação administrativa destrutiva com confirmação;
- janela larga e janela estreita.

### Critério de aprovação

O usuário deve aprovar explicitamente:

- paleta;
- tipografia;
- marca;
- densidade;
- dashboard;
- telas tabulares;
- configurações;
- PDF;
- tema claro.

Se qualquer um desses pontos for rejeitado, atualizar o protótipo antes de iniciar o código.

## Fase 2 — isolamento do trabalho

Depois da aprovação do protótipo:

1. Revalidar `git status`, `HEAD` e `origin/main` no checkout principal.
2. Não limpar nem fazer stash das alterações locais do usuário.
3. Criar um worktree irmão, sugerido:

   `C:\Users\edils\workspace\usage-monitor-visual-refactor`

4. Criar nele a branch:

   `codex/refatoracao-visual-opencode`

5. Basear a branch no `origin/main` revalidado, sem carregar o estado sujo do checkout principal.
6. Confirmar que o novo worktree inicia limpo antes da primeira edição.

Não fazer commit ou push sem pedido explícito.

## Fase 3 — design system

### Paleta base aprovada

#### Tema escuro

- Background: `#131010`.
- Surface: `#1B1818`.
- Surface raised: `#211E1E`.
- Border: `#3D3838`.
- Foreground: `#F2EDED`.
- Muted foreground: `#B8B2B2`.

#### Tema claro

- Background: `#F6F3F3`.
- Surface: `#FFFCFC`.
- Surface raised: `#EFEAEA`.
- Border: `#D7D0D0`.
- Foreground: `#171414`.
- Muted foreground: `#686060`.

#### Semântica

- Verde: sucesso, atividade saudável e economia.
- Amarelo/laranja: atenção.
- Vermelho: saturação, erro e ação destrutiva.
- Azul: informação, links, seleção e custo quando necessário.
- Cores de integração: somente em pequenos marcadores, linhas de gráfico e identificação local.
- Nunca usar apenas cor para comunicar estado.

Todos os pares de texto e superfície precisam alcançar contraste WCAG AA.

### Tipografia

- IBM Plex Mono em todos os textos.
- Pesos: 400, 500 e 600.
- Escala inicial: 10, 12, 14, 16, 20 e 28 sp.
- Evitar pesos acima de 600.
- Métricas devem usar alinhamento tabular quando disponível.
- Textos longos precisam manter largura de leitura controlada e espaçamento suficiente.

### Formas e profundidade

- Raios: 4, 6, 8 e no máximo 10 dp.
- Evitar pills, exceto quando a forma representar claramente um estado compacto.
- Elevação:
  - 0 dp para base;
  - 2 dp para superfícies destacadas;
  - 8 dp para dialogs, menus e overlays.
- A profundidade principal deve vir de:
  - mudança discreta de superfície;
  - bordas de 1 dp;
  - divisórias;
  - espaçamento;
  - sobreposição controlada.
- Não usar glassmorphism, blur pesado ou gradientes decorativos extensos.

### Espaçamento e motion

- Grade de espaçamento: 4, 8, 12, 16, 24 e 32 dp.
- Motion:
  - 120 ms para hover e foco;
  - 180 ms para seleção e atualização localizada;
  - 240 ms para expansão, recolhimento e entrada de conteúdo.
- Não adicionar animações infinitas.
- Motion deve indicar mudança de estado, foco ou atualização; nunca servir apenas como decoração.

### Primitivas compartilhadas

Criar componentes internos equivalentes a:

- `AppWindowScaffold`;
- `AppTitleBar`;
- `AppToolbar`;
- `AppSectionHeader`;
- `AppDataSurface`;
- `AppDataRow`;
- `AppTabs`;
- `AppButton` e `AppIconButton`;
- `AppTextField`;
- `AppSwitch`;
- `AppProgressTrack`;
- `AppStatusIndicator`;
- `AppBanner`;
- `AppTooltip`;
- `AppEmptyState`;
- `AppLoadingState`;
- `AppErrorState`.

Esses componentes devem continuar stateless: dados entram por parâmetros e eventos saem por lambdas.

## Fase 4 — marca e ícones

### Direção

- Criar um monograma geométrico próprio `UM`.
- Usar construção vetorial determinística.
- Manter legibilidade em 16, 20, 24, 32, 48, 64, 128 e 256 px.
- Não reutilizar nem redesenhar o logotipo do OpenCode.
- Criar um wordmark `usage monitor` com IBM Plex Mono.

### Entregáveis

- Fonte vetorial da marca.
- `app_icon.png`.
- `app_icon.ico` com múltiplas resoluções.
- `app_icon.icns`.
- recursos usados por janela, bandeja, instalador e distribuições.
- validação do overlay de risco do ícone da bandeja.

## Fase 5 — aplicação por tela

### Dashboard

- Manter layout responsivo de uma ou duas colunas.
- Substituir grandes cards coloridos por painéis neutros.
- Identificar a fonte por marcador, texto e pequeno acento.
- Transformar cada quota numa linha compacta contendo:
  - rótulo;
  - valor principal;
  - percentual ou saldo;
  - barra horizontal;
  - reinício ou observação.
- Preservar:
  - múltiplos perfis Anthropic;
  - todas as seis integrações;
  - refresh individual;
  - histórico;
  - sessões CLI;
  - uso do time;
  - presença;
  - minimização;
  - reordenação;
  - semáforo de risco;
  - avisos persistentes;
  - atualização disponível;
  - sucesso parcial.
- Converter o rodapé numa status bar compacta com versão, contagem regressiva, refresh global e configurações.

### Histórico

- Reunir API, conta e intervalo numa toolbar compacta.
- Remover chips excessivamente grandes.
- Usar painel principal neutro para o gráfico.
- Organizar métricas em estrutura tabular.
- Preservar:
  - seleção por fonte;
  - seleção de conta;
  - `24h`, `7 dias`, `30 dias` e `Total`;
  - reinícios de janela;
  - média por hora;
  - forecast;
  - comparação com período anterior;
  - regras específicas de saldo e créditos.

### Sessões CLI

- Converter a listagem em linhas alinhadas, visualmente próximas de tabela.
- Usar fundo neutro e marcador semântico discreto.
- Preservar:
  - janelas `5h`, `7 dias`, `30 dias` e `Total`;
  - atualização ao vivo;
  - status de saúde;
  - tokens, cache, custo e tempo ativo;
  - comando de retomada;
  - abas Sessões e Resumo;
  - exportação CSV, JSON e PDF;
  - busca, ordenação, paginação e eixos do resumo;
  - avisos de indexação, dados antigos e atualização.

### Detalhe de sessão

- Criar cabeçalho compacto com voltar, ID e ação de copiar.
- Dar destaque estrutural à recomendação de saturação.
- Organizar métricas principais numa grade neutra.
- Manter gráfico de contexto por turno como elemento principal.
- Preservar seção Avançado, composição de tokens, cache, custo, economia e tooltips.

### Uso do time

- Aplicar a mesma estrutura tabular das sessões locais.
- Preservar integrantes expansíveis, sessões, resumo, tendência e PDF.
- Manter escalas compartilhadas nos gráficos de tendência.
- Preservar modo administrativo e confirmações destrutivas.

### Presença

- Criar cabeçalho compacto com totais e filtro.
- Organizar integrantes em linhas com colunas estáveis.
- Preservar conectado/desconectado, trabalhando/parado, status de saúde e ações administrativas.
- Garantir que a própria máquina continue protegida contra remoção inválida.

### Configurações

- Manter a janela separada.
- Alterar o tamanho inicial para aproximadamente `820 x 720 dp`.
- Substituir a fileira de chips por navegação lateral inspirada na documentação do OpenCode.
- Manter conteúdo rolável e scrollbar sempre identificável.
- Preservar integralmente:
  - Geral;
  - Alertas;
  - APIs;
  - Contas;
  - Time;
  - tema;
  - idioma;
  - auto-start;
  - always-on-top;
  - opacidade;
  - orçamento;
  - limiares de alerta;
  - perfis Anthropic;
  - integração de time;
  - modo administrativo;
  - toasts e validações.

### Administração de chaves

- Aplicar o mesmo shell visual das demais janelas.
- Manter criação, listagem, limites, vínculos, revogação e mensagens de erro.
- Não alterar contratos do servidor.

### Molduras das janelas

- Manter janelas undecorated.
- Padronizar title bar, área arrastável, título, marca e controles.
- Preservar:
  - fechar;
  - minimizar quando existente;
  - redimensionar;
  - always-on-top;
  - opacidade;
  - ativação ao reabrir;
  - geometria persistida;
  - expansão segura do histórico;
  - encerramento correto de ViewModels e `HttpClient`.

## Fase 6 — relatórios PDF

- Manter o PDF como layout canônico escuro, independentemente do tema ativo da interface.
- Embutir IBM Plex Mono no PDFBox.
- Preservar o saneamento de texto existente nesta iniciativa; não transformar a refatoração visual numa migração de contrato Unicode.
- Aplicar:
  - nova marca;
  - paleta escura;
  - bordas finas;
  - cabeçalhos compactos;
  - métricas alinhadas;
  - tabelas com alternância discreta de superfície;
  - cores semânticas restritas.
- Preservar todas as seções, valores, paginação, rodapé e conteúdo dos relatórios.
- Validar relatórios curto, médio e longo.

## Fase 7 — screenshots, tour e documentação

- Atualizar `ScreenshotGenerator` usando os componentes reais.
- Manter dados totalmente sintéticos.
- Gerar e revisar:
  - dashboard;
  - histórico;
  - configurações;
  - integração de time;
  - sessões CLI;
  - detalhe de sessão;
  - uso do time;
  - presença dark;
  - presença light.
- Adicionar capturas ausentes caso o novo visual dependa delas para demonstrar resumo, tendência ou administração.
- Atualizar `tour.gif` para refletir a nova sequência visual.
- Atualizar descrições do README apenas quando a mudança visual tornar o texto atual incorreto.

## Contratos que não podem mudar

- Nenhuma mudança em entidades de domain.
- Nenhuma mudança em DTOs ou mappers.
- Nenhuma mudança em repositories ou data sources.
- Nenhuma mudança em endpoints ou autenticação.
- Nenhuma mudança em SQLite ou preferências persistidas.
- Nenhuma mudança nos intervalos de polling.
- Nenhuma mudança no agrupamento por `accountUuid`.
- Nenhuma mudança nos contratos de time, presença, sessões ou alertas.
- Nenhuma mudança funcional nos ViewModels.
- Nenhuma remoção de ação, campo, filtro, gráfico, exportação ou mensagem existente.

Mudanças de assinatura são aceitáveis apenas em componentes internos de UI quando necessárias para aplicar tokens, estados visuais ou primitivas compartilhadas.

## Testes automatizados

Executar durante a implementação:

```bat
gradlew.bat desktopTest --tests "com.usagemonitor.ui.*"
gradlew.bat desktopTest --tests "com.usagemonitor.data.PdfUsageReportRendererTest"
```

Executar ao final:

```bat
gradlew.bat allTests
gradlew.bat build
gradlew.bat generateScreenshots
gradlew.bat generateTourGif
gradlew.bat createDistributable
```

Não usar a task raiz `test`, pois ela não existe neste projeto KMP.

Em validações pesadas no Windows:

- não executar múltiplas tarefas Gradle pesadas em paralelo;
- usar `--no-daemon` e heap maior somente se houver evidência de OOM ou worker residual;
- se o empacotamento falhar ao substituir o executável, verificar primeiro o atributo read-only do artefato gerado.

## Testes de UI obrigatórios

- Ações continuam localizáveis por semântica ou `testTag`.
- Navegação das configurações funciona por mouse e teclado.
- Botões de refresh individual e global funcionam.
- Card minimiza, expande e reordena.
- Histórico abre com a fonte e conta corretas.
- Sessões abrem para o perfil correto.
- Detalhe abre e fecha sem perder o estado da lista.
- Exportações continuam disponíveis na aba correta.
- Integrantes do time expandem e recolhem.
- Ações destrutivas continuam exigindo confirmação.
- Scrollbars permanecem visíveis e não sobrepõem conteúdo.
- Toasts e banners continuam acessíveis.

## QA visual obrigatório

Revisar manualmente:

- temas dark e light;
- idiomas PT e EN;
- uma e duas colunas no dashboard;
- tamanhos mínimos de cada janela;
- escala do Windows em 100% e 150%;
- hover, pressed, focus e disabled;
- loading, empty, success, partial success e error;
- conteúdos longos;
- múltiplas contas Anthropic;
- todas as integrações;
- contraste WCAG AA;
- alinhamento de colunas e métricas;
- tooltips dos gráficos;
- ícone em 16, 20, 24, 32, 48, 64, 128 e 256 px;
- ícone da bandeja com e sem risco;
- PNG, ICO e ICNS;
- relatório PDF curto, médio e longo;
- screenshots e GIF gerados.

## Critérios de aceite

A iniciativa só pode ser considerada concluída quando:

1. O protótipo completo tiver sido aprovado.
2. Todas as áreas visuais listadas estiverem migradas.
3. Nenhuma funcionalidade existente tiver sido removida ou alterada.
4. Dark e light tiverem paridade funcional e visual.
5. Todos os testes automatizados passarem.
6. Todas as capturas tiverem sido inspecionadas.
7. Os PDFs tiverem sido renderizados e inspecionados.
8. O pacote desktop tiver sido gerado com o novo ícone.
9. O checkout principal continuar intacto.
10. O diff final estiver restrito à iniciativa visual.

## Regras de publicação

- Não fazer commit ou push sem pedido explícito.
- Quando houver pedido de publicação, usar a skill local `usage-monitor-commit-push`.
- Fazer stage explícito apenas dos arquivos da refatoração.
- Não incluir `.kotlin/`, `output/`, mudanças de versão preexistentes ou outros artefatos fora do escopo.
- Commit, push, PR e release são etapas separadas.

## Handoff para a próxima sessão

Ao retomar:

1. Ler `AGENTS.md` integralmente.
2. Ler este documento integralmente.
3. Verificar `git status`, `HEAD` e `origin/main`.
4. Não tocar nas mudanças locais protegidas do checkout principal.
5. Criar apenas o protótipo completo fora do repositório.
6. Apresentar o protótipo ao usuário.
7. Aguardar aprovação explícita.
8. Somente depois criar worktree/branch e iniciar a implementação.

