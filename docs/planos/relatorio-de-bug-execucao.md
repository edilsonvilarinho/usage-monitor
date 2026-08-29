# Relatório de bug: trilha de eventos, captura em crash e export para issue

Plano de execução da [issue #123](https://github.com/edilsonvilarinho/usage-monitor/issues/123).
Branch `feat/bug-report-breadcrumbs`, worktree `../usage-monitor-123`.

## Problema

Um erro no app não deixa rastro nenhum fora dos recorders opt-in de crédito da Anthropic e do Codex —
e os dois guardam **corpo de resposta HTTP**, não a sequência de passos do usuário. Quando algo
quebra, a única fonte é o que o usuário lembra de descrever.

## Objetivo

1. Trilha leve de eventos (*breadcrumbs*) sempre gravada em disco, para reconstruir os passos até o erro.
2. Um ponto na aba Geral das Configurações para descrever um problema e gerar um pacote de diagnóstico.
3. Captura best-effort da janela do app em caso de falha não tratada.
4. Abertura de uma issue pré-preenchida no GitHub, para o usuário revisar e publicar.

**Nada sai da máquina do usuário automaticamente.**

## Decisões registradas

### Entrega por arquivo + navegador, nunca por envio

A ideia original — mandar o relatório por e-mail para um endereço fixo — foi descartada na issue. O
app é distribuído publicamente e não existe infraestrutura de envio no repositório. As três formas de
viabilizá-la (endpoint próprio com SMTP, `mailto:` manual, ou chave de provedor transacional embutida
no binário) ou exigem hospedar um serviço novo, ou vazam uma credencial num app público. A mesma
régua que já rejeita hardcode de credencial nas integrações existentes rejeita as três.

O app grava o pacote em disco por um diálogo de salvar — mesmo padrão do `DesktopUsageExportWriter` —
e abre o navegador na página de nova issue com título e corpo pré-preenchidos. Quem publica é o
usuário.

### Respostas às três perguntas em aberto da issue

1. **A aba Geral não tem botão `PRIMARY` hoje.** Verificado em
   `SettingsDialogContent.kt` (`GeneralSettingsTab`, ~386–494): ela é só dois `AppDataSurfaceFlush`
   com `AppSectionHeader` ("Aparência" e "Sistema") e linhas de opção, sem nenhum `AppButton`. Logo
   "Reportar um bug" entra como **`PRIMARY`**, no `trailing` do cabeçalho de uma seção nova
   "Diagnóstico" — o mesmo lugar do "Adicionar" da aba Contas, que é o precedente do repositório para
   ação sobre a seção inteira.
2. **Cortes.** `breadcrumbs.jsonl` usa **200/100 linhas**, os mesmos `MAX_LINES`/`KEPT_LINES` do
   `StartupDiagnostics` — dois cortes para o mesmo tipo de arquivo seriam dois donos da mesma
   decisão. No corpo pré-preenchido da issue entram os **últimos 30 breadcrumbs**, e o corpo é
   truncado em **6.000 caracteres** antes do percent-encoding, com folga para o título.
3. **Repositório alvo.** Constante única `BUG_REPORT_REPOSITORY_URL` em `desktopMain`, sem override
   por variável de ambiente: build de fork continua abrindo o upstream, que é onde as issues são
   triadas.

### Desenho

- **`LocalBreadcrumbRecorder` é modelado em `StartupDiagnostics.kt`**: mesmo lock, mesmo trim antes do
  append, mesmo `restrictToOwnerReadWrite`, mesmo `runCatching` que nunca derruba quem chamou. Não
  abrir um segundo desenho de recorder para o mesmo tipo de arquivo.
- **`toJson()` do envelope é montado à mão, não por `kotlinx.serialization`.** O envelope mora no
  domain, e o domain não importa biblioteca externa (`CLAUDE.md`) — foi por isso que o
  `UsageExporter` foi parar em `data`. Aqui o pacote é uma árvore de cinco campos e um vetor de
  objetos de três campos: um escapador de string coberto por teste custa menos que mover a entidade
  para fora do domain só para serializá-la.
- **Writer e capturer injetáveis**, precedente exato do `DesktopUsageExportWriter` e do
  `rememberClipboardWriter`: teste de componente não abre diálogo de arquivo, não escreve no disco de
  quem roda a suíte, e `java.awt.Robot` não é utilizável em CI headless.
- **Nenhuma composable nova em `main()`** — o método já estourou o backend JVM uma vez. O handler de
  crash é registrado **antes** do `application { }`, e a leitura do marcador é função de topo chamada
  de dentro de um `remember`.
- **Nenhuma animação infinita** no diálogo — trava o `waitForIdle` dos testes de componente.
- **Sem hostname e sem usuário do sistema.** O pacote vira o corpo de uma issue pública, e as duas
  informações identificam a pessoa sem ajudar a diagnosticar o app.

## Pontos de situação

| # | Micro-atividade | Situação | Commit | Verificação |
|---|---|---|---|---|
| B01 | Plano `docs/planos/relatorio-de-bug-execucao.md` com a tabela de pontos de situação | ✅ Concluída | `2a0ad62` | `gh issue view 123` lido; `SettingsDialogContent.kt:386-494` lido — `GeneralSettingsTab` sem nenhum `AppButton`, confirmando a resposta 1 |
| B02 | Comentário vivo criado na issue #123 com a tabela | ✅ Concluída | `20e588d` | `gh issue comment 123 --body-file status-123.md` → `issues/123#issuecomment-5462958514` |
| B03 | `BreadcrumbCategory` + `Breadcrumb` (domain) | ✅ Concluída | `ba721bf` | `desktopTest --tests "com.usagemonitor.domain.BreadcrumbTest"` → `tests="6" failures="0" errors="0"`, BUILD SUCCESSFUL em 34s |
| B04 | `BugReportMachineInfo` — OS, versão, arquitetura, JVM, versão do app, idioma, escala de UI, resolução, fuso | ✅ Concluída | `24e30b0` | `desktopTest --tests "com.usagemonitor.domain.BugReportMachineInfoTest"` → `tests="3" failures="0" errors="0"`, BUILD SUCCESSFUL em 33s |
| B05 | `BugReportEnvelope` + `toJson()` | ✅ Concluída | (este commit) | `desktopTest --tests "com.usagemonitor.domain.BugReportEnvelopeJsonTest"` → `tests="5" failures="0" errors="0"`, BUILD SUCCESSFUL em 29s |
| B06 | `toGithubIssueBody()` com truncagem (30 breadcrumbs, 6.000 chars) | ⏳ Pendente | — | — |
| B07 | `BreadcrumbRecorder` (interface no domain) + implementação nula | ⏳ Pendente | — | — |
| B08 | `LocalBreadcrumbRecorder` — jsonl, lock, trim 200/100, `restrictToOwnerReadWrite` | ⏳ Pendente | — | — |
| B09 | Pontos de chamada de navegação (abertura de cada tela/modal) | ⏳ Pendente | — | — |
| B10 | Pontos de chamada de use case (início e resultado) | ⏳ Pendente | — | — |
| B11 | Pontos de chamada nos `catch` que hoje falham em silêncio | ⏳ Pendente | — | — |
| B12 | `GenerateBugReportUseCase` | ⏳ Pendente | — | — |
| B13 | `CrashHandler` — handler, breadcrumb `CRASH`, marcador `pending-crash.json` | ⏳ Pendente | — | — |
| B14 | Captura best-effort da janela via capturer injetável | ⏳ Pendente | — | — |
| B15 | Registro do handler antes de `application { }` e leitura do marcador no arranque | ⏳ Pendente | — | — |
| B16 | `DesktopBugReportWriter` — diálogo de salvar, writer injetável | ⏳ Pendente | — | — |
| B17 | `BugReportDialog` stateless | ⏳ Pendente | — | — |
| B18 | Seção "Diagnóstico" na aba Geral com o botão `PRIMARY` | ⏳ Pendente | — | — |
| B19 | Botão "Abrir issue no GitHub" — URL montada e navegador aberto | ⏳ Pendente | — | — |
| B20 | Fluxo do marcador: arranque seguinte oferece o relatório e apaga o marcador | ⏳ Pendente | — | — |
| B21 | `prototipo-visual-opencode.html` — `§12 #cfg-geral` ganha a seção nova | ⏳ Pendente | — | — |
| B22 | `allTests` verde + QA manual: crash proposital, dark/light, PT/EN | ⏳ Pendente | — | — |

## Verificação

```bat
gradlew.bat desktopTest --tests "com.usagemonitor.domain.*"
gradlew.bat desktopTest --tests "com.usagemonitor.data.*"
gradlew.bat desktopTest --tests "com.usagemonitor.ui.*"
gradlew.bat allTests
```

QA manual: gerar um crash proposital e confirmar que o próximo arranque oferece o relatório com a
trilha e, quando possível, a captura da janela.

## Fora do escopo

- Envio automático de qualquer tipo (e-mail, webhook, telemetria).
- Log geral de toda a aplicação.
- Hostname, usuário do sistema, IP.
- Conteúdo de prompt, resposta de IA, corpo de resposta HTTP ou credencial em qualquer forma.
