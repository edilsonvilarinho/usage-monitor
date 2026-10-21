# Sonda X11 de arrasto (#434): janela gerenciada e sem decoração, como a HUD, que no
# clique entrega o arrasto ao compositor por _NET_WM_MOVERESIZE (direção 8 = MOVE).
#
# Modo (1º argumento):
#   same   -- o pedido sai da conexão que recebeu o clique
#   other  -- o pedido sai de outra conexão, como faria uma biblioteca ao lado do AWT
# Acrescentar "-ungrab" solta a captura implícita do ponteiro antes do pedido.
import sys
import time

from Xlib import X, display
from Xlib.protocol import event

MODE = sys.argv[1] if len(sys.argv) > 1 else 'other'
SECONDS = float(sys.argv[2]) if len(sys.argv) > 2 else 20

d = display.Display()
other = display.Display()
screen = d.screen()
root = screen.root


def atom(name):
    return d.intern_atom(name)


window = root.create_window(
    100, 100, 200, 120, 0, screen.root_depth, X.InputOutput, X.CopyFromParent,
    background_pixel=0x00aa00, event_mask=X.ButtonPressMask | X.ButtonReleaseMask)
window.set_wm_name('drag-probe-' + MODE)
# Sem decoração, como a HUD (flags=MWM_HINTS_DECORATIONS, decorations=0).
window.change_property(atom('_MOTIF_WM_HINTS'), atom('_MOTIF_WM_HINTS'), 32, [2, 0, 0, 0, 0])
window.map()
d.sync()


def position():
    translated = window.translate_coords(root, 0, 0)
    return -translated.x, -translated.y


deadline = time.time() + SECONDS
while time.time() < deadline:
    while d.pending_events():
        received = d.next_event()
        if received.type == X.ButtonPress:
            if MODE.endswith('-ungrab'):
                d.ungrab_pointer(X.CurrentTime)
                d.flush()
            sender = other if MODE.startswith('other') else d
            message = event.ClientMessage(
                window=window, client_type=atom('_NET_WM_MOVERESIZE'),
                data=(32, [received.root_x, received.root_y, 8, received.detail, 1]))
            sender.screen().root.send_event(
                message, event_mask=X.SubstructureRedirectMask | X.SubstructureNotifyMask)
            sender.flush()
            print(f"press em {received.root_x},{received.root_y} posição {position()}", flush=True)
        elif received.type == X.ButtonRelease:
            # Medida para o app: o cliente recebe o release depois do arrasto do compositor?
            print(f"release em {received.root_x},{received.root_y} posição {position()}", flush=True)
    time.sleep(0.02)
print(f"final {position()}", flush=True)
