"""Sintetiza a fala de cada cena nos dois idiomas com o Piper e mede a duração de cada arquivo.

Uso: python synthesize.py <scenes.json> <voices-dir> <saida-dir>
Escreve <saida-dir>/<idioma>/<cena>.wav e <saida-dir>/durations.json ({"en": {"cena": segundos}, "pt": {...}}).
Roda no venv do cache de ferramentas (setup-tools.mjs), nunca no Python do sistema.
"""

import json
import sys
import wave
from pathlib import Path

from piper import PiperVoice, SynthesisConfig

# Vozes ouvidas e aprovadas no vídeo da 41.11 (docs/planos/video-v41-11/roteiro.md, U4).
VOICES = {"en": "en_US-ryan-high.onnx", "pt": "pt_BR-faber-medium.onnx"}
# 1,12 na 41.11; o pedido seguinte foi "não ficar muito rápido". A faber fala mais depressa que a ryan:
# a 1,2 ela chegou a 19,9 caracteres/s, contra 15,8 da ryan na mesma cena (v42.0.0, cena "team").
LENGTH_SCALE = {"en": 1.4, "pt": 1.5}
# Silêncio entre frases, como na 41.11: frase curta o Piper lê corrida, e a pausa é o que dá fôlego.
SENTENCE_SILENCE = 0.35


def write_speech(voice: PiperVoice, config: SynthesisConfig, text: str, path: Path) -> None:
    chunks = list(voice.synthesize(text, syn_config=config))
    first = chunks[0]
    silence = bytes(int(first.sample_rate * SENTENCE_SILENCE) * first.sample_width * first.sample_channels)
    with wave.open(str(path), "wb") as wav_file:
        wav_file.setframerate(first.sample_rate)
        wav_file.setsampwidth(first.sample_width)
        wav_file.setnchannels(first.sample_channels)
        for index, chunk in enumerate(chunks):
            if index > 0:
                wav_file.writeframes(silence)
            wav_file.writeframes(chunk.audio_int16_bytes)


def main() -> None:
    scenes_path, voices_dir, output_dir = (Path(argument) for argument in sys.argv[1:4])
    scenes = json.loads(scenes_path.read_text(encoding="utf-8"))["scenes"]
    durations: dict[str, dict[str, float]] = {}
    for language, model in VOICES.items():
        voice = PiperVoice.load(voices_dir / model)
        config = SynthesisConfig(length_scale=LENGTH_SCALE[language])
        target = output_dir / language
        target.mkdir(parents=True, exist_ok=True)
        durations[language] = {}
        for scene in scenes:
            line = scene["lines"][language]
            path = target / f"{scene['id']}.wav"
            text = line.get("say", line["text"])
            # O Piper sorteia a duração: a mesma fala muda alguns décimos a cada síntese. Reaproveitar
            # o WAV garante que o vídeo final tem a fala que o --stills conferiu e a linha do tempo aceitou.
            stamp = path.with_suffix(".txt")
            signature = f"{LENGTH_SCALE[language]}|{SENTENCE_SILENCE}|{text}"
            if not (path.exists() and stamp.exists() and stamp.read_text(encoding="utf-8") == signature):
                write_speech(voice, config, text, path)
                stamp.write_text(signature, encoding="utf-8")
            with wave.open(str(path), "rb") as wav_file:
                durations[language][scene["id"]] = round(wav_file.getnframes() / wav_file.getframerate(), 3)
    (output_dir / "durations.json").write_text(json.dumps(durations, indent=2), encoding="utf-8")
    print(output_dir / "durations.json")


if __name__ == "__main__":
    main()
