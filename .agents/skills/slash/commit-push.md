# /commit-push — Commit And Push Workflow

Cria um commit e faz push da branch atual, seguindo as regras deste repositório.

> Fluxo mecânico — sem Plan Mode, sem subagentes.

## Usage

```bash
/commit-push "fix: update linux packaging"
/commit-push "feat: add history filters"
```

Se a mensagem não for informada, pedir antes de prosseguir.

---

## Step 0 — Permission gate

Só executar se o utilizador tiver pedido explicitamente para commitar e/ou dar push nesta conversa.

Se o pedido não for explícito, parar e informar.

## Step 1 — Inspect git state

```bash
git status --short
git branch --show-current
git remote -v
```

Identificar:

- branch atual
- ficheiros modificados
- se há mudanças staged e unstaged

Se houver mudanças não relacionadas com a tarefa atual, não incluí-las no commit.

## Step 2 — Review the exact diff to ship

```bash
git diff --stat
git diff -- .github/workflows/release-linux.yml
git diff --cached --stat
```

Ler o diff real dos ficheiros que entrarão no commit.

Se nada estiver staged:

```bash
git add <ficheiros-da-tarefa>
```

Nunca usar `git add .` quando houver risco de apanhar mudanças não relacionadas.

## Step 3 — Run the narrowest relevant verification

Escolher o menor comando que valide a mudança:

- workflow/docs only: sem build obrigatória; validar diff e coerência
- Kotlin/domain/presentation/data: `gradlew.bat allTests` ou um filtro mais estreito quando suficiente
- installer/packaging Windows: `gradlew.bat packageInstaller` se a mudança exigir validação do instalador

Se a verificação falhar, parar antes do commit.

## Step 4 — Configure git identity for commit

Antes de commitar:

```bash
git config user.name "codex"
git config user.email "codex@openai.com"
```

## Step 5 — Commit

Confirmar o conteúdo staged:

```bash
git diff --cached --stat
git diff --cached
```

Criar o commit:

```bash
git commit -m "<mensagem>"
```

## Step 6 — Restore repository git identity

Após o commit, restaurar:

```bash
git config user.name "edilsonvilarinho"
git config user.email "edilson.vilarinho.messias@gmail.com"
```

Mesmo se o push falhar, a identidade deve ser restaurada antes de terminar.

## Step 7 — Push current branch

```bash
git push origin <branch-atual>
```

Se a branch não tiver upstream:

```bash
git push -u origin <branch-atual>
```

## Step 8 — Report back

Partilhar:

- branch usada
- hash curto do commit criado
- resumo curto do que entrou
- resultado do push

Se algo impedir o push, informar o bloqueio exato em vez de tentar contornar silenciosamente.
