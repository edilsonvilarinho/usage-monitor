# UI kit — Usage Monitor desktop

Click-through recreation of the app's real views. Synthetic data only, in the spirit of the
repo's `ScreenshotFixtures`: no account, machine, project path or key is real.

Open `index.html`. The left rail switches screens; the top strip switches theme.

| Screen | File | Interaction |
| --- | --- | --- |
| Dashboard | `Dashboard.jsx` | minimize/expand any card; card status-bar icons open History, CLI Sessions, Team, Presence; footer opens Settings |
| Modo somente cards | `CardsOnly.jsx` | hover the top 34px to reveal the window frame |
| Barra HUD | `Hud.jsx` | static — third chrome, 24dp strip anchored to the top edge (issue #164) |
| Histórico | `History.jsx` | range segments; current vs. previous period |
| Sessões CLI | `CliSessions.jsx` | tabs Sessões/Resumo/Tendência; window segments; live text filter; row opens the detail |
| Detalhe de sessão | `SessionDetail.jsx` | collapse/expand the Avançado block |
| Uso do time | `TeamUsage.jsx` | expand a member into their sessions (sibling rows + 2dp guide) |
| Conectados agora | `Presence.jsx` | connected vs. working-now states |
| Configurações | `Settings.jsx` | lateral nav, one section mounted at a time; sliders for opacity and UI scale |

Screens compose the published primitives only — no screen re-implements a button, a panel or a
progress track. Each file declares what it needs at the top:

```js
const { AppWindowFrame, AppPanel, AppDataRow } = DS;
export function Dashboard({ onOpen }) { … }
```
