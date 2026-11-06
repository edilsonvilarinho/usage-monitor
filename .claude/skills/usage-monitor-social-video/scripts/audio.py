#!/usr/bin/env python3
"""Trilha e efeitos do video social (#542, tarefa B06).

Uso: python scripts/audio.py --work <dir> --variant <v>
Rodar com o python do venv (toolPaths().python de usage-monitor-release-video/scripts/setup-tools.mjs).

Le <dir>/<v>.js (node, vm) e grava, em <dir>/:
- <v>-music.wav: faixa "Pulso de sonda" (base audio/music.py), harmonia muda nos limites de beat;
  o clímax é o beat com climax: true, sem nenhum, o penúltimo; a abertura é o primeiro beat e
  a resolução o último; camadas por beat (nenhum arranjo repetido por mais de 40 s), fade-in 2 s,
  fade-out 3 s, loudnorm I=-20 TP=-2 em duas passadas (medido e aplicado).
- <v>-sfx.wav: efeitos sonoros nos cues (base audio/sfx.py).
Ambos: 48 kHz estereo, PCM 24 bits, exatamente duration * 48000 amostras.
A voz (<v>-voice.wav) não entra aqui: ela só é lida no mix de scripts/render.mjs.
"""

import argparse
import json
import re
import subprocess
import sys
from pathlib import Path

try:
    import numpy as np
    from scipy.signal import butter, oaconvolve, sosfilt
except ImportError as err:
    raise SystemExit(f"faltam numpy/scipy ({err}): rode com o python do venv "
                     "(toolPaths().python de setup-tools.mjs)")

RATE = 48000
SEED = 540
SFX_SEED = 547
FADE_IN = 2.0
FADE_OUT = 3.0
LOUDNORM = "I=-20:TP=-2:LRA=11"
MUSIC_PEAK = 0.89
RAMP = 1.5          # segundos de transicao de cada camada entre beats
TAIL = 1.0          # sobreposicao de acordes (cauda do beat anterior)
SPREAD = (-0.5, 0.25, 0.55, -0.25)
ARR_MAX_RUN = 40.0  # nenhum arranjo igual por mais de 40 s
FFMPEG = "ffmpeg"
FFPROBE = "ffprobe"
PCM_IN = ["-f", "f32le", "-ar", str(RATE), "-ac", "2", "-i", "pipe:0"]

STOPS = {
    "principal": {1: 1.0, 2: 0.5, 3: 0.22, 4: 0.2, 5: 0.1, 6: 0.05},
    "oitava": {2: 1.0, 4: 0.45, 6: 0.2},
    "pedal": {1: 1.0, 2: 0.35, 3: 0.12},
    "arpejo": {1: 1.0, 2: 0.4, 3: 0.22, 4: 0.12},
}

# Harmonia da sonda: la frigio. Oito acordes em ciclo; clímax e resolução são forçados pelo papel do beat.
PROG = [
    [45, 52, 57, 60],        # Am
    [46, 53, 58, 62],        # Bb (bemol: tensao)
    [45, 52, 57, 60],        # Am
    [43, 50, 55, 58],        # G
    [53, 56, 59, 65],        # F
    [52, 56, 59, 62, 66],    # E com setima e nona (dominante)
    [45, 52, 57, 60, 64],    # Am add9
    [46, 52, 58, 64],        # Bb sobre o baixo
]
CLIMAX = [40, 47, 56, 62, 66]     # E maj7(9), clímax
RESOLVE = [33, 45, 52, 57, 60, 64]  # Am add9 com baixo em A1, resolução

LAYERS = ("pulse", "arp", "pedal", "pads", "brilho")
# Niveis por camada (pulse, arp, pedal, pads, brilho) para cada arranjo.
ARR = {
    "open": (0.0, 0.0, 1.0, 0.45, 0.0),
    "pulse": (0.85, 0.0, 1.0, 0.5, 0.0),
    "arp": (0.7, 0.9, 0.8, 0.6, 0.0),
    "air": (0.0, 0.7, 0.4, 1.0, 0.35),
    "drive": (1.0, 1.0, 0.9, 0.7, 0.0),
    "break": (0.45, 0.0, 0.0, 0.85, 0.25),
    "peak": (1.0, 1.0, 1.0, 0.9, 1.0),
}
CYCLE = ["pulse", "arp", "air", "drive", "break", "arp", "drive", "air"]


