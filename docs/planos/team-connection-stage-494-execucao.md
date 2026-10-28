# Caminho da conexão nas telas de time — issue #494

Procedência: Claude Opus 5.5 (esforço padrão), Claude Code CLI no Windows 11, 2026-10-28, branch
`fix/issue-494-team-connection-stage`, commits com a identidade `claude <claude@anthropic.com>`.

## Objetivo

As superfícies de time mostravam a mensagem crua da exceção (`(certificate_expired) PKIX path validation failed: …`),
sem dizer de quem é o problema, sem "tentar de novo" e, nas Configurações, cortada no meio. O dono escolheu a opção
**AJ4 · Caminho da conexão** (galeria `build/gargantua-preview/team-error-options.html`): toda falha de conversa com o
servidor de time vira a etapa onde a conexão parou — Endereço › Conexão › Certificado › Servidor › Chave/Token —, com
a mesma classificação em todas as superfícies.

## Fatos confirmados (2026-10-28, `a3b2e52d`)

- `openssl s_client -connect lab.informata.com.br:5175 | openssl x509 -noout -dates`: `notAfter=Oct 27 15:21:43 2026
  GMT`, emissor Let's Encrypt YR2; `date -u` = 2026-10-28 13:47. A causa do print é o certificado do servidor vencido.
- Cadeia real da exceção no JDK 21 (`java Probe.java https://lab.informata.com.br:5175/health`):
  `SSLHandshakeException` → `SSLHandshakeException` → `sun.security.validator.ValidatorException` →
  `CertPathValidatorException` (`reason=EXPIRED`, `index=0`, `certPath` com 3 certificados, `notAfter` do índice 0 =
  `2026-10-27T15:21:43Z`) → `CertificateExpiredException`. A data sai do certificado, não da mensagem.
- `CompositeX509TrustManager.firstAccepting` (`SystemTrustStore.kt:94-106`) relança a **primeira** falha intacta: a
  cadeia que chega ao app é a do `cacerts`, igual à do probe (a mensagem do print é a mesma).
- Texto cru publicado: `TeamUsageViewModel.kt:628/716`, `TeamPresenceViewModel.kt:272/352`,
  `TeamKeysAdminViewModel.kt:178/182/203`, `AppSettingsFeedback.kt:192/221/255`, `TeamSyncService.kt:323`,
  `TuiTeamReader.kt:60`, `TerminalTeamRunner.kt:194/201`. O bot (`TelegramBotChatMessages.team`) responde só "did not
  answer right now".
- `AppErrorState` (`AppStates.kt:392`) já aceita `retryLabel`/`onRetry`; as telas de time não passam nenhum.
- HTTP de erro do servidor de time vira `TeamServerException(statusCode, …)` em `data` (`RemoteTeamDataSource.kt:533`);
  `presentation` não pode importar `data` (`ArchitectureRulesTest`).
- `TeamUsageViewModel.kt` tem 752 linhas (teto 800; acima de 750 a mudança começa extraindo).

## Decisões

- **Classificação pelo tipo**, nunca por substring (regra do `isConnectivityFailure`): `UnknownHostException` →
  Endereço; `ConnectException`/`NoRouteToHostException`/timeout → Conexão; `SSLHandshakeException` → Certificado, com
  o motivo pela causa (`CertPathValidatorException.reason` `EXPIRED`/`NOT_YET_VALID`, `CertPathBuilderException` →
  não confiável); status 401/403 → Chave/Token; outro status → Servidor. Exceção sem tipo conhecido não é
  classificada: a tela mostra a mensagem de hoje.
- **Etapa anterior só ganha ✓ quando a falha seguinte a prova** (TLS falhou ⇒ endereço e conexão passaram).
- O status HTTP sobe ao domain como porta (`HttpStatusFailure`), implementada pelo `TeamServerException`.
- Modelo puro no domain; classificador em `presentation/viewmodel` (ao lado do `NetworkFailure.kt`); frases em
  rótulos `localized(...)`; uma primitiva de trilha usada por todas as telas.
- `status --json` é contrato: campo **novo** (`stage`, `reason`), `lastFailure` intacto.
- Fora: indicador de time na HUD/card, acesso web, "confiar mesmo assim" (aceitar certificado inválido expõe o token),
  renovar o certificado do servidor (operação, fora do app).

## Atividades

| Atividade | O que faz |
|---|---|
| A01 | Este plano |
| A02 | Domain: `TeamConnectionStage`, `TeamCertificateProblem`, `TeamConnectionFailure` e a progressão das etapas; porta `HttpStatusFailure` |
| A03 | Classificador `teamConnectionFailureOf(Throwable)`, com exceção real do `CertPathValidator` no teste |
| A04 | Rótulos PT/EN: nome da etapa, frase do que parou e do que fazer |
| A05 | Primitiva de trilha (compacta e completa) + contrato em `docs/design-system/` |
| A06 | Sessões do time: erro da lista, do detalhe e das remoções, com "Tentar de novo" |
| A07 | Conectados agora: erro da carga e das ações |
| A08 | Chaves de conta: erro da carga e das ações |
| A09 | Configurações › Time: testar conexão, vínculo das contas, token admin e falha de envio |
| A10 | Bot do Telegram: `/team` diz a etapa |
| A11 | Modo terminal: `team`, aba Team e `stage` no `status --json` |
| A12 | Docs: `presentation.md`, protótipos, histórico da skill `usage-monitor-visual-options` |

## Ponto de situação

| Atividade | Comando | Resultado |
|---|---|---|
| A01 | sem harness de teste (documento) | plano revisado contra os fatos acima |
| A02 | `gradlew.bat desktopTest --tests "com.usagemonitor.domain.TeamConnectionFailureTest"` | vermelho: 4 de 4 falharam com o stub vazio (`AssertionError: expected:` em 3, `List is empty` em 1); verde: 4 testes, 0 falhas |

## Problemas em aberto e riscos

- Nome de host diferente do certificado chega como `CertificateException` genérica (sem tipo próprio): cai em
  Certificado com motivo "outro", sem frase específica.
- `SocketTimeoutException` não diz se o tempo esgotou na conexão ou na leitura: classificada como Conexão, sem ✓ em
  Endereço.
- Nenhum teste exercita o sistema de janelas (#342); as telas são cobertas por `runDesktopComposeUiTest`.
