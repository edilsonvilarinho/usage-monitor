"""Sintetiza a fala de cada cena nos dois idiomas e mede a duração de cada arquivo.

O motor sai do roteiro: sem `voice`, o Piper com as vozes do vídeo de release; com
`"voice": {"engine": "kokoro", "voices": {"en": "am_michael", "pt": "pf_dora"}, "speed": {"en": 0.86, "pt": 0.78}, "pause": 0.5}`, o Kokoro
(tour em vídeo, #505: a faber do Piper soou robótica, e dora/michael foram escolhidas pelo dono ouvindo amostras).

Uso: python synthesize.py <scenes.json> <voices-dir> <saida-dir>
Escreve <saida-dir>/<idioma>/<cena>.wav e <saida-dir>/durations.json ({"en": {"cena": segundos}, "pt": {...}}).
Roda no venv do cache de ferramentas (setup-tools.mjs), nunca no Python do sistema.
"""

import json
import re
import sys
import wave
from pathlib import Path

import numpy as np

# Vozes ouvidas e aprovadas no vídeo da 41.11 (docs/planos/video-v41-11/roteiro.md, U4).
VOICES = {"en": "en_US-ryan-high.onnx", "pt": "pt_BR-faber-medium.onnx"}
# 1,12 na 41.11; o pedido seguinte foi "não ficar muito rápido". A faber fala mais depressa que a ryan:
# a 1,2 ela chegou a 19,9 caracteres/s, contra 15,8 da ryan na mesma cena (v42.0.0, cena "team").
LENGTH_SCALE = {"en": 1.4, "pt": 1.5}
# Silêncio entre frases, como na 41.11: frase curta o Piper lê corrida, e a pausa é o que dá fôlego.
SENTENCE_SILENCE = 0.35


KOKORO_LANG = {"en": "en-us", "pt": "pt-br"}
KOKORO_MODEL = "kokoro-v1.0.onnx"
KOKORO_VOICES = "voices-v1.0.bin"


def write_speech(voice, config, text: str, path: Path) -> None:
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


def write_kokoro(engine, voice: str, language: str, speed: float, pause: float, text: str, path: Path) -> None:
    # Frase por frase, com o mesmo silêncio do Piper entre elas: o fôlego entre frases é o que tira a pressa.
    sentences = [part.strip() for part in re.split(r"(?<=[.!?:])\s+", text) if part.strip()]
    pieces = []
    rate = 24000
    for index, sentence in enumerate(sentences):
        samples, rate = engine.create(sentence, voice=voice, speed=speed, lang=KOKORO_LANG[language])
        if index > 0:
            pieces.append(np.zeros(int(rate * pause), dtype=np.float32))
        pieces.append(np.asarray(samples, dtype=np.float32))
    audio = np.clip(np.concatenate(pieces), -1.0, 1.0)
    with wave.open(str(path), "wb") as wav_file:
        wav_file.setframerate(rate)
        wav_file.setsampwidth(2)
        wav_file.setnchannels(1)
        wav_file.writeframes((audio * 32767).astype("<i2").tobytes())


def speakers(project: dict, voices_dir: Path):
    """Uma função de fala por idioma e a assinatura que decide se o WAV guardado ainda vale."""
    voice = project.get("voice", {"engine": "piper"})
    if voice["engine"] == "kokoro":
        from kokoro_onnx import Kokoro

        engine = Kokoro(str(voices_dir / KOKORO_MODEL), str(voices_dir / KOKORO_VOICES))
        for language in ("en", "pt"):
            name = voice["voices"][language]
            # Um número vale para os dois idiomas; um objeto por idioma. A dora fala mais depressa que o michael.
            speed = voice.get("speed", 1.0)
            speed = speed[language] if isinstance(speed, dict) else speed
            # Pausa entre frases (s): mais fôlego é o outro jeito de tirar a pressa sem acelerar nada.
            pause = voice.get("pause", SENTENCE_SILENCE)
            yield language, f"kokoro|{name}|{speed}|{pause}", (
                lambda text, path, name=name, language=language, speed=speed, pause=pause:
                write_kokoro(engine, name, language, speed, pause, text, path))
        return
    from piper import PiperVoice, SynthesisConfig

    for language, model in VOICES.items():
        piper_voice = PiperVoice.load(voices_dir / model)
        config = SynthesisConfig(length_scale=LENGTH_SCALE[language])
        yield language, f"{LENGTH_SCALE[language]}|{SENTENCE_SILENCE}", (
            lambda text, path, piper_voice=piper_voice, config=config: write_speech(piper_voice, config, text, path))


def main() -> None:
    scenes_path, voices_dir, output_dir = (Path(argument) for argument in sys.argv[1:4])
    project = json.loads(scenes_path.read_text(encoding="utf-8"))
    scenes = project["scenes"]
    durations: dict[str, dict[str, float]] = {}
    for language, settings, speak in speakers(project, voices_dir):
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
            signature = f"{settings}|{text}"
            if not (path.exists() and stamp.exists() and stamp.read_text(encoding="utf-8") == signature):
                speak(text, path)
                stamp.write_text(signature, encoding="utf-8")
            with wave.open(str(path), "rb") as wav_file:
                durations[language][scene["id"]] = round(wav_file.getnframes() / wav_file.getframerate(), 3)
    (output_dir / "durations.json").write_text(json.dumps(durations, indent=2), encoding="utf-8")
    print(output_dir / "durations.json")


if __name__ == "__main__":
    main()
