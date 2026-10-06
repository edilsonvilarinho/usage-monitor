# Issue #383 — plano de adequação do histórico

Data: 06/10/2026. Estado: direção 01 escolhida; UI e PDF implementados no checkout. Verificação automatizada e inspeção de fixtures concluídas; QA manual Windows pendente.

Publicação: o pedido posterior “cria pr” autorizou branch, commits, push e PR das mudanças descritas, em 06/10/2026. As notas abaixo sobre ausência de autorização registram o estado anterior a esse pedido. Aceite manual e release continuam pendentes. Capturas sintéticas antes/depois dos dois temas e tabela a 125% estão versionadas em `issue383-visual/evidence/`.

Issue: https://github.com/edilsonvilarinho/usage-monitor/issues/383 — **Modal de histórico**.

## Pedido e limite desta entrega

A issue solicita melhorar a organização visual do histórico, apresentar **dez sugestões de tela para escolha do direcionamento** e permitir exportar os dados em PDF.

Este documento registra a análise e a sequência de execução. O pedido posterior “pode continuar” autorizou avançar na base e nas propostas visuais. A escolha posterior “pode ser a 01 resumo por detalhe” definiu **01 — Resumo, gráfico e detalhes**; a continuação aplicou essa composição e a exportação de dados. Commit, push, alteração da issue e release não foram solicitados. A alteração preexistente em `server/package-lock.json` fica fora do trabalho.

## 1. O que dá para afirmar com segurança

| Evidência no checkout antes da adequação | Consequência para o plano |
| --- | --- |
| `HistoryScreen.kt`: cabeçalho, `HistoryControls` e relatório estão dentro da mesma `Column.verticalScroll` | Filtros desaparecem durante a rolagem. Separar comandos e conteúdo é uma mudança concreta de layout. |
| `HistoryMetrics.kt`: métricas já usam duas colunas de pares rótulo/valor; valores longos têm duas linhas e reticências | A correção exige hierarquia e espaço para leitura; não basta acrescentar alinhamento que já existe. |
| `HistoryScreen.kt`: o card pode conter gráfico, resumo intervalar, análise intervalar, resumo semanal e análise semanal | Há vários níveis de informação em uma mesma superfície. A exploração deve comparar maneiras de separar resumo e detalhe. |
| `HistoryWindowAnalysis.kt`: tabela de janelas, distribuição horária e limite de oito janelas recentes na tela | A apresentação deve explicar o limite; o PDF não deve herdá-lo silenciosamente. |
| `HistoryProviderCards.kt`: DeepSeek e atividade local têm composições próprias; atividade local repete a última coleta já presente no rodapé | Consolidar contexto repetido e desenhar variações por tipo de dado, sem impor uma anatomia de cota a todas as fontes. |
| `ModalWindowsHost.kt`: histórico aberto por fonte, `showSourceSelector = false`, largura mínima de 320dp | O cenário principal é o modal por fonte. A captura com todas as APIs não representa sozinha esse fluxo. Layouts largos precisam de adaptação ou de uma decisão explícita de largura mínima. |
| `HistoryViewModel.kt`: não recebe `UsageExportWriter` nem oferece exportação | PDF ainda precisa de ação, estado, montagem do documento e ligação no grafo. |
| `UsageExportRequests.kt`, `DesktopUsageExportWriter.kt`, `UsageReportDocument.kt`, `PdfUsageReportRenderer.kt` | Já existem payload de relatório, seletor de destino, escrita fora da EDT, PDFBox e paginação. Reutilizar essa infraestrutura. |
| `UsageReportSection`: suporta métricas, tabelas e grade de atividade; não oferece seção de gráfico de linhas | PDF de dados pode ser entregue com as seções existentes. Um gráfico vetorial exige extensão própria e não deve ser presumido pronto. |
| `UsageHistoryRepositoryImpl.kt`: `TOTAL` amostra até 720 pontos por série; as janelas são calculadas antes dessa amostragem | Exportar o relatório carregado não equivale a exportar todos os snapshots brutos do banco. A diferença precisa estar escrita no relatório. |
| `ApiUsageHistoryReport`: contém intervalo nominal e última coleta, mas não os limites absolutos usados na consulta | Registrar o recorte resolvido no carregamento; recalculá-lo na exportação pode produzir datas diferentes das que originaram os dados. |

