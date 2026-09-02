The dot of `AppStatusIndicator`, on its own. One legal use: the collapsed HUD pill.

```jsx
<AppStatusDot level="ok" title="Normal" />
```

Extracted from `AppStatusIndicator`, which now consumes it — two anatomies for the same 6dp dot
would drift apart. `off` draws it hollow instead of filled: with no color to tell them apart, the
outline is what separates "disconnected" from "connected" in a greyscale capture.

**This does not loosen "color never informs alone."** The HUD pill collapses to the dot while every
source is on track — data that says everything is fine does not need to occupy screen until it stops
being true — and there the word is one mouse movement away: hovering brings the whole pill back.
Anywhere else, ship `AppStatusIndicator`.
