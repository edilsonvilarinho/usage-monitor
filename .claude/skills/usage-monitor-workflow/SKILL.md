---
name: usage-monitor-workflow
description: Run the usage-monitor delivery workflow end to end — issue with a short plan, plan in docs/planos, worktree, strict TDD (red before green, recorded), local tests, PR with auto-merge, CI incident count, cleanup and a test-case comment on the issue. Use for EVERY request that changes the repository, even when the user does not mention the workflow.
---

# Usage Monitor Workflow

The "Fluxo de trabalho" section of `CLAUDE.md` is the rule; this skill is how to execute it. It applies to every request
that changes a versioned file. A question that only asks for an explanation is answered in chat, outside the flow.
No step waits for approval: the `CLAUDE.md` rule is the authorization for commit, push, PR and merge.

## 1. Issue (always)

Use the issue the user gave. Without one, create it — the first line is mandatory (`CLAUDE.md`, "Convenções"):

```bash
gh issue create --title "<what>" --body-file <scratchpad>/issue.md
```

Body: `🤖 Escrito por Claude Code, a pedido de @edilsonvilarinho`, then the plan in **at most 15 lines**: **O quê**,
**Atividades** (A01, A02… one line each), **Onde**, **Validação**, **Fora** (what is deliberately left out).

In chat, the plan for the user is at most one paragraph. Every chat report stays within 15 lines.

## 2. Plan in `docs/planos/` (activity A01)

`docs/planos/<subject>-<issue>-execucao.md`, modeled on `fluxo-automatizado-496-execucao.md`: provenance (model,
tool, date, branch, commit identity), **Objetivo**, **Fatos confirmados** (each with the command that measured it),
**Decisões**, **Atividades**, **Ponto de situação** (one row per activity, written in that activity's commit, with the
command and its result — never the intention), **Problemas em aberto e riscos**. A01 commits the plan alone.

## 3. Worktree and branch

```bash
git fetch origin
git worktree add .claude/worktrees/<slug> -b <type>/issue-<n>-<slug> origin/main
```

Never commit on `main` (the ruleset rejects the push anyway). Commit identity, passed per command and never written
to the config — the same thing the `usage-monitor-commit-push` script does since #498:

```bash
git -c user.name=claude -c user.email=claude@anthropic.com commit -F <message>
```

Conventional Commits in English, trailers from the conversation's attribution reminder. One activity, one commit:
test, code, docs and the plan row together.

## 4. TDD — every activity that changes behavior

1. **Red.** Write the test for the behavior first: the contract (route, body, published state, text, decision), as
   the neighboring tests do. Run the narrowest command and confirm it fails **on the assertion, for the expected
   reason**. A compile error is not red: add the minimal stub so the test compiles and fails on what it asserts.
2. **Green.** Write the least production code that makes it pass. Run the same command.
3. **Refactor** with the test green; run it again.
4. **Record** in the activity's plan row: the red command and the failure line (`expected …, actual …`), then the
   green command and its count. A row without the red run is an activity that did not follow TDD.

A bug fix starts with a test that reproduces the bug (red), never with the fix. A test that only executes code without
asserting behavior does not count (`cobertura-95-execucao.md`, "Decisões").

Without a test harness (Actions YAML, `docs/`, design system, skill text), the row says so and names what validated
it (parse, a real run, real data). That is the only exception.

## 5. Local tests before the PR

Run the slice of what changed, mirroring the `changes` job of `ci.yml`:

| Changed | Command |
|---|---|
| `src/**`, Gradle files | `gradlew.bat allTests` (narrow with `desktopTest --tests …` while iterating) |
| `server/**` | `cd server && npm test` (`npm run test:coverage` for coverage) |
| `tools/ci/**` | `node --test "tools/ci/test/*.test.mjs"` |
| any test change | report of the `usage-monitor-test-report` skill |

Diff coverage before opening the PR (advisory in CI, floor 90%):

```bash
gradlew.bat allTests koverXmlReport -Pcoverage
node tools/ci/diff-coverage.mjs --base origin/main --kover build/reports/kover/report.xml
node tools/ci/diff-coverage.mjs --base origin/main --lcov server/coverage/lcov.info
```

Below 90%: write the missing tests (TDD) or justify each uncovered line in the plan row.

## 6. PR with auto-merge

```bash
git push -u origin <branch>
gh pr create --title "<conventional title> (#<n>)" --body-file <scratchpad>/pr.md
gh pr merge --auto --merge <pr>
```

Body: `Closes #<n>`, what changed, **platforms tested** (`CLAUDE.md`, "CI e testes"; window host changes say the
#342 risk), test plan with the commands run, footer from the attribution reminder. The `main` ruleset requires
`ci-ok`; GitHub merges by itself when it passes and deletes the remote branch. Never `--admin`.

## 7. CI and the incident stop

Watch the `CI` run of the head commit, not `gh pr checks --watch`: right after a push the CI run sits queued behind
the `concurrency` cancel with no check registered yet, and `gh pr checks --watch` returns as soon as the checks it
sees (the skipped `Dependabot auto-merge` job) are done — measured on PR #497.

```bash
gh run list --branch <branch> --workflow ci.yml --limit 1 --json databaseId,headSha
gh run watch <run-id> --exit-status
gh run view <run-id> --log-failed        # on red
node tools/ci/ci-incidents.mjs            # count after every red run
node tools/ci/ci-incidents.mjs --local "<what failed>"   # local test failure after the PR is open
```

Fix the red with TDD (reproduce, then fix) and push. Exit code 3 from `ci-incidents.mjs` is the 4th incident: run
`gh pr merge --disable-auto <pr>`, stop touching the branch, and hand back to the user the diagnosis of each failure,
what was tried and the branch state.

## 8. After the merge

```bash
gh pr view <pr> --json state,mergedAt,mergeCommit
cd <repo root>
git -c core.longpaths=true worktree remove .claude/worktrees/<slug>
git branch -D <branch>
git fetch --prune
```

`core.longpaths` is mandatory on Windows: `server/node_modules` (after `npm ci`) passes 260 characters, and without it
`git worktree remove` exits 255 with `Filename too long`, drops the registration and leaves the folder half deleted
(#498, measured). It goes per command, never into the user's config.

Then comment on the issue with the test cases created — **at most 20 lines**, first line
`🤖 Escrito por Claude Code, a pedido de @edilsonvilarinho`:

```bash
gh issue comment <n> --body-file <scratchpad>/tests.md
```

Content: one line per test file or group — what behavior each case asserts (not the method name alone), the red →
green evidence of the key cases, totals (tests added, suite count), diff coverage result, and what stayed without a
test and why. Close the issue if `Closes #<n>` did not.

Finally tell the user, within 15 lines: PR, merge commit, CI run, incidents, and the issue comment link.
