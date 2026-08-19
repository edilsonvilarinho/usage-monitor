"""Gera os ícones do Usage Monitor a partir do monograma.

Construção determinística: a mesma entrada produz byte a byte a mesma saída, e é
por isso que o SVG ao lado é referência e não fonte — nada aqui lê o SVG, o
desenho está descrito em código, no mesmo sistema de coordenadas de 24 unidades.

    python tools/brand/render_icons.py

Escreve:
    src/desktopMain/resources/icons/app_icon.png    512 px, para a janela e a bandeja
    src/desktopMain/resources/icons/app_icon.ico    16/32/48/256, para o Windows
    src/desktopMain/resources/icons/app_icon.icns   ic07–ic14, para o macOS
    src/desktopMain/resources/icon.png              cópia de 256 px usada pelo jpackage
    src/desktopMain/resources/icon.ico              cópia do .ico

O `.icns` **não é verificável nesta máquina**: quem o valida é o job `build-macos`
do release. O formato aqui é o mínimo que o macOS aceita — cabeçalho `icns`,
tamanho total e um chunk por tamanho com payload PNG.
"""
from __future__ import annotations

import math
import os
import struct
from pathlib import Path

from PIL import Image, ImageDraw

# ── Geometria, em unidades de 24 ────────────────────────────────────────────
CANVAS = 24.0
CORNER_RADIUS = 5.0
STROKE = 2.1
ARC_RADIUS = 2.6

TOP = 6.5
BOTTOM = 16.3
LEFT = 7.0
RIGHT = 17.0
CENTER_BOTTOM = 13.1

FIELD = (0xF2, 0xED, 0xED, 0xFF)
INK = (0x13, 0x10, 0x10, 0xFF)

# Supersampling: o traço tem 2,1 unidades e em 16px vira 1,4 pixel. Sem
# reamostrar, a borda do arco fica serrilhada justamente no tamanho da bandeja.
SUPERSAMPLE = 8

ROOT = Path(__file__).resolve().parents[2]
ICONS_DIR = ROOT / 'src' / 'desktopMain' / 'resources' / 'icons'
RESOURCES_DIR = ROOT / 'src' / 'desktopMain' / 'resources'

ICO_SIZES = (16, 32, 48, 256)
# ic07–ic14: 128, 256, 512, 1024 em @1x e @2x. São os tipos que aceitam PNG
# direto; os antigos (is32, il32) exigem RLE e máscara separada.
ICNS_CHUNKS = (
    (b'ic07', 128),
    (b'ic08', 256),
    (b'ic09', 512),
    (b'ic10', 1024),
    (b'ic11', 32),
    (b'ic12', 64),
    (b'ic13', 256),
    (b'ic14', 512),
)


def _arc_points(center_x, center_y, radius, start_deg, end_deg, steps=48):
    """Pontos de um arco. Os arcos do bojo são desenhados como polilinha porque
    `ImageDraw.arc` não aceita `joint`, e a emenda entre arco e reta apareceria."""
    points = []
    for index in range(steps + 1):
        angle = math.radians(start_deg + (end_deg - start_deg) * index / steps)
        points.append((center_x + radius * math.cos(angle), center_y + radius * math.sin(angle)))
    return points


def _monogram_paths():
    """As duas polilinhas do monograma, em unidades de 24."""
    outer = [(LEFT, TOP), (LEFT, BOTTOM - ARC_RADIUS)]
    outer += _arc_points(LEFT + ARC_RADIUS, BOTTOM - ARC_RADIUS, ARC_RADIUS, 180, 90)
    outer += [(RIGHT - ARC_RADIUS, BOTTOM)]
    outer += _arc_points(RIGHT - ARC_RADIUS, BOTTOM - ARC_RADIUS, ARC_RADIUS, 90, 0)
    outer += [(RIGHT, TOP)]

    stem = [(CANVAS / 2, TOP), (CANVAS / 2, CENTER_BOTTOM)]
    return outer, stem


def render(size: int) -> Image.Image:
    scale = size * SUPERSAMPLE / CANVAS
    canvas = Image.new('RGBA', (size * SUPERSAMPLE, size * SUPERSAMPLE), (0, 0, 0, 0))
    draw = ImageDraw.Draw(canvas)

    draw.rounded_rectangle(
        (0, 0, size * SUPERSAMPLE - 1, size * SUPERSAMPLE - 1),
        radius=CORNER_RADIUS * scale,
        fill=FIELD,
    )

    width = max(1, round(STROKE * scale))
    outer, stem = _monogram_paths()
    for path in (outer, stem):
        scaled = [(x * scale, y * scale) for (x, y) in path]
        draw.line(scaled, fill=INK, width=width)
        # Um disco em **cada** vértice, não só nas pontas: é o que dá a ponta
        # redonda do `stroke-linecap="round"` do protótipo e, de quebra, fecha as
        # junções. O `joint='curve'` do Pillow desenha um polígono na emenda e
        # deixava farpas visíveis na curva do bojo.
        for (x, y) in scaled:
            draw.ellipse((x - width / 2, y - width / 2, x + width / 2, y + width / 2), fill=INK)

    return canvas.resize((size, size), Image.LANCZOS)


def write_png(path: Path, size: int) -> None:
    render(size).save(path, format='PNG', optimize=True)
    print('escrito', path.relative_to(ROOT), f'{size}px')


def write_ico(path: Path) -> None:
    render(256).save(path, format='ICO', sizes=[(s, s) for s in ICO_SIZES])
    print('escrito', path.relative_to(ROOT), 'ICO', ICO_SIZES)


def write_icns(path: Path) -> None:
    import io

    payloads = []
    for chunk_type, size in ICNS_CHUNKS:
        buffer = io.BytesIO()
        render(size).save(buffer, format='PNG', optimize=True)
        data = buffer.getvalue()
        payloads.append(chunk_type + struct.pack('>I', len(data) + 8) + data)

    body = b''.join(payloads)
    path.write_bytes(b'icns' + struct.pack('>I', len(body) + 8) + body)
    print('escrito', path.relative_to(ROOT), 'ICNS', len(ICNS_CHUNKS), 'chunks')


def main() -> None:
    os.makedirs(ICONS_DIR, exist_ok=True)
    write_png(ICONS_DIR / 'app_icon.png', 512)
    write_ico(ICONS_DIR / 'app_icon.ico')
    write_icns(ICONS_DIR / 'app_icon.icns')
    write_png(RESOURCES_DIR / 'icon.png', 256)
    write_ico(RESOURCES_DIR / 'icon.ico')


if __name__ == '__main__':
    main()
