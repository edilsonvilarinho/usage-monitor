#!/bin/sh
# COSMIC aninhado para reproduzir no Bazzite o que só o relator via (#434).
#
# O cosmic-comp roda numa janela do desktop atual, dentro de um distrobox Fedora 44,
# sem mexer na imagem do sistema. Ele sobe o próprio Xwayland; o app e as sondas
# rodam contra esse DISPLAY. Um monitor só: cobre colocação e arrasto, não a troca
# de monitor.
#
#   sh tools/cosmic/cosmic-nested.sh setup            cria o container e instala o compositor
#   sh tools/cosmic/cosmic-nested.sh start            sobe o cosmic-comp (fica em primeiro plano)
#   sh tools/cosmic/cosmic-nested.sh app [args…]      abre o app instalado dentro do COSMIC
#   sh tools/cosmic/cosmic-nested.sh placement        sonda de colocação por tipo de janela
#   sh tools/cosmic/cosmic-nested.sh drag [modo]      sonda de arrasto por _NET_WM_MOVERESIZE
#   sh tools/cosmic/cosmic-nested.sh window-drag NOME arrasta a janela NOME pelo ponto (100,20)
#
# O clique sintético só chega pelo X do host: o xdotool no DISPLAY do COSMIC não
# entrega ponteiro. Por isso o arrasto é dirigido no host, sobre a janela do compositor.
set -eu

BOX=cosmic-debug
HERE=$(cd "$(dirname "$0")" && pwd)
HOST_DISPLAY=${HOST_DISPLAY:-:0}

cosmic_display() {
    # O Xwayland do cosmic-comp é o único iniciado com -enable-ei-portal sem -auth.
    pgrep -a Xwayland | awk '!/-auth/ { print $3; exit }'
}

cosmic_window_origin() {
    window=$(DISPLAY=$HOST_DISPLAY xdotool search --name '^COSMIC$' | head -n 1)
    DISPLAY=$HOST_DISPLAY xwininfo -id "$window" |
        awk '/Absolute upper-left X/ { x = $NF } /Absolute upper-left Y/ { y = $NF } END { print x, y }'
}

window_position() {
    DISPLAY=$1 xwininfo -id "$2" |
        awk '/Absolute upper-left X/ { x = $NF } /Absolute upper-left Y/ { y = $NF } END { print x "," y }'
}

# Pressiona em (x, y) do COSMIC, move 20 x (-12, +10) e solta.
drag_from() {
    set -- $(cosmic_window_origin) "$1" "$2"
    DISPLAY=$HOST_DISPLAY xdotool mousemove $(($1 + $3)) $(($2 + $4))
    sleep 0.3
    DISPLAY=$HOST_DISPLAY xdotool mousedown 1
    sleep 0.2
    for _ in $(seq 1 20); do
        DISPLAY=$HOST_DISPLAY xdotool mousemove_relative -- -12 10
        sleep 0.04
    done
    sleep 0.3
    DISPLAY=$HOST_DISPLAY xdotool mouseup 1
    sleep 0.5
}

case "${1:-}" in
    setup)
        distrobox create -n "$BOX" -i registry.fedoraproject.org/fedora:44 --nvidia -Y
        distrobox enter "$BOX" -- sudo dnf install -y cosmic-comp xorg-x11-server-Xwayland \
            xdotool xprop xwininfo xrandr wmctrl python3-xlib \
            libXtst libXi libXrender libXext fontconfig dejavu-sans-fonts mesa-libEGL
        ;;
    start)
        exec distrobox enter "$BOX" -- env RUST_LOG=info cosmic-comp
        ;;
    app)
        shift
        DISPLAY=$(cosmic_display) XDG_CURRENT_DESKTOP=COSMIC XDG_SESSION_DESKTOP=COSMIC \
            exec "$HOME/.local/bin/usage-monitor" "$@"
        ;;
    placement)
        distrobox enter "$BOX" -- env DISPLAY="$(cosmic_display)" python3 -I "$HERE/x11_placement_probe.py"
        ;;
    drag)
        mode=${2:-other}
        display=$(cosmic_display)
        distrobox enter "$BOX" -- env DISPLAY="$display" python3 -I "$HERE/x11_drag_probe.py" "$mode" 12 &
        sleep 4
        window=$(DISPLAY=$display xdotool search --name "^drag-probe-$mode\$" | head -n 1)
        before=$(window_position "$display" "$window")
        drag_from $((${before%,*} + 100)) $((${before#*,} + 60))
        echo "drag $mode: antes $before depois $(window_position "$display" "$window")"
        wait
        ;;
    window-drag)
        display=$(cosmic_display)
        window=$(DISPLAY=$display xdotool search --name "$2" | head -n 1)
        before=$(window_position "$display" "$window")
        drag_from $((${before%,*} + 100)) $((${before#*,} + 20))
        echo "window-drag '$2': antes $before depois $(window_position "$display" "$window")"
        ;;
    *)
        sed -n '2,18p' "$0"
        exit 2
        ;;
esac