class Timeline:
    def __init__(self, duration):
        self.duration = duration
        self.n = int(round(duration * RATE))
        self.t = np.arange(self.n) / RATE


# ---------- utilitarios de sintese ----------
def hz(midi):
    return 440.0 * 2.0 ** ((midi - 69) / 12.0)


def smooth(x):
    x = np.clip(x, 0.0, 1.0)
    return x * x * (3.0 - 2.0 * x)


def envelope(n, attack, release):
    env = np.ones(n)
    a = min(n, max(1, int(attack * RATE)))
    env[:a] = smooth(np.arange(a) / a)
    r = min(n, int(release * RATE))
    if r > 0:
        env[n - r:] *= smooth(np.linspace(1.0, 0.0, r))
    return env


def pan(mono, position):
    angle = (position + 1.0) * np.pi / 4.0
    return np.stack([mono * np.cos(angle), mono * np.sin(angle)], axis=1)


def center(mono):
    return pan(mono, 0.0)


def place(stereo, mono, start, position):
    n = stereo.shape[0]
    begin = int(round(start * RATE))
    end = min(n, begin + len(mono))
    if begin >= n or end <= begin:
        return
    stereo[begin:end] += pan(mono[: end - begin], position)


def rms_norm(x, target):
    level = float(np.sqrt(np.mean(x ** 2)))
    return x * (target / level) if level > 0 else x


def section_at(bounds, t):
    idx = int(np.searchsorted(bounds, t, side="right")) - 1
    return min(max(idx, 0), len(bounds) - 2)


def ramp_curve(tl, bounds, levels):
    xs, ys = [0.0], [levels[0]]
    for i in range(1, len(levels)):
        xs += [bounds[i], bounds[i] + RAMP]
        ys += [levels[i - 1], levels[i]]
    xs.append(tl.duration + 1.0)
    ys.append(levels[-1])
    return np.interp(tl.t, xs, ys)


def organ(rng, freq, seconds, stop, attack=0.8, release=1.2, detune=(-6.0, 0.0, 5.0)):
    """Orgao de tubos aditivo: fileiras levemente desafinadas, ataque lento, vibrato leve."""
    n = int(round(seconds * RATE))
    t = np.arange(n) / RATE
    wobble = 0.012 * np.sin(2 * np.pi * 4.7 * t)
    total = np.zeros(n)
    for cents in detune:
        base = freq * 2.0 ** (cents / 1200.0)
        for ratio, amp in stop.items():
            total += amp * np.sin(2 * np.pi * base * ratio * t + wobble + rng.uniform(0, 2 * np.pi))
    total /= len(detune) * sum(stop.values())
    return total * envelope(n, attack, release)


def chord_layer(rng, chords, bounds, stop, shift=0, attack=0.8, release=1.2,
                detune=(-6.0, 0.0, 5.0), target=None):
    n = int(round(bounds[-1] * RATE))
    out = np.zeros((n, 2))
    for i, chord in enumerate(chords):
        start = bounds[i]
        seconds = bounds[i + 1] - start + TAIL
        for j, midi in enumerate(chord):
            mono = organ(rng, hz(midi + shift), seconds, stop, attack, release, detune)
            place(out, mono, start, SPREAD[j % len(SPREAD)])
    return rms_norm(out, target) if target is not None else out


def hall_ir(rng, seconds=7.0):
    """Reverb de catedral: cauda de 7 s, escurecida pelo ar, pre-atraso de 35 ms."""
    n = int(seconds * RATE)
    t = np.arange(n) / RATE
    decay = np.exp(-6.9 * t / seconds)
    raw = rng.standard_normal((n, 2)) * decay[:, None]
    raw = sosfilt(butter(2, 4500, btype="low", fs=RATE, output="sos"), raw, axis=0)
    ir = np.vstack([np.zeros((int(0.035 * RATE), 2)), raw])
    return ir / np.sqrt(np.sum(ir ** 2, axis=0))


