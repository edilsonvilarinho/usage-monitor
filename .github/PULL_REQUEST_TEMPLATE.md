# What this changes

<!-- One or two sentences. What behaviour is different after this merges? -->

Closes #

## Why

<!-- The problem this solves. If it fixes a bug, what was the actual cause? -->

## How it was verified

<!-- The command you ran and its result, not the intention. For example:
     gradlew.bat allTests -> BUILD SUCCESSFUL, 131 classes / 1408 tests / 0 failures -->

- [ ] `gradlew.bat allTests` passes locally

**Platforms tested** (OS, version, desktop/window manager — e.g. `Windows 11 24H2`,
`elementary OS 6.1 / Gala (X11)`):

<!-- CI only runs the suite on Windows. See "Platform reality" in CONTRIBUTING.md. -->

## Checklist

- [ ] Conventional Commits subject, in English
- [ ] One activity per commit — code, tests and docs for the same decision go together
- [ ] No secret, key or token added, and no API key read from an environment variable
- [ ] Bug fix: a test that fails on `main` and passes here
- [ ] If a screen changed: screenshots regenerated (`gradlew.bat generateScreenshots`) and the design
      system / prototype updated in this same commit; before/after screenshots below for every OS
      and theme touched
- [ ] If a window host changed (`HudWindow.kt`, `DesktopWindowFrame.kt`, `Main.kt`, `*WindowHost`):
      opened on Linux (X11) before merge, or the risk is stated in the notes below
- [ ] If user-visible behaviour changed: `README.md` and `README.pt-BR.md` updated
- [ ] If a new value was added to an existing enum: the reason is written down in the code

## Notes for the reviewer

<!-- Anything you decided against, a trade-off you took, or something you want a second opinion on. -->