A imagem versionada `img/history.png` foi inspecionada como referência: mostra gráfico e resumos em painéis aninhados. Ela não exibe todos os controles e análises presentes no código atual e **não comprova a aparência da aplicação instalada**.

## 2. O que ainda é hipótese

- A percepção de texto disperso pode decorrer da competição entre gráfico, métricas, previsões longas e painéis aninhados, além de variações de largura e escala.
- A organização recomendada abaixo pode reduzir o esforço de leitura; isso depende da comparação visual e da escolha do usuário.
- Não há evidência suficiente para atribuir uma causa visual única ou afirmar que o problema atinge todas as fontes da mesma forma.

## 3. Evidência que falta

- Capturas atuais do modal real por fonte, inclusive com ambas as cotas e lista longa de modelos.
- Comparação em largura reduzida, nomes de conta longos, temas claro/escuro e escala elevada.
- Escolha concluída: direção 01, registrada na conversa em 06/10/2026.
- PDF implementado como relatório estruturado de métricas, janelas, distribuição horária e leituras; gráficos de linhas não foram acrescentados ao renderizador.

## 4. Como validar

Gerar uma base atual com fixtures sintéticas, comparar as dez alternativas usando os mesmos dados e executar tarefas de leitura: localizar uso atual, identificar intervalo/cota, consultar previsão quando suportada, encontrar janelas anteriores e localizar exportação. Registrar cortes, repetição, passos de navegação e necessidade de rolagem. Após a escolha, confirmar o comportamento no aplicativo Windows.

## 5. Próximo passo seguro

A1 e A2 foram concluídas; a escolha ocorreu depois da entrega das dez telas. A direção 01 foi aplicada em A3 e o PDF em A4/A5. Concluir a verificação A6 e manter QA manual separado dos testes offscreen.

## Diretrizes da adequação

- Usar a skill `.codex/skills/usage-monitor-design/SKILL.md`, `docs/design-system/readme.md`, tokens e contratos das primitivas.
- Comparar composição e navegação, mantendo a linguagem visual: superfícies neutras, IBM Plex, números alinhados, estados com palavra, cor de fonte e espaçamento existente.
- Manter fonte, conta, intervalo e ação de PDF acessíveis fora da rolagem dos dados. Em largura normal, usar a toolbar prevista no sistema; em largura reduzida, projetar uma solução que comporte os controles sem comprimi-los ou alterar tokens silenciosamente.
- Distinguir **intervalo consultado** (`24h`, `7 dias`, `30 dias`, `Total`) de **cota exibida** (intervalar, semanal, ambas). O nome da cota vem da série: MiniMax não deve receber um rótulo fixo de 5h.
- Destacar poucas métricas primárias; previsões e explicações longas ganham espaço próprio. Todo detalhe atual continua acessível.
- Não somar porcentagens de cotas ou unidades incompatíveis. Não apresentar previsão, limite ou reinício para atividade local sem esses dados; preservar a separação entre série Codex reportada e legado.
- Manter zoom, tooltip, reinícios e comparação do período anterior com as regras atuais. A extensão do PDF não muda coleta, APIs ou persistência.
- Evitar tabelas que cortem nomes e valores; adaptar colunas ao espaço. Informação essencial não pode depender exclusivamente de hover.

## Dez direções para prototipar

As dez propostas foram renderizadas com o mesmo conjunto de dados sintéticos. Os IDs da tabela correspondem à ordem do carrossel; os nomes exibidos não têm prefixo numérico. Fontes editáveis e revisão local em `docs/planos/issue383-visual/`.

