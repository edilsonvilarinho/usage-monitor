"""Gera os ícones do Usage Monitor: o Gargantua com o nome (rodada I10).

O desenho muda com o tamanho, porque o ICO e o ICNS guardam uma imagem por
tamanho e cada faixa tem o que cabe nela:

    >= 96 px   núcleo com disco e o nome escrito, USAGE / MONITOR
    32-64 px   as iniciais U·M com um Gargantua em miniatura no lugar do ponto
    <= 24 px   só o núcleo com o disco (bandeja, barra de título)

A faixa é escolhida pelo tamanho **lógico**: o `ic11` do macOS tem 32 pixels,
mas aparece como 16 pt, e por isso recebe o desenho da bandeja.

Construção determinística: a mesma entrada produz byte a byte a mesma saída. O
desenho está descrito em código, no sistema de coordenadas de 24 unidades do
protótipo (`build/gargantua-preview/icon-name-options.html`, rodada I da skill
usage-monitor-visual-options). Só Pillow: gradiente, brilho e disco são camadas
com máscara, sem numpy nem cairo.

    python tools/brand/render_icons.py

Escreve:
    src/desktopMain/resources/icons/app_icon.png          512 px, nome inteiro (menu do Linux, jpackage)
    src/desktopMain/resources/icons/app_icon_window.png    64 px, U·M (ícone das janelas)
    src/desktopMain/resources/icons/app_icon_tray.png      32 px, núcleo (bandeja)
    src/desktopMain/resources/icons/app_icon.ico           16/24/32/48/64/256, para o Windows
    src/desktopMain/resources/icons/app_icon.icns          ic07–ic14, para o macOS
    src/desktopMain/resources/icon.png                     cópia de 256 px usada pelo jpackage
    src/desktopMain/resources/icon.ico                     cópia do .ico
    docs/design-system/assets/app-icon*.png                as três faixas, para o design system

O `.icns` **não é verificável nesta máquina**: quem o valida é o job `build-macos`
do release. O formato aqui é o mínimo que o macOS aceita — cabeçalho `icns`,
tamanho total e um chunk por tamanho com payload PNG. O `.ico` segue a mesma
ideia: cada entrada é um PNG (aceito desde o Vista), porque o `save(format='ICO')`
do Pillow reduz uma imagem só e perderia a troca de desenho por tamanho.
"""
from __future__ import annotations

import io
import math
import os
import struct
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageFont

# ── Paleta Gargantua (a mesma dos tokens `--gargantua-*`) ───────────────────
GOLD = (0xE8, 0xAE, 0x63)
HOT = (0xFF, 0xF0, 0xCB)
EMBER = (0xB4, 0x50, 0x1E)
CORE = (0x03, 0x05, 0x08)
FIELD_TOP = (0x1A, 0x1E, 0x28)
FIELD_BOTTOM = (0x03, 0x05, 0x08)
PHOTON = (0xFF, 0xE6, 0xBE)

CANVAS = 24.0
CORNER_RADIUS = 5.0

# Faixas por tamanho lógico.
FULL_MIN_SIZE = 96
INITIALS_MIN_SIZE = 32

# Tela de trabalho mínima: o traço mais fino (0,4 unidade) precisa de alguns
# pixels para a redução LANCZOS não serrilhar a borda.
MIN_WORK_SIDE = 1024

# Resolução da queda radial do brilho antes de ser ampliada.
GLOW_SAMPLES = 256

ROOT = Path(__file__).resolve().parents[2]
ICONS_DIR = ROOT / 'src' / 'desktopMain' / 'resources' / 'icons'
RESOURCES_DIR = ROOT / 'src' / 'desktopMain' / 'resources'
FONTS_DIR = RESOURCES_DIR / 'fonts'
DESIGN_ASSETS_DIR = ROOT / 'docs' / 'design-system' / 'assets'

ICO_SIZES = (16, 24, 32, 48, 64, 256)
# (tipo, pixels, tamanho lógico). ic11–ic14 são @2x: o lógico é a metade.
ICNS_CHUNKS = (
    (b'ic07', 128, 128),
    (b'ic08', 256, 256),
    (b'ic09', 512, 512),
    (b'ic10', 1024, 512),
    (b'ic11', 32, 16),
    (b'ic12', 64, 32),
    (b'ic13', 256, 128),
    (b'ic14', 512, 256),
)


