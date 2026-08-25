# Parecer técnico conclusivo — atualização automática Windows

## 1. Identificação e procedência

| Campo | Valor |
|---|---|
| Data do parecer | 2026-08-22 |
| Repositório | `C:\Users\edils\workspace\usage-monitor` |
| Branch | `feat/auto-update-windows-75` |
| HEAD confrontado | `6a69659d589cbdad18b96e0b930e759199a87938` |
| Snapshot de código originalmente auditado | `31bd8507baf3578eae3316d1e3637390a0444a74` |
| Auditoria confrontada | `docs/planos/auditoria-atualizacao-automatica-windows-2026-08-22.md` |
| SHA-256 da auditoria no working tree | `01C4C84D6D569BAD2E6AAFE3BE56F6002AB630D8C292541FD925BF3BA713F207` |
| Contra-auditoria confrontada | `docs/planos/contra-auditoria-atualizacao-automatica-windows-2026-08-22.md` |
| SHA-256 da contra-auditoria | `6C5504123CF05CDD2682014FC185D9CA0FBD9693190FBE12027D229DBC02D14C` |
| Modelo deste parecer | `gpt-5.6-sol` |
| Nível de execução deste parecer | `ultra` |

O modelo e o nível de execução foram lidos do `turn_context` da sessão Codex que produziu este
parecer. `ultra` identifica o modo de execução registrado pelo Codex; não é apresentado como valor do
parâmetro público `reasoning.effort`. A documentação oficial da OpenAI lista, para GPT-5.6, os níveis
públicos até `max`: [OpenAI Model Guidance](https://developers.openai.com/api/docs/guides/latest-model).

A auditoria usada como entrada está modificada no working tree e não corresponde integralmente à
versão adicionada pelo commit `36bf529`. Por isso este parecer prende a entrada ao SHA-256 acima, não
apenas ao commit. A contra-auditoria corresponde ao arquivo versionado no HEAD `6a69659`.

Entre o snapshot `31bd850` e o HEAD deste parecer entraram somente os dois relatórios. Não houve
mudança de código, teste, instalador ou workflow nesse intervalo. Logo os dois documentos analisam o
mesmo código e podem ser confrontados diretamente.

## 2. Veredito executivo

> **REPROVADA a ativação da atualização automática, a execução da A19, a publicação de release com o
> auto-update habilitado e o encerramento da issue #75.**

A contra-auditoria não derruba esse veredito. Ela melhora parte da calibragem, identifica lacunas
adicionais e corrige o impacto de alguns achados, mas confirma o núcleo transacional que impede o
aceite.

A branch pode ser reaproveitada como base porque contém decisões defensivas válidas: opt-in desligado
por padrão, feature gate inativo, seleção conservadora de origem, download retomável, SHA-256,
staging antes do swap, `SetCompressor zlib` e ausência de `taskkill /F` no caminho `/UPDATE`.

O recurso continua seguro somente por inatividade:

- `AUTO_UPDATE_SHIPPED = false` em `AutoUpdateController.kt:26` impede a criação do instalador;
- `MIN_UPDATABLE_TARGET_VERSION = "999.0.0"` em `WindowsAppUpdateInstaller.kt:35` mantém o alvo
  inalcançável;
- a preferência permanece `false` por padrão.

Os bloqueadores centrais permanecem comprovados:

1. o instalador grava `status=success` e remove `$INSTDIR.old` antes de verificar se a aplicação nova
   inicia e permanece saudável;
2. falhas controladas encerram o instalador sem relançar a instalação preservada;
3. falha automática de extração não produz receipt nem limpa o staging;
4. o resultado de `schedule()` é descartado pelo ViewModel;
5. o rollback mais crítico não é verificado nem existe recuperação após interrupção entre os dois
   renames;
6. não houve smoke empacotado de upgrade, relançamento e rollback.

## 3. Método e evidências novas

Foram relidos integralmente os dois documentos pelos hashes registrados e confrontados os pontos
controvertidos com:

- `src/installer/UsageMonitor.nsi` e o roteiro `Invoke-UpdateScenarios.ps1`;
- downloader, resolvedor de origem, controller e ViewModel;
- `Main.kt`, inclusive os três caminhos de encerramento;
- `ci.yml`, `release-linux.yml` e `build.gradle.kts`;
- artefatos locais existentes e assets publicados da v37.0.0;
- resultados XML atuais do `desktopTest`.

### 3.1 Validações executadas

| Validação | Resultado |
|---|---|
| `.\gradlew.bat desktopTest --rerun --no-daemon` | `BUILD SUCCESSFUL` em 1m42s; 114 suítes, 1.221 testes, zero falhas, erros ou ignorados |
| Soma dos XML em `build/test-results/desktopTest` | 114 suítes, 1.221 testes, 0 falhas, 0 erros, 0 ignorados |
| Consulta dos assets da release `v37.0.0` | sete assets; todos possuem `digest` SHA-256 retornado pela API do GitHub |
| Busca por `checksums.txt` no workflow atual | não existe geração ou publicação desse arquivo |
| Payload local do instalador | `build/installer/files/app/usage-monitor-desktop-36.0.0-d3e5e1e8f7b83b815fc981a6e5528082.jar` dentro do Setup denominado v37 |
| Origem da instalação real aberta | `jpackage.app-path=C:\Users\edils\AppData\Local\Usage Monitor\Usage Monitor.exe`; registro HKCU compatível com NSIS per-user v23 |

Uma segunda invocação concorrente de `desktopTest --rerun --no-daemon` falhou antes de executar os
testes porque `build/test-results/desktopTest/binary/output.bin` estava aberto pela execução que já
corria. Isso é contenção do ambiente de teste, não falha do produto, e não altera o resultado completo
posteriormente confirmado nos XML.

### 3.2 Validações não executadas

- nenhum cenário NSIS S1–S6 foi repetido neste parecer;
- nenhum instalador foi executado;
- nenhum smoke empacotado, upgrade real, relançamento saudável ou rollback foi exercitado;
- nenhum teste de SmartScreen, antivírus ou assinatura foi executado;
- nenhum job de Actions desta branch foi disparado;
- nenhum commit, push, PR ou release foi criado.

O teste JVM verde comprova apenas o que a suíte cobre. Não comprova o ciclo empacotado de atualização.

## 4. Achados aceitos pelos dois documentos

Os 24 itens abaixo foram confirmados sem ressalva material pela contra-auditoria. O parecer os mantém,
com a única atualização explícita indicada em P2-16.

| ID | Decisão conclusiva | Evidência central |
|---|---|---|
| P0-01 | **Confirmado — P0.** Sucesso e remoção do backup antecedem o relançamento e qualquer prova de saúde. | `UsageMonitor.nsi:367-376`; `Exec` somente em `:186-192`. |
| P1-02 | **Confirmado — P1.** Falhas controladas não relançam a versão anterior. | `UsageMonitor.nsi:313-334`; `.onInstFailed` em `:199-206`. |
| P1-03 | **Confirmado — P1.** Falha automática de `File /r` não grava receipt, não limpa staging e não relança. | `UsageMonitor.nsi:195-206,277-281`. |
| P1-04 | **Confirmado — P1.** O `Result<Unit>` de `schedule()` é descartado. | `DashboardViewModel.kt:883-890`. |
| P1-07 | **Confirmado — P1.** Restauração após falha do segundo rename não é verificada nem testada. | `UsageMonitor.nsi:322-326`; cenários S1–S6. |
| P1-08 | **Confirmado — P1.** Receipt de falha não governa backoff ou nova tentativa. | `AutoUpdateController.kt:91-99`; `Main.kt:1666-1670`; `DashboardViewModel.kt:820-845`. |
| P1-10 | **Confirmado — bloqueio de conclusão.** A19 e A20 continuam pendentes e o recurso permanece inalcançável. | Feature flag falsa, mínimo `999.0.0` e tabela A00–A20. |
| P2-01 | **Confirmado — P2.** O roteiro E2E usa e remove o receipt real do usuário sem backup. | `Invoke-UpdateScenarios.ps1:39,120-130,179,197,214,248-251`. |
| P2-02 | **Confirmado — P2.** `WorkDirectory` livre pode virar alvo de exclusão recursiva sem sentinela. | `Invoke-UpdateScenarios.ps1:22-25,146`. |
| P2-03 | **Confirmado — P2.** Limite de tentativas existe apenas na memória do ViewModel. | `DashboardViewModel.kt:833-845`. |
| P2-04 | **Confirmado — P2.** Há TOCTOU entre SHA-256 verificado no download e arquivo executado no agendamento. | `UpdateArtifactDownloader.kt:145-151`; `WindowsAppUpdateInstaller.kt:117-140`. |
| P2-05 | **Confirmado — P2.** Parcial completo sem tamanho declarado pode repetir HTTP 416. | `UpdateArtifactDownloader.kt:53-67,95-119`. |
| P2-06 | **Confirmado — P2.** Resolução de suporte pode executar `reg.exe` sincronamente durante composição. | `AutoUpdateController.kt:45-46`; `Main.kt:1666-1668`; `AutoStartManager`. |
| P2-07 | **Confirmado — P2.** Ligar o interruptor não inicia imediatamente download já anunciado. | `DashboardViewModel.kt:863-875`. |
| P2-08 | **Confirmado — P2, risco estático.** Estado concorrente não é reduzido atomicamente. | `DashboardViewModel.kt:741-875`; dispatcher multithread. |
| P2-09 | **Confirmado — P2.** O recorte de arquivos do job do instalador é incompleto e o job não possui timeout próprio. | `ci.yml:119-150`. |
| P2-10 | **Confirmado — P2.** S1–S6 não exercitam as garantias mais críticas. | `Invoke-UpdateScenarios.ps1:167-246`. |
| P2-12 | **Confirmado — P2 residual.** SHA-256 protege integridade, mas não substitui assinatura independente nem separa binário e digest de uma fonte comprometida. | Contrato `assets[].digest`; Setup sem assinatura. |
| P2-16 | **Confirmado com incerteza reduzida — P2.** `jpackage.app-path` funciona na instalação NSIS v23 desta máquina, mas não foi validado no app-image desta branch nem no A20. | `jcmd ... VM.system_properties`; `atualizacao-automatica-windows-execucao.md:424-429`. |
| P2-17 | **Confirmado — P2 de rastreabilidade.** A issue #75 ainda contém requisito Linux sem issue substituta. | Issue #75 e exclusão Linux no plano. |
| P3-01 | **Confirmado — P3.** A12a não foi registrada no mesmo commit da alteração. | Commits `04fadb7` e `e461f7c`. |
| P3-02 | **Confirmado — P3.** Evidência reproduzível de A16 entrou somente no commit seguinte. | Commits `cefc8a5` e `408d5d0`. |
| P3-03 | **Confirmado — P3.** “PR 1” e “PR 2” são blocos de uma branch, não PRs separados. | Ausência de PR da branch. |
| P3-05 | **Confirmado — P3.** Parte das sondas e medições históricas não está versionada. | A00, A02 e A15; catálogo externo sem hash versionado. |

## 5. Arbitragem dos pontos contestados

| ID | Posição da auditoria | Posição da contra-auditoria | Decisão conclusiva |
|---|---|---|---|
| P0-02 / A20 | A20 é inexequível como documentado. | Feed local pode apontar para asset real do GitHub. | **A auditoria está correta na literalidade.** A20 exige feed HTTP e Setup local; o downloader rejeita o asset local. Pré-release GitHub é outro procedimento, exige publicação externa e dois app-images genuínos. É bloqueio do roteiro vigente, não impossibilidade estrutural do produto. |
| P1-01 / PID | PID é enviado, mas não aguardado; orçamento documentado não existe. | Extração anterior ao rename amplia o tempo e o rename é sonda mais forte. | **P2 operacional + P3 documental.** A extração aumenta um tempo não medido. Rename bem-sucedido comprova ausência de handle que bloqueie a troca, não término lógico nem saúde. O plano e comentários afirmam espera por PID inexistente. |
| P1-05 / versão do asset | Tag e asset executado não estão vinculados. | Workflow normal produz um único Setup da tag. | **P2 obrigatório antes de A19.** O fluxo normal reduz probabilidade, mas não impede asset manual, obsoleto ou múltiplo. O consumidor precisa recusar ambiguidade e vincular versão. |
| P1-06 / gate A19 | Teste aceita qualquer mínimo diferente da sentinela. | Sem exposição atual porque dois guardas estão desligados. | **Mantido como P1 pré-ativação, sem exposição atual.** O teste existe justamente para a atividade que removerá os guardas; sua fraqueza não pode ser rebaixada por eles ainda estarem ativos. |
| P1-09 / CI | Filtro pode falhar aberto e pular cenários. | Falha é determinística em checkout raso, mas release possui gate incondicional. | **P2 de feedback/gate de PR.** A reprodução local é forte, porém não houve run real desta branch. O `verify` atual reduz o impacto sobre uma release futura, mas esse gate também não foi exercitado aqui. |
| P2-11 / S6 | S6 chama mudança de comportamento de “não regressão”. | A mudança é maior: `/S` sobrepõe e pode deixar resíduo. | **Confirmado e refinado — P2.** A contra-auditoria reforça o achado; S6 não prova não regressão e não verifica resíduos da versão anterior. |
| P2-13 / feed visível | URL integral pode expor segredo em screenshot. | É aviso deliberado de teste, controlado pelo operador. | **P3.** O valor realmente é exibido; nenhum segredo real foi encontrado e a precondição é local. O aviso não elimina o risco, apenas reduz a severidade. |
| P2-14 / parciais | Parciais de versões superadas podem acumular. | SHA inválido é apagado; o caso é mais estreito. | **Confirmado com escopo reduzido — P2.** Restam parciais interrompidos por rede; cada parcial pode estar próximo do tamanho total. |
| P2-15 / redirects | Host final não é revalidado. | Comportamento é deliberado e SHA-256 compensa enquanto o feed é confiável. | **P3 residual deliberado.** Não é bloqueador independente. O risco relevante é quem controla simultaneamente feed, URL e digest. |
| P3-04 / autoria | `AGENTS.md` foi descumprido pelo desenvolvimento Claude. | `AGENTS.md` seria específico do Codex; `CLAUDE.md` governaria Claude. | **Ambiguidade de governança — P3.** Os metadados existem, mas nenhum documento prova a regra de precedência entre os dois arquivos para todos os agentes. Não cabe declarar erro factual nem violação fechada. |

## 6. Arbitragem dos novos achados N1–N6

### N1 — allowlist prende o host, não o repositório

**Válido com precondição restrita — P2 antes de A19.** `UpdateArtifactDownloader.kt:199-223`
aceita qualquer caminho em `github.com` ou `www.github.com`. Com o override, a mesma fonte escolhe URL
e digest. Isso não é vulnerabilidade remota na configuração padrão: exige controle local de
`USAGE_MONITOR_UPDATE_FEED_URL`, além de opt-in e feature habilitada.

A correção proposta pela contra-auditoria é internamente contraditória: exigir HTTPS no override
inviabiliza o feed HTTP local usado para declarar A20 executável. A reauditoria precisa escolher uma
política única: fixture de loopback explicitamente isolada ou asset remoto de teste controlado. Não
cabe prescrever simultaneamente as duas políticas.

### N2 — instalador baixado retido após sucesso

**Válido com impacto inflado — P2 operacional.** O instalador recém-preparado fica no diretório de
updates porque `prune()` preserva o asset atual. Ele não fica necessariamente “para sempre”: a
próxima preparação bem-sucedida apaga o anterior. O estado correto é a retenção de aproximadamente
um instalador completo até a próxima preparação ou limpeza explícita.

### N3 — receipt sem timestamp e sem consumo

**Parcialmente válido; incorporado ao P1-08.** Ausência de consumo inequívoco é defeito real. Timestamp
não foi provado como requisito universal: correlação por versão, estado persistente e consumo
idempotente podem resolver o reprocessamento. Timestamp passa a ser obrigatório apenas se a política
de backoff depender de idade ou TTL.

Apagar o receipt imediatamente também não é solução completa, porque ele alimenta a informação
histórica mostrada nas Configurações. O contrato deve separar “último resultado para exibição” de
“evento ainda não processado”.

### N4 — tempo de extração amplia o orçamento

**Não é achado independente.** É refinamento de P1-01. A extração ocorre antes do primeiro rename e
acrescenta tempo ao encerramento, mas esse tempo não foi medido no pacote real e não implementa a
espera por PID afirmada pelo plano.

### N5 — pico de disco sem verificação prévia

**Válido — P2.** A estimativa de “~360 MB” da contra-auditoria está subestimada. As medições usadas
neste parecer foram:

| Componente coexistente | Bytes |
|---|---:|
| Instalação real atual | 186.631.094 |
| App-image local usado como aproximação do staging novo | 196.216.457 |
| `UsageMonitor-Setup-37.0.0.exe` local | 122.309.088 |
| **Total aproximado** | **505.156.639** |

O total é aproximação medida, não tamanho contratual. Além disso, validar espaço apenas dentro do
NSIS pode ser tarde: o download completo já foi gravado em `user.home`, enquanto a instalação usa
`$LOCALAPPDATA`. A verificação precisa considerar os volumes efetivos antes do download e antes do
staging.

### N6 — divergência de contagem da suíte

**Rejeitado — erro factual da contra-auditoria.** O plano registra:

- A13: 114 classes / 1.217 testes em `atualizacao-automatica-windows-execucao.md:461`;
- A14: 114 classes / 1.221 testes em `:463`.

O commit A14 `88bab58` acrescentou quatro testes: dois em `AppUpdateRepositoryImplTest` e dois em
`AutoUpdateToggleTest`. A execução atual confirmou 114 suítes / 1.221 testes / zero falhas. O comando
de diff usado pela contra-auditoria não sustenta a premissa de que nenhum teste mudou entre as duas
contagens.

## 7. Lacunas adicionais confirmadas durante a arbitragem

### L1 — não existe recuperação após interrupção entre os dois renames

Depois de `Rename "$INSTDIR" "$UpdateBackup"` em `UsageMonitor.nsi:302` e antes de
`Rename "$UpdateStaging" "$INSTDIR"` em `:319`, queda de energia, término do processo ou falha externa
pode deixar o caminho oficial ausente, com dados apenas em `.old` e `.new`. Não existe bootstrap que
reconheça e recupere esse estado no próximo arranque.

**Classificação:** P1 latente, sem exposição atual porque o recurso está desligado.

### L2 — operações pós-swap não são verificadas transacionalmente

Depois do segundo rename, gravação de registro, criação do uninstaller, atalhos, log, receipt e
remoção do backup ocorrem em `UsageMonitor.nsi:341-376` sem verificação que governe rollback. O fluxo
pode ter a árvore nova no disco e metadados incompletos, ou declarar sucesso sem diagnóstico íntegro.

**Classificação:** P1 antes da ativação.

### L3 — `shutdownApplication` não fecha `profileRegistry`

O shutdown hook e `onDispose` fecham `profileRegistry` em `Main.kt:617-669`. O caminho explícito
`shutdownApplication`, usado pelo fechamento da janela e por “Reiniciar e atualizar agora”, não o
fecha em `Main.kt:1096-1119`. Como `shutdownStarted` já foi marcado, o shutdown hook posterior não
compensa a omissão.

**Classificação:** P2 de encerramento ordenado; falta teste que prove drenagem de qualquer escrita
pendente do registry.

### L4 — Setup local denominado v37 contém payload v36

O arquivo local `build/installer/UsageMonitor-Setup-37.0.0.exe` mede 122.309.088 bytes, mas o payload
em `build/installer/files/app` contém
`usage-monitor-desktop-36.0.0-d3e5e1e8f7b83b815fc981a6e5528082.jar`.

Isso confirma somente recompilação/parametrização do `.nsi`; não comprova um pacote completo desta
branch nem fornece os dois app-images genuinamente distintos exigidos por A20.

**Classificação:** P3 de evidência empacotada.

### L5 — pipeline não exige exatamente um MSI e um NSIS

`release-linux.yml:230-250` copia todos os `*.msi` e `UsageMonitor-Setup-*.exe` encontrados. O gate do
MSI verifica um nome esperado, mas não existe asserção de cardinalidade para as duas famílias. A task
`packageInstaller` em `build.gradle.kts:251-259` apenas registra o caminho quando o EXE existe; não
falha explicitamente quando ele não existe. `if-no-files-found: error` exige algum arquivo no
`dist-build`, não cada família obrigatória.

A release v37.0.0 prova apenas aquele run: foram publicados sete assets, incluindo um MSI e um NSIS,
todos com `assets[].digest`. Ela não transforma cardinalidade em invariável do workflow.

**Classificação:** P2 de pipeline antes da publicação com auto-update.

### L6 — origem empacotada foi validada apenas em instalação anterior

A instalação real aberta nesta máquina é NSIS per-user v23. O registro HKCU aponta para
`C:\Users\edils\AppData\Local\Usage Monitor`, e `jpackage.app-path` devolveu o executável nesse mesmo
diretório. Isso demonstra que a fonte primária funciona em uma instalação empacotada real.

Não demonstra que o app-image produzido por esta branch exponha a mesma propriedade nem que
`WindowsInstallOriginResolver` classifique corretamente o pacote usado no A20. P2-16 fica reduzido,
mas não encerrado.

## 8. Release, CI e `checksums.txt`

O workflow atual possui um gate de release melhor que o descrito pelo impacto original de P1-09:
`release-linux.yml:19-55` executa `allTests` e os cenários NSIS sem filtro, e `publish-release` depende
de `verify` e dos três builds em `:316-323`.

Isso é configuração estática, não evidência operacional desta branch:

- não existe run de Actions desta branch;
- a release v37.0.0 ocorreu antes da inclusão dos cenários NSIS no job `verify`;
- o Setup local não representa pacote completo da branch;
- os 33 asserts de S1–S6 usam payload mínimo e não exercitam saúde, PID vivo, interrupção entre
  renames, falha de extração, consumo do receipt ou rollback pós-swap.

Também não existe `checksums.txt` nos sete assets da v37.0.0, e o workflow atual não gera esse arquivo.
O app usa `assets[].digest` da API do GitHub e descarta assets sem papel executável, incluindo um
eventual `checksums.txt`. Qualquer documentação futura deve preservar essa distinção.

## 9. Critérios consolidados de reauditoria

Antes de A19, release com auto-update ou encerramento da #75, deve existir evidência reproduzível de:

1. sucesso gravado somente depois de confirmação observável de inicialização/saúde;
2. backup preservado até essa confirmação e rollback automático quando a versão nova não sobe;
3. recuperação determinística após interrupção entre os dois renames;
4. resultado verificado do segundo rename, da restauração e das operações pós-swap;
5. receipt produzido em todas as falhas, com ciclo de vida idempotente e separação entre evento
   pendente e histórico exibido;
6. falha de `schedule()` persistida de forma observável inclusive no caminho de saída;
7. vínculo inequívoco entre tag, versão do app, exatamente um asset NSIS compatível e mínimo que
   entende `/UPDATE`;
8. SHA-256 revalidado no agendamento ou carregado no contrato da preparação;
9. política única para feed/asset de teste: loopback isolado ou publicação remota controlada, sem
   contradizer a restrição HTTPS/repositório;
10. espaço livre validado nos volumes efetivos antes do download e antes do staging;
11. E2E isolado de receipt, diretório de trabalho, registro, atalhos e instalação real;
12. CI que falha fechado, cobre todos os arquivos relevantes, possui timeout e foi executado numa PR;
13. origem NSIS comprovada no app-image produzido pela branch;
14. smoke v37→versão nova com dois app-images genuínos, dados/preferências preservados, fechamento
    normal, botão de reinício, relançamento saudável, falhas induzidas e rollback;
15. validação de SmartScreen/antivírus/assinatura no fluxo silencioso;
16. rastreamento separado do requisito Linux antes de encerrar a issue #75.

A sugestão da contra-auditoria de deixar `.old` para o app apagar no primeiro arranque reduz a perda
de rollback, mas não fecha esses critérios: sem health handshake e sem bootstrap externo, ela preserva
rollback manual e não restaura automaticamente quando o app novo nem sequer inicia.

## 10. Conclusão final

### Decisões

- **Ativar atualização automática:** reprovado.
- **Executar A19:** reprovado.
- **Publicar release com auto-update habilitado:** reprovado.
- **Encerrar issue #75:** reprovado.
- **Usar a branch como base para correções posteriores:** aceitável.

### Síntese

A auditoria original acerta o veredito e o núcleo transacional, mas superestima a severidade de alguns
itens. A contra-auditoria melhora parte dessa calibragem e encontra riscos reais, porém:

- não torna A20 executável como ele foi escrito;
- minimiza a função do gate A19;
- trata como pequenas correções que atravessam NSIS, Kotlin, persistência e E2E;
- propõe políticas incompatíveis para o feed de teste;
- superestima a retenção do instalador;
- declara timestamp obrigatório sem demonstrar a política que o exige;
- subestima o pico de disco;
- erra a contagem histórica da suíte.

O ciclo completo `preparar → encerrar → instalar → recuperar de interrupção → relançar → confirmar
saúde → remover rollback → refletir resultado` continua aberto. Código correto no happy path e suíte
JVM verde não substituem aceite empacotado.

## 11. Integridade deste parecer

- Este arquivo é o único artefato criado por este trabalho.
- Nenhum arquivo de código, teste, configuração, workflow ou plano anterior foi alterado.
- A modificação preexistente em
  `docs/planos/auditoria-atualizacao-automatica-windows-2026-08-22.md` foi preservada integralmente.
- Os SHA-256 das duas entradas permaneceram os registrados na seção 1.
- Nenhum commit, push, PR ou release foi criado.
- Nenhuma correção de runtime foi implementada.
- O julgamento está vinculado ao HEAD, aos hashes de entrada e às evidências declaradas neste
  documento. Mudança posterior exige nova verificação.