| ID | Direção | Organização | Benefício esperado e custo |
| --- | --- | --- | --- |
| 01 | Resumo, gráfico e detalhes | Até quatro métricas primárias por linha, gráfico central, análises expansíveis abaixo | Boa visão inicial e preservação de detalhes; exige tornar a expansão clara. **Recomendação inicial.** |
| 02 | Abas por tarefa | Visão geral, janelas e distribuição; filtros e PDF fixos | Reduz extensão da tela; exige navegação para cruzar informações. |
| 03 | Gráfico com resumo lateral | Gráfico amplo e coluna de métricas; detalhes abaixo | Leitura simultânea em janela larga; precisa empilhar na largura reduzida. |
| 04 | Tabela de resumo com gráfico de detalhe | Uma linha por série/modelo; seleção abre gráfico e análise | Adequado a muitas séries; o gráfico exige seleção explícita. |
| 05 | Lista lateral de modelos | Navegação por modelo/família e área única de análise | Evita pilha longa de cards; precisa apresentar contexto da seleção e adaptar a navegação em janela estreita. |
| 06 | Seções por pergunta | “Uso atual”, “Como variou”, “Janelas observadas” e “Distribuição horária” | Hierarquia por intenção de leitura; títulos e agrupamentos devem acompanhar a semântica de cada fonte. |
| 07 | Janelas em primeiro plano | Tabela de janelas recente, resumo acima e gráfico complementar | Facilita investigar esgotamentos; precisa de alternativa para saldo e fontes sem janelas. |
| 08 | Comparação entre cotas | Resumos intervalar/semanal lado a lado e gráfico compartilhado | Facilita comparar as duas cotas sem repetir estrutura; empilhar em janela estreita e separar unidades diferentes. |
| 09 | Lista compacta expansível | Uma linha de resumo por série/modelo, expansão com gráfico e métricas | Boa densidade com muitos modelos; detalhes não podem ficar difíceis de descobrir. |
| 10 | Relatório contínuo por seções | Cabeçalhos claros, métricas alinhadas, gráfico e tabelas em sequência, sem painéis dentro de painéis | Leitura linear e relação clara com PDF; conserva mais rolagem. |

**Escolha registrada:** 01 — Resumo, gráfico e detalhes. A orientação inicial era 01 como base; 04, 05 e 09 são comparações prioritárias para muitas séries. A recomendação não substitui a escolha. Uma direção pode ter variações para cota, saldo e atividade local; não serão dez implementações de produção.

### Contrato da galeria

- Galeria HTML local com opções 01–10, comparação lado a lado e alternância claro/escuro. Usar `styles.css`, `tokens/`, `assets/` e primitivas via `_ds_local.js`, conforme a skill.
- Fixture principal: conta com cotas intervalar e semanal, incluindo reinício, previsão longa e mais de oito janelas. Fixture complementar: múltiplos modelos de atividade local.
- Não adicionar números calculados apenas para decorar o mock. Usar dados sintéticos e semântica já suportada.
- Validar o modal por fonte sem seletor de API; manter uma amostra do modo com seletor para compatibilidade.
- Documentar resposta em largura reduzida, escala elevada, estado sem dados e troca de filtro. Conferir nomes de conta e modelo longos antes da seleção.
- Critérios de escolha: leitura do contexto, prioridade dos dados, acesso aos detalhes, adaptação e aderência ao sistema visual.

## Contrato proposto para o PDF

1. **Escopo:** relatório da fonte e conta selecionadas, no intervalo carregado, respeitando o seletor de cotas quando aplicável. Expansão de cards e zoom são estados de navegação: não retiram dados do relatório. Essa regra deve aparecer no texto da ação/ajuda.
2. **Coerência:** capturar relatório, conta, intervalo, cota e idioma no início da ação. Desabilitar exportação em carregamento, erro, ausência de séries, releitura e exportação em andamento, com motivo legível. `isRefreshing` merece cobertura específica: o estado pode indicar o novo intervalo enquanto conserva o relatório anterior.
3. **Tempo:** transportar os limites absolutos resolvidos no carregamento em contrato aditivo. Separar intervalo consultado, primeira/última observação disponível e horário de geração. Em `Total`, não imprimir o sentinela `Long.MIN_VALUE` como data; informar “todo o histórico disponível” e a cobertura observada.
4. **Conteúdo:** cabeçalho com fonte/conta, filtros e horários; métricas das séries incluídas; tabela de janelas; distribuição horária em tabela; ressalvas de cobertura, limite indisponível e comparação. Exportar todas as janelas contidas no relatório, sem o corte visual de oito. Uma seção de pontos pode usar os pontos já carregados, identificados como amostrados em `Total`; não anunciar exportação bruta integral.
5. **Formatação:** aproveitar funções puras de agrupamento e formatação de histórico. Extrair as métricas específicas de DeepSeek hoje montadas no composable para que tela e PDF compartilhem os mesmos valores/rótulos. Ausência de dado permanece explícita; não converter desconhecido em zero.
6. **Arquitetura:** builder de histórico em arquivo próprio em `presentation/ui/report`, com `UsageReportDocument`; request específica de histórico, sem adaptar `HistoryRange` artificialmente a `CliSessionRange`; `UsageExportPayload.Report` → `UsageExportWriter` → writer/renderizador desktop. PDF não entra no enum de formatos CSV/JSON.
7. **Destino e resultado:** seletor de arquivo já existente; nome sugerido com histórico, fonte, intervalo e data, sem e-mail ou segredo. Cancelamento não produz mensagem de sucesso/erro; sucesso informa destino; falha mantém a tela e permite tentar novamente.
8. **Desempenho e ciclo de vida:** geração/gravação fora da EDT, prevenção de cliques duplicados e cancelamento da coroutine no encerramento. Mudanças de filtro durante uma exportação não alteram o snapshot capturado. Validar o vínculo do seletor de destino com a janela de histórico e evitar resultado associado a outra conta.
9. **Visual:** reaproveitar A4 em paisagem, paleta escura canônica, IBM Plex e paginação existentes. Verificar cabeçalhos repetidos, números, textos longos e rodapé em documentos extensos. O renderizador atual trunca células e valores: escolher seções/larguras ou acrescentar quebra de texto onde isso causar perda de conteúdo relevante.
10. **Gráficos:** o requisito de dados em PDF pode ser atendido por métricas e tabelas. Se a direção escolhida exigir gráfico de linhas no documento, planejar uma seção vetorial e testes próprios antes de implementar; não usar captura da janela como relatório.

