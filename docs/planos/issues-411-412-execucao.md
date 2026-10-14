# Issues #411 e #412 — guia de uso e dicas ao abrir; teste instável do bot do Telegram

## Ponto de situação

**Estado atual:** `Em execução — #412 concluída (A1, A2); #411 aguardando a escolha da rodada visual AC.`

| Campo | Valor |
| --- | --- |
| Modelo | Claude Opus 5.5 (`claude-opus-5-5`) |
| Ferramenta | Claude Code (desktop, aba Code) |
| Data | 2026-10-13 |
| Branch | `feat/411-412-guide-and-telegram-flaky` (criada a partir de `main` em `34fa639a`) |
| Autor dos commits | identidade temporária da skill `usage-monitor-commit-push`, trailer `Co-Authored-By: Claude Opus 5.5` |

- As alterações pré-existentes em `server/package-lock.json` e `docs/llm/` ficam fora desta entrega.
- As duas issues saem na mesma branch por decisão do usuário. #412 vem primeiro porque o `verify`
  instável bloqueia o `publish-release`.

## Diagnóstico (evidência)

| # | Ponto | Evidência | Estado |
| --- | --- | --- | --- |
| E1 | `start()` inscreve o coletor de alertas de forma assíncrona | `TelegramBotService.kt:162-164`: `scope.launch { alerts.collect { … } }` em `Dispatchers.IO` | confirmado |
| E2 | O fluxo de alertas do teste descarta emissão sem inscrito | `TelegramBotServiceTest.kt`: `MutableSharedFlow(extraBufferCapacity = 4)`, sem `replay` | confirmado |
| E3 | Cinco testes emitem alerta logo após `start()` | 12 chamadas a `alerts.emit` em 5 testes | confirmado |
| E4 | Produção também usa `replay = 0` | `UsageAlertViewModel.kt:58-62`; bot iniciado na construção (`AppViewModels.kt:291`); o primeiro alerta depende da primeira coleta, que passa pela rede | perda no arranque **não confirmada**; semântica de produção intocada |
| E5 | A API falsa perde mensagem enfileirada durante o lote | `FakeTelegramApi.getUpdates`: `synchronized(pending) { pending.toList().also { pending.clear() } }`, e o `api.pending +=` do teste não passa pelo lock — o que entra entre o `toList()` e o `clear()` é apagado | confirmado (A2) |
| E6 | Ajuda tem 12 tópicos (`HelpTopic`) e GIF por tópico | `HelpCatalog.kt` (602 linhas), `src/desktopMain/resources/help/*.gif` | confirmado |
| E7 | Texto da ajuda desatualizado | `HelpCatalog.kt:98`: "A janela principal traz um card por fonte" — não há janela principal desde a HUD única | confirmado |
| E8 | Ajuda não cobre funções recentes | sem tópico para bot do Telegram, acesso web local, comparação de modelos, análise de sessão, contas Codex extras, recolher a HUD | confirmado |
| E9 | `HelpCatalog.kt` passa de 800 linhas se ganhar ~6 tópicos nos dois idiomas | teto do `ArchitectureRulesTest` | confirmado — dividir antes de crescer |

## Decisões

1. Dica abre em **toda** abertura, inclusive pelo `--autostart` (usuário, 2026-10-13).
2. Dica no máximo **uma vez por dia** (dia em `America/Sao_Paulo`), próxima da fila, sem repetir até esgotar (usuário).
3. **Exceção à regra de enum aprovada para `HelpTopic`** (usuário): o único `when` exaustivo sobre ele é o `HelpCatalog`, exatamente onde a falta deve quebrar a compilação. Precedente: X1 (#399).
4. Precedência por abertura, nunca dois modais juntos: Novidades > Guia de introdução > Dica do dia.
5. Guia automático só em instalação nova: `releaseNotesSeenVersion == null && onboardingCompletedVersion == null`, lido antes do efeito que grava a marca das Novidades; o recibo do instalador é só guarda, nunca condição — ele não existe no macOS e no Linux é escrito depois de o app subir (#127).
6. Preferências em `PreferencesSettings` (não é segredo). Janelas novas por `AppDialogWindow`, sem pré-aquecimento, abertas depois de a HUD estar na tela.

### Inicialização por plataforma

| Ponto | Windows | Linux | macOS |
| --- | --- | --- | --- |
| `--autostart` | valor `Run` (`AutoStartManager.kt:166`) | `Exec=` do `.desktop` (`:515`) | `ProgramArguments` do LaunchAgent (`:536`) |
| Recibo do instalador | antes de o app subir | depois (#127) | não existe |
| Preferência | registro `HKCU\Software\JavaSoft\Prefs\com\usagemonitor` | `~/.java/.userPrefs/com/usagemonitor/prefs.xml` | `~/Library/Preferences/com.apple.java.util.prefs.plist` |
| Verificação | testado | X11 antes do merge ou risco no PR (#342) | nunca verificado em Mac real |

## Execução — uma atividade, um commit

| # | Atividade | Issue |
| --- | --- | --- |
| A1 | `test(telegram)`: esperar o coletor de alertas antes de emitir | #412 |
| A2 | `test(telegram)`: retirada atômica das mensagens na API falsa | #412 |
| B1 | Inventário de funções e dicas (neste documento) | #411 |
| B2 | Rodada visual AC — 10 modelos de guia e dica do dia; **parada para escolha** | #411 |
| B3 | `refactor(help)`: dividir `HelpCatalog` por idioma | #411 |
| B4 | `feat(help)`: tópicos das funções recentes e texto atualizado | #411 |
| B5 | `feat(onboarding)`: decisão de arranque e preferências | #411 |
| B6 | `feat(onboarding)`: janela do guia de introdução | #411 |
| B7 | `feat(tips)`: dica do dia com opção de desligar | #411 |

## Pontos de situação

| Atividade | Commit | Evidência | Estado |
| --- | --- | --- | --- |
| A1 | `test(telegram): await the alert collector before emitting` | Reprodução: `delay(200)` temporário antes do `collect` em `start()` → `gradlew.bat desktopTest --tests "com.usagemonitor.TelegramBotServiceTest"`: 33 testes, **5 falhas** — exatamente os 5 que emitem alerta, incluindo o da issue. Com `startAndAwaitAlertCollector()` e o mesmo atraso: os 5 passam | concluída |
| A2 | `test(telegram): drain the fake update queue atomically` | Achado da A1: com o atraso, falharam 2 testes que não emitem alerta. Reprodução: `Thread.sleep(30)` temporário entre `toList()` e `clear()` → 33 testes, **9 falhas**, todos com mensagem ou toque enfileirado depois de `start()`. Com `ConcurrentLinkedQueue` + `poll()` e a mesma espera dentro da retirada: 0 falhas. Sem instrumentação, A1 + A2: `gradlew.bat desktopTest --tests "com.usagemonitor.TelegramBotServiceTest" --rerun` 20×: 20 verdes, 33 testes, 0 falhas; `gradlew.bat allTests`: 2704 testes, 0 falhas | concluída |

## Problemas em aberto e riscos

- E4: possível perda do primeiro alerta em produção se ele for emitido antes de o bot se inscrever. Sem evidência; não vira mudança sem ela.
- `Main.kt` (369 linhas) e `ModalWindowsHost.kt` (337) crescem com as janelas novas.
- Teste manual de instalação nova exige limpar chaves de preferência: backup do armazenamento do SO antes.

## Desvios do plano e achados da execução

- **A2 não estava no plano.** A reprodução da A1 expôs uma segunda corrida, na API falsa (E5). Mesma issue, causa diferente: commit próprio.
