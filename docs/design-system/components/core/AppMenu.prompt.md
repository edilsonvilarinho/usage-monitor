Dropdown menu anchored to a control: one choice from a short list, the current one marked.

```jsx
<AppMenu
  open={open}
  value="standard"
  options={[{ id: 'standard', label: 'Padrão' },
            { id: 'cards', label: 'Somente os cards' },
            { id: 'hud', label: 'Barra HUD' }]}
  onSelect={setMode}
  onDismiss={() => setOpen(false)}
>
  <AppIconButton glyph="▤" label="Modo de janela" onClick={() => setOpen(!open)} />
</AppMenu>
```

**Menu or segmented control?** The segmented control shows every option all the time and costs the
width of all of them; the menu shows the current one and the rest on demand. Use the segmented
control on a toolbar with room, the menu on a status bar that has none — three window-mode labels
side by side do not fit on a 30dp bar that already carries five actions.

**Not the platform's own menu.** That one brings its own surface, radius, entry animation and item
height, and none of the four belong to this system. Dressing it from the outside would leave two
menu designs in the same app, one of them invisible in the code.

**The selected item carries a mark as well as the highlight**, and the mark's column is reserved on
every row — otherwise the selected label shifts sideways whenever the choice moves. The highlight is
the same container the segmented control and the settings nav item use: there is no second selection
design here.

**It opens upwards when it does not fit below.** A popup on this platform is a layer *inside* the
window, clipped to its bounds, and the footer is the window's last row — a menu that only opened
downwards would be born outside the window. The position is pinned to the window on both axes.

**No entry animation.** The same rule as everywhere else in this system: an endless one stalls the
component tests' idle wait, and a menu is not where a one-off transition earns its cost.
