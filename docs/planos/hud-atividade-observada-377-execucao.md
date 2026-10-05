# Issue #377 — atividade observada no HUD

## Objetivo e decisões

- Zen Free e Kilo Free: requisições locais por modelo; Gemini CLI: tokens locais por modelo.
- HUD recolhido: soma das últimas 5h com unidade e abreviação existente; estado neutro "Atividade local".
- Balão: últimas 5h e últimos 7 dias por modelo; sem cotas, percentuais, reinícios ou restante.
- Quatro modelos visíveis, rolagem para os demais e redução da área rolável conforme a tela e a escala.
- Histórico, coleta, persistência, API e limites dos provedores preservados.
- A alteração pré-existente em `server/package-lock.json` fica fora desta entrega.

## Atividades sequenciais

| Atividade | Alteração e aceite | Evidência / estado |
| --- | --- | --- |
| 1. Modelo | `HudObservedModel`, agrupamento compartilhado com o dashboard, resumo com unidade, acessibilidade e bandeja; nenhuma cota gerada para fonte observada | Implementado; quatro testes novos de modelo aprovados |
| 2. Superfícies e geometria | Balão com linhas de dados e rolagem; nota de limite indisponível; cabeçalho/ações fixos; indicador sem arcos; contas de cota mantêm comportamento | Implementado; 492 testes de UI/modelo aprovados, incluindo os três testes novos de componente |
| 3. Validação e documentação | Contrato, kit e protótipo atualizados; fixtures sintéticas nas três fontes, temas, orientações e modo compacto | 24 capturas geradas; amostras inspecionadas visualmente; `allTests` aprovado com 2.441 testes, zero falhas e zero ignorados |

## Comandos de validação

```powershell
.\gradlew.bat desktopTest --tests "com.usagemonitor.ui.*" --tests "com.usagemonitor.presentation.HudObservedModelTest" --no-daemon --console=plain
.\gradlew.bat allTests --no-daemon --console=plain
.\gradlew.bat generateScreenshots -PscreenshotScenario=observed -PscreenshotOutputDir=build/issue377-screenshots --no-daemon --console=plain
git diff --check
```

As capturas opcionais não substituem o fluxo padrão do gerador. Este cenário usa apenas dados sintéticos e salva a evidência em `build/`, sem atualizar os prints gerais em `img/`.

### Evidência local

- `build/issue377-ui.log`: execução sequencial concluída com sucesso; 492 testes e zero falhas nos relatórios XML.
- `build/issue377-alltests.log`: suite agregada aprovada; 2.441 testes, zero falhas e zero ignorados. `ArchitectureRulesTest`: oito testes aprovados.
- `git diff --check`: aprovado.
- `build/issue377-screenshots/`: 24 imagens (três fontes × dois temas × duas orientações × dois modos).
- Os testes de tela baixa exercitam as quatro bordas; o teste de rolagem alcança o oitavo modelo e confirma nota e ações visíveis.
- Duas execuções concorrentes de testes disputaram o diretório de resultados (`EOFException` / arquivo temporário ausente). Esses resultados foram descartados; os comandos de validação devem rodar em sequência no mesmo checkout.

## Riscos, rollback e pendências

- Risco principal: divergência entre altura calculada e composição; cobrir as quatro bordas e lista longa em tela baixa com testes.
- Rollback: reverter somente as alterações desta entrega; não há migração nem mudança em dados locais.
- Validação manual do HUD instalado no Windows: pendente; testes/capturas offscreen não constituem aprovação física.
- Publicação autorizada pelo pedido de criação do PR. Branch `codex/issue-377-hud-atividade-observada`, staging restrito e identidade definida pelo repositório; implementação, testes e documentação agrupados em commit atômico.
