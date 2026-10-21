# Sonda X11 de colocação (#434): uma janela por variante, cada uma pede uma posição
# na abertura e outra depois de mapeada; imprime onde o compositor a pôs.
# Rodar com DISPLAY apontando para o Xwayland do compositor medido.
import sys
import time

from Xlib import X, Xatom, display

d = display.Display()
screen = d.screen()
root = screen.root


def atom(name):
    return d.intern_atom(name)


def position(window):
    translated = window.translate_coords(root, 0, 0)
    return -translated.x, -translated.y


def net_wm_state(window):
    prop = window.get_full_property(atom('_NET_WM_STATE'), Xatom.ATOM)
    return [d.get_atom_name(value) for value in (prop.value if prop else [])]


def create(name, x, y, override=False, window_type=None, above=False):
    window = root.create_window(
        x, y, 200, 120, 0, screen.root_depth, X.InputOutput, X.CopyFromParent,
        background_pixel=0xff00ff, override_redirect=override,
        event_mask=X.StructureNotifyMask)
    window.set_wm_name(name)
    window.set_wm_normal_hints(flags=1 | 4, x=x, y=y)  # USPosition | PPosition, como o AWT
    if window_type:
        window.change_property(atom('_NET_WM_WINDOW_TYPE'), Xatom.ATOM, 32, [atom(window_type)])
    if above:
        window.change_property(atom('_NET_WM_STATE'), Xatom.ATOM, 32, [atom('_NET_WM_STATE_ABOVE')])
    window.map()
    d.sync()
    return window


VARIANTS = [
    ('normal', {}),
    ('normal+above', {'above': True}),
    ('utility', {'window_type': '_NET_WM_WINDOW_TYPE_UTILITY'}),
    ('dock', {'window_type': '_NET_WM_WINDOW_TYPE_DOCK'}),
    ('notification', {'window_type': '_NET_WM_WINDOW_TYPE_NOTIFICATION'}),
    ('override-redirect', {'override': True}),
]

windows = []
for index, (name, options) in enumerate(VARIANTS):
    requested = (20 + (index % 3) * 420, 20 + (index // 3) * 400)
    windows.append((name, requested, create(name, *requested, **options)))
time.sleep(1.5)
d.sync()

mapped = {}
for name, requested, window in windows:
    mapped[name] = position(window)
    window.configure(x=requested[0] + 30, y=requested[1] + 30)
d.sync()
time.sleep(1.5)
d.sync()

for name, requested, window in windows:
    after = position(window)
    print(f"{name:18} pedido {requested[0]},{requested[1]}  mapeada {mapped[name][0]},{mapped[name][1]}  "
          f"configure(+30) {after[0]},{after[1]}  state={net_wm_state(window)}")

# Tempo para uma captura de tela, se pedido.
time.sleep(float(sys.argv[1]) if len(sys.argv) > 1 else 0)
