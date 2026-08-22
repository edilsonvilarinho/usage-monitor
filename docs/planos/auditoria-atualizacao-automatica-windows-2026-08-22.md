# Auditoria técnica — atualização automática Windows

## 1. Identificação

| Campo | Valor |
|---|---|
| Data da auditoria | 2026-08-22 |
| Repositório | `C:\Users\edils\workspace\usage-monitor` |
| Branch auditada | `feat/auto-update-windows-75` |
| HEAD auditado | `31bd8507baf3578eae3316d1e3637390a0444a74` |
| Base de comparação | `origin/main` em `8aa6e73c26a65de85b88973a23f866aa180dac42` |
| Plano confrontado | `docs/planos/atualizacao-automatica-windows-execucao.md` |
| Issue confrontada | GitHub issue `#75` |
| Modelo usado nesta auditoria | `gpt-5.6-sol` |
| Nível de esforço desta auditoria | `ultra` |
| Modelo declarado no desenvolvimento | `Claude Opus 5 (1M context)` |
| Nível de esforço declarado no desenvolvimento | `max` |

O modelo e o esforço desta auditoria foram obtidos do `turn_context` do arquivo local da sessão Codex, não inferidos pelo texto gerado. A documentação oficial da OpenAI trata a escolha do modelo e o nível de raciocínio como parâmetros distintos: [Model guidance](https://developers.openai.com/api/docs/guides/latest-model). `ultra` é o nível de execução registrado por esta sessão Codex; não é apresentado aqui como valor do parâmetro público `reasoning.effort` da API.

O cabeçalho do plano original declara `Claude Opus 5 (1M context)`, esforço `max` e execução pelo Claude Code CLI. Esses dados descrevem o desenvolvimento auditado, não esta auditoria.

## 2. Veredito executivo

> **REPROVADA para ativação da atualização automática, publicação de release e encerramento da issue #75.**

A branch contém uma base defensiva tecnicamente relevante: opt-in desligado por padrão, gate explícito de ativação, seleção conservadora de plataforma/origem, download retomável com SHA-256, extração antes da troca, ausência de `taskkill /F` no caminho de update e preservação de `SetCompressor zlib`.

Isso não torna o recurso pronto. Há defeitos comprovados no fechamento transacional do update, na correspondência entre release e instalador, no tratamento de falhas, na retroalimentação do receipt e no filtro do CI. O aceite empacotado A20 não pode ser executado como foi escrito. Além disso, A19 e A20 continuam formalmente pendentes.

O risco atual está contido exclusivamente porque:

- `AUTO_UPDATE_SHIPPED = false` em `src/desktopMain/kotlin/com/usagemonitor/update/AutoUpdateController.kt:26`;
- `MIN_UPDATABLE_TARGET_VERSION = "999.0.0"` em `src/desktopMain/kotlin/com/usagemonitor/update/WindowsAppUpdateInstaller.kt:35`;
- a preferência é `false` por padrão.

Portanto, a branch está **segura por inatividade**, não por conclusão do fluxo de atualização.

## 3. Escopo e método

A auditoria comparou:

1. requisito remoto da issue #75;
2. plano e registro de execução A00–A20;
3. diff integral entre `8aa6e73` e `31bd850`;
4. código Kotlin comum/desktop e Compose;
5. instalador NSIS e roteiro PowerShell de cenários;
6. workflows de CI/release;
7. testes automatizados e evidências versionadas;
8. histórico e atomicidade dos commits.

Foram usados quatro níveis de julgamento:

- **P0 — bloqueador absoluto:** impede afirmar segurança ou executar o aceite definido;
- **P1 — alto:** pode provocar perda do rollback, falso sucesso, app fechado ou gate de segurança ineficaz;
- **P2 — médio:** risco operacional, segurança residual, cobertura insuficiente ou comportamento degradado;
- **P3 — processo/evidência:** não é defeito direto de runtime, mas reduz rastreabilidade e reprodutibilidade.

Cada achado abaixo separa evidência, impacto, classificação e confiança. Nenhuma causa foi presumida sem referência concreta.

## 4. Fotografia confirmada da branch

| Item | Resultado confirmado | Evidência |
|---|---|---|
| Branch | `feat/auto-update-windows-75` | `git branch --show-current` |
| HEAD local | `31bd8507baf3578eae3316d1e3637390a0444a74` | `git rev-parse HEAD` |
| HEAD remoto | mesmo hash local | `git ls-remote origin refs/heads/feat/auto-update-windows-75` |
| Base | `8aa6e73c26a65de85b88973a23f866aa180dac42` | `git merge-base HEAD origin/main` |
| Commits | 23 | `git rev-list --count 8aa6e73..HEAD` |
| Arquivos no diff | 39 | `git diff --name-only 8aa6e73..HEAD` |
| Volume do diff | 4.833 inserções e 83 remoções | `git diff --shortstat 8aa6e73..HEAD` |
| Worktree antes da auditoria | limpa | `git status --short` sem saída |
| Whitespace do diff | limpo | `git diff --check 8aa6e73..HEAD` sem saída |
| Pull request da branch | inexistente | `gh pr list --head feat/auto-update-windows-75 --state all` retornou `[]` |
| GitHub Actions da branch | nenhuma execução | `gh run list --branch feat/auto-update-windows-75` sem runs |
| Issue #75 | aberta | `gh issue view 75` |

## 5. Bloqueadores e achados de alta severidade

### P0-01 — sucesso é registrado e o backup é apagado antes de comprovar que a versão nova funciona

**Evidência**

- `src/installer/UsageMonitor.nsi:367-376` grava `status=success` e remove `$INSTDIR.old`.
- O relançamento só ocorre depois, em `.onInstSuccess`, por `Exec` em `src/installer/UsageMonitor.nsi:186-192`.
- O retorno de `Exec` não é inspecionado e não existe handshake de inicialização ou saúde.

**Impacto**

Uma versão pode ser extraída e trocada corretamente, mas falhar ao iniciar. Nesse cenário o instalador já apagou a única cópia de rollback e deixou um receipt de sucesso factualmente falso. O usuário fica sem rollback automático para a versão anterior.

**Classificação:** defeito de projeto transacional e bloqueador de release.
**Confiança:** 100%.

### P0-02 — o smoke test A20 é inexequível como documentado

**Evidência**

- O plano exige feed JSON local por HTTP e `Setup.exe` local em `docs/planos/atualizacao-automatica-windows-execucao.md:297-300`.
- `src/desktopMain/kotlin/com/usagemonitor/update/UpdateArtifactDownloader.kt:199-204` aceita o artefato somente quando a URL é HTTPS e o host pertence à lista GitHub confiável.

**Impacto**

O feed local pode ser servido, mas o `Setup.exe` local é rejeitado antes da requisição. O único cenário declarado pelo plano como capaz de fechar a issue não pode ser executado com a costura especificada.

**Classificação:** bloqueio objetivo de validação e inconsistência factual do plano.
**Confiança:** 100%.

### P1-01 — o PID é recebido, mas nunca é aguardado

**Evidência**

- O launcher envia `/PID=<pid>` em `src/desktopMain/kotlin/com/usagemonitor/update/WindowsAppUpdateInstaller.kt:134-140`.
- O NSIS apenas lê e grava o PID no receipt; o comentário declara explicitamente “só vai para o recibo” em `src/installer/UsageMonitor.nsi:63-65`.
- A espera real é o retry do primeiro `Rename`: 30 tentativas com `Sleep 500`, aproximadamente 15 segundos, em `src/installer/UsageMonitor.nsi:300-315`.
- O plano exige aguardar o PID por aproximadamente 30 segundos em `docs/planos/atualizacao-automatica-windows-execucao.md:223-234`.

**Impacto**

Shutdowns mais lentos podem resultar em `reason=locked`, apesar de o contrato documentado afirmar espera explícita pelo processo. Código, comentário do launcher e plano descrevem uma garantia que o instalador não implementa.

**Classificação:** defeito de aderência A08/A16 e erro documental.
**Confiança:** 100%.

### P1-02 — falhas controladas não relançam a versão anterior

**Evidência**

- Os caminhos `locked`, `staging-unavailable` e `swap-failed` terminam em `Abort` em `src/installer/UsageMonitor.nsi:313-334`.
- `.onInstFailed`, em `src/installer/UsageMonitor.nsi:199-206`, apenas tenta restaurar o backup.
- O único `Exec` de relançamento existe em `.onInstSuccess`, em `src/installer/UsageMonitor.nsi:186-192`.
- A20 afirma que, com arquivo travado, o “app volta na v37” em `docs/planos/atualizacao-automatica-windows-execucao.md:306-307`.

**Impacto**

Depois de fechar o app para atualizar, uma falha deixa o usuário sem aplicação aberta. A versão anterior pode continuar no disco, mas não “volta” automaticamente. O banner só poderá aparecer quando o usuário iniciar a aplicação manualmente.

**Classificação:** defeito funcional e divergência do aceite A20.
**Confiança:** 100%.

### P1-03 — falha automática de extração não produz receipt nem relançamento

**Evidência**

- `File /r` pode abortar a Section diretamente, reconhecido nos comentários de `src/installer/UsageMonitor.nsi:195-198` e `277-281`.
- `.onInstFailed`, em `src/installer/UsageMonitor.nsi:199-206`, não chama `WriteUpdateReceipt`, não remove staging parcial e não relança a aplicação.
- O plano afirma receipt em caminhos de falha em `docs/planos/atualizacao-automatica-windows-execucao.md:149-152`.

**Impacto**

Falta de espaço, antivírus ou erro de extração pode produzir falha silenciosa sem receipt, exatamente no cenário em que o mecanismo deveria preservar diagnóstico pós-restart.

**Classificação:** defeito funcional e documental.
**Confiança:** 100%.

### P1-04 — falha ao iniciar o instalador é descartada

**Evidência**

- `AppUpdateInstaller.schedule()` retorna `Result<Unit>`.
- `DashboardViewModel.scheduleUpdateOnExit()` ignora esse resultado em `src/commonMain/kotlin/com/usagemonitor/presentation/viewmodel/DashboardViewModel.kt:883-890`.
- `AppUpdateFailureReason.SCHEDULE` existe para UI/teste, mas nenhum fluxo de produção o emite; o registro efetivo de falha usa somente `DOWNLOAD` em `DashboardViewModel.kt:820-845`.
- O plano exige “schedule falhando → Failed” em `docs/planos/atualizacao-automatica-windows-execucao.md:255-259`.

**Impacto**

Se o arquivo for removido, o suporte mudar ou `ProcessBuilder.start()` falhar, a ação “Reiniciar e atualizar agora” pode encerrar a aplicação sem iniciar instalador, sem receipt e sem erro visível.

**Classificação:** defeito A11 e lacuna de observabilidade.
**Confiança:** 100%.

### P1-05 — a versão da release não está vinculada à versão do asset executado

**Evidência**

- `src/commonMain/kotlin/com/usagemonitor/data/repository/AppUpdateRepositoryImpl.kt:78-94` classifica como NSIS qualquer `.exe` cujo nome contenha `setup`.
- O gate mínimo compara `update.version`, isto é, a tag/feed, em `src/desktopMain/kotlin/com/usagemonitor/update/WindowsAppUpdateInstaller.kt:85-95`.
- A seleção usa o primeiro NSIS compatível em `WindowsAppUpdateInstaller.kt:150-157`.
- Não há comparação obrigatória entre versão da tag e versão codificada no nome/metadado do asset, nem rejeição de múltiplos candidatos.

**Impacto**

Uma release `v40.0.0` que contenha primeiro um `UsageMonitor-Setup-37.0.0.exe` faz a tag 40 passar pelo gate e permite que um instalador legado receba `/UPDATE`. Isso contorna a barreira criada para impedir execução destrutiva contra instaladores antigos.

**Classificação:** defeito do gate de compatibilidade.
**Confiança:** 100%.

### P1-06 — o portão A19 aceita qualquer mínimo diferente da sentinela

**Evidência**

- `src/desktopTest/kotlin/com/usagemonitor/update/AutoUpdateWiringTest.kt:23-38` verifica essencialmente a coerência entre feature flag e uso da sentinela `999.0.0`.
- Um mínimo incorreto como `0.0.0` deixa de ser sentinela e ainda satisfaz o teste.
- O plano atribui ao teste função de portão de segurança para A19 em `docs/planos/atualizacao-automatica-windows-execucao.md:450-456`.

**Impacto**

A19 pode ser ligada com um mínimo incapaz de garantir que o asset já entende `/UPDATE`, mantendo a suíte verde.

**Classificação:** lacuna crítica de validação; sem exposição atual porque o recurso permanece desligado.
**Confiança:** 100%.

### P1-07 — rollback após a primeira troca não é verificado nem testado

**Evidência**

- Se o segundo `Rename` falhar, `src/installer/UsageMonitor.nsi:322-326` executa `Rename "$UpdateBackup" "$INSTDIR"` sem `ClearErrors`/`IfErrors` posterior.
- `.onInstFailed` repete a tentativa de restauração em `UsageMonitor.nsi:199-205`, também sem verificar o resultado.
- Mesmo sem comprovação, o log afirma que a instalação permaneceu intacta em `UsageMonitor.nsi:332`.
- S3 bloqueia o primeiro `Rename`; S4 falha antes da troca. Nenhum cenário S1–S6 induz falha entre os dois renames: `src/installer/test/Invoke-UpdateScenarios.ps1:173-246`.

**Impacto**

Uma falha simultânea no segundo rename e na restauração pode deixar somente `$INSTDIR.old`, enquanto receipt/log afirmam falha controlada. É o caminho mais crítico do rollback e permanece sem asserção real.

**Classificação:** defeito de verificação e lacuna de teste do rollback.
**Confiança:** 99%.

### P1-08 — receipt de falha não governa backoff ou nova tentativa

**Evidência**

- O receipt é lido uma vez por `AutoUpdateController` em `src/desktopMain/kotlin/com/usagemonitor/update/AutoUpdateController.kt:91-99`.
- `Main.kt:470-472` entrega instalador e preferência ao ViewModel, mas não entrega o receipt.
- `Main.kt:1666-1670` usa o receipt somente na tela de Configurações.
- O backoff é criado apenas por falha de preparação/download em `DashboardViewModel.kt:820-845`.

**Impacto**

Após `locked` ou `swap-failed`, a aplicação reaberta não transforma o receipt em estado `Failed` nem bloqueia nova tentativa da mesma versão. O artefato pode ser preparado e agendado repetidamente em cada encerramento.

**Classificação:** defeito do ciclo pós-installer.
**Confiança:** 100%.

### P1-09 — filtro de mudanças do CI pode falhar aberto

**Evidência**

- `.github/workflows/ci.yml:123-138` usa `$ErrorActionPreference = 'Stop'`, mas não verifica `$LASTEXITCODE` após `git fetch` e `git diff`.
- Em PowerShell com `$PSNativeCommandUseErrorActionPreference=False`, falha de comando nativo não vira exceção apenas por `$ErrorActionPreference`.
- Validação controlada durante a auditoria confirmou `git diff` retornando exit 128 e o script PowerShell continuando.

**Impacto**

Uma falha do fetch/diff pode deixar `$changed` vazio, produzir `relevant=false` e concluir o job com sucesso sem executar os cenários do instalador.

**Classificação:** defeito do gate de CI.
**Confiança:** 100%.

### P1-10 — A19 e A20 permanecem pendentes

**Evidência**

- `AUTO_UPDATE_SHIPPED=false` e mínimo `999.0.0` mantêm o recurso inalcançável.
- O plano marca A19 e A20 como pendentes.
- Não existe smoke empacotado registrado, PR ou run de Actions para esta branch.

**Impacto**

A branch não entrega atualização automática utilizável. Esse estado é deliberado e correto como proteção intermediária, mas impede aprovação funcional e publicação.

**Classificação:** bloqueio de conclusão, não regressão de runtime atual.
**Confiança:** 100%.

## 6. Riscos médios e dívidas técnicas

### P2-01 — o roteiro E2E apaga o receipt real do usuário

**Evidência:** `src/installer/test/Invoke-UpdateScenarios.ps1:39` usa `%USERPROFILE%\.usage-monitor\update-receipt.properties`; o arquivo é removido em `:120-126`, `:179`, `:197`, `:214` e durante a limpeza em `:248-251`, sem backup/restauração.
**Impacto:** execução local pode destruir o registro legítimo da última atualização ou deixar receipt fictício se o processo for interrompido.
**Confiança:** 100%.

### P2-02 — `WorkDirectory` permite exclusão recursiva arbitrária

**Evidência:** o parâmetro público é aceito em `Invoke-UpdateScenarios.ps1:22-25` e removido recursivamente em `:146`, sem exigir raiz temporária, sentinel ou diretório criado pelo próprio teste.
**Impacto:** um parâmetro incorreto pode apagar workspace ou dados fora do escopo do cenário.
**Confiança:** 100%.

### P2-03 — o teto de tentativas existe somente em memória

**Evidência:** `updateBackoff` é estado do `DashboardViewModel` em memória e é atualizado em `DashboardViewModel.kt:833-845`; não existe persistência das tentativas por versão.
**Impacto:** reiniciar a aplicação zera o teto. Um SHA/tamanho incorreto pode provocar novo download de aproximadamente 120 MB a cada ciclo da aplicação.
**Confiança:** 100%.

### P2-04 — existe janela TOCTOU entre verificação e execução

**Evidência:** o SHA é validado em `UpdateArtifactDownloader.kt:145-151`; horas depois, `WindowsAppUpdateInstaller.schedule()` verifica apenas que o arquivo existe em `WindowsAppUpdateInstaller.kt:117-140`. O digest não integra `AppUpdatePreparation`.
**Impacto:** arquivo alterado, corrompido ou substituído depois de `Ready` pode ser executado sem nova verificação.
**Confiança:** 100%.

### P2-05 — retomada pode ficar presa quando o tamanho é ausente

**Evidência:** `sizeBytes` pode ser nulo; `UpdateArtifactDownloader.kt:53-67` só elimina parcial no EOF quando há tamanho declarado e `:95-119` envia `Range` a partir do tamanho atual. Um servidor que responde 416 não entra nos casos 200/206 aceitos.
**Impacto:** se o processo cair após terminar o corpo e antes do hash, o `.part` completo pode repetir HTTP 416 indefinidamente.
**Confiança:** 100%.

### P2-06 — consulta síncrona ao Registro pode ocorrer durante composição

**Evidência:** `AutoUpdateController.support` recalcula suporte em `AutoUpdateController.kt:45-46`; `Main.kt:1666-1668` lê o getter durante composição. A resolução chega a `reg.exe` por `WindowsInstallOriginResolver`, e `AutoStartManager` aguarda o processo sincronamente sem timeout.
**Impacto:** após A19, recomposições da aba Geral podem criar processos `reg.exe` e bloquear a thread de UI. Hoje o gate falso mascara esse caminho.
**Confiança:** 100%.

### P2-07 — ativar o switch não inicia imediatamente um download já anunciado

**Evidência:** o watcher em `DashboardViewModel.kt:863-875` trata apenas `enabled=false`; `enabled=true` retorna sem reavaliar o update atual.
**Impacto:** o usuário ativa a opção diante de um update `Available`, mas o download aguarda refresh manual ou o próximo poll, podendo levar até 10 minutos.
**Confiança:** 100%.

### P2-08 — estado concorrente não é reduzido atomicamente

**Evidência:** `trackedVersion()` é lido duas vezes em `DashboardViewModel.kt:741-746`; watcher, job de download e check alteram `preparedUpdate`, `downloadingVersion`, `updateBackoff` e `updateDownloadJob` sem mutex ou geração comum em `:741-875`. Os testes usam dispatcher virtual serial.
**Impacto:** desligamento ou troca de release concorrente com o retorno de `prepare()` pode republicar `Ready` depois de `forgetPendingUpdate()` ou sobrepor estado de versão.
**Classificação:** risco concorrente comprovado estaticamente, sem reprodução real nesta auditoria.
**Confiança:** 95%.

### P2-09 — cobertura do filtro CI é incompleta e o job não tem timeout próprio

**Evidência:** `.github/workflows/ci.yml:131-136` limita o filtro a `src/installer/*` e ao pacote desktop `update/*`; não inclui `build.gradle.kts`, `Main.kt`, contratos comuns ou o próprio workflow. `Invoke-UpdateScenarios.ps1:89-95` usa `Start-Process -Wait` sem timeout e o job não define `timeout-minutes`.
**Impacto:** mudanças relevantes podem não acionar os cenários; regressão que reabra diálogo silencioso pode manter o runner bloqueado por período excessivo.
**Confiança:** 100%.

### P2-10 — garantias centrais não são exercitadas pelos cenários S1–S6

**Evidência:** `Invoke-UpdateScenarios.ps1:173-246` não exercita PID realmente vivo, `updateSwapFailed`, restauração após o primeiro rename, falha de extração, saúde pós-relaunch ou receipt consumido pelo Kotlin. O plano exige atalho do Menu Iniciar em `atualizacao-automatica-windows-execucao.md:285-292`, mas o roteiro não contém essa asserção.
**Impacto:** 33 asserções verdes não comprovam as garantias de maior risco.
**Confiança:** 100%.

### P2-11 — S6 aprova comportamento novo como “não regressão”

**Evidência:** no caminho sem `/UPDATE`, `UsageMonitor.nsi:164-178` adiciona `/SD IDNO`; em `origin/main`, `/S` sobre instalação existente bloqueava no `MessageBox`. S6, em `Invoke-UpdateScenarios.ps1:238-246`, aprova sobrescrita silenciosa como “comportamento de hoje inalterado”.
**Impacto:** o teste não prova não regressão; ele consolida uma mudança comportamental no instalador comum.
**Confiança:** 100%.

### P2-12 — garantias de autenticidade são limitadas

**Evidência:** SHA-256 e binário são obtidos do mesmo canal de release/feed; o Setup Windows permanece sem assinatura de código independente. O plano reconhece SmartScreen/assinatura como pendência.
**Impacto:** SHA-256 detecta corrupção, mas não protege contra comprometimento da conta/release que substitua simultaneamente binário e digest.
**Classificação:** risco residual aceito no plano, não defeito de hash.
**Confiança:** 100%.

### P2-13 — URL completa do feed pode expor segredo visualmente

**Evidência:** `src/commonMain/kotlin/com/usagemonitor/presentation/ui/components/SettingsDialogContent.kt:904-915` apresenta o valor integral de `feedUrlOverride`.
**Impacto:** userinfo, token ou assinatura colocados na query string aparecem na UI e em screenshots.
**Confiança:** 100% quanto à exposição do valor; não foi encontrado segredo real versionado.

### P2-14 — parciais de versões fracassadas podem acumular

**Evidência:** a poda em `WindowsAppUpdateInstaller.kt:95-100` só ocorre depois de download bem-sucedido; troca de versão cancela estado em `DashboardViewModel.kt:741-779`, mas não remove `.part` da versão anterior.
**Impacto:** downloads incompletos de várias versões podem acumular no diretório local.
**Confiança:** 100%.

## 7. Rastreabilidade, escopo e processo

### P2-15 — a issue #75 contém requisito Linux ainda não rastreado separadamente

**Evidência**

- `gh issue view 75` exige seleção por sistema/arquitetura e atualização Linux user-space por `.tar.gz`.
- O plano exclui Linux em `docs/planos/atualizacao-automatica-windows-execucao.md:55-61` e afirma que ele será tratado em issue própria.
- A busca atual de issues relacionadas não encontrou issue substituta; #75 permanece aberta.
- O plano afirma que A20 fecha a issue em `:297`.

**Impacto:** fechar #75 após um aceite exclusivamente Windows eliminaria o rastreamento de requisito remoto não entregue.

**Confiança:** 100%.

### P3-01 — A12a não foi registrada no mesmo commit

- O commit `04fadb7` contém `AppControls.kt` e o teste correspondente.
- A situação A12a foi adicionada ao plano apenas no commit seguinte, `e461f7c`, confirmado por `git blame`.
- Isso diverge da regra de atomicidade registrada em `CLAUDE.md:368`.

**Impacto:** dívida de processo; não altera o runtime.
**Confiança:** 100%.

### P3-02 — evidência A16 foi separada do roteiro A17

- `cefc8a5` entrega `/UPDATE` e registra 22 asserções.
- `Invoke-UpdateScenarios.ps1`, capaz de reproduzir as asserções, só entra no commit seguinte, `408d5d0`.

**Impacto:** o snapshot A16 não preserva a própria evidência de validação.
**Confiança:** 100%.

### P3-03 — os blocos chamados “PR 1” e “PR 2” não existem como PRs separados

- Todo o trabalho está em uma branch.
- `gh pr list --head feat/auto-update-windows-75 --state all` retornou vazio.

**Impacto:** a separação de segurança definida no plano ocorreu apenas por commits, não por revisão/merge independente.
**Confiança:** 100%.

### P3-04 — convenções versionadas de autoria não foram seguidas

- Os 23 commits usam autor `claude <claude@anthropic.com>` e trailers `Claude Opus 5`.
- `AGENTS.md:207-219` exige identidade temporária `codex <codex@openai.com>`.
- `CLAUDE.md:366` registra outra convenção de trailer.

**Impacto:** metadados são coerentes com o cabeçalho do plano, mas divergentes das regras versionadas do repositório. Não é defeito de runtime.
**Confiança:** 100%.

### P3-05 — parte da evidência histórica não é reproduzível pelo Git

- A02 não versiona instaladores-sonda ou scripts usados nas medições.
- A00, A02 e A15 registram resultados principalmente como narrativa.
- O catálogo externo de 40 defeitos citado pelo plano não possui hash ou cópia versionada no repositório.

**Impacto:** não é possível reproduzir integralmente a cadeia histórica usando apenas checkout e comandos documentados. Isso não prova que as medições sejam falsas.
**Confiança:** 100% quanto à ausência de evidência versionada.

## 8. Matriz de aderência A00–A20

| Atividade | Situação auditada | Julgamento |
|---|---|---|
| A00 | Não reproduzível | Baseline está descrita, mas a execução histórica completa não está preservada no Git. |
| A01 | Entregue | Guardrails foram versionados; também alteram governança global do repositório. |
| A02 | Não reproduzível | Resultados estão documentados; sondas e instaladores de medição não estão versionados. |
| A03 | Entregue | Contrato de assets/tamanho/digest é coerente com os assets avaliados. |
| A04 | Entregue | Classificação de artefatos está coberta, embora o reconhecimento de NSIS seja amplo demais para o gate final. |
| A05 | Entregue | Contrato permanece puro no domínio. |
| A06 | Parcial | Implementa origem NSIS/unmanaged de modo conservador; o plano previa também classificação explícita MSI. |
| A07 | Entregue | Resume, timeout, tamanho, SHA-256 e promoção atômica têm implementação e testes. |
| A08 | Parcial | O app envia PID; o instalador não aguarda esse PID. |
| A09 | Entregue | Preferência persistida com default `false`. |
| A10 | Entregue | Estados Available/Downloading/Ready/Failed, protótipo e testes de UI foram adicionados. |
| A11 | Parcial | Deduplicação, cancelamento e backoff em memória existem; falha de schedule e garantia “exactly once” não estão fechadas. |
| A12a | Entregue com dívida de processo | Semântica do switch foi corrigida; status entrou no commit seguinte. |
| A12 | Entregue | Configuração, motivos de indisponibilidade, receipt e protótipo existem. |
| A13 | Parcial | Wiring está presente e deliberadamente inativo; não houve inspeção visual/runtime completa. |
| A14 | Parcial | Override do feed existe, mas não viabiliza o `Setup.exe` local exigido pelo A20. |
| A15 | Entregue | Guardas e preparação para o modo do instalador foram registradas. |
| A16 | Parcial/reprovada | Happy path existe; PID não é aguardado, falhas não relançam, extração pode falhar sem receipt e sucesso não comprova saúde. |
| A17 | Parcial | Seis cenários e 33 asserções existem; rollback real, PID, relaunch e isolamento do receipt não são cobertos. |
| A18 | Parcial | YAML foi criado e analisado; não houve run real e o filtro pode falhar aberto. |
| A19 | Pendente | Feature flag continua falsa e mínimo continua na sentinela. |
| A20 | Inexequível | Pendente e incompatível com a restrição de URL do downloader na forma documentada. |

## 9. Pontos tecnicamente corretos

Os itens abaixo foram confirmados e devem ser preservados em eventual correção:

- opt-in persistido com default `false`;
- gate de ativação impede disparo prematuro nesta branch;
- arquitetura `presentation -> domain <- data` permanece preservada;
- domínio não recebeu dependências Ktor, Compose ou arquivo;
- plataforma/arquitetura/origem desconhecidas falham de forma conservadora;
- artefato exige SHA-256 e tamanho é validado quando informado;
- nomes com separadores ou `..` são rejeitados;
- URL de download é restringida a HTTPS/GitHub;
- download usa `.part`, suporta 206 e reinicia corretamente quando o servidor ignora Range com 200;
- timeout total curto do cliente comum foi substituído no download por timeout de conexão/socket adequado;
- publicação final usa movimento atômico quando suportado;
- cancelamento de corrotina é propagado;
- deduplicação evita reinício normal do mesmo download em voo;
- `prepare()` roda em dispatcher de I/O;
- instalação NSIS permanece per-user, sem elevação;
- `SetCompressor zlib` foi preservado;
- o caminho `/UPDATE` não usa `taskkill /F`;
- a nova árvore é extraída antes do primeiro rename destrutivo;
- staging e backup permanecem no mesmo volume;
- `SetOutPath "$TEMP"` evita que o CWD bloqueie o swap;
- relançamento usa `Exec`, não `ExecWait`;
- desktop shortcut e chave `Run` não são recriados automaticamente no caminho de update;
- SQLite, `team.json` e preferências ficam fora de `$INSTDIR`;
- UI possui estados localizados, progresso textual, fallback manual e semântica `Role.Switch`/`ToggleableState`.

Esses acertos reduzem risco e justificam reaproveitar a base. Eles não anulam os bloqueadores listados.

## 10. Validações executadas nesta auditoria

| Validação | Resultado |
|---|---|
| `.\gradlew.bat desktopTest --rerun --no-daemon` | `BUILD SUCCESSFUL`; 1.221 testes em 114 classes, zero falhas, erros ou ignorados |
| `git diff --check 8aa6e73..HEAD` | sem saída |
| Parser PowerShell sobre `Invoke-UpdateScenarios.ps1` | zero erros de sintaxe |
| Parser YAML dos workflows | válido |
| NSIS local | versão 3.12 localizada |
| Preprocessamento/compilação de inspeção do `.nsi` | exit code 0 |
| Sincronismo remoto da branch | HEAD local igual ao remoto |
| PR da branch | inexistente |
| Execução do Actions da branch | inexistente |

O teste verde comprova compilação e contratos cobertos pela suíte. **Não comprova** instalação empacotada, upgrade real, espera do processo, relançamento saudável, rollback após o primeiro rename, preservação do receipt ou comportamento do SmartScreen.

### Validações deliberadamente não executadas

- Nenhum cenário E2E de `Invoke-UpdateScenarios.ps1` foi executado nesta auditoria.
- Motivo: o roteiro usa e remove o receipt real em `%USERPROFILE%\.usage-monitor\update-receipt.properties` e aceita exclusão recursiva de `WorkDirectory` sem sentinel.
- Não houve smoke empacotado A20.
- Não houve inspeção SmartScreen ou validação de assinatura.
- Não houve instalação/upgrade em máquina limpa.
- Não houve execução do GitHub Actions.

Executar o roteiro no estado atual apenas para obter “verde” teria alterado estado real do usuário e não cobriria os principais bloqueadores. A não execução foi uma decisão de segurança, não uma alegação de aprovação.

## 11. Evidência faltante antes de qualquer ativação

Para reavaliar o recurso, seria necessário demonstrar, no mínimo:

1. sucesso somente após confirmação observável de inicialização/saúde, mantendo rollback até esse ponto;
2. restauração comprovada quando o segundo rename e/ou relaunch falham;
3. receipt produzido em todas as falhas, inclusive aborto automático de extração;
4. relançamento da versão anterior após falha controlada;
5. falha de `schedule()` refletida em estado observável;
6. vínculo inequívoco entre versão alvo, asset NSIS e mínimo compatível;
7. backoff pós-installer governado pelo receipt e resistente a restart;
8. E2E isolado de receipt, registro, atalhos e diretório de trabalho reais;
9. filtro CI que falha fechado e cobre todos os arquivos que alteram o fluxo;
10. smoke empacotado executável, incluindo app real, dados preservados, update, relaunch e rollback;
11. rastreamento separado do requisito Linux antes de encerrar #75.

Esta lista registra critérios de reauditoria. Nenhuma correção foi implementada como parte deste trabalho.

## 12. Conclusão final

### Decisão

- **Merge para ativar atualização automática:** reprovado.
- **Publicação de release com auto-update habilitado:** reprovada.
- **Execução de A19 no estado atual:** reprovada.
- **Encerramento da issue #75:** reprovado.
- **Uso da branch como base para correções posteriores:** aceitável, porque há componentes sólidos e o recurso permanece bloqueado.

### Justificativa objetiva

O fluxo atual consegue baixar, verificar e trocar arquivos no happy path, mas não fecha de forma transacional o ciclo completo “preparar → encerrar → instalar → relançar → confirmar saúde → remover rollback → refletir resultado”. Os defeitos P0/P1 permitem falso sucesso, perda de rollback, aplicação fechada após falha, execução de asset incompatível, repetição indefinida e CI verde sem executar o teste crítico.

A conclusão não depende de preferência arquitetural: ela decorre diretamente das ordens de operação, resultados ignorados, caminhos de erro e incompatibilidade entre o A20 documentado e a validação de URL implementada.

## 13. Integridade desta auditoria

- Nenhum arquivo de código, teste, configuração, workflow ou plano original foi alterado.
- Nenhuma API, schema, contrato ou comportamento da aplicação foi modificado.
- Nenhum commit, push, PR ou release foi criado.
- Este documento é o único artefato autorizado.
- O julgamento está vinculado ao snapshot `31bd8507baf3578eae3316d1e3637390a0444a74`; qualquer mudança posterior exige nova auditoria.