## Atividades sequenciais

Executar uma atividade por vez. Cada conclusão atualiza este documento com arquivos efetivamente alterados, evidências e pendências. Os commits abaixo são unidades previstas; só executar commit/push após pedido explícito.

### A1 — base atual e contrato de conteúdo

- **Objetivo:** confirmar a superfície visual e mapear quais dados existem por fonte.
- **Arquivos:** `HistoryScreen.kt`, `HistoryMetrics.kt`, `HistoryProviderCards.kt`, `HistoryWindowAnalysis.kt`; fixtures/gerador em `src/desktopTest/kotlin/com/usagemonitor/screenshots/`; este plano.
- **Aceite:** capturas atuais do modal por fonte, matriz cota/saldo/reportado/atividade local e inventário de métricas com contexto e unidades. Se for preciso acrescentar cenário ao gerador, saída específica em `build/issue383/`.
- **Validação:** comparação com código, kit `History.jsx` e seção Histórico do protótipo; inspeção das capturas. Testes já existentes servem como mapa, não como aprovação visual.
- **Risco:** tratar screenshot antigo ou fluxo com seletor de API como reprodução do modal real.
- **Commit previsto:** documentação e fixtures sintéticas necessárias à base atual.
- **Estado:** base inicial concluída — 24 capturas de cota, reportado, saldo e atividade local, em três larguras e dois temas. Escala global de 125% verificada na continuação A6; aplicativo instalado permanece pendente.

### A2 — dez propostas e escolha

- **Objetivo:** tornar as opções comparáveis e permitir escolher uma direção.
- **Arquivos:** galeria nova em `docs/planos/` com recursos locais do design system; este plano. Não substituir ainda o protótipo aprovado.
- **Aceite:** dez composições distintas, temas e comportamento em espaço reduzido documentados; nenhum campo sem fonte/semântica; recomendação justificada pelos critérios da galeria.
- **Validação:** abrir e inspecionar todas as opções; checar conteúdo completo, foco, controles e dados idênticos entre propostas.
- **Risco:** opções que só mudam cores ou inventam métricas não atendem ao pedido.
- **Commit previsto:** galeria e registro de avaliação/decisão.
- **Estado:** dez propostas renderizadas e inspecionadas; direção 01 escolhida pelo usuário. A conversa usa o carrossel da skill `visualize`; `index.html` usa seletor externo. A comparação simultânea prevista foi substituída por navegação entre propostas, preservando seus estados locais.

### A3 — composição escolhida na UI

