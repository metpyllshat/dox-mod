"""Синтез звуков интерфейса мода «Чёрный канал» (без внешних файлов).

Пишем WAV стандартной библиотекой, потом ffmpeg (libvorbis) кладёт .ogg
в assets/blackchannel/sounds. Голос диктора здесь не делается: он идёт
через синтез речи и лежит в sounds/announcer/*.ogg.

Запуск: FFMPEG=/путь/к/ffmpeg python3 tools/gen_sounds.py
(если FFMPEG не задан, берётся ffmpeg из PATH).
"""
import math
import os
import random
import struct
import subprocess
import tempfile
import wave

RATE = 22050
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "blackchannel", "sounds")
FFMPEG = os.environ.get("FFMPEG", "ffmpeg")
rng = random.Random(7)


def clip(x):
    return max(-1.0, min(1.0, x))


def samples(seconds):
    return int(RATE * seconds)


def tone(freq_fn, seconds, decay=0.0, shape="sine"):
    out = []
    phase = 0.0
    for i in range(samples(seconds)):
        t = i / RATE
        f = freq_fn(t)
        phase += 2 * math.pi * f / RATE
        if shape == "square":
            v = 1.0 if math.sin(phase) >= 0 else -1.0
            v *= 0.6
        else:
            v = math.sin(phase)
        out.append(v * math.exp(-decay * t))
    return out


def noise(seconds, level):
    return [rng.uniform(-1, 1) * level for _ in range(samples(seconds))]


def mix(*parts):
    n = max(len(p) for p in parts)
    res = [0.0] * n
    for p in parts:
        for i, v in enumerate(p):
            res[i] += v
    return res


def fade(data, attack=0.005, release=0.02):
    a = max(1, samples(attack))
    r = max(1, samples(release))
    n = len(data)
    for i in range(n):
        g = 1.0
        if i < a:
            g = i / a
        if i > n - r:
            g = min(g, (n - i) / r)
        data[i] *= g
    return data


def echo(data, delay, gain):
    d = samples(delay)
    out = list(data) + [0.0] * d
    for i, v in enumerate(data):
        out[i + d] += v * gain
    return out


def scale(data, gain):
    return [clip(v * gain) for v in data]


def concat(*parts):
    out = []
    for p in parts:
        out.extend(p)
    return out


def click():
    body = tone(lambda t: 1800, 0.045, decay=90)
    tick = noise(0.01, 0.4)
    return scale(fade(mix(body, tick + [0.0] * (len(body) - len(tick)))), 0.5)


def ping():
    sweep = tone(lambda t: 1200 + 1200 * min(1.0, t / 0.08), 0.35, decay=9)
    return scale(fade(echo(sweep, 0.18, 0.35), release=0.08), 0.55)


def hum():
    base = tone(lambda t: 92, 0.7, shape="square")
    harm = tone(lambda t: 184, 0.7, decay=1.5)
    am = [0.6 + 0.4 * math.sin(2 * math.pi * 24 * i / RATE) for i in range(len(base))]
    buzz = [b * m for b, m in zip(mix(base, harm), am)]
    grit = noise(0.7, 0.12)
    return scale(fade(mix(buzz, grit), attack=0.02, release=0.15), 0.45)


SOUNDS = {
    "ui/click": click,
    "bearing/ping": ping,
    "jam/hum": hum,
}


def write(name, data):
    path = os.path.join(OUT, name + ".ogg")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        wav_path = os.path.join(tmp, "s.wav")
        with wave.open(wav_path, "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(RATE)
            w.writeframes(b"".join(struct.pack("<h", int(clip(v) * 32767)) for v in data))
        subprocess.run([FFMPEG, "-y", "-loglevel", "error", "-i", wav_path,
                        "-c:a", "libvorbis", "-q:a", "4", path], check=True)
    print("ok", path, os.path.getsize(path))


if __name__ == "__main__":
    for name, fn in SOUNDS.items():
        write(name, fn())
