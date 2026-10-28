"""Trilha espacial e efeitos sonoros do tour em vídeo (#505), sintetizados aqui: nenhuma amostra de terceiros.

Uso: python soundtrack.py <duração-s> <eventos.json> <saída-dir>
Escreve <saída-dir>/music.wav (a trilha na duração exata) e <saída-dir>/effects.wav (cada efeito no instante do
seu evento, [{"name", "at", "duration"?}] calculado por `soundEvents` no timeline.mjs). Semente fixa: dois renders
do mesmo roteiro dão o mesmo som.

A trilha é a do protótipo aprovado pelo dono: drone em lá (55 Hz) e mi (82 Hz), pad Am9 → Fmaj7 → Cmaj7 → G6 com o
filtro varrendo devagar, sinos esparsos e reverb longo. "Não pode ficar muito rápido": acordes de 10 s e sinos em
semínimas a 72 bpm, metade da densidade do protótipo.
"""

import json
import sys
from pathlib import Path

import numpy as np
from scipy.signal import butter, oaconvolve, sosfilt

RATE = 48000
SEED = 505
CHORDS = [[57, 64, 67, 71, 72], [53, 60, 64, 69, 76], [48, 55, 64, 67, 71], [55, 62, 64, 71, 74]]
CHORD_SECONDS = 10.0
BELL_BPM = 72
BELL_SKIP = 0.55


def t_axis(seconds):
    return (np.arange(int(seconds * RATE)) / RATE).astype(np.float32)


def midi(note):
    return 440.0 * 2 ** ((note - 69) / 12)


def pan(mono, position):
    angle = (position + 1) * np.pi / 4
    return np.stack([mono * np.cos(angle), mono * np.sin(angle)], axis=1).astype(np.float32)


def reverb(stereo, rng, seconds, mix):
    n = int(seconds * RATE)
    decay = np.exp(-np.linspace(0, 7, n)).astype(np.float32)
    impulse = np.stack([rng.standard_normal(n).astype(np.float32) * decay for _ in range(2)], axis=1)
    impulse /= np.sqrt((impulse ** 2).sum(axis=0))
    wet = np.stack([oaconvolve(stereo[:, c], impulse[:, c])[: len(stereo)] for c in range(2)], axis=1)
    return (stereo * (1 - mix) + wet * mix).astype(np.float32)


def lowpass(signal, cutoff):
    return sosfilt(butter(4, cutoff, fs=RATE, output="sos"), signal, axis=0).astype(np.float32)


def add(target, piece, at):
    begin = int(at * RATE)
    if begin >= len(target):
        return
    piece = piece[: len(target) - begin]
    target[begin:begin + len(piece)] += piece


def pad_voice(freq, t, rng):
    out = np.zeros_like(t)
    for detune in (-0.006, 0.0, 0.0065):
        f = freq * (1 + detune)
        phase = rng.uniform(0, 2 * np.pi)
        for k in range(1, 7):
            out += np.sin(2 * np.pi * f * k * t + phase * k).astype(np.float32) / (k ** 1.6)
    return out


def bell(freq, seconds=2.4):
    t = t_axis(seconds)
    modulator = np.sin(2 * np.pi * freq * 3.5 * t) * 2.2 * np.exp(-t * 3)
    return (np.sin(2 * np.pi * freq * t + modulator) * np.exp(-t * 2.2)).astype(np.float32)


def music(seconds, rng):
    t = t_axis(seconds)
    mix = np.zeros((len(t), 2), dtype=np.float32)
    breathe = 0.75 + 0.25 * np.sin(2 * np.pi * t / 9)
    mix += pan((np.sin(2 * np.pi * 55 * t) + 0.6 * np.sin(2 * np.pi * 82.41 * t)) * 0.16 * breathe, 0)
    for index in range(int(np.ceil(seconds / CHORD_SECONDS)) + 1):
        start = index * CHORD_SECONDS - 1.5
        length = CHORD_SECONDS + 3.0
        local = t_axis(length)
        envelope = np.minimum(1, local / 2.5) * np.minimum(1, (length - local) / 2.5)
        chord = CHORDS[index % len(CHORDS)]
        for position, note in zip(np.linspace(-0.7, 0.7, len(chord)), chord):
            voice = pan(pad_voice(midi(note), local, rng) * envelope * 0.035, position)
            skip = int(max(-start, 0) * RATE)
            add(mix, voice[skip:], max(start, 0))
    weight = (0.5 + 0.5 * np.sin(2 * np.pi * t / 23))[:, None]
    mix = lowpass(mix, 550) * (1 - weight) + lowpass(mix, 1700) * weight
    step = 60 / BELL_BPM
    for n in range(int(seconds / step)):
        if n * step < 6 or rng.random() < BELL_SKIP:
            continue
        chord = CHORDS[int(n * step / CHORD_SECONDS) % len(CHORDS)]
        add(mix, pan(bell(midi(int(rng.choice(chord)) + 12)) * 0.07, rng.uniform(-0.8, 0.8)), n * step)
    mix = reverb(mix, rng, 4.5, 0.5)
    fade = (np.minimum(1, t / 4) * np.minimum(1, (seconds - t) / 5))[:, None]
    return mix * fade