- **Objetivo:** aplicar hierarquia, controles fixos e navegação escolhidos preservando regras do histórico.
- **Arquivos:** `HistoryScreen.kt`, `HistoryGroupedContent.kt`, `HistoryProviderCards.kt`, `HistoryMetrics.kt`, `HistoryWindowAnalysis.kt`; componentes de histórico novos se necessário; `HistoryScreenTest.kt`; `docs/design-system/ui_kits/desktop-app/History.jsx`; seção Histórico do protótipo.
- **Aceite:** comandos disponíveis ao rolar, métricas essenciais legíveis, detalhes acessíveis, contexto único e distinção clara de intervalo/cota; todas as variantes suportadas. O modo estreito funciona na largura mínima vigente ou registra mudança explícita de geometria.
- **Validação:** testes Compose de navegação, rolagem, expansão/abas quando adotadas, conta/cota, nomes longos e janela reduzida; capturas dos dois temas. Preservar testes de agrupamento e de série reportada.
- **Risco:** regressão de tooltip/zoom, filtros escondidos, dado de conta anterior ou aparência incompleta em fontes menos usadas.
- **Commit previsto:** UI, testes e referências visuais atualizadas juntos; se criar primitiva, incluir consumo e contrato no mesmo commit. Arquivos até 800 linhas e funções até 300, sem exceção nova.
- **Estado:** implementada a direção 01. Resumo de três métricas antes do gráfico; detalhes das cotas abertos e análise de janelas recolhida; filtros fixos com quebra de linha, métricas e janelas empilhadas em largura inferior a 600dp. Referências `History.jsx` e protótipo atualizadas. Verificação automatizada concluída.

### A4 — documento de histórico e recorte temporal

- **Objetivo:** montar um relatório puro e coerente com os dados carregados.
- **Arquivos:** novo builder de histórico em `presentation/ui/report/`; `UsageExportRequests.kt` ou request de histórico em arquivo separado; `UsageHistoryModels.kt`, `UsageHistoryRepositoryImpl.kt` e testes para limites absolutos; funções de métricas compartilhadas e testes novos de relatório.
- **Aceite:** fonte/conta/intervalo/cota corretos, timestamps do carregamento, métricas iguais às da tela, todas as janelas disponíveis, ressalvas de amostragem e ausência; PT/EN. Nenhuma migração de banco.
- **Validação:** testes puros com cota intervalar/semanal/ambas, reportado, saldo, requisições/tokens locais, `Total`, mais de oito janelas e exportação após avanço do relógio.
- **Risco:** reconstruir o período com horário de geração, duplicar cálculo ou tratar dados amostrados como histórico bruto.
- **Commit previsto:** contrato temporal, builder, formatação compartilhada e respectivos testes.
- **Estado:** contrato temporal, seleção de séries, builder e texto completo com quebra no PDF implementados. Testes focados passaram; sem alteração de schema.

### A5 — ação de PDF e integração desktop

- **Objetivo:** ligar documento, ação e gravação com feedback claro.
- **Arquivos:** `HistoryViewModel.kt`, `HistoryUiState.kt`, UI do histórico, `AppViewModels.kt`; contratos de resultado/exportação se necessário; `HistoryViewModelTest.kt`, `HistoryScreenTest.kt`. Reutilizar `AppGraph.usageExportWriter`; alterar host/writer apenas se a validação do seletor exigir.
- **Aceite:** ação “Relatório PDF”, disponibilidade explicada, snapshot estável, bloqueio de duplicidade, destino escolhido e feedback de sucesso/falha; cancelamento silencioso; encerramento sem trabalho pendente no scope.
- **Validação:** writer falso para sucesso, erro e cancelamento; clique durante releitura; troca de conta/intervalo/cota durante escrita; cliques repetidos; UI responsiva. Confirmar seletor de destino no modal Windows.
- **Risco:** exportar relatório antigo com filtros novos ou mostrar resultado numa conta diferente.
- **Commit previsto:** ação completa, DI, estados e testes; não deixar botão sem implementação.
- **Estado:** ação, writer existente, DI, resultado, bloqueio de cliques repetidos e cancelamento implementados. Testes focados passaram; seletor de destino no modal real Windows permanece pendente.

### A6 — verificação final e documentação

- **Objetivo:** validar a direção aplicada e o PDF gerado, com rastreabilidade na issue.
- **Arquivos:** testes de relatório/renderizador e screenshots; protótipo, `History.jsx`, contratos alterados, `HelpCatalog.kt` e mídia de ajuda quando o fluxo mudar; este plano.
- **Aceite:** dados consistentes, PDF abre e pagina corretamente, regressões do histórico preservadas, captura e inspeção visual concluídas, documentação compatível com a entrega.
- **Validação:** comandos abaixo em sequência; inspeção visual das páginas PDF e amostras de tela; uso manual no Windows. Validar PT/EN, claro/escuro, escala elevada, conta longa, fonte sem dados, previsão longa, lista de modelos e histórico extenso.
- **Risco:** confundir testes offscreen com aprovação do aplicativo instalado. Registrar QA manual pendente até execução real.
- **Commit previsto:** verificações finais e documentação coerentes com o resultado. Atualizar issue/publicar apenas após autorização correspondente.
- **Estado:** capturas, PDFs e mídia de ajuda gerados e inspecionados; testes agregados aprovados. QA manual Windows e publicação na issue permanecem pendentes.

