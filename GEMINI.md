# GEMINI.md

Este ficheiro orienta o comportamento do Gemini (Google DeepMind) neste repositório.

## ⚠️ Fonte de Verdade Principal

Para evitar duplicação e desatualização de regras entre diferentes LLMs (Gemini, Claude, Codex), a **fonte de verdade absoluta** para arquitetura, comandos de build, regras de negócio e restrições de código é o ficheiro:

👉 **[AGENTS.md](./AGENTS.md)**

Sempre consulte o `AGENTS.md` e o `README.md` antes de tomar decisões arquiteturais, propor soluções ou rodar comandos. O `AGENTS.md` foi consolidado para que não seja necessário replicar regras para cada agente específico.

## 🧠 Entendimento Rápido do Projeto

Para contexto imediato, eis o modelo de funcionamento da aplicação:

- **Propósito:** App Desktop Kotlin Multiplatform (KMP) + Compose Desktop para monitorizar consumo e quotas de APIs de IA (Anthropic, Codex, MiniMax) num único dashboard.
- **Arquitetura:** Clean Architecture (`Presentation -> Domain <- Data`). A camada `Domain` é Kotlin puro, sem dependências de Ktor, Compose ou outras infraestruturas.
- **Source Sets:** 
  - `commonMain` (código de domínio, dados e apresentação partilhado)
  - `desktopMain` (bootstrapping JVM, injeção de dependências manual, acesso ao SQLite local, `AutoStartManager`)
  - `installer` (scripts para NSIS)
- **Comandos Principais:** (via PowerShell) `gradlew.bat run`, `gradlew.bat desktopJar`, `gradlew.bat allTests`, `gradlew.bat packageInstaller`.
- **Regras Estritas a Seguir:**
  - A chave da MiniMax é injetada via ambiente (`MINIMAX_API_KEY`). **NUNCA** fazer hardcode de credenciais.
  - **Git:** O Gemini tem regras específicas de commit (ver abaixo). O Gemini **NUNCA** realizará `git commit/push` sem pedido expresso.
  - Idiomas: Código (classes, variáveis, métodos) em Inglês, comentários em Português.
  - Componentes UI (Compose): Devem ser mantidos estritamente **stateless** na medida do possível, com fluxo unidirecional explícito.

Para aprofundar detalhes técnicos, de lifecycle ou troubleshooting do instalador NSIS, consulte o `AGENTS.md`.

## Commit convention

Antes de commitar:
```bash
git config user.name "gemini"
git config user.email "gemini@google.com"
```

Depois de commitar, restaurar:
```bash
git config user.name "edilsonvilarinho"
git config user.email "edilson.vilarinho.messias@gmail.com"
```