def whoosh(rng, seconds=1.3):
    t = t_axis(seconds)
    noise = rng.standard_normal(len(t)).astype(np.float32)
    out = np.zeros_like(t)
    hop = RATE // 50
    for block in range(0, len(t), hop):
        center = 250 * (16 ** (block / len(t)))
        sos = butter(2, [center * 0.7, center * 1.4], btype="band", fs=RATE, output="sos")
        out[block:block + hop] = sosfilt(sos, noise[block:block + hop])
    mono = out * np.sin(np.pi * np.clip(t / seconds, 0, 1)) ** 2
    angle = (np.linspace(-0.8, 0.8, len(t)) + 1) * np.pi / 4
    return reverb(np.stack([mono * np.cos(angle), mono * np.sin(angle)], axis=1).astype(np.float32), rng, 2.0, 0.35)


def lens(rng, seconds=2.2):
    t = t_axis(seconds)
    freq = 38 + 102 * np.exp(-t * 2.4)
    sub = np.sin(2 * np.pi * np.cumsum(freq) / RATE) * np.exp(-t * 1.6) * np.minimum(1, t / 0.02)
    air = sosfilt(butter(2, 1800, fs=RATE, output="sos"), rng.standard_normal(len(t))) * np.exp(-t * 4) * 0.25
    return reverb(pan((sub + air).astype(np.float32), 0), rng, 3.0, 0.4)


def chime(rng):
    return reverb(pan(bell(midi(88), 1.6) * 0.6 + bell(midi(95), 1.6) * 0.3, 0.2), rng, 2.5, 0.45)


def keys(rng, seconds):
    count = max(1, int(seconds * 30))
    out = np.zeros(int((seconds + 0.3) * RATE), dtype=np.float32)
    size = int(0.012 * RATE)
    click = sosfilt(butter(2, [1500, 6000], btype="band", fs=RATE, output="sos"), rng.standard_normal(size))
    click = (click * np.exp(-np.linspace(0, 6, size))).astype(np.float32)
    for n in range(count):
        begin = int((n / 30 + rng.uniform(0, 0.008)) * RATE)
        out[begin:begin + size] += click * rng.uniform(0.5, 1.0)
    return pan(out, -0.1)


def riser(rng, seconds=3.0):
    t = t_axis(seconds)
    tone = np.sin(2 * np.pi * np.cumsum(110 * (8 ** (t / seconds))) / RATE) * 0.35
    noise = sosfilt(butter(2, 3000, btype="high", fs=RATE, output="sos"), rng.standard_normal(len(t))) * 0.25
    return reverb(pan(((tone + noise) * (t / seconds) ** 2).astype(np.float32), 0), rng, 2.5, 0.4)


def peak(signal, db):
    top = float(np.max(np.abs(signal)))
    return signal if top == 0 else (signal / top * 10 ** (db / 20)).astype(np.float32)


# Nível de cada efeito sobre a trilha: a lente marca o capítulo; deslize, sino e teclas ficam atrás da fala.
EFFECT_GAIN_DB = {"riser": -8, "lens": -6, "whoosh": -14, "chime": -16, "keys": -18}


def effects(seconds, events, rng):
    track = np.zeros((int(seconds * RATE), 2), dtype=np.float32)
    cache = {
        "riser": riser(rng), "lens": lens(rng), "whoosh": whoosh(rng), "chime": chime(rng),
    }
    for event in events:
        name = event["name"]
        sound = keys(rng, event["duration"]) if name == "keys" else cache[name]
        add(track, peak(sound, EFFECT_GAIN_DB[name]), event["at"])
    return track


def write_wav(path, stereo):
    import wave

    data = (np.clip(stereo, -1, 1) * 32767).astype("<i2")
    with wave.open(str(path), "wb") as wav_file:
        wav_file.setnchannels(2)
        wav_file.setsampwidth(2)
        wav_file.setframerate(RATE)
        wav_file.writeframes(data.tobytes())


def main():
    seconds = float(sys.argv[1])
    events = json.loads(Path(sys.argv[2]).read_text(encoding="utf-8"))
    output = Path(sys.argv[3])
    output.mkdir(parents=True, exist_ok=True)
    write_wav(output / "music.wav", peak(music(seconds, np.random.default_rng(SEED)), -3))
    write_wav(output / "effects.wav", effects(seconds, events, np.random.default_rng(SEED + 1)))
    print(output / "music.wav")


if __name__ == "__main__":
    main()