## Comandos de validação previstos

Executados na continuação conforme registro ao final. Rodar sequencialmente; saídas de fixtures ficam em `build/issue383/`.

```powershell
.\gradlew.bat desktopTest --tests "com.usagemonitor.presentation.HistoryViewModelTest" --tests "com.usagemonitor.presentation.ui.History*" --tests "com.usagemonitor.ui.HistoryScreenTest" --tests "com.usagemonitor.data.UsageHistoryRepositoryImplTest" --no-daemon --console=plain
.\gradlew.bat desktopTest --tests "com.usagemonitor.presentation.UsageReportBuildersTest" --tests "com.usagemonitor.data.PdfUsageReportRendererTest" --no-daemon --console=plain
.\gradlew.bat desktopTest --tests "com.usagemonitor.ui.*" --no-daemon --console=plain
.\gradlew.bat allTests --no-daemon --console=plain
.\gradlew.bat generateScreenshots '-PscreenshotOutputDir=build/issue383/screenshots' --no-daemon --console=plain
git diff --check
```

Acrescentar ao primeiro/segundo comando as classes novas de builder, estado e recorte temporal quando criadas. O cenário `history-baseline`, adicionado em A1, cobre as quatro anatomias atuais. Ampliar a matriz em A3 para a direção escolhida e escala global; não atualizar `img/` incidentalmente.

PDF: gerar fixtures curtas e de múltiplas páginas; reler com PDFBox para verificar conteúdo, conta/filtros, contagem de linhas e repetição de cabeçalhos; renderizar páginas e inspecionar números/textos completos. Não usar apenas existência de bytes como aceite.

## Riscos gerais, rollback e conclusão

- **Impacto previsto:** apresentação do histórico e nova exportação local; contrato temporal aditivo, sem mudança de schema, API ou servidor.
- **Rollback:** reverter apenas os commits desta issue, preservando mudanças alheias; PDFs já gerados são arquivos locais e continuam utilizáveis.
- **Principais riscos:** perda de informação por compactação, cálculo duplicado, associação incorreta entre filtros e snapshot, truncamento em PDF e regressão de geometria/modal.
- **Critério de conclusão:** dez propostas entregues e uma direção registrada; UI escolhida implementada; PDF funcional e coerente; testes e inspeção visual aprovados; QA manual explicitamente concluído ou pendente. Build não substitui aprovação visual.
- **Evidência do planejamento:** issue lida com `gh issue view 383`, análise do código e design system, inspeção da imagem versionada e plano salvo.

## Evidências da continuação — base e exploração visual

- Skills aplicadas: `.codex/skills/usage-monitor-design/SKILL.md` e `visualize/SKILL.md` do plugin instalado, incluindo o contrato de carrossel em `tweak.md`.
- Treze módulos da galeria são compilados das primitivas JSX publicadas; tokens são lidos do design system e limitados ao escopo do produto. As primitivas publicadas não foram modificadas pela galeria. As referências de Histórico foram atualizadas em A3 após a escolha.
- Cenário `history-baseline` em `generateScreenshots`; fixtures em `Issue383HistoryFixtures.kt`; saída em `build/issue383/baseline/`.
- Comando da base: `gradlew.bat generateScreenshots '-PscreenshotScenario=history-baseline' '-PscreenshotOutputDir=build/issue383/baseline' --no-daemon --console=plain`. Execução final: `BUILD SUCCESSFUL`, 24 imagens, evidência em `build/issue383-baseline.log`.
- Inspeção das capturas confirma problemas em 320dp: números quebrados, valores truncados e seletor de intervalo incompleto; em atividade local, a última coleta aparece no conteúdo e no rodapé. Evidência offscreen com fixtures, não validação da instalação Windows.
- Galeria: `docs/planos/issue383-visual/index.html`; fontes e procedimento em `docs/planos/issue383-visual/README.md`. Fragmento da conversa no diretório de visualizações deste chat.
- Verificação final: 180 combinações (dez propostas × três larguras × dois temas × três estados); zero erros de JavaScript e zero transbordamentos externos. Relatório em `build/issue383/gallery-inspection/inspection.json` e capturas no mesmo diretório.
- Interações verificadas: abas, todas as janelas, prévia de PDF, bloqueio de PDF sem dados, seleção do oitavo modelo, cota semanal, zoom e expansão. Inspeção visual das dez opções com cotas e amostras estreitas claras de atividade local concluída.
- Intervalo e conta ilustram contexto sobre fixture fixa; não consultam banco. Cota, abas, seleção de modelo, expansão e zoom alteram conteúdo local. PDF é uma prévia de dados e não grava arquivo.
- A escolha de direção, variantes de saldo/reportado, escala global de Compose e implementação do PDF foram tratadas na continuação abaixo. Nenhum commit/push executado.