class Painter:
    """Tela RGBA supersampled com primitivas em unidades de 24."""

    def __init__(self, side: int):
        self.side = side
        self.u = side / CANVAS
        self.image = Image.new('RGBA', (side, side), (0, 0, 0, 0))

    # ── máscaras e tintas ──────────────────────────────────────────────────
    def mask(self) -> tuple[Image.Image, ImageDraw.ImageDraw]:
        m = Image.new('L', (self.side, self.side), 0)
        return m, ImageDraw.Draw(m)

    def solid(self, rgb, alpha: float = 1.0) -> Image.Image:
        return Image.new('RGBA', (self.side, self.side), (*rgb, round(255 * alpha)))

    def linear(self, stops, start: float, end: float, vertical: bool) -> Image.Image:
        """Gradiente de `start` a `end` (unidades), estendido nas pontas."""
        strip = Image.new('RGBA', (self.side, 1))
        values = []
        for pixel in range(self.side):
            t = ((pixel + .5) / self.u - start) / (end - start)
            values.append(_interpolate(stops, min(1.0, max(0.0, t))))
        strip.putdata(values)
        paint = strip.resize((self.side, self.side), Image.NEAREST)
        return paint.transpose(Image.TRANSPOSE) if vertical else paint

    def paint(self, paint: Image.Image, mask: Image.Image) -> None:
        layer = paint.copy()
        layer.putalpha(ImageChops.multiply(paint.getchannel('A'), mask))
        self.image.alpha_composite(layer)

    # ── formas ─────────────────────────────────────────────────────────────
    def box(self, cx, cy, r):
        u = self.u
        return ((cx - r) * u, (cy - r) * u, (cx + r) * u, (cy + r) * u)

    def glow(self, cx, cy, radius, alpha, rgb=GOLD) -> None:
        """Brilho radial linear, do centro (`alpha`) à borda (zero), como o do protótipo."""
        diameter = max(2, round(2 * radius * self.u))
        # Calculado aqui, e não pelo `Image.radial_gradient`: aquele só chega a 255
        # no canto do quadrado, e o brilho saía com borda reta.
        falloff = Image.new('L', (GLOW_SAMPLES, GLOW_SAMPLES))
        half = GLOW_SAMPLES / 2
        falloff.putdata([round(255 * alpha * max(0.0, 1 - math.hypot(x + .5 - half, y + .5 - half) / half))
                         for y in range(GLOW_SAMPLES) for x in range(GLOW_SAMPLES)])
        falloff = falloff.resize((diameter, diameter), Image.BICUBIC)
        m, _ = self.mask()
        m.paste(falloff, (round((cx - radius) * self.u), round((cy - radius) * self.u)))
        self.paint(self.solid(rgb), m)

    def field(self) -> None:
        u = self.u
        field_mask, draw = self.mask()
        draw.rounded_rectangle((0, 0, self.side - 1, self.side - 1), radius=CORNER_RADIUS * u, fill=255)
        small = Image.new('RGBA', (64, 64))
        small.putdata([_interpolate(((0, (*FIELD_TOP, 255)), (1, (*FIELD_BOTTOM, 255))), (x + y) / 126)
                       for y in range(64) for x in range(64)])
        self.paint(small.resize((self.side, self.side), Image.BICUBIC), field_mask)
        # Borda fina em ouro: descola o campo escuro da barra de tarefas escura.
        edge, draw = self.mask()
        inset = .35 * u
        draw.rounded_rectangle((inset, inset, self.side - 1 - inset, self.side - 1 - inset),
                               radius=4.7 * u, outline=255, width=max(1, round(.7 * u)))
        self.paint(self.solid(GOLD, .35), edge)

    def ring(self, cx, cy, r, width, rgb, alpha=1.0, start=0.0, end=360.0) -> None:
        m, draw = self.mask()
        w = max(1, round(width * self.u))
        outer = r + width / 2
        if end - start >= 360:
            draw.ellipse(self.box(cx, cy, outer), outline=255, width=w)
        else:
            draw.arc(self.box(cx, cy, outer), start, end, fill=255, width=w)
        self.paint(self.solid(rgb, alpha), m)

    def hole(self, cx, cy, r, rim) -> None:
        m, draw = self.mask()
        draw.ellipse(self.box(cx, cy, r), fill=255)
        self.paint(self.solid(CORE), m)
        self.ring(cx, cy, r + rim * .3, rim, HOT)

    def disk(self, cx, cy, rx, ry, width, front: bool, tilt_deg=-12.0) -> None:
        """Meia elipse do disco de acreção com Doppler (brasa à esquerda, luz à direita).

        `front` é a metade de baixo, que passa na frente do núcleo. Desenhada sem
        inclinação e girada depois, pelo mesmo centro."""
        u = self.u
        m, draw = self.mask()
        w = max(1, round(width * u))
        draw.ellipse(((cx - rx - width / 2) * u, (cy - ry - width / 2) * u,
                      (cx + rx + width / 2) * u, (cy + ry + width / 2) * u), outline=255, width=w)
        if front:
            draw.rectangle((0, 0, self.side, cy * u), fill=0)
        else:
            draw.rectangle((0, cy * u, self.side, self.side), fill=0)
        for x in (cx - rx, cx + rx):
            draw.ellipse(self.box(x, cy, width / 2), fill=255)
        paint = self.linear(((0, (*EMBER, 140)), (.5, (*GOLD, 255)), (1, (*HOT, 255))), cx - rx, cx + rx, False)
        layer = paint.copy()
        layer.putalpha(ImageChops.multiply(paint.getchannel('A'), m))
        # Canvas gira no sentido horário com ângulo positivo (y para baixo); o PIL, no anti-horário.
        self.image.alpha_composite(layer.rotate(-tilt_deg, resample=Image.BICUBIC, center=(cx * u, cy * u)))

    def stroke(self, points, width, paint: Image.Image) -> None:
        """Polilinha com pontas e junções redondas."""
        u = self.u
        m, draw = self.mask()
        w = max(1, round(width * u))
        scaled = [(x * u, y * u) for (x, y) in points]
        draw.line(scaled, fill=255, width=w)
        for (x, y) in scaled:
            draw.ellipse((x - w / 2, y - w / 2, x + w / 2, y + w / 2), fill=255)
        self.paint(paint, m)

    def clip_to_field(self) -> None:
        field_mask, draw = self.mask()
        draw.rounded_rectangle((0, 0, self.side - 1, self.side - 1), radius=CORNER_RADIUS * self.u, fill=255)
        self.image.putalpha(ImageChops.multiply(self.image.getchannel('A'), field_mask))