def finish(tl, dry, ir, mix):
    wet = np.stack([oaconvolve(dry[:, c], ir[:, c])[: tl.n] for c in range(2)], axis=1)
    out = dry * (1.0 - mix) + wet * mix
    out *= (smooth(tl.t / FADE_IN) * smooth((tl.duration - tl.t) / FADE_OUT))[:, None]
    return out / np.max(np.abs(out)) * MUSIC_PEAK


# ---------- sonda: pulso grave em 66 bpm, arpejo de orgao, pedal e pads ----------
def pulse_track(tl):
    out = np.zeros(tl.n)
    period = 60.0 / 66.0
    tt = np.arange(int(0.5 * RATE)) / RATE
    freq = 48.0 + 14.0 * np.exp(-tt / 0.04)
    phase = 2 * np.pi * np.cumsum(freq) / RATE
    thump = np.sin(phase) * np.exp(-tt / 0.11) * np.minimum(1.0, tt / 0.004)
    k = 0
    while k * period < tl.duration:
        begin = int(round(k * period * RATE))
        end = min(tl.n, begin + len(thump))
        out[begin:end] += thump[: end - begin]
        k += 1
    return out


def arpeggio(tl, rng, bounds, chords):
    out = np.zeros((tl.n, 2))
    step = 60.0 / 66.0 / 2.0  # colcheias em 66 bpm
    k = 0
    t = 0.0
    while t < tl.duration - 0.4:
        pattern = sorted(chords[section_at(bounds, t)])
        midi = pattern[k % len(pattern)] + 12 * ((k // len(pattern)) % 2)
        mono = organ(rng, hz(midi), 0.9, STOPS["arpejo"], attack=0.02, release=0.45,
                     detune=(-3.0, 0.0, 3.0))
        place(out, mono, t, 0.5 if k % 2 == 0 else -0.5)
        k += 1
        t += step
    return rms_norm(out, 0.06)


def pcs(chord):
    return frozenset(m % 12 for m in chord)


def beat_roles(beats):
    """Papel de cada beat pela posição: abertura (primeiro), resolução (último), clímax, meio.
    Clímax: o beat com climax true; sem nenhum, o penúltimo."""
    if len(beats) < 3:
        raise SystemExit("roteiro com menos de 3 beats")
    last = len(beats) - 1
    flagged = [i for i, b in enumerate(beats) if b.get("climax") is True]
    if len(flagged) > 1:
        raise SystemExit(f"mais de um beat com climax: true (posições {flagged})")
    peak = flagged[0] if flagged else last - 1
    if not 0 < peak < last:
        raise SystemExit(f"clímax na posição {peak}: precisa ser um beat do meio")
    roles = []
    for i in range(len(beats)):
        if i == 0:
            roles.append("opening")
        elif i == last:
            roles.append("resolution")
        elif i == peak:
            roles.append("peak")
        else:
            roles.append("middle")
    return roles


def chords_for(roles):
    """Um acorde por beat, muda em cada limite: clímax no pico, resolução no último, progressão no resto."""
    out, cursor = [], 0
    for role in roles:
        if role == "peak":
            out.append(CLIMAX)
        elif role == "resolution":
            out.append(RESOLVE)
        else:
            out.append(PROG[cursor % len(PROG)])
            cursor += 1
    for i in range(1, len(out)):
        if pcs(out[i]) == pcs(out[i - 1]):
            raise SystemExit(f"harmonia nao muda entre os beats {i - 1} e {i}")
    return out


def arrangement(roles):
    """Nome do arranjo por beat: abertura e resolução abertas, pico com clímax, demais em ciclo."""
    names, cursor, prev = [], 0, None
    for i, role in enumerate(roles):
        if role in ("opening", "resolution"):
            name = "open"
        elif role == "peak":
            name = "peak"
        else:
            name = CYCLE[cursor % len(CYCLE)]
            cursor += 1
        if name == prev:
            raise SystemExit(f"arranjo repetido no beat {i}")
        names.append(name)
        prev = name
    return names


def max_arrangement_run(names, bounds):
    best, run_start = 0.0, bounds[0]
    for i, name in enumerate(names):
        if i == 0 or name != names[i - 1]:
            run_start = bounds[i]
        best = max(best, bounds[i + 1] - run_start)
    return best


def build_music(tl, bounds, chords, names):
    if max_arrangement_run(names, bounds) > ARR_MAX_RUN:
        raise SystemExit("arranjo igual por mais de 40 s")
    builders = {
        "pulse": lambda: center(rms_norm(pulse_track(tl), 0.12)),
        "arp": lambda: arpeggio(tl, np.random.default_rng(SEED + 2), bounds, chords),
        "pedal": lambda: chord_layer(np.random.default_rng(SEED + 3),
                                     [[min(ch) - 12] for ch in chords], bounds, STOPS["pedal"],
                                     attack=0.6, release=1.0, target=0.07),
        "pads": lambda: chord_layer(np.random.default_rng(SEED + 4), chords, bounds, STOPS["principal"],
                                    attack=1.2, release=1.5, target=0.035),
        "brilho": lambda: chord_layer(np.random.default_rng(SEED + 5),
                                      [[m + 12 for m in ch] for ch in chords], bounds, STOPS["oitava"],
                                      attack=1.0, release=1.5, target=0.03),
    }
    total = np.zeros((tl.n, 2))
    for li, layer in enumerate(LAYERS):
        levels = [ARR[name][li] for name in names]
        curve = ramp_curve(tl, bounds, levels)
        total += builders[layer]() * curve[:, None]
    ir = hall_ir(np.random.default_rng(SEED + 100))
    return finish(tl, total, ir, mix=0.35)


# ---------- efeitos (base audio/sfx.py) ----------
def time_axis(seconds):
    return np.arange(int(round(seconds * RATE))) / RATE


def bandpass(x, low, high):
    return sosfilt(butter(2, [low, high], btype="band", fs=RATE, output="sos"), x)


def lowpass(x, cutoff):
    return sosfilt(butter(2, cutoff, btype="low", fs=RATE, output="sos"), x)


def peak_norm(x, gain):
    level = float(np.max(np.abs(x)))
    return x * (gain / level) if level > 0 else x


def fade_end(x, seconds, release):
    t = time_axis(seconds)
    return x * np.clip((seconds - t) / release, 0.0, 1.0)


def fx_rise(rng):
    seconds = 2.5
    t = time_axis(seconds)
    freq = 180.0 + (760.0 - 180.0) * (t / seconds) ** 2.2
    tone = np.sin(2 * np.pi * np.cumsum(freq) / RATE)
    air = peak_norm(bandpass(rng.standard_normal(len(t)), 300, 4000), 1.0)
    env = (t / seconds) ** 2 * np.clip((seconds - t) / 0.02, 0.0, 1.0)
    return peak_norm((0.55 * tone + 0.6 * air) * env, 0.5), seconds - 0.02, 0.0


def fx_whoosh(rng):
    seconds = 1.1
    t = time_axis(seconds)
    air = bandpass(rng.standard_normal(len(t)), 500, 2800)
    env = np.sin(np.pi * t / seconds) ** 2
    pan_pos = -0.9 + 1.8 * t / seconds
    return peak_norm(air * env, 0.35), seconds / 2, pan_pos


def fx_tick(_rng):
    seconds = 0.04
    t = time_axis(seconds)
    x = np.sin(2 * np.pi * 1480 * t) + 0.3 * np.sin(2 * np.pi * 2960 * t)
    env = np.minimum(1.0, t / 0.002) * np.exp(-t / 0.008)
    return peak_norm(fade_end(x * env, seconds, 0.01), 0.25), 0.002, 0.0


def fx_red(_rng):
    seconds = 0.5
    t = time_axis(seconds)
    x = np.sin(2 * np.pi * 110 * t) + np.sin(2 * np.pi * 116.5 * t) + 0.5 * np.sin(2 * np.pi * 165 * t)
    env = np.minimum(1.0, t / 0.015) * np.exp(-t / 0.16)
    return peak_norm(fade_end(x * env, seconds, 0.03), 0.3), 0.015, 0.0


def fx_green(_rng):
    seconds = 1.2
    t = time_axis(seconds)
    partials = ((1.0, 1.0, 1.1), (2.0, 0.45, 0.8), (2.76, 0.3, 0.5), (5.4, 0.14, 0.25), (8.93, 0.06, 0.12))
    x = sum(a * np.exp(-t / tau) * np.sin(2 * np.pi * 880 * ratio * t) for ratio, a, tau in partials)
    env = np.minimum(1.0, t / 0.004)
    return peak_norm(fade_end(x * env, seconds, 0.2), 0.32), 0.004, 0.0


def fx_impact(rng):
    seconds = 4.0
    t = time_axis(seconds)
    freq = 40.0 + 35.0 * np.exp(-t / 0.12)
    sub = np.sin(2 * np.pi * np.cumsum(freq) / RATE) * np.exp(-t / 0.9)
    tail = lowpass(rng.standard_normal(len(t)), 500) * np.exp(-t / 1.1)
    env = np.minimum(1.0, t / 0.004)
    return peak_norm(fade_end(env * (0.9 * sub + 0.25 * tail), seconds, 0.5), 0.7), 0.005, 0.0


EFFECTS = {"rise": fx_rise, "whoosh": fx_whoosh, "tick": fx_tick, "red": fx_red,
           "green": fx_green, "impact": fx_impact}


def render_sfx(tl, cues):
    rng = np.random.default_rng(SFX_SEED)
    out = np.zeros((tl.n, 2))
    for cue in cues:
        name = cue["sound"]
        if name not in EFFECTS:
            raise SystemExit(f"efeito desconhecido no cue t={cue['t']}: {name}")
        mono, peak_offset, pos = EFFECTS[name](rng)
        start = max(float(cue["t"]) - peak_offset, 0.0)
        begin = int(round(start * RATE))
        end = min(tl.n, begin + len(mono))
        if end <= begin:
            continue
        seg = mono[: end - begin]
        p = np.broadcast_to(pos[: end - begin] if np.ndim(pos) else pos, seg.shape)
        angle = (p + 1.0) * np.pi / 4.0
        out[begin:end, 0] += seg * np.cos(angle)
        out[begin:end, 1] += seg * np.sin(angle)
    peak = float(np.max(np.abs(out)))
    if peak >= 1.0:
        raise SystemExit(f"pico do sfx acima de 1.0: {peak:.3f}")
    return out


# ---------- ffmpeg / ffprobe ----------
def run_ff(args, data=None):
    proc = subprocess.run(args, input=data, capture_output=True)
    err = proc.stderr.decode("utf-8", "replace")
    if proc.returncode != 0:
        raise SystemExit("ffmpeg falhou:\n" + err[-1500:])
    return err


def pcm(stereo):
    return np.ascontiguousarray(stereo, dtype="<f4").tobytes()


def loudnorm_json(err):
    return json.loads(re.findall(r"\{[^{}]*\}", err)[-1])


def loudnorm_measure(stereo):
    args = [FFMPEG, "-hide_banner", "-nostats", *PCM_IN,
            "-af", f"loudnorm={LOUDNORM}:print_format=json", "-f", "null", "-"]
    return loudnorm_json(run_ff(args, pcm(stereo)))


def loudnorm_af(m):
    return (f"loudnorm={LOUDNORM}:measured_I={m['input_i']}:measured_TP={m['input_tp']}"
            f":measured_LRA={m['input_lra']}:measured_thresh={m['input_thresh']}"
            f":offset={m['target_offset']}:linear=true")


def loudnorm_file(path):
    args = [FFMPEG, "-hide_banner", "-nostats", "-i", str(path),
            "-af", f"loudnorm={LOUDNORM}:print_format=json", "-f", "null", "-"]
    return loudnorm_json(run_ff(args))


def write_wav(stereo, path, af=None):
    args = [FFMPEG, "-hide_banner", "-nostats", "-y", *PCM_IN]
    if af:
        args += ["-af", af]
    args += ["-c:a", "pcm_s24le", "-ar", str(RATE), "-ac", "2", str(path)]
    run_ff(args, pcm(stereo))


def probe(path):
    proc = subprocess.run([FFPROBE, "-v", "error", "-show_entries",
                           "format=duration:stream=codec_name,sample_rate,channels",
                           "-of", "json", str(path)], capture_output=True, text=True, check=True)
    data = json.loads(proc.stdout)
    stream = data["streams"][0]
    return {"duration_s": float(data["format"]["duration"]), "codec": stream["codec_name"],
            "sample_rate": int(stream["sample_rate"]), "channels": int(stream["channels"])}


# ---------- dados da variante ----------
def load_script(work, variant):
    code = ("const vm=require('vm'),fs=require('fs');const c={window:{}};"
            "vm.runInNewContext(fs.readFileSync(process.argv[1],'utf8'),c);"
            "console.log(JSON.stringify(c.window.WF_SCRIPT))")
    proc = subprocess.run(["node", "-e", code, str(work / f"{variant}.js")],
                          capture_output=True, text=True, check=True)
    script = json.loads(proc.stdout)
    if script["variant"] != variant:
        raise SystemExit(f"{variant}.js declara variant={script['variant']}")
    return script


def run_variant(work, variant):
    script = load_script(work, variant)
    tl = Timeline(float(script["duration"]))
    beats = script["beats"]
    for i, (a, b) in enumerate(zip(beats, beats[1:]), start=1):
        if abs(a["end"] - b["start"]) > 1e-9:
            raise SystemExit(f"{variant}: beats nao contiguos entre as posicoes {i - 1} e {i}")
    bounds = [float(b["start"]) for b in beats] + [tl.duration]
    roles = beat_roles(beats)
    chords = chords_for(roles)
    names = arrangement(roles)
    run = max_arrangement_run(names, bounds)

    music = build_music(tl, bounds, chords, names)
    before = loudnorm_measure(music)
    music_path = work / f"{variant}-music.wav"
    write_wav(music, music_path, af=loudnorm_af(before))
    del music

    sfx = render_sfx(tl, script["cues"])
    sfx_path = work / f"{variant}-sfx.wav"
    write_wav(sfx, sfx_path)

    m_info = probe(music_path)
    s_info = probe(sfx_path)
    m_final = loudnorm_file(music_path)
    expected_samples = tl.n
    for label, info in (("music", m_info), ("sfx", s_info)):
        if abs(info["duration_s"] - tl.duration) > 0.0005:
            raise SystemExit(f"{variant}-{label}: duracao {info['duration_s']} != {tl.duration}")
        if info["sample_rate"] != RATE or info["channels"] != 2:
            raise SystemExit(f"{variant}-{label}: formato {info}")
    print(f"{variant}: duration={tl.duration} expected_samples={expected_samples} "
          f"music_ffprobe={m_info['duration_s']:.6f}s sfx_ffprobe={s_info['duration_s']:.6f}s "
          f"music_I={m_final['input_i']} music_TP={m_final['input_tp']} "
          f"music_I_pass1={before['input_i']} max_arrangement_run={run:.2f}s "
          f"cues={len(script['cues'])} beats={len(beats)}")


def parse_args(argv):
    parser = argparse.ArgumentParser(description="Trilha e efeitos do video social (#542).")
    parser.add_argument("--work", required=True, type=Path,
                        help="pasta de trabalho: lê <v>.js, grava <v>-music.wav e <v>-sfx.wav")
    parser.add_argument("--variant", required=True, help="variante, ex.: short-pt")
    return parser.parse_args(argv)


def main(argv):
    args = parse_args(argv)
    work = args.work.resolve()
    if not work.is_dir():
        raise SystemExit(f"--work não é uma pasta: {work}")
    if not re.fullmatch(r"[a-z0-9][a-z0-9-]*", args.variant):
        raise SystemExit(f"--variant inválido: {args.variant}")
    if not (work / f"{args.variant}.js").is_file():
        raise SystemExit(f"roteiro ausente: {work / (args.variant + '.js')}")
    run_variant(work, args.variant)


if __name__ == "__main__":
    main(sys.argv[1:])