## Evidências da direção 01 e PDF

- Escolha humana: “pode ser a 01 resumo por detalhe”, em 06/10/2026.
- UI: `HistoryScreen.kt`, `HistoryControls.kt`, `HistorySummary.kt`, `HistorySeriesCard.kt`, `HistoryProviderCards.kt`, `HistoryMetrics.kt` e `HistoryWindowAnalysis.kt`. Composição sem painel externo aninhando gráfico, métricas e análise; contexto da conta selecionada permanece completo.
- Dados compartilhados: `HistoryPresentation.kt` serve a tela e o relatório. Reportado mantém métricas próprias; atividade local conserva requisições ou tokens e não recebe limite ou previsão inventados.
- Exportação: `HistoryReportBuilder.kt`, contrato temporal em `UsageHistoryModels.kt`/`UsageHistoryRepositoryImpl.kt`, estados e ação no ViewModel, DI em `AppViewModels.kt`. `UsageExportPayload.Report` aceita idioma capturado; o writer aplica esse idioma no rodapé.
- PDF: nova seção `Paragraphs` em `UsageReportDocument.kt`/`PdfUsageReportRenderer.kt` para preservar contexto, modelos e previsões extensas. Cabeçalhos compactos têm identificação completa repetida no corpo com quebra de texto. Todas as janelas e pontos disponíveis no relatório são incluídos; Total declara a amostragem.
- Referências e ajuda: `History.jsx`, seção Histórico de `prototipo-visual-opencode.html`, passos PT/EN de `HelpCatalog.kt`.
- Preview: React/ReactDOM incorporados e HTML estático pré-renderizado; dez telas verificadas sem rede e versão standalone legível sem JavaScript. O log do host que exibiu a tela branca não foi fornecido, portanto sua causa específica não está afirmada. Evidência: `build/issue383/startup/fixed.json`.
- Testes focados: `desktopTest` com `ui.History*`, `presentation.History*`, `presentation.ui.History*`, `UsageHistoryRepositoryImplTest` e `PdfUsageReportRendererTest`: **BUILD SUCCESSFUL**, `build/issue383-focused.log`. Cobrem escopo temporal/cota, todas as janelas, reportado, tokens locais, Total, sucesso/falha/cancelamento, releitura e expansão/rolagem.
- Capturas: cenário `history-baseline` agora permite comparar as quatro anatomias depois da adequação em dois temas, larguras 320/680/1030 e escala 125% em 320/1030: **40 capturas**. Saída final `build/issue383/final/`; comando `generateScreenshots '-PscreenshotScenario=history-baseline' '-PscreenshotOutputDir=build/issue383/final'`.
- Relatórios reais das mesmas fixtures: oito PDFs (quatro fontes × PT/EN), com texto extraído e páginas renderizadas por PDFBox, incluindo 18 páginas por relatório de oito modelos locais, com leituras de 5h e 7d. Conteúdo de amostra inteiramente sintético.
- Pendências explícitas: validar aplicativo Windows, seletor de destino ligado à janela correta e acessibilidade por teclado no modal instalado. Atualização remota da issue, commit, push e release aguardam pedido específico.

### Verificação anterior registrada — insuficiente para os defeitos relatados depois

