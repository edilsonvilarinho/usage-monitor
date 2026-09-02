# Changelog

Notable changes to Usage Monitor, in the format of [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

**The complete per-version history is on the
[releases page](https://github.com/edilsonvilarinho/usage-monitor/releases)**, where every tag since
v1 carries its own notes, generated from the commits it shipped. This file is not a transcript of
that — it starts here, and from now on each release gets a human summary of what actually changed for
someone using the app.

## [Unreleased]

### Added

- A window-mode menu in the footer: one icon opens the three frames -- standard, cards only and the
  HUD strip -- with the current one marked. Until now the two reduced frames were reachable only
  through two switches buried in the Settings "System" section, two keyboard shortcuts or the tray,
  which is why they were mostly found by accident.

- The HUD strip now ends with a countdown to the next automatic collection. It was already in the
  footer, but the footer is not drawn in HUD mode, so the only way to know how long was left was to
  leave the mode. It is drawn once, on the first row — the polling is a single loop for the whole
  app, not one per account.

- In-app help window (`F1`, the footer icon, or the tray menu): twelve topics covering what each
  feature does, how to turn it on, and an animated demo of it. The demos are recorded offscreen from
  the app's own components and ship inside the installer, so the window works with no network.

- MIT license, and the repository metadata that goes with an open project: description, topics,
  contribution guide, security policy, and issue and pull request templates.
- English `README.md`, with a Portuguese mirror in
  [`README.pt-BR.md`](README.pt-BR.md).
- Reference documentation split out of the README:
  [`docs/integrations.md`](docs/integrations.md),
  [`docs/architecture.md`](docs/architecture.md) and
  [`docs/build-and-release.md`](docs/build-and-release.md).

---

For versions up to and including **38.0.2**, see the
[releases page](https://github.com/edilsonvilarinho/usage-monitor/releases).
