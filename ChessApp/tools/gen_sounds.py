#!/usr/bin/env python3
"""Synthesise the three tiny sound effects used by the app (original work, released under CC0 1.0).

Usage (from the ChessApp directory):  python3 tools/gen_sounds.py
Pure standard library; output is 16-bit mono 22.05 kHz WAV in app/src/main/res/raw.
"""
import math
import pathlib
import random
import struct
import wave

RATE = 22050
OUT = pathlib.Path(__file__).resolve().parent.parent / "app/src/main/res/raw"


def render(path, seconds, voices, noise=0.0, noise_decay=60.0):
    """voices: list of (frequency Hz, gain, decay per second, start offset in seconds)."""
    rng = random.Random(7)  # deterministic output
    n = int(RATE * seconds)
    samples = []
    for i in range(n):
        t = i / RATE
        v = 0.0
        for freq, gain, decay, start in voices:
            if t >= start:
                tt = t - start
                v += gain * math.sin(2 * math.pi * freq * tt) * math.exp(-decay * tt)
        v += noise * (rng.random() * 2 - 1) * math.exp(-noise_decay * t)
        attack = min(1.0, t / 0.002)           # 2 ms fade-in avoids a click
        samples.append(max(-1.0, min(1.0, v * attack)))
    with wave.open(str(path), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(b"".join(struct.pack("<h", int(s * 32000)) for s in samples))
    print(f"wrote {path.name}: {path.stat().st_size} bytes")


OUT.mkdir(parents=True, exist_ok=True)
render(OUT / "move.wav", 0.12, [(190, 0.55, 38, 0), (95, 0.45, 30, 0)], noise=0.30, noise_decay=90)
render(OUT / "capture.wav", 0.18, [(240, 0.6, 28, 0), (120, 0.5, 24, 0), (480, 0.25, 40, 0)], noise=0.55, noise_decay=55)
render(OUT / "game_end.wav", 0.60, [(523, 0.35, 7, 0), (659, 0.30, 7, 0.12), (784, 0.30, 5, 0.24)])