- `desktopTest --tests 'com.usagemonitor.ui.*' --tests 'com.usagemonitor.presentation.HistoryReportBuilderTest' --tests 'com.usagemonitor.presentation.HistoryExportTest' --tests 'com.usagemonitor.data.PdfUsageReportRendererTest'`: **509 testes, zero falhas**, `build/issue383-ui.log`.
- `generateScreenshots '-PscreenshotScenario=history-baseline' '-PscreenshotOutputDir=build/issue383/final' allTests --no-daemon --console=plain`: **BUILD SUCCESSFUL**, 2458 testes agregados, zero falhas/erros/ignorados; `build/issue383-final.log`. Inclui os testes de arquitetura, sem exceções novas.
- `generateHelpMedia '-PhelpMediaTopic=history'`: atualização restrita a `src/desktopMain/resources/help/history.gif`, 23 frames; início e encerramento inspecionados. `img/history.png` atualizado com a captura sintética da fonte Anthropic. Outras mídias preservadas.
- Matriz final: 40 capturas; oito PDFs PT/EN com 54 páginas renderizadas. Inspeção de amostras dos quatro tipos de dado, largura mínima, escala de 125%, páginas iniciais e finais e cabeçalhos de continuação. As fixtures de saldo e atividade local não herdam reinício de cota dos dados Anthropic.
- Kit `History.jsx`: compilação e navegação verificadas em 320/680/1030px, sem erros de JavaScript ou transbordamento externo; cota, expansão e prévia de PDF funcionam. Evidência do procedimento em `build/issue383/verify_kit.cjs`.
- `git diff --check`: aprovado. A alteração preexistente de 30 linhas removidas em `server/package-lock.json` permanece fora do escopo. Não houve commit, push, alteração remota da issue ou release.

**Pendência de aceite manual:** abrir o modal no aplicativo Windows, operar por teclado e confirmar destino/cancelamento/gravação do seletor real de arquivo. Testes offscreen, fixtures e PDFs sintéticos não substituem esse aceite.

## Correções após as capturas de uso real — 06/10/2026

As capturas fornecidas pelo usuário invalidaram o aceite visual anterior. A matriz anterior não exercitava a tabela larga expandida com várias linhas nem a lista de três contas longas no cabeçalho de uma janela baixa.

- **Sobreposição confirmada:** o ramo largo de `HistoryWindowTable`, dentro de `BoxWithConstraints`, emitia cabeçalho e linhas como irmãos de `Box`. Todos ocupavam a mesma origem. Envolver esse ramo em `Column` corrige a posição das linhas; não houve substituição das fontes IBM Plex.
- **Cabeçalho excessivo confirmado:** lista permanente de contas e repetição do nome selecionado consumiam a região fixa. Agora a seleção usa `AppMenu`, com texto completo e rolagem apenas enquanto aberto. Intervalo e cota consomem `AppSegmentedControl`; PDF tem rótulo curto e descrição acessível completa.
- **Adaptação:** uma linha de controles a partir de 720dp quando a fonte é fixa; duas linhas entre 600 e 719dp; abaixo de 600dp o intervalo usa menu, cotas têm linha própria e as três métricas usam tabela compacta. O modal por fonte tem título sem subtítulo duplicado. Alterar o escopo carregado volta a rolagem ao início.
- **Primitiva e adoção juntas:** limites de largura/altura e quebra dos itens adicionados ao `AppMenu`, consumidos pelo histórico e registrados em JSX, tipos e contrato. Kit e protótipo refletem os controles compactos.
- **Regressões geométricas:** `HistoryLayoutRegressionTest` verifica oito linhas sem sobreposição, altura fixa de até 60dp com três contas longas em 1030×560 e nomes completos no menu em 320dp com escala 125%. Selecionar a última conta também é exercitado.
- **Capturas adicionais:** `history-regression`, 24 imagens em `build/issue383/regression`: Anthropic/Codex, claro/escuro, cinco geometrias/escala; mais tabela expandida em claro/escuro e escala 100/125%. Todas usam identificações sintéticas. Inspeção das amostras de janela baixa, escala elevada, largura mínima e tabela expandida realizada.
- **Crash posterior #389:** tratado separadamente em `historico-389-reentrada-renderizacao.md`. O layout não é apresentado como causa do crash; a reentrada está confirmada, o iniciador não.

O aceite na execução Windows afetada, o fluxo real de PDF e a acessibilidade por teclado continuam pendentes. Não considerar a issue #389 resolvida apenas porque a sonda abriu normalmente.

Verificação posterior: `allTests` aprovou **2463 testes**, sem falhas/erros/ignorados; `build/issue383-389-final.log`. As 24 capturas adicionais foram geradas novamente depois dos ajustes. O kit foi verificado em 320/680/1030px, incluindo abertura do menu, limites da janela, três contas completas e seleção da última. A ajuda usa rolagem do composable real, preservando filtros e última coleta; `img/history.png` reflete a janela baixa com contas longas. Commit, push, issue remota e release continuam fora desta entrega.
