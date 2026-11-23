# Vídeo de lançamento v42.3.0 — roteiro (#633)

**Fonte:** `v42.2.0..v42.3.0` (`collect-changes.mjs v42.3.0`); cenas das issues #576/#600/#602/#612 (buraco negro),
#619/#623 (HUD flutuante), #584 (reordenar), #558/#560/#571 (Codex), #556/#610 (análise); "E mais": #566 (Ultra), #567
(trabalho extra pausado), #562 (janelas paradas liberam memória), #586/#590/#592 (estrelas). #570 (Gemini 4 Argon) ficou
de fora: o commit só adiciona teste.

**Parâmetros medidos (2026-11-23):** 118.5 s nos dois idiomas; 1080×1920, 60 fps, h264 + aac
(`ffprobe`); maior ritmo EN 14.8 caracteres/s (`minimize-1`), PT 14.9 (`minimize-3`);
render dos dois idiomas em 1 211 s de parede. Voz `elevenlabs/eleven-v4`/`george` pelo OpenRouter. Telas: demos reais
gravadas por `generateVideoMedia` (HUD com fundo transparente, `surface: "hud"`).

| Cena | Tempo | Tela | EN | PT |
|---|---|---|---|---|
| title | 0.0–9.1 s | title | Usage Monitor 42.3. Your AI accounts, now in orbit. | Usage Monitor 42.3. Suas contas de IA, agora em órbita. |
| minimize | 9.1–39.7 s | `video-hud-minimize` | 0 s: Too many accounts on the bar? Open one and press minimize.<br>6 s: It falls into the black hole, out of your way.<br>11 s: Hover the hole. You will see every minimized account.<br>20 s: Pick one to check it, then press plus to bring it back. | 0 s: Contas demais na barra? Abra uma e toque em minimizar.<br>6 s: Ela cai no buraco negro e sai do caminho.<br>11 s: Passe o mouse no buraco para ver as contas minimizadas.<br>20 s: Escolha uma para conferir e toque em mais para trazer de volta. |
| floating | 39.7–54.3 s | `video-hud-floating` | 0 s: In floating mode, the HUD rests as a black hole, anywhere on screen.<br>5.5 s: Point at it. Your accounts open in a column, right beside it. | 0 s: No modo flutuante, a HUD descansa como um buraco negro, em qualquer lugar da tela.<br>5.5 s: Aponte para ele e as contas se abrem numa coluna ao lado. |
| reorder | 54.3–68.1 s | `video-hud-reorder` | 0 s: Want your main account first?<br>4.5 s: Hold its ring and drag it into place. The new order is saved. | 0 s: Quer a conta principal primeiro?<br>4.5 s: Segure o anel e arraste até o lugar. A nova ordem fica salva. |
| codex | 68.1–80.6 s | `video-codex-sessions` | 0 s: Codex sessions now open like Claude's, with tokens and models.<br>5 s: One click copies the resume command. Done. | 0 s: As sessões do Codex agora abrem como as do Claude, com tokens e modelos.<br>5 s: Um clique copia o comando para retomar a sessão. |
| analysis | 80.6–96.6 s | `video-analysis-all` | 0 s: In analysis, pick All to see your whole history.<br>8.5 s: Then compare your average pace with the last hour. | 0 s: Na análise, escolha Tudo para ver todo o histórico.<br>8.5 s: Depois compare o ritmo médio com o da última hora. |
| more | 96.6–108.5 s | list | Also new: the Ultra plan label, paused extra usage explained, lighter idle windows, and a starfield behind the rings. | E mais: rótulo do plano Ultra, trabalho extra pausado explicado, janelas mais leves e estrelas atrás dos anéis. |
| closing | 108.5–116.7 s | closing | Usage Monitor 42.3. Free and open source, on GitHub. | Usage Monitor 42.3. Grátis e de código aberto, no GitHub. |