def _interpolate(stops, t):
    for (t0, c0), (t1, c1) in zip(stops, stops[1:]):
        if t <= t1:
            k = 0.0 if t1 == t0 else (t - t0) / (t1 - t0)
            return tuple(round(a + (b - a) * k) for a, b in zip(c0, c1))
    return stops[-1][1]


def _u_points(x0, x1, top, cy, steps=48):
    """U: hastes em x0/x1 a partir de `top`, fundo em semicírculo centrado em `cy`."""
    r = (x1 - x0) / 2
    points = [(x0, top), (x0, cy)]
    for index in range(1, steps):
        angle = math.pi - math.pi * index / steps
        points.append((x0 + r + r * math.cos(angle), cy + r * math.sin(angle)))
    points += [(x1, cy), (x1, top)]
    return points


def _hot_to_gold(p: Painter, top, bottom):
    return p.linear(((0, (*HOT, 255)), (1, (*GOLD, 255))), top, bottom, True)


def _draw_full(p: Painter) -> None:
    """>= 96 px: núcleo com disco e o nome."""
    p.field()
    p.glow(12, 8.6, 8, .25)
    p.disk(12, 9, 7.4, 1.8, 1.1, front=False)
    p.ring(12, 8.6, 4.4, .5, PHOTON, .5, start=189, end=351)
    p.hole(12, 8.6, 3.5, .55)
    p.disk(12, 9, 7.4, 1.8, 1.1, front=True)
    _text(p, 'USAGE', 'IBMPlexMono-SemiBold.ttf', 3.15, 17.2, _hot_to_gold(p, 15, 21))
    _text(p, 'MONITOR', 'IBMPlexMono-Regular.ttf', 2.35, 20.4, p.solid(GOLD))
    p.clip_to_field()


def _draw_initials(p: Painter) -> None:
    """32–64 px: U·M, com o Gargantua em miniatura no lugar do ponto."""
    p.field()
    paint = _hot_to_gold(p, 6, 18)
    p.stroke(_u_points(3.5, 9.3, 6.5, 14.3), 2.1, paint)
    p.stroke(((14.7, 17.2), (14.7, 6.5), (17.6, 12), (20.5, 6.5), (20.5, 17.2)), 2.1, paint)
    p.glow(12, 12.2, 3, .35)
    p.disk(12, 12.4, 3.2, .75, .6, front=False)
    p.hole(12, 12.2, 1.4, .4)
    p.disk(12, 12.4, 3.2, .75, .6, front=True)
    p.clip_to_field()


def _draw_core(p: Painter) -> None:
    """<= 24 px: só o núcleo com o disco, que é o que se lê na bandeja."""
    p.field()
    p.glow(12, 12, 11, .25)
    p.disk(12, 12.6, 10, 2.6, 1.9, front=False)
    p.hole(12, 12, 5, .9)
    p.disk(12, 12.6, 10, 2.6, 1.9, front=True)
    p.clip_to_field()


def _text(p: Painter, value, font_file, size_units, baseline_y, paint) -> None:
    font = ImageFont.truetype(str(FONTS_DIR / font_file), size=max(1, round(size_units * p.u)))
    m, draw = p.mask()
    draw.text((12 * p.u, baseline_y * p.u), value, fill=255, font=font, anchor='ms')
    p.paint(paint, m)


def variant_for(logical_size: int):
    if logical_size >= FULL_MIN_SIZE:
        return _draw_full
    if logical_size >= INITIALS_MIN_SIZE:
        return _draw_initials
    return _draw_core


def render(size: int, logical_size: int | None = None) -> Image.Image:
    side = max(MIN_WORK_SIDE, size * 4)
    painter = Painter(side)
    variant_for(logical_size or size)(painter)
    return painter.image.resize((size, size), Image.LANCZOS)


def _png_bytes(image: Image.Image) -> bytes:
    buffer = io.BytesIO()
    image.save(buffer, format='PNG', optimize=True)
    return buffer.getvalue()


def write_png(path: Path, size: int, logical_size: int | None = None) -> None:
    path.write_bytes(_png_bytes(render(size, logical_size)))
    print('escrito', path.relative_to(ROOT), f'{size}px')


def write_ico(path: Path) -> None:
    payloads = [_png_bytes(render(size)) for size in ICO_SIZES]
    header = struct.pack('<HHH', 0, 1, len(ICO_SIZES))
    offset = len(header) + 16 * len(ICO_SIZES)
    entries = b''
    for size, data in zip(ICO_SIZES, payloads):
        side = 0 if size >= 256 else size
        entries += struct.pack('<BBBBHHII', side, side, 0, 0, 1, 32, len(data), offset)
        offset += len(data)
    path.write_bytes(header + entries + b''.join(payloads))
    print('escrito', path.relative_to(ROOT), 'ICO', ICO_SIZES)


def write_icns(path: Path) -> None:
    payloads = []
    for chunk_type, size, logical in ICNS_CHUNKS:
        data = _png_bytes(render(size, logical))
        payloads.append(chunk_type + struct.pack('>I', len(data) + 8) + data)

    body = b''.join(payloads)
    path.write_bytes(b'icns' + struct.pack('>I', len(body) + 8) + body)
    print('escrito', path.relative_to(ROOT), 'ICNS', len(ICNS_CHUNKS), 'chunks')


def main() -> None:
    os.makedirs(ICONS_DIR, exist_ok=True)
    write_png(ICONS_DIR / 'app_icon.png', 512)
    write_png(ICONS_DIR / 'app_icon_window.png', 64)
    write_png(ICONS_DIR / 'app_icon_tray.png', 32, logical_size=16)
    write_ico(ICONS_DIR / 'app_icon.ico')
    write_icns(ICONS_DIR / 'app_icon.icns')
    write_png(RESOURCES_DIR / 'icon.png', 256)
    (RESOURCES_DIR / 'icon.ico').write_bytes((ICONS_DIR / 'app_icon.ico').read_bytes())
    print('escrito', (RESOURCES_DIR / 'icon.ico').relative_to(ROOT), 'cópia do ICO')
    # Design system: as três faixas em 2x, para as páginas de marca.
    write_png(DESIGN_ASSETS_DIR / 'app-icon.png', 256)
    write_png(DESIGN_ASSETS_DIR / 'app-icon-initials.png', 128, logical_size=64)
    write_png(DESIGN_ASSETS_DIR / 'app-icon-tray.png', 48, logical_size=24)


if __name__ == '__main__':
    main()
